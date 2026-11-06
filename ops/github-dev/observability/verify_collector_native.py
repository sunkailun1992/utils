"""在固定官方 Collector 中回放合成数据；无网络、无 ES/NAS 或真实日志。"""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid

ROOT = Path(__file__).resolve().parent
IMAGE = 'otel/opentelemetry-collector-contrib:0.164.0'
TRACE = '0123456789abcdef0123456789abcdef'
SPAN = '0123456789abcdef'
SECRET = 'SYNTHETIC_SECRET_MUST_DISAPPEAR'
EXPECTED = {
    'java-call': ('fixture-java', '1.2.3', True),
    'python-call': ('fixture-python', '0.2.4', True),
    'java-startup': ('fixture-java', '1.2.3', False),
}

# 与 rag_engine.observability 的 ObservationBoundary/operation 一致；名称和结果只取生产有限集合。
PYTHON_CASES = [
    ('POST /v1/retrievals', {'http.route': '/v1/retrievals', 'http.request.method': 'POST',
                            'http.response.status_code': 200, 'rag.result': result})
    for result in ('success', 'empty', 'degraded')
] + [
    ('rag.' + stage, {'rag.stage': stage, 'rag.result': result})
    for stage in ('retrieval', 'graph_build')
    for result in ('success', 'empty', 'degraded', 'cancelled', 'timeout', 'failed')
]
TRACE_EXPECTED = {
    ('fixture-java', 'rag.retrieve', ''): {'business.outcome': 'matched'},
    ('fixture-java', 'mcp.protocol', ''): {'business.outcome': 'protocol_error'},
    **{('fixture-python', name, fields['rag.result']): fields for name, fields in PYTHON_CASES},
}


def value(item):
    if isinstance(item, dict):
        return {'mapValue': {'values': attributes(item)}}
    if isinstance(item, int):
        return {'intValue': str(item)}
    return {'stringValue': item}


def attributes(items):
    return [{'key': key, 'value': value(item)} for key, item in items.items()]


def decoded(items):
    return {entry['key']: next(iter(entry['value'].values())) for entry in items}


def input_logs():
    events = []
    for stage, (service, version, traced) in EXPECTED.items():
        event = {'@timestamp': '2026-11-06T00:00:00.000000000Z',
                 'service': {'name': service, 'version': version, 'environment': 'github-dev', 'private': SECRET},
                 'stage': stage, 'event': 'execution', 'log': {'level': 'INFO'},
                 'elapsed_ms': 7, 'result': 'success', 'message': SECRET, 'private': SECRET}
        if traced:
            event.update(trace_id=TRACE, span_id=SPAN)
        events.append(event)
    return events


def prepare(directory):
    directory = Path(directory)
    if directory.is_symlink() or (directory.exists() and any(directory.iterdir())):
        raise ValueError('Synthetic fixture directory must be empty and must not be a symlink')
    directory.mkdir(parents=True, exist_ok=True)
    # 仅放宽本次合成挂载根，不能遍历修改宿主私有目录；bind mount 内非 root UID 需要 x。
    directory.chmod(0o755)
    logs = directory / 'logs'
    logs.mkdir(parents=True)
    logs.chmod(0o755)
    events = input_logs()
    (logs / 'runtime.jsonl').write_text(''.join(json.dumps(event) + '\n' for event in events))
    records = [{'timeUnixNano': '1793923200000000000', 'body': value(SECRET),
                'attributes': attributes(event)} for event in events]
    # 三条日志明确在同一个原始 ResourceLogs 中，不能靠 receiver 预先分服务规避串号。
    grouping = {'resourceLogs': [{'resource': {}, 'scopeLogs': [{'logRecords': records}]}]}
    (directory / 'grouping.jsonl').write_text(json.dumps(grouping) + '\n')
    spans = []
    for name, outcome in (('rag.retrieve', 'matched'), ('mcp.protocol', 'protocol_error')):
        spans.append({'traceId': TRACE, 'spanId': SPAN, 'name': name,
                      'startTimeUnixNano': '1793923200000000000', 'endTimeUnixNano': '1793923200007000000',
                      'attributes': attributes({'business.outcome': outcome, 'private': SECRET}),
                      'status': {'code': 2, 'message': SECRET},
                      'events': [{'name': SECRET, 'timeUnixNano': '1793923200000000000',
                                  'attributes': attributes({'exception.message': SECRET})}]})
    traces = {'resourceSpans': [{'resource': {'attributes': attributes({
        'service.name': 'fixture-java', 'service.version': '1.2.3',
        'deployment.environment.name': 'github-dev', 'private': SECRET})},
        'scopeSpans': [{'spans': spans}]}]}
    python_spans = []
    for name, fields in PYTHON_CASES:
        python_spans.append({'traceId': TRACE, 'spanId': SPAN, 'name': name,
                             'startTimeUnixNano': '1793923200000000000',
                             'endTimeUnixNano': '1793923200007000000',
                             'attributes': attributes({**fields, 'private': SECRET, 'url.full': SECRET}),
                             'status': {'code': 2 if fields['rag.result'] in {'failed', 'timeout'} else 0}})
    traces['resourceSpans'].append({'resource': {'attributes': attributes({
        'service.name': 'fixture-python', 'service.version': '0.2.4',
        'deployment.environment.name': 'github-dev', 'private': SECRET})},
        'scopeSpans': [{'spans': python_spans}]})
    (directory / 'traces.jsonl').write_text(json.dumps(traces) + '\n')
    (directory / 'otlp_token').write_text('synthetic-fixture-only')
    for path in (logs / 'runtime.jsonl', directory / 'grouping.jsonl',
                 directory / 'traces.jsonl', directory / 'otlp_token'):
        path.chmod(0o644)
    return directory


def read_batches(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def assert_logs(path):
    batches = read_batches(path)
    assert SECRET not in json.dumps(batches), 'secret survived privacy processor'
    seen = {}
    for batch in batches:
        for resource in batch.get('resourceLogs', []):
            metadata = decoded(resource.get('resource', {}).get('attributes', []))
            for scope in resource.get('scopeLogs', []):
                for record in scope.get('logRecords', []):
                    fields = decoded(record.get('attributes', []))
                    stage = fields['stage']
                    name, version, traced = EXPECTED[stage]
                    assert metadata == {'service.name': name, 'service.version': version,
                                        'deployment.environment.name': 'github-dev'}, (stage, metadata)
                    assert not (set(metadata) & set(fields)), 'grouping fields stayed on record'
                    assert record.get('body', {}).get('stringValue', '') == '', 'raw body survived'
                    assert set(fields) <= {'event', 'duration_ms', 'error_code', 'outcome', 'stage', 'trace_id', 'span_id'}
                    assert str(fields['duration_ms']) == '7' and fields['outcome'] == 'success'
                    assert fields.get('trace_id') == (TRACE if traced else None)
                    assert fields.get('span_id') == (SPAN if traced else None)
                    seen[stage] = seen.get(stage, 0) + 1
    assert seen == {stage: 1 for stage in EXPECTED}, seen


def assert_traces(path):
    batches = read_batches(path)
    assert SECRET not in json.dumps(batches), 'secret survived span cleanup'
    seen = {}
    for batch in batches:
        for resource in batch.get('resourceSpans', []):
            metadata = decoded(resource['resource']['attributes'])
            service = metadata['service.name']
            assert service in {'fixture-java', 'fixture-python'}
            assert metadata == {'service.name': service,
                                'service.version': '1.2.3' if service == 'fixture-java' else '0.2.4',
                                'deployment.environment.name': 'github-dev'}
            for scope in resource.get('scopeSpans', []):
                for span in scope.get('spans', []):
                    fields = decoded(span.get('attributes', []))
                    # OTLP JSON 的 intValue 是字符串；保持整数比较明确。
                    if 'http.response.status_code' in fields:
                        fields['http.response.status_code'] = int(fields['http.response.status_code'])
                    key = (service, span['name'], fields.get('rag.result', ''))
                    assert key not in seen, 'duplicate fixture span'
                    seen[key] = fields
                    assert span.get('status', {}).get('message', '') == ''
                    assert all(event['name'] == 'event' and not event.get('attributes')
                               for event in span.get('events', []))
    assert seen == TRACE_EXPECTED, seen


def native_case(directory, case, base, overlay, assertion):
    output = directory / f'output-{case}'
    output.mkdir(mode=0o777)
    output.chmod(0o777)  # 仅临时合成输出，允许固定镜像的非 root UID 写入。
    overlay.setdefault('exporters', {})['file/assertion'] = {
        'path': '/output/events.jsonl', 'format': 'json', 'flush_interval': '100ms'}
    overlay_path = directory / f'{case}.json'
    overlay_path.write_text(json.dumps(overlay))
    overlay_path.chmod(0o644)
    name = 'fleet-observability-fixture-' + uuid.uuid4().hex[:12]
    common = ['docker', 'run', '--rm', '--name', name, '--network', 'none', '--read-only',
              '--cap-drop', 'ALL', '-e', 'FLEET_LOG_DIR=/fixture/logs',
              '-e', 'OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:9', '-e', 'FLEET_OTLP_TOKEN=synthetic',
              '-e', 'GITHUB_ES_URL=http://127.0.0.1:9', '-e', 'GITHUB_ES_API_KEY=synthetic',
              '-v', f'{ROOT}:/config:ro', '-v', f'{directory}:/fixture:ro',
              '-v', f'{directory / "otlp_token"}:/run/secrets/otlp_token:ro',
              '-v', f'{output}:/output:rw']
    command = common + [IMAGE, '--config=/config/' + base, '--config=/fixture/' + overlay_path.name]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    deadline = time.monotonic() + 30
    try:
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise AssertionError('Collector exited: ' + process.communicate()[0])
            try:
                assertion(output / 'events.jsonl')
                print('PASS native Collector:', case)
                return
            except (FileNotFoundError, json.JSONDecodeError, AssertionError):
                time.sleep(0.1)
        raise AssertionError(f'Collector fixture {case} produced no valid output')
    finally:
        subprocess.run(['docker', 'stop', '--time', '3', name], capture_output=True, timeout=10)
        try:
            logs, _ = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            logs, _ = process.communicate()
        if process.returncode not in (0, None):
            print(logs)


def run_native(directory):
    native_case(directory, 'filelog', 'collector-agent.yaml', {
        'receivers': {'filelog/runtime': {'start_at': 'beginning'}},
        'service': {'pipelines': {'logs': {'exporters': ['file/assertion']}}}}, assert_logs)
    native_case(directory, 'same-batch', 'collector-agent.yaml', {
        'receivers': {'otlp_json_file/fixture': {'include': ['/fixture/grouping.jsonl'], 'start_at': 'beginning'}},
        'service': {'pipelines': {'logs': {'receivers': ['otlp_json_file/fixture'],
                                         'exporters': ['file/assertion']}}}}, assert_logs)
    native_case(directory, 'business-spans', 'collector.yaml', {
        'receivers': {'otlp_json_file/fixture': {'include': ['/fixture/traces.jsonl'], 'start_at': 'beginning'}},
        'exporters': {'file/unused': {'path': '/output/unused-logs.jsonl', 'format': 'json'}},
        'service': {'pipelines': {'traces': {'receivers': ['otlp_json_file/fixture'],
                                           'exporters': ['file/assertion']},
                                  'logs': {'receivers': ['otlp_json_file/fixture'],
                                           'exporters': ['file/unused']}}}}, assert_traces)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only', type=Path, help='只生成合成 fixture；不代表原生验证通过')
    args = parser.parse_args()
    if args.prepare_only:
        prepare(args.prepare_only)
        print('Prepared synthetic fixtures only; native Collector was not run')
    else:
        with tempfile.TemporaryDirectory(prefix='fleet-collector-fixture-') as temporary:
            run_native(prepare(temporary))
