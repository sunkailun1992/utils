# GitHub 独立开发环境

本目录是个人 GitHub 项目族的配置入口。个人服务的配置、服务发现和 AI Registry 使用独立的 `github-dev` namespace，默认 group 为 `DEFAULT_GROUP`。按当前约定，`dev`、`test`、`prod` 三个 profile 都连接个人 dev，统一读取 `GITHUB_NACOS_DEV_*`；Git 的 `main/test/preprod` 不代表部署了三个环境。公司 dev 的配置独立维护，不由这里的发布脚本更新。

当前复核结果见 [2026-11-04 迁移整体验证](audits/2026-11-04.md)。`acceptance.json` 和 `delivery-manifest.json` 保留首次迁移快照，不作为当前运行态或版本依据。

## 配置与凭据

- `nacos/DEFAULT_GROUP/`：25 个可提交模板。职责、消费者与参数注释见 [配置分类索引](nacos/README.md)。新增/修改时必须遵循 [Nacos 注释与归类规范](../../docs/ai-coding/NACOS_COMMENT_SPEC.md)。
- `~/.config/kellen-github/dev.env`：本机私有配置，普通文件、权限 `0600`，不进 Git。
- 各 Java 服务根目录的 `config/application-dev.yml`：本机私有 Nacos 引导配置。写入前在本机 Git 排除规则中加入对应路径并核对已忽略、未跟踪。使用当前用户持有的 `0600` 普通文件。保存 Nacos dev 地址、命名空间、group、账号及密码；模型、数据库和业务配置仍使用各自入口。
- `~/.config/kellen-github/nacos-rendered/DEFAULT_GROUP/`：包含真实连接值及通用模型密钥的私有渲染结果，文件权限 `0600`。禁止复制到仓库或 Wiki。
- 模型密钥经用户明确确认沿用其通用自有密钥；没有复制医院、患者、问卷、报告模板、企微或设备业务配置。微信应用身份未因模型密钥授权而复制。
- Nacos 引导只接受 `GITHUB_NACOS_*`，不再回落到旧 `NACOS_NAMESPACE`。Spring discovery/config、Dubbo 与未来启用的 AI SDK namespace 都引用同一个 `custom.namespace`。
- Dubbo 使用独立 `GITHUB_DEV_DUBBO` 分组；Redis 使用独立逻辑库 `12`；RabbitMQ 使用 `/github-dev` vhost；RAG 使用 `github_dev_rag` collection。
- ES 请求日志由 `elasticsearch.request-log-index=github_dev_request_log` 隔离，要求消费者使用 `com:utils:1.6.0` 并重启载入实体映射；仅切换 Nacos namespace 不会自动隔离 ES 索引。单节点开发索引使用零副本，既有 `request_log` 和 Kibana 系统索引保持原样。

## 数据库

个人 Java 业务库统一使用 **NAS 上的 PostgreSQL 18**，本机只运行 JDBC 客户端和迁移工具，不安装或启动 PostgreSQL 服务。七个库使用同名独立低权限角色、各自的 `public` schema；每个角色只能连接自己的业务库。NAS 原有 MySQL 容器、数据和账号继续保留，供美联 dev 及必要中间件使用；个人数据库切换不迁移或修改美联库。

| 服务 | PostgreSQL 数据库 / 角色 | 表数 |
|---|---|---:|
| ai | `github_dev_ai` | 17 |
| ai-memory | `github_dev_ai_memory` | 9 |
| rag | `github_dev_rag` | 2 |
| message | `github_dev_message` | 3 |
| user | `github_dev_user` | 15 |
| mcp | `github_dev_mcp` | 2 |
| report | `github_dev_report` | 2 |

共 50 张表，包含七组公共 `ddl_history` / Seata `undo_log`。`ai-agent`、gateway 不使用业务 JDBC 数据库。RAG 正文和向量继续由 Qdrant 保存，可重建图谱继续由 Neo4j 保存；不恢复旧关系库 RAG / 图谱表。现行 Entity 与 PostgreSQL 权威 DDL 是建表依据，历史 MySQL SQL 保留为历史材料，不在 PostgreSQL 执行。

NAS 独立 Conductor OSS 的 PostgreSQL、隔离锁 Redis、UI/API 认证、安全观测和真实合成工作流验收，见 [Conductor 运维说明](../../docs/conductor-operations.md)。HTTP、404 重试、Cron、WAIT 恢复与日志链路对账已分别验证；旧 XXL-JOB 服务退役，原 MySQL 调度数据完整保留。完整私有 Compose 不进入本目录。

生成逐库结构脚本（只写文件，不连接数据库）：

```bash
python3 ops/github-dev/scripts/prepare_databases.py --output /tmp/github-dev-db-plan
```

输出七份 `github_dev_*.sql` 和 `database-manifest.json`。这些脚本只创建 schema 内结构，不创建数据库、不复制记录、不运行用户种子。先由 NAS 管理流程建立数据库与独立角色，再将对应脚本执行到相应空数据库。脚本读取各仓库权威 DDL，不维护第二套业务 SQL 副本。

迁移已有 MySQL 数据前，停止个人业务写入，确认所有 `undo_log` 为空；保持新库全表为空，不提前启动应用 DDL runner 或用户 bootstrap。受控迁移工具先只读生成计划，执行时核对计划摘要、逐表行数与指纹、数据类型和自增序列；全部通过后才发布 PostgreSQL 连接配置并启动应用。原 MySQL 数据继续保留用于核对和回退，不因迁移成功自动删除。运行态切换状态以本轮实际验收回执为准，不能仅凭本文件判断已经切换。

用户 bootstrap 只建立通用租户、管理员角色及资源权限，使用 PostgreSQL `ON CONFLICT DO NOTHING` 保留既有记录，不创建人员账号或默认密码、不覆盖已有权限。授权的管理用户由受控迁移保留；真实内容不写入公开 SQL、日志、聊天或 Wiki。

## 统一存储

新文件通过 X File Storage `2.3.0`，默认平台 `minio-private`，使用已授权的测试 MinIO 与独立私有 bucket `kellen-github-dev`。本轮不安装新的 MinIO 服务。新对象前缀为 `github/ai`，不导入公司文件。

AI 上传策略、完成登记和展示下载都调用统一门面；小程序提交完整签名 `formFields`，数据库保存 `storage_platform`。已登记文件继续按记录中的平台读取，切换默认平台不会把历史文件读到另一后端。历史空平台记录仅为原 OSS 兼容，新平台明确选定但配置错误时拒绝回落。凭据不传给前端。

## 校验、渲染与发布

在 `utils` 仓库执行：

```bash
python3 ops/github-dev/scripts/verify_configuration.py
python3 -m unittest discover -s ops/github-dev/tests -v
python3 ops/github-dev/scripts/render_nacos.py
python3 ops/github-dev/scripts/sync_nacos.py
```

配置检查需要同级 Java 服务与 `admin-web`，会检查 27 个 profile、dataId 覆盖、实体表清单，以及前端 service 前缀、网关路由优先级、StripPrefix 与后端 Controller 根路径。它不是完整 API 方法/参数校验，也不能替代在线验证。路由反例测试不需要同级仓库，已接入 CI。

`render_nacos.py` 只替换 YAML 标量中的 `GITHUB_*` 占位符，保留注释、分组与 Spring `${custom.*}` 引用，并与原语义渲染结果比较；凭据通过字符串序列化转义，不能拼成额外字段。渲染成功只证明本地产物已生成，发布前仍需读回当前 Nacos 配置。

`sync_nacos.py` 默认只读，使用仓库现有 Gradle 依赖中的官方 Nacos SDK，逐 dataId 读回核对，日志只在私有目录。目录结构和说明使用 `nacos/catalog.json`，合并/删除清单见 `nacos/retired-dataids.json`。渲染时使用新的空私有输出目录，避免以前的 8 个旧文件残留被当作候选重复发布；发布前比对输出文件集合与 catalog。新建配置需显式 `--publish`；更新已有配置还需 `--previous <已复核的旧配置目录>`，程序比对旧内容并通过 CAS 发布，遇到漂移拒绝覆盖。真实配置只发到固定的 `github-dev/DEFAULT_GROUP`，不写 Nacos 数据库。

## 本地启动

本机 IDEA、`bootRun` 和直接执行 `java -jar` 的工作目录统一为对应服务仓库根。默认 profile 为 `dev`，Spring Boot 自动读取私有 `config/application-dev.yml`，也接受 `.yaml`。五个引导键是 `GITHUB_NACOS_DEV_SERVER_ADDR`、`GITHUB_NACOS_DEV_NAMESPACE`（`github-dev`）、`GITHUB_NACOS_GROUP`（`DEFAULT_GROUP`）、`GITHUB_NACOS_USERNAME` 和 `GITHUB_NACOS_PASSWORD`。直接启动时 OS 环境变量优先。显式启用 `test` 或 `prod` 时，Spring 不读取 `dev` 文件，连接仍由对应 profile 的个人 Nacos 变量和既有默认值决定。

旧 `config/application-dev.properties` 仍可用。同目录两种格式并存时，同名属性以 properties 为准。迁移后将旧文件移到 Spring 配置搜索路径之外保留。个人工作区观测入口在仅保留 YAML 时，从 YAML 读取 Nacos 地址、group、账号和密码，并继续固定 `dev`、`github-dev` 和既有观测参数。服务器托管 Jar 继续从目标服务器的私有 env 读取 Nacos 配置。

本机 Nacos 控制台端口是 `18080`，应用端 `server-addr` 使用 `8848`，gRPC 使用 `9848`。命名空间显示名和真实 ID 均为 `github-dev`。控制台 URL 与 SDK 地址分别配置，不能把控制台地址直接填进应用。

个人本地灰度启动前，先将 `local-canary.yaml` 单独发布到 `github-dev / DEFAULT_GROUP` 并读回。各服务的 `dev` 启动入口必需导入该配置，`test` 和 `prod` 不导入。签名密钥放在私有 `dev.env` 的 `GITHUB_PERSONAL_CANARY_SIGNING_KEY`（32 字节随机数的 base64url，43 个字符），由 `render_nacos.py` 渲染进 `local-canary.yaml`，网关与参与服务因此读到同一把密钥；缺少时渲染失败，不会发布空密钥。当前 github-dev 的 Nacos 未开启客户端鉴权，能访问 Nacos 的人可读到该密钥，属于开发环境风险等级。轮换时更新 `dev.env`，重新渲染并 CAS 发布该 dataId，再重启网关和参与服务（该配置 `refreshEnabled=false`）。默认标签是 `kellen-local`，修改 `APP_CANARY_TAG` 时同步修改本地网关 `dev` 的授权映射。未就绪时本地后端不注册或导出参与灰度的 RPC，签名为空时拒绝灰度授权。

当前已发布的个人公共制品为 `com:utils:1.6.6` 和 `com:rpc-api:1.0.9`（第1阶段底座升级，Java 21），各消费者按自己的依赖锁定精确版本，不会自动升级。与美联共用阿里云 Maven 仓库地址，但美联制品使用 `com.kellen` group，两边独立发布和递增版本。Maven 凭据从私有环境或用户 Gradle 配置注入，不放进本目录；发布前核对目标版本未占用，发布后核对远程 POM、JAR 和本地构建的 SHA-256。历史验收 JSON 中的 `1.4.9` 是当时运行快照，不能作为新构建的坐标。

先构建目标服务，再从 `utils` 启动：

```bash
python3 ops/github-dev/scripts/start_service.py user --dry-run
python3 ops/github-dev/scripts/start_service.py user
```

启动器只解析私有环境文件中的 `GITHUB_*`，不执行 shell 内容，固定 `APP_ENV=dev` 与 `github-dev`。其它服务名为 `gateway`、`message`、`report`、`ai`、`ai-agent`、`ai-memory`、`rag`、`mcp`。网关端口 `7100`；管理端前端沿用 `http://localhost:7100`。允许浏览器 Origin 明确列在 `gateway.cors.allowed-origins`，默认仅本机 `8000` 开发前端；需要其它地址时显式更新，不放开 `*`。

## 2026-11-04 首次迁移历史快照

- Nacos 37 个 dataId 通过官方 SDK 发布和逐一读回；公司 `dev` 配置不覆盖。
- 七个个人数据库的表数量与 `storage_platform` 已读回；应用账户能读个人库，访问公司原 `user.auth_user` 被 MySQL 权限拒绝。
- MySQL 存储平台 migration 在个人临时同构表完成首次加列和第二次 no-op，列数量仍为一，临时表已回收。
- X File Storage 在真实 MinIO 验证 POST 上传、存在性、短签名 GET、大小限制，以及篡改 owner key/MIME 拒绝，测试对象回收。
- Java/前端/小程序回归、公共制品、版本和各仓源 SHA 见各仓 `docs/security-architecture-sync.md` 及本目录交付清单。
- 实际临时启动验证：gateway 与 ai-agent 聚合 health 为 `200 UP`；gateway 经独立 Nacos 发现 user，返回个人租户 `100`，匿名受保护接口 `403`。CORS 允许 Origin 为 `200`、未允许或 `null` Origin 为 `403`；敏感端点 `404`；40 个并发公共读取得到 19 个 `200` 与 21 个 `429`，伪造不同 `X-Forwarded-For` 不能避开 socket IP 限流。ai-agent 匿名模型入口为 `401`，未进入模型执行。
- 首次迁移时 ES `7.17.9` 与 Java client 不兼容，user 聚合 health 曾为 `503`。后续已升级 ES 并修正两边 dev 的 ES 连接配置；该次 9 个服务 health/readiness 均为 `200 UP`，user 保留真实 ES 健康检查。旧的失败记录保留在历史验收 JSON，不应据此再次关闭健康检查。
- 业务演示种子、Prompt 自动同步、AgentCard 自动发布均关闭，避免重新导入既有医疗资源。模型配置接通不等于 AI 对话或付费模型效果验收；本轮不发付费推理请求。微信登录、真实业务流程、RPC/模型组合仍分别验收。
- 本轮功能源分支为 `codex/sync-security-architecture-20261103`，Git 集成目标为 `main`。交付清单和运行验收 JSON 是 Git 发布前的验证快照；当前提交与推送状态以各仓库 Git refs 及远端读回为准。MavenLocal、GitHub CI、远程 Maven 制品和真实服务部署分别验收。
