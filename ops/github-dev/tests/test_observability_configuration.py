"""观测配置边界：旧链路不能复活，管理端点只增加受保护的指标。"""
import json
import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from render_nacos import yaml_json

ROOT = Path(__file__).resolve().parents[1]

class ObservabilityConfigurationTests(unittest.TestCase):
    def test_retired_monitoring_has_no_template_or_active_catalog_entry(self):
        active = {row['dataId'] for row in json.loads((ROOT / 'nacos/catalog.json').read_text())['entries']}
        retired = {row['dataId'] for row in json.loads((ROOT / 'nacos/retired-dataids.json').read_text())['retired']}
        for name in ('admin.yaml', 'zipkin.yaml'):
            self.assertNotIn(name, active)
            self.assertIn(name, retired)
            self.assertFalse((ROOT / 'nacos/DEFAULT_GROUP' / name).exists())

    def test_metrics_exposure_keeps_token_and_safe_logging(self):
        config = ROOT / 'nacos/DEFAULT_GROUP'
        actuator = yaml_json((config / 'actuator.yaml').read_text())
        self.assertEqual(actuator['management']['endpoints']['web']['exposure']['include'], 'health,info,prometheus')
        self.assertEqual(actuator['security']['actuator']['token'], '${GITHUB_ACTUATOR_TOKEN:}')
        complete_logging = yaml_json((config / 'logging.yml').read_text())['logging']
        for logger in ('org.springframework.ai.chat.model.MessageAggregator',
                       'org.springframework.ai.model.observation.ErrorLoggingObservationHandler'):
            self.assertEqual(complete_logging['level'][logger], 'OFF')
        logging = complete_logging['structured']
        self.assertEqual(logging['format']['console'], 'ecs')
        fields = logging['json']['include'].split(',')
        self.assertNotIn('message', fields)
        self.assertNotIn('log.stack_trace', fields)
        self.assertNotIn('authorization', fields)
        self.assertIn('trace_id', fields)
        self.assertIn('span_id', fields)
