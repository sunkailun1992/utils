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
   - 按任务阅读 `AI_DESIGN_PATTERN_GUIDE.md`、`PROJECT_CODING_SPEC.md`、`AI_ENGINEERING_GUARDRAILS.md`、`SECURITY_CODING_SPEC.md`、`UTILS_TOOL_CATALOG.md`。

2. 调用点确认
   - 使用 `rg` 搜索本仓库已有实现。
   - 搜索消费者项目引用，重点检查 `../user`、`../message`。
   - 修改 public 类型、注解、AOP、异常、认证、租户、响应和 MyBatis-Plus 配置前，必须确认消费者影响。

3. 工程门禁
   - 按 `AI_ENGINEERING_GUARDRAILS.md` 做风险分级。
   - 公共 API、认证、租户、数据权限、SQL 插件、异常和统一响应默认高风险起步。

4. 工程实现
   - 按 `PROJECT_CODING_SPEC.md` 的包结构、命名、注释和安全规则实现。
   - 新增公共配置、SDK 适配、拦截器、策略、工具类或自动配置前，必须按 `AI_DESIGN_PATTERN_GUIDE.md` 判断模式和公共 API 兼容性。
   - AI 新增或修改 Java、Gradle、Shell、Markdown 或 properties 内容时，必须遵守 `AI_COMMENT_STYLE_GUIDE.md`。
   - 优先让代码自解释，能用类型名、方法名、泛型、常量和小方法表达的意图，不用注释补救。
   - 注释应说明公共职责、消费者影响、安全边界和误用风险，禁止机械逐行、行尾堆叠和注释掉的死代码。
   - 注释必须保持缩进、对齐、换行和段落美观一致，不能为了补说明把公共 API 或构建脚本弄乱。
   - 不恢复旧 `Json` 响应、旧 token 认证、旧数据源常量、`javax.*` 包名。
   - 只要修改生产代码、配置、构建脚本或公共示例，必须提升一次 `build.gradle` 的 `version`。
   - 同一批未提交改动只允许提升一次版本号，后续继续修复、补测试或补文档不得重复提升。
   - 纯文档、注释、README 或 AI 规范改动不强制提升制品版本；如果同一任务同时改了代码或构建配置，则仍按代码改动提升一次版本。

5. 验证和交付
   - 执行本项目构建。
   - 修改公共 API 或基础配置时发布到本地 Maven 并编译消费者项目。
   - 从 `main` 分支开始提交并推送 Git 仓库时，必须同步执行 `./gradlew publish`，把同版本制品推送到远程私有 Maven 仓库。
   - 非 `main` 分支或未提交状态默认只执行 `./gradlew publishToMavenLocal` 做消费者本地验证，不推送远程私有 Maven 制品。
   - 输出验证结果、消费者影响、剩余风险和回滚方式。

## 3. 必读规范分工

| 文件 | 用途 |
| --- | --- |
| `README.md` | 当前公共包定位、版本、构建发布和消费者验证 |
| `docs/ai-coding/README.md` | AI 阅读顺序、修改前检查和文档维护 |
| `AI_DESIGN_PATTERN_GUIDE.md` | 公共包设计模式、公共 API 兼容性、自动配置、拦截器和工具类抽象规则 |
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
bash scripts/check-secrets.sh
```

main 分支提交并推送远程 Git 仓库时的发布验证：

```bash
./gradlew publish
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
- 每次代码或构建改动必须说明本次版本号是否已提升；未提交期间同一批改动只提升一次。
- 从 `main` 分支提交并推送时，交付说明必须包含远程私有 Maven 制品发布结果。
