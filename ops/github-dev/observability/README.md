# 个人微服务观测与极空间部署

## 目标与状态

覆盖 gateway、user、message、ai、ai-agent、ai-memory、rag、report、mcp，以及Python rag-engine。这里是独立观测编排，不包含业务服务、数据库或RAG图谱重建。代码/配置检查、镜像启动、目标采集、日志入库与真实链路分别验收；文件存在不表示已安装。

技术路线：Micrometer Prometheus 指标；OTel agent/Python SDK→Collector→Jaeger；运行日志仍进既有ES，Grafana统一查看。Java agent仅traces，metrics/logs exporter关闭，防止重复SDK/重复计数。

## 是否安装到极空间

可以把以下4个组件单独部署到极空间。固定tag来自官方release，现场解析目标架构digest并写私有.env后再启动，不使用latest。

| 组件 | 固定版本 | 模板硬上限 | 持久化 |
|---|---|---|---|
| OTel Collector contrib | 0.164.0 | 0.5CPU / 384MiB | 有界内存队列，无业务数据 |
| Prometheus | 3.16.0 | 0.5CPU / 768MiB | named volume，7天/2GB先到先限 |
| Grafana | 13.3.0 | 0.5CPU / 512MiB | named volume，禁匿名/注册 |
| Jaeger v2 | 2.23.0 | 0.5CPU / 1536MiB | Badger named volume，24小时TTL；Go软限768MiB |

合计是2CPU、3200MiB的配置上限，不是实测RSS、最低要求或NAS当前余量。部署前核现有进程、可用内存/swap、CPU、磁盘空间与IO、目标架构和端口冲突。若余量不足，先改变观测部署位置或参数，不挤占现有Engine/OCR/Neo4j，不拿旧快照证明当前容量。

Jaeger持久化Badger在持续采集时曾触及原768MiB容器上限并被内核OOM终止；重启后的`State.OOMKilled=false`不能代替历史内核与cgroup证据。本模板为Jaeger保留1536MiB硬限，并设置`GOMEMLIMIT=768MiB`。Go软限只约束runtime管理的内存，Badger mmap及OS代持文件页等并不全部计入，不能把它当成进程RSS硬限或“不会再OOM”的保证。[Go官方内存限制说明](https://pkg.go.dev/runtime#hdr-Environment_Variables)

应用前记录权威Compose的SHA、Jaeger容器身份、重启数、`memory.events`的OOM计数及内存基线；只更新Jaeger的这两项预算，保持镜像、Badger数据卷和24小时TTL。观察至少覆盖原故障复现窗口，并包含实际采集和查询，核对新旧Trace ID可读、重启/OOM计数未增长、内存趋势有余量后再报告该窗口通过。回滚仅恢复已审核的配置差异并保留原卷；原768MiB预算已出现OOM，恢复它会重引该风险，需要同时重新评估采集负载或部署位置，不通过删除trace或缩短TTL来伪装稳定。

Java若运行在Mac，NAS上的Collector不能直接读Mac日志。`collector-agent.yaml`用于应用运行主机的轻量文件采集进程；已有采集器优先复用。同宿主时可在中央Collector合入filelog接收，不重复起一套完整监控栈。本仓没有自动安装本机Docker、Collector或任何系统工具。

## 私有配置与启动前检查

1. 将`.env.example`复制到Git忽略的`.env`，填写已核私网绑定和既有ES地址。地址不含内嵌凭据。API key仅私有环境提供，权限限新运行日志索引。
2. 建立仅属主可遍历的`.private/`（0700），按现有权威配置提供`actuator_token`和`engine_token`；它们分别匹配当前服务运维令牌与Engine服务密钥，不能随机重置。另生成独立`otlp_token`和`grafana_password`，不回显或提交。Compose file secrets是只读bind，原文件0600可能让容器非root UID无法读取；给精确文件设置可读mode（例如受0700父目录保护的0444），或为容器UID设置专用ACL，并在目标实例验证，不能放宽整个私有目录。仅将需要的文件挂给对应容器。
3. 私有`targets.json`从`targets.example.json`填真实地址。9服务必须完整；gateway和ai-agent使用受保护独立管理端口（示例17100/17310），不要把主端口或公网网关当作采集入口。其它服务仅受限私网可达。
4. 生成Prometheus配置，缺服务、URL/凭据/路径注入和未替换示例会失败：

```bash
python3 render.py .private/targets.json --output generated/prometheus.json
docker compose config --quiet
```

5. 在目标主机只读核容量：`docker version`、`docker stats --no-stream`、磁盘容量和现有监听。不要在日志/聊天贴完整环境、私有Compose、连接串或令牌。
6. 经具体部署授权后拉固定镜像，记录各镜像RepoDigest并覆盖`.env`，再验证容器内的配置：

```bash
docker compose run --rm --no-deps collector validate --config=/etc/otelcol/collector.yaml
docker compose run --rm --no-deps --entrypoint promtool prometheus check config /etc/prometheus/prometheus.json
docker compose run --rm --no-deps --entrypoint promtool prometheus check rules /etc/prometheus/alerts.yaml
docker compose run --rm --no-deps jaeger validate --config=/etc/jaeger/config.yaml
```

原生component校验在目标镜像中执行，YAML可解析不能替代它。

启动仅此独立项目：`docker compose up -d`。不要运行`down -v`，不要覆盖业务编排。Grafana默认宿主13000、Collector默认14318、Jaeger原生UI默认16686，均仅loopback绑定；跨主机必须显式私网绑定和网络访问源限制。`JAEGER_UI_PORT`可覆盖UI宿主端口；Prometheus和Jaeger OTLP不映射宿主端口，Grafana仍走内部网络查询。

## Jaeger兼容与完整链路搜索

当前固定Jaeger 2.23.0已移除旧的服务列表与条件搜索HTTP接口；Grafana 13.3.0 的现场只读插件 API 核出官方 Jaeger 插件 13.0.8、Elasticsearch 插件 12.9.5，插件版本与 Grafana 服务版本分别记录。Jaeger 插件仍有调用旧接口的路径。Compose通过`GF_FEATURE_TOGGLES_ENABLE=jaegerEnableGrpcEndpoint`启用官方实验性v3兼容入口，使数据源健康检查、服务/操作列表和Trace ID查询使用v3。数据源URL保持内部地址`http://jaeger:16686`，不能追加`/api/v3`。

该开关没有迁移插件的`Search`和依赖图查询路径，不能据健康检查成功宣称Grafana内完整条件搜索已支持。按服务、标签、耗时和时间范围搜索请使用看板顶部的“完整链路搜索（Jaeger）”，进入Jaeger原生UI；Grafana Explore用于输入已知Trace ID查看链路。保留当前Jaeger版本和数据卷，无需降级或迁移既有trace。兼容范围依据[官方插件13.0.8查询实现](https://github.com/grafana/grafana-jaeger-datasource/blob/v13.0.8/pkg/jaeger/querydata.go)与[服务/操作列表路由](https://github.com/grafana/grafana-jaeger-datasource/blob/v13.0.8/pkg/jaeger/callresource.go)。

看板的“Jaeger搜索地址”文本变量默认`http://localhost:16686`，这是打开看板的浏览器所在主机。浏览器与观测服务不在同一主机时，填写浏览器可达的已核私网地址或已有受保护入口；改变`JAEGER_UI_PORT`后也要同步填写对应端口。这里不自动读取Compose环境，不在公共模板中保存真实私网地址。Jaeger原生UI默认没有登录保护，保持loopback，或仅向受信私网来源开放；本模板不创建公网入口或新增反向代理。

现场验收需经受认证Collector送入一条仅含有限元数据的合成span，再通过Grafana数据源实际查回同一Trace ID，并验证服务/操作列表及原生UI条件搜索命中；还要核对运行日志到ES的同trace关联。配置检查与HTTP健康成功各自只证明对应阶段，不能替代这组读回。

## Java与Python接入

`kellen-workspace/scripts/run-observed.py`只给选定服务bootRun JVM挂agent，先校验固定官方 agent 与本地构建隐私扩展两个 SHA-256；Gradle daemon、编译器与测试JVM不受影响。缺隐私扩展或任一摘要/版本不匹配均拒绝启用。Jar/IDE直接运行时把相同 agent 和扩展参数配置到服务JVM，不能只修改应用启动后才加载的Nacos。

- 固定 Java agent v2.33.1 内置 SDK 1.67.0，独立 `agent-extension/` 仅以 compileOnly SDK 1.67.0 编译，薄 Jar 版本 0.1.3（Java 21 编译）；应用 API/context/common 由 BOM 1.67.0 对齐 agent 内置 SDK，不创建第二个 tracing SDK。
- Java 出站前必须加载 privacy extension：强制 `otel.span.event.count.limit=0`，原始异常 events 不进入 batch 队列；exporter 包装器清理 attributes/events/status description/resource/link attributes/scope/tracestate，只保留 trace/span/parent IDs、时间、状态码和有限业务元数据。动态 URL/query、SQL、headers、prompt/body 以及未知字段不出站。自动 span 名称归一成固定 kind 操作名，仅固定业务名称原样保留；动态 route/RPC 名称不保留。中央 Collector 仍是第二道清理。
- 扩展保留实际 Java `business.outcome`、ai-agent `outcome/operation` 和 Python `rag.result` 的有限值合同；中央 native fixture 同时覆盖 Python 请求、retrieval 与 graph_build 的结果及两种 service resource，不能只用 Java span 推定跨语言合同。
- `OTEL_SERVICE_NAME`、版本与`deployment.environment.name=github-dev`保持一致。workspace 的服务 JVM 同时从 `project.version` 设置 `SPRING_APPLICATION_VERSION`，供 Boot ECS 输出 `service.version`；共享 `logging.yml` 显式输出环境 `github-dev`。
- Jar/IDE/其它部署入口也必须将确定的制品版本注入 `SPRING_APPLICATION_VERSION`（或 `spring.application.version`），并在 OTel resource 的 `service.version` 中使用同一值。缺版本时 ECS 明确输出 `unknown`，不能把它当作已核部署版本；名称取 `spring.application.name`，缺名称同样输出 `unknown`。
- `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`；endpoint与Authorization由私有环境注入。
- `OTEL_METRICS_EXPORTER=none`、`OTEL_LOGS_EXPORTER=none`、`OTEL_PROPAGATORS=tracecontext`、`OTEL_SPAN_EVENT_COUNT_LIMIT=0`。扩展还会强制关闭 events 与两种 exporter，不能由环境重新打开。
- 初期内部parent-based 10%采样，公网gateway按自身概率采样，避免调用方sampled标志强制全采。短时验收可显式100%，之后按容量恢复。
- graph build为独立trace并link提交请求，排队过期/取消/超时/worker kill由父进程保留终态；不往worker stdout写观测输出。

完整端到端实际验收：gateway→ai→rag→Engine；ai→ai-agent→ai-memory；message→user；MCP协议业务结果。report骨架只验基础指标，不编造不存在的业务。

## 日志到ES

运行日志与旧请求审计索引分离。Java用共享`logging.yml`的真实ECS字段白名单（没有message/原始异常/任意MDC）；Python使用metadata-only单行JSON，带UTC`@timestamp`。本机文件收集器只读指定日志目录，发出前清空body、白名单属性；中心再次清理。

Java文件输出需在运行环境显式设置`LOGGING_FILE_NAME`到本机安全日志目录，并设置`LOGGING_STRUCTURED_FORMAT_FILE=ecs`，文件轮转有界。不要同步写NAS远程文件造成业务请求阻塞；应用主机的Collector再异步发送。Python stderr采集/轮转按engine说明，不更改业务容器资源。

`collector-agent.yaml`读取`FLEET_LOG_DIR/*.json`/`*.jsonl`，`elapsed_ms/result`映射为`duration_ms/outcome`。从每条 Java/Python 嵌套 `service` 中只提取名称、版本、环境到 record attributes，再由 `groupbyattrs` 按三字段关联到各自 resource，随后 batch；不能在逐条转换时改同批共享 resource。没有活动 trace 的启动/后台日志也保留这三字段，缺 trace 不补造。通过独立OTLP凭据送中心。当前模板为短时有界内存队列；进程重启/队列满可丢遥测，不能宣称零丢失。部署前核文件权限、轮转行为和offset持久化需求，禁止同一日志从stdout和文件重复采集。

主机 filelog 将成对、非零、小写十六进制 `trace_id`（32 位）/`span_id`（16 位）提升到标准 OTLP LogRecord ID，并保留查询 alias。非法、全零、类型错误或只有一项的 alias 成对删除，不通过字符串截断或补零修造关联；已有完整 native ID 时以它为准统一 alias，部分 native ID 先清空再仅由合法完整 alias 恢复。未提供采样 flags 时保持未知，不推定 sampled。`@timestamp` 仍由 parser 转为事件时间，等级只接受 `TRACE/DEBUG/INFO/WARN/ERROR/FATAL`，对应标准 SeverityNumber `1/5/9/13/17/21`；未知等级不出站自由文本，使用 UNSPECIFIED。依据[固定 0.164 Log Context/枚举](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/pkg/ottl/contexts/ottllog/README.md)及 [TraceID/SpanID 转换](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/pkg/ottl/ottlfuncs/README.md#traceid)。这仍是裁剪 ECS 输入经 OTLP 导出的 raw ES schema，不声称保留完整 ECS。现有 ES 模板未索引 `SeverityNumber`，其 `_source` 值与可搜索字段须分别判断；Grafana 继续按既有 `SeverityText` 筛选等级，无需迁移索引。

回归入口是 `verify_collector_native.py`：固定 Collector 实际处理合法/非法/零 ID、无 trace 启动、native/alias 冲突、等级及时间 fixture；本机 `--collector-bin` 模式另将真实 OTLP/HTTP JSON 导出到随机 loopback 合成 sink 并断言标准字段，默认 Docker 的 `--network none` 模式仅验证同一处理链的 OTLP JSON 文件输出。两种模式均不读取应用文件或连接真实后端。此修复只交付独立 `ops/` 配置及 SHA，不进入 Maven JAR、不提升 SDK 或业务服务版本。运行配置切换前备份既有配置与 offset；失败时恢复上一配置并仅重启所属主机 Collector，保留 offset，不重放历史文件；切换成功仍需对本轮真实请求在 ES/Jaeger 中另行读回。

中央ES exporter通过`mapping.allowed_modes: [raw]`限定唯一映射，兼容ES7.17/8/9，并保留普通静态索引`github_dev_runtime_log`。固定Collector 0.164.0已忽略`mapping.mode`，仅写`mode: raw`会继续默认使用`otel`，可能要求data stream而导致逐文档拒绝；不能靠配置可解析推定raw已生效。不要放开其他mapping模式改变已审计输出合同，也不为观测新建data stream或升级既有ES。实际版本、认证、索引模板/生命周期先核。导出schema的`@timestamp`、`TraceId/SpanId/SeverityText`与白名单自定义`trace_id/span_id/event`分清，Grafana使用实际字段。[官方0.164映射配置](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/elasticsearchexporter/README.md#elasticsearch-document-mapping)

验收以真实索引读回及`otelcol.elasticsearch.docs.processed`的逐文档`outcome`为准。Bulk HTTP 200仍可能逐条拒绝，此时通用`sent_log_records`增长、`send_failed`为零不代表已入库；检查`outcome`与实际输出的HTTP状态标签（当前为`http.response.status_code`；并非每种结果都有该标签），要求安全合成日志成功写入精确索引，并通过ES与Grafana读回同一trace。既有“Collector导出失败（含ES逐文档）”面板同时展示trace导出失败与ES文档非success速率。固定版本的内部遥测实际保留UTF-8带点名称，PromQL通过`{__name__="otelcol.elasticsearch.docs.processed"}`查询，不能把它猜成下划线名称。只有Collector当前采集正常、对应成功输出计数器当前仍存在且有足够采集样本，才给尚未注册的失败序列显示`0`；目标不可达、成功计数器已stale或全部输出基线缺失仍保留无数据。成功基线仅限制补零分支，已观察到的真实错误不依赖成功计数器存在。Prometheus的`CollectorDroppedSpans`规则保留既有名称，并同时检测ES逐文档非success，避免Bulk HTTP成功掩盖文档拒绝。诊断只读取指标和错误元数据，保持请求/响应正文、失败文档输入日志关闭；没有真实文档命中不能宣称日志链路已验收。[官方bulk逐文档处理](https://github.com/open-telemetry/opentelemetry-collector-contrib/blob/v0.164.0/exporter/elasticsearchexporter/bulkindexer.go)

部署前先按 [ES mapping 准备步骤](es/README.md) 只读核现有审计索引。实体 Keyword 注解和应用重启不会自动给已存在索引补字段；本仓只提供 additive mapping JSON 与新运行日志索引模板，不执行 ES 写入。

Grafana 的 ES 数据源通过私有 `GITHUB_ES_READ_AUTHORIZATION` 提供完整 Authorization 字符串（例如 `ApiKey <编码值>`），provisioning 以 `secureJsonData.httpHeaderValue1` 引用，容器环境只注入 Grafana。权限限运行日志索引的读取，并与 Collector 的 `GITHUB_ES_API_KEY` 写入凭据分开；默认空值兼容未启用认证的 ES。数据源 `editable: false`，请改私有环境后重建 Grafana 使 provisioning 生效，不能靠 UI 临时输入覆盖模板。已开启 ES 安全的目标必须配置此值并验实际查询；本仓不生成真实凭据、不读写 ES。先发送一条安全合成事件，证实应用→采集→ES→Grafana→同一Jaeger trace。反向trace→ES日志需独立配置correlation与时间窗口并实际验收，不把默认Jaeger数据源当成已开箱双向。

## 看板、告警与故障验收

自带全9服务+Engine采集状态、HTTP请求率/P95、JVM堆与Collector错误看板。公共 Actuator 模板已仅为 `http.server.requests` 开启 histogram；P95 仍需模板实际发布、服务加载且真实 scrape 出现 buckets；业务指标按固定结果枚举聚合，不加tenant/KB/业务ID/hash标签。

“个人微服务 / 个人 Java 微服务总览”（UID `github-dev-fleet`）的采集状态按10个预期目标分别查询当前值，`1`为绿色“采集正常”，`0`为红色“采集失败”，缺少目标为红色“未发现目标”。不使用时间窗口平均值显示当前状态；同一预期目标意外出现多个实例时，任一实例采集失败仍显示失败。采集正常只表示指标端点可读，不能替代业务、日志与链路验收。

其余面板的`No data`需按指标含义判断，面板说明中也保留了这一区分：

| 面板 | 没有数据时先核对 |
| --- | --- |
| HTTP速率 / HTTP P95 | 服务刚启动是否已有两次采集、是否收到请求；P95另外需要请求分桶与窗口内耗时样本 |
| JVM堆占用 | Java目标采集正常时应有JVM指标；缺失需检查绑定与运行制品，Python Engine不提供JVM指标 |
| RAG检索结果 / 图谱构建终态 / AI、RAG、MCP业务结果 | 带标签计数器在实际调用或终态发生后注册；从未调用、进程重启或窗口内样本不足可为空 |
| Engine阶段P95 | 仅实际执行的阶段注册直方图，窗口内无耗时观测时不计算分位数 |
| Collector导出失败 | 先核Collector采集与输出基线，不能把Collector不可达或指标契约缺失解释为没有失败 |

业务无事件与采集故障分开判断，不把缺少的业务序列普遍补成`0`，也不把没有样本的P95显示成`0秒`。无需为填满图表重传文档、调用付费模型或重建图谱。

业务结果面板同时消费 `ai_agent_model_call_seconds_count`，这是 ai-agent 的 `ai.agent.model.call` Timer 导出的计数序列；实际结果标签为 `outcome`，包括 `success/failure/cancelled`，与其他业务 counter 按 `application/outcome` 聚合。Java RAG/MCP 的有限结果保留在 span 的 `business.outcome` 中。

告警先按低流量门槛、持续时间触发：目标不可达、HTTP5xx至少20样本且超过5%、JVM堆持续85%、Collector上报失败。告警规则可查询不等于已通知；Grafana通知渠道凭据和路由在目标私有设置完成，必须验证触发及恢复。RAG Engine降级/构建失败、阶段预算及AI/MCP失败在部署时验证后补相应阈值，不以低流量偶发比例自动判断专业质量。

验收必须覆盖：无/错令牌、公网管理阻断、并发trace不串、SSE成功/失败/取消、MCP HTTP200业务失败、RAG空命中/降级/超时、worker终态、Collector/ES/Jaeger停机/队列满时业务不阻塞，以及原文/秘密注入不进入遥测。保留上游5秒、Engine3秒、图扩展0.4秒预算。只复用已发布知识库做少量检索，不重抽图谱。

本机检查：

```bash
python3 -m unittest discover -s ops/github-dev/observability -p 'test_*.py' -v
```

采集状态、缺失目标与空业务序列的PromQL语义使用Prometheus原生工具回放：

```bash
promtool test rules ops/github-dev/observability/dashboard-promql-tests.json
```

该JSON文件也是合法YAML输入，覆盖正常、采集失败、缺失及stale目标、重复实例失败，以及无业务样本时保持空序列；还覆盖Collector仍在线但成功计数器已stale时不复用旧样本补零、真实错误仍可显示。`fuzzy_compare: true`仅容许Prometheus官方定义的浮点最后一位差异，避免`rate`外推结果的ULP差异误报，不设置业务误差阈值。Python检查会核对夹具使用当前看板查询；原生回放仍需目标固定版本的`promtool`，没有执行时不能用Python检查代替。

固定镜像的原生行为回放（需要已可用的 Docker，不由本仓安装）：

```bash
python3 ops/github-dev/observability/verify_collector_native.py
```

CI 还原生校验主机 Collector 配置。回放只覆盖实际配置的处理链，替换合成输入/本地 file exporter，容器 `--network none`，不会连接 ES、Jaeger、NAS 或业务服务；覆盖真实 filelog JSON parser、同一个原始 ResourceLogs 的两服务与无 trace 日志、正文/秘密清理和 RAG/MCP 实际结果属性。普通 Python 单测只验证 fixture/断言器，不代表该原生回放已执行；没有 Docker 时保留这一验收缺口。

## 退役与回滚

旧Admin/Zipkin本地模板及imports已删除并登记`retired-dataids.json`。目标Nacos旧项在新制品及配置切换读回后再按权威旧值备份清理；此目录不自动DELETE远端dataId。

旧RAG仅清理已替代的Java检索回退/MySQL派生图谱。旧表候选与恢复条件见rag的`docs/legacy-rag-retirement.md`；此编排无数据库清理动作。文档、版本、来源、Qdrant原文向量与Neo4j已发布图保留。

回滚可先移除服务JVM agent/关闭Python观测，保留业务配置；停止独立观测项目不删除卷。旧RAG实现已移除，回退需恢复相互匹配的旧制品/配置；删表后还需备份恢复，不能再使用已删除开关。


### 灰度选路排障

在 Grafana 的“运行日志（Explore / Elasticsearch）”中按 `event:"traffic.route.decision"` 查询。追加 `AND traffic_route_mode:"canary"` 查看灰度，追加 `AND traffic_route_decision:"unavailable"` 定位无可用目标。用 `traffic_route_hop` 区分 `gateway`、`http_provider`、`dubbo_consumer` 和 `dubbo_provider`，再沿同一 `trace_id` 打开链路。日志摘要使用固定格式 `traffic route <hop> <mode> <decision>`，不包含授权内容、身份、原始请求头、标签、地址或 URL。

在 Jaeger 原生 UI 选服务后按 `traffic.route.mode=canary` 和 `traffic.route.decision=unavailable` 等标签查询。Grafana 的 Jaeger 数据源可用已知 Trace ID 查看该链路。查看前需要目标 JVM 使用隐私扩展 0.1.2 或更新版本，两级 Collector 使用本版白名单，并核对运行日志索引的三个关键词字段。应用、Collector 和索引分别验收。Prometheus 不使用这些字段作为指标标签。

### 安全HTTP/AI日志与全量开发跟踪

github-dev的Java/Python应使用非parent-based的全量采样，验证incoming sampled=0仍保留原Trace/parent；不要只把parentbased ratio调成1。薄扩展仅保留有限灰度选路字段及模型操作、提供方、模型/Prompt有限标识、真实报告的token/finish reason、reasoning存在性和Dubbo协议，原始prompt/completion/events/异常继续删除。`fleet.ai.usage.available/final`区分供应商是否报告及正常终态；缺失token不补0，reasoning输出是output子集而非另加总。

`privacy_contract.py`定义两级Collector共同的有界类型合同。HTTP终态使用`summary`、方法、框架路由、状态、耗时、实际读写字节、完整性及安全JSON投影；SSE/AI正文/未知路由自由正文不进入日志。filelog把JSON number解为Double，因此仅在数值精确为非负安全整数时转Long，字符串数字或非法类型删除。ES的`runtime-log-additive-mapping.json`只增加新字段，既有索引先读回类型，冲突须报告；不得重建索引或修改请求审计历史。正文投影存`index:false`仅用于展开查看，不能当全文搜索库。

Grafana的日志message字段为代码生成的`summary`，trace_id仍链接Jaeger。启动日志没有HTTP终态摘要时可能仅有级别元数据，不能为显示方便放开原始`message`。每个HTTP终态恰一条，模型事件继承真实模型child span；无Trace的后台日志不伪造关联。

看板顶部“运行日志（Explore / Elasticsearch）”在当前 Grafana 来源打开固定数据源 `fleet-elasticsearch`，继承点击时看板时间窗，默认仅查 `Resource.deployment.environment.name:"github-dev"`，上限 500 条。按服务追加 `AND Resource.service.name:"rag"`，按链路追加 `AND trace_id:"<32位TraceID>"`，按级别追加 `AND SeverityText:(WARN OR ERROR OR FATAL)`；也可用 `Resource.service.version:"<确定版本>"` 区分新旧进程。HTTP 状态用 `http_status:[400 TO 499]` 或 `[500 TO 599]`，业务终态用 `outcome:"failure"`、`outcome:"cancelled"` 或 `outcome:"timeout"`，不能只凭 HTTP 200 推断业务成功。达到上限时缩小时间窗或条件，不能把前 500 条当成完整记录。展开日志后使用 `trace_id` 的“查看链路”链接；从 trace 回日志则在 Explore 分屏选择 Elasticsearch，使用同一 ID 与时间窗，当前不宣称已配置一键反向关联。

入口采用官方 [Explore URL schema 1](https://grafana.com/docs/grafana/latest/visualizations/explore/get-started-with-explore/#generate-explore-urls-from-external-tools) 与实际 [ES 插件 12.9.5 Logs 查询合同](https://github.com/grafana/grafana-elasticsearch-datasource/blob/v12.9.5/src/dataquery.gen.ts)。它增加一个导航链接，保留 9 个面板、现有 Jaeger 搜索入口和数据源配置；部署后仍需在浏览器点开并确认数据源、时间窗、默认查询及日志→链路跳转，源文件校验不代替 GUI 验收。

可用现有固定0.164.0二进制在应用之外回放四段合成fixture（文件采集、同batch服务隔离、中心日志、业务/GenAI spans）：

```bash
python3 verify_collector_native.py --collector-bin /path/to/verified/otelcol-contrib-0.164.0
```

默认仍使用固定官方Docker镜像供CI；本机模式只启动合成文件receiver/exporter，不读取应用日志，不安装组件。两种方式都不能代替NAS/ES/Jaeger实际请求对账。


filelog使用Collector内置`file_storage/offsets`持久化读取位置，`FLEET_LOG_STATE_DIR`必须指向本机受控0700目录。托管入口强制使用所选state下`collector-offsets`，拒绝被Git跟踪或未忽略、非本人、软链接、宽权限目录/文件，并保持已有offset。初次没有checkpoint仍按`start_at:end`跳过历史；建立checkpoint后的正常停止/恢复会读取停机追加的日志。固定0.164原生fixture已验证seed→stop→离线追加两条→resume只读两条，重复0。它证明源读取恢复，不能把内存OTLP队列、崩溃/断电、轮转或存储持久化描述为exactly-once或永久零丢失。
