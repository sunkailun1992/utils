# Conductor OSS 运维说明

本文记录个人开发环境的 Conductor 部署边界和验收方法。完整 Compose、真实绑定地址、认证信息及运行回执单独保存在受控私有交付中，不进入本仓库。这里不提供另一套生产模板，也不通过文档操作部署。

## 组件与持久化

采用 [Conductor OSS 3.33.0 正式版本](https://github.com/conductor-oss/conductor/releases/tag/v3.33.0)，使用包含服务端与编译后 UI 的官方镜像。部署按目标架构核对 manifest 和 Docker image ID，并固定 digest，不使用浮动 `latest` 或开发版。

| 组件 | 当前职责与边界 |
| --- | --- |
| Conductor | 独立 NAS 服务，运行 UID `10001`；上限为 1 CPU、1536 MiB，JVM 最大堆 768 MiB |
| PostgreSQL 18 | NAS 上既有 PostgreSQL 实例；Conductor 使用独立数据库、低权限角色及 Hikari 连接池，最大连接数 4 |
| 锁 Redis | 独立 Redis 7.4.7 容器，运行 UID `999`；上限为 0.25 CPU、128 MiB，无宿主端口，仅 Conductor 内部网络可达 |
| 配置与 agent 初始化 | 一次性校验固定 agent、隐私扩展和内联配置，成功标准为 `exit=0` |
| 数据库初始化 | 一次性建立 Conductor 自有数据库、角色及受管标记，已有完整状态读回验证，未知部分状态拒绝覆盖 |

工作流、队列、索引与日程使用 PostgreSQL；独立 Redis 承担分布式锁。锁实例的默认用户关闭，专用用户与命名空间只服务 Conductor，不修改共用 Redis 的默认账号、逻辑库或键。初始化有跨连接和跨数据库步骤，不能把初始化标记描述成覆盖整个建库过程的单一事务。

配置、agent、安全日志与 filelog offset 使用各自 named volume。维护不执行 `down -v`、卷清理或全项目重建。现有 MySQL 及其数据继续保留，个人调度替换不删除其它项目或旧 XXL-JOB 的数据。

服务端镜像自带 JRE 21，个人业务服务继续使用 Java 17。本轮通过 REST 和内置 HTTP system task 接入，没有引入 Java SDK、独立 worker 或业务调度 HTTP 接口。后续业务任务按实际授权和幂等要求接入，不复用旧演示任务作为已完成业务能力。

## 访问与认证

浏览器访问私有交付中记录的 `<CONDUCTOR_PRIVATE_ORIGIN>`。编译后 UI 与 `/api/` 都经同一 Nginx 入口执行 Basic 认证，转发前移除该 Authorization。后端 `8080` 只监听容器回环地址，不发布宿主端口。

`/actuator/prometheus` 使用独立采集身份；UI 身份不能读取该端点，采集身份不能访问 UI 或业务 API。无凭据、错误凭据和跨身份访问都应拒绝。该入口隔离不等于已实现面向多人的细粒度权限管理。

UI 的 `/context.js` 从后端动态读取并禁用缓存，AI 集成保持关闭；不能使用镜像静态文件里的默认开关推定实际状态。Nginx access log 关闭，不记录 query、Authorization、请求体或响应体。

## 指标、链路与安全日志

指标使用服务原生 Prometheus 端点，由既有 Prometheus 抓取。先看 `up{job="conductor"}`，再核对实际指标样本；目标 UP、health 成功和工作流完整执行分别验收。

链路使用固定 OTel Java agent 和校验过的隐私扩展，均校验 SHA-256。只启用 traces，使用 `tracecontext` 和开发环境全量采样；原始 events、异常描述、SQL、URL/query、headers 和 payload 在出站前清理，中央 Collector 再清理一次。不在应用内增加第二个 SDK。开发环境采样策略发生调整后，应重新验收传入 `sampled=0` 的实际请求，不能用此前快照推定后续仍完整采集。

运行日志使用镜像内的 **Log4j2**。源端只输出固定事件、级别、调用类和合法 trace/span 摘要，不输出 message、异常堆栈、SQL、payload 或任意 MDC。中央 Collector 只读取这个自有 named volume，并持久化 offset；不挂 Docker socket，也不扫描其它容器或宿主日志。Grafana 通过既有 ES 数据源查看固定 `summary`，链路进入既有 Jaeger。

读回时应注意：

- ES raw 映射可能将 `Resource.*` 与投影属性平铺到顶层，也可能返回嵌套对象。规范化后核对 `service.name=conductor`、`service.version=3.33.0`、`deployment.environment.name=github-dev`，拒绝冲突路径、未知投影和非空 Body。
- 当前 Jaeger v3 读回出现 `http.status_code`，查询器需同时兼容 `http.response.status_code`。只接受合法整数状态码；双字段同时存在但不一致时拒绝。
- Jaeger 搜索无命中可返回 HTTP 404。404 可以作为空结果，HTTP 500、部分结果、截断或冲突 span 不能当作空结果。
- 工作流启动请求与异步任务不必属于同一条 trace。HTTP task 的真实客户端 span、下游 SERVER span 和下游唯一终态日志，应按实际 trace/span/parent 关系对账。

Conductor 只接入上述安全运行日志，没有把它扩展为个人 Java 服务与 Engine 的“每个 HTTP SERVER 必有一个终态事件”合同。不要以 Conductor 日志条数替代工作流任务次数。

## 本轮验收与保留边界

2026-11-10 的部署读回快照已核对：Conductor 与独立锁 Redis 健康、无 OOM 和自动重启；UID、入口认证、身份分离、回环监听、原有受保护容器的配置及生命周期保持；原生指标、ES 安全日志与一条保存了预期 parent 的 `sampled=0` 元数据 trace 均已回读。这里的 UI 验证是 HTTP 访问和内容检查，不代替完整浏览器交互。

本轮已完成以下免费合成验收。以下结果属于这次固定部署与合成请求的快照，不替代后续真实业务任务的幂等、权限和效果验收：

| 场景 | 实际结果 |
| --- | --- |
| HTTP 健康工作流 | 原有健康执行完成，HTTP 200 和固定健康结果正确；后续采用既有 ID，没有重新启动该健康工作流 |
| 失败与重试 | 严格 API 下不存在的合成定义返回真实 404；首次加两次重试共 3 个 FAILED 尝试，3 条客户端与服务端父子边回读通过 |
| UTC Cron | 45 秒有界窗口内实际触发 1 次，关闭 catch-up，随后暂停；最终删除本次日程 |
| WAIT 与重启恢复 | 同一 WAIT task 先观察到执行中，仅重启 Conductor；同 task ID 在新容器启动时间后完成，暂停日程仍可读，其它 24 个容器的配置和生命周期保持 |
| 跨组件对账 | 预期健康请求 `2 + 1` 次；Engine SERVER 3 条、匹配 Conductor 客户端 parent 3 条、ES 唯一终态 3 条，N/N 一致 |

初次执行及读回工具的失败回执均保留，后续成功不覆盖它们。空 JSON 的 Content-Type 错误修正后，健康启动请求曾在客户端 5 秒等待后超时，服务器实际已完成；通过 correlation 采用既有 ID。根路径的不存在页面被 SPA 返回 200 HTML，不能用于验证失败重试；纠正场景使用另一个合成命名空间和真实 API 404，保留原已执行定义。Spring 错误消息会省略查询参数，也可能没有 reason phrase；读回同时严格核对任务完整输入、固定 404 载体和实际 trace 父子边，不能只匹配异常文字中的 `404`。

后续恢复必须先核对原 manifest、定义及服务器默认值、对应 correlation 与日程状态，确认个人 Java 采集全部恢复。每个变更先保存操作意图和一次性标记。只有工作流启动 POST 的客户端等待按实测调整为 15 秒，其它读取保持 5 秒，Engine 原有检索预算不变；POST 结果不确定时只读 correlation，不能自动重发。失败只保留固定异常类别、明确 SQLSTATE 和状态等元数据，不保存原始响应。

验收只使用合成命名空间和无业务副作用的接口，没有调用模型、检索或患者数据。定义和执行历史保留供复核；清理只针对确认归属的日程与合成记录。

新调度验收完成后，已按明确退役授权移除旧 XXL-JOB 服务和容器，只重建 Conductor 一次性初始化容器并禁用继承的 HTTP 健康检查，实际 `exit=0`、healthcheck 为 `NONE`。其它 23 个容器的完整配置和生命周期保持，MySQL、PostgreSQL、Engine、Conductor 与锁 Redis 没有因此重启，没有执行 SQL 或删除卷、网络。原 MySQL 调度库全表保持，包含一条归属未核实的启用定义；没有把它认领成个人演示任务、修改其启用标记或删除历史记录。退役时未发现活跃执行器、手动或缓存地址、未完成成功触发及近期排队任务。

## 维护与故障定位

1. 先核对当前 Compose 摘要、镜像 digest/image ID、容器 ID、启动时间、重启数、OOM、health 和资源；旧回执不是持续健康证明。
2. 检查 UI/API、采集身份与回环端口边界，再看 Prometheus、Collector 导出结果、ES 实际记录及 Jaeger v3。
3. 控制面失败时记录具体阶段、方法和路由模板，区分定义写入、启动请求与任务执行；客户端没拿到 workflow ID 不证明服务器没有创建记录，先按已保存 correlation 只读核对。
4. 有错误再按固定异常类别、SQLSTATE、连接和锁的元数据定位，不为排障临时放宽 Redis ACL、关闭持久化或提高超时掩盖问题。
5. 更新前备份权威私有文件并做摘要/CAS 比较，明确允许变化的服务、卷与配置。仅处理本次服务，逐项核对其它容器的 ID、完整配置及 `StartedAt` 等生命周期；`RestartCount=0` 不能证明未发生显式重启。

回滚只使用已核对的私有备份和固定镜像，先确认当前工作流及持久状态，再对明确服务执行。不自动全项目重启，不删除 PostgreSQL、MySQL、日志或 offset 卷。

功能与配置依据还可查阅 [官方配置说明](https://docs.conductor-oss.org/documentation/configuration/appconf.html)和 [HTTP task 文档](https://docs.conductor-oss.org/documentation/configuration/workflowdef/systemtasks/http-task.html)。官方文档可能随主线更新，实际运维以固定版本、原生校验和本轮运行回执为准。
