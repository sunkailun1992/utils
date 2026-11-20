output "issuer_uri" {
  description = "github-dev 域的签发方地址（各服务 issuer-uri）"
  value       = "${var.keycloak_url}/realms/${keycloak_realm.github_dev.realm}"
}

output "client_secrets" {
  description = "各机密客户端的密钥，只写入本机私有 env，不打印"
  sensitive   = true
  value = {
    gateway         = keycloak_openid_client.gateway.client_secret
    ai              = keycloak_openid_client.ai.client_secret
    rag             = keycloak_openid_client.rag.client_secret
    prometheus      = keycloak_openid_client.prometheus.client_secret
    user_admin      = keycloak_openid_client.user_admin.client_secret
    terraform_admin = keycloak_openid_client.terraform_admin.client_secret
  }
}
