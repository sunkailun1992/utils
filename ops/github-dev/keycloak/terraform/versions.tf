# Keycloak 域配置（第5阶段，2026-11-20）：用 Keycloak 官方 Terraform Provider 管理 NAS 上的 github-dev 域、客户端与作用域。
# 状态文件含客户端密钥，放仓库外的私有目录，init 时用 -backend-config 指定路径，不把本机路径写进仓库。
terraform {
  required_version = ">= 1.14.0"
  required_providers {
    keycloak = {
      source  = "keycloak/keycloak"
      version = "~> 5.11"
    }
  }
  backend "local" {}
}
