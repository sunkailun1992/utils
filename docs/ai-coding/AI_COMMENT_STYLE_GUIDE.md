# AI 注释规范

本规范约束 AI 在 `utils` 公共包中新增或修改注释的方式。项目是 Java 17 公共基础能力包，注释必须服务于公共 API、消费者兼容、认证租户上下文、工具边界和安全维护。

## 0. AI 执行流程

- 修改注释前先识别文件类型和上下文，例如 Java 公共 API、Gradle 发布脚本、Shell、Markdown 或 properties。
- 优先阅读 `AGENTS.md`、`docs/ai-coding/AI_CODING_GUIDE.md`、本文件、`PROJECT_CODING_SPEC.md`、`UTILS_TOOL_CATALOG.md`、`SECURITY_CODING_SPEC.md`。
- 本规范未覆盖的文件类型，先查官方或主流规范，补充规范来源和公共包落地规则后再改代码。
- 不为了统一风格批量重排发布配置、仓库凭据、生产配置或消费者依赖版本。

## 1. 总原则

- 自解释优先：能用清晰类型名、方法名、泛型、常量和小方法表达的意图，先重构代码，不用注释补救。
- 注释只解释代码看不出的内容：公共职责、消费者影响、安全边界、认证租户上下文、异常脱敏、SQL/文件/HTTP 工具误用风险。
- 不给 package、import、普通注解、简单赋值、普通 getter/setter 或显而易见的链式调用逐行加注释。
- 禁止逐行翻译式注释，例如“设置字段”“返回结果”“调用工具类”。
- 禁止用注释保留废弃实现、调试 main、临时示例或整块旧代码；历史版本交给 Git。
- 注释必须随代码同步更新，过时注释必须删除或修正。
- 作者与时间按主流规范（Google Java Style / Oracle Javadoc）交给版本控制：`@author` 可选保留、格式统一即可，不强制；创建时间、修改时间、邮箱不写进注释（Git 记录），禁止 `@DateTime`、`@email`、`@ClassName`、`@explain` 等非标准标签。

## 2. Java 公共 API 注释

- 最低覆盖（对齐 Google Java Style / Oracle Javadoc 规范）：每个 public 类型、每个 public 或 protected 方法/字段都必须有 Javadoc；仅当成员自解释时可省略——简单 getter/setter、覆写方法（`@Override`）、含义显而易见的常量。
- public 类型、注解、AOP、配置类、异常、上下文、工具类和第三方 SDK 封装应使用 Javadoc 说明职责、线程/租户/安全边界和消费者影响。
- public 方法注释应说明参数约束、返回语义、副作用、异常和不适用场景；不能只复述方法名。
- 涉及认证、租户、动态数据源、MyBatis-Plus、文件、HTTP、JSON、Excel、PDF、第三方 SDK 的注释必须说明误用风险。
- 实现注释只写在关键逻辑块上方，解释兼容、降级、脱敏、资源释放和安全判断原因。

## 3. Gradle、Shell、Properties 和 Markdown 注释

- Gradle 注释解释插件、依赖、版本、发布任务和消费者兼容原因，不解释 DSL 语法。
- Shell 注释解释安全边界、错误处理、密钥脱敏、退出码和幂等策略，不解释普通命令。
- Properties 生成类配置一般不加注释；确需修改时说明版本、安全和构建兼容影响。
- Markdown 文档把关键规则写成可见正文，不用隐藏注释承载规范。
- 发现 Maven 仓库凭据、SDK 凭证、生产配置或个人路径时，只报告文件行号和风险，不自动替换、删除或移动。

## 4. 格式和美观度

- 维持当前文件缩进、空行、换行宽度和段落风格，不在同一文件混用多套注释风格。
- JavaDoc 段落短而完整，长句按语义换行，不写超长单行。
- 行尾注释只用于短枚举、短单位或既有对齐风格；造成列宽混乱或超长行时改为块上方注释。
- 不为了“看起来整齐”改动 Maven 凭据、生产地址、SDK 密钥、消费者版本或发布配置所在行。
- 提交前从 diff 视觉检查一次：注释应让公共 API 边界更容易扫读，而不是更乱。

## 5. 检查清单

- 注释是否解释了公共职责、消费者影响或安全边界？
- 是否可以用更好的命名、类型、泛型、常量或小方法替代注释？
- 是否存在注释掉的旧实现、调试代码、临时示例或整块废弃实现？
- 是否泄露 Maven 凭据、SDK 密钥、生产地址、个人路径或内部令牌？
- 缩进、换行、对齐和段落是否与当前文件风格一致？

## 6. 参考依据

- [Google Java Style Guide - Javadoc](https://google.github.io/styleguide/javaguide.html#s7-javadoc)
- [Oracle JDK Documentation Comment Specification](https://docs.oracle.com/en/java/javase/21/docs/specs/javadoc/doc-comment-spec.html)
- [Gradle Build Language Reference](https://docs.gradle.org/current/dsl/)
- [Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html)
- [CommonMark Specification](https://spec.commonmark.org/current/)
- Robert C. Martin《Clean Code》第 4 章 Comments：注释是次优手段，优先让代码自解释；注释掉的代码应删除。
