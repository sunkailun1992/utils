# github-dev 域：个人 GitHub 九个服务与 admin-web 的统一登录。账号由 user 服务经 Admin API 管理，不在 Terraform 里。
resource "keycloak_realm" "github_dev" {
  realm                       = "github-dev"
  enabled                     = true
  display_name                = "GitHub Dev"
  ssl_required                = "external"
  default_signature_algorithm = "RS256"
  # 令牌与会话时长：访问令牌 5 分钟（网关会话里自动刷新），SSO 空闲 30 分钟、最长 10 小时。
  access_token_lifespan    = "5m"
  sso_session_idle_timeout = "30m"
  sso_session_max_lifespan = "10h"
  # 不开刷新令牌轮换：BFF 是机密客户端，RFC 9700 以客户端认证约束刷新令牌；并发刷新时轮换会让同一会话的其他请求失效（第5阶段裁定）。
  revoke_refresh_token = false
  password_policy      = "length(12) and notUsername"
  # 允许改用户名：管理员在 user 服务改用户名时经 Admin API 同步过来（关着时 Keycloak 拒绝，error-user-attribute-read-only）；
  # 用户自己能否改由用户档案的 edit 权限决定，这里只许管理员（见 user_profile.tf）。
  edit_username_allowed = true

  internationalization {
    supported_locales = ["zh-CN", "en"]
    default_locale    = "zh-CN"
  }

  security_defenses {
    brute_force_detection {
      permanent_lockout          = false
      max_login_failures         = 5
      wait_increment_seconds     = 60
      max_failure_wait_seconds   = 900
      failure_reset_time_seconds = 43200
    }
  }
}
