# LiteLLM 模型控制台与配置归属

本目录维护个人 Java fleet 的 LiteLLM OSS 部署合同与公开示例。用户已选择 LiteLLM；固定 `v1.108.4` non-root 镜像，复用现有 PostgreSQL 中的独立数据库。示例不能直接覆盖现有 NAS Compose，真实配置、密码和运行证据只保存在 Git 外。

## 配置放在哪里

| 内容 | 权威位置 | 业务管理页面 |
| --- | --- | --- |
| 模型 deployment、provider/API base、上游凭据、虚拟密钥、预算、限流、用量 | LiteLLM 原生控制台及其专属数据库 | 只读模型目录、能力分类、配置入口；跳转原生控制台 |
| 业务选择哪个 provider、角色 Prompt、知识库、租户与来源权限 | 各业务服务自己的数据库/既有业务 API | provider 选择及既有知识库/权限页面；Prompt CRUD 与任务绑定管理仍缺，不复制第二套 LiteLLM CRUD |
| OSS、COS、MinIO/S3 连接、默认上传平台、上传开关、有效期和容量策略 | AI 存储管理 API 及 PostgreSQL | 文件存储页面；X File Storage 原生适配器 |
| 数据源、注册发现、消息队列、环境、治理参数 | Nacos 的对应基础设施/服务 dataId | 不为每个基础设施复制管理 UI |
| 存储加密根密钥、允许连接的私有存储 origin | 仅 AI 服务 OS 环境/私有部署输入 | 不可在业务页面或 Nacos 修改 |

模型部署与上游凭据以 LiteLLM 为管理源，业务应用使用各自最小权限 virtual key。DeepSeek/Qwen/RAG 已按各自连接接入；Google 因 NAS native 请求仍为 UNKNOWN，当前保留 Agent 原 direct 路径及其专属 Google 凭据，不能宣称所有应用已只持 virtual key。AI/Agent/RAG 的九份源码 profile 均不再直接导入 `ai-model-secrets.yaml`；旧远端 dataId 仅保留为显式回滚来源，Google 必需字段由 Agent 服务配置显式提供。存储凭据由 AI 服务使用独立 AES-GCM 根密钥加密保存，不复用 JWT、数据库密码或模型主密钥；Nacos 保留基础设施连接，迁移后的业务存储连接不再由共享 Nacos 配置管理。

业务生成参数还要按执行层的显式来源核对：DeepSeek/Qwen 的 `SERVICE` 保留服务参数，`DEPLOYMENT` 才省略服务覆盖并继承原生 deployment 默认；Google direct 选项保持原合同。源码支持该选择不等于所有在运行实例已完成参数迁移，也不能声称已有完整生成策略或 Prompt 管理页面。

## 能力范围与页面状态

模型页面覆盖语言/多模态、Embedding、Rerank、OCR、音频转录、语音合成、图像、视频和未识别类型。LiteLLM 是模型协议网关，具体 endpoint 和参数依赖 provider 与固定版本；原生目录中的 mode/provider 是分类依据，不靠名称猜测。多个业务 alias 可以共享同一原生 deployment ID，页面按 `(id, modelName)` 区分记录。

“框架支持”“已配置”“业务已接入”“实际验证通过”必须分别取证。目录可读不代表模型健康。暂未调用的类型显示未验证，不生成虚构健康时间。当前文档解析器/本地 OCR 与云模型 `/v1/ocr` 是不同的接入路径，不因网关支持 OCR 自动替换解析流程。

原生控制台入口包括模型、API keys、预算、用量和 Playground。浏览器不获得 master key、目录只读 key 或上游凭据；只读目录由 AI 后端的独立凭据查询 `/v1/model/info` 并做白名单投影。固定版本原生 UI 的高级 SSO、审计和部分 key 参数可能属于 Enterprise，不能把文档中的所有字段当成 OSS 免费能力。已实际确认 `allowed_passthrough_routes` 在该版本要求 Enterprise；OSS 使用其支持的 `allowed_routes` 并补拒绝测试。

生产配置使用 `general_settings.allow_client_side_credentials=false`，模型 deployment 不开放 client-side endpoint 配置。固定版本源码与实际负例只证明已核选定 endpoint/config 覆盖会被拒绝；该开关不全面禁止 `api_key`、`custom_llm_provider` 等原生 BYOK 能力。业务 scoped key 只进入受信任的 Java/Engine 服务端，浏览器和普通用户不获得 key 或模型网关直连入口；业务 typed DTO/SDK 不透传用户任意模型 kwargs。这是当前应用边界，不能写成 LiteLLM 全局禁用 BYOK。

## 部署与安全日志

- `compose.example.yaml`：官方 non-root digest、UID 65534、drop ALL、no-new-privileges、已有网络与 loopback 端口。只新增一个服务，不重建已有 PostgreSQL 或整套环境。
- `config.example.yaml`：首次 bootstrap 的空模型目录示例，DB 管理，零模型重试/回退，关闭正文日志，限定 Prometheus 标签。模型和 provider 密钥经原生管理 API/控制台设置；示例中的空目录不代表当前运行目录仍为空。
- `fleet_safe_logger.py`：通过官方 `CustomLogger` 扩展点输出固定安全字段。独立 INFO/ERROR logger 保留失败可见性，禁止输出异常原文、堆栈、正文、headers、URL 和 key。
- `private-env.example`：私有输入名称。`LITELLM_LOG=CRITICAL` 抑制原生 provider 错误文本；安全 callback 独立记录结果。单靠 `redact_messages_in_exceptions` 或 NO_CONTENT 无法证明所有输出安全。
- `image-evidence.public.json`：官方固定制品核查边界，不代表完整供应链、漏洞扫描或透明日志验证。

配置和 callback 的目标路径使用 `/etc`。必须在实际运行中证明配置及其所有父目录不能被 UID 65534 改写、替换或 chmod；单个文件只读而父目录可写不足以保护配置。升级后重新执行真实 SDK 加载、协议与隐私验收，不能只运行本目录的 stub 单元测试。

示例中的 `fleet-existing` 由 `FLEET_EXISTING_NETWORK` 引用一个已有 Docker 网络。实施前必须将实际 network name、ID、`Internal` 属性与目标服务连接逐一对应核实；一个同时支持数据库访问和 provider 出口的既有 `internal=false` bridge 可以足够，不强制双网络，也不能仅凭 Engine 连接了两个网络推断哪个具备出口。只有目标当前仅连接 internal 网络、确需外部 provider 出口时，才另行核定是否附加一个已有的非 internal 出口网络。本示例不创建网络，不修改已有网络、VPN、DNS 或主机路由；占位符和网络资格检查本身不证明实际出口可达。

独立数据库连接成功、容器 health 通过和模型目录可读，只证明各自的局部状态。provider 出口须在目标容器实际 namespace 内先执行无凭据的 DNS/TLS 检查，再按每个 provider 的真实协议做受限次数的验证；native Gemini、OpenAI-compatible、Embedding 和 Rerank 不能相互代替。涉及真实请求的预算、密钥与权限、取消、隐私及费用继续分别验收。超时或未知结果不自动重放。

```bash
python3 -m unittest discover -s ops/github-dev/ai-gateway -p 'test_*.py'
```

该命令只验证 callback 的白名单、去重、错误与并发行为；不会访问 NAS、真实模型、数据库或运行中的 LiteLLM，也不证明实际互联网可达。固定镜像内真实 SDK/loader、完整 stdout/DB/原始 OTLP 需单独验收。

## 费用与预算的独立边界

固定版本的 Gemini/DeepSeek/Qwen 部分条目有内置美元估算价格，但不能据此认定地区、缓存、思考和长上下文阶梯都与当前供应商账单一致。现用北京 DashScope 的 text-embedding-v4、qwen3-rerank、qwen3.7-plus 指定快照不在固定成本表中；不允许填零冒充免费，也不把 CNY 原价直接放入 USD 字段。LiteLLM spend/预算账本以 USD 处理，不在请求时自动换汇。

固定版 DashScope rerank 返回 Token 用量，但继承的成本分支按 query/search_units 计算；仅补 Token 单价不能证明该路由正确扣费。协议、维度、取消和权限验证可独立推进，费用及预算须单独核对真实非零预期 spend、地区/阶梯价格与拒绝行为；未验收即保留未验收，不能用零成本或价格表存在代替实际取证。

当前模型费用与预算状态为 `NOT_VERIFIED`；下述五项协议 PASS 不改变这个结论。

参考：[固定版本成本表](https://raw.githubusercontent.com/BerriAI/litellm/e6f080fb5633aa71ea81b652bd72069aff1f0228/litellm/model_prices_and_context_window_backup.json)、[成本币种](https://docs.litellm.ai/docs/proxy/model_cost_map#currency)、[阿里官方价格](https://help.aliyun.com/zh/model-studio/model-pricing)。

## 运行态进展与验收门槛

2026-11-11（Asia/Singapore）的阶段性事实：独立空台与专属数据库是首次部署阶段，当前目录已进入下述六个 deployment、五个 service user 和五个 scoped key 阶段。首轮十场景合成协议及取消计数已取证、资源已清理。首轮完整 stdout 隐私检查发现两处上游错误正文，因此该轮隐私结果为失败，原始证据保留。官方扩展方式的安全日志已安装；全新十场景复验的清理前后完整 stdout/DB 采样均通过，已知正文/密钥命中为零，十个响应调用 ID 与唯一安全终态逐个关联，两条 503 保持 ERROR，流式关闭和取消不冒充完整成功。两个批次的七模型与独立 key 均已精确清理，owned mock 已停止。首轮失败证据保留，复验通过不改写旧结论。

上述合成阶段未导入真实 provider key，其结果不代表业务切流。原生 OTel 的独立 loopback 批次已完成：十个场景、28 份完整原始 export、273 spans；请求父链、响应调用 ID、资源与 503 状态逐个关联通过，但异常属性、事件和状态描述共六处命中合成正文，raw 隐私为失败。stdout/专属 DB 的本轮已知值命中仍为零。测试模型、key 和自建 mock/sink 已精确清理；清理前快照的并发采集失败与清理后完整 raw 失败分别保留，不重放请求。

后续生产安全配置已切至 OTel OFF；原有六个物理模型的 deployment、五个 service user 和五个 scoped key 已一次导入，没有重放注册。受限协议验证的阶段性结果如下，五项 PASS 不扩大为所有模型或所有业务链路健康：

| 物理模型 | 本轮受限协议结果 |
| --- | --- |
| `gemini-3.1-flash-lite` | native 请求 90 秒超时，结果 UNKNOWN；不自动重放，费用及上游最终结果未核。 |
| `deepseek-v4-flash` | PASS。 |
| `qwen-plus` | PASS。 |
| `text-embedding-v4` | PASS；既有 1024 维绑定保持。 |
| `qwen3-rerank` | PASS；不等于 Token 费用计算或预算已验。 |
| `qwen3.7-plus-2026-05-26` | PASS；该模型协议结果不等于图谱抽取或图谱质量已验。 |

Engine `0.3.0` 已实际部署，新 worker readiness、active lease 与心跳推进已通过验收。新的 M1 有界业务链也已实际执行：一次合成文档上传、自动图谱任务 `SUCCEEDED`、A/B 各一次检索与来源隔离、Qwen 完整 SSE、文档内 gold 标记与会话记忆读回一致，最后恢复原 Google 业务默认，HTTP 链路通过；不重放该轮。模型协议、worker readiness、来源隔离与此次有限效果分别取证，不扩大为医疗准确性或所有任务质量通过。

M1 同时保留两个实际 finding：成功审计行的 `tenant_id` 落入历史默认 `1`，以及流式 usage 尾块把已知终止原因覆盖为 `other`。AI `1.6.8` 显式写入已验证审计租户、`1.6.9` 移除遗留微信服务端；最终 AI `1.7.0` 的通用 404/固定 500 隐私修复与 Agent `0.3.2` 的已知 finish reason 保留均已交付，精确 CI [AI 42932889150](https://github.com/sunkailun1992/ai/actions/runs/42932889150) 与 [Agent 42928881554](https://github.com/sunkailun1992/ai-agent/actions/runs/42928881554) 均为 SUCCESS。

修复后的 M2 新 audit-only 单次流已实际执行一次：复用 M1 已成功文档和图谱，不重新上传、建图或显式检索；Qwen 完整 SSE 的 Gold、A 会话两条/B 会话空、Google 全部选择 metadata 恢复均通过。已验证租户下恰好一条成功审计行，assistant SHA 与本轮响应一致；实际 AI `1.7.0`、Agent `0.3.2` 的 finish 为 `stop`，input/output usage 为 447/55。该新场景验证修复，不回填或改写 M1 的默认租户与 `other` 历史，也不把 usage 当成费用预算证明。

中央日志的原生 `fluent_forward`/OTTL 验证已完成：25 条合成输入过滤后保留 11 条安全记录，真实 Docker wire 的 11 条安全记录也已实际验证。生产中央 Collector 与 LiteLLM 各进行过一次受控 `up`；当时 `HostConfig.Mounts` 恰好三项对象的纯排列差异触发严格 guard，完成唯一差异证明后通过 accept-only 收口，中央部署现已 accepted，没有重放 `up`。既有 ES 已真实摄入 9 条安全记录，已知分类值扫描及向量内容检查均为 0 命中；不把扫描输入口径当成实际去重值数量，也不把已知零命中扩大成全部未知内容的安全证明。旧中央投影丢失 legacy call hash，显式调用匹配为 false，不能凭时间相邻伪造逐调用关联。

四个有限属性的 Collector 补丁已完成独立原生验证与生产验收：25 个合成输入向量在 file、Docker wire 两路径各保留 11 条安全记录，0 canary 命中；生产仅 Collector 一次受控重建，LiteLLM 未重建，其他 33 容器连续，Engine owner/知识数据与心跳保护通过，Collector 切换时已有的 6 个 deployment 及 5 个 scoped 服务密钥全行摘要保持；这不限制后续 UI 认证 token 或管理变更，也不声明运行库永久只有 5 个 key。全局隐私过滤不变，只有 LiteLLM pipeline 使用增加四个 typed `fleet.llm.*` 字段的独立隐私克隆。ES `dynamic:false` 保持，新字段可在 `_source` 查回，不声称已索引。

M2 的 LiteLLM/Collector 完整 stdout、stderr、本轮只读 DB 快照与 ES 全部 3 条命中已独立审计：33 个已知值的原文、编码变体与递归字段扫描均为 0 命中，向量内容检查为 0；stdout 3 条与 ES 3 条的 call hash 及四字段逐条精确匹配。该范围只证明本轮捕获窗口与已知值检查，未证明 Java→LiteLLM 精确 call join；安全 callback 的 `completion_verified` 仍为 false，不声称 `generationCompletionVerified`。旧投影的匹配失败与原生 raw OTel 隐私失败均保留。

原生 metrics 的鉴权正负例、独立非 admin metrics key 的推理及管理路由拒绝、完整指标正文的已知密钥及编码变体检查已取证。Prometheus 原生 key job 经唯一一次 `SIGHUP` 热加载后 14/14 targets UP，没有重启 Prometheus。原生 LiteLLM UI 已实际登录，6 模型、14 个 mode 选项（含 OCR）和原生高级参数可见；这不表示所有类型或 UI 管理功能已验收。Grafana 已实际登录并读回五面板，M1/M2 的请求及 Token 脉冲可见。

M2 后完整 metrics 的已知值检查为 0 命中，metrics key/user scope 未变。延迟桶共 72 条系列；同一 1 分钟窗口、15 秒步长查询 P95 共 23 个样本，3 个有限值、20 个 NaN，有限最大值约 1.85 秒，浏览器显示对应短线；输入/输出 rate 各有 3 个正值样本。稀疏且无新请求观测的窗口产生 NaN 正常，不补零或修改查询。该 P95 是固定 LiteLLM 回调处理直方图的分位，并非完整业务链路 SLO。callback failure family 暂无样本，面板如实显示无数据，不能由此推断 0 次失败。未来 Engine 基线变化后仍须重新预检并审计动态指标正文；这些局部界面与监控结果不替代费用预算、Prompt CRUD 或任务绑定验收。

**生产接入当前保持安全结构化日志，OTel 保持关闭，不能将“关闭”写成“追踪已验证”。** 官方 exporter factory 的小范围安全投影另行验证；其注册不天然覆盖 key/team 动态 exporter 或已有全局 provider，不能为了追踪复制一套治理框架，也不用中央清洗替代生产端证明。只有实际原始出口与相应绕过负例通过，才允许启用中央 OTel。后续新增 provider 或扩大请求覆盖仍须独立验收。Prometheus 必须使用非 admin 主体的独立 metrics key，不用 master；Grafana 复用已有 datasource，空数据如实显示。任何未知写入结果都只能 GET 读回，不能自动重放生成请求。

本目录和设计文档完全不进入 Maven 制品，按 Git 提交与配置 SHA 交付，不重复发布同版 SDK。Java 客户端、AI 存储和业务服务的版本、CI、部署及实际验收分别记录。

参考：[LiteLLM](https://github.com/BerriAI/litellm)、[支持的接口](https://docs.litellm.ai/docs/supported_endpoints)、[OCR](https://docs.litellm.ai/docs/ocr)、[X File Storage](https://github.com/dromara/x-file-storage)、[Dify](https://github.com/langgenius/dify)、[RAGFlow](https://github.com/infiniflow/ragflow)。Dify/RAGFlow 用于交互与职责参考，不引入另一套工作流或模型管理后台。

## 文件存储的实际迁移范围

AI 1.6.7 与 admin-web 1.1.0 已交付 main/CI。现有 MinIO 连接经管理 API 一次导入，平台与原 300 秒上传/300 秒查看/20 MiB 策略保值；真实 WRITE/EXISTS/READ/VERIFY_PRIVATE/CLEANUP 五步通过且 owned 对象已清理。匿名 401、租户管理员 403、平台管理员 200 已取证；同 key 原操作数据查回一致、换内容重用 key 返回 409，拒绝及重放后三张控制表和时间戳不变。密钥、明文凭据及密文均不进入管理 API 结果。

当前文件表基线为 0，历史平台/null 读取只由针对性 fixture 证明；OSS、COS、S3 为官方 SDK/XFS 回环验证，不冒充真实云凭据已配置。Nacos 的旧 storage.yaml 保留私有回滚来源，新 AI 三 profile 不再导入；已在实际 DB 迁移/新版本与读写验收后，仅 CAS 移除 ai-spring.yaml 的旧 ai.oss，其他 typed 值与 metadata 保持。浏览器已验证真实连接、生效/探针版本、KEEP 不回显旧密钥、无改动禁保存、还原后禁保存、本地时区和 MiB 展示。
