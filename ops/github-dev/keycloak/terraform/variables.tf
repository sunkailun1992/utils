variable "keycloak_url" {
  description = "Keycloak 对外地址（https://<NAS主机>:18443），来自本机私有 env"
  type        = string
}

variable "root_ca_file" {
  description = "mkcert 根证书路径（mkcert -CAROOT 下的 rootCA.pem）"
  type        = string
}

variable "admin_client_id" {
  description = "master 域管理客户端 ID"
  type        = string
}

variable "admin_client_secret" {
  description = "master 域管理客户端密钥，来自本机私有 env"
  type        = string
  sensitive   = true
}

variable "admin_user_password" {
  description = "正式管理员 kellen-admin 的初始口令，来自本机私有 env（由用户存入 1Password）"
  type        = string
  sensitive   = true
}
