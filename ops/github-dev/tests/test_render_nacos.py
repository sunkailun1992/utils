"""注释随 Nacos 模板一起渲染；私有值必须保持为完整的 YAML 字符串。"""
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from render_nacos import overlay_document, render, render_directory, render_document, yaml_json


class NacosRenderTests(unittest.TestCase):
    def test_comments_groups_and_spring_placeholders_survive_render(self):
        source = ('# 私有值来自 ${GITHUB_SECRET}，注释不替换\n'
                  'service:\n'
                  '  # 连接凭据\n'
                  '  secret: "${GITHUB_SECRET}" # 保留行尾说明\n'
                  '\n'
                  '  endpoint: ${GITHUB_ENDPOINT:https://example.invalid/v1}\n'
                  '  ref: ${custom.namespace}\n')
        actual = render_document(source, {'GITHUB_SECRET': 'a: #b\n"引号"\\尾部'})
        self.assertIn('# 私有值来自 ${GITHUB_SECRET}，注释不替换', actual)
        self.assertIn('# 保留行尾说明\n\n', actual)
        self.assertIn('  ref: ${custom.namespace}\n', actual)
        self.assertEqual(yaml_json(actual), render(yaml_json(source), {'GITHUB_SECRET': 'a: #b\n"引号"\\尾部'}))

    def test_empty_defaults_and_scalar_types_are_preserved(self):
        source = 'empty: ${GITHUB_EMPTY:}\nport: ${GITHUB_PORT:6334}\non: true\ncount: 2\n'
        self.assertEqual(yaml_json(render_document(source, {})), yaml_json('empty: ""\nport: "6334"\non: true\ncount: 2\n'))

    def test_missing_secret_fails_without_exposing_content(self):
        with self.assertRaisesRegex(ValueError, '^缺少私有配置键：GITHUB_MISSING$'):
            render_document('secret: ${GITHUB_MISSING}\n', {})

    def test_no_substitution_is_byte_identical(self):
        source = '# 已有中文说明\n---\nvalue: "${custom.namespace}"\nlist: [a, b]\n'
        self.assertEqual(render_document(source, {}), source)

    def test_keycloak_placeholders_stay_for_runtime_resolution(self):
        # Keycloak 的客户端密钥与签发方只由各服务运行时的环境变量解析（按服务限定分发），不以明文写进 Nacos；渲染时环境里有值也不替换。
        source = ('custom:\n'
                  '  # 签发方\n'
                  '  keycloak-issuer-uri: ${GITHUB_KEYCLOAK_ISSUER_URI}\n'
                  '  # 客户端密钥\n'
                  '  client-secret: ${GITHUB_KEYCLOAK_AI_CLIENT_SECRET}\n'
                  '  # 其它私有值照常渲染\n'
                  '  host: ${GITHUB_POSTGRESQL_HOST}\n')
        environment = {'GITHUB_KEYCLOAK_ISSUER_URI': 'https://issuer.invalid/realms/x',
                       'GITHUB_KEYCLOAK_AI_CLIENT_SECRET': 'must-not-appear', 'GITHUB_POSTGRESQL_HOST': 'db.invalid'}
        actual = render_document(source, environment)
        self.assertIn('keycloak-issuer-uri: ${GITHUB_KEYCLOAK_ISSUER_URI}\n', actual)
        self.assertIn('client-secret: ${GITHUB_KEYCLOAK_AI_CLIENT_SECRET}\n', actual)
        self.assertNotIn('must-not-appear', actual)
        self.assertEqual(yaml_json(actual)['custom']['host'], 'db.invalid')

    def test_runtime_placeholders_need_no_private_value(self):
        self.assertIn('${GITHUB_KEYCLOAK_RAG_CLIENT_SECRET}', render_document('secret: ${GITHUB_KEYCLOAK_RAG_CLIENT_SECRET}\n', {}))

    def test_duplicate_keys_fail_instead_of_silently_overwriting_a_value(self):
        with self.assertRaisesRegex(ValueError, '重复键'):
            render_document('service:\n  enabled: true\n  enabled: false\n', {})

    def test_multiline_and_unicode_scalar_positions(self):
        source = '键: "前缀 ${GITHUB_NAME} 后缀"\ntext: |\n  before ${GITHUB_NAME}\n  after\n# 下一组\nnext: true\n'
        actual = render_document(source, {'GITHUB_NAME': '测试'})
        self.assertIn('# 下一组\nnext: true', actual)
        self.assertEqual(yaml_json(actual), render(yaml_json(source), {'GITHUB_NAME': '测试'}))


class NacosOverlayTests(unittest.TestCase):
    """保值渲染：模板只提供注释、键序与结构，同路径的值取远端。"""

    TEMPLATE = ('# 文件说明\n'
                'agent:\n'
                '  # 生成参数来源\n'
                '  source: SERVICE\n'
                '  # 上游地址\n'
                '  base-url: https://api.example.invalid\n'
                '  # 运行期密钥占位符\n'
                '  api-key: rendered-secret\n'
                '  # 超时（毫秒）\n'
                '  timeout: 1000\n'
                '  # 列表\n'
                '  hosts: [a, b]\n')

    def test_remote_values_win_and_template_comments_survive(self):
        remote = ('agent:\n  source: DEPLOYMENT\n  base-url: http://gateway.invalid:14000/v1\n'
                  '  api-key: ${GITHUB_AGENT_KEY}\n  timeout: 2500\n  hosts: [c, d]\n')
        actual = overlay_document(self.TEMPLATE, remote)
        self.assertEqual(yaml_json(actual), yaml_json(remote))
        for comment in ('# 文件说明', '# 生成参数来源', '# 运行期密钥占位符', '# 列表'):
            self.assertIn(comment, actual)
        self.assertIn('  timeout: 2500\n', actual)

    def test_unchanged_values_keep_template_text(self):
        self.assertEqual(overlay_document(self.TEMPLATE, self.TEMPLATE), self.TEMPLATE)

    def test_remote_only_keys_fail_unless_declared_as_drops(self):
        remote = self.TEMPLATE.replace('  timeout: 1000\n', '  timeout: 1000\n  retired-flag: true\n')
        with self.assertRaisesRegex(ValueError, r'^远端有模板没有的键：agent\.retired-flag'):
            overlay_document(self.TEMPLATE, remote)
        dropped = overlay_document(self.TEMPLATE, remote, drops={'agent.retired-flag'})
        self.assertNotIn('retired-flag', yaml_json(dropped)['agent'])

    def test_template_only_keys_keep_their_rendered_value(self):
        remote = self.TEMPLATE.replace('  timeout: 1000\n', '')
        self.assertEqual(yaml_json(overlay_document(self.TEMPLATE, remote))['agent']['timeout'], 1000)

    def test_structure_mismatch_fails_without_echoing_values(self):
        for remote in ('agent:\n  source: SERVICE\n  base-url: x\n  api-key: remote-secret\n  timeout: 1000\n  hosts: [a]\n',
                       'agent:\n  source: {nested: remote-secret}\n  base-url: x\n  api-key: y\n  timeout: 1\n  hosts: [a, b]\n'):
            with self.assertRaises(ValueError) as caught:
                overlay_document(self.TEMPLATE, remote)
            self.assertNotIn('remote-secret', str(caught.exception))
            self.assertRegex(str(caught.exception), r'agent\.(hosts|source)')

    def test_block_scalar_and_empty_value_positions(self):
        template = '# 说明\ntext: |\n  old line\n# 空值\nempty:\n# 下一组\nnext: true\n'
        remote = 'text: "新\\n两行"\nempty: "值: #不是注释"\nnext: false\n'
        actual = overlay_document(template, remote)
        self.assertEqual(yaml_json(actual), yaml_json(remote))
        self.assertIn('# 下一组\nnext: false', actual)


class NacosRenderDirectoryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix='nacos-render-')
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        self.source, self.output, self.remote = root / 'templates', root / 'out', root / 'remote'
        for directory in (self.source, self.remote):
            directory.mkdir()
        (self.source / 'a.yaml').write_text('# 说明\nkey: ${GITHUB_A}\n')
        (self.source / 'b.yaml').write_text('# 说明\nflag: false\n# 旧开关\nold: 1\n')
        (self.remote / 'b.yaml').write_text('flag: true\nold: 1\nretired: true\n')

    def test_only_selected_dataids_keep_remote_values(self):
        names = render_directory(self.source, self.output, {'GITHUB_A': 'x'}, remote=self.remote,
                                 keep_remote=['b.yaml'], drops=['b.yaml:retired'])
        self.assertEqual(names, ['a.yaml', 'b.yaml'])
        self.assertEqual(yaml_json((self.output / 'a.yaml').read_text()), {'key': 'x'})
        self.assertEqual(yaml_json((self.output / 'b.yaml').read_text()), {'flag': True, 'old': 1})
        self.assertIn('# 旧开关', (self.output / 'b.yaml').read_text())

    def test_stale_output_files_are_rejected_before_any_write(self):
        self.output.mkdir()
        (self.output / 'retired.yaml').write_text('old: true\n')
        with self.assertRaisesRegex(ValueError, 'retired.yaml'):
            render_directory(self.source, self.output, {'GITHUB_A': 'x'})
        self.assertEqual(sorted(file.name for file in self.output.iterdir()), ['retired.yaml'])

    def test_failed_overlay_writes_nothing(self):
        with self.assertRaisesRegex(ValueError, 'b.yaml.*retired'):
            render_directory(self.source, self.output, {'GITHUB_A': 'x'}, remote=self.remote, keep_remote=['b.yaml'])
        self.assertFalse(self.output.exists() and any(self.output.iterdir()))

    def test_keep_remote_and_drop_arguments_are_validated(self):
        for keep, drops, message in ((['c.yaml'], [], '不是当前模板'), (['a.yaml'], [], '缺少'),
                                     (['b.yaml'], ['a.yaml:key'], '--drop')):
            with self.assertRaisesRegex(ValueError, message):
                render_directory(self.source, self.output, {'GITHUB_A': 'x'}, remote=self.remote,
                                 keep_remote=keep, drops=drops)
        with self.assertRaisesRegex(ValueError, '--remote'):
            render_directory(self.source, self.output, {'GITHUB_A': 'x'}, keep_remote=['b.yaml'])


if __name__ == '__main__':
    unittest.main()
