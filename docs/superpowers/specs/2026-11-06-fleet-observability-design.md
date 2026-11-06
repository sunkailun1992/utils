# 个人微服务监控与旧 RAG 清理设计

用户已确认全体系实施 OpenTelemetry + Grafana，并要求移除 Spring Boot Admin、Zipkin 历史配置；RAG 以当前 Java 门面 + Python Engine + Qdrant + Neo4j 为准，清理旧实现和无用表。

## 范围

9 服务 gateway/user/message/ai/ai-agent/ai-memory/rag/report/mcp，公共 utils，必要时统一启动入口 kellen-workspace。rpc-api 不因 tracing 添加业务字段。公司项目及其他独立个人应用排除。

## 实现约定

- Java17/Boot4.0.4，Dubbo3.3.6；近期固定稳定 OTel agent，只有 API 依赖，不叠加第二个 SDK。
- Java 指标唯一来源 Micrometer Prometheus；OTEL_METRICS_EXPORTER=none、OTEL_LOGS_EXPORTER=none。
- 采集管理端点受运维令牌保护，网关公网管理阻断保持。Python metrics 也需受限访问，不能公开凭据。
- Collector、Prometheus、Grafana、Jaeger 单进程持久化独立编排；ES保留，现场版本核对后接日志采集，不改业务 Compose。
- 结构日志及 span 只保留有限元数据；不采正文、问题、prompt、模型回包、令牌、动态URL、原始异常。trace/span 为真实 OTel context，业务会话ID保持独立。
- 指标标签为有限路由模板/阶段/结果；tenant、KB、业务ID、traceId、hash 不作为 labels。
- Python FastAPI/httpx HTTP链路与检索阶段观测；后台图任务用独立trace关联提交请求，父进程记录终态，子进程stdout JSON协议保持。
- exporter批处理、有界队列、有限超时，故障不阻塞业务；不在请求内flush。监控开关关闭可正常运行。
- 旧 admin.yaml/zipkin.yaml 及 imports/catalog/实际渲染工具引用清理；远端删除在目标配置读回、备份、消费者切换核实后执行。
- 清理旧 Java MySQL 图谱/旧检索回退前核当前调用链。不能误删当前文档、来源、版本、Qdrant写入/读回、幂等与权限代码。只为已确认旧表新增独立清理SQL，不修改历史已执行DDL；实际DROP前核目标库、引用与内容。
- 保留当前RAG预算：上游5秒、Engine3秒、图扩展0.4秒；不重建知识库，不扩大现有Engine/OCR/Neo4j资源。

## 验收

全服务registry与受保护采集；并发/异步上下文不串线；SSE成功/取消/错误、MCP业务失败、RAG降级/超时可观察；Python父进程与worker终态、安全标签和日志；Collector失败不阻塞；日志与ES/trace关联；历史配置无活动引用；旧RAG移除后合同/权限/版本/来源测试通过。代码测试、Git、制品、目标部署、实际采集分别报告。

## 部署边界

极空间可运行四个观测组件，但先只读核容量/现有组件。先完成代码及本机验证，再提供不可变版本、部署配置、资源和回滚清单；未现场部署不能称完成实际采集。旧RAG表实际删除同样先提供已核对象。
