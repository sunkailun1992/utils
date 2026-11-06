"""验证公开模板只传递私有认证引用，不连接 ES 或读取任何真实凭据。"""
from pathlib import Path
import json
import sys
import unittest
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / 'scripts'))
from render_nacos import yaml_json


class GrafanaReadAuthorizationTest(unittest.TestCase):
    def test_tmpfs_options_cannot_be_parsed_as_another_mount(self):
        services = yaml_json((ROOT / 'compose.yaml').read_text())['services']
        for name, service in services.items():
            for mount in service.get('tmpfs', []):
                with self.subTest(service=name, mount=mount):
                    target, _, options = mount.partition(':')
                    self.assertTrue(target.startswith('/'), 'tmpfs target must be an absolute path')
                    if name == 'grafana' and target == '/tmp':
                        self.assertIn('size=32m', options.split(','))
                        self.assertIn('mode=1777', options.split(','))

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


class JaegerCompatibilityTest(unittest.TestCase):
    def test_v3_mode_and_native_ui_preserve_internal_query_and_storage(self):
        services = yaml_json((ROOT / 'compose.yaml').read_text())['services']
        self.assertEqual(set(services), {'collector', 'jaeger', 'prometheus', 'grafana'})
        self.assertEqual(
            services['grafana']['environment'].get('GF_FEATURE_TOGGLES_ENABLE'),
            'jaegerEnableGrpcEndpoint',
        )
        jaeger = services['jaeger']
        self.assertEqual(jaeger['ports'], [
            '${OBS_BIND_IP:-127.0.0.1}:${JAEGER_UI_PORT:-16686}:16686',
        ])
        self.assertIn('jaeger-data:/tmp', jaeger['volumes'])
        self.assertEqual(jaeger['image'], '${JAEGER_IMAGE:-jaegertracing/jaeger:2.23.0}')
        self.assertNotIn('ports', services['prometheus'])
        datasources = yaml_json((ROOT / 'grafana/provisioning/datasources/fleet.yaml').read_text())['datasources']
        datasource = next(item for item in datasources if item['type'] == 'jaeger')
        self.assertEqual(datasource['url'], 'http://jaeger:16686')

    def test_native_search_link_resolves_a_configurable_browser_address(self):
        dashboard = json.loads((ROOT / 'grafana/dashboards/fleet.json').read_text())
        variables = dashboard.get('templating', {}).get('list', [])
        self.assertTrue(variables, 'native Jaeger search needs a browser-reachable URL')
        variable = next(item for item in variables if item['name'] == 'jaeger_ui_url')
        self.assertEqual(variable['type'], 'textbox')
        self.assertEqual(variable['query'], 'http://localhost:16686')
        self.assertEqual(variable['current']['value'], variable['query'])
        link = next(item for item in dashboard['links'] if item['type'] == 'link')
        self.assertTrue(link['targetBlank'])
        self.assertFalse(link['keepTime'])
        token = '${jaeger_ui_url:raw}'
        self.assertIn(token, link['url'])
        for address in [variable['query'], 'http://private-test.example:17686']:
            parsed = urlsplit(link['url'].replace(token, address))
            expected = urlsplit(address)
            self.assertEqual((parsed.scheme, parsed.hostname, parsed.port),
                             (expected.scheme, expected.hostname, expected.port))
            self.assertEqual(parsed.path, '/search')
            self.assertEqual(parsed.query, '')
        self.assertIn('\nJAEGER_UI_PORT=16686\n', (ROOT / '.env.example').read_text())


class CollectorRetryBudgetTest(unittest.TestCase):
    def test_retry_intervals_fit_the_bounded_elapsed_budget(self):
        def seconds(value):
            self.assertTrue(value.endswith('s'), 'retry budget uses explicit seconds')
            return float(value[:-1])

        for filename in ['collector-agent.yaml', 'collector.yaml']:
            exporters = yaml_json((ROOT / filename).read_text())['exporters']
            for name, exporter in exporters.items():
                retry = exporter.get('retry_on_failure')
                if not retry or not retry.get('enabled'):
                    continue
                with self.subTest(config=filename, exporter=name):
                    # Collector 0.164.0 的默认 initial/max interval 分别为 5s/30s。
                    initial = seconds(retry.get('initial_interval', '5s'))
                    maximum = seconds(retry.get('max_interval', '30s'))
                    elapsed = seconds(retry['max_elapsed_time'])
                    self.assertLessEqual(initial, maximum)
                    self.assertLessEqual(maximum, elapsed)
                    self.assertEqual(elapsed, 15)


class ElasticsearchMappingTest(unittest.TestCase):
    def test_static_runtime_index_uses_only_effective_raw_mapping(self):
        config = yaml_json((ROOT / 'collector.yaml').read_text())
        exporter = config['exporters']['elasticsearch/runtime']
        self.assertEqual(exporter['logs_index'], 'github_dev_runtime_log')
        # 0.164 忽略 mapping.mode；允许 otel/bodymap 等模式会改变已审计的输出合同。
        self.assertEqual(exporter['mapping'], {'allowed_modes': ['raw']})
        self.assertNotIn('pipeline', exporter)
        privacy = config['processors']['transform/privacy']['log_statements']
        log_statements = next(item['statements'] for item in privacy if item['context'] == 'log')
        self.assertIn('set(body, "")', log_statements)

    def test_existing_error_panel_includes_document_rejections(self):
        dashboard = json.loads((ROOT / 'grafana/dashboards/fleet.json').read_text())
        self.assertEqual(len(dashboard['panels']), 9)
        panel = next(item for item in dashboard['panels'] if item['id'] == 5)
        self.assertEqual(len({target['refId'] for target in panel['targets']}), len(panel['targets']))
        expressions = [target['expr'] for target in panel['targets']]
        self.assertTrue(any('otelcol_exporter_send_failed_spans' in expr for expr in expressions))
        self.assertTrue(any('otelcol_elasticsearch_docs_processed' in expr and
                            'outcome!="success"' in expr and 'rate(' in expr
                            for expr in expressions),
                        'bulk HTTP success must not hide individual document rejections')
