# 个人 Nacos 配置目录

本目录仅维护 `github-dev / DEFAULT_GROUP` 的 37 个模板。按职责分类，不改变 dataId、group、namespace 或服务的 import 顺序。Yudao 与美联实际配置不在本目录维护范围。

每个文件包含职责、导入服务与叶子参数注释；`GITHUB_*` 由私有环境渲染，Spring `${custom.*}` 等引用留给应用解析。空映射为有意保留的服务配置入口，不删除。

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
| [qdrant.yaml](DEFAULT_GROUP/qdrant.yaml) | Qdrant gRPC 连接；RAG HTTP 客户端另见 rag.yaml | ai、ai-memory、rag |
| [rabbitmq.yaml](DEFAULT_GROUP/rabbitmq.yaml) | RabbitMQ 连接、独立 vhost 与消费监听器开关 | ai、ai-memory、mcp、message、rag、report、user |
| [redis.yaml](DEFAULT_GROUP/redis.yaml) | 公共 Redis 缓存和 Spring Data Redis 共用连接 | ai、ai-memory、gateway、mcp、message、rag、report、user |
| [seata.yaml](DEFAULT_GROUP/seata.yaml) | Seata 分布式事务启用状态 | ai、ai-memory、mcp、message、rag、report、user |
| [storage.yaml](DEFAULT_GROUP/storage.yaml) | X File Storage 统一入口与私有 MinIO 平台 | ai |
| [xxl-job.yaml](DEFAULT_GROUP/xxl-job.yaml) | XXL-JOB 执行器启用状态 | ai、ai-memory、mcp、message、rag、report、user |

## 安全与可观测性

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [actuator.yaml](DEFAULT_GROUP/actuator.yaml) | 管理端点访问令牌、暴露范围与健康详情策略 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [admin.yaml](DEFAULT_GROUP/admin.yaml) | Spring Boot Admin 客户端注册开关 | ai、ai-memory、gateway、mcp、message、rag、report、user |
| [logging.yml](DEFAULT_GROUP/logging.yml) | 全局与应用包日志等级 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [security-auth.yaml](DEFAULT_GROUP/security-auth.yaml) | 认证来源、数据权限与租户隔离 | ai-memory、mcp、message、rag、report、user |
| [security-jwt.yaml](DEFAULT_GROUP/security-jwt.yaml) | 人员访问 JWT 的签名密钥与签发方 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [swagger.yaml](DEFAULT_GROUP/swagger.yaml) | OpenAPI 文档和 Swagger UI 暴露开关 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [traffic-governance.yaml](DEFAULT_GROUP/traffic-governance.yaml) | 灰度治理开关与服务发现泳道元数据 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [zipkin.yaml](DEFAULT_GROUP/zipkin.yaml) | Micrometer 链路追踪开关 | ai、ai-memory、gateway、mcp、message、rag、report、user |

## 共享 AI 协议

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [a2a.yaml](DEFAULT_GROUP/a2a.yaml) | A2A 租户、提供方及协议入口约定 | ai、ai-agent、ai-memory、mcp、rag |

## 共享 AI 模型

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [ai-model-secrets.yaml](DEFAULT_GROUP/ai-model-secrets.yaml) | Google GenAI 凭据与模型选择；真实凭据仅在私有渲染结果和 Nacos | ai-agent、ai-memory、rag |

## 服务专属

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [ai-agent.yaml](DEFAULT_GROUP/ai-agent.yaml) | Agent 模型、注册、Prompt 同步和服务身份边界 | ai-agent |
| [ai-memory.yaml](DEFAULT_GROUP/ai-memory.yaml) | ai-memory 当前无专属业务配置，保留空映射供既有 import 使用 | ai-memory |
| [ai.yaml](DEFAULT_GROUP/ai.yaml) | AI 演示数据、文件策略、Agent 发现和客户端签名 | ai |
| [mcp.yaml](DEFAULT_GROUP/mcp.yaml) | MCP/Agent 注册和 OAuth 资源服务器配置 | mcp |
| [message.yaml](DEFAULT_GROUP/message.yaml) | message 当前无专属业务配置，保留空映射供既有 import 使用 | message |
| [rag.yaml](DEFAULT_GROUP/rag.yaml) | RAG 向量库隔离和嵌入模型连接 | rag |
| [report.yaml](DEFAULT_GROUP/report.yaml) | report 当前无专属业务配置，保留空映射供既有 import 使用 | report |
| [user.yaml](DEFAULT_GROUP/user.yaml) | OAuth 授权服务器面向客户端的公开入口 | user |

## 服务框架

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [ai-agent-spring.yaml](DEFAULT_GROUP/ai-agent-spring.yaml) | ai-agent 的 Spring AI 模型实现选择 | ai-agent |
| [ai-memory-spring.yaml](DEFAULT_GROUP/ai-memory-spring.yaml) | ai-memory 的主/灰度数据源；均引用个人 schema | ai-memory |
| [ai-spring.yaml](DEFAULT_GROUP/ai-spring.yaml) | ai 的主/灰度数据源；均引用个人 schema | ai |
| [gateway-spring.yaml](DEFAULT_GROUP/gateway-spring.yaml) | 网关路由、Redis 限流和浏览器 CORS 白名单 | gateway |
| [mcp-spring.yaml](DEFAULT_GROUP/mcp-spring.yaml) | mcp 的主/灰度数据源；均引用个人 schema | mcp |
| [message-spring.yaml](DEFAULT_GROUP/message-spring.yaml) | message 的主/灰度数据源；均引用个人 schema | message |
| [rag-spring.yaml](DEFAULT_GROUP/rag-spring.yaml) | rag 的主/灰度数据源；均引用个人 schema | rag |
| [report-spring.yaml](DEFAULT_GROUP/report-spring.yaml) | report 的主/灰度数据源；均引用个人 schema | report |
| [user-spring.yaml](DEFAULT_GROUP/user-spring.yaml) | user 的主/灰度数据源；均引用个人 schema | user |

## 维护与验收

- 统一规则见 [Nacos 注释与归类规范](../../../docs/ai-coding/NACOS_COMMENT_SPEC.md)。
- 渲染必须保留说明，不能用普通 YAML dump 丢弃注释。
- 整理前后比较解析结果及列表顺序；发布前读取权威内容，存在漂移先复核，使用 CAS 并在发布后逐文件读回。
- 注释不赋予修改真实值的权限；凭据只在私有文件及目标 Nacos 中处理，不写入本文。
