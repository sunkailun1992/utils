# Keycloak 域配置（Terraform）

第5阶段（2026-11-20/21）起，个人 GitHub 的统一登录由 NAS 上的 Keycloak 26.8.2 负责（`orb-keycloak`，HTTPS 宿主端口 18443，证书由 mkcert 签发）。本目录用 Keycloak 官方 Terraform Provider（`keycloak/keycloak`，`~> 5.11`）管理：

| 文件 | 内容 |
|---|---|
| `realm.tf` | `github-dev` 域：令牌与会话时长、不轮换刷新令牌、zh-CN、暴力破解防护、密码策略 |
| `scopes.tf` | `github-api` 作用域：受众 `github-api` 与 `github-ai`，用户属性 `user_id` 映射为令牌声明 |
| `clients.tf` | 网关 BFF、ai（令牌交换）、rag 与 prometheus（客户端凭证）、user-admin（账号管理），以及只作受众的 ai-agent、rag-engine、mcp |
| `user_profile.tf` | 用户档案：保留 Keycloak 缺省的四个属性（都选填；`username` 与 `firstName` 以 user 服务为准，只许管理员修改），声明 `user_id`（只许管理员查看与修改；Keycloak 24 起未声明的属性会被丢弃） |
| `master.tf` | master 域的 `terraform-admin` 服务账号与人工管理员 `kellen-admin` |
| `outputs.tf` | 签发方地址；客户端密钥（sensitive，只写入本机私有 env） |

## 怎么运行

不在本目录直接跑 `terraform`：用本机私有工具 `~/.config/kellen-github/tools/kc_terraform.py`，它从私有 env 组装 `TF_VAR_*`、把状态与插件目录放到 `~/.config/kellen-github/phase5/terraform/`（0700，状态含客户端密钥），并只对本进程把 `NO_PROXY` 设为 NAS 主机。首次初始化：

```bash
TF_DATA_DIR=~/.config/kellen-github/phase5/terraform/.terraform terraform init -backend-config="path=$HOME/.config/kellen-github/phase5/terraform/terraform.tfstate"
```

日常：`python3 -I kc_terraform.py plan -detailed-exitcode`（0 为无差异）、`python3 -I kc_terraform.py apply`、`python3 -I kc_terraform.py export-secrets`（客户端密钥写进 `keycloak.env` 与 `dev.env`，改前备份 dev.env）。只读核对用 `nas_kc.py summary` 与 `nas_kc.py token-check <客户端>`。

## 注意

- Keycloak 26.8 的管理接口只认带 `kc-admin-api` 作用域的令牌：`terraform-admin` 与 `github-user-admin` 都显式分配了它；新建管理类客户端时照做，否则 `/admin/*` 返回 401。
- 管理接口只认在客户端作用域内的角色：`github-user-admin` 开完整作用域（服务账号只分配了 `manage-users`、`view-users`）。
- `github-api` 作用域的受众带 `github-ai`：Keycloak 标准令牌交换要求被交换的用户令牌受众包含发起交换的客户端。
- 账号（`github-dev` 域的用户）由 user 服务经 Admin API 管理，不进 Terraform：用户名、昵称（`firstName`）、`user_id`、启停与临时口令由 user 服务写入；邮箱与姓选填（user 服务不收集），首次登录只要求改临时口令。域开了「允许改用户名」，供管理员改名同步；用户在账号控制台只能改邮箱与姓。
- 首次部署用 Keycloak 的临时引导管理客户端 apply，之后改用 `terraform-admin`，临时客户端与编排里的引导环境变量已删除。
- `terraform-admin` 丢失或密钥泄露时，按 Keycloak 官方的管理员恢复做法（`kc.sh bootstrap-admin service`，连同一个库）建一个临时管理客户端，把它的 ID 与密钥写进 `keycloak.env` 的 `KC_BOOTSTRAP_ADMIN_CLIENT_ID`、`KC_BOOTSTRAP_ADMIN_CLIENT_SECRET`，以 `KC_TF_USE_BOOTSTRAP=1` 运行 `kc_terraform.py apply` 轮换 `terraform-admin` 的密钥，再 `export-secrets`；完成后删除临时客户端与这两个键。
- 真库测试用户 `fleet-live-test`（`user_id` 为 `live-test-0001`，邮箱用保留域 `example.invalid`）由 `~/.config/kellen-github/tools/kc_test_user.py` 维护，不进 Terraform；它生成 `fleet-kc-test.env` 供 utils 的 `KeycloakLiveTest` 使用。
- 改域本身（`keycloak_realm.github_dev`）时，`realm-management` 数据源会推迟到应用阶段才读，计划里两条 github-user-admin 的服务账号角色会显示为替换（配置并没变）。这时先 `apply -target=keycloak_realm.github_dev`（连同要改的其他资源），再整体 `plan -detailed-exitcode` 确认无差异（2026-11-21 改档案与改名设置时这样做）。
