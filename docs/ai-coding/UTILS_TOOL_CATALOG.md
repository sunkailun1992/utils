# utils 工具类归类目录

本文件按能力域整理 `utils` 现有工具类和配置类，方便 AI 或开发者快速判断应该复用、调整或继续收敛哪些类。

## 一、统一响应与错误处理

核心类：

- `com.kellen.utils.response.ApiResponse`：统一响应对象，字段为 `success`、`code`、`data`、`errorMessage`、`traceId`、`timestamp`；`traceId` 取当前 OTel 链路（与响应头同值），没有时不输出；`timestamp` 固定按 GMT+8 取值；`fail(...)` 为泛型，可直接用于有类型的接口。
- `com.kellen.utils.enumeration.ReturnCode`：统一错误码枚举。编码按阿里巴巴 Java 开发手册分 A/B/C 三类，成功为 `"0"`；常量为英文大写下划线（如 `SUCCESS`、`USER_PASSWORD_ERROR`），属性为 `getCode()`、`getMessage()`；编码与文案一经发布不再修改，`ReturnCodeTest` 按快照逐项比对。
- `com.kellen.utils.exception.ApiExceptionHandler`：全局异常处理基类，继承 Spring 的 `ResponseEntityExceptionHandler`，服务用 `@RestControllerAdvice` 子类启用。
- `com.kellen.utils.exception.BusinessException`：业务异常。
- `com.kellen.utils.exception.RpcException`：RPC 调用异常。
- `com.kellen.utils.exception.UserException`：携带 `ReturnCode` 的用户异常。

使用原则：

- 新接口统一返回 `ApiResponse`。
- 新业务错误统一选择 `ReturnCode`，不要新增中文或拼音常量。
- 不恢复旧 `Json` 返回对象。
- 异常处理统一进入 `ApiExceptionHandler`，避免 Controller 重复拼失败响应。
- HTTP 状态码约定：业务与参数校验异常回 200 加 `success=false`；Spring MVC 标准异常保留 Spring 的状态码（400 回 `A0400`，其他 4xx 回 `A0001`）；未知异常回 500 加 `B0001`；Spring Security 的鉴权异常原样抛出，由 Spring Security 回 401 或 403。
- 子类不要再为 `Exception`、`HttpMessageNotReadableException` 等基类已处理的异常写处理方法，否则 Spring 启动时报 `Ambiguous @ExceptionHandler`。
- SQL 与 RPC 异常只返回错误码默认文案，底层报错原文不交给客户端。

## 二、认证、安全与上下文

核心类：

- `com.kellen.security.oauth2.FleetResourceServerAutoConfiguration`：OAuth2 资源服务器自动配置（配了 issuer-uri 的服务自动接入），配置前缀 `fleet.security.oauth2`（`FleetOAuth2Properties`），详见 [认证规范](AUTH_SPEC.md)。
- `com.kellen.security.oauth2.SecurityContextRpcService` / `SecurityContextResult`：按 `user_id` 加载业务上下文的 Dubbo 契约，由 user 服务提供。
- `com.kellen.security.oauth2.SecurityUserCache`：业务上下文两级缓存（Caffeine → Redis 版本分键），user 服务改动用户后调用 `bumpVersion`。
- `com.kellen.security.oauth2.FleetAuthenticationToken` / `MachineAuthenticationToken`：用户令牌与机器令牌的认证结果。
- `com.kellen.security.oauth2.client.FleetOAuth2Clients`：出站令牌（客户端凭证、RFC 8693 令牌交换）的 `RestClient` 拦截器与 `WebClient` 过滤器。
- `com.kellen.security.config.ActuatorSecurityConfig`：独立 Actuator 安全链，health 公开，其余端点使用外置 `security.actuator.token` 与 `actuatorReq` 校验，缺失时失败关闭。
- `com.kellen.security.SecurityUser`：当前登录用户模型。
- `com.kellen.security.UserContextHolder`：用户上下文（TransmittableThreadLocal）。

使用原则：

- 业务代码通过 `UserContextHolder.get()` 读取当前用户。
- 请求结束必须清理用户上下文。
- 身份只来自 Keycloak 访问令牌；自签 JWT（jjwt、`JwtUtils`）与旧 Redis token 用户模式已在第5阶段删除。
- 密钥、token、签名串不得写入普通业务日志。

## 三、租户、数据源与 MyBatis-Plus

核心类：

- `com.kellen.utils.context.TenantContextHolder`：租户上下文（TransmittableThreadLocal）。
- `com.kellen.security.TenantBoundary`：请求点名的目标租户与活动租户的边界（2.0.7 起）；`require(tenantId)` 只放行自己的活动租户，平台超级管理员可点名任一租户，否则 403。
- `com.kellen.security.config.TenantProperties`：租户请求头配置。
- `com.kellen.config.dubbo.DubboContextPropagationFilter`：Dubbo RPC 上下文透传。
- `com.kellen.config.web.ReqInterceptor`：HTTP 请求租户上下文初始化。
- `com.kellen.utils.context.DynamicSourceTtl`：动态数据源上下文，`peek()` 只读不补位。
- 数据源不再由 utils 自写配置：各服务用 dynamic-datasource 原生 `spring.datasource.dynamic.*`，`application.yml` 放连接池与 Seata 参数，Nacos 放 master、gray 地址与账号；`seata` 固定 `true`，由 dynamic-datasource 按 AT 包装 Seata 代理。
- Flyway：utils 以 `api` 带上 `spring-boot-starter-flyway` 与 `flyway-database-postgresql`，不放迁移；MyBatis-Plus 的顺序探测器让 `SqlSessionTemplate` 等 Flyway 迁移完成。`config.flyway.EarlyFlywayMigration` 在创建任何 Bean 之前先按 `spring.flyway.*` 迁移，数据源包 Seata 代理时库里已有 `undo_log`。
- `com.kellen.config.mybatis.MyBatisPlusConfig`：MyBatis-Plus 插件配置。
- `com.kellen.config.mybatis.MybatisPlusSecurityProperties`：MyBatis-Plus SQL 安全插件开关配置。
- `com.kellen.config.mybatis.MyMetaObjectHandler`：MyBatis-Plus 自动填充处理器；插入填创建时间、更新时间、创建人、更新人、租户与创建部门，更新刷新更新时间与更新人；没有登录用户时不填人和部门，已有值不覆盖。
- `com.kellen.datapermission.DataPermissionSqlHandler`：数据权限条件，直接构造 JSqlParser 表达式（本人与部门为等值条件，部门树与自定义为 `IN`），只对配了规则的表生效，默认本人列 `create_by`、部门列 `create_dept`；内部异常不吞掉。
- `com.kellen.entity.EntityBase`、`com.kellen.entity.TenantEntityBase`：实体基类两层，前者保留全部公共字段（审计字段按 RuoYi-Vue、RuoYi-Vue-Plus、JeecgBoot 等主流项目的多数命名），后者再加租户ID与创建部门。

当前边界：

- 只保留 `master` 与 `gray` 两个数据源。
- Druid 连接池的借出前校验、空闲检测、保活与连接、读取超时（毫秒）写在各服务 `application.yml` 的 `spring.datasource.dynamic.druid`；JDBC URL 不带 `connectTimeout`、`socketTimeout`，见[超时单位说明](../postgresql-druid-timeouts.md)。
- `version` 只作为 MyBatis-Plus 乐观锁字段。
- `MyBatisPlusConfig` 按 MyBatis-Plus 官方顺序注册插件：多租户、数据权限 → 分页、乐观锁 → 非法 SQL 检查、防全表更新删除。
- `IllegalSQLInnerInterceptor` 与 `BlockAttackInnerInterceptor` 属于默认安全边界，关闭前必须补充原因、替代防护和测试。
- MyBatis-Plus `SqlInjector` 只允许作为 Mapper 通用方法扩展点，不作为 SQL 注入防护能力。
- 需要租户隔离的调用必须显式维护 `TenantContextHolder` 生命周期。
- 接口参数或请求体里的 `tenantId` 不能直接写进 `TenantContextHolder`：先过 `TenantBoundary.require(tenantId)`，越出活动租户就拒绝（平台超级管理员除外）。
- Dubbo 透传只透传可信上下文，不把业务参数当作租户隔离依据。

## 四、AOP 与幂等

utils 当前没有切面。原有的请求日志切面与防重复提交从未生效，已在 1.6.8 删除（见文末）。以后需要防重复提交时，对照主流项目（RuoYi-Vue-Plus `@RepeatSubmit`、芋道 `@Idempotent` 等）的切面写法新增，键名包含租户、用户与方法，异常时释放。

## 五、Spring Boot 基础配置

核心类：

- `com.kellen.config.async.AsyncConfig`：异步线程池（线程名前缀 `async-`），任务装饰器见「可观测性」。
- `com.kellen.config.dubbo.DubboContextPropagationFilter`：Dubbo 动态数据源、租户和 Seata XID 透传配置。
- `com.kellen.config.dubbo.DubboExtensionPropertiesAliasConfig`：给租户、流量治理配置 Bean 补上 Dubbo 扩展按 setter 名查找用的别名，让透传过滤器拿到 Spring 绑定的配置。
- `com.kellen.config.dubbo.UnprocessedRequestFilter`：Dubbo 调用端协议层过滤器（2.1.1 起），同步调用在连接不可用时不发请求、标记“未发出”，并记下本次提供方对该方法的幂等声明。
- `com.kellen.config.dubbo.ConsumerRetryClusterFilter`：Dubbo 调用端集群层过滤器（2.1.1 起），全部同步调用的统一重试：未发出的透明重试到超时，声明了幂等的方法在请求发出后的连接类失败按次数、退避与令牌桶预算重试；带重试指标与日志。见 `RPC_API_CODING_SPEC.md`「调用端统一重试」。
- `com.kellen.config.redis.RedisCacheConfig`：Redis 缓存配置，值序列化器为 Spring Data Redis 4 的 Jackson 3 `GenericJacksonJsonRedisSerializer`，多态类型只放行 `com.kellen.` 与 `java.` 包。
- `com.kellen.config.redis.RedisLockAutoConfiguration`：自动配置，有 `RedissonClient` 时装配 `RedissonLocker` 并设入 `LockUtil`。
- `com.kellen.config.json.JsonUtilAutoConfiguration`：自动配置，容器启动后让 `JsonUtil` 改用 Spring Boot 配好的 `JsonMapper`。
- `com.kellen.config.swagger.Swagger`：OpenAPI 配置。

使用原则：

- Redis 连接、`StringRedisTemplate` 与 `RedissonClient` 由 Spring Boot 与 `redisson-spring-boot-starter` 按 `spring.data.redis.*` 自动配置，不在 utils 里手工建连接。
- 上传大小用 `spring.servlet.multipart.*` 配置，由需要上传的服务自己声明。
- 优雅停机用 Spring Boot 自带的 `server.shutdown=graceful`（3.4 起默认），需要先下线再停机时用 Spring Cloud 自带的 `serviceregistry` 端点。
- 配置类应只负责框架装配，不混入业务流程。
- 新增配置属性要写清楚默认值、来源和消费者。
- 对外暴露的运维端点必须明确安全边界。

## 六、通用基础工具

核心类：

- `com.kellen.utils.json.JsonUtil`：JSON 工具，方法名对照 Hutool `JSONUtil`：`toJsonStr`、`toBean`、`toList`；容器内使用 Spring Boot 配好的 `JsonMapper`。
- `com.kellen.utils.http.OkHttpUtils`：OkHttp 调用工具，共享单例客户端，使用 JDK 默认证书与主机名校验；`send` 返回响应体文本，非 2xx 抛 `IOException`。
- `com.kellen.utils.http.IpUtils`：`getClientIp`（Hutool `JakartaServletUtil.getClientIP`，回环统一为 `127.0.0.1`）、`getLocalIp`、`isInnerIp`（Hutool `NetUtil.isInnerIP`）。
- `com.kellen.utils.convert.GeneralConvertor`：通用转换工具（基于 Hutool `BeanUtil`，嵌套对象与集合深拷贝，行为与原 Dozer 实现一致）。

使用原则：

- 优先复用已有工具类，不新增功能重复的 `CommonUtils`；能直接用 Hutool、Spring、OkHttp 等现成能力的不自己实现。
- HTTP、Redis、JSON 工具涉及外部系统或序列化边界，修改后必须增加调用方验证。
- 访问自签名的内部服务时，为该服务单独配置信任库，不在 `OkHttpUtils` 里放开校验。

## 七、分布式锁

核心类：

- `com.kellen.utils.redisson.Locker`：锁接口。
- `com.kellen.utils.redisson.RedissonLocker`：Redisson 锁实现，`unlock` 只释放当前线程持有的锁。
- `com.kellen.utils.redisson.LockUtil`：锁工具门面。
- `com.kellen.utils.redisson.LockAcquireException`：等待超时或被中断时抛出。

使用原则：

- 优先用回调式 `executeWithLock` / `runWithLock`：在等待时间内加锁，不传租约由 Redisson 看门狗续期，`finally` 里只释放自己持有的锁。
- 分布式锁 key 必须包含业务域、租户、用户或资源 ID，避免不同业务互相阻塞。
- 锁等待时间必须按业务风险设置，不使用无界等待。

## 八、第三方平台封装

对象存储：

- `com.kellen.storage.PrivateObjectStorageService`：X File Storage 私有存储门面，统一 OSS / MinIO / COS 的 POST 策略（返回 `UploadPolicy`）、对象存在性、短时 GET 签名与服务端上传；所有者与 token 授权由业务服务维护。
- `com.kellen.storage.DisplayTokenUtils`：私有文件展示 token 的哈希与常量时间比对，业务表只存哈希。
- `com.kellen.storage.ObjectStorageConfig`：加载官方 `dromara.x-file-storage` 平台配置，不复制业务文件服务或保存另一套存储凭据。

使用原则：

- 第三方工具不得在日志中输出 accessKey、secret、token。
- SDK 调用失败应转换成业务可识别异常，避免上抛不稳定 SDK 异常结构。
- 平台专属配置对象和通用工具逻辑要分离。

## 九、枚举、常量与校验

核心类：

- `com.kellen.utils.enumeration.HttpType`：HTTP 请求体类型（`FORM`、`JSON`、`XML`）。
- `com.kellen.utils.enumeration.HttpWay`：HTTP 方式枚举。
- `com.kellen.utils.verify.Phone`：手机号校验注解，按 Hutool `PhoneUtil.isMobile`（13–19 号段），使用注解自身提示语，空值通过，可标在字段、方法、参数与容器元素上。
- `com.kellen.utils.verify.check.PhoneValidator`：手机号校验器。

使用原则：

- 通用枚举才能留在 `utils`。
- 明显业务专属枚举应迁回业务项目，避免公共包变成业务字典仓库。
- 校验工具和校验注解需要同时说明校验目标、空值策略和错误提示。

## 十、消息队列（RocketMQ）

核心类（rocketmq-spring 2.3.6，客户端 5.5.1；只在服务配了 `rocketmq.name-server` 时装配）：

- `com.kellen.utils.mq.MqTemplate`：发送工具，底层是 `RocketMQTemplate`。`send` 同步发送，失败抛 `MqSendException`；`sendAfterCommit` 在事务提交后发送、回滚不发，提交后失败只记日志。
- `com.kellen.utils.mq.MqSendOptions`：发送选项，`withTag`、`withKeys`、`withDelay`（1 秒到 3 天）、`withOrderKey`（不能与延时同用）。
- `com.kellen.utils.mq.MqHeaders`：消息属性名（`fleet_` 前缀）：租户、操作人用户与部门、数据源标记、泳道与灰度标签。
- `com.kellen.config.mq.MqRocketMqInitializer`：Bean 后置处理器（照芋道的 `TenantRocketMQInitializer`），给生产者挂发送钩子、给每个监听容器的消费者挂消费钩子，并按实例泳道改写消费组与订阅。
- `com.kellen.config.mq.MqContextSendMessageHook`、`MqContextConsumeMessageHook`：发送时补上下文属性；消费前还原租户、操作人与数据源标记，消费后恢复原值。
- `com.kellen.config.mq.MqInstanceLane`、`MqLaneSelector`：本实例的泳道与灰度标签（Nacos 注册元数据 `traffic.lane`、`canary.tag`），以及按泳道算出的消费组名与 SQL92 订阅。

使用原则：

- 发送用 `MqTemplate`，消费用 `@RocketMQMessageListener` 加 `RocketMQListener<T>`；不直接用 RocketMQ 原生客户端，不用 `RocketMQTemplate` 的异步发送与单向发送。
- 只支持单条消费；消费必须幂等；消费逻辑不调需要灰度授权的 RPC。
- 配置、命名、账号授权与泳道规则见 [消息队列规范](MQ_SPEC.md)。

## 十一、收敛原则

- 如果类只服务单个业务项目，应迁回业务项目；只有个别服务用到的依赖或配置，由该服务自己声明。
- 过时、小众、从未生效或已被替代且无人使用的工具直接删除，不为「以后可能用」保留；判断在用要看服务实际引用，以及 Spring 组件是否真正生效（注解要有切面驱动）。
- 保留的工具，写法与命名对照同类的多个主流项目，以多数做法为准，不以单个项目为准（如 Hutool、OkHttp、Redisson、MyBatis-Plus、Spring Boot；后台框架对照 RuoYi-Vue、RuoYi-Vue-Plus、JeecgBoot、芋道 ruoyi-vue-pro 等）。
- 删除或迁移前必须先用 `rg` 检查 `utils` 与消费者项目引用。

### 已移除（1.6.8）

- 钉钉机器人、阿里云短信、直播、函数工作流、旧 `OssUtils`，以及 `CommonAliyunProperties`、`CommonWechatProperties`、`SmsEnum`：无人使用，告警已由 Grafana 负责。
- 请求日志写 ES（`RequestRequiredAspect`、`@RequestRequired`、`com.kellen.log.*`、`ElasticsearchConfig`、`RequestUtil`、`MethodsBean`、`MethodsJudge`）：切面从未生效，日志已由 OTel 进 ES，参数校验用 Bean Validation。
- 防重复提交（`@PreventRepeat`、`PreventRepeatInit`、`PreventRepeatException`、`@Methods`、`MethodsInit`、`MethodsParam`、`ReflectionUtils`）：唯一驱动者是上面的切面，从未生效。
- `PdfUtils`（PDF 由 rag 引擎、OCR 与 Gotenberg 处理）、`RabbitMqConfig`（1.7.2 起由第十节的 RocketMQ 消息模块取代）、`ActuatorInterceptor`、`@DynamicDataSource`（用 dynamic-datasource 自带的 `@DS`）。
- `DateException`、`ParameterNullException`、`SignException`、`SmsException`：无人抛出。
- `ServiceShutDownEndpoint`、`MultipartConfig`、`DataSourceUtil`、`SensitiveStrategyConfig`、`ConfigClientController`：分别改用 Spring Boot 优雅停机与 `serviceregistry` 端点、服务自配 `spring.servlet.multipart.*`、`DynamicSourceTtl`、mybatis-mate 默认策略、Spring Boot 与 Redisson 自动配置。
- 依赖：Dozer（换 Hutool）、Orika、Aviator、javax.mail、commons-collections4、Guava 的显式声明（Guava 仍由 Seata 传递引入并锁定版本）、Jedis、iText、font-asian、PDFBox、`spring-boot-starter-amqp`、`spring-boot-starter-data-elasticsearch`、`aliyun-java-sdk-core` 的显式声明、`aliyun-java-sdk-fnf`；`spring-boot-starter-websocket` 与 `poi-ooxml` 改由使用它们的服务声明。

### 已移除的重复工具类（1.3.0）

以下薄包装类已删除，请直接使用类路径上已有的主流库，不要再引入同名自定义工具：

- 字符串：`org.apache.commons.lang3.StringUtils` 或 `cn.hutool.core.util.StrUtil`（原 `convert.StringUtils`）。
- 对象类型：`cn.hutool.core.util.ObjectUtil` / `org.springframework.util.ObjectUtils`（原 `convert.ObjectUtils`）。
- Map：`cn.hutool.core.map.MapUtil`（原 `convert.MapUtils`）。
- 集合流：`cn.hutool.core.collection.CollStreamUtil` 或 JDK Stream（原 `convert.StreamUtils`）。
- 对象映射：统一用 `convert.GeneralConvertor`（原 Orika 版 `convert.BeanMapper`）。
- 高精度数字：`cn.hutool.core.util.NumberUtil` / `BigDecimal.compareTo`（原 `math.BigDecimalUtils`）。
- 统一社会信用代码：`cn.hutool.core.util.CreditCodeUtil`（原 `validation.CreditCodeUtil`）。
- 动态代理：按需自建 `InvocationHandler`（原 `reflect.Invoker`，无人使用）。

以下零引用的工具类也已删除，需要时改用 Hutool 或迁回业务项目：

- 邮件：`cn.hutool.extra.mail.MailUtil`（原 `email.EmailUtils`/`email.EmailAccount`）。
- Excel：`cn.hutool.poi.excel.ExcelUtil`（原 `excel.ExcelExportUtil`；POI 需由使用的服务自行声明）。
- RSA：`cn.hutool.crypto.asymmetric.RSA`（原 `auth.RsaUtils`）。
- Redis：直接用 `StringRedisTemplate` / `RedisCacheConfig`（原 `redis.RedisUtils`）。
- 文件类型：`cn.hutool.core.io.FileTypeUtil`（原 `file.FileFormat`）。
- 公式 `math.FormulaUtils`、WebSocket 推送 `websocket.WebSocketUtils`、IP 归属 `http.AddressUtils`（含硬编码密钥，已随类删除）、枚举 `enumeration.LenEnum`/`NumericEnum`：均零引用删除，需要时迁回业务项目。

## 可观测性

- `com.kellen.observability.TraceContextSnapshot`：捕获有效 OTel trace/span 标识，无活动 span 返回空字段，不回退业务 MDC。
- `com.kellen.observability.TraceContextTaskDecorator`：提交时捕获 OTel 上下文与租户、用户、数据源等 TransmittableThreadLocal 上下文（`TtlRunnable`），执行后恢复工作线程原值，不复制 MDC；没有它，线程池复用线程时会沿用创建该线程那次请求的租户。

## 轻量HTTP观测子制品

`fleet-observability` 独立发布 `com:fleet-observability:0.1.0`，提供类型安全JSON投影、真实OTel关联、一次终态Servlet/Reactive适配。Spring/Web栈compileOnly，不为网关或Agent传入完整utils依赖；安全边界与实际验证入口见 [HTTP安全结构化观测](../fleet-http-observability.md)。
