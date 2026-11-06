"""验证原生回放输入/断言器；本单测不冒充 Collector 已运行。"""
import copy
import json
import os
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import patch

import verify_collector_native as native


class NativeFixtureContractTest(unittest.TestCase):
    def test_synthetic_inputs_allow_non_owner_read_under_private_umask_without_widening_siblings(self):
        with tempfile.TemporaryDirectory() as temporary:
            parent = Path(temporary)
            private = parent / '.private'
            private.mkdir(mode=0o700)
            secret = private / 'real-secret'
            secret.write_text('must stay private')
            secret.chmod(0o600)
            root = parent / 'synthetic'
            root.mkdir(mode=0o700)
            previous_umask = os.umask(0o077)
            try:
                native.prepare(root)
                # 只让 runner 构造 overlay/参数；本测试完全不调用 Docker。
                with patch.object(native.subprocess, 'Popen', side_effect=RuntimeError('no Docker execution')):
                    with self.assertRaisesRegex(RuntimeError, 'no Docker execution'):
                        native.native_case(root, 'permission', 'collector-agent.yaml', {}, native.assert_logs)
            finally:
                os.umask(previous_umask)
            for directory in (root, root / 'logs'):
                self.assertEqual(stat.S_IMODE(directory.stat().st_mode), 0o755)
            for file in (root / 'logs/runtime.jsonl', root / 'grouping.jsonl', root / 'traces.jsonl',
                         root / 'otlp_token', root / 'permission.json'):
                self.assertEqual(stat.S_IMODE(file.stat().st_mode), 0o644)
            self.assertEqual(stat.S_IMODE((root / 'output-permission').stat().st_mode), 0o777)
            self.assertEqual(stat.S_IMODE(parent.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(private.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(secret.stat().st_mode), 0o600)
            self.assertEqual(secret.read_text(), 'must stay private')

    def test_existing_nonempty_directory_is_rejected_without_permission_changes(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            marker = root / 'private-data'
            marker.write_text('must not overwrite')
            marker.chmod(0o600)
            with self.assertRaises(ValueError):
                native.prepare(root)
            self.assertEqual(stat.S_IMODE(root.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(marker.stat().st_mode), 0o600)
            self.assertEqual(marker.read_text(), 'must not overwrite')
            self.assertEqual(list(root.iterdir()), [marker])

    def test_input_contains_two_services_in_one_resource_and_untraced_startup(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = native.prepare(temporary)
            payload = json.loads((root / 'grouping.jsonl').read_text())
            self.assertEqual(len(payload['resourceLogs']), 1)
            records = payload['resourceLogs'][0]['scopeLogs'][0]['logRecords']
            self.assertEqual(len(records), 3)
            fields = [native.decoded(record['attributes']) for record in records]
            self.assertEqual(len({json.dumps(item['service']) for item in fields}), 2)
            self.assertNotIn('trace_id', fields[2])

    def test_log_assertion_rejects_cross_service_secret_and_missing_records(self):
        resources = []
        for stage, (name, version, traced) in native.EXPECTED.items():
            fields = {'stage': stage, 'duration_ms': 7, 'outcome': 'success'}
            if traced:
                fields.update(trace_id=native.TRACE, span_id=native.SPAN)
            resources.append({'resource': {'attributes': native.attributes({
                'service.name': name, 'service.version': version,
                'deployment.environment.name': 'github-dev'})},
                'scopeLogs': [{'logRecords': [{'attributes': native.attributes(fields)}]}]})
        payload = {'resourceLogs': resources}
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / 'output.jsonl'
            output.write_text(json.dumps(payload) + '\n')
            native.assert_logs(output)
            mutations = []
            crossed = copy.deepcopy(payload)
            crossed['resourceLogs'][0]['resource']['attributes'][0]['value'] = native.value('fixture-python')
            mutations.append(crossed)
            secret = copy.deepcopy(payload)
            secret['resourceLogs'][0]['scopeLogs'][0]['logRecords'][0]['body'] = native.value(native.SECRET)
            mutations.append(secret)
            missing = copy.deepcopy(payload)
            missing['resourceLogs'].pop()
            mutations.append(missing)
            for mutated in mutations:
                output.write_text(json.dumps(mutated) + '\n')
                with self.assertRaises(AssertionError):
                    native.assert_logs(output)
