# 统一 AI 模型与文件存储管理设计

状态：用户已选择 LiteLLM 并授权实施；空控制台属于首次部署历史阶段，当前已导入六个 deployment、五个 service user 和五个 scoped key，业务迁移按阶段验收。核查时间：2026-11-11（Asia/Singapore）。

## 权威配置与主流组件

模型部署、凭据、virtual key、预算与用量由 **LiteLLM 原生管理台**统一维护。业务后台提供只读目录、能力分类与原生管理入口，不再实现第二套模型增删改查。参考 LiteLLM 官方固定源码，以及 Dify/RAGFlow 的模型/知识库职责划分；不读取公司项目或复制公司业务。

文件存储使用 **X File Storage 2.3.0** 原生适配器。OSS、COS、MinIO/S3 连接与策略由 AI 控制台和 AI PostgreSQL 维护；凭据独立 AES-256-GCM 加密，根密钥与允许 origin 仅来自该服务 OS 环境。Nacos 保留数据源、发现、队列、环境与治理参数。默认上传平台变化不重写旧文件平台；禁用上传仍保留旧文件读取；有引用或默认平台禁止删除连接。

业务 provider 选择、角色 Prompt、记忆与知识库权限继续由所属业务服务维护，不转给模型网关。当前已有 provider 选择与只读目录入口，完整 Prompt CRUD、任务到模型的绑定管理仍缺；不能把现有 Prompt 表或框架支持写成管理页已实现。所有新增控制面读写要求平台超级管理员与管理权限；普通租户管理员不得读连接配置或写全局模型选择。

执行层生成参数的权威来源必须按显式 `SERVICE` / `DEPLOYMENT` 选择核对；前者保留服务参数，后者由原生 deployment 提供默认。Google direct 参数沿用原合同，不用框架支持或源码 flag 推定所有运行参数已页面化。

模型能力类型为 CHAT（含多模态）、EMBEDDING、RERANK、OCR、AUDIO_TRANSCRIPTION、AUDIO_SPEECH、IMAGE、VIDEO、OTHER。框架支持、已配置、业务已接入、实际验证通过四种状态分别取证；接口支持按具体 provider 和版本核对。本地文档解析/OCR 不因云 OCR endpoint 可用自动迁移。

## 固定版本、镜像与证据边界

[官方 v1.108.4 发布页](https://github.com/BerriAI/litellm/releases/tag/v1.108.4)对应源码提交 `e6f080fb5633aa71ea81b652bd72069aff1f0228`，发布时间为 2026-11-09T23:00:51Z。该页明确列出主镜像和 `non_root` 镜像，不猜测未验证的 tag。

| 官方镜像 | OCI index SHA-256 | linux/amd64 manifest SHA-256 | 镜像默认用户 |
| --- | --- | --- | --- |
| `ghcr.io/berriai/litellm:v1.108.4` | `17921779153960f086e2660994cc398fc76ed65c531765177803b9ae0a25d97a` | `6786696c8ce30bc1628dd82443f5cbc63f6bf926e9e273ea773aca0f201e9247` | `root` |
| `ghcr.io/berriai/litellm-non_root:v1.108.4` | `f14a4cd90546d56a673a3a4240199da54f1912138444c3b7ab9705e01df17cdf` | `37d91f124eeb23b80a19634a5951091639df236b31cfd6ab238f9f9012097d94` | `65534` |

推荐模板固定第二项 index digest。两项 config 均带 `org.opencontainers.image.source=https://github.com/BerriAI/litellm` 和上述 revision，config/manifest/index 的内容散列已逐级核对。压缩层合计主镜像约 420 MB、non_root 约 644 MB；后续已按相同 non_root digest 在 NAS 拉取并运行，发行包版本 1.108.4、UID 65534、专属数据库和 readiness 已核；这不扩展下述供应链证明边界。

使用发布页固定的 [cosign 公钥](https://raw.githubusercontent.com/BerriAI/litellm/e82a3c646908ba0a6ee0beddc4310d7a6cac4065/cosign.pub)，其文件 SHA-256 为 `e3fe3e08c44b16407fe0b37c43b4f8b603b3a0f4ee74c29e647bc022d762c2c0`。已用既有 OpenSSL 验证两项 index 的 ECDSA-SHA256 签名负载，且负载内 repository/digest 精确相符；另核主镜像同 digest 的带签名 DSSE SPDX SBOM。该 `.att` 是物料清单，不是 SLSA 构建来源证明。**未执行 cosign、Rekor/SCT/透明日志包含证明、镜像扫描或构建重现**，不能把这些未执行项写成通过。部署前需读回同一 digest、source/revision、发行包版本与运行 UID。

## 近期官方安全范围

- [2026-03 官方更新](https://docs.litellm.ai/blog/security-update-march-2026)：恶意 PyPI `1.82.7`、`1.82.8`；该事件关于 Docker 路径的结论不能泛化到后续事件。
- [2026-10 官方攻击链记录](https://docs.litellm.ai/blog/security-incident-oct-21-2026-attack-chain)与[受影响制品公告](https://docs.litellm.ai/blog/security-incident-oct-21-2026)：`1.107.0` 的 PyPI 和 Docker/GHCR 制品受影响，含当时指向它的浮动 tag；GitHub 源 tag 本身不能证明发布镜像无恶意。本文不用 `latest`、`lts`、`main-*` 或仅版本号代替摘要签名。
- [GHSA-7hp6-4w63-5g45](https://github.com/BerriAI/litellm/security/advisories/GHSA-7hp6-4w63-5g45)：认证用户利用加密密钥跨用途复用升级为管理员。公告列出的修复线为 `1.100.4`、`1.101.3`、`1.102.2`、`1.103.1`、`1.104.0rc2`；修补后的 bearer token 在固定源码中使用带用途前缀的 AES-GCM AAD。独立 salt/master 值不能代替补丁。
- [GHSA-w4c7-34wj-hqm8](https://github.com/BerriAI/litellm/security/advisories/GHSA-w4c7-34wj-hqm8)：Enterprise 管理端点未认证文件读取；公告各维护线修复为 `1.103.10`、`1.104.8`、`1.105.6`、`1.106.5`。保留 Enterprise 范围，不能误报为所有 OSS 功能受影响。
- [GHSA-g5ff-637f-6q2m](https://github.com/BerriAI/litellm/security/advisories/GHSA-g5ff-637f-6q2m)：低权限用户借 Vertex 凭据配置读取本地文件，修复版本 `1.95.0`。

v1.108.4 在上述公告的已知影响区间之外。这是版本范围核对，不是“无漏洞”证明；实施前重新核官方公告并验证相同摘要，发现新阻断即停止。

## 运行合同与验收次序

1. 固定镜像摘要、独立数据库/role、独立 master/salt/bootstrap UI 密码；只新增一个 UID 65534 服务，保留原 33 个容器。仅 loopback 发布，后续面向业务网络开放须精确私有接口和端口核查。数据库独立不代表集群级物理隔离，其他库 PUBLIC CONNECT 另行如实报告。
2. 模型目录存 DB；首次空台与合成验证阶段不导入真实 provider key，完成该阶段后再按独立账本一次导入已有模型及业务 key。禁用后台模型健康调用、自动模型信息刷新、自动重试/回退/镜像请求。配置及 callback 放 root 管理的 `/etc`，实测运行 UID 无写入、替换或 chmod 权限。
3. 合成上游单独记录 chat/SSE/Embedding1024/Rerank、OpenAI 错误/取消与 Gemini native 成功/流式/错误/取消；每场景唯一尝试，native query provider key 与 thinking 配置保持，不把 virtual key 当上游 key。
4. 完整保存 stdout 和专属数据库全部审计/日志表，检查合成正文及真实 key 的原文/编码形式。首轮协议后实际发现两处 stdout 上游错误正文，隐私结果失败；保留证据，安装官方 CustomLogger 白名单扩展及原生日志级别控制后，用全新 run 复验，禁止删除旧失败日志来伪造通过。
5. OTel 关闭时不得称其已验收。先在同网络命名空间的 loopback sink 捕获实际原始 OTLP protobuf，逐字段核对 parent 链、事件、状态、错误文本、URL、baggage 和所有正文/密钥；raw 出口通过后才接既有中央 Collector。中央安全投影不能替代 raw 出口证明。实际原生十场景批次已证明父链和响应 ID 正确，但完整 raw 的异常属性/事件/状态命中合成正文，隐私失败。生产模型管理可在 OTel 关闭且安全日志/权限通过的条件下推进；不为该可选追踪功能另造治理框架，官方 exporter 扩展须单独证明出口与绕过边界后才启用。
6. 独立 metrics-only virtual key 的实际正负例通过后，添加一个 Prometheus job，校验后热加载，不重启现有 Prometheus。该原生 key job 已唯一一次 SIGHUP 热加载，14/14 targets UP；复用 datasource 的五个 Grafana 面板已由 API 配置，浏览器实际登录并显示 M1/M2 请求、Token 脉冲与 M2 稀疏 P95 短线。无样本保留无数据，不填假零；本轮页面结果不扩展为费用、全部 UI 功能或完整业务链路 SLO 验收。
7. 逐个导入当前已有真实 provider、物理模型与最小业务 key；Nacos/Engine 参数逐项备份、CAS 和读回。Embedding 物理模型与 1024 维保持；图谱模型版本与缓存身份保持，不自动重建向量库或图谱。
8. 真实业务验证用新的明确场景和次数上限，独立记录流式、取消、隔离、检索与图谱结果、日志/trace/usage。已有冻结付费批次不重放。未知写入结果只 GET 查询；失败保留证据，不重复 POST。

## 当前运行验收状态

六个 deployment、五个非 admin service user 及五个 scoped key 已一次注册。DeepSeek、Qwen Plus、Embedding、Rerank 与图谱 Qwen 五项受限协议验证通过；Google native 唯一一次请求在 90 秒客户端超时后为 `UNKNOWN`，未重发，不能推断上游未执行或无费用。Embedding 保持原物理模型与 1024 维；模型费用及预算状态仍为 `NOT_VERIFIED`。

Engine `0.3.0` 已实际部署，新 worker readiness、active lease 和心跳推进通过。M1 真实有界业务链已完成一次上传、自动图谱 `SUCCEEDED`、A/B 两次检索及来源隔离、Qwen SSE、文档内 gold 与 memory 一致性读回，并恢复 Google 业务默认，HTTP 链路通过；不重放该轮，也不扩大为专业医疗准确性或所有业务质量通过。

原 M1 的审计 `tenant_id=1` 与 finish reason 被覆盖成 `other` 的实际 finding 均保留，不回填历史。AI `1.6.8` 审计租户写入、`1.6.9` 遗留微信服务端退役，以及最终 AI `1.7.0` 通用 404/固定 500 隐私修复和 Agent `0.3.2` finish 保留均已交付；精确 CI [AI 42932889150](https://github.com/sunkailun1992/ai/actions/runs/42932889150) 与 [Agent 42928881554](https://github.com/sunkailun1992/ai-agent/actions/runs/42928881554) 均为 SUCCESS。

M2 已在 AI `1.7.0`/Agent `0.3.2` 实际执行一次新的 Qwen 完整 SSE，复用 M1 文档及原图谱终态，无新上传、建图或显式检索；Gold、A 两条/B 空和 Google 全部选择 metadata 恢复通过。已验证租户下恰有一条成功审计行，assistant SHA 与响应一致，finish 为 `stop`，input/output usage 447/55。它只验证此次审计与流式修复，不冒称重新验证图谱、Java→LiteLLM 精确 call join 或 `generationCompletionVerified`，usage 也不是费用预算证据。

原生 OTel raw 隐私仍为失败（RED），生产保持 OFF。中央日志使用原生 `fluent_forward` 与 OTTL：25 条合成输入过滤后保留 11 条安全记录，真实 Docker wire 的 11 条安全记录已实际验证。生产 Collector 与 LiteLLM 各一次受控 `up` 后，曾因 `HostConfig.Mounts` 中恰好三项对象的纯排列差异停在 guard；该唯一差异严格证明后已 accept-only 收口，中央部署现为 accepted，没有重放 `up`。既有 ES 已真实摄入 9 条安全记录，已知分类值及向量内容扫描均为 0 命中；此处不报告未核实际去重值数量。legacy call hash 未被旧投影保留，显式调用匹配为 false，不能伪造关联。

四个有限属性的 Collector 补丁已完成 file/wire 独立原生验证（25 个合成输入向量→每条路径 11 条安全记录、0 canary），生产只 Collector 一次重建、LiteLLM 不重建、其他 33 容器连续，Engine owner/知识数据与心跳保护通过，Collector 切换时已有的 6 个 deployment 及 5 个 scoped 服务密钥全行摘要不变；该历史结论不限定后续 UI 认证 token 或管理变更，不声明运行库永久只有 5 个 key。全局隐私规则与其他 pipeline 原样保留，只有 LiteLLM 独立隐私克隆增加四个 typed `fleet.llm.*` 字段；ES `dynamic:false` 不变，新字段仅 `_source` 可见，不声称已索引。

M2 完整 LiteLLM/Collector stdout、stderr、本轮只读 DB 快照与 ES 全部 3 条命中已独立审计，33 个已知值的原文/编码变体及递归字段 0 命中、向量内容 0 命中；stdout 3 条↔ES 3 条 call hash 与四个 typed 字段精确匹配。该结果限定本轮捕获窗口、已知值和 callback→ES，不扩大为未知内容永不泄漏、Java 侧逐调用关联或完整业务终态。

Prometheus job 已唯一一次 SIGHUP 热加载，14/14 targets UP；五面板已实际浏览器登录验收。M2 完整 metrics 已知值检查 0 命中，metrics key/user scope 不变；72 条延迟桶系列对应同一 1 分钟窗口、15 秒步长的 23 个 P95 样本，3 个有限值/20 个 NaN，有限最大值约 1.85 秒，浏览器短线可见；input/output rate 各 3 个正值样本。无新观测窗口的 NaN 如实保留，固定 LiteLLM 回调直方图的 P95 不代表完整业务链路 SLO。callback failure family 无样本时显示无数据，不补零，也不推断零失败。

Google NAS native 仍是唯一一次 90 秒 UNKNOWN，不重试，保留 Agent 原 direct 路径；AI/Agent/RAG 九份源码 profile 无 `ai-model-secrets.yaml` 直接 import，旧远端项只保留显式回滚，Agent 专属 Google 字段仍必须保留。原生 LiteLLM UI 本轮已登录，6 模型目录、14 个 mode 选项（含 OCR）及原生高级参数可见；未调用类型与全部 UI 管理能力不能由可见性推断通过。完整 Prompt CRUD/任务绑定 UI、模型费用与预算、OSS/COS/S3 真实云账号读写仍未验收。

## UI 与业务兼容

原生 UI 路径按固定 1.108.4 源码校验；浏览器不持管理 master 或目录只读 key。服务端 GET `/v1/model/info` 使用独立 key，限制超时、大小、深度与记录数，只返回 alias/provider/mode/有限元数据。deployment ID 可在多个 alias 间重复；使用 `(id, modelName)` 标识目录记录。目录响应没有可靠运行版本时返回 null；所有未实际调用的健康状态为 NOT_CHECKED。

OSS 版本的部分高级管理参数不开放，已实际确认 `allowed_passthrough_routes` 属 Enterprise；使用 OSS 支持的 `allowed_routes` 并用越权负例验证。业务 key 绑定专属非 admin service user，并限制模型和路由；预算字段和价格表存在不等于费用及预算拒绝已经验收。

生产设置 `general_settings.allow_client_side_credentials=false`，模型 deployment 不开放 client-side endpoint 配置。固定版本源码及实际负例只证明已核选定 endpoint/config 覆盖受到限制，`api_key`、`custom_llm_provider` 等原生 BYOK 能力并非被该开关全面禁用。当前应用 key 仅由受信任 Java/Engine 后端持有，业务 typed DTO/SDK 不透传用户任意 LiteLLM kwargs；浏览器与普通用户不获得 key 或网关直连入口。这是应用隔离边界，不宣称全局禁用 BYOK，也不新增自建 hook 或第二套策略框架。

存储管理写入使用 requestId 操作账本、版本 CAS 和服务端 payload fingerprint；未知响应通过同 requestId 查询。探针只写本轮前缀下的小合成对象，核写/存在/签名读 SHA/匿名拒绝/精确删除；不得创建 bucket、修改 ACL/CORS 或触碰业务文件。当前 ai_file_object 实际基线为 0，旧平台/null 兼容以专项 fixture 验证，不能称真实历史文件回归通过。

## 回滚与交付

每次 NAS 修改以 fresh canonical 内容摘要为 CAS，只 `up --no-deps` 当前目标服务；其他容器配置/生命周期摘要保持。业务切流前失败仅停新增服务；切流后先按 CAS 恢复该业务之前的连接并验证，再停新增服务。保留 LiteLLM DB/salt/备份，不自动 drop 或降级 schema。存储旧 Nacos 快照保留为私有回滚材料，活动服务不再导入；公开旧模板归档，不列入自动删除清单。

当前阶段性进度及公开示例见 [运维说明](../../../ops/github-dev/ai-gateway/README.md)。Git、CI、Maven 制品、配置发布、容器运行和真实业务验收分别取证；未执行项保留未完成状态。该 ops/文档边界不进入 SDK JAR，不重复发布同版 Maven；业务代码按所属项目升版验证。
