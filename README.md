# utils

`utils` 是面向 Spring Boot 4 / Java 17 的公共基础能力包，是 `user`、`message` 等业务服务复用通用能力的唯一来源。业务项目和网关项目不要复制本仓库里的工具类源码；需要公共能力时，先在本仓库维护并发布 Maven 制品，再让消费者项目升级依赖。

当前提供统一响应、错误码、认证上下文、多租户、MyBatis-Plus 基础配置、AOP 通用能力、请求日志、Redis/Redisson、HTTP、JSON、文件、Excel、PDF、阿里云、钉钉、微信等通用工具。

## 当前定位

- 发布坐标：`com:utils:1.6.2`
- Java 版本：`17`
- Spring Boot：`4.0.4`
- Spring Cloud：`2025.1.1`
- Spring Cloud Alibaba：`2025.1.0.0`
- Nacos Client：`3.2.2`
- Seata Server：`2.7.0`
- Seata Client：`2.6.0`
- Servlet / Validation 包名：统一使用 `jakarta.*`
- 统一响应：只使用 `com.kellen.utils.response.ApiResponse`
- 统一错误码：只使用 `com.kellen.utils.enumeration.ReturnCode`
- 全局异常处理：使用 `com.kellen.utils.exception.ApiExceptionHandler`
- 认证上下文：使用 `SecurityUser` 与 `UserContextHolder`
- 租户上下文：使用 `TenantContextHolder`
- 动态数据源：只保留 `master` 与 `gray`
- 个人业务库迁移目标为 PostgreSQL；`postgresql` / `postgresql-gray` 配置、字符串租户与 Boolean 逻辑删除、独立测试库及切换边界见 [PG 底座说明](docs/postgresql-fleet-foundation.md)。历史 MySQL 仍供其它项目或必要中间件使用，本轮不删除其数据库、账号或容器。
- 数据源连接健康：默认借出前执行 `SELECT 1` 校验，并启用空闲检测与保活；保活间隔默认 60 秒且会自动归一化为严格大于空闲检测间隔，避免数据库空闲超时后的首次请求命中失效连接或 Druid 因非法时间组合启动失败
- 乐观锁：`EntityBase.version` 只表示 MyBatis-Plus `@Version` 数据库版本号
- JSON：`JsonUtil` 使用 Spring Boot 4 默认 Jackson 3 databind API；`json/bean/list` 方法签名保持兼容

### Jackson 3 迁移说明

- `JsonUtil.getJsonMapper()` 的返回类型由 Jackson 2
  `com.fasterxml.jackson.databind.ObjectMapper` 调整为 Jackson 3
  `tools.jackson.databind.ObjectMapper`，直接调用该方法的消费者必须重新编译。
- `JsonUtil.json(...)`、`bean(...)`、`list(...)` 的方法签名及空值、日期、
  单引号、未知字段和高精度小数规则保持不变。
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

- `ActuatorSecurityConfig` 为已暴露的 Actuator 端点建立独立安全链，与 `security.auth.enabled` 无关。health 及探针保持公开；其它端点需要 `actuatorReq` 请求头匹配运行时 `security.actuator.token`，配置缺失时拒绝访问。令牌每次请求重新读取，轮换无需重启。旧 MVC `ActuatorInterceptor` 已停用，不能再注册为保护措施。
- JWT 的 create/parse 静态 API 保留，由 `JwtKeyConfig` 从当前 Spring `Environment` 读取 `security.jwt.secret`；必须为 Base64 且解码后至少 32 字节，未初始化、缺值、格式错误或弱密钥均拒绝签发和解析，不使用历史固定口令。`security.jwt.issuer` 控制默认 issuer，显式传入的 OAuth issuer 保持原值。个人与其它环境使用独立随机密钥，切换密钥会使旧 token 失效。
- MVC 租户上下文优先采用已认证 JWT/OAuth 的 `SecurityUser`，不能被 `tenantId`、`tenant-id` 或 `X-Tenant-Id` HTTP 头覆盖。已认证但无租户的请求返回 403；公开登录入口不从匿名头建立租户，由业务验证登录参数。`tenant.ignore-without-tenant=false` 防止 SQL 层缺上下文时跳过租户条件；Dubbo/MQ 的既有租户头契约保留。
- 文件存储统一入口是 `com.kellen.storage.PrivateObjectStorageService`，使用 X File Storage `2.3.0` 管理 OSS / MinIO 平台与客户端。MinIO SDK 固定 `8.6.0`，保留兼容的 8.x API 并包含 XML 值替换漏洞修复。
- 配置使用官方 `dromara.x-file-storage.default-platform`、`minio[]` 或 `aliyun-oss[]`。平台项需要 `platform`、`enable-storage: true`、`end-point`、`bucket-name`、`access-key`、`secret-key`；`base-path` 必须为空，由业务 `baseDir` 形成对象键。凭据只从环境或个人 Nacos namespace 注入，桶应为私有。
- `createUploadPolicy(...)` 保持 POST 表单上传；`OssUploadPolicy.formFields` 是完整表单字段，新客户端原样提交。原 OSS 字段和十参数构造器保留，MinIO 使用 S3 V4 签名字段。X File Storage 尚无 POST policy 统一 API，此处通过它管理的同一个客户端做协议适配。
- `verifyObjectExists(platform, key)`、`signedViewUri(platform, key, expiry)` 支持读取持久化平台；调用方必须先完成所有者和展示 token 校验，不能把此通用门面当成授权服务。历史文件保留原平台，切换默认平台不会迁移已有对象。
- 未使用 OSS、短信、钉钉、直播的服务不再需要这些可选配置的占位值；调用对应功能时仍须提供真实运行配置。

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
- 业务库公共基础脚本源头为 `src/main/resources/db/postgresql/common-infra-schema.sql`；接入 MyBatis-Plus DDL 与 Seata AT 的消费者服务首次启动空库前，必须在目标业务库手动执行该脚本，先建 `ddl_history` 和 `undo_log`。
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
