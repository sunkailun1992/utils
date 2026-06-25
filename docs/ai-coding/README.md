# utils AI 编码入口

本目录用于给 AI 或新接手开发者快速理解 `utils` 的当前架构边界、编码规范和工具类归类。修改代码前先读本文件，再按需阅读规范与目录。

## 阅读顺序

1. 先读 [AI 编码指导规范](AI_CODING_GUIDE.md)，确认执行步骤和禁止事项。
2. 再读 [目录管理规范](AI_DIRECTORY_STRUCTURE_GUIDE.md)，确认 Java Library、公共包、测试、文档和消费者边界。
3. 再读 [注释规范](AI_COMMENT_STYLE_GUIDE.md)，确认自解释优先、禁止注释掉死代码和排版要求。
4. 再读 [设计模式规范](AI_DESIGN_PATTERN_GUIDE.md)，确认公共包模式、公共 API 兼容性和禁止巨型工具类规则。
5. 再读 [项目编码规范](PROJECT_CODING_SPEC.md)，确认响应、异常、认证、租户、动态数据源、乐观锁和注释规则。
6. 再读 [AI 自动化开发流程](AI_AUTOMATION_WORKFLOW.md)，按需求说明、验收标准、开发手册、测试说明和交付说明组织自动化开发。
7. 再读 [AI 工程门禁规范](AI_ENGINEERING_GUARDRAILS.md)，确认风险分级、Definition of Done、测试门禁、安全门禁和交付说明。
8. 再读 [分支管理规范](BRANCHING_SPEC.md)，确认分支命名、短分支生命周期、release/hotfix、tag 和清理规则。
9. 再读 [环境配置入口规范](ENVIRONMENT_CONFIG_SPEC.md)，确认环境、Nacos namespace、Java profile 和前端/小程序边界。
10. 再读 [项目版本变更规范](VERSIONING_SPEC.md)，确认 `group = 'com'`、`version = '1.0.0'`、补丁递增和公共包消费者同步规则。
11. 再读 [RPC API 协作规范](RPC_API_CODING_SPEC.md)，确认 `utils` 只维护 Dubbo 上下文透传等横切能力，不维护业务 RPC 契约。
12. 涉及认证、权限、脱敏、水平越权、文件遍历、退出清理 token、XSS、SQL 注入、文件上传校验、CSRF、SSRF、限流资源消耗、加密密钥、批量赋值、字段级授权、供应链、配置安全、异常失败关闭、安全日志告警时，读 [安全编码规范](SECURITY_CODING_SPEC.md)。
13. 涉及 MyBatis-Plus 动态字段、排序、数据权限、租户、乐观锁、SQL 插件或 Mapper 扩展时，必须确认 `SqlInjector` 只用于扩展 SQL 方法，不能作为 SQL 注入防护方案。
14. MyBatis-Plus SQL 安全优先使用后端字段白名单、参数绑定、LambdaWrapper、`SqlInjectionUtils.check(...)` 或 `checkSqlInjection()` 补充校验，以及 `IllegalSQLInnerInterceptor`、`BlockAttackInnerInterceptor` 默认拦截。
15. 涉及业务微服务标准分层、RESTful Controller、ServiceQuery、ServiceResults、BO、Query、VO、Mapper 示例时，读 [公共示例模板](examples/README.md) 和 `examples/Example*`。
16. 再读 [工具类归类目录](UTILS_TOOL_CATALOG.md)，确认目标能力应该放在哪个包、复用哪个类、哪些历史工具需要谨慎使用。
17. 如果涉及工具类新增或包名调整，阅读 [包结构分类说明](PACKAGE_REFACTOR_GUIDE.md)，确认目标子包。
18. 最后阅读目标 Java 类及其上下游调用点，避免只根据类名猜测行为。

## 修改前检查

- 使用 `rg` 搜索现有实现，优先复用已有工具类。
- 检查是否会影响消费者项目，尤其是同级 `../user` 项目。
- 确认没有重新引入旧 `Json` 响应、旧 token 认证、旧多数据源名称或 `javax.*`。
- AI 新增或修改 Java 代码时，必须遵守 `AI_COMMENT_STYLE_GUIDE.md`，补充说明公共职责、消费者影响和安全边界的有效注释。
- 优先让代码自解释，禁止机械逐行、行尾堆叠和注释掉的死代码。
- AI 新增或重构公共配置、SDK 适配、拦截器、策略、工具类或自动配置前，必须遵守 `AI_DESIGN_PATTERN_GUIDE.md`。
- 涉及接口鉴权、数据脱敏、水平越权、文件遍历、退出清理 token、XSS 跨站脚本、SQL 注入、文件上传校验、CSRF、SSRF、限流资源消耗、加密密钥、批量赋值、字段级授权、供应链、配置安全、异常失败关闭、安全日志告警时，必须同步检查 `SECURITY_CODING_SPEC.md`。
- 涉及 MyBatis-Plus Wrapper、Mapper XML、排序字段、动态列名、动态表名、导出字段、查询增强时，必须先设计后端白名单，再考虑 `SqlInjectionUtils` 或 `checkSqlInjection()` 补充校验。
- 涉及业务 Dubbo RPC 接口、DTO、枚举和值对象时，必须去同级 `../rpc-api` 修改；`utils` 只维护 Dubbo 上下文透传等横切能力，不新增 `com.kellen.rpc.*` 业务契约。
- 不得把 MyBatis-Plus `SqlInjector` 写成防 SQL 注入能力；它是自定义通用 Mapper 方法的扩展点，新增前必须证明标准 `BaseMapper`、Service 或 XML 无法满足需求。
- 修改 `docs/ai-coding/examples` 公共示例模板时，必须以 `utils` 为唯一源头，并同步到 `user`、`message` 等业务微服务的本地副本。
- 新增或修改功能前必须按 `AI_AUTOMATION_WORKFLOW.md` 先整理需求说明、验收标准和开发手册；小改动可以简化输出，但检查项不能跳过。
- 新增或修改功能后必须按 `AI_ENGINEERING_GUARDRAILS.md` 做风险分级、Definition of Done、测试证据、安全检查、风险和回滚说明。
- 新增或修改 README、AI 规范、配置、脚本、测试、示例和代码时，禁止写入个人电脑绝对路径、本机下载目录、本机 JDK 路径或本机仓库完整路径；需要表达目录关系时使用相对路径、环境变量或 `<PLACEHOLDER>` 占位符。
- 只要本次任务修改生产代码、配置、构建脚本或公共示例，就必须按 `VERSIONING_SPEC.md` 提升一次 `build.gradle` 的 `version`；同一批未提交改动只提升一次版本号，后续补代码、补测试、补文档不得重复提升。
- 纯 README、AI 规范、注释或说明文档改动不强制提升制品版本；如果同时改了代码或构建配置，则仍按 `VERSIONING_SPEC.md` 提升一次。
- 分支命名、短分支生命周期、release/hotfix、tag 和分支清理按 `BRANCHING_SPEC.md` 处理。
- 环境、Nacos namespace、Java profile 和前端/小程序边界按 `ENVIRONMENT_CONFIG_SPEC.md` 处理。
- 如果修改公共 API、注解、AOP、认证、租户、异常、返回值、MyBatis-Plus 配置，修改后必须执行 `./gradlew clean build -x test` 和 `./gradlew publishToMavenLocal`。
- 如果从 `main` 分支开始提交并推送 Git 仓库，必须同时执行 `./gradlew publish`，把同版本制品推送到远程私有 Maven 仓库。
- 非 `main` 分支或未提交状态默认只做 `./gradlew publishToMavenLocal` 本地验证，不推送远程私有 Maven 制品。

## 文档维护

- 新增公共工具类时，同步更新 [工具类归类目录](UTILS_TOOL_CATALOG.md)。
- 新增或调整自动化开发流程时，同步更新 [AI 自动化开发流程](AI_AUTOMATION_WORKFLOW.md)。
- 新增或调整工程门禁时，同步更新 [AI 工程门禁规范](AI_ENGINEERING_GUARDRAILS.md)。
- 新增或调整设计模式、公共 API 抽象、自动配置、拦截器或工具类归属规则时，同步更新 [设计模式规范](AI_DESIGN_PATTERN_GUIDE.md)。
- 新增或调整架构规则时，同步更新 [项目编码规范](PROJECT_CODING_SPEC.md)。
- 新增或调整安全规则时，同步更新 [安全编码规范](SECURITY_CODING_SPEC.md)。
- 新增或调整公共 examples 时，先改 `utils/docs/ai-coding/examples`，再同步到业务微服务本地副本。
- 新增或调整 RPC 契约时，先改同级 `../rpc-api` 的源码和 AI 规范，再同步 provider/consumer 依赖。
- 根目录 `README.md` 只放项目定位、构建命令和最高优先级约束。
