# utils 项目编码规范

## 基础规则

- 项目面向 Spring Boot 3 / Java 17，新增代码必须使用 `jakarta.*`，不得引入 `javax.*`。
- 公共类、配置类、注解、枚举、异常类必须保留稳定包名，修改前先检查消费者项目是否直接依赖。
- 新增 Java 代码必须补齐类注释、字段注释、方法 JavaDoc、关键逻辑注释。
- 关键逻辑包括认证、租户、权限、Redis、动态数据源、乐观锁、SQL 参数校验、事务、异常处理、返回值组装。
- 注释解释业务意图和边界，不写“给变量赋值”这类无效注释。

## 命名规范

- public 类型使用 PascalCase，文件名必须与 public 类型名完全一致。
- 工具类使用 `XxxUtils`，例如 `JwtUtils`、`OkHttpUtils`。
- 配置类使用 `XxxConfig`，例如 `DynamicDataSourceConfig`、`MyBatisPlusConfig`。
- AOP 切面使用业务语义命名，例如 `RequestRequiredAspect`。
- 缩写保持项目当前写法：`JwtUtils`、`OkHttpUtils`、`MyBatisPlusConfig`、`ApiResponse`。
- 不新增含糊类名，例如 `TestUtil`、`CommonUtil`、`BaseUtil2`。

## 统一响应

- Controller 或全局异常处理统一返回 `ApiResponse<T>`。
- 成功返回使用 `ApiResponse.success()` 或 `ApiResponse.success(data)`。
- 失败返回使用 `ApiResponse.fail(returnCode)` 或 `ApiResponse.fail(returnCode, error)`。
- 响应字段统一为 `success`、`code`、`msg`、`data`、`errorMessage`、`timestamp`。
- 不得恢复或新增旧 `Json` 统一返回对象。

## 错误码与异常

- 业务错误码统一使用 `ReturnCode`。
- 全局异常处理统一放在 `ApiExceptionHandler`。
- 业务异常应携带稳定错误码，不直接把内部堆栈、SQL、密钥、token 暴露给调用方。
- 捕获异常时使用日志框架记录上下文，不使用 `printStackTrace()`。

## 认证与用户上下文

- 当前认证来源是 JWT 或网关透传身份头，最终写入 `SecurityUser`。
- 业务代码读取当前用户必须通过 `UserContextHolder.get()`。
- 请求结束后必须清理 `UserContextHolder`，避免线程复用导致身份串号。
- 不得恢复旧 `token` 请求头认证。
- 不得通过 Redis token 用户对象作为授权来源。

## 租户与动态数据源

- 当前租户上下文统一使用 `TenantContextHolder`。
- 租户 ID 应从认证用户或可信请求上下文写入，不从不可信任参数直接决定数据隔离。
- 请求结束后必须清理 `TenantContextHolder`。
- 动态数据源只允许 `DynamicSourceTtl.MASTER_DATASOURCE` 与 `DynamicSourceTtl.SLAVE_DATASOURCE`。
- `MASTER_DATASOURCE` 的值为 `master`，`SLAVE_DATASOURCE` 的值为 `gray`。
- 不得新增或恢复 `bank`、`hz`、`hx`、`jghx` 等历史数据源。

## MyBatis-Plus

- 实体基础字段统一继承 `EntityBase`。
- `EntityBase.version` 只表示 MyBatis-Plus 乐观锁字段。
- 乐观锁由 `MyBatisPlusConfig` 注册 `OptimisticLockerInnerInterceptor`。
- 更新接口需要提交查询时拿到的当前 `version`。
- 更新建议使用 `updateById(entity)`，避免只按 id 的 `UpdateWrapper` 绕过乐观锁。

## AOP 与幂等

- 请求切面统一由 `RequestRequiredAspect` 承载。
- 切面当前负责 SQL 参数校验、动态数据源上下文、请求日志、幂等锁生命周期。
- 幂等锁由 `PreventRepeatInit` 处理。
- 幂等 key 应包含租户、用户、类名、方法名，避免跨租户或跨用户互相影响。
- 异常路径必须释放幂等锁并清理线程上下文。

## 工具类扩展

- 新工具类优先放入现有语义包，例如 `utils/email`、`utils/excel`、`utils/redisson`、`aliyun/*`。
- 只有跨多个业务项目复用、且没有业务私有语义的能力，才放入 `utils`。
- 业务枚举、业务公式、业务专属常量应优先留在业务项目，不继续扩大公共包污染面。
- 新增第三方 SDK 封装时，应把配置对象、调用工具、异常处理和日志边界拆清楚。

## 验证要求

修改 `utils` 后至少执行：

```bash
./gradlew clean build -x test
./gradlew publishToMavenLocal
```

如果修改公共 API、认证、异常、响应、租户、动态数据源或 MyBatis-Plus 配置，还需要编译消费者项目：

```bash
cd /Users/sunkailun/Desktop/个人/GitHub/user
./gradlew clean compileJava -x test
```
