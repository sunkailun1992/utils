"""注释随 Nacos 模板一起渲染；私有值必须保持为完整的 YAML 字符串。"""
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from render_nacos import render, render_document, yaml_json


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

    def test_duplicate_keys_fail_instead_of_silently_overwriting_a_value(self):
        with self.assertRaisesRegex(ValueError, '重复键'):
            render_document('service:\n  enabled: true\n  enabled: false\n', {})

    def test_multiline_and_unicode_scalar_positions(self):
        source = '键: "前缀 ${GITHUB_NAME} 后缀"\ntext: |\n  before ${GITHUB_NAME}\n  after\n# 下一组\nnext: true\n'
        actual = render_document(source, {'GITHUB_NAME': '测试'})
        self.assertIn('# 下一组\nnext: true', actual)
        self.assertEqual(yaml_json(actual), render(yaml_json(source), {'GITHUB_NAME': '测试'}))


if __name__ == '__main__':
    unittest.main()
