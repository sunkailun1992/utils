# utils AI 编码入口

本目录用于给 AI 或新接手开发者快速理解 `utils` 的当前架构边界、编码规范和工具类归类。修改代码前先读本文件，再按需阅读规范与目录。

## 阅读顺序

1. 先读 [项目编码规范](PROJECT_CODING_SPEC.md)，确认响应、异常、认证、租户、动态数据源、乐观锁和注释规则。
2. 涉及认证、权限、脱敏、水平越权、文件遍历、退出清理 token、XSS、SQL 注入、文件上传校验、CSRF、SSRF、限流资源消耗、加密密钥、批量赋值、字段级授权、供应链、配置安全、异常失败关闭、安全日志告警时，读 [安全编码规范](SECURITY_CODING_SPEC.md)。
3. 涉及 MyBatis-Plus 动态字段、排序、数据权限、租户、乐观锁、SQL 插件或 Mapper 扩展时，必须确认 `SqlInjector` 只用于扩展 SQL 方法，不能作为 SQL 注入防护方案。
4. MyBatis-Plus SQL 安全优先使用后端字段白名单、参数绑定、LambdaWrapper、`SqlInjectionUtils.check(...)` 或 `checkSqlInjection()` 补充校验，以及 `IllegalSQLInnerInterceptor`、`BlockAttackInnerInterceptor` 默认拦截。
5. 再读 [工具类归类目录](UTILS_TOOL_CATALOG.md)，确认目标能力应该放在哪个包、复用哪个类、哪些历史工具需要谨慎使用。
6. 如果涉及工具类新增或包名调整，阅读 [包结构分类说明](PACKAGE_REFACTOR_GUIDE.md)，确认目标子包。
7. 最后阅读目标 Java 类及其上下游调用点，避免只根据类名猜测行为。

## 修改前检查

- 使用 `rg` 搜索现有实现，优先复用已有工具类。
- 检查是否会影响消费者项目，尤其是 `/Users/sunkailun/Desktop/个人/GitHub/user`。
- 确认没有重新引入旧 `Json` 响应、旧 token 认证、旧多数据源名称或 `javax.*`。
- AI 新增或修改 Java 代码时，每一行新增或修改内容都必须补充注释，说明该行用途、业务含义或安全边界。
- 涉及接口鉴权、数据脱敏、水平越权、文件遍历、退出清理 token、XSS 跨站脚本、SQL 注入、文件上传校验、CSRF、SSRF、限流资源消耗、加密密钥、批量赋值、字段级授权、供应链、配置安全、异常失败关闭、安全日志告警时，必须同步检查 `SECURITY_CODING_SPEC.md`。
- 涉及 MyBatis-Plus Wrapper、Mapper XML、排序字段、动态列名、动态表名、导出字段、查询增强时，必须先设计后端白名单，再考虑 `SqlInjectionUtils` 或 `checkSqlInjection()` 补充校验。
- 不得把 MyBatis-Plus `SqlInjector` 写成防 SQL 注入能力；它是自定义通用 Mapper 方法的扩展点，新增前必须证明标准 `BaseMapper`、Service 或 XML 无法满足需求。
- 如果修改公共 API、注解、AOP、认证、租户、异常、返回值、MyBatis-Plus 配置，修改后必须执行 `./gradlew clean build -x test` 和 `./gradlew publishToMavenLocal`。

## 文档维护

- 新增公共工具类时，同步更新 [工具类归类目录](UTILS_TOOL_CATALOG.md)。
- 新增或调整架构规则时，同步更新 [项目编码规范](PROJECT_CODING_SPEC.md)。
- 新增或调整安全规则时，同步更新 [安全编码规范](SECURITY_CODING_SPEC.md)。
- 根目录 `README.md` 只放项目定位、构建命令和最高优先级约束。
