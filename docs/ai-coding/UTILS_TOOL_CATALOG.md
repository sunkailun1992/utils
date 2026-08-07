# utils 工具类归类目录

本文件按能力域整理 `utils` 现有工具类和配置类，方便 AI 或开发者快速判断应该复用、调整或继续收敛哪些类。

## 一、统一响应与错误处理

核心类：

- `com.kellen.utils.response.ApiResponse`：统一响应对象。
- `com.kellen.utils.enumeration.ReturnCode`：统一错误码枚举。
- `com.kellen.utils.exception.ApiExceptionHandler`：全局异常处理器。
- `com.kellen.utils.exception.BusinessException`：业务异常。
- `com.kellen.utils.exception.ParameterNullException`：参数为空异常。
- `com.kellen.utils.exception.PreventRepeatException`：防重复提交异常。
- `com.kellen.utils.exception.RpcException`：RPC 调用异常。
- `com.kellen.utils.exception.SignException`：签名异常。
- `com.kellen.utils.exception.SmsException`：短信异常。
- `com.kellen.utils.exception.UserException`：用户异常。

使用原则：

- 新接口统一返回 `ApiResponse`。
- 新业务错误统一选择 `ReturnCode`。
- 不恢复旧 `Json` 返回对象。
- 异常处理统一进入 `ApiExceptionHandler`，避免 Controller 重复拼失败响应。

## 二、认证、安全与上下文

核心类：

- `com.kellen.security.config.SecurityAuthConfig`：安全认证配置入口。
- `com.kellen.security.config.SecurityAuthProperties`：认证白名单与请求头配置。
- `com.kellen.security.SecurityAuthenticationFilter`：认证过滤器。
- `com.kellen.security.SecurityUser`：当前登录用户模型。
- `com.kellen.security.UserContextHolder`：用户上下文 ThreadLocal。
- `com.kellen.utils.auth.JwtUtils`：JWT 生成与解析工具。

使用原则：

- 业务代码通过 `UserContextHolder.get()` 读取当前用户。
- 请求结束必须清理用户上下文。
- JWT 是身份承载方式之一，不再使用旧 Redis token 用户模式。
- 密钥、token、签名串不得写入普通业务日志。

## 三、租户、数据源与 MyBatis-Plus

核心类：

- `com.kellen.utils.context.TenantContextHolder`：租户上下文 ThreadLocal。
- `com.kellen.security.config.TenantProperties`：租户请求头配置。
- `com.kellen.config.dubbo.DubboContextPropagationFilter`：Dubbo RPC 上下文透传。
- `com.kellen.config.web.ReqInterceptor`：HTTP 请求租户上下文初始化。
- `com.kellen.utils.context.DynamicSourceTtl`：动态数据源上下文。
- `com.kellen.config.datasource.DynamicDataSourceConfig`：Druid 动态数据源配置。
- `com.kellen.utils.datasource.DataSourceUtil`：数据源工具。
- `com.kellen.utils.annotations.DynamicDataSource`：动态数据源注解。
- `com.kellen.config.mybatis.MyBatisPlusConfig`：MyBatis-Plus 插件配置。
- `com.kellen.config.mybatis.MybatisPlusSecurityProperties`：MyBatis-Plus SQL 安全插件开关配置。
- `com.kellen.config.mybatis.MyMetaObjectHandler`：MyBatis-Plus 自动填充处理器。
- `com.kellen.entity.EntityBase`：通用实体基类。

当前边界：

- 只保留 `master` 与 `gray` 两个数据源。
- Druid 数据源默认启用借出前有效性校验、空闲检测和最小连接保活；消费者可通过 `utils.datasource.health.*` 覆盖校验语句、超时、检测间隔和最小空闲连接数。
- `version` 只作为 MyBatis-Plus 乐观锁字段。
- `MyBatisPlusConfig` 默认注册租户、数据权限、非法 SQL、防全表更新删除、分页和乐观锁插件。
- `IllegalSQLInnerInterceptor` 与 `BlockAttackInnerInterceptor` 属于默认安全边界，关闭前必须补充原因、替代防护和测试。
- MyBatis-Plus `SqlInjector` 只允许作为 Mapper 通用方法扩展点，不作为 SQL 注入防护能力。
- 需要租户隔离的调用必须显式维护 `TenantContextHolder` 生命周期。
- Dubbo 透传只透传可信上下文，不把业务参数当作租户隔离依据。

## 四、AOP、幂等与请求日志

核心类：

- `com.kellen.aop.RequestRequiredAspect`：请求切面。
- `com.kellen.idempotent.PreventRepeatInit`：防重复提交初始化与清理。
- `com.kellen.utils.annotations.PreventRepeat`：防重复提交注解。
- `com.kellen.utils.annotations.RequestRequired`：请求校验注解。
- `com.kellen.utils.annotations.Methods`：方法组合校验注解。
- `com.kellen.utils.methods.MethodsInit`：方法校验初始化。
- `com.kellen.utils.methods.MethodsJudge`：方法条件判断。
- `com.kellen.utils.methods.MethodsParam`：方法参数模型。
- `com.kellen.utils.methods.MethodsBean`：方法校验配置模型。
- `com.kellen.log.*`：请求日志、RPC 日志、Elasticsearch 日志相关实体、Mapper、Service。

使用原则：

- SQL 参数校验、动态数据源、日志、幂等锁属于切面职责。
- 新增切面逻辑必须保证正常返回、异常返回都清理上下文。
- 请求日志不得记录敏感密钥、完整 token 或密码。

## 五、Spring Boot 基础配置

核心类：

- `com.kellen.config.async.AsyncConfig`：异步线程配置。
- `com.kellen.config.dubbo.DubboContextPropagationFilter`：Dubbo 动态数据源、租户和 Seata XID 透传配置。
- `com.kellen.config.redis.RedisCacheConfig`：Redis 缓存配置。
- `com.kellen.config.elasticsearch.ElasticsearchConfig`：Elasticsearch 配置。
- `com.kellen.config.file.MultipartConfig`：文件上传配置。
- `com.kellen.config.swagger.Swagger`：OpenAPI 配置。
- `com.kellen.config.actuator.ActuatorInterceptor`：Actuator 拦截器。
- `com.kellen.config.actuator.ServiceShutDownEndpoint`：服务停机端点。
- `com.kellen.config.actuator.CustomWebMvcEndpointHandlerMapping`：Web MVC Endpoint 映射适配。
- `com.kellen.config.actuator.ConfigClientController`：配置客户端控制器。
- `com.kellen.config.sensitive.SensitiveStrategyConfig`：敏感字段处理配置。

使用原则：

- 配置类应只负责框架装配，不混入业务流程。
- 新增配置属性要写清楚默认值、来源和消费者。
- 对外暴露的运维端点必须明确安全边界。

## 六、通用基础工具

核心类：

- `com.kellen.utils.json.JsonUtil`：基于 Spring Boot 4 默认 Jackson 3 的 JSON 序列化与反序列化工具。
- `com.kellen.utils.http.OkHttpUtils`：OkHttp HTTP 调用工具。
- `com.kellen.utils.http.RequestUtil`：请求工具。
- `com.kellen.utils.http.IpUtils`：IP 工具。
- `com.kellen.utils.convert.GeneralConvertor`：通用转换工具（基于 Dozer）。
- `com.kellen.utils.reflect.ReflectionUtils`：反射工具。
- `com.kellen.utils.file.PdfUtils`：PDF 工具。

使用原则：

- 优先复用已有工具类，不新增功能重复的 `CommonUtils`。
- HTTP、Redis、JSON 工具涉及外部系统或序列化边界，修改后必须增加调用方验证。
- 文件、PDF、Excel 工具容易受依赖版本影响，修改后要做最小样例验证。

## 七、分布式锁

核心类：

- `com.kellen.utils.redisson.Locker`：锁接口。
- `com.kellen.utils.redisson.RedissonLocker`：Redisson 锁实现。
- `com.kellen.utils.redisson.LockUtil`：锁工具门面。

使用原则：

- 分布式锁 key 必须包含业务域、租户、用户或资源 ID，避免不同业务互相阻塞。
- 加锁和解锁必须成对出现，异常路径必须释放锁。
- 锁等待时间和持有时间必须按业务风险设置，不使用无界等待。

## 八、第三方平台封装

阿里云：

- `com.kellen.aliyun.AliyunKey`：阿里云密钥配置。
- `com.kellen.aliyun.Oss`：OSS 配置。
- `com.kellen.aliyun.oss.OssUtils`：OSS 操作工具。
- `com.kellen.aliyun.Sms`：短信配置。
- `com.kellen.aliyun.sms.SmsUtils`：短信发送工具。
- `com.kellen.aliyun.LiveStreaming`：直播配置。
- `com.kellen.aliyun.live.CreateLiveUtils`：直播创建工具。
- `com.kellen.aliyun.workflow.WorkflowRegion`：工作流地域枚举。
- `com.kellen.aliyun.workflow.WorkflowUtils`：工作流工具。

钉钉：

- `com.kellen.aliyun.DingDing`：钉钉配置。
- `com.kellen.aliyun.dingding.DingDingUtil`：钉钉发送工具。
- `com.kellen.aliyun.dingding.SendRebootUtil`：钉钉机器人发送工具。
- `com.kellen.aliyun.dingding.markdown.*`：Markdown 消息模型与构造器。
- `com.kellen.aliyun.dingding.text.*`：文本消息模型。

使用原则：

- 第三方工具不得在日志中输出 accessKey、secret、token。
- SDK 调用失败应转换成业务可识别异常，避免上抛不稳定 SDK 异常结构。
- 平台专属配置对象和通用工具逻辑要分离。

## 九、枚举、常量与校验

核心类：

- `com.kellen.utils.enumeration.HttpType`：HTTP 类型枚举。
- `com.kellen.utils.enumeration.HttpWay`：HTTP 方式枚举。
- `com.kellen.utils.enumeration.SmsEnum`：短信枚举。
- `com.kellen.utils.verify.Phone`：手机号校验注解。
- `com.kellen.utils.verify.check.PhoneValidator`：手机号校验器。

使用原则：

- 通用枚举才能留在 `utils`。
- 明显业务专属枚举应迁回业务项目，避免公共包变成业务字典仓库。
- 校验工具和校验注解需要同时说明校验目标、空值策略和错误提示。

## 十、收敛原则

- 如果类只服务单个业务项目，应迁回业务项目。
- 如果类是多个项目共享的基础能力，应补齐注释、异常边界、示例和归类。
- 删除或迁移前必须先用 `rg` 检查 `utils` 与消费者项目引用。
- 请求日志只保留采集、存储与服务能力，不在公共包内自动暴露查询 Controller。

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
- Excel：`cn.hutool.poi.excel.ExcelUtil`（原 `excel.ExcelExportUtil`）。
- RSA：`cn.hutool.crypto.asymmetric.RSA`（原 `auth.RsaUtils`）。
- Redis：直接用 `StringRedisTemplate` / `RedisCacheConfig`（原 `redis.RedisUtils`）。
- 文件类型：`cn.hutool.core.io.FileTypeUtil`（原 `file.FileFormat`）。
- 公式 `math.FormulaUtils`、WebSocket 推送 `websocket.WebSocketUtils`、IP 归属 `http.AddressUtils`（含硬编码密钥，已随类删除）、枚举 `enumeration.LenEnum`/`NumericEnum`：均零引用删除，需要时迁回业务项目。
