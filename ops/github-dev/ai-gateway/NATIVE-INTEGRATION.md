# 原生中央日志与监控片段

本目录新增文件是公开、无密钥的标准配置片段，不是完整部署文件，也不包含私有 Compose、实际 NAS 地址、凭据或一次性恢复脚本。固定适配版本为 Docker `27.3.1`、Docker Compose `2.29.7`、OpenTelemetry Collector Contrib `0.164.0`、LiteLLM `1.108.4`。后续版本必须重新执行原生校验和有区分力的合同验证。

## 来源与文件完整性

以下三份 JSON 从已冻结的运维候选逐字节复制，公开文件中的占位符保持不变：

| 公开文件 | 冻结来源 | SHA-256 |
| --- | --- | --- |
| `collector.fragment.json` | `central-logs-correlation-v1` 四字段原生验证片段 | `5dd619563a8ca335d2bb445fe279a3dcaf673469c8591779a2b51f8de753ba79` |
| `prometheus-job.template.json` | `monitoring-apply-v3` job 候选 | `8435035056c5e69f300af7e2f333193f061f4a93eb83784932db9c3c64eb3e9f` |
| `grafana-dashboard.template.json` | `monitoring-apply-v3` 五面板候选 | `39ce11854dc44e0ec0b9b619c636940b9bc656f2b11e1bc467b770e8720c46d8` |

`logging.compose.example.yaml` 将冻结 `docker.logging.fragment.json` 的全部 driver/options 转为标准 Compose overlay，值和类型保持；其来源 JSON SHA 为 `d489b63eea2d9758895ce9a537310fc9cca07ed3f1e11c1c3aad2082517e230a`。overlay 仅追加 Collector 的一个 loopback TCP 端口，并设置 LiteLLM logging，不附带 image、网络、环境变量、凭据或卷变化。

## 中央日志集成顺序

1. 先读回并备份现有完整 Collector 配置及权威 Compose，核对实际版本、容器身份和文件 SHA。将片段中的 receiver、processor 和独立 pipeline 合入完整文档；旧 receiver、pipeline、auth、exporter、全局隐私规则及 Conductor offset store 均保留。`transform/litellm_privacy` 必须与既有 `transform/privacy` 完整内容核对，只在 log attributes 的 keep 白名单增加下述四字段；原全局 processor 不改，只有 `logs/litellm_fluent` 使用该独立克隆。`elasticsearch/runtime` 仍精确引用既有 exporter；全局隐私源有漂移时停止并重新核对、原生验证，不能直接覆盖。
2. 对合并后的完整配置运行既有固定版 `/otelcol-contrib validate`。保留错误诊断，校验失败不能继续；仅对片段做 JSON 解析不等于原生 Collector 校验通过。
3. 同时生成运行文件与权威 Compose 中 shared init 的持久化正文及 SHA 校验更新。Compose `2.29.7` 原文与 `config --format json` 的美元符号表示需要 `$$`；真正的进程内 quoted heredoc 使用 `$`。首次集成的完整差异仅允许批准的 Collector 正文/hash、一个端口及 LiteLLM logging；已有中央日志部署上的四字段增量只允许 Collector 正文/hash 更新，端口与 LiteLLM logging 不变。不能用独立 overlay 的有效性代替整份语义差异验证。
4. overlay 不能独立 `up`，也不能泛化重放 shared init 或重建整个项目。首次集成按已批准顺序先使 Collector 就绪，再使 LiteLLM 的 Fluentd driver 生效；后续四字段增量已只对 Collector 执行一次显式 `--force-recreate --no-deps --no-build --pull never`，没有再次 `up` LiteLLM。每条命令先记录意图，未知结果只读验收，不重放。记录其他容器的完整配置及生命周期，保持 Engine owner、知识数据和模型/key 全行摘要稳定。
5. 独立查回 Docker 日志缓存、Collector 接收、Elasticsearch 文档和 Grafana 查询结果。只有实际数据读回才证明中央日志可查，不能把端口可连、容器健康或配置通过当成日志摄取成功。

Fluent Forward receiver 在容器内监听 `0.0.0.0:24224`，宿主只发布 `127.0.0.1:14324`。该协议入口未配置 TLS 或握手认证；loopback 发布、固定 tag/container 检查和内容白名单保留可信 NAS/容器边界，不提供密码学上的生产者身份。不要额外发布 UDP 或公网监听。

白名单处理清空正文、自由 attributes、event name 和原始上下文，仅接受固定服务/版本/环境及有限结果组合；合法非零 trace/span 才提升为标准字段。流式关闭不冒充完整生成成功。非阻塞缓冲和本地缓存均有上限，不能承诺零丢失；Collector 重建也可能短暂影响其他遥测。

LiteLLM 独立隐私克隆仅增加四个有类型、有受限条件的 attributes：

| 字段 | 类型及条件 |
| --- | --- |
| `fleet.llm.call_id_sha256` | 64 位小写十六进制字符串，仅 `correlation_available=true` 时设置。 |
| `fleet.llm.terminal` | 已通过固定 event/outcome 组合校验的 boolean。 |
| `fleet.llm.completion_verified` | boolean；当前白名单只接受 `false`，不由关闭流推断完成。 |
| `fleet.llm.correlation_available` | 已通过来源、schema 与 hash 条件校验的 boolean。 |

ES 模板维持 `dynamic:false`，四字段可从完整 `_source` 读回，不声称已经索引或可按字段检索。原全局隐私规则及其余日志 pipeline 不变。

## 已有证据与未完成项

固定版本的独立原生 file 与真实 Docker Fluentd wire 验证均已完成：25 个合成输入向量过滤后，每条路径各保留 11 条安全记录，canary 命中为零，四字段类型和投影逐条核对通过。公开片段与已验源逐字节一致；运行回执保留在受限证据中，本次文档更新没有重新执行 Docker 或发送模型请求。

首次中央日志部署曾因原生挂载/环境列表的纯排列触发严格 guard，唯一差异证明后通过 accept-only 收口，没有重放已发命令；旧失败与恢复历史保留。此前旧投影未保留 call hash，其显式逐调用匹配仍为 false。随后四字段补丁已实际完成：只 Collector 一次受控重建，LiteLLM 未重建，其他 33 容器连续，Engine owner/知识数据与心跳保护通过，Collector 切换时已有的 6 个 deployment 及 5 个 scoped 服务密钥全行摘要不变；该历史边界不表示整个运行库永久只有 5 个 key，也不包含后续 UI 认证 token 或管理变更。

补丁后的 M2 只执行了一次新的 Qwen 完整 SSE，复用 M1 文档和已完成图谱，没有重新上传、建图或显式检索。Gold、A 会话 2 条/B 会话 0 条、Google 全部选择 metadata 恢复，以及已验证租户下唯一成功审计行与 assistant SHA 一致性均通过；AI `1.7.0`、Agent `0.3.2` 的实际 finish 为 `stop`，input/output usage 为 447/55。原 M1 审计默认租户 `1` 与 finish 被覆盖为 `other` 的 finding 不回填。

M2 的 LiteLLM/Collector 完整 stdout、stderr、本轮只读 DB 快照和 ES 全部 3 条命中已独立审计：33 个已知值的原文、编码变体与递归字段检查均为 0 命中，向量内容检查为 0。stdout 3 条与 ES 3 条的 call hash 和四个 typed 字段精确匹配。该窗口内已知值检查不证明所有未知内容或永久零丢失；LiteLLM callback→ES 的匹配不等于 Java→LiteLLM 精确 call join，也不把 `completion_verified=false` 改称完整生成终态证明。

生产 OTel 继续关闭。中央日志白名单不是原始 OTel 隐私验收的替代，也不将此前原生 raw 出口正文命中的失败改写成通过。

## Prometheus 与 Grafana

两份公开 JSON 保持无私密值的模板；实际 Prometheus 原生 key job 已完成校验及唯一一次 SIGHUP 热加载，14/14 targets UP，既有 Prometheus 没有重启。Grafana 五面板已 API 配置并在浏览器实际登录读回 M1/M2 的请求和 Token 脉冲。原生 LiteLLM UI 本轮已登录，6 模型目录、14 个 mode 选项（含 OCR）和原生高级参数可见；这些界面结果不代表每一种能力已调用或全部 UI 功能已验收。

M2 完整 metrics 的已知值检查为 0 命中，metrics key/user 只读 scope 未变。延迟桶共 72 条系列；同一 1 分钟窗口、15 秒查询步长的 P95 结果共 23 个样本，其中 3 个有限值、20 个 NaN，有限最大值约 1.85 秒，浏览器实际显示短线；输入/输出 Token rate 各有 3 个正值样本。无新观测的稀疏窗口产生 NaN 符合原生 histogram/rate 语义，不修改查询或补零。此 P95 是固定 LiteLLM 回调处理直方图的分位，不能扩展为完整 Java/RAG 业务链路 SLO。callback failure family 当前没有样本，面板如实显示无数据，不能据此宣布 0 次失败。

后续重新部署或扩大覆盖时继续遵守：

- job 中的 `REVIEWED_LITELLM_SERVICE:4000` 和 `/REVIEWED_EXISTING_PRIVATE_MOUNT/litellm_metrics_token` 必须替换为已核定的内部 DNS 与既有私有只读挂载路径。token 由独立 metrics-only 非 admin 主体提供，文件为实际 Prometheus UID/GID 可读的 `0600`，不把 token 内容放入 Git。另行读回 scope，并证明推理与管理路由拒绝。
- 所有 actual metrics 必须先完整审计已知密钥及编码变体、所有 label 名称和值，再选择有限 family 抓取；过滤不能用来掩盖原始出口泄漏。Engine 或 canonical 基线变更后重新预检，动态指标正文的旧 SHA 不可复用为新证据。
- Grafana 模板中的 `__ACTUAL_PROMETHEUS_DATASOURCE_UID__` 只在确认既有 Prometheus datasource 后精确替换。保留五面板、有限标签和“暂无数据”语义；没有请求、token 或错误样本时不补假零，没有费用或预算准确性的暗示。
- 实施前用原生 `promtool check config` 验证完整候选及实际 token 可读性；对已有 Prometheus 进程核 PID/start time 后只发送一次已记录意图的 SIGHUP。未知信号结果只读验收，不重复 reload，不重启原有容器。Grafana 复用现有 provider 自动加载，并分别核 API 和浏览器。

本批只涉及不进入 Maven 制品的 `ops/` 文件，按 Git/配置 SHA 独立交付，不提升或重复发布 Java SDK 制品。真实配置更新须 fresh CAS、保留备份和阶段证据；失败不自动大范围回滚或清除证据。
