"""验证原生回放输入/断言器；本单测不冒充 Collector 已运行。"""
import copy
import io
import json
import os
from pathlib import Path
import stat
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import verify_collector_native as native


class NativeFixtureContractTest(unittest.TestCase):
    def test_boundary_assertion_rejects_false_native_correlation_level_time_and_flags(self):
        expected = {'fixture': ('0123456789abcdef0123456789abcdef', '0123456789abcdef', 17, 'ERROR', True, 0)}
        record = {'traceId': '0123456789abcdef0123456789abcdef', 'spanId': '0123456789abcdef',
                  'severityNumber': 17, 'severityText': 'ERROR', 'timeUnixNano': '1794182400000000000',
                  'attributes': native.attributes({'stage': 'fixture', 'trace_id': '0123456789abcdef0123456789abcdef',
                                                   'span_id': '0123456789abcdef'})}
        payload = [{'resourceLogs': [{'resource': {'attributes': native.attributes({
            'service.name': 'fixture-java', 'service.version': '1.2.3',
            'deployment.environment.name': 'github-dev'})}, 'scopeLogs': [{'logRecords': [record]}]}]}]
        native.assert_boundaries(payload, expected)
        mutations = [('traceId', ''), ('spanId', '1111111111111111'), ('severityNumber', 9),
                     ('severityText', 'INFO'), ('timeUnixNano', '1'), ('flags', 1)]
        for key, value in mutations:
            changed = copy.deepcopy(payload)
            changed[0]['resourceLogs'][0]['scopeLogs'][0]['logRecords'][0][key] = value
            with self.subTest(key=key), self.assertRaises(AssertionError):
                native.assert_boundaries(changed, expected)

    def test_filelog_persists_offsets_without_replaying_initial_history(self):
        config = (native.ROOT / 'collector-agent.yaml').read_text()
        self.assertIn('storage: file_storage/offsets', config)
        self.assertIn('directory: ${env:FLEET_LOG_STATE_DIR}', config)
        self.assertIn('extensions: [file_storage/offsets]', config)
        self.assertIn('start_at: end', config)

    def test_resumed_offset_assertion_rejects_duplicates_and_missing_records(self):
        def payload(stages):
            return {'resourceLogs': [{'resource': {'attributes': native.attributes({
                'service.name': 'fixture-java', 'service.version': '1.2.3',
                'deployment.environment.name': 'github-dev'})}, 'scopeLogs': [{'logRecords': [
                    {'attributes': native.attributes({'event': 'http.server.exchange', 'stage': stage})}
                    for stage in stages]}]}]}
        expected = ['offline-first', 'offline-second']
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / 'records.jsonl'
            path.write_text(json.dumps(payload(expected)) + '\n')
            native.assert_offset_records(path, expected)
            for stages in (expected[:1], expected + ['offline-first'], expected + ['online-before-stop'], []):
                path.write_text(json.dumps(payload(stages)) + '\n')
                with self.subTest(stages=stages), self.assertRaises(AssertionError):
                    native.assert_offset_records(path, expected)

    def test_nested_attributes_use_otlp_anyvalue_kvlist_encoding(self):
        # OTLP AnyValue 的 map 字段名是 kvlistValue；未知 mapValue 会被 receiver 读成 Empty。
        encoded = native.value({'service': {'name': 'fixture-java'}})
        self.assertEqual(encoded, {'kvlistValue': {'values': [{
            'key': 'service', 'value': {'kvlistValue': {'values': [{
                'key': 'name', 'value': {'stringValue': 'fixture-java'}}]}}}]}})

    def test_timeout_keeps_collector_diagnostics_after_clean_shutdown(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = native.prepare(temporary)
            process = SimpleNamespace(poll=lambda: None, returncode=0,
                                      communicate=lambda timeout=None: ('synthetic processor failure\n', None))
            output = io.StringIO()
            with patch.object(native.subprocess, 'Popen', return_value=process), \
                    patch.object(native.subprocess, 'run'), \
                    patch.object(native.time, 'monotonic', side_effect=[0, 31]), \
                    patch('sys.stdout', output):
                with self.assertRaisesRegex(AssertionError, 'produced no valid output'):
                    native.native_case(root, 'timeout', 'collector-agent.yaml', {}, native.assert_logs)
            self.assertIn('synthetic processor failure', output.getvalue())

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
            self.assertEqual(len(records), len(native.EXPECTED))
            fields = [native.decoded(record['attributes']) for record in records]
            self.assertEqual(len({json.dumps(item['service']) for item in fields}), 2)
            self.assertNotIn('trace_id', fields[2])

    def test_log_assertion_rejects_cross_service_secret_and_missing_records(self):
        resources = []
        for stage, (name, version, traced) in native.EXPECTED.items():
            fields = {key: item for key, item in next(event for event in native.input_logs() if event['stage'] == stage).items()
                      if key in native.LOG_FIELDS}
            fields['duration_ms'] = 7
            fields['outcome'] = 'success'
            for key in ['http_status', 'ai_output_tokens']:
                if key in fields:
                    fields[key] = str(fields[key])
            for key in ['ai_input_tokens'] + (['request_bytes', 'request_body_complete'] if stage == 'python-call' else []):
                fields.pop(key, None)
            if stage != 'route-selected':
                for key in native.ROUTING_LOG_ENUMS:
                    fields.pop(key, None)
            if stage == 'route-invalid':
                fields.pop('summary')
            if traced:
                fields.update(trace_id=native.TRACE, span_id=native.SPAN)
            resources.append({'resource': {'attributes': native.attributes({
                'service.name': name, 'service.version': version,
                'deployment.environment.name': 'github-dev'})},
                'scopeLogs': [{'logRecords': [{'attributes': native.attributes(fields),
                    'timeUnixNano': '1793923200000000000', 'severityNumber': 9, 'severityText': 'INFO',
                    **({'traceId': native.TRACE, 'spanId': native.SPAN} if traced else {})}]}]})
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
            for key, wrong in [('traceId', ''), ('spanId', '1111111111111111'),
                               ('severityNumber', 0), ('timeUnixNano', '1'), ('flags', 1)]:
                invalid = copy.deepcopy(payload)
                invalid['resourceLogs'][0]['scopeLogs'][0]['logRecords'][0][key] = wrong
                mutations.append(invalid)
            for mutated in mutations:
                output.write_text(json.dumps(mutated) + '\n')
                with self.assertRaises(AssertionError):
                    native.assert_logs(output)

    def test_central_allowlist_preserves_actual_engine_result_contract(self):
        collector = (native.ROOT / 'collector.yaml').read_text()
        self.assertIn('"rag.result"', collector)
        self.assertNotIn('"rag.outcome"', collector)

    def test_trace_assertion_rejects_missing_python_results_secrets_and_resource_crossing(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = native.prepare(temporary)
            payload = json.loads((root / 'traces.jsonl').read_text())
            for resource in payload['resourceSpans']:
                resource['resource']['attributes'] = [field for field in resource['resource']['attributes']
                                                       if field['key'] != 'private']
                for scope in resource['scopeSpans']:
                    for span in scope['spans']:
                        span['attributes'] = [field for field in span['attributes']
                                              if native.SECRET not in json.dumps(field)
                                              and (not field['key'].startswith('traffic.route.')
                                                   or span['name'] == 'canary-routing')]
                        span.get('status', {}).pop('message', None)
                        span['events'] = []
            output = root / 'sanitized.jsonl'
            output.write_text(json.dumps(payload) + '\n')
            native.assert_traces(output)
            for mutation in ('result', 'secret', 'resource'):
                candidate = copy.deepcopy(payload)
                python = candidate['resourceSpans'][1]
                span = python['scopeSpans'][0]['spans'][0]
                if mutation == 'result':
                    span['attributes'] = [field for field in span['attributes'] if field['key'] != 'rag.result']
                elif mutation == 'secret':
                    span['attributes'] += native.attributes({'url.full': native.SECRET})
                else:
                    python['resource']['attributes'] = candidate['resourceSpans'][0]['resource']['attributes']
                output.write_text(json.dumps(candidate) + '\n')
                with self.subTest(mutation=mutation), self.assertRaises(AssertionError):
                    native.assert_traces(output)
