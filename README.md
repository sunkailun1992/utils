# utils

`utils` 是面向 Spring Boot 4 / Java 21 的公共基础能力包，是 `user`、`message` 等业务服务复用通用能力的唯一来源。业务项目和网关项目不要复制本仓库里的工具类源码；需要公共能力时，先在本仓库维护并发布 Maven 制品，再让消费者项目升级依赖。

当前提供统一响应、错误码、统一异常处理、认证上下文、多租户、数据权限、MyBatis-Plus 基础配置、Redis/Redisson、RocketMQ 消息收发、HTTP、JSON、对象存储等通用工具；写法对照同类里 GitHub 关注度最高的项目，过时或无人使用的工具直接删除。

## 当前定位

- 发布坐标：`com:utils:2.0.8`
- Java 版本：`21`
- Gradle：`9.9.1`（wrapper 校验官方分发 SHA-256）
- Spring Boot：`4.0.9`
- Spring Cloud：`2025.1.4`
- Spring Cloud Alibaba：`2025.1.0.0`
- Nacos Client / Server：`3.3.0`
- Seata Server：`2.7.0`
- Seata Client：`2.6.0`
- Servlet / Validation 包名：统一使用 `jakarta.*`
- 统一响应：只使用 `com.kellen.utils.response.ApiResponse`
- 统一错误码：只使用 `com.kellen.utils.enumeration.ReturnCode`
- 全局异常处理：使用 `com.kellen.utils.exception.ApiExceptionHandler`
- 认证上下文：使用 `SecurityUser` 与 `UserContextHolder`
- 租户上下文：使用 `TenantContextHolder`
- 动态数据源：只保留 `master` 与 `gray`
- 个人业务库为 PostgreSQL；数据源用 dynamic-datasource 原生配置（各服务 `application.yml` 的 `spring.datasource.dynamic` 块，地址与账号在 Nacos 的 `spring.datasource.dynamic.datasource.master/gray`），字符串租户与 Boolean 逻辑删除、独立测试库及切换边界见 [PG 底座说明](docs/postgresql-fleet-foundation.md)。历史 MySQL 仍供其它项目或必要中间件使用，本轮不删除其数据库、账号或容器。
- 数据源连接健康：各服务 `application.yml` 的 `spring.datasource.dynamic.druid` 统一设借出前 `SELECT 1` 校验、空闲检测与保活（检测 30 秒、保活 60 秒，保活间隔须大于检测间隔，否则 Druid 启动失败）及连接 3000 毫秒、读取 30000 毫秒超时；JDBC URL 不带 `connectTimeout`、`socketTimeout`，见[超时单位说明](docs/postgresql-druid-timeouts.md)
- 实体基类：`EntityBase` 保留全部公共字段 `id`、`code`、`description`、`createTime`、`createBy`、`updateTime`、`updateBy`、`deleted`、`label`、`sort`、`version`（审计字段按 RuoYi-Vue、RuoYi-Vue-Plus、JeecgBoot 等主流项目的多数命名），`TenantEntityBase` 再加 `tenantId`、`createDept`；时间、创建人、修改人、租户与创建部门由 `MyMetaObjectHandler` 自动填充；乐观锁 `version` 只表示 MyBatis-Plus `@Version` 数据库版本号
- JSON：`JsonUtil` 使用 Spring Boot 4 默认 Jackson 3 databind API；`json/bean/list` 方法签名保持兼容

### Jackson 3 迁移说明

- `JsonUtil.getJsonMapper()` 的返回类型由 Jackson 2
  `com.fasterxml.jackson.databind.ObjectMapper` 调整为 Jackson 3
  `tools.jackson.databind.ObjectMapper`，直接调用该方法的消费者必须重新编译。
- `1.6.8` 起方法名对照 Hutool `JSONUtil` 改为 `toJsonStr`、`toBean`、`toList`，容器内改用 Spring Boot
  配好的 `JsonMapper`，不再沿用单引号、浮点默认转 BigDecimal、自定义日期格式等旧规则。
- Jackson 注解仍使用 `com.fasterxml.jackson.annotation` 包；这里只迁移 databind/core API，
  不要求业务 DTO 改写注解。

## 仓库边界

- `utils`：公共工具、公共配置、公共注解、公共异常、公共上下文和可被多个服务复用的第三方封装。
- `user`：用户、认证、租户、权限、账号等业务实现，依赖 `com:utils` 制品，具体版本以消费者项目 `build.gradle` 为准。
- `gateway`：只做路由、跨域、限流、Actuator 访问保护和 OpenAPI 聚合；不保留 `com.kellen.utils` 本地副本。

维护原则：

- 公共工具类只在本仓库新增、修改和删除。
- 单个业务项目专用逻辑不要放入本仓库。
- 消费者项目发现缺少公共能力时，先评估是否属于多项目通用能力；确认通用后再改 `utils`。
- 从历史项目迁移代码时，必须先搜索消费者引用，避免把已经废弃的旧认证、旧响应、旧数据源逻辑带回来。

## 构建验证

```bash
./gradlew clean build -x test
./gradlew publishToMavenLocal
```

真实 Seata 两库提交与回滚使用显式 opt-in 的[跨进程 PostgreSQL 合同](docs/seata-live-contract.md)：
两个独立 JVM 通过 Triple 调用并连接两个专用测试库，复用公共数据源代理与 XID filter。
默认测试不连接外部组件；该基础设施合同与 fleet 业务全局事务接入分别验收。

## 管理端点与私有文件存储

- `ActuatorSecurityConfig` 为已暴露的 Actuator 端点建立独立安全链，与是否接入资源服务器无关。health 及探针保持公开；其它端点需要 `actuatorReq` 请求头匹配运行时 `security.actuator.token`，配置缺失时拒绝访问。令牌每次请求重新读取，轮换无需重启。旧 MVC `ActuatorInterceptor` 已停用，不能再注册为保护措施。
- 身份由 NAS 上的 Keycloak 签发：配了 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 的服务自动成为 OAuth2 资源服务器（只收 RS256、校验签发方与受众，Keycloak 不可达时启动不受影响、受保护接口返回 401），业务上下文经 `SecurityContextRpcService` 按请求取自 user 服务并缓存；出站服务间调用用客户端凭证或 RFC 8693 令牌交换。自签 JWT（jjwt、`JwtUtils`）、网关透传身份头已删除。接入方式见 [认证规范](docs/ai-coding/AUTH_SPEC.md)。
- MVC 租户上下文只取认证上下文：活动租户由 `X-Tenant-Id` 在用户所属租户里选择（user 服务校验归属），`tenantId`、`tenant-id` 等其它 HTTP 头不认；已认证但无租户的请求返回 403，匿名请求不带租户上下文。`tenant.ignore-without-tenant=false` 防止 SQL 层缺上下文时跳过租户条件；Dubbo/MQ 的既有租户头契约保留。
- 文件存储统一入口是 `com.kellen.storage.PrivateObjectStorageService`，使用 X File Storage `2.3.0` 管理 OSS / MinIO / 腾讯 COS 平台与客户端。`1.6.3` 补充 COS 原生 POST 表单签名，固定官方 SDK `com.qcloud:cos_api:5.6.283`，保留原方法与构造器。MinIO SDK 固定 `8.6.0`，也用于兼容 S3 的平台。
- 静态配置使用官方 `dromara.x-file-storage.default-platform`、`minio[]`、`aliyun-oss[]` 或 `tencent-cos[]`。MinIO/OSS 使用 `end-point`、`access-key/secret-key`，COS 使用 `region`、`secret-id/secret-key`；平台项有稳定 `platform` 和私有 `bucket-name`。Spring 配置另有 `enable-storage`，core Builder 不使用该字段；`base-path` 必须为空，由业务 `baseDir` 形成对象键。凭据仅在服务端注入，不进入源码、日志或管理元数据响应。AI 的 DB 加密配置、权限和动态句柄管理属于 AI 业务控制面，不放进公共包。
- `createUploadPolicy(...)` 返回 `UploadPolicy`，保持 POST 表单上传；`formFields` 是完整表单字段，客户端原样提交。MinIO 使用 S3 V4 签名字段，COS 使用官方 `COSSigner.buildPostObjectSignature` 及其专用表单协议。三者均固定精确对象 key、MIME、大小范围和到期时间，不能用普通请求签名冒充 POST 签名。真实云端权限/CORS、私有访问和读写仍需单独验收。
- `verifyObjectExists(platform, key)`、`signedViewUri(platform, key, expiry)` 支持读取持久化平台；调用方必须先完成所有者和展示 token 校验（`DisplayTokenUtils.matches`，库里只存哈希），不能把此通用门面当成授权服务。历史文件保留原平台，切换默认平台不会迁移已有对象。
- 上传下载统一走 X File Storage，OSS 只是其中一个平台；阿里云 OSS 专用的静态配置（`aliyun.oss.*`、`AliyunKey`）与直传工具已删除。

## 消息队列（RocketMQ）

- 基于 rocketmq-spring 2.3.6（`api` 依赖 `rocketmq-spring-boot-starter`），RocketMQ 客户端锁 5.5.1，与 NAS 上的 5.5 服务端同一版本线；只在服务配了 `rocketmq.name-server` 时装配，没配的服务不连 RocketMQ。
- 发送用 `MqTemplate`（同步发送；`sendAfterCommit` 事务提交后才发），选项有 tag、key、延时与顺序键。消费用 rocketmq-spring 的 `@RocketMQMessageListener` 加 `RocketMQListener<T>`，只支持单条，必须幂等。
- 收发钩子让租户、操作人与数据源标记随消息传递；消费组与订阅按本实例的泳道（Nacos 注册元数据 `traffic.lane`、`canary.tag`）自动隔离。
- 收发消息的服务要在自己的 `build.gradle` 把 RocketMQ 客户端锁到 5.5.1（Spring Cloud Alibaba 的 BOM 会压回 5.3.1，缺 ACL 签名类、启动失败），见规范的「服务接入」。
- 配置模板、命名、账号授权与禁止事项见 [消息队列规范](docs/ai-coding/MQ_SPEC.md)。

## 远程发布

个人公共包统一使用 `com:utils:1.6.0` 和 `com:rpc-api:1.0.7`；美联公共包使用 `com.kellen:utils` 和 `com.kellen:rpc-api`。两边共用阿里云 Maven 地址，由 group 形成不同仓库路径，独立发布和递增版本。个人消费者不得引用 `com.kellen` 的这两个制品；Java package `com.kellen.*` 不受 Maven group 迁移影响。

个人坐标此前从 `com.kellen:utils:1.4.9` 迁移到 `com:utils:1.5.1`，`1.5.2` 增加 ES 请求日志索引配置；`1.5.3` 增加 Prometheus registry、真实 OTel trace/span 快照与异步上下文恢复。观测使用 API 1.59.0，不在应用内创建第二个 SDK。保留旧制品用于历史构建，不覆盖或删除美联制品。回滚需同时回滚消费者依赖和对应代码，不能仅改制品 group。

`1.5.4` 修复 `ops/github-dev/observability/` 的采集状态配色、缺失目标判定、Collector UTF-8 指标名及无数据语义。本批没有修改 Java API 或运行时 JAR 逻辑，观测配置独立于 Maven JAR 分发；消费者无需仅为本批观测配置修复升级 `com:utils`，可以继续使用已发布的精确版本。新版本从 `main` 发布时仍须按本仓规则同步发布 Maven 制品。

`1.5.5` 首次引入轻量 `com:fleet-observability:0.1.0`，统一受控HTTP终态与输入输出投影；旧审计/慢请求提醒不再保存或发送原始参数与返回对象。独立Web栈、隐私与传输合同见 [HTTP安全结构化观测](docs/fleet-http-observability.md)。

请求日志索引通过 `elasticsearch.request-log-index` 配置；未指定时保留 `request_log`，个人 `github-dev` 使用 `github_dev_request_log`。Nacos namespace 只隔离配置与服务发现，不能隔离同一 ES 的索引。更换索引不会搬迁或删除历史日志，发布后需重新启动消费者以载入新的实体映射。

阿里云 Maven 仓库凭证不要写入仓库文件，放到本机 `~/.gradle/gradle.properties`：

```properties
privateMavenUrl=<PRIVATE_MAVEN_URL>
privateMavenUsername=<PRIVATE_MAVEN_USERNAME>
privateMavenPassword=<PRIVATE_MAVEN_PASSWORD>
```

发布到阿里云 Maven 仓库：

```bash
./gradlew publish
```

版本与发布规则：

- 只要本次任务修改生产代码、配置、构建脚本或公共示例，就必须提升一次 `build.gradle` 中的 `version`；完全不进入 Maven 制品的独立 `ops/` 部署配置仅按[版本规范的窄边界](docs/ai-coding/VERSIONING_SPEC.md#什么时候改-version)交付，不能据此豁免普通应用配置或安全验证。
- 同一批未提交改动只提升一次版本号；提交前继续补代码、补测试、补文档或修编译错误时，不得再次提升版本号。
- 纯 README、AI 规范、注释或说明文档改动不强制提升制品版本；如果同一任务同时改了代码或构建配置，则仍按代码改动规则提升一次。
- 非 `main` 分支或未提交状态默认只执行 `./gradlew publishToMavenLocal` 做消费者本地验证，不推送远程私有 Maven 制品。
- 从 `main` 分支开始提交并推送 Git 仓库时，必须同步执行 `./gradlew publish`，把同版本 jar、sourcesJar、javadocJar 和 Maven 元数据推送到远程私有 Maven 仓库。

消费者项目升级 `utils` 后，应同步执行消费者项目编译验证。例如 `user` 项目：

```bash
cd ../user
./gradlew clean compileJava -x test
```

如果调整了 gateway 会关心的公共约定，还需要验证 gateway：

```bash
cd ../gateway
./gradlew clean test bootJar --no-daemon
```

## 重要约束

- 不再使用旧 `Json` 统一返回对象。
- 不再通过旧 `token` 请求头或 Redis token 用户模式作为认证授权来源。
- 不再把 HTTP 请求头里的 `version` 当作业务版本校验。
- 不再新增 `bank`、`hz`、`hx`、`jghx` 等动态数据源常量或配置。
- 不在 `gateway`、`user` 等消费者项目内复制 `utils` 工具类源码。
- utils 不放迁移；各业务服务的表结构只由本服务 `src/main/resources/db/migration` 的 Flyway 迁移管理；已提交的迁移文件不修改，改结构只新增 `V<下一个>__<说明>.sql`；新库保持空库，由服务首次启动时的 Flyway 建表，不手工执行任何公共脚本；Seata 固定开启，utils 在建数据源之前先执行迁移，空库首次启动不用关 Seata。
- 不把网关路由、Nacos 配置、SLS/Logback 本地配置放进本仓库。
- AI 新增或修改 Java 代码时，必须补充说明公共职责、消费者影响和安全边界的有效注释，禁止机械逐行或行尾堆叠注释。
- 新增 Java 类必须补齐类注释、字段注释、方法 JavaDoc，并在关键逻辑块前说明为什么这样实现。
- 新增公共类型必须遵守 PascalCase 命名，文件名必须与 public 类型名一致。

## AI 阅读入口

AI 或新接手开发者按下面顺序阅读，不要只根据类名推断行为：

1. [AI 根入口](AGENTS.md)：确认公共包职责、阅读顺序、工程门禁和消费者验证要求。
2. [AI 编码入口](docs/ai-coding/README.md)：确认修改前检查项、验证命令和文档维护要求。
3. [AI 编码指导规范](docs/ai-coding/AI_CODING_GUIDE.md)：确认公共包 AI 修改流程、工程门禁和消费者验证要求。
4. [项目编码规范](docs/ai-coding/PROJECT_CODING_SPEC.md)：确认响应、异常、认证、租户、动态数据源、乐观锁和注释规则。
5. [安全编码规范](docs/ai-coding/SECURITY_CODING_SPEC.md)：修改认证、权限、SQL、文件、脱敏、上传下载、日志和第三方 SDK 前必须阅读。
6. [工具类归类目录](docs/ai-coding/UTILS_TOOL_CATALOG.md)：确认现有能力应该复用哪个包和哪个类。
   涉及消息收发时读 [消息队列规范](docs/ai-coding/MQ_SPEC.md)。
7. [包结构整理建议](docs/ai-coding/PACKAGE_REFACTOR_GUIDE.md)：新增或迁移工具类前确认目标包。

## AI 修改流程

1. 先用 `rg` 搜索本仓库和消费者项目引用，确认真实调用点。
2. 判断能力归属：多项目通用留在 `utils`，业务专用迁回业务项目，网关专用留在 `gateway`。
3. 修改公共 API 时同步更新本 README 和 `docs/ai-coding/*` 对应文档。
4. 执行 `./gradlew clean build -x test`，需要消费者验证时再执行对应消费者项目编译。
5. 发布前执行 `./gradlew publishToMavenLocal`；从 `main` 分支提交并推送 Git 仓库时必须同步执行 `./gradlew publish`，凭证放本机 Gradle 配置或环境变量。

## 个人微服务观测

唯一公共模板、独立极空间编排、采集目标渲染与验收步骤见 [观测部署说明](ops/github-dev/observability/README.md)。服务日志在请求线程通过 `TraceContextSnapshot.capture()` 取得真实 trace/span，异步保存保持该快照；业务会话 ID 不作为链路标识。公共异步池通过 `TraceContextTaskDecorator` 绑定并恢复 OTel Context，不替代用户与租户授权。

个人 Java 服务已移除 XXL-JOB 依赖、执行器配置与演示任务，不再读取 `xxl-job.yaml`。Conductor OSS 作为 NAS 独立服务通过 REST 接入；当前未接入业务 Java 调度接口，不添加 Java 21 SDK 或虚构任务开关。NAS XXL-JOB 服务按用户后续授权另行退役，先确认美联 dev 已显式关闭执行器；旧 MySQL 调度数据保留。

`1.6.0` 将个人业务 JDBC 驱动、方言与 Boolean 逻辑删除切换为 PostgreSQL，并移除个人 XXL-JOB 依赖；这是需要数据库迁移与配置联动的 minor 变更。配套轻库 `0.1.1` 修复 Boot ECS 中同名 MDC/KVP 导致整条事件丢失的问题，必须与 `logging.structured.json.context.include=false` 一起使用。源码/制品校验通过与实际配置发布、数据库切换、服务及链路验收分别取证。

`1.6.2` 配套轻库 `0.1.2` 修复 Servlet Security 在 ERROR 分派内再次 `sendError(403)` 时等待不存在后续分派而遗漏终态的问题；同时保留已提交响应上 `sendError` 抛错的一次失败终态。不改变认证规则或 403 响应，也不增加日志字段。真实 Tomcat/Security 回归、异步/取消相邻路径与运行态日志联结分别验收。

`1.6.6` 是第1阶段底座升级：JDK 21、Gradle 9.9.1、Spring Boot 4.0.9、Spring Cloud 2025.1.4、Nacos 客户端 3.3.0、Dubbo 3.3.7、MyBatis-Plus 3.5.18.1 等依赖顺延，公共 API 不变。配套轻库 `0.1.4` 与 `com:traffic-canary:0.1.1` 只改编译基线与依赖版本；OTel API/BOM 对齐 Java agent 2.33.1 内置的 1.67.0，不让应用侧 API 比 agent 新。消费者须运行在 Java 21。

`1.6.7` 修复 `DubboContextPropagationFilter` 拿不到 Spring 绑定配置的问题。Dubbo 3 创建过滤器后按 setter 名（`trafficGovernanceProperties`、`tenantProperties`）到 Spring 容器取 Bean，不按类型查找；`@EnableConfigurationProperties` 注册的 Bean 名对不上，过滤器一直用默认值，结果是提供方拒绝所有灰度授权、灰度实例不带授权也放行、自定义租户头名不生效。新增 `DubboExtensionPropertiesAliasConfig` 给容器里唯一的同类型 Bean 补别名，不新增 Bean；`DubboContextPropagationFilterInjectionTest` 按 Dubbo 真实注入路径覆盖。公共 API 不变，消费者升级后重启生效。

`1.6.8` 是第2阶段 utils 改造（一次发版）：删除过时、小众、从未生效或无人使用的 51 个类与十余个依赖（钉钉与阿里云短信/直播/工作流、请求日志写 ES、从未生效的防重复提交、PdfUtils、RabbitMQ 配置、Dozer、Orika、Aviator、Guava 显式声明、Jedis 等）；`GeneralConvertor` 改用 Hutool，`OkHttpUtils` 恢复默认证书校验并按官方用法关闭响应；Redis 连接与 `RedissonClient` 交给 Spring Boot 与 Redisson 官方 starter 自动配置，锁工具补回调式加锁与安全解锁；缓存序列化器换成 Jackson 3 实现；数据权限改为直接构造 JSqlParser 表达式、出错不再放行；异步任务带上提交时的租户与用户上下文；`ReturnCode` 常量改英文命名（编码与文案不变）；`ApiResponse` 去掉与 `errorMessage` 重复的 `msg`、新增 `traceId`、时间固定 GMT+8；`ApiExceptionHandler` 继承 `ResponseEntityExceptionHandler`，补齐 Spring MVC 标准异常与兜底；`@Phone`、`IpUtils`、`JsonUtil` 按 Hutool 写法改写；javadoc 报错即失败。消费者升级时需把错误码常量改成新名、删掉子类里与基类重复的异常处理、需要上传的服务自行声明 `spring.servlet.multipart.*`、需要 WebSocket 或 POI 的服务自行声明依赖。

`1.6.9` 是第3阶段 utils 改造（一次发版）：删除 MyBatis-Plus DDL 建表方式（`PostgresqlDdl*` 与公共建表脚本），`api` 带上 Flyway 11.14.1 与其 PostgreSQL 模块，各服务表结构改由自己的 `db/migration` 迁移管理；实体基类按芋道两层，`EntityBase` 只留 `createTime`、`updateTime`、`creator`、`updater`、`deleted`（列 `is_deleted`），`TenantEntityBase` 只多 `tenantId`，`MyMetaObjectHandler` 插入时填创建人、更新人（取当前登录用户，没有就不填），数据权限默认本人字段改为 `creator`；删除自写的 `DynamicDataSourceConfig`，数据源改用 dynamic-datasource 原生配置，`seata` 跟随 `seata.enabled`。消费者升级时需新增 `V1__baseline.sql` 等迁移并删掉 `IDdl` 实现与旧脚本，实体改继承 `TenantEntityBase` 并自行声明 `id`、`version` 等字段，`application.yml` 加数据源与 Flyway 块，Nacos 换成 `spring.datasource.dynamic.datasource.master/gray` 键（URL 不带 `connectTimeout`、`socketTimeout`）。

`1.7.0` 是第3阶段返工（一次发版）：实体基类恢复全部公共字段，`EntityBase` 有 `id`、`code`、`description`、`createTime`、`creator`、`updateTime`、`updater`、`deleted`、`label`、`sorting`、`version`，`TenantEntityBase` 再加 `tenantId`、`createDept`（插入时取当前登录用户的部门），数据权限默认部门列改为 `create_dept`；Seata 固定开启（数据源块 `seata: true`，不再跟随 `seata.enabled`），新增 `EarlyFlywayMigration` 在建数据源之前先按 `spring.flyway.*` 迁移，新空库开着 Seata 也能首次启动；删除阿里云 OSS 专用代码（`com.kellen.aliyun`），上传策略改为 `com.kellen.storage.UploadPolicy`（去掉恒为空的 `securityToken`），展示 token 改为 `DisplayTokenUtils.hash/matches`。消费者升级时删掉实体里基类已有的 `id`、`code`、`description`、`label`、`sorting`、`version` 声明，租户表用新迁移加 `create_dept` 列，`application.yml` 数据源块改为 `seata: true`，并把 `OssTokenUtils`、`OssUploadPolicy` 换成新类。

`1.7.1` 把审计字段改为主流项目的多数命名：`EntityBase` 的 `creator`、`updater` 改为 `createBy`、`updateBy`（列 `create_by`、`update_by`，与 RuoYi-Vue、RuoYi-Vue-Plus、JeecgBoot 一致），数据权限默认本人列改为 `create_by`；规范改为每张新建表都必须包含全部基础字段。消费者升级时用新迁移把 `creator`、`updater` 列改名为 `create_by`、`update_by`，代码里的 `getCreator`、`getUpdater` 改为 `getCreateBy`、`getUpdateBy`。

`1.7.2` 是第4阶段（一次发版）：新增 RocketMQ 消息模块，基于 rocketmq-spring 2.3.6、客户端锁 5.5.1，只在服务配了 `rocketmq.name-server` 时装配——`MqTemplate` 同步发送与事务提交后发送（tag、key、延时、顺序键），收发钩子让租户、操作人与数据源标记随消息传递，消费组与订阅按实例泳道自动隔离；用法见 [消息队列规范](docs/ai-coding/MQ_SPEC.md)。用户、租户、数据源标记、忽略数据权限标记与流量治理快照五个线程上下文不再向新建线程继承，线程池里执行要带上下文的任务须经 `TtlRunnable`、`TtlExecutors` 或 utils 的异步线程池（`TraceContextTaskDecorator`）。另把 RabbitMQ 描述改为 RocketMQ，运维模板与测试文档串里的默认本人列改为 `create_by`（只改注释）。公共 API 只增不改，消费者升级时只改依赖版本。

`2.0.0` 是第5阶段（一次发版，不兼容）：认证换成 Keycloak。服务配了 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 就成为标准资源服务器（只收 RS256、校验签发方与受众，业务上下文经 Dubbo 向 user 服务取并缓存，`X-Tenant-Id` 在用户所属租户里选活动租户）；配了 `spring.security.oauth2.client.registration.*` 就有出站令牌（客户端凭证与 RFC 8693 令牌交换，`FleetOAuth2Clients`）。删除 jjwt、`JwtUtils`、自签令牌过滤器与旧认证开关，HTTP 头里的租户与身份一律不信任。用法见 [认证规范](docs/ai-coding/AUTH_SPEC.md)。

`2.0.1` 给出站取令牌加超时：访问 Keycloak 令牌端点缺省连接 5 秒、读取 10 秒（`fleet.security.oauth2.token-endpoint.*`）。此前 HTTP 客户端库不限读取时间，Keycloak 卡住时取令牌的请求与线程会一直挂着；超时与 Keycloak 拒绝一样按没取到令牌处理，不调用下游。公共 API 只增不改。

`2.0.2` 给 `FleetOAuth2Clients` 加 `bearerToken(注册ID)`：不经 `RestClient`/`WebClient` 的客户端（如 rag 调 RAG 引擎用的 JDK `HttpClient`）按同样的主体与缓存规则取访问令牌，没取到就报错，即 Spring Security 文档里直接使用 `OAuth2AuthorizedClientManager` 的写法。公共 API 只增不改。

`2.0.3` 补全受保护资源元数据（RFC 9728）：Spring Security 7 在每条资源服务器链上发布 `/.well-known/oauth-protected-resource`（资源标识按取元数据的地址推出，401 质询的 `resource_metadata` 指向它），此前元数据里没有授权服务器，客户端无从发现去哪里取令牌；现在默认链声明授权服务器为 `issuer-uri`（Keycloak 域地址）。mcp 按 MCP 授权规范靠它让客户端发现 Keycloak。公共 API 不变。

`2.0.4` 修复业务上下文的 Dubbo 引用在消费服务里建不出来：引用原先写成资源服务器自动配置里的嵌套配置，消费服务的主类在 `com.kellen` 包、默认组件扫描覆盖 utils，嵌套配置被当成普通配置提前处理，早于 `@EnableDubbo` 登记引用注解处理器，条件判定不成立，除 user 外的服务把所有用户令牌按“用户不存在”拒绝（401）。现在由独立的 `FleetSecurityContextDubboAutoConfiguration`（排在 Dubbo 自动配置之后）创建；测试按消费服务的接线（`@EnableDubbo` 加组件扫描）复核，并约束 utils 登记的自动配置及其嵌套类都不是组件扫描的候选（Spring Boot 要求自动配置只由 imports 文件加载）。公共 API 不变。

`2.0.5` 让业务上下文的 Dubbo 引用与各服务既有引用一样在启动时不初始化（`init = false`）：2.0.4 的引用沿用 Dubbo 默认在启动时初始化全部引用，没配注册中心的完整上下文测试（ai、ai-agent 的测试启动类）因此起不来（No such any registry to reference）；现在第一次需要业务上下文的请求才初始化并连接，初始化失败与调用失败一样按 user 服务不可用返回 503。测试覆盖不配注册中心时上下文照常启动、首个请求 503，以及本进程有提供方时首个请求才初始化并放行；user 服务里用不到的那个自引用也不再在启动时初始化。公共 API 不变。

`2.0.6` 落实设计里的“受众默认 github-api”：服务没配 `spring.security.oauth2.resourceserver.jwt.audiences` 时，只收受众含 `github-api` 的令牌（`fleetDefaultAudienceValidator`，Spring Boot 按 issuer-uri 建解码器时装上容器里的 `OAuth2TokenValidator`），漏配不再变成不校验受众、接受本域发给别的服务的令牌；配了就照旧由 Spring Boot 校验。运维的 `verify_configuration` 同时要求配了签发方的模板显式写受众。第5阶段整体复查发现。公共 API 只增不改。

`2.0.7` 新增 `com.kellen.security.TenantBoundary`：接口参数或请求体里点名的 `tenantId` 只能等于当前用户的活动租户（资源服务器按 `X-Tenant-Id` 经 user 核实后写入 `UserContextHolder`），平台超级管理员（`ROLE_PLATFORM_SUPER_ADMIN`）可以点名任一租户；不满足时抛 `AccessDeniedException`，回 403。服务在把请求里的租户写进 `TenantContextHolder` 之前先调 `TenantBoundary.require(tenantId)`。做法同芋道 `TenantSecurityWebFilter`（登录用户的租户与请求租户不一致即拒绝），依据 OWASP API Security Top 10 2023 的 API1。起因是第 5 阶段最终复核发现 user 与 message 的管理接口用请求里的 tenantId 覆盖已核实的活动租户。

`2.0.8` 收掉第 5 阶段推迟的资源服务器小问题（依据见 `docs/ai-coding/AUTH_SPEC.md`）：令牌有效但 user 里没有该用户时由 401 改 403「账号未开通」（RFC 6750 §3.1，避免网关 BFF 反复重新登录）；`SecurityUser` 新增 `idpSubject`，资源服务器核对 `user_id` 属于令牌的 `sub`（OIDC Core §5.7），不一致回 403；新增 `FleetIssuedAtValidator`，`iat` 必须有且不能晚于当前时间 60 秒以上（口径同 PyJWT）；没有业务上下文提供方时启动失败，并由启动失败分析说明做法；业务上下文的 Redis 副本由 30 分钟改为 5 分钟，版本自增失败的日志写出真实的失效上限。新增 Gradle 测试夹具 `FakeIssuer`（分类器 `test-fixtures`），消费者不再各自复制。公共 API 只增不改：`SecurityUser` 保留 9 个参数的构造器，`SecurityContextResult.Status` 新增只在本地判定的 `SUBJECT_MISMATCH`。

`2.0.9` 是第6阶段第三部分（不兼容）：实体基类的排序字段 `sorting` 改名 `sort`，Java 访问方法随之变为 `getSort()`、`setSort()`，对应列名 `sort`。消费方升级到 2.0.9 时，同一批上线里要用 Flyway 把各表的 `sorting` 列改名为 `sort`（`ALTER TABLE … RENAME COLUMN sorting TO sort`），查询白名单和排序字段里的 `sorting` 一并改为 `sort`，前端同批改字段；只升依赖不改列名，MyBatis-Plus 读写 `sort` 会报列不存在。做法按《Refactoring Databases》的 Rename Column：每张表只由一个服务读写、前后端同批发布，所以直接改名，不留过渡列。没升级的服务继续用 2.0.8，不受影响。回退：消费方的 Flyway 改名迁移是在服务启动时执行的，没有改名前的备份，回退要反向改名——在一个事务里把各表的 `sort` 改回 `sorting`、删掉对应的 `flyway_schema_history` 记录、确认 `undo_log` 为空，再把 utils 换回 2.0.8、各服务换回上一版，各服务 CI 固定的 utils 检出提交一并退回（逐库做法见 user、message、ai、ai-memory 的 README）。
