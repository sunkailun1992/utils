# master 域的正式管理入口：Terraform 用的 terraform-admin 服务账号与人工登录控制台的 kellen-admin（口令由用户存入 1Password）。
# 首次 apply 用 Keycloak 引导用的临时客户端，之后 Terraform 改用 terraform-admin，临时客户端删除。
data "keycloak_realm" "master" {
  realm = "master"
}

data "keycloak_role" "master_admin" {
  realm_id = data.keycloak_realm.master.id
  name     = "admin"
}

resource "keycloak_openid_client" "terraform_admin" {
  realm_id                     = data.keycloak_realm.master.id
  client_id                    = "terraform-admin"
  name                         = "Terraform 管理"
  access_type                  = "CONFIDENTIAL"
  standard_flow_enabled        = false
  implicit_flow_enabled        = false
  direct_access_grants_enabled = false
  service_accounts_enabled     = true
}

resource "keycloak_openid_client_service_account_realm_role" "terraform_admin" {
  realm_id                = data.keycloak_realm.master.id
  service_account_user_id = keycloak_openid_client.terraform_admin.service_account_user_id
  role                    = data.keycloak_role.master_admin.name
}

resource "keycloak_user" "kellen_admin" {
  realm_id = data.keycloak_realm.master.id
  username = "kellen-admin"
  enabled  = true

  initial_password {
    value     = var.admin_user_password
    temporary = false
  }
}

resource "keycloak_user_roles" "kellen_admin" {
  realm_id   = data.keycloak_realm.master.id
  user_id    = keycloak_user.kellen_admin.id
  role_ids   = [data.keycloak_role.master_admin.id]
  exhaustive = false
}

# Keycloak 26.8 的管理接口只认带 kc-admin-api 作用域的令牌：引导客户端缺省带，新建的管理客户端要显式给（2026-11-21 实测：不带时 /admin/serverinfo 返回 401）。
resource "keycloak_client_scope_assignments" "terraform_admin" {
  realm_id        = data.keycloak_realm.master.id
  client_id       = keycloak_openid_client.terraform_admin.id
  default_scopes  = ["basic", "acr", "profile", "email", "roles", "web-origins", "service_account", "kc-admin-api"]
  optional_scopes = []
}
