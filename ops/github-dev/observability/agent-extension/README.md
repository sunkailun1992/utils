# Java agent 出站隐私扩展

本独立 Gradle 构建只服务于观测启动边界，不被 `com:utils` 或应用构建引用。正式薄 Jar 0.1.1 只有自身类与 SPI 注册；SDK 1.67.0 为 compileOnly，由固定 OTel agent 2.33.1 提供。应用保持 API/BOM 1.59.0。新依赖均来自 Maven Central；OTel 为 Apache-2.0，JUnit 仅测试使用。这里没有安装系统工具、自动发布制品或执行部署。

## 构建、测试与制品

在 utils 根目录使用已有 wrapper：

```bash
./gradlew -p ops/github-dev/observability/agent-extension clean test runtimeChecksums --no-daemon
```

`build/runtime/` 只含官方 `opentelemetry-javaagent-2.33.1.jar`、`fleet-privacy-extension-0.1.1.jar` 和 `SHA256SUMS`。构建校验 agent 的官方 SHA-256 `e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149`（[Maven Central 发布摘要](https://repo.maven.apache.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.33.1/opentelemetry-javaagent-2.33.1.jar.sha256)）；扩展摘要来自本次确定源码的可重复薄 Jar。复制到 Git 外固定运行目录后重新核对两个摘要，保存源码 commit 与校验回执。不能给旧扩展重新命名来冒充新版本。

实际测试启动子 JVM，应用只依赖 API 1.59.0（确认应用 classpath 没有 SDK），加载真实 agent 与扩展，向 loopback HTTP 端点发送含合成 buildId/KB/secret/js_code 的 GET，并制造异常、未知 attributes/URL/SQL/scope/name/link 内容。loopback OTLP 接收端直接解析原始 protobuf，断言秘密不存在、业务 metadata 保留及自动 HTTP 的 traceparent/父子关系成立。测试专用 verifier SPI 在实际 batch processor 入队前确认 events=0，即使调用方试图设为128。测试 verifier Jar 不进入运行 bundle。

证据输出到 `build/egress-evidence/` 与 `build/test-results/test/`。这是 JVM 到 Collector 之前的真实出口验证；不代表原生 Collector、Jaeger、Prometheus、Grafana 或 NAS 已启动。原生 Docker fixture 继续由独立 CI/部署步骤验收，本轮本机未运行 Docker。

## 私有运行环境

在 Git 外受保护的环境文件中提供以下变量，路径指向已校验的确定制品，endpoint/认证按现场提供：

```dotenv
OTEL_JAVAAGENT_PATH=/ABSOLUTE_RUNTIME_DIR/opentelemetry-javaagent-2.33.1.jar
OTEL_JAVAAGENT_SHA256=e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149
FLEET_PRIVACY_EXTENSION_PATH=/ABSOLUTE_RUNTIME_DIR/fleet-privacy-extension-0.1.1.jar
FLEET_PRIVACY_EXTENSION_SHA256=LOCAL_BUILD_SHA256_FROM_SHA256SUMS
OTEL_EXPORTER_OTLP_ENDPOINT=http://COLLECTOR_PRIVATE_HOST:4318
OTEL_EXPORTER_OTLP_HEADERS=PRIVATE_AUTHORIZATION_HEADER
```

workspace 使用 `python3 scripts/run-observed.py <service> --dry-run` 先校验，再去掉 `--dry-run` 运行。摘要、固定版本或路径不符均失败关闭，不打印私有值；只给选定 `bootRun` JVM 配置扩展，Gradle/test JVM 不加载 agent。普通未启用观测的入口继续可用。

Jar/IDE 入口也先执行同一 `run-observed.py <service> --dry-run` 校验；随后在该服务的私有 JVM 运行配置中同时设置 `-javaagent:$OTEL_JAVAAGENT_PATH` 与 `OTEL_JAVAAGENT_EXTENSIONS=$FLEET_PRIVACY_EXTENSION_PATH`，以及 `OTEL_SPAN_EVENT_COUNT_LIMIT=0`。IDE 环境字段应填已校验的绝对路径，不假定 IDE 自动展开 `$变量`。同时设置 `OTEL_METRICS_EXPORTER=none`、`OTEL_LOGS_EXPORTER=none`、`OTEL_PROPAGATORS=tracecontext`、`OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`、对应 service/version/env 和有界队列/采样配置，详见上层 README。不能只挂标准 agent；Nacos 在进程启动后加载，不能替代 agent 扩展参数。

若回滚隐私扩展必须一并关闭观测或恢复经过验证的匹配 agent/扩展组合，不能退回裸 agent 继续导出。

## 白名单与维护

资源只保留 service/name/version/environment 三字段。span attributes 采用有限枚举和明确数值/布尔类型，只保留 HTTP method/status、RPC/DB system 类别和已核业务结果。未知键或值默认删除，不能以正则把 URL 或原始异常清洗后重新放行。span 名称只允许固定业务名；自动 spans 用 kind 操作名，牺牲动态细节以防名称携带路径/SQL。scope 元数据归一，links 保留关联 IDs 并移除属性/tracestate，status 只保留 code。

扩展使用官方 [AutoConfigurationCustomizer SPI](https://github.com/open-telemetry/opentelemetry-java/blob/v1.67.0/sdk-extensions/autoconfigure-spi/src/main/java/io/opentelemetry/sdk/autoconfigure/spi/AutoConfigurationCustomizer.java) 包装实际 exporter，`DelegatingSpanData` 保留身份/时间。SDK 内部仍可能短暂持有自动 span URL attributes；保证是在导出前清除，只有 events 明确在 SDK 入队前禁收。不得把出站验证描述成“任何原始 attributes 从未进入 JVM 内存”。


0.1.1增加有限GenAI模型/Prompt版本/供应商报告用量、reasoning存在性及Dubbo方法元数据；`ai.model.call`为实际供应商调用child。真实agent测试用incoming未采样的remote parent证明全量开发采样仍保留Trace/parent、自动HTTP下游标为sampled=1，并在Collector前原始OTLP中核对metadata正向值及秘密/events清除。正文采集并不因此打开；内部CoT不在协议可见范围。
