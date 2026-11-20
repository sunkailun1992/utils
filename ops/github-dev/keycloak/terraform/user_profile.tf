# 用户档案（Keycloak 24 起默认启用声明式用户档案，未声明的属性会被丢弃；unmanaged_attribute_policy 保持缺省的关闭）。
# 前四项与 Keycloak 缺省定义一致；user_id 由 user 服务经 Admin API 写入、经 github-api 作用域映射为令牌声明，
# 只许管理员查看与修改，用户不能在账号控制台自己改（否则可冒充他人）。不设必填，避免登录时要求用户补填自己改不了的字段。
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
      edit = ["admin", "user"]
    }
  }

  attribute {
    name               = "email"
    display_name       = "$${email}"
    required_for_roles = ["user"]
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
    name               = "firstName"
    display_name       = "$${firstName}"
    required_for_roles = ["user"]
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
    name               = "lastName"
    display_name       = "$${lastName}"
    required_for_roles = ["user"]
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
