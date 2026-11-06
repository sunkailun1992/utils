"""验证公开模板只传递私有认证引用，不连接 ES 或读取任何真实凭据。"""
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / 'scripts'))
from render_nacos import yaml_json


class GrafanaReadAuthorizationTest(unittest.TestCase):
    def test_read_authorization_is_private_provisioning_and_separate_from_writer(self):
        compose = yaml_json((ROOT / 'compose.yaml').read_text())
        services = compose['services']
        grafana = services['grafana']['environment']
        collector = services['collector']['environment']
        self.assertEqual(grafana['GITHUB_ES_READ_AUTHORIZATION'], '${GITHUB_ES_READ_AUTHORIZATION:-}')
        self.assertNotIn('GITHUB_ES_API_KEY', grafana)
        self.assertEqual(collector['GITHUB_ES_API_KEY'], '${GITHUB_ES_API_KEY:-}')
        self.assertNotIn('GITHUB_ES_READ_AUTHORIZATION', collector)
        for name, service in services.items():
            if name != 'grafana':
                self.assertNotIn('GITHUB_ES_READ_AUTHORIZATION', service.get('environment', {}))
        datasources = yaml_json((ROOT / 'grafana/provisioning/datasources/fleet.yaml').read_text())['datasources']
        elasticsearch = next(item for item in datasources if item['type'] == 'elasticsearch')
        self.assertFalse(elasticsearch['editable'])
        self.assertEqual(elasticsearch['jsonData']['httpHeaderName1'], 'Authorization')
        self.assertEqual(elasticsearch['secureJsonData'], {'httpHeaderValue1': '$GITHUB_ES_READ_AUTHORIZATION'})
        self.assertNotIn('httpHeaderValue1', elasticsearch['jsonData'])
        self.assertIn('\nGITHUB_ES_READ_AUTHORIZATION=\n', (ROOT / '.env.example').read_text())
