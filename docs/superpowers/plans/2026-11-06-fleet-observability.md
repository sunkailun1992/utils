# Fleet Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 统一9个个人Java服务与Python引擎的指标、链路、安全日志和采集配置，移除旧Admin/Zipkin与确认已替代的RAG实现。

**Architecture:** Micrometer→Prometheus，OTel Java agent/Python SDK→Collector→Jaeger，安全结构日志保留ES；Grafana统一展示。共享utils只提供横切观测API/registry，不向gateway或ai-agent引入重型依赖。

**Tech Stack:** Java17/Boot4.0.4/Dubbo3.3.6、Micrometer、OTel、Python3.12/FastAPI、Prometheus/Grafana/Jaeger/Collector。

**Spec:** ../specs/2026-11-06-fleet-observability-design.md

## Global Constraints

- 仅个人9服务及公共utils，rpc-api原则不变；当前用户main-only要求覆盖旧仓库test/preprod规则。
- 不采正文、凭据或原始异常；动态ID不作为metrics label；业务会话ID不能覆盖真实trace。
- 管理端点失败关闭；不开放公网网关Actuator。
- 监控不放宽业务预算，不重建RAG知识库，不更改业务Compose资源。
- 每仓最新远端基线短分支，保留无关改动；版本本批只提升一次，公共库仅MavenLocal验证直到明确发布阶段。
- 不安装/部署NAS、不改远端Nacos、不DROP数据库，先准备可审查配置与迁移。

## Review Focus

- 并发请求和线程池复用时真实trace不串线且原上下文恢复。
- 无/错误运维令牌与网关动态路径不可绕过采集保护。
- 自动instrumentation的异常/URL/headers不会泄露正文和秘密。
- HTTP200中的降级/JSON-RPC错误与SSE取消分别统计。
- worker超时/kill后有父进程终态，stdout协议不被遥测污染。

## Task 1: 公共utils及独立监控编排（主代理）

Files: build.gradle；src/main/java/com/kellen/observability/*；log/entity/{RequestLog,ElasticSearchRequestLog}.java；aop/RequestRequiredAspect.java；ops/github-dev/observability/*；相应tests。

Interfaces: 提供com:utils:1.5.3中的Prometheus registry和安全TraceContextSnapshot；统一trace_id/span_id结构字段。OTel仅API，不创建全局SDK。

- [x] 先写有效/无效span及异步快照、日志事件脱敏测试并观察失败。
- [x] 实现安全上下文/日志关联；加入registry，提升utils1.5.3并MavenLocal。
- [x] 准备四组件独立Compose、Collector限内存/白名单、Prometheus目标/告警、Grafana数据源和看板、NAS前置检查与回滚说明。
- [x] 执行utils测试、配置验证、密钥扫描与独立review。

## Task 2: gateway及ai-agent独立接入

Files: 两仓build.gradle、相关management/security/observability配置及tests；profile历史imports与文档。

Interfaces: Micrometer registry，OTel API；固定service/version/env；保留现有运维token机制，无重型utils依赖。

- [x] 测试Prometheus正确/错误token、独立管理端口及公网阻断；测试模型/SSE取消观测。
- [x] 加入registry、安全管理入口、真实模型ObservationRegistry及必要业务span，清理Admin/Zipkin imports和文档。
- [x] 各仓完整test/bootJar/密钥检查，版本按远端顺延，review。

## Task 3: 七个utils消费者、旧配置及旧RAG清理

Files: user/message/ai/ai-memory/rag/report/mcp的build.gradle及profiles/docs；utils/ops/github-dev/nacos的catalog、retired、旧admin/zipkin模板和维护工具；kellen-workspace服务agent启动入口；rag旧实现/独立清理SQL与tests。

Interfaces: 精确依赖com:utils:1.5.3；OTel API及Prometheus由utils提供。后台/流式业务只观测已存在入口。

- [x] 盘点旧Admin/Zipkin全部活动引用，先以配置核对测试确定预期变化。
- [x] 清理文件、imports/catalog和旧共享变量，同步渲染/校验测试，不删除其他配置。
- [x] 升级7消费者精确utils版本、补真实RAG/MCP/AI业务结果指标；区分业务traceId与OTel字段，保持DTO兼容。
- [x] 删除确认替代的旧Java图谱/检索回退；保护当前Java门面职责，补删除迁移及调用链/数据库影响说明；不执行实际DROP。
- [x] 准备服务JVM专用agent启动，不污染Gradle/test JVM。
- [x] 执行配置测试、受影响Java完整测试和编译；等待utilsMavenLocal可用，不编造远程制品已发布。

## Task 4: Python引擎观测

Files: rag/engine/src/rag_engine/observability.py及app/http/retrieval/graph_jobs相关入口；pyproject/uv.lock；tests；README/OpenAPI/Compose可选观测变量。

Interfaces: 接受traceparent/tracestate，OTLP HTTP exporter有界超时；受服务令牌保护GET /metrics；有限标签；真实metadata-only spans和安全JSON事件。

- [x] 先测试ASGI上下文、敏感异常/URL清理、metrics保护/标签、Collector不可达、worker终态和不污染stdout。
- [x] 实现可关闭SDK、FastAPI/httpx边界、检索阶段与父进程任务生命周期，提交关联使用link；不改核心提取守卫。
- [x] 更新锁文件及契约，版本0.2.4（先核占用）；完整pytest/ruff/format与跨语言合同。

## Task 5: 整体验证与交付

- [x] 为每任务生成review diff包，独立审查规范与质量，修复重要问题。
- [x] 核全9服务覆盖、旧配置无活动引用、日志字段与dashboard匹配，运行跨仓配置校验/构建/安全检查。
- [x] 汇总确定源码、版本、MavenLocal、测试证据、NAS部署清单与旧表迁移清单；区分未发布/未部署/未删表。
- [x] 全分支最终独立review，交付具体结果；实际远端发布/安装/删除以当前授权和已核目标为准。

本轮源码和本机交付已完成，最终独立定向复审为 0 Critical / 0 Important。全部提交保留本地功能分支；远程 Git/Maven、CI、原生镜像验证、NAS 安装、Nacos/ES 写入和旧表实际 DROP 尚未执行。完整回执与可下载 Compose 配套包保存在本轮 visualization 交付目录，不能以本计划勾选替代目标环境验收。
