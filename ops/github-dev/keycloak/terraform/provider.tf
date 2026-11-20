# 管理连接：master 域里的管理客户端（首次为 Keycloak 引导用的临时客户端，配好后换成 terraform-admin）。
provider "keycloak" {
  url                 = var.keycloak_url
  realm               = "master"
  client_id           = var.admin_client_id
  client_secret       = var.admin_client_secret
  root_ca_certificate = file(var.root_ca_file)
}
