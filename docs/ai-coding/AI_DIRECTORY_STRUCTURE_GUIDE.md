# AI 目录管理规范

本规范约束 AI 在 `utils` 公共基础包中新增、移动、拆分和命名目录的方式。目录管理必须基于当前 Java 17、Gradle `java-library`、Maven Publish、Spring 生态公共库结构。

## 核心依据

- Gradle Java Library SourceSet：生产源码放 `src/main/java`，生产资源放 `src/main/resources`，测试源码放 `src/test/java`，测试资源放 `src/test/resources`。
- Java Library 分层：公共 API、配置、注解、拦截器、工具类、SDK 适配和安全扩展必须按包边界表达职责。
- Java 包命名：包名小写，目录结构必须和 `package` 声明一致。
- Maven Publish：发布坐标、源码 jar、JavaDoc jar 和消费者兼容性决定目录变更必须谨慎。
- GitHub / AI 规范：CI 放 `.github/workflows/`，AI 规范放 `docs/ai-coding/`，根目录只保留 `AGENTS.md` 作为入口。

## 当前标准目录

```text
.
├── AGENTS.md
├── README.md
├── build.gradle
├── settings.gradle
├── gradle/
├── src/
├── docs/ai-coding/
├── scripts/
└── .github/
```

生产代码根包：

```text
src/main/java/com/kellen
```

当前公共包目录职责：

| 目录 | 职责 |
| --- | --- |
| `aliyun` | 阿里云、钉钉、OSS、短信、直播、工作流等 SDK 适配。 |
| `aop` | 公共切面。 |
| `config` | Spring 配置、自动配置、数据源、Redis、MyBatis、Swagger、Web 等公共配置。 |
| `datapermission` | 数据权限公共能力。 |
| `entity` | 公共实体基类或共享对象。 |
| `idempotent` | 幂等能力。 |
| `log` | 公共日志、日志实体、Mapper 和服务。 |
| `security` | 安全配置和认证相关公共能力。 |
| `utils` | 无业务状态的通用工具，按 `auth`、`context`、`file`、`http`、`json`、`response` 等子包归类。 |

## 目录规则

- 新增公共能力前先查 `UTILS_TOOL_CATALOG.md` 和 `PACKAGE_REFACTOR_GUIDE.md`，确认是否已有合适包和类。
- 多项目通用能力才进入 `utils`；单个业务服务专用逻辑必须留在业务项目。
- 新增工具类必须放入职责明确的子包，不新增 `common`、`misc`、`helper`、`temp` 这类低信息目录。
- 新增 Spring 配置或自动配置放 `config` 下对应子包，不散落到工具包中。
- 新增第三方 SDK 适配放清晰的平台包，例如 `aliyun/<product>`；不得把密钥、账号或生产 endpoint 写进代码目录。
- 新增测试必须放 `src/test/java/com/kellen`，测试资源放 `src/test/resources`。
- 当前按公共能力和技术职责组织包（config、http、security、aliyun 等，接近 package-by-feature）；新增工具应优先进入现有明确能力目录。当某类公共 SDK 或工具能力持续膨胀，且消费者改动总是跨多个目录联动时，才评估继续拆分特性包。演进必须有真实复用和维护痛点，不为少量工具类强行新增低信息目录，并同步发布说明、消费者编译验证和工具目录文档。
- AI 规范统一放 `docs/ai-coding/`；根目录不再新增 `AI_*.md`、`*_SPEC.md` 或临时分析文档。
- 当前仓库不得嵌套 `user`、`message`、`gateway`、`admin-web`、`ai` 等同级项目副本；跨项目修改必须切换到真实同级仓库。
- 构建产物、Maven 旧产物、IDE 文件、本机模块文件和系统文件不得提交，例如 `build/`、`target/`、`.gradle/`、`.idea/`、`*.iml`、`.DS_Store`。

## 变更流程

1. 先判断文件属于公共 API、工具类、配置、SDK 适配、测试、文档、脚本、CI 还是发布配置。
2. 查找现有同类目录，优先复用，不新增平行体系。
3. 移动 Java 文件时同步 `package`、import、测试、README、`UTILS_TOOL_CATALOG.md` 和 `PACKAGE_REFACTOR_GUIDE.md`。
4. 任何包名或公共类移动都必须评估 `../user`、`../message` 等消费者编译影响。
5. 执行 `git diff --check`，涉及 Java 目录或 package 变化时执行 `./gradlew clean build -x test` 和必要的消费者编译。

## 检查清单

- 是否符合 Java Library / Gradle / Maven Publish 主流目录约定？
- 是否保持公共包职责，不把业务服务专属逻辑放入 `utils`？
- 是否更新了工具类归类目录和包结构说明？
- 是否没有嵌套同级项目副本？
- 是否没有移动或替换已有 SDK 凭证、Maven 仓库凭证、密钥或生产配置？
