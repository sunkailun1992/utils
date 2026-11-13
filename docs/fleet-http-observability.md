# HTTP安全结构化观测

当前公共包 `1.6.6` 配套独立子制品 `com:fleet-observability:0.1.4`，满足个人服务每个HTTP请求的终态、真实trace关联、有限输入输出投影。共享业务响应、认证、租户、幂等、数据源与RPC契约保持原有职责。

实现与配置见 [轻量观测库](../fleet-observability/README.md)。Servlet与Reactive只加载各自适配器；Java21、Boot4、Jackson3固定版本，公共库不提供第二套OTel SDK。默认关闭，由受信配置显式开启。

`0.1.1` 修复真实 Boot4 ECS 的同名 MDC/KVP 合流冲突，避免整条 HTTP/AI 事件被编码器丢弃。共享模板必须设置 `logging.structured.json.context.include=false`，由固定成员 SPI 唯一输出受控字段，不能单独发布关闭上下文的配置而保留旧轻库。根公共包同时迁移 PostgreSQL，数据库切换边界见 [PG 底座](postgresql-fleet-foundation.md)。

## 兼容与隐私

旧 `RequestRequiredAspect` 保留参数校验、幂等、动态数据源及审计索引写入；审计请求/结果只保留固定省略信息，不序列化业务返回对象，不记录用户名、IP、扩展点说明原文或动态URI。慢请求提醒仅包含框架路由模板、真实trace和耗时，不再外发请求参数。历史ES索引与实体公共API不删除。

`ReqInterceptor` 的既有响应 `traceId` 头仅在当前有效OTel span存在时设置，不能采用旧业务MDC值冒充链路身份。

HTTP和AI内容投影坚持字段/类型白名单；AI自由正文始终不从普通HTTP日志导出。可选AI诊断API当前只接受受控元数据，不声称具备任意患者自由文脱敏能力。运行时默认不输出任意message、异常或MDC，扩展后的ECS/Collector/ES字段须单独按实际采集链验收。

## 验收与开发手册

- 正向：RAG检索的topK等数值控制、模型标识、响应code/success和来源数正确保留。
- 反向：同一合成请求的query、prompt、context、密码、身份、未知键和原始异常不进入事件；重复键/超限/编码失败不泄露前缀。
- 传输：真实Servlet/Reactive HTTP字节不变，writer和async正常，SSE首块及时送达、取消终态一次，错误处理后的400/404响应计数正确。
- 关联：有效OTel快照可跨异步回调；线程池复用/并发不串请求，无span时为空。
- 依赖：过滤另一Web栈的classpath上下文仍可启动；生成POM/runtime图无Spring starter、DB、Redis、Seata或utils反向依赖。

根 `test/check` 覆盖子模块，Maven Local发布包含两个坐标。正式远程发布仍从已验证main确定提交执行，并读回两个制品的JAR/POM/sources/javadoc与消费者精确依赖解析；Git、制品和运行验收分别报告。本机既有运行进程不因编译/测试自动重启。

回滚需同时回退消费者的utils/轻库精确版本和运行配置；默认关闭观测不影响现有认证与管理端点保护。不得为了排障放宽令牌或关闭隐私过滤。
