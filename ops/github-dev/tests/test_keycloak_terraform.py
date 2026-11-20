"""Keycloak 域配置（Terraform）的静态检查：客户端与作用域齐全、BFF 客户端的 PKCE 与回调、管理客户端最小权限、不含明文密钥。

只读 .tf 文本，不依赖 terraform 命令，CI 也能跑；真实生效以 terraform plan 无差异与 nas_kc.py 读回为准。
"""
import re
import subprocess
import unittest
from pathlib import Path

TF = Path(__file__).resolve().parents[1] / 'keycloak' / 'terraform'


def source():
    return '\n'.join(p.read_text(encoding='utf-8') for p in sorted(TF.glob('*.tf')))


def blocks(kind):
    """返回 {资源名: 块正文}，按花括号配对截取。"""
    text, found = source(), {}
    for match in re.finditer(r'resource\s+"' + kind + r'"\s+"([A-Za-z0-9_]+)"\s*\{', text):
        depth, start = 1, match.end()
        for index in range(start, len(text)):
            depth += {'{': 1, '}': -1}.get(text[index], 0)
            if depth == 0:
                found[match.group(1)] = text[start:index]
                break
    return found


def sub_blocks(block, kind):
    """返回块里所有名为 kind 的子块正文（按花括号配对）。"""
    found = []
    for match in re.finditer(r'(?m)^\s*' + kind + r'\s*\{', block):
        depth, start = 1, match.end()
        for index in range(start, len(block)):
            depth += {'{': 1, '}': -1}.get(block[index], 0)
            if depth == 0:
                found.append(block[start:index])
                break
    return found


def attr(block, name):
    match = re.search(r'^\s*' + name + r'\s*=\s*(.+)$', block, re.M)
    return match.group(1).strip() if match else ''


class KeycloakTerraformTests(unittest.TestCase):
    def test_all_clients_and_scopes_declared(self):
        ids = {attr(b, 'client_id').strip('"') for b in blocks('keycloak_openid_client').values()
               if 'keycloak_realm.github_dev.id' in b}
        self.assertEqual(ids, {'github-gateway', 'github-ai', 'github-rag', 'github-prometheus', 'github-user-admin',
                               'github-ai-agent', 'github-rag-engine', 'github-mcp'})
        names = {attr(b, 'name').strip('"') for b in blocks('keycloak_openid_client_scope').values()}
        self.assertIn('github-api', names)

    def test_realm_settings(self):
        realm = blocks('keycloak_realm')['github_dev']
        self.assertEqual(attr(realm, 'realm'), '"github-dev"')
        self.assertEqual(attr(realm, 'access_token_lifespan'), '"5m"')
        self.assertEqual(attr(realm, 'sso_session_idle_timeout'), '"30m"')
        self.assertEqual(attr(realm, 'sso_session_max_lifespan'), '"10h"')
        self.assertEqual(attr(realm, 'default_locale'), '"zh-CN"')
        self.assertRegex(realm, r'brute_force_detection\s*\{')

    def test_gateway_is_confidential_auth_code_with_pkce_and_both_callbacks(self):
        gateway = blocks('keycloak_openid_client')['gateway']
        self.assertEqual(attr(gateway, 'access_type'), '"CONFIDENTIAL"')
        self.assertEqual(attr(gateway, 'standard_flow_enabled'), 'true')
        self.assertEqual(attr(gateway, 'direct_access_grants_enabled'), 'false')
        self.assertEqual(attr(gateway, 'implicit_flow_enabled'), 'false')
        self.assertEqual(attr(gateway, 'pkce_code_challenge_method'), '"S256"')
        self.assertIn('http://localhost:7100/login/oauth2/code/keycloak', gateway)
        self.assertIn('http://localhost:8000/login/oauth2/code/keycloak', gateway)

    def test_machine_clients_only_use_client_credentials(self):
        clients = blocks('keycloak_openid_client')
        for name in ('rag', 'prometheus', 'user_admin'):
            self.assertEqual(attr(clients[name], 'service_accounts_enabled'), 'true', name)
            self.assertEqual(attr(clients[name], 'standard_flow_enabled'), 'false', name)
            self.assertEqual(attr(clients[name], 'direct_access_grants_enabled'), 'false', name)
        for name in ('ai_agent', 'rag_engine', 'mcp'):
            self.assertEqual(attr(clients[name], 'access_type'), '"BEARER-ONLY"', name)

    def test_user_admin_service_account_has_only_manage_and_view_users(self):
        roles = {attr(b, 'role').strip('"') for b in blocks('keycloak_openid_client_service_account_role').values()
                 if 'user_admin' in (attr(b, 'service_account_user_id') or '')}
        self.assertEqual(roles, {'manage-users', 'view-users'})

    def test_github_api_scope_carries_audience_and_user_id(self):
        audience = [b for b in blocks('keycloak_openid_audience_protocol_mapper').values() if 'github_api' in b]
        self.assertTrue(any('"github-api"' in b for b in audience))
        user_id = [b for b in blocks('keycloak_openid_user_attribute_protocol_mapper').values() if 'github_api' in b]
        self.assertTrue(any(attr(b, 'user_attribute') == '"user_id"' and attr(b, 'claim_name') == '"user_id"' for b in user_id))

    def test_master_admin_entries_use_variables_and_admin_role(self):
        clients = blocks('keycloak_openid_client')
        self.assertEqual(attr(clients['terraform_admin'], 'service_accounts_enabled'), 'true')
        realm_role = blocks('keycloak_openid_client_service_account_realm_role')['terraform_admin']
        self.assertIn('master_admin', attr(realm_role, 'role'))
        user = blocks('keycloak_user')['kellen_admin']
        self.assertEqual(attr(user, 'value'), 'var.admin_user_password')

    def test_terraform_admin_has_admin_api_scope(self):
        # Keycloak 26.8 的管理接口只认带 kc-admin-api 作用域的令牌（引导客户端缺省带，新建的管理客户端要显式给）。
        assignment = blocks('keycloak_client_scope_assignments')['terraform_admin']
        self.assertIn('keycloak_openid_client.terraform_admin.id', assignment)
        self.assertIn('"kc-admin-api"', attr(assignment, 'default_scopes'))

    def test_user_admin_has_admin_api_scope(self):
        assignment = blocks('keycloak_client_scope_assignments')['user_admin']
        self.assertIn('keycloak_openid_client.user_admin.id', assignment)
        self.assertIn('"kc-admin-api"', attr(assignment, 'default_scopes'))

    def test_gateway_refresh_tokens_are_not_rotated(self):
        self.assertEqual(attr(blocks('keycloak_realm')['github_dev'], 'revoke_refresh_token'), 'false')

    def test_ai_can_exchange_tokens_for_ai_agent_audience(self):
        ai = blocks('keycloak_openid_client')['ai']
        self.assertEqual(attr(ai, 'standard_token_exchange_enabled'), 'true')
        mapper = blocks('keycloak_openid_audience_protocol_mapper')['ai_agent_audience']
        self.assertIn('keycloak_openid_client.ai.id', mapper)
        self.assertIn('keycloak_openid_client.ai_agent.client_id', mapper)

    def test_user_profile_declares_admin_only_user_id(self):
        # Keycloak 24 起启用声明式用户档案，未声明的属性会被丢弃：user_id 必须声明，且只许管理员查看与修改，用户不能自改冒充他人。
        profile = blocks('keycloak_realm_user_profile')['github_dev']
        self.assertIn('keycloak_realm.github_dev.id', profile)
        self.assertNotIn('unmanaged_attribute_policy', profile)
        user_id = [b for b in sub_blocks(profile, 'attribute') if attr(b, 'name') == '"user_id"']
        self.assertEqual(len(user_id), 1)
        permissions = sub_blocks(user_id[0], 'permissions')[0]
        self.assertEqual(attr(permissions, 'view'), '["admin"]')
        self.assertEqual(attr(permissions, 'edit'), '["admin"]')

    def test_user_profile_keeps_keycloak_default_attributes(self):
        profile = blocks('keycloak_realm_user_profile')['github_dev']
        names = [attr(b, 'name').strip('"') for b in sub_blocks(profile, 'attribute')]
        self.assertEqual(names, ['username', 'email', 'firstName', 'lastName', 'user_id'])
        required = {attr(b, 'name').strip('"'): attr(b, 'required_for_roles') for b in sub_blocks(profile, 'attribute')}
        for name in ('email', 'firstName', 'lastName'):
            self.assertEqual(required[name], '["user"]', name)
        self.assertEqual(required['user_id'], '')

    def test_ai_tokens_carry_user_id_for_token_exchange(self):
        # 交换出的令牌按 github-ai 自己的映射器生成；它不带 github-api 作用域（避免受众扩大），所以 user_id 映射器挂在客户端上。
        mappers = [b for b in blocks('keycloak_openid_user_attribute_protocol_mapper').values()
                   if 'keycloak_openid_client.ai.id' in b]
        self.assertEqual(len(mappers), 1)
        mapper = mappers[0]
        self.assertEqual(attr(mapper, 'user_attribute'), '"user_id"')
        self.assertEqual(attr(mapper, 'claim_name'), '"user_id"')
        self.assertEqual(attr(mapper, 'add_to_access_token'), 'true')
        self.assertEqual(attr(mapper, 'add_to_id_token'), 'false')
        assignments = blocks('keycloak_client_scope_assignments')
        self.assertFalse(any('keycloak_openid_client.ai.id' in b and 'github_api' in b for b in assignments.values()))

    def test_no_plaintext_secrets_or_hosts(self):
        text = source()
        self.assertNotRegex(text, r'(client_secret|password|secret)\s*=\s*"[^"$]')
        self.assertNotRegex(text, r'\b\d{1,3}(\.\d{1,3}){3}\b')

    def test_state_plugin_dir_and_tfvars_are_git_ignored_but_lock_file_is_not(self):
        # 状态文件含客户端密钥：即使有人在本目录直接 init，也不能被 git add 进去（GitHub 官方 Terraform 忽略模板）。
        def ignored(name):
            return subprocess.run(['git', 'check-ignore', '-q', str(TF / name)], cwd=TF).returncode == 0
        for name in ('terraform.tfstate', 'terraform.tfstate.backup', '.terraform/providers/x', 'dev.tfvars', 'crash.log'):
            self.assertTrue(ignored(name), name)
        self.assertFalse(ignored('.terraform.lock.hcl'))


if __name__ == '__main__':
    unittest.main()
