# AGENTS.md

本文件是 `utils` 公共基础包的 AI 编码入口。AI 修改本项目代码前，必须先阅读本文件，再按影响范围阅读 `docs/ai-coding` 下的规范和目标 Java 类。

## Nacos 配置注释要求

- 新增或修改 Nacos 配置、模板、引导配置及渲染/发布工具前，必须阅读 [Nacos 注释与归类规范](docs/ai-coding/NACOS_COMMENT_SPEC.md)。
- 每个 dataId 写明职责、环境归属、消费者；每个可调参数写明用途、必要单位、格式、空值及开关影响。YAML 使用 `#` 注释，渲染结果必须保留说明。
- 注释整理保持键、类型、值、占位符和列表/import 顺序不变；发布前读回权威配置并备份，使用旧版本比对/CAS，发布后逐项读回。真实凭据不得进入 Git 或注释。
- 只处理明确授权的仓库及环境；Yudao 项目排除，不随本规范同步修改。

## Git 分支、完整交付与清理

- 长期保护 `main`、`test`、`preprod`：分别为正式主干、集成测试、发布候选；禁止 force push 和删除。新任务 fetch 后从最新 `origin/main` 的确定 SHA 建独立短分支，续作可复用已核实归属的本功能分支，保留其他会话与无关 dirty。
- 完整功能包含必要代码、测试、文档、migration、公共契约和依赖。进入 test/preprod 后保留源分支；候选按最新 main 加完整功能及必需跨仓配套验证，不默认把混合 test 整条合 main，也不逐提交拼装常规交付。
- 完整功能合入 main、远端 refs 和包含关系核实后，清理本次已结束的本地及远端短分支。只进 test/preprod、未合入、用途不明或被其他 worktree 占用的分支不能删；三条长期分支永久排除在短分支清理外。详情见 `docs/ai-coding/BRANCHING_SPEC.md`。
- Git 分支不自动切换 profile 或触发部署。现有 APP_ENV=dev、github-dev 与服务根 config/application-dev.properties 模式保持；base application.yml 不改写，未来环境映射和部署另行配置与验收。
- 按风险执行编译、测试、安全专项检查及必要独立验证；Review 是补充，不新增人工代码审批。只按已有用户授权执行提交、推送、合并、tag、制品和部署；纯规范不升业务版本或重复发布 Maven 制品。

## 项目定位

- 项目名称：`utils`
- 项目类型：Spring Boot 4 / Java 17 公共基础能力包
- 发布坐标：`com:utils`
- 主要消费者：同级 `../user`、`../message`、`../ai`，以及需要统一响应、认证上下文、多租户、MyBatis-Plus、Redis、HTTP、文件、Excel、PDF、第三方 SDK 的服务
- 核心风险：公共 API 破坏消费者、旧认证/旧响应回流、公共工具污染业务语义、认证和租户上下文串号、SQL 安全误用

## 修改前阅读顺序

任何代码修改前必须先阅读：

1. `README.md`：确认公共包定位、版本、构建发布和消费者验证要求。
2. `docs/ai-coding/README.md`：确认 AI 编码入口、修改前检查和文档维护要求。
3. `docs/ai-coding/AI_CODING_GUIDE.md`：确认当前项目的 AI 执行步骤。
4. `docs/ai-coding/AI_DIRECTORY_STRUCTURE_GUIDE.md`：确认 Java Library、公共包、测试、文档和消费者边界。
5. `docs/ai-coding/AI_COMMENT_STYLE_GUIDE.md`：确认注释规范、自解释优先、禁止注释掉死代码和排版要求。
6. `docs/ai-coding/AI_DESIGN_PATTERN_GUIDE.md`：确认公共包设计模式、公共 API 兼容性和禁止巨型工具类规则。
7. `docs/ai-coding/BRANCHING_SPEC.md`：确认分支命名、短分支生命周期、release/hotfix、tag 和清理规则。
8. `docs/ai-coding/ENVIRONMENT_CONFIG_SPEC.md`：确认环境、Nacos namespace、Java profile 和前端/小程序边界。
9. `docs/ai-coding/VERSIONING_SPEC.md`：确认 `group = 'com'`、补丁递增和消费者同步规则。
10. `docs/ai-coding/RPC_API_CODING_SPEC.md`：确认 `utils` 与同级 `../rpc-api` 的 RPC 职责边界。
11. `docs/ai-coding/TESTING_SPEC.md`：确认公共包单元测试、组件测试、Spring 上下文测试和消费者编译验证边界。
12. `docs/ai-coding/PROJECT_CODING_SPEC.md`：确认公共响应、异常、认证、租户、动态数据源、乐观锁、注释和 examples 规则。
13. `docs/ai-coding/AI_ENGINEERING_GUARDRAILS.md`：确认风险分级、Definition of Done、测试门禁、安全门禁和交付说明。
14. `docs/ai-coding/SECURITY_CODING_SPEC.md`：涉及认证、权限、SQL、文件、脱敏、日志、第三方 SDK 或安全扩展点时必须阅读。
15. `docs/ai-coding/UTILS_TOOL_CATALOG.md`：新增或迁移工具类前确认目标包和现有能力。
16. `docs/ai-coding/NACOS_CONFIG_SPEC.md`：涉及公共 `@ConfigurationProperties` 归属、共享 dataId 或 Nacos 配置中心拆分时必读。

## 项目边界

- 多项目通用能力才放入 `utils`；单个业务服务专用逻辑必须留在业务项目。
- 公共工具、公共配置、公共注解、公共异常、公共上下文和第三方通用封装在本仓库维护。
- 业务 RPC 接口、DTO、枚举和值对象不放在 `utils`，统一维护在同级 `../rpc-api`。
- 个人公共包统一使用 `com:utils` / `com:rpc-api`，美联对应包使用 `com.kellen:utils` / `com.kellen:rpc-api`；共用远程 Maven 地址时按 group 隔离并独立发布版本。同步公司代码时不得同步覆盖个人构建坐标或消费者依赖坐标。
- 新增公共配置、SDK 适配、拦截器、策略、工具类或自动配置时，必须优先沿用 `docs/ai-coding/AI_DESIGN_PATTERN_GUIDE.md` 中的 Auto Configuration、Strategy、Adapter、Interceptor、Template、Factory 等公共包适用模式。
- 不在 `user`、`message`、`gateway` 等消费者项目复制 `utils` 源码。
- 修改公共 API、注解、AOP、认证、租户、异常、返回值或 MyBatis-Plus 配置时，必须评估消费者项目编译影响。
- 公共数据库基础脚本源头是 `src/main/resources/db/common-infra-schema.sql`；接入 MyBatis-Plus DDL 与 Seata AT 的业务服务首次启动空库前，必须让目标业务库手动执行该脚本，不能依赖消费者应用首次启动自动创建 `undo_log`。
- 业务微服务表设计规范由 `docs/ai-coding/PROJECT_CODING_SPEC.md` 和 `docs/ai-coding/examples` 提供模板；新增表必须包含公共治理字段，`version` 只做乐观锁，业务版本使用 `*_version` 命名。
- `utils` 不维护业务路由或 Swagger UI 聚合列表；新增公共 OpenAPI/Springdoc 能力或新 Java 服务消费者时，必须提醒消费者在 `../gateway` 的 Nacos `gateway-spring.yaml` 按需补业务路由和 `springdoc.swagger-ui.urls`，并先验证对应网关文档路径。

## AI 工程门禁

- AI 新增或修改功能前，必须按 `AI_AUTOMATION_WORKFLOW.md` 整理需求说明、验收标准和开发手册。
- AI 完成改动后，必须按 `docs/ai-coding/AI_ENGINEERING_GUARDRAILS.md` 做风险分级、Definition of Done、测试证据、安全检查、风险和回滚说明。
- 测试分层按 `docs/ai-coding/TESTING_SPEC.md` 执行；工具类、小逻辑和上下文过滤器可以用 AssertJ 单元测试，自动配置才使用最小 Spring 上下文。
- 公共 API、认证上下文、租户上下文、数据权限、SQL 插件、异常处理和统一响应相关改动默认高风险起步。
- AI 不得为了兼容单个消费者而破坏公共包职责或恢复旧 `Json` 响应、旧 token 认证、旧数据源常量、`javax.*` 包名。
- 只要本次任务修改了生产代码、配置、构建脚本或公共示例，就必须按 `docs/ai-coding/VERSIONING_SPEC.md` 提升一次 `build.gradle` 中的 `version`；如果同一批改动尚未提交，版本号只提升一次，后续继续补代码、补测试、补文档不得重复提升。
- 文档、注释、README、AI 规范等纯文档改动不强制提升制品版本；如果文档改动伴随代码或构建改动，则按 `docs/ai-coding/VERSIONING_SPEC.md` 提升一次版本。
- 公共包版本变化并从 `main` 发布该确定提交时，必须同时执行远程私有 Maven 仓库发布，保证 Git 代码和远程 `com:utils:<version>` 制品一致。
- 非 `main` 分支或未提交状态默认只允许 `publishToMavenLocal` 做消费者本地验证，不得把试验性 jar 推送到远程私有仓库。

## 多智能体协作规则

- 子智能体可以并行做调用点搜索、消费者影响分析、现有工具类盘点、测试缺口分析和 Review。
- 修改公共 API、认证、租户、MyBatis-Plus 配置、AOP、异常处理时，不允许多个 worker 并行写同一类或同一配置。
- 最终能力归属、公共 API 兼容性、消费者验证结论和发布说明必须由主智能体收口。

## 验证命令

基础验证：

```bash
./gradlew clean build -x test
./gradlew publishToMavenLocal
bash scripts/check-secrets.sh
```

如果修改公共 API、认证、异常、响应、租户、动态数据源或 MyBatis-Plus 配置，还需要编译消费者项目，例如：

```bash
cd ../user
./gradlew clean compileJava -x test
```

公共包版本变化并从 main 发布该确定提交时，还必须发布远程私有 Maven 制品：

```bash
./gradlew publish
```

## 禁止事项

- 禁止提交 Maven 仓库凭证、真实密钥、本机 Gradle 配置、本机绝对路径或发布账号。
- 禁止 AI 触碰真实密钥、第三方 SDK 凭证或 Maven 仓库凭证（疑似密钥只能告警，由项目负责人处理）；配置中心结构性调整（dataId 拆分/合并、import 顺序、`${}` 引用、Nacos 接入地址/namespace/group）允许 AI 自主完成，但必须保值不改值，不得擅自变更生产业务配置的实际取值。
- 禁止把业务服务专属枚举、业务公式、业务常量放进公共包。
- 禁止把 `UserRpcService`、`MessageRpcService`、业务 RPC DTO 或 provider/consumer 实现放进公共包；这些契约属于同级 `../rpc-api`。
- 禁止把 MyBatis-Plus `SqlInjector` 描述或实现成 SQL 注入防护能力。
- 禁止默认关闭 `IllegalSQLInnerInterceptor`、`BlockAttackInnerInterceptor` 等安全拦截而不说明替代防护。
- 禁止在公共工具里提供绕过鉴权、租户隔离、数据归属校验、参数绑定或文件路径校验的便捷方法。
