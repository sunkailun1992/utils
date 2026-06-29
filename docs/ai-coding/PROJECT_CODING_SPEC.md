# utils 项目编码规范

## 基础规则

- 项目面向 Spring Boot 4 / Java 17，新增代码必须使用 `jakarta.*`，不得引入 `javax.*`。
- 公共类、配置类、注解、枚举、异常类必须保留稳定包名，修改前先检查消费者项目是否直接依赖。
- AI 新增或修改 Java 代码时，必须补充能解释公共职责、消费者影响和安全边界的有效注释，不采用机械逐行注释。
- 新增 Java 代码必须补齐类注释、字段注释、方法 JavaDoc，并在关键逻辑块前说明为什么这样实现。
- 关键逻辑包括认证、租户、权限、Redis、动态数据源、乐观锁、SQL 参数校验、事务、异常处理、返回值组装。
- 注释解释业务意图、公共 API 影响和安全边界，不写“给变量赋值”这类无效注释。
- 局部改造历史代码时，只补本次改动和直接相关逻辑的说明，不借机大面积重写无关历史代码。

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
- 退出登录、密码变更、用户禁用、刷新 token 失败等认证状态变化必须清理或失效 token、登录态缓存和线程上下文。
- 不得恢复旧 `token` 请求头认证。
- 不得通过 Redis token 用户对象作为授权来源。

## 安全规则

- 安全细则独立维护在 `SECURITY_CODING_SPEC.md`。
- 新增或修改认证、权限、脱敏、水平越权、文件遍历、退出清理 token、XSS 跨站脚本、SQL 注入、文件上传校验相关代码时，必须先阅读安全规范。
- 公共工具类不得提供绕过接口鉴权、租户隔离、数据归属校验、参数绑定、文件路径校验或上传校验的便捷方法。
- 公共异常、日志、响应、导出、通知和审计能力必须默认执行敏感信息保护，不得输出密码、token、密钥、验证码、完整身份证、完整手机号、完整邮箱、内部地址和堆栈。

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
- 业务微服务新增业务表时，DDL 默认包含 `code`、`description`、`create_date_time`、`create_name`、`modify_date_time`、`modify_name`、`is_delete`、`type`、`state`、`label`、`sorting`、`version`、`tenant_id` 等公共治理字段，不得只写业务字段。
- `type/state` 是 DDL 默认治理/状态字段，但不放入 `EntityBase`。业务代码需要读写时由当前实体自行声明并配套 `IEnum` 或受控枚举；不需要读写时依赖数据库默认值。
- 数据库列 `version` 只表示 MyBatis-Plus 乐观锁。业务版本、模板版本、协议版本、Prompt 版本必须使用明确列名，例如 `template_version`、`prompt_version`、`protocol_version`，不得复用 `version`。
- `src/main/resources/db/common-infra-schema.sql` 是业务库公共基础脚本源头，维护 `ddl_history` 与 Seata AT `undo_log`。消费者服务全新或空业务库首次启动前，必须在目标业务库手动执行该脚本；Seata AT 会在 `DataSource` 初始化时先检查 `undo_log`，不能依赖 MyBatis-Plus DDL 首次启动自动创建。
- 乐观锁由 `MyBatisPlusConfig` 注册 `OptimisticLockerInnerInterceptor`。
- 更新接口需要提交查询时拿到的当前 `version`。
- 更新建议使用 `updateById(entity)`，避免只按 id 的 `UpdateWrapper` 绕过乐观锁。
- SQL 安全由 `MyBatisPlusConfig` 默认注册 `IllegalSQLInnerInterceptor` 和 `BlockAttackInnerInterceptor`，消费者项目不得无说明关闭。
- 如确需关闭非法 SQL 或防全表更新删除插件，必须在业务配置、变更说明和测试用例中说明原因、影响范围和替代防护。
- 动态排序字段、动态 select 字段、动态表名、导出字段和搜索字段必须先做后端白名单，不得直接使用前端传入值。
- `SqlInjectionUtils.check(...)` 和 Wrapper 的 `checkSqlInjection()` 只能作为补充校验，不能替代字段白名单和参数绑定。
- MyBatis-Plus `SqlInjector` 是自定义通用 Mapper 方法的扩展点，不是 SQL 注入防护能力。
- 新增 `SqlInjector`、`AbstractMethod` 或自定义批量 SQL 方法时，SQL 片段只能来自实体元数据、后端常量或白名单，不得来自请求参数。
- 优先使用 `LambdaQueryWrapper`、`LambdaUpdateWrapper`、`#{}` 参数绑定和 Mapper 方法参数，避免手写字符串列名和 XML `${}`。
- `DataPermissionInterceptor` 用于数据范围控制，不能替代接口鉴权、租户隔离和业务归属校验。
- `TenantLineInnerInterceptor` 只处理 SQL 租户条件，租户上下文仍必须来自可信认证或网关上下文，不能信任前端普通参数。

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

## 路径与本机环境规范

- README、AI 规范、YAML、properties、SQL、脚本、测试、示例和 Java 代码中不得写入个人电脑绝对路径、下载目录、IDE 路径、JDK 安装路径或本机仓库完整路径。
- 需要描述同级仓库时，使用 `../user`、`../message`、`../gateway` 这类相对路径，不使用开发者机器上的完整目录。
- 需要描述可变安装目录、日志目录、上传目录、导出目录、临时目录或 JDK 路径时，使用环境变量、消费者项目配置、`~` 用户目录、`${user.home}`、`${java.io.tmpdir}` 或 `<PLACEHOLDER>` 占位符。
- 公共工具类不得保留本地调试用绝对路径；需要演示文件能力时，使用单元测试临时目录或文档占位符。
- 提交前必须使用 `rg` 搜索本机用户名、用户目录、仓库根目录和系统盘路径关键字，检查是否残留本机路径。

## 公共 examples 同步规范

- `docs/ai-coding/examples` 是业务微服务 Java 分层示例模板的公共源头。
- `user`、`message` 等业务微服务可以保留 `docs/ai-coding/examples` 本地副本，方便 AI 在单项目上下文中直接阅读。
- 公共 `Example*` 模板只允许先在 `utils` 修改，再同步到业务微服务副本，禁止业务微服务单独长期分叉公共模板。
- 业务项目确实需要专属示例时，必须另建 `docs/ai-coding/project-examples` 或 `docs/ai-coding/business-examples`，不要污染公共 `Example*` 模板。
- 同步公共 examples 后，必须检查业务项目 `docs/ai-coding/README.md` 和 `PROJECT_CODING_SPEC.md` 是否仍说明本地 examples 是同步副本。

## 版本与发布规范

- 只要本次任务修改生产代码、配置、构建脚本或公共示例，就必须提升一次 `build.gradle` 中的 `version`。
- 同一批未提交改动只提升一次版本号；提交前继续补代码、补测试、补文档或修编译错误时，不得再次提升版本号。
- 纯 README、AI 规范、注释或说明文档改动不强制提升制品版本；如果同一任务同时改了代码或构建配置，则仍按代码改动规则提升一次。
- 版本提升后必须评估消费者项目是否需要同步升级 `com:utils:<version>`，并按影响范围执行消费者编译验证。
- 非 `main` 分支或未提交状态默认只允许发布到 Maven Local，用于消费者项目本地验证。
- 从 `main` 分支开始提交并推送 Git 仓库时，必须同步执行 `./gradlew publish`，把同版本制品推送到远程私有 Maven 仓库。
- Git 推送成功但远程私有 Maven 发布失败时，交付说明必须明确失败原因和补救命令，不能把发布链路描述为完成。

## 验证要求

修改 `utils` 后至少执行：

```bash
./gradlew clean build -x test
./gradlew publishToMavenLocal
```

如果修改公共 API、认证、异常、响应、租户、动态数据源或 MyBatis-Plus 配置，还需要编译消费者项目：

```bash
cd ../user
./gradlew clean compileJava -x test
```
