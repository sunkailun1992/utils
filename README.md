# utils

`utils` 是面向 Spring Boot 3 / Java 17 的公共基础能力包，提供统一响应、错误码、认证上下文、多租户、MyBatis-Plus 基础配置、AOP 通用能力、请求日志、Redis/Redisson、HTTP、JSON、文件、Excel、PDF、阿里云、钉钉、微信等通用工具。

## 当前定位

- 发布坐标：`com:utils:1.0.9`
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
cd /Users/sunkailun/Desktop/个人/GitHub/user
./gradlew clean compileJava -x test
```

## 重要约束

- 不再使用旧 `Json` 统一返回对象。
- 不再通过旧 `token` 请求头或 Redis token 用户模式作为认证授权来源。
- 不再把 HTTP 请求头里的 `version` 当作业务版本校验。
- 不再新增 `bank`、`hz`、`hx`、`jghx` 等动态数据源常量或配置。
- 新增 Java 类必须补齐类注释、字段注释、方法 JavaDoc、关键逻辑注释。
- 新增公共类型必须遵守 PascalCase 命名，文件名必须与 public 类型名一致。

## AI 阅读入口

- [AI 编码入口](docs/ai-coding/README.md)
- [项目编码规范](docs/ai-coding/PROJECT_CODING_SPEC.md)
- [工具类归类目录](docs/ai-coding/UTILS_TOOL_CATALOG.md)
- [包结构整理建议](docs/ai-coding/PACKAGE_REFACTOR_GUIDE.md)
