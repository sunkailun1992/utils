# 用户档案（Keycloak 24 起默认启用声明式用户档案，未声明的属性会被丢弃；unmanaged_attribute_policy 保持缺省的关闭）。
# 前四项沿用 Keycloak 缺省的属性与校验，两处按 user 服务调整：
# - 都不设必填：user 服务不收集邮箱与姓、昵称可空，缺省的必填会让每个账号首次登录都被要求补填资料（VERIFY_PROFILE）；
#   现在首次登录只要求改临时口令。
# - username 与 firstName（昵称）以 user 服务为准，由它经 Admin API 写入：用户能看不能改（类似 LDAP 联邦的只读属性），
#   避免在账号控制台里改了与用户中心不一致。email、lastName 不由 user 服务管理，保留用户可改。
# user_id 由 user 服务经 Admin API 写入、经 github-api 作用域映射为令牌声明，只许管理员查看与修改，用户不能在账号控制台自己改（否则可冒充他人）。
resource "keycloak_realm_user_profile" "github_dev" {
  realm_id = keycloak_realm.github_dev.id

  attribute {
    name         = "username"
    display_name = "$${username}"
    validator {
      name   = "length"
      config = { min = "3", max = "255" }
    }
    validator {
      name = "username-prohibited-characters"
    }
    validator {
      name = "up-username-not-idn-homograph"
    }
    permissions {
      view = ["admin", "user"]
      edit = ["admin"]
    }
  }

  attribute {
    name         = "email"
    display_name = "$${email}"
    validator {
      name = "email"
    }
    validator {
      name   = "length"
      config = { max = "255" }
    }
    permissions {
      view = ["admin", "user"]
      edit = ["admin", "user"]
    }
  }

  attribute {
    name         = "firstName"
    display_name = "$${firstName}"
    validator {
      name   = "length"
      config = { max = "255" }
    }
    validator {
      name = "person-name-prohibited-characters"
    }
    permissions {
      view = ["admin", "user"]
      edit = ["admin"]
    }
  }

  attribute {
    name         = "lastName"
    display_name = "$${lastName}"
    validator {
      name   = "length"
      config = { max = "255" }
    }
    validator {
      name = "person-name-prohibited-characters"
    }
    permissions {
      view = ["admin", "user"]
      edit = ["admin", "user"]
    }
  }

  attribute {
    name         = "user_id"
    display_name = "业务用户 ID"
    validator {
      name   = "length"
      config = { min = "1", max = "64" }
    }
    permissions {
      view = ["admin"]
      edit = ["admin"]
    }
  }

  group {
    name                = "user-metadata"
    display_header      = "User metadata"
    display_description = "Attributes, which refer to user metadata"
  }
}
