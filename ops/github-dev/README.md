# GitHub 独立开发环境

本目录是个人 GitHub 项目族的配置入口。公司 `dev` 保留原状；个人服务的配置、服务发现和 AI Registry 使用独立的 `github-dev` namespace，默认 group 为 `DEFAULT_GROUP`。`test`、`prod` profile 分别默认 `github-test`、`github-prod`，本轮只创建和配置 `github-dev`。

## 配置与凭据

- `nacos/DEFAULT_GROUP/`：37 个可提交模板，只含公共默认值与 `GITHUB_*` 占位符。
- `~/.config/kellen-github/dev.env`：本机私有配置，普通文件、权限 `0600`，不进 Git。
- 各 Java 服务根目录的 `config/application-dev.properties`：本机私有 Nacos 引导配置，权限 `0600`，已加入对应 `.gitignore`。仅保存 Nacos dev 地址、命名空间、group、username/password 五个连接键；不要复制模型、数据库或业务配置进去。
- `~/.config/kellen-github/nacos-rendered/DEFAULT_GROUP/`：包含真实连接值及通用模型密钥的私有渲染结果，文件权限 `0600`。禁止复制到仓库或 Wiki。
- 模型密钥经用户明确确认沿用其通用自有密钥；没有复制医院、患者、问卷、报告模板、企微或设备业务配置。微信应用身份未因模型密钥授权而复制。
- Nacos 引导只接受 `GITHUB_NACOS_*`，不再回落到旧 `NACOS_NAMESPACE`。Spring discovery/config、Dubbo 与未来启用的 AI SDK namespace 都引用同一个 `custom.namespace`。
- Dubbo 使用独立 `GITHUB_DEV_DUBBO` 分组；Redis 使用独立逻辑库 `12`；RabbitMQ 使用 `/github-dev` vhost；RAG 使用 `github_dev_rag` collection。

## 数据库

个人 schema 在已有开发 MySQL 上干净初始化，不复制公司数据。应用使用独立 `github_dev` 账户，仅获下表 schema 的数据读写和必要 DDL 权限；没有公司原库权限，也没有全局管理权限。

| 服务 | schema | 表数 |
|---|---|---:|
| ai | `github_dev_ai` | 17 |
| ai-memory | `github_dev_ai_memory` | 9 |
| rag | `github_dev_rag` | 4 |
| message | `github_dev_message` | 3 |
| user | `github_dev_user` | 15 |
| mcp | `github_dev_mcp` | 2 |
| report | `github_dev_report` | 2 |

共 52 张表，按 GitHub 当前 Entity 和各服务权威 DDL 生成。`ai-agent`、gateway 无业务库。GitHub 现有 AI 代码仍引用的问卷等表只保留空结构；没有同步新的公司业务代码。未被 GitHub 代码使用的公司报告、评估、用药提醒、设备表不创建，原库不删除。

仅初始化通用租户 `100`、管理员角色及资源权限。没有复制或创建人员账号，没有默认弱口令，没有预置公司的 OAuth confidential client。新的 AI 服务身份使用独立密钥并强制签名；它与人员账户分开。真实人员管理员需通过单独受控流程建立。

生成初始化计划（只输出文件，不连接数据库）：

```bash
python3 ops/github-dev/scripts/prepare_databases.py --output /tmp/github-dev-db-plan
```

该计划明确 `CREATE DATABASE`，已有同名库时拒绝继续，不能当作任意环境的重置脚本。它读取同级仓库的权威 DDL，不维护第二套业务 SQL 副本。`ai-file-storage-platform.sql` 使用当前 schema 列存在检查，可由预建过程与应用 DDL runner 先后执行。

## 统一存储

新文件通过 X File Storage `2.3.0`，默认平台 `minio-private`，使用已授权的测试 MinIO 与独立私有 bucket `kellen-github-dev`。本轮不安装新的 MinIO 服务。新对象前缀为 `github/ai`，不导入公司文件。

AI 上传策略、完成登记和展示下载都调用统一门面；小程序提交完整签名 `formFields`，数据库保存 `storage_platform`。已登记文件继续按记录中的平台读取，切换默认平台不会把历史文件读到另一后端。历史空平台记录仅为原 OSS 兼容，新平台明确选定但配置错误时拒绝回落。凭据不传给前端。

## 校验、渲染与发布

在 `utils` 仓库执行：

```bash
python3 ops/github-dev/scripts/verify_configuration.py
python3 ops/github-dev/scripts/render_nacos.py
python3 ops/github-dev/scripts/sync_nacos.py
```

最后一项默认只读，使用仓库现有 Gradle 依赖中的官方 Nacos SDK，逐 dataId 读回核对，日志只在私有目录。新建配置需显式 `--publish`；更新已有配置还需 `--previous <已复核的旧配置目录>`，程序比对旧内容并通过 CAS 发布，遇到漂移拒绝覆盖。真实配置只发到固定的 `github-dev/DEFAULT_GROUP`，不写 Nacos 数据库。

## 本地启动

IDE、`bootRun`、`java -jar` 的工作目录统一为当前服务仓库根目录。Spring Boot 会自动读取本机 `config/application-dev.properties`，`application.yml` 保持原样，默认 profile 为 `dev`。该文件填写 `GITHUB_NACOS_DEV_SERVER_ADDR`、`GITHUB_NACOS_DEV_NAMESPACE=github-dev`、`GITHUB_NACOS_GROUP=DEFAULT_GROUP`、`GITHUB_NACOS_USERNAME`、`GITHUB_NACOS_PASSWORD`；真实值不进 Git，OS 环境变量优先。`test`/`prod` 不读取 dev 文件；缺少私有文件且没有地址变量时，dev 启动立即失败。

本机 Nacos 控制台端口是 `18080`，应用端 `server-addr` 使用 `8848`，gRPC 使用 `9848`。命名空间显示名和真实 ID 均为 `github-dev`。控制台 URL 与 SDK 地址分别配置，不能把控制台地址直接填进应用。

个人公共制品使用 `com:utils:1.5.1` 和 `com:rpc-api:1.0.7`。与美联共用阿里云 Maven 仓库地址，但美联制品使用 `com.kellen` group，两边独立发布和递增版本。Maven 凭据从私有环境或用户 Gradle 配置注入，不放进本目录；发布前核对目标版本未占用，发布后核对远程 POM、JAR 和本地构建的 SHA-256。历史验收 JSON 中的 `1.4.9` 是当时运行快照，不能作为新构建的坐标。

先构建目标服务，再从 `utils` 启动：

```bash
python3 ops/github-dev/scripts/start_service.py user --dry-run
python3 ops/github-dev/scripts/start_service.py user
```

启动器只解析私有环境文件中的 `GITHUB_*`，不执行 shell 内容，固定 `APP_ENV=dev` 与 `github-dev`。其它服务名为 `gateway`、`message`、`report`、`ai`、`ai-agent`、`ai-memory`、`rag`、`mcp`。网关端口 `7100`；管理端前端沿用 `http://localhost:7100`。允许浏览器 Origin 明确列在 `gateway.cors.allowed-origins`，默认仅本机 `8000` 开发前端；需要其它地址时显式更新，不放开 `*`。

## 本轮验证与边界

- Nacos 37 个 dataId 通过官方 SDK 发布和逐一读回；公司 `dev` 配置不覆盖。
- 七个个人数据库的表数量与 `storage_platform` 已读回；应用账户能读个人库，访问公司原 `user.auth_user` 被 MySQL 权限拒绝。
- MySQL 存储平台 migration 在个人临时同构表完成首次加列和第二次 no-op，列数量仍为一，临时表已回收。
- X File Storage 在真实 MinIO 验证 POST 上传、存在性、短签名 GET、大小限制，以及篡改 owner key/MIME 拒绝，测试对象回收。
- Java/前端/小程序回归、公共制品、版本和各仓源 SHA 见各仓 `docs/security-architecture-sync.md` 及本目录交付清单。
- 实际临时启动验证：gateway 与 ai-agent 聚合 health 为 `200 UP`；gateway 经独立 Nacos 发现 user，返回个人租户 `100`，匿名受保护接口 `403`。CORS 允许 Origin 为 `200`、未允许或 `null` Origin 为 `403`；敏感端点 `404`；40 个并发公共读取得到 19 个 `200` 与 21 个 `429`，伪造不同 `X-Forwarded-For` 不能避开 socket IP 限流。ai-agent 匿名模型入口为 `401`，未进入模型执行。
- user 的 liveness/readiness 为 `200 UP`，但聚合 health 为 `503`：共用 ES `7.17.9` 与传递的 `elasticsearch-java 9.2.6` cluster-health 响应解析不兼容。本轮保留此结果，没有禁用健康检查或改动共用 ES；ES 索引能力未验收。后续应独立规划个人 ES 版本匹配，不能把已通过的认证或数据库验证当作 ES 验收。
- 业务演示种子、Prompt 自动同步、AgentCard 自动发布均关闭，避免重新导入既有医疗资源。模型配置接通不等于 AI 对话或付费模型效果验收；本轮不发付费推理请求。微信登录、真实业务流程、RPC/模型组合仍分别验收。
- 本轮功能源分支为 `codex/sync-security-architecture-20261103`，Git 集成目标为 `main`。交付清单和运行验收 JSON 是 Git 发布前的验证快照；当前提交与推送状态以各仓库 Git refs 及远端读回为准。MavenLocal、GitHub CI、远程 Maven 制品和真实服务部署分别验收。
