"""完整baseline的内存mutation：观测迁移仅允许已核键、值和类型。"""
import contextlib
import copy
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
import verify_configuration as verifier


class StructuredLoggingBaselineTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.config = self.root / 'nacos/DEFAULT_GROUP'
        self.config.mkdir(parents=True)
        templates = {}
        for name in ('actuator.yaml', 'logging.yml'):
            text = (verifier.CONFIG / name).read_text()
            (self.config / name).write_text(text)
            templates[name] = verifier.yaml_json(text)
        (self.config.parent / 'retired-dataids.json').write_text(json.dumps({'retired': []}))
        before = copy.deepcopy(templates)
        before['actuator.yaml']['management']['endpoints']['web']['exposure']['include'] = 'health,info'
        before['actuator.yaml']['management'].pop('metrics')
        logging = before['logging.yml']['logging']
        logging.pop('structured')
        for name in ('org.springframework.ai.chat.model.MessageAggregator',
                     'org.springframework.ai.model.observation.ErrorLoggingObservationHandler'):
            logging['level'].pop(name)
        profile = json.dumps({'spring': {'config': {
            'import': ['nacos:actuator.yaml', 'nacos:logging.yml']}}})
        profiles = {}
        for environment in ('dev', 'test', 'prod'):
            relative = f'fixture/src/main/resources/application-{environment}.yml'
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(profile)
            profiles[relative] = profile
        self.baseline = self.root / 'baseline.json'
        self.baseline.write_text(json.dumps({
            'profiles': profiles,
            'templates': {name: json.dumps(value) for name, value in before.items()}}))

    def verify(self, mutation=None):
        original = verifier.effective_configuration

        def injected(imports, templates):
            effective = original(imports, templates)
            # 仅修改新版对象；旧快照没有structured节点。
            if mutation is not None and 'structured' in effective.get('logging', {}):
                path, value = mutation
                parent = effective['logging']['structured']
                for key in path[:-1]:
                    parent = parent[key]
                parent[path[-1]] = value
            return effective

        with (patch.object(verifier, 'ROOT', self.root),
              patch.object(verifier, 'CONFIG', self.config),
              patch.object(verifier, 'SERVICES', ('fixture',)),
              patch.object(verifier, 'effective_configuration', injected),
              contextlib.redirect_stdout(io.StringIO())):
            verifier.check_consolidation_baseline(self.baseline)

    def test_accepts_only_approved_complete_structured_configuration(self):
        self.verify()

    def test_rejects_unapproved_structured_keys_and_types(self):
        mutations = (
            (('extra',), 'unapproved'),
            (('format', 'file'), 'logstash'),
            (('json', 'add'), {'sensitive': 'unapproved'}),
            (('format', 'console'), ['ecs']),
            (('json', 'include'), ['@timestamp']),
            (('json', 'include'), verifier.APPROVED_STRUCTURED_INCLUDE + ',message'),
            (('json', 'include'), verifier.APPROVED_STRUCTURED_INCLUDE + ',ai_input'),
            (('json', 'context', 'include'), True),
            (('json', 'context', 'include'), 'false'),
            (('json', 'context', 'include'), 0),
            (('json',), []),
            (('format',), 'ecs'),
            (('ecs', 'service', 'version'), 'unapproved'),
            (('ecs', 'service', 'environment'), ['github-dev']),
            (('ecs', 'service', 'extra'), 'unapproved'),
            (('ecs',), []),
        )
        for mutation in mutations:
            with self.subTest(path=mutation[0]), self.assertRaisesRegex(AssertionError, "structured logging"):
                self.verify(mutation)

    def test_histogram_guard_accepts_only_exact_approved_leaf_and_type(self):
        path = self.config / 'actuator.yaml'
        original = verifier.yaml_json(path.read_text())
        for distribution in (
                {'percentiles-histogram': {'http.server.requests': False}},
                {'percentiles-histogram': {'http.server.requests': 'true'}},
                {'percentiles-histogram': {'http.server.requests': 1}},
                {'percentiles-histogram': {'http.server.requests': True, 'all': True}},
                {'percentiles-histogram': {'http.server.requests': True}, 'percentiles': {'all': 0.95}}):
            candidate = copy.deepcopy(original)
            candidate['management']['metrics']['distribution'] = distribution
            path.write_text(json.dumps(candidate))
            with self.subTest(distribution=distribution), self.assertRaises(AssertionError):
                self.verify()
