# utils 工具类归类目录

本文件按能力域整理 `utils` 现有工具类和配置类，方便 AI 或开发者快速判断应该复用、调整或继续收敛哪些类。

## 一、统一响应与错误处理

核心类：

- `com.kellen.utils.response.ApiResponse`：统一响应对象。
- `com.kellen.utils.enumeration.ReturnCode`：统一错误码枚举。
- `com.kellen.utils.exception.ApiExceptionHandler`：全局异常处理器。
- `com.kellen.utils.exception.BusinessException`：业务异常。
- `com.kellen.utils.exception.CustomerException`：自定义异常。
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
- `com.kellen.utils.auth.RsaUtils`：RSA 加解密工具。

使用原则：

- 业务代码通过 `UserContextHolder.get()` 读取当前用户。
- 请求结束必须清理用户上下文。
- JWT 是身份承载方式之一，不再使用旧 Redis token 用户模式。
- 密钥、token、签名串不得写入普通业务日志。

## 三、租户、数据源与 MyBatis-Plus

核心类：

- `com.kellen.utils.context.TenantContextHolder`：租户上下文 ThreadLocal。
- `com.kellen.security.config.TenantProperties`：租户请求头配置。
- `com.kellen.config.web.ReqInterceptor`：Feign 请求上下文透传。
- `com.kellen.utils.context.DynamicSourceTtl`：动态数据源上下文。
- `com.kellen.config.datasource.DynamicDataSourceConfig`：Druid 动态数据源配置。
- `com.kellen.utils.datasource.DataSourceUtil`：数据源工具。
- `com.kellen.utils.annotations.DynamicDataSource`：动态数据源注解。
- `com.kellen.config.mybatis.MyBatisPlusConfig`：MyBatis-Plus 插件配置。
- `com.kellen.config.mybatis.MyMetaObjectHandler`：MyBatis-Plus 自动填充处理器。
- `com.kellen.entity.EntityBase`：通用实体基类。

当前边界：

- 只保留 `master` 与 `gray` 两个数据源。
- `version` 只作为 MyBatis-Plus 乐观锁字段。
- 需要租户隔离的调用必须显式维护 `TenantContextHolder` 生命周期。
- Feign 透传只透传可信上下文，不把业务参数当作租户隔离依据。

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
- `com.kellen.config.feign.FeignConfiguration`：OpenFeign 配置。
- `com.kellen.config.feign.FeignOkHttpConfig`：Feign OkHttp 配置。
- `com.kellen.config.redis.RedisCacheConfig`：Redis 缓存配置。
- `com.kellen.config.elasticsearch.ElasticsearchConfig`：Elasticsearch 配置。
- `com.kellen.config.file.MultipartConfig`：文件上传配置。
- `com.kellen.config.swagger.Swagger`：Knife4j / OpenAPI 配置。
- `com.kellen.config.actuator.ActuatorInterceptor`：Actuator 拦截器。
- `com.kellen.config.actuator.ServiceShutDownEndpoint`：服务停机端点。
- `com.kellen.config.actuator.CustomWebMvcEndpointHandlerMapping`：Web MVC Endpoint 映射适配。
- `com.kellen.config.actuator.ConfigClientController`：配置客户端控制器。
- `com.kellen.config.sensitive.SensitiveStrategyConfig`：敏感字段处理配置。
- `com.kellen.config.wechat.WeChat`：微信配置属性。

使用原则：

- 配置类应只负责框架装配，不混入业务流程。
- 新增配置属性要写清楚默认值、来源和消费者。
- 对外暴露的运维端点必须明确安全边界。

## 六、通用基础工具

核心类：

- `com.kellen.utils.json.JsonUtil`：JSON 序列化与反序列化工具。
- `com.kellen.utils.http.OkHttpUtils`：OkHttp HTTP 调用工具。
- `com.kellen.utils.redis.RedisUtils`：Redis 操作工具。
- `com.kellen.utils.websocket.WebSocketUtils`：WebSocket 推送工具。
- `com.kellen.utils.http.RequestUtil`：请求工具。
- `com.kellen.utils.http.IpUtils`：IP 工具。
- `com.kellen.utils.http.AddressUtils`：地址解析工具。
- `com.kellen.utils.convert.MapUtils`：Map 工具。
- `com.kellen.utils.convert.ObjectUtils`：对象工具。
- `com.kellen.utils.convert.StringUtils`：字符串工具。
- `com.kellen.utils.math.BigDecimalUtils`：金额与高精度数字工具。
- `com.kellen.utils.math.FormulaUtils`：通用公式计算工具。
- `com.kellen.utils.convert.BeanMapper`：对象映射工具。
- `com.kellen.utils.convert.GeneralConvertor`：通用转换工具。
- `com.kellen.utils.reflect.ReflectionUtils`：反射工具。
- `com.kellen.utils.reflect.Invoker`：反射调用辅助工具。
- `com.kellen.utils.convert.StreamUtils`：Stream 辅助工具。
- `com.kellen.utils.file.FileFormat`：文件格式工具。
- `com.kellen.utils.file.PdfUtils`：PDF 工具。
- `com.kellen.utils.excel.ExcelExportUtil`：Excel 导出工具。

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

邮件与微信：

- `com.kellen.utils.email.EmailAccount`：邮件账号模型。
- `com.kellen.utils.email.EmailUtils`：邮件发送工具。
- `com.kellen.config.wechat.WeChat`：微信配置。
- `com.kellen.utils.enumeration.WxEnum`：微信相关枚举。

使用原则：

- 第三方工具不得在日志中输出 accessKey、secret、token。
- SDK 调用失败应转换成业务可识别异常，避免上抛不稳定 SDK 异常结构。
- 平台专属配置对象和通用工具逻辑要分离。

## 九、枚举、常量与校验

核心类：

- `com.kellen.utils.constants.UniversalConstant`：通用常量。
- `com.kellen.utils.enumeration.HttpType`：HTTP 类型枚举。
- `com.kellen.utils.enumeration.HttpWay`：HTTP 方式枚举。
- `com.kellen.utils.enumeration.NumericEnum`：数字枚举。
- `com.kellen.utils.enumeration.LenEnum`：长度枚举。
- `com.kellen.utils.enumeration.SmsEnum`：短信枚举。
- `com.kellen.utils.enumeration.SourceValueEnum`：来源值枚举。
- `com.kellen.utils.enumeration.SystemSourceEnum`：系统来源枚举。
- `com.kellen.utils.enumeration.AppCodeEnum`：应用编码枚举。
- `com.kellen.utils.verify.Phone`：手机号校验注解。
- `com.kellen.utils.verify.check.PhoneValidator`：手机号校验器。

使用原则：

- 通用枚举才能留在 `utils`。
- 明显业务专属枚举应迁回业务项目，避免公共包变成业务字典仓库。
- 校验注解需要同时说明校验目标、空值策略和错误提示。

## 十、历史业务工具与待收敛区域

当前已确认保留的通用类：

- `com.kellen.utils.validation.CreditCodeUtil`：统一社会信用代码工具，可保留为通用校验工具。
- `com.kellen.utils.math.FormulaUtils`：只保留表达式执行、BigDecimal 转换、金额计算、费率按天折算等通用能力。

收敛原则：

- 如果类只服务单个业务项目，应迁回业务项目。
- 如果类是多个项目共享的基础能力，应补齐注释、异常边界、示例和归类。
- 删除或迁移前必须先用 `rg` 检查 `utils` 与消费者项目引用。
- 请求日志只保留采集、存储与服务能力，不在公共包内自动暴露查询 Controller。
