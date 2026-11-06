# ES 首写前的 mapping 准备

本目录仅提供待审查请求体，不连接 ES，不执行变更。请求审计沿用 `github_dev_request_log`；运行日志使用独立的 `github_dev_runtime_log`。不覆盖、重建或删除任一历史索引，也不将实体 `@Document` 改为默认 `alwaysWriteMapping=true`。

## 现有请求审计索引

先用已核授权与受限凭据只读请求 `GET /github_dev_request_log/_mapping`，记录实际解析出的每个具体索引及 `trace_id`、`span_id` 的 mapping；索引名是 alias 时逐一核背后索引，不从 Java 注解推断现场类型。新实体字段不会因已有索引上的应用重启自动变成 Keyword。

- 两字段均不存在：在首次写入新字段之前，审核后向明确的目标具体索引发 `PUT /<verified-concrete-index>/_mapping`，请求体使用 `request-log-trace-mapping.json`。该 JSON **仅包含** `trace_id/span_id` 两个 Keyword 字段。
- 已有字段都是 Keyword：无需覆盖；再次核读当前 mapping 后继续部署前验收。
- 任一字段是 Text、Object 或其它类型，或多个 alias 背后索引不一致：停止该字段发布，不能在原索引上强改类型。由负责人选择新命名索引/受控迁移并核对 alias、保留与回滚；本目录不执行 reindex、alias 切换或删索引。
- 目标不存在：先确认实际配置中的索引名与是否允许创建，审核新索引的完整既有业务 mapping，不能只拿这两个新增字段替代原有审计 schema。

获得执行授权并完成 PUT 后，重新 `GET /<verified-concrete-index>/_mapping` 核字段类型，再写入安全合成审计记录并按 trace 精确查询；只有读回与查询通过，才能声称 mapping/写入已验。部署回滚不删除新增字段，先回退应用写入方。

## 新运行日志索引

`runtime-log-index-template.json` 是 ES 7.17+ composable index template 的候选请求体，可审核后用于 `PUT /_index_template/github-dev-runtime-log`。它只匹配独立的精确名称 `github_dev_runtime_log`，不匹配 `github_dev_request_log`、默认 `request_log` 或历史通配索引；只影响之后创建的索引。部署前先只读核现有 index template、匹配优先级、同名索引是否已经存在和现场版本，再用 `_index_template/_simulate_index/github_dev_runtime_log` 审核最终组合；发现优先级冲突时停止，不能强覆盖其它模板。

固定 Collector 0.164 的 `raw` encoder 把 resource 属性放在 `Resource` 前缀下，因此服务筛选字段为 `Resource.service.name`、`Resource.service.version`、`Resource.deployment.environment.name`；日志自己的白名单属性 `trace_id/span_id/event/stage/outcome` 位于根。候选模板的 `dynamic=false` 只限制索引字段，不代替 Collector 清空正文/秘密，也不会从 `_source` 中移除未映射值。时间字段为 `@timestamp`，原生记录的关联/级别字段为 `TraceId/SpanId/SeverityText`。

模板不设置 alias、生命周期或业务数据保留策略。若运行索引已经存在，模板不会回填其 mapping；先 GET 实际 mapping，另审 additive 变更或新索引方案。首次安全合成事件后，读回实际 `_source` 与 mapping，核三项服务 metadata 和无 trace 的启动事件，并在 Grafana 按服务筛选、按真实 trace 跳转。候选文件存在、JSON 可解析与目标已写入分别记录。
