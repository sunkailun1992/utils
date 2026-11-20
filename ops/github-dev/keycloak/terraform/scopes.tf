# github-api 作用域：后端接口的受众与身份声明。受众含 github-api（各服务校验）与 github-ai（ai 用户令牌交换时，Keycloak 要求被交换令牌的受众包含发起方）。
resource "keycloak_openid_client_scope" "github_api" {
  realm_id               = keycloak_realm.github_dev.id
  name                   = "github-api"
  description            = "个人 GitHub 后端接口：受众 github-api、github-ai，携带 user_id"
  include_in_token_scope = true
}

resource "keycloak_openid_audience_protocol_mapper" "github_api_audience" {
  realm_id                 = keycloak_realm.github_dev.id
  client_scope_id          = keycloak_openid_client_scope.github_api.id
  name                     = "aud-github-api"
  included_custom_audience = "github-api"
  add_to_access_token      = true
  add_to_id_token          = false
}

resource "keycloak_openid_audience_protocol_mapper" "github_api_ai_audience" {
  realm_id                 = keycloak_realm.github_dev.id
  client_scope_id          = keycloak_openid_client_scope.github_api.id
  name                     = "aud-github-ai"
  included_client_audience = keycloak_openid_client.ai.client_id
  add_to_access_token      = true
  add_to_id_token          = false
}

# user_id：user 服务建账号时写入的用户属性（user 库的用户编号），各服务据此加载业务上下文。
resource "keycloak_openid_user_attribute_protocol_mapper" "github_api_user_id" {
  realm_id            = keycloak_realm.github_dev.id
  client_scope_id     = keycloak_openid_client_scope.github_api.id
  name                = "user_id"
  user_attribute      = "user_id"
  claim_name          = "user_id"
  claim_value_type    = "String"
  add_to_access_token = true
  add_to_id_token     = true
  add_to_userinfo     = true
}
