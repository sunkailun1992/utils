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
    def test_persistent_badger_has_headroom_beyond_the_go_soft_limit(self):
        jaeger = yaml_json((ROOT / 'compose.yaml').read_text())['services']['jaeger']
        self.assertEqual(jaeger['mem_limit'], '1536m',
                         'the previous 768 MiB container limit caused persistent Badger OOM restarts')
        self.assertEqual((jaeger.get('environment') or {}).get('GOMEMLIMIT'), '768MiB',
                         'Go needs a soft budget below the whole-container hard limit')
        self.assertIn('jaeger-data:/tmp', jaeger['volumes'])
        storage = yaml_json((ROOT / 'jaeger.yaml').read_text())['extensions']['jaeger_storage']
        badger = storage['backends']['primary']['badger']
        self.assertFalse(badger['ephemeral'])
        self.assertEqual(badger['directories'],
                         {'keys': '/tmp/jaeger/keys', 'values': '/tmp/jaeger/values'})
        self.assertEqual(badger['ttl']['spans'], '24h')

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
        self.assertTrue(any('__name__="otelcol.elasticsearch.docs.processed"' in expr and
                            'outcome!="success"' in expr and 'rate(' in expr
                            for expr in expressions),
                        'bulk HTTP success must not hide individual document rejections')


class DashboardSemanticsTest(unittest.TestCase):
    def setUp(self):
        self.dashboard = json.loads((ROOT / 'grafana/dashboards/fleet.json').read_text())
        self.panels = {panel['id']: panel for panel in self.dashboard['panels']}

    def test_expected_targets_keep_failure_and_absence_visibly_distinct(self):
        panel = self.panels[1]
        expected = {'gateway', 'user', 'message', 'ai', 'ai-agent', 'ai-memory',
                    'rag', 'report', 'mcp', 'rag-engine'}
        self.assertEqual({target['legendFormat'] for target in panel['targets']}, expected)
        self.assertEqual(len(panel['targets']), len(expected))
        for target in panel['targets']:
            service = target['legendFormat']
            self.assertEqual(
                target['expr'],
                f'min(up{{job="fleet-{service}",environment="github-dev"}}) or vector(-1)',
            )
            self.assertTrue(target['instant'], 'state must use the current scrape, not a time average')
            self.assertFalse(target['range'])
        self.assertEqual(panel['options']['reduceOptions']['calcs'], ['lastNotNull'])
        mappings = panel['fieldConfig']['defaults']['mappings'][0]['options']
        self.assertEqual({value: mapping['color'] for value, mapping in mappings.items()},
                         {'-1': 'red', '0': 'red', '1': 'green'})
        self.assertNotEqual(mappings['-1']['text'], mappings['0']['text'])

    def test_missing_business_samples_and_latency_are_not_invented_as_zero(self):
        for panel_id in [2, 3, 4, 6, 7, 8, 9]:
            panel = self.panels[panel_id]
            with self.subTest(panel=panel['title']):
                self.assertIn('description', panel)
                for target in panel['targets']:
                    self.assertNotIn('or vector(0)', target['expr'])
                    self.assertNotIn('0 *', target['expr'])
        for panel_id in [3, 9]:
            self.assertIn('不能', self.panels[panel_id]['description'])
            self.assertIn('0 秒', self.panels[panel_id]['description'])

    def test_native_promql_fixtures_cover_the_actual_dashboard_queries(self):
        fixture = json.loads((ROOT / 'dashboard-promql-tests.json').read_text())
        self.assertTrue(fixture['fuzzy_compare'], 'rate extrapolation can differ by the final float bit')
        cases = {case['name']: case for case in fixture['tests']}
        actual = {target['expr'] for panel in self.dashboard['panels']
                  for target in panel['targets']}
        for case in cases.values():
            for query in case['promql_expr_test']:
                self.assertIn(query['expr'], actual, 'native fixture must exercise deployed queries')
        expected_states = {'healthy': 1, 'scrape_failure': 0,
                           'stale_target': -1, 'missing_targets': -1,
                           'one_failed_duplicate_is_not_hidden': 0}
        for name, value in expected_states.items():
            self.assertEqual(cases[name]['promql_expr_test'][0]['exp_samples'][0]['value'], value)
        missing = cases['missing_targets']['promql_expr_test']
        self.assertEqual({query['expr'] for query in missing},
                         {target['expr'] for target in self.panels[1]['targets']})
        for query in cases['missing_business_samples_stay_unknown']['promql_expr_test']:
            self.assertEqual(query['exp_samples'], [])

    def test_collector_failure_fallback_needs_current_health_and_real_output_baseline(self):
        panel = self.panels[5]
        for target in panel['targets']:
            self.assertIn('job="collector"', target['expr'])
            self.assertIn('and on() (min(up{job="collector"}) == 1)', target['expr'])
            self.assertNotIn('or vector(0)', target['expr'])
        self.assertIn('otelcol_exporter_sent_spans', panel['targets'][0]['expr'])
        self.assertIn('and on(exporter) sum by(exporter)(otelcol_exporter_sent_spans{job="collector"})',
                      panel['targets'][0]['expr'])
        self.assertIn('outcome="success"', panel['targets'][1]['expr'])
        self.assertIn('and on() sum({__name__="otelcol.elasticsearch.docs.processed",'
                      'job="collector",outcome="success"})', panel['targets'][1]['expr'])
        self.assertNotIn('otelcol_elasticsearch_docs_processed', panel['targets'][1]['expr'])
        fixture = json.loads((ROOT / 'dashboard-promql-tests.json').read_text())
        cases = {case['name']: case for case in fixture['tests']}
        for query in cases['collector_no_failures_with_success_baseline']['promql_expr_test']:
            self.assertEqual(query['exp_samples'][0]['value'], 0)
        for query in cases['collector_failures_are_not_hidden']['promql_expr_test']:
            self.assertEqual(query['exp_samples'][0]['value'], 1)
        for query in cases['collector_stale_success_does_not_hide_real_failures']['promql_expr_test']:
            self.assertEqual(query['exp_samples'][0]['value'], 1)
        for name in ['collector_down_does_not_reuse_old_success',
                     'collector_missing_does_not_reuse_old_success',
                     'collector_missing_output_baseline_is_unknown',
                     'collector_insufficient_output_samples_is_unknown',
                     'collector_stale_success_baseline_is_unknown']:
            for query in cases[name]['promql_expr_test']:
                self.assertEqual(query['exp_samples'], [], name)
        rule = yaml_json((ROOT / 'alerts.yaml').read_text())['groups'][0]['rules']
        exporter_rule = next(item for item in rule if item['alert'] == 'CollectorDroppedSpans')
        self.assertIn('__name__="otelcol.elasticsearch.docs.processed"', exporter_rule['expr'])
        self.assertIn('outcome!="success"', exporter_rule['expr'])


class StructuredIoContractTest(unittest.TestCase):
    def test_both_boundaries_use_identical_typed_log_contract(self):
        from privacy_contract import LOG_FIELDS, log_statements, span_statements
        for name, include_resource in [('collector-agent.yaml', True), ('collector.yaml', False)]:
            data = yaml_json((ROOT / name).read_text())
            statements = next(item['statements'] for item in data['processors']['transform/privacy']['log_statements']
                              if item['context'] == 'log')
            for statement in log_statements(include_resource):
                self.assertIn(statement, statements)
        for forbidden in ('message', 'error.message', 'error.stack_trace', 'password', 'authorization', 'ai_input', 'ai_output'):
            self.assertNotIn(forbidden, LOG_FIELDS)
        data = yaml_json((ROOT / 'collector.yaml').read_text())
        statements = next(item['statements'] for item in data['processors']['transform/privacy']['trace_statements']
                          if item['context'] == 'span')
        for statement in span_statements():
            self.assertIn(statement, statements)

    def test_additive_schema_never_changes_existing_index_or_audit_fields(self):
        from privacy_contract import LOG_FIELDS
        addition = json.loads((ROOT / 'es/runtime-log-additive-mapping.json').read_text())
        self.assertEqual(set(addition), {'properties'})
        fields = addition['properties']
        self.assertLessEqual(set(fields), set(LOG_FIELDS))
        self.assertFalse(set(fields) & {'@timestamp', 'Resource', 'TraceId', 'SpanId', 'trace_id', 'span_id'})
        self.assertEqual(fields['request_body'], {'type': 'text', 'index': False})
        self.assertEqual(fields['response_body_complete'], {'type': 'boolean'})
        self.assertEqual(fields['ai_input_tokens'], {'type': 'long'})

    def test_grafana_displays_generated_summary_and_keeps_trace_join(self):
        sources = yaml_json((ROOT / 'grafana/provisioning/datasources/fleet.yaml').read_text())['datasources']
        source = next(item for item in sources if item['type'] == 'elasticsearch')
        self.assertEqual(source['jsonData']['logMessageField'], 'summary')
        self.assertTrue(any(item['field'] == 'trace_id' for item in source['jsonData']['dataLinks']))
