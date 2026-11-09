# fleet-observability

轻量 Java 17 / Spring Boot 4 HTTP 观测库，坐标 `com:fleet-observability:0.1.1`。`utils` 通过 `api project` 引入；没有数据库、Redis、Seata、Spring starter 或 Servlet/Reactor 运行时传递依赖，适用于独立的 Servlet 与 WebFlux 服务。消费者提供其实际 Spring Web 栈，条件自动配置只启用对应适配器。

## 服务端配置

```properties
# 默认关闭；仅由受信部署配置开启，不接受请求头或业务参数打开。
fleet.observability.enabled=true
# 每个方向的复制硬上限为8192字节，可以调低，不能调高。
fleet.observability.max-body-bytes=8192
# AI自由正文不由HTTP访问日志采集。该可选API目前只投影诊断元数据。
fleet.observability.ai-content-enabled=false
fleet.observability.max-ai-content-characters=2048
# 0.1.1 的固定成员 SPI 提供安全上下文；关闭 Boot 默认自由 MDC/KVP 合流，避免同名键冲突丢日志。
logging.structured.json.context.include=false
```

保持现有 OTel Java agent 与私有配置。库只读取当前有效 `SpanContext`，不建立 SDK，不采用业务 DTO/MDC 的 `traceId`；未挂 agent 时 trace 字段缺失。是否全量采样属于受信运行配置，不能由本库伪造链路来补齐。

`0.1.1` 通过 Boot 结构化日志 SPI 仅输出固定、限长、类型受控的 HTTP/AI 字段。可信事件 KVP 优先；MDC 只可回退合法 OTel trace/span。任意 message、异常正文/堆栈、未知 MDC/KVP 均不导出；普通异常日志只提供等级、安全 logger 类别和根异常类别摘要。真实 Boot encoder 回归覆盖同名字段冲突、唯一 JSON 成员和不泄密，应用运行态与 Collector/ES 仍须另验。

## HTTP合同

每请求恰好一个 `event=http.server.exchange` 终态事件。`summary` 由固定方法、框架路由模板、状态、耗时、结果构造；未知路由为 `UNMATCHED`。不记录原始 URI、query、IP、身份、headers、异常消息或堆栈。

字段为 `summary,event,http_method,http_route,http_status,duration_ms,outcome,request_bytes,response_bytes,request_body_complete,response_body_complete,request_body,response_body,request_body_omitted,response_body_omitted,error_type,error_code,trace_id,span_id`。状态是整数，耗时和实读/成功写出量是非负数，完整性是布尔值，body是安全投影后的JSON字符串。无法证明 Reactive 部分写出量的取消/失败请求省略 `response_bytes`，不得把 `Content-Length` 或发送前排队字节数当成功写出量。

- 普通JSON只保留允许类型的安全envelope/分页字段，未知业务键删除。RAG检索控制和模型选择使用固定方法+框架路由模板的声明策略。
- 超限、重复键、过深、非法JSON、未知编码整体省略，不保存原始前缀。请求仅复制业务实际读取的字节，不主动读取或二次消费。
- 响应即时透传。SSE、multipart、二进制、文件不聚合正文；保留flush、背压和取消。AI chat/patient/questionnaire路由在HTTP层始终禁止正文捕获。
- Servlet通过原生async/error dispatch追踪最终响应；WebFlux使用Spring原生 `HttpHandlerDecoratorFactory` 包住包含 `WebExceptionHandler` 的整条传输处理，WebFilter只关联框架路由，不重复发事件。
- 自定义 `FleetEventSink` 可替换默认 SLF4J key/value sink。sink失败被隔离，不改变业务结果，也不回退打印原文。

`SafeBodyProjector.projectAiDiagnostic(String)` 仅在受信开关开启时返回允许字段的诊断元数据JSON，默认缺失。它不实现任意患者、Prompt或回答自由文的通用脱敏，也不代表开启正文诊断能力；新增正文方案必须定义独立受控结构并验证后才能接入。

## 构建与验证

在仓库根执行 `./gradlew :fleet-observability:test :fleet-observability:verifyLightweightPublication publishToMavenLocal --offline`。根 `test/check/publishToMavenLocal` 也包含子模块对应检查。发布POM只含精确版本JSON、SLF4J、OTel API依赖，独立验证生成POM、runtime依赖图和JAR自动配置资源。

测试启动本机临时Servlet与Reactive HTTP server，不访问真实模型、中间件或监控平台；验证JSON透传、实读/实写量、401/404/非法JSON最终响应、writer、async、SSE及时flush/取消、一次终态、类型投影和秘密省略。单纯应用测试通过不代表Collector/ES/Grafana字段已接通，部署链路须另验。
