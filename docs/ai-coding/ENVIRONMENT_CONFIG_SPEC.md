# GitHub 环境配置入口规范

个人项目与美联公司开发环境分别维护。代码使用 main（正式主干）、test（集成测试）、preprod（发布候选）和短功能分支；Git 阶段不自动切换 Spring profile 或 Nacos namespace，也不代表环境已部署。

| profile | 默认 namespace | Nacos 地址变量 |
|---|---|---|
| dev | github-dev | GITHUB_NACOS_DEV_SERVER_ADDR → GITHUB_NACOS_SERVER_ADDR → 当前开发服务默认地址 |
| test | github-dev | 同 dev |
| prod | github-dev | 同 dev |

按用户当前决定，三个 profile 均连接个人 `github-dev`，统一接受 `GITHUB_NACOS_DEV_NAMESPACE` 显式覆盖，不再接受旧 `NACOS_NAMESPACE` 回退。`test`、`prod` profile 名称不代表已经建立或部署对应环境；不得指向公司 dev 临时替代。未来建立独立环境时，需同时调整 profile、契约测试与本规范。

application.yml 仅保留端口、服务名、默认 APP_ENV=dev 与 GITHUB_NACOS_GROUP。application-*.yml 同时声明连接地址、namespace、config/discovery 绑定和本 profile 的 spring.config.import。真实中间件、业务开关和密钥仍在对应 Nacos；不将业务配置搬回本地 profile。

config 与 discovery 共用 custom.nacos-ip/custom.namespace。Dubbo、Nacos AI Registry 和 Prompt SDK 也必须显式引用它们；独立 SDK 的 username/password 不能假设继承 Spring Cloud 配置。Gateway 不承担业务认证。

个人环境的可提交模板、私有渲染、CAS 发布、数据库与统一 X File Storage 入口见 [GitHub 独立开发环境](../../ops/github-dev/README.md)。本机 dev 引导首选各服务仓库根目录的私有 `config/application-dev.yml`（也可用 `.yaml`）。写入前先在服务仓库设置 Git 忽略规则，确认路径已忽略且未跟踪，再保存为当前用户持有的 `0600` 普通文件。`~/.config/kellen-github/` 下的私有输入同样不进 Git。前端通过 `http://localhost:7100` 访问后端。

本机直接启动时，Spring Boot 从私有引导文件读取 `GITHUB_NACOS_DEV_SERVER_ADDR`、`GITHUB_NACOS_DEV_NAMESPACE`、`GITHUB_NACOS_GROUP`、`GITHUB_NACOS_USERNAME`、`GITHUB_NACOS_PASSWORD`。IDEA、`bootRun` 或本机直接执行 `java -jar` 时，工作目录使用对应服务仓库根。旧 `config/application-dev.properties` 仍可用。同目录两种格式并存时，同名属性以 properties 为准。迁移后将旧文件备份到 Spring 配置搜索路径之外。直接启动时 OS 环境变量可覆盖私有文件。使用个人工作区观测入口且仅保留 YAML 时，Nacos 地址、group、账号和密码从 YAML 读取，启动器继续固定 `dev` 和 `github-dev`。服务器托管 Jar 继续读取目标服务器的私有 env。

显式启用 `test` 或 `prod` 时，Spring 不读取 `dev` 引导文件，连接仍沿用各 profile 中的个人 Nacos 变量与默认值。地址未覆盖时遵循对应 profile 既有约定。

当前个人 Nacos 的控制台使用 18080，Spring Cloud/SDK server-addr 使用 8848，客户端 gRPC 使用 9848；控制台 URL 不能直接作为 server-addr。命名空间 ID 为 github-dev，与显示名一致。真实地址及账号密码只填入上述本机私有文件。

只读校验：在 utils 运行 python3 ops/github-dev/scripts/verify_configuration.py。发布前读回旧配置，已有内容更新必须 CAS；发布后逐项读回，不能以命令成功代替运行验收。
