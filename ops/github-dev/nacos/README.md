# 个人 Nacos 配置目录

本目录维护 `github-dev / DEFAULT_GROUP` 的 21 个活跃模板，默认渲染只遍历 `DEFAULT_GROUP/`。按职责分类，共享配置独立维护；服务自身和框架参数统一放在 `{service}-spring.yaml`。group、namespace 及保留项的 import 顺序不变。Yudao 与美联实际配置不在本目录维护范围。

每个文件开头写明 dataId、分类、用途与导入服务，此后每一行配置（含分组键和列表项）都紧贴一行注释或带行尾注释，由 `tests/test_nacos_comments.py` 检查。`GITHUB_*` 由私有环境渲染，Spring `${custom.*}` 等引用留给应用解析。[目录清单](catalog.json) 记录 dataId 说明、分类和消费者，供 Nacos 配置描述同步与一致性校验。Dubbo 自动生成的 metadata/mapping 不属于人工配置模板，不因数量多而删除。

## 共享变量

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [reuse-configuration.yaml](DEFAULT_GROUP/reuse-configuration.yaml) | 跨 dataId 引用的基础设施地址与 RPC 分组 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |

## 共享基础设施

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [dubbo.yaml](DEFAULT_GROUP/dubbo.yaml) | Dubbo Triple 协议和个人 namespace/group 隔离 | ai、ai-agent、ai-memory、mcp、message、rag、report、user |
| [mybatis-plus.yaml](DEFAULT_GROUP/mybatis-plus.yaml) | MyBatis-Plus 映射、逻辑删除和 MyBatis-Mate 授权 | ai、ai-memory、mcp、message、rag、report、user |
| [redis.yaml](DEFAULT_GROUP/redis.yaml) | 公共 Redis 缓存和 Spring Data Redis 共用连接 | ai、ai-memory、gateway、mcp、message、rag、report、user |
| [seata.yaml](DEFAULT_GROUP/seata.yaml) | Seata 分布式事务启用状态 | ai、ai-memory、mcp、message、rag、report、user |

## 安全与可观测性

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [actuator.yaml](DEFAULT_GROUP/actuator.yaml) | 管理端点访问令牌、暴露范围与健康详情策略 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [logging.yml](DEFAULT_GROUP/logging.yml) | 全局日志等级与安全结构化 HTTP/AI 投影白名单 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [local-canary.yaml](DEFAULT_GROUP/local-canary.yaml) | 本地灰度标签、就绪标记和授权，只有 dev 导入 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [security-auth.yaml](DEFAULT_GROUP/security-auth.yaml) | 认证来源、数据权限与租户隔离 | ai-memory、mcp、message、rag、report、user |
| [security-jwt.yaml](DEFAULT_GROUP/security-jwt.yaml) | 人员访问 JWT 的签名密钥与签发方 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [swagger.yaml](DEFAULT_GROUP/swagger.yaml) | OpenAPI 文档和 Swagger UI 暴露开关 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |
| [traffic-governance.yaml](DEFAULT_GROUP/traffic-governance.yaml) | 灰度治理开关与服务发现泳道元数据 | ai、ai-agent、ai-memory、gateway、mcp、message、rag、report、user |

## 服务配置

| dataId | 用途 | 当前 dev 导入服务 |
|---|---|---|
| [ai-agent-spring.yaml](DEFAULT_GROUP/ai-agent-spring.yaml) | Agent 模型、注册、Prompt 同步、服务身份和 Spring AI 模型实现选择 | ai-agent |
| [ai-memory-spring.yaml](DEFAULT_GROUP/ai-memory-spring.yaml) | ai-memory 的主/灰度数据源；均引用个人 schema | ai-memory |
| [ai-spring.yaml](DEFAULT_GROUP/ai-spring.yaml) | AI 主/灰度数据源、演示种子、Agent 发现和客户端签名 | ai |
| [gateway-spring.yaml](DEFAULT_GROUP/gateway-spring.yaml) | 网关路由、Redis 限流和浏览器 CORS 白名单 | gateway |
| [mcp-spring.yaml](DEFAULT_GROUP/mcp-spring.yaml) | MCP 主/灰度数据源、Agent 注册和 OAuth 资源服务器 | mcp |
| [message-spring.yaml](DEFAULT_GROUP/message-spring.yaml) | message 的主/灰度数据源；均引用个人 schema | message |
| [rag-spring.yaml](DEFAULT_GROUP/rag-spring.yaml) | RAG 主/灰度数据源、独立向量集合、嵌入模型和解析/检索引擎 | rag |
| [report-spring.yaml](DEFAULT_GROUP/report-spring.yaml) | report 的主/灰度数据源；均引用个人 schema | report |
| [user-spring.yaml](DEFAULT_GROUP/user-spring.yaml) | 用户服务主/灰度数据源与 OAuth 授权服务器公开入口 | user |

## 远端值以远端为准的项

`ai-agent-spring.yaml` 与 `rag-spring.yaml` 的部分值在 LiteLLM 接入、RAG 引擎启用后直接维护在远端，私有 `dev.env` 复现不了（RAG 引擎内部密钥只在远端与引擎侧）：

- `ai.agent.deepseek/qwen.base-url`、`rag.vector.qwen-base-url` 指向 LiteLLM 网关；`generation-defaults-source` 为 `DEPLOYMENT`。
- `${GITHUB_GOOGLE_API_KEY}`、`${GITHUB_DEEPSEEK_API_KEY}`、`${GITHUB_QWEN_API_KEY}`、`${GITHUB_RAG_QWEN_API_KEY}` 在远端保持原样，由服务进程的 OS 环境在运行期解析为各自的 virtual key。
- `rag.engine.base-url`、`rag.engine.api-key`、`rag.vector.hybrid-write-enabled`、`rag.vector.default-knowledge-base` 取远端值。

这两个文件不能用普通渲染发布，必须按下面的保值渲染发布，否则会把模板默认值或私有 env 里的直连凭据写回远端。

## 发布步骤

1. **只读盘点并备份**：读取远端 `github-dev/DEFAULT_GROUP` 全部 dataId，在 Git 外 0700 目录保存 JSON 快照，另存一份远端原文目录（文件名即 dataId，0600）。日志只打印 dataId 与数量。
2. **保值渲染**：渲染到新的空目录（输出目录含非当前模板的文件会直接报错，避免已退役 dataId 随发布被重新写回）：

   ```bash
   python3 scripts/render_nacos.py --output <Git 外空目录> --remote <远端原文目录> \
     --keep-remote ai-agent-spring.yaml --keep-remote rag-spring.yaml
   ```

   `--keep-remote` 的文件只取模板的注释、键序与结构，同路径的值取远端；远端多出的键必须用 `--drop <dataId>:<键路径>` 显式声明才会删除，否则报错并只列键路径。
3. **证明等价**：逐 dataId 比较候选与远端的解析结果，键、类型、列表顺序一致；差异只能是计划内的删除与有意变更。
4. **CAS 发布并读回**：`python3 scripts/sync_nacos.py --directory <候选目录> --publish --previous <远端原文目录>`。远端在盘点后被改过会拒绝发布。
5. **同步描述并退役旧项**：`scripts/maintain_nacos_catalog.py --directory <候选目录> --backup-directory <Git 外目录> --apply` 先核对远端内容等于候选，再把配置描述同步为 catalog 的说明；退役项加 `--retire --previous <远端原文目录>`，只要求仍在远端的旧项有快照，删除前逐项核对未漂移，删除后读回不存在。
6. **验证运行态**：重启受影响服务，健康为 UP，启动日志无缺少配置的报错。

## 退役项

退役 dataId 及原因见 [retired-dataids.json](retired-dataids.json)。`elasticsearch.yaml`、`rabbitmq.yaml`、`a2a.yaml`、`qdrant.yaml`、`ai-model-secrets.yaml`、`storage.yaml`、`xxl-job.yaml` 没有服务导入或代码读取，已从模板和远端删除；Git 外私有快照保留，恢复须重新发布并恢复对应服务的导入。

同批删除的无用键（无代码绑定、无 `${}` 引用，或框架已不再提供）：

| dataId | 键 | 原因 |
|---|---|---|
| mybatis-plus.yaml | `mybatis-plus.typeEnumsPackage`、`mybatis-plus.global-config.field-strategy` | MyBatis-Plus 3.5.18.1 已无这两个属性 |
| rag-spring.yaml | `rag.engine.retrieval-enabled`、`rag.engine.graph-enabled` | `RagEngineProperties` 无对应字段，仅远端残留 |
| local-canary.yaml | `traffic.governance.instance.tag` | 无引用，服务直接使用 `local-canary.tag` |
| redis.yaml | `redis.hostName`、`redis.port`、`redis.database`、`redis.password` | 只被同文件 `spring.data.redis.*` 转引，连接改为只声明一次 |
| swagger.yaml | `swagger.enable` | 无读取方，文档开关以 `springdoc.*` 为准 |

## 维护与验收

- 统一规则见 [Nacos 注释与归类规范](../../../docs/ai-coding/NACOS_COMMENT_SPEC.md)。
- 渲染必须保留说明，不能用普通 YAML dump 丢弃注释。
- 注释不赋予修改真实值的权限；凭据只在私有文件及目标 Nacos 中处理，不写入本文。
- 指标使用受令牌保护的 Prometheus 端点，链路由服务 JVM 专用 OpenTelemetry agent 上报；`admin.yaml`、`zipkin.yaml` 早已退役。调度使用 NAS 上独立的 Conductor OSS，不存在对应的 Java 配置 dataId。
