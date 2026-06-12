# utils AI 编码指导规范

> 本文件是 `utils` 公共基础包的 AI 编码主入口，用于把现有项目规范、工程门禁、安全规范和工具目录串成稳定执行顺序。具体编码细则以 `PROJECT_CODING_SPEC.md`、`SECURITY_CODING_SPEC.md` 和目标 Java 类为准。

## 1. 项目定位

`utils` 是面向 Spring Boot 3 / Java 17 的公共基础能力包，为 `user`、`message` 等业务服务提供统一响应、错误码、认证上下文、租户上下文、MyBatis-Plus 基础配置、AOP、请求日志、Redis、HTTP、JSON、文件、Excel、PDF 和第三方 SDK 封装。

AI 修改本项目时，必须先判断能力归属：

- 多项目通用、无业务私有语义：可以留在 `utils`。
- 单个业务项目专用：应留在业务项目，不放进公共包。
- 网关专用：应留在 `gateway`。
- 已有公共能力：优先复用，不新增平行工具类。

## 2. 标准 AI 开发流程

1. 阅读入口
   - 先读根目录 `AGENTS.md` 和 `README.md`。
   - 再读 `docs/ai-coding/README.md`。
   - 按任务阅读 `PROJECT_CODING_SPEC.md`、`AI_ENGINEERING_GUARDRAILS.md`、`SECURITY_CODING_SPEC.md`、`UTILS_TOOL_CATALOG.md`。

2. 调用点确认
   - 使用 `rg` 搜索本仓库已有实现。
   - 搜索消费者项目引用，重点检查 `../user`、`../message`。
   - 修改 public 类型、注解、AOP、异常、认证、租户、响应和 MyBatis-Plus 配置前，必须确认消费者影响。

3. 工程门禁
   - 按 `AI_ENGINEERING_GUARDRAILS.md` 做风险分级。
   - 公共 API、认证、租户、数据权限、SQL 插件、异常和统一响应默认高风险起步。

4. 工程实现
   - 按 `PROJECT_CODING_SPEC.md` 的包结构、命名、注释和安全规则实现。
   - AI 新增或修改 Java 代码时，每一行新增或修改内容都必须补充注释，说明用途、业务含义或安全边界。
   - 不恢复旧 `Json` 响应、旧 token 认证、旧数据源常量、`javax.*` 包名。

5. 验证和交付
   - 执行本项目构建。
   - 修改公共 API 或基础配置时发布到本地 Maven 并编译消费者项目。
   - 输出验证结果、消费者影响、剩余风险和回滚方式。

## 3. 必读规范分工

| 文件 | 用途 |
| --- | --- |
| `README.md` | 当前公共包定位、版本、构建发布和消费者验证 |
| `docs/ai-coding/README.md` | AI 阅读顺序、修改前检查和文档维护 |
| `PROJECT_CODING_SPEC.md` | 响应、异常、认证、租户、动态数据源、乐观锁、MyBatis-Plus、AOP、工具类和注释规范 |
| `AI_AUTOMATION_WORKFLOW.md` | 需求说明、验收标准、开发手册、测试说明和交付说明模板 |
| `AI_ENGINEERING_GUARDRAILS.md` | 风险分级、Definition of Done、测试门禁、安全门禁和交付模板 |
| `SECURITY_CODING_SPEC.md` | 认证、权限、脱敏、SQL、文件、日志、依赖、供应链和配置安全 |
| `UTILS_TOOL_CATALOG.md` | 工具类归类和复用清单 |
| `PACKAGE_REFACTOR_GUIDE.md` | 新增或迁移工具类时的目标包判断 |
| `examples/README.md` | 业务微服务标准分层示例模板入口 |

## 4. 公共包实现规则

- 新增 public 类型必须使用 PascalCase，文件名与 public 类型名一致。
- 工具类命名使用清晰语义，例如 `JwtUtils`、`OkHttpUtils`、`ExcelUtils`。
- 配置类使用 `XxxConfig`，AOP 切面使用业务语义命名。
- 公共异常、日志、响应、导出、通知和审计能力必须默认保护敏感信息。
- 公共工具不得提供绕过鉴权、租户隔离、数据归属校验、参数绑定、文件路径校验或上传校验的便捷方法。
- MyBatis-Plus `SqlInjector` 只能作为自定义通用 Mapper 方法扩展点，不是 SQL 注入防护能力。

## 5. 验证命令

基础验证：

```bash
./gradlew clean build -x test
./gradlew publishToMavenLocal
```

消费者验证示例：

```bash
cd ../user
./gradlew clean compileJava -x test
```

如果修改会影响 `message` 或 `gateway`，也必须执行对应项目编译或说明未验证原因。

## 6. 交付底线

- 不提交 Maven 仓库凭证、真实密钥、本机 Gradle 配置、本机绝对路径或发布账号。
- 不把业务服务专属枚举、业务公式、业务常量放进公共包。
- 不在消费者项目复制 `utils` 源码。
- 不绕过公共响应、认证上下文、租户上下文、异常处理和 MyBatis-Plus 安全插件。
- 每次公共能力改动必须说明消费者影响、验证命令、未验证项和回滚方式。
