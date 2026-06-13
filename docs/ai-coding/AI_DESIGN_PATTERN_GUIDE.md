# AI 设计模式规范

本规范约束 AI 在 `utils` 公共基础包中选择、引入和调整设计模式的方式。目标是稳定公共 API、降低消费者误用风险，而不是把业务语义塞进公共包。

## 1. 总原则

- 先判断能力归属：多项目通用能力才进入 `utils`，单个业务服务专用逻辑留在业务项目。
- 优先沿用现有公共包结构、命名和消费者契约。
- 新增模式必须保护公共 API 兼容性、认证/租户上下文、统一响应、异常、MyBatis-Plus 插件和工具类归类。
- 不允许为了单个消费者临时需求破坏公共职责。
- 公共抽象必须可测试、可文档化、可被 `user`、`message` 等消费者稳定依赖。

## 2. 标准参考

- GoF 设计模式：Strategy、Adapter、Factory、Template Method、Decorator、Builder 等。
- SOLID 原则：尤其是接口隔离、依赖倒置和开闭原则。
- GRASP 原则：判断公共能力职责归属。
- Martin Fowler 企业应用模式：Service Layer、Repository、Unit of Work、DTO、Gateway 等结构语言。
- Spring Boot 官方惯例：自动配置、条件装配、配置属性、Bean 生命周期。
- MyBatis-Plus 官方扩展点：Interceptor、MetaObjectHandler、SqlInjector、Wrapper 等。

## 3. 本项目推荐模式

### Auto Configuration / Configuration Properties

适用 Spring Boot 公共配置。

- 公共配置优先使用类型安全配置属性。
- 自动配置必须可关闭、可覆盖、默认安全。
- 不把消费者业务默认值硬编码进公共包。

### Strategy

适用数据权限、脱敏、编码、文件校验、第三方 SDK 通道等可替换规则。

- 多种实现时使用小接口和清晰命名。
- 默认策略必须安全，失败时不放宽权限。
- 策略不能依赖单个业务服务的私有枚举或业务字段。

### Adapter

适用 Redis、HTTP、JSON、文件、Excel、PDF、第三方 SDK 封装。

- 外部库差异、异常码、返回结构封装在 Adapter 中。
- 对外暴露稳定、脱敏、可测试的公共方法。
- 不把第三方 SDK 密钥、账号或业务配置写死。

### Template Method / Pipeline

适用请求日志、幂等、防重复提交、导入导出、文件校验等稳定流程。

- 公共流程稳定后才抽模板。
- 钩子方法必须命名清晰，默认实现安全。
- 不用继承掩盖消费者业务差异；组合优先。

### Decorator / Interceptor

适用 MyBatis-Plus、Web、日志、脱敏和安全增强。

- 拦截器必须默认失败关闭，不绕过参数绑定、租户隔离或数据权限。
- 不得把 `SqlInjector` 描述成 SQL 注入防护，它只是 Mapper 方法扩展点。
- 新增拦截器必须说明顺序、影响范围和消费者验证方式。

### Factory / Builder

适用复杂公共对象、SDK 客户端和配置化构造。

- Factory 不隐藏密钥来源和配置校验。
- Builder 不隐藏网络调用、文件写入或数据库副作用。
- 简单工具方法无需强制 Factory。

## 4. 谨慎或禁止使用

- 手写 Singleton：Spring Bean 已管理生命周期。
- Service Locator：公共包依赖必须显式。
- 巨型 Utils：不要新增包罗万象的 `CommonUtils`、`ObjectUtils2`、`NewUtils`。
- 业务枚举、业务公式、业务常量进入公共包。
- 全局静态可变上下文；认证和租户上下文必须可设置、可清理、可测试。
- 破坏性 public API 重命名；必须评估并验证消费者。
- 模式先行重构：没有公共复用价值时不抽公共模式。

## 5. 消费者影响要求

修改以下内容必须评估 `../user`、`../message` 等消费者：

- public 类、方法、字段、注解、异常、枚举。
- 认证上下文、租户上下文、数据权限、MyBatis-Plus 插件。
- 统一响应、错误码、日志脱敏、文件/HTTP/Redis/JSON 工具。
- 自动配置、starter 行为、默认 Bean。

## 6. 检查清单

- 该能力是否确实被两个以上项目复用，或明显属于公共基础能力？
- 是否避免把业务语义、业务枚举、业务流程放进公共包？
- 是否保持 public API 兼容，或明确了破坏性变更和迁移方式？
- 是否使用合适的 Strategy、Adapter、Interceptor、Template、Factory，而非巨型工具类？
- 是否默认安全：失败关闭、脱敏、参数绑定、租户和数据权限不放宽？
- 是否验证 `./gradlew clean build -x test`、`publishToMavenLocal` 和必要消费者编译？
