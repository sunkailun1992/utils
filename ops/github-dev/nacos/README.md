# 个人 Nacos 配置目录

本目录仅维护 `github-dev / DEFAULT_GROUP` 的 27 个模板。按职责分类，共享配置继续独立维护；服务自身和框架参数统一到 `{service}-spring.yaml`。group、namespace 及保留项的 import 顺序不变。Yudao 与美联实际配置不在本目录维护范围。

每个文件包含职责、导入服务与叶子参数注释；`GITHUB_*` 由私有环境渲染，Spring `${custom.*}` 等引用留给应用解析。[目录清单](catalog.json) 同时记录 dataId 说明、分类和消费者，供 Nacos 配置描述同步与一致性校验。Dubbo 自动生成的 metadata/mapping 不属于人工配置模板，不因数量多而删除。

## 共享变量

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [reuse-configuration.yaml](DEFAULT_GROUP/reuse-configuration.yaml) | 跨 dataId 引用的基础设施地址与 RPC 分组 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |

## 共享基础设施

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [dubbo.yaml](DEFAULT_GROUP/dubbo.yaml) | Dubbo Triple 协议和个人 namespace/group 隔离 | ai、ai-agent、ai-memory、mcp、message、rag、report、user |
| [elasticsearch.yaml](DEFAULT_GROUP/elasticsearch.yaml) | Elasticsearch 连接及个人请求日志索引 | ai、ai-memory、mcp、message、rag、report、user |
| [mybatis-plus.yaml](DEFAULT_GROUP/mybatis-plus.yaml) | MyBatis-Plus 映射、逻辑删除和 MyBatis-Mate 授权 | ai、ai-memory、mcp、message、rag、report、user |
| [qdrant.yaml](DEFAULT_GROUP/qdrant.yaml) | Qdrant gRPC 连接；RAG HTTP 客户端另见 rag-spring.yaml | ai、ai-memory、rag |
| [rabbitmq.yaml](DEFAULT_GROUP/rabbitmq.yaml) | RabbitMQ 连接、独立 vhost 与消费监听器开关 | ai、ai-memory、mcp、message、rag、report、user |
| [redis.yaml](DEFAULT_GROUP/redis.yaml) | 公共 Redis 缓存和 Spring Data Redis 共用连接 | ai、ai-memory、gateway、mcp、message、rag、report、user |
| [seata.yaml](DEFAULT_GROUP/seata.yaml) | Seata 分布式事务启用状态 | ai、ai-memory、mcp、message、rag、report、user |
| [storage.yaml](DEFAULT_GROUP/storage.yaml) | X File Storage 统一入口与私有 MinIO 平台 | ai |
| [xxl-job.yaml](DEFAULT_GROUP/xxl-job.yaml) | XXL-JOB 执行器启用状态 | ai、ai-memory、mcp、message、rag、report、user |

## 安全与可观测性

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [actuator.yaml](DEFAULT_GROUP/actuator.yaml) | 管理端点访问令牌、暴露范围与健康详情策略 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [logging.yml](DEFAULT_GROUP/logging.yml) | 全局日志等级与安全结构化元数据白名单 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [security-auth.yaml](DEFAULT_GROUP/security-auth.yaml) | 认证来源、数据权限与租户隔离 | ai-memory、mcp、message、rag、report、user |
| [security-jwt.yaml](DEFAULT_GROUP/security-jwt.yaml) | 人员访问 JWT 的签名密钥与签发方 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [swagger.yaml](DEFAULT_GROUP/swagger.yaml) | OpenAPI 文档和 Swagger UI 暴露开关 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [traffic-governance.yaml](DEFAULT_GROUP/traffic-governance.yaml) | 灰度治理开关与服务发现泳道元数据 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |

## 共享 AI 协议

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [a2a.yaml](DEFAULT_GROUP/a2a.yaml) | A2A 租户、提供方及协议入口约定 | ai、ai-agent、ai-memory、mcp、rag |

## 共享 AI 模型

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [ai-model-secrets.yaml](DEFAULT_GROUP/ai-model-secrets.yaml) | Google GenAI 凭据与模型选择；真实凭据仅在私有渲染结果和 Nacos | ai-agent、ai-memory、rag |

## 服务配置

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [ai-agent-spring.yaml](DEFAULT_GROUP/ai-agent-spring.yaml) | Agent 模型、注册、Prompt 同步、服务身份和 Spring AI 模型实现选择 | ai-agent |
| [ai-memory-spring.yaml](DEFAULT_GROUP/ai-memory-spring.yaml) | ai-memory 的主/灰度数据源；均引用个人 schema | ai-memory |
| [ai-spring.yaml](DEFAULT_GROUP/ai-spring.yaml) | AI 主/灰度数据源、演示种子、文件策略、Agent 发现和客户端签名 | ai |
| [gateway-spring.yaml](DEFAULT_GROUP/gateway-spring.yaml) | 网关路由、Redis 限流和浏览器 CORS 白名单 | gateway |
| [mcp-spring.yaml](DEFAULT_GROUP/mcp-spring.yaml) | MCP 主/灰度数据源、Agent 注册和 OAuth 资源服务器 | mcp |
| [message-spring.yaml](DEFAULT_GROUP/message-spring.yaml) | message 的主/灰度数据源；均引用个人 schema | message |
| [rag-spring.yaml](DEFAULT_GROUP/rag-spring.yaml) | RAG 主/灰度数据源、独立向量集合、嵌入模型和解析/检索引擎 | rag |
| [report-spring.yaml](DEFAULT_GROUP/report-spring.yaml) | report 的主/灰度数据源；均引用个人 schema | report |
| [user-spring.yaml](DEFAULT_GROUP/user-spring.yaml) | 用户服务主/灰度数据源与 OAuth 授权服务器公开入口 | user |

## 合并与旧项清理

本轮按实际消费者和键空间核对后，将 `ai.yaml`、`ai-agent.yaml`、`mcp.yaml`、`rag.yaml`、`user.yaml` 合并到对应 `{service}-spring.yaml`；合并前两项在所有 profile 中相邻，均为 `optional`、`refreshEnabled=true`，且没有重复叶子键。移除空映射 `ai-memory.yaml`、`message.yaml`、`report.yaml`。清理清单及替代关系见 [retired-dataids.json](retired-dataids.json)。这次结构调整已单独授权，不能据此把今后的纯注释任务扩大为自动合并。

发布必须先从 Nacos 读取当前 37 个 DEFAULT_GROUP 配置并备份，按当前真实值合并并证明有效参数等价，再发布 27 个候选和说明。不能拿旧渲染模板覆盖远端运行值。旧 8 项先保留至消费者换用新 imports 并完成健康/配置读回验证，再按清理清单逐项确认旧版本未漂移后删除；它们的备份不进入 Git。若无法切换全部消费者，报告待清理，不把“模板已删除”称为“远端已删除”。Dubbo 自动配置不进入这份清理清单。

旧版本回滚先恢复旧 8 个 dataId，再恢复应用旧 imports；合并目标中的专属参数须与旧项保持一致，避免刷新覆盖差异。共享模板、namespace/group 和凭据保持不变。RAG 新增 `hybrid-write-enabled` 默认关闭，与本轮解析/检索升级联动，不能因配置合并而提前打开。

## 维护与验收

- 统一规则见 [Nacos 注释与归类规范](../../../docs/ai-coding/NACOS_COMMENT_SPEC.md)。
- 渲染必须保留说明，不能用普通 YAML dump 丢弃注释。
- 整理前后比较解析结果及列表顺序；发布前读取权威内容，存在漂移先复核，使用 CAS 并在发布后逐文件读回。
- 注释不赋予修改真实值的权限；凭据只在私有文件及目标 Nacos 中处理，不写入本文。

`admin.yaml`、`zipkin.yaml` 已退役，仅登记于 retired-dataids.json；远端尚未删除。指标使用受令牌保护的 Prometheus 端点，链路由服务 JVM 专用 OpenTelemetry agent 上报。ECS 日志默认排除 message 与异常正文，trace_id/span_id 保持真实上下文。
