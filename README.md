# utils

`utils` 是面向 Spring Boot 3 / Java 17 的公共基础能力包，是 `user` 等业务服务复用通用能力的唯一来源。业务项目和网关项目不要复制本仓库里的工具类源码；需要公共能力时，先在本仓库维护并发布 Maven 制品，再让消费者项目升级依赖。

当前提供统一响应、错误码、认证上下文、多租户、MyBatis-Plus 基础配置、AOP 通用能力、请求日志、Redis/Redisson、HTTP、JSON、文件、Excel、PDF、阿里云、钉钉、微信等通用工具。

## 当前定位

- 发布坐标：`com:utils:1.1.9`
- Java 版本：`17`
- Spring Boot：`3.2.4`
- Servlet / Validation 包名：统一使用 `jakarta.*`
- 统一响应：只使用 `com.kellen.utils.response.ApiResponse`
- 统一错误码：只使用 `com.kellen.utils.enumeration.ReturnCode`
- 全局异常处理：使用 `com.kellen.utils.exception.ApiExceptionHandler`
- 认证上下文：使用 `SecurityUser` 与 `UserContextHolder`
- 租户上下文：使用 `TenantContextHolder`
- 动态数据源：只保留 `master` 与 `gray`
- 乐观锁：`EntityBase.version` 只表示 MyBatis-Plus `@Version` 数据库版本号

## 仓库边界

- `utils`：公共工具、公共配置、公共注解、公共异常、公共上下文和可被多个服务复用的第三方封装。
- `user`：用户、认证、租户、权限、账号等业务实现，依赖 `com:utils:1.1.9`。
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

## 远程发布

阿里云 Maven 仓库凭证不要写入仓库文件，放到本机 `~/.gradle/gradle.properties`：

```properties
aliyunMavenUsername=你的用户名
aliyunMavenPassword=你的密码
```

发布到阿里云 Maven 仓库：

```bash
./gradlew publish
```

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
- 不把网关路由、Nacos 配置、SLS/Logback 本地配置放进本仓库。
- AI 新增或修改 Java 代码时，每一行新增或修改内容都要补充注释，说明该行的用途、业务含义或安全边界。
- 新增 Java 类必须补齐类注释、字段注释、方法 JavaDoc、关键逻辑逐行注释。
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
5. 发布前执行 `./gradlew publishToMavenLocal`，远程发布只通过 `./gradlew publish`，凭证放本机 Gradle 配置或环境变量。
