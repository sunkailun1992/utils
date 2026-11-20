# github-dev 域的客户端：网关 BFF、服务间调用方与只作受众的资源方。客户端密钥由 Keycloak 生成，经 terraform output 写入本机私有 env。

# 网关 BFF：机密客户端，授权码加 PKCE；回调只在本机网关与 admin-web 开发服务器。
resource "keycloak_openid_client" "gateway" {
  realm_id                        = keycloak_realm.github_dev.id
  client_id                       = "github-gateway"
  name                            = "GitHub 网关（BFF）"
  access_type                     = "CONFIDENTIAL"
  standard_flow_enabled           = true
  implicit_flow_enabled           = false
  direct_access_grants_enabled    = false
  service_accounts_enabled        = false
  pkce_code_challenge_method      = "S256"
  valid_redirect_uris             = ["http://localhost:7100/login/oauth2/code/keycloak", "http://localhost:8000/login/oauth2/code/keycloak"]
  valid_post_logout_redirect_uris = ["http://localhost:8000/", "http://localhost:7100/"]
  web_origins                     = []
  frontchannel_logout_enabled     = false
  full_scope_allowed              = false
}

# 网关的作用域：默认带 github-api（受众与 user_id）；BFF 不需要离线令牌等可选作用域。
resource "keycloak_client_scope_assignments" "gateway" {
  realm_id        = keycloak_realm.github_dev.id
  client_id       = keycloak_openid_client.gateway.id
  default_scopes  = ["basic", "acr", "profile", "email", "roles", "web-origins", keycloak_openid_client_scope.github_api.name]
  optional_scopes = []
}

# ai：调 ai-agent 时用 RFC 8693 标准令牌交换，把用户令牌换成受众 github-ai-agent、azp 为 github-ai 的令牌。
resource "keycloak_openid_client" "ai" {
  realm_id                        = keycloak_realm.github_dev.id
  client_id                       = "github-ai"
  name                            = "GitHub AI 服务"
  access_type                     = "CONFIDENTIAL"
  standard_flow_enabled           = false
  implicit_flow_enabled           = false
  direct_access_grants_enabled    = false
  service_accounts_enabled        = true
  standard_token_exchange_enabled = true
  full_scope_allowed              = false
}

resource "keycloak_openid_audience_protocol_mapper" "ai_agent_audience" {
  realm_id                 = keycloak_realm.github_dev.id
  client_id                = keycloak_openid_client.ai.id
  name                     = "aud-github-ai-agent"
  included_client_audience = keycloak_openid_client.ai_agent.client_id
  add_to_access_token      = true
  add_to_id_token          = false
}

# rag：调 RAG 引擎的纯机器调用，只开客户端凭证。
resource "keycloak_openid_client" "rag" {
  realm_id                     = keycloak_realm.github_dev.id
  client_id                    = "github-rag"
  name                         = "GitHub RAG 服务"
  access_type                  = "CONFIDENTIAL"
  standard_flow_enabled        = false
  implicit_flow_enabled        = false
  direct_access_grants_enabled = false
  service_accounts_enabled     = true
  full_scope_allowed           = false
}

resource "keycloak_openid_audience_protocol_mapper" "rag_engine_audience" {
  realm_id                 = keycloak_realm.github_dev.id
  client_id                = keycloak_openid_client.rag.id
  name                     = "aud-github-rag-engine"
  included_client_audience = keycloak_openid_client.rag_engine.client_id
  add_to_access_token      = true
  add_to_id_token          = false
}

# prometheus：抓 RAG 引擎 /metrics，只开客户端凭证。
resource "keycloak_openid_client" "prometheus" {
  realm_id                     = keycloak_realm.github_dev.id
  client_id                    = "github-prometheus"
  name                         = "Prometheus 抓取"
  access_type                  = "CONFIDENTIAL"
  standard_flow_enabled        = false
  implicit_flow_enabled        = false
  direct_access_grants_enabled = false
  service_accounts_enabled     = true
  full_scope_allowed           = false
}

resource "keycloak_openid_audience_protocol_mapper" "prometheus_rag_engine_audience" {
  realm_id                 = keycloak_realm.github_dev.id
  client_id                = keycloak_openid_client.prometheus.id
  name                     = "aud-github-rag-engine"
  included_client_audience = keycloak_openid_client.rag_engine.client_id
  add_to_access_token      = true
  add_to_id_token          = false
}

# user-admin：user 服务管理账号用，服务账号只有 realm-management 的 manage-users 与 view-users。
resource "keycloak_openid_client" "user_admin" {
  realm_id                     = keycloak_realm.github_dev.id
  client_id                    = "github-user-admin"
  name                         = "GitHub 用户服务（账号管理）"
  access_type                  = "CONFIDENTIAL"
  standard_flow_enabled        = false
  implicit_flow_enabled        = false
  direct_access_grants_enabled = false
  service_accounts_enabled     = true
  # 管理接口只认在客户端作用域内的角色；服务账号只分配了下面两个角色，完整作用域不多给权限。
  full_scope_allowed = true
}

# 管理接口只认带 kc-admin-api 作用域的令牌（Keycloak 26.8），user 服务调 Admin API 要显式给。
resource "keycloak_client_scope_assignments" "user_admin" {
  realm_id        = keycloak_realm.github_dev.id
  client_id       = keycloak_openid_client.user_admin.id
  default_scopes  = ["basic", "acr", "profile", "email", "roles", "web-origins", "service_account", "kc-admin-api"]
  optional_scopes = []
}

data "keycloak_openid_client" "realm_management" {
  realm_id  = keycloak_realm.github_dev.id
  client_id = "realm-management"
}

resource "keycloak_openid_client_service_account_role" "user_admin_manage_users" {
  realm_id                = keycloak_realm.github_dev.id
  service_account_user_id = keycloak_openid_client.user_admin.service_account_user_id
  client_id               = data.keycloak_openid_client.realm_management.id
  role                    = "manage-users"
}

resource "keycloak_openid_client_service_account_role" "user_admin_view_users" {
  realm_id                = keycloak_realm.github_dev.id
  service_account_user_id = keycloak_openid_client.user_admin.service_account_user_id
  client_id               = data.keycloak_openid_client.realm_management.id
  role                    = "view-users"
}

# 只作受众的资源方：不开任何登录流程。
resource "keycloak_openid_client" "ai_agent" {
  realm_id    = keycloak_realm.github_dev.id
  client_id   = "github-ai-agent"
  name        = "GitHub AI Agent（受众）"
  access_type = "BEARER-ONLY"
}

resource "keycloak_openid_client" "rag_engine" {
  realm_id    = keycloak_realm.github_dev.id
  client_id   = "github-rag-engine"
  name        = "RAG 引擎（受众）"
  access_type = "BEARER-ONLY"
}

resource "keycloak_openid_client" "mcp" {
  realm_id    = keycloak_realm.github_dev.id
  client_id   = "github-mcp"
  name        = "GitHub MCP（受众）"
  access_type = "BEARER-ONLY"
}
