"""在固定官方 Collector 中回放合成数据；无网络、无 ES/NAS 或真实日志。"""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
import os
from privacy_contract import LOG_FIELDS

ROOT = Path(__file__).resolve().parent
IMAGE = 'otel/opentelemetry-collector-contrib:0.164.0'
TRACE = '0123456789abcdef0123456789abcdef'
SPAN = '0123456789abcdef'
SECRET = 'SYNTHETIC_SECRET_MUST_DISAPPEAR'
COLLECTOR_BIN = None
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
MODEL_FIELDS = {'gen_ai.operation.name': 'chat', 'gen_ai.provider.name': 'gcp.gemini',
                'gen_ai.request.model': 'fixture-model-v1', 'gen_ai.usage.input_tokens': '17',
                'gen_ai.usage.output_tokens': '11', 'gen_ai.usage.reasoning.output_tokens': '3',
                'fleet.ai.usage.available': True, 'fleet.ai.usage.final': True,
                'fleet.ai.reasoning.present': False, 'fleet.ai.reasoning.kind': 'none',
                'rpc.system.name': 'apache_dubbo', 'rpc.service': 'com.kellen.rpc.user.UserRpcService',
                'rpc.method': 'findUser', 'gen_ai.response.finish_reasons': ['stop']}
TRACE_EXPECTED = {
    ('fixture-java', 'ai.model.call', ''): MODEL_FIELDS,
    ('fixture-java', 'rag.retrieve', ''): {'business.outcome': 'matched'},
    ('fixture-java', 'mcp.protocol', ''): {'business.outcome': 'protocol_error'},
    **{('fixture-python', name, fields['rag.result']): fields for name, fields in PYTHON_CASES},
}


def value(item):
    if isinstance(item, bool):
        return {'boolValue': item}
    if isinstance(item, list):
        return {'arrayValue': {'values': [value(v) for v in item]}}
    if isinstance(item, dict):
        return {'kvlistValue': {'values': attributes(item)}}
    if isinstance(item, int):
        return {'intValue': str(item)}
    return {'stringValue': item}


def attributes(items):
    return [{'key': key, 'value': value(item)} for key, item in items.items()]


def decoded(items):
    def item_value(item):
        if 'arrayValue' in item:
            return [item_value(value) for value in item['arrayValue'].get('values', [])]
        return next(iter(item.values()))
    return {entry['key']: item_value(entry['value']) for entry in items}


def input_logs():
    events = []
    for stage, (service, version, traced) in EXPECTED.items():
        event = {'@timestamp': '2026-11-06T00:00:00.000000000Z',
                 'service': {'name': service, 'version': version, 'environment': 'github-dev', 'private': SECRET},
                 'stage': stage, 'event': 'execution', 'log': {'level': 'INFO'},
                 'elapsed_ms': 7, 'result': 'success', 'message': SECRET, 'private': SECRET}
        if stage == 'java-call':
            event.update(event='http.server.exchange', summary='POST /api/rag/retrievals 200 7ms success',
                         http_method='POST', http_route='/api/rag/retrievals', http_status=200,
                         request_bytes=54, response_bytes=99, request_body_complete=True,
                         response_body_complete=True, request_body='{"topK":3}',
                         response_body='{"code":"200","success":true,"data":{"sourceCount":1}}')
        elif stage == 'python-call':
            event.update(event='ai.model.call', summary='AI patient_analysis finished: success',
                         ai_provider='gcp.gemini', ai_model='fixture-model', ai_usage_available=True,
                         ai_output_tokens=11, ai_input_tokens=SECRET, ai_reasoning_present=False,
                         request_bytes=SECRET, request_body_complete=SECRET,
                         ai_input_summary='prompt_source=nacos; system_characters=100; content_capture=disabled')
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
                      'attributes': attributes({'business.outcome': outcome, 'private': SECRET, 'gen_ai.response.finish_reasons': [SECRET],
                        'http.request.method': {'secret': SECRET}, 'rpc.service': {'secret': SECRET},
                        'rag.stage': [SECRET], 'db.system.name': SECRET, 'error.type': SECRET}),
                      'status': {'code': 2, 'message': SECRET},
                      'events': [{'name': SECRET, 'timeUnixNano': '1793923200000000000',
                                  'attributes': attributes({'exception.message': SECRET})}]})
    model_fields = {key: (int(item) if key.startswith('gen_ai.usage.') else item) for key, item in MODEL_FIELDS.items()}
    spans.append({'traceId': TRACE, 'spanId': SPAN, 'name': 'ai.model.call',
                  'startTimeUnixNano': '1793923200000000000', 'endTimeUnixNano': '1793923200007000000',
                  'attributes': attributes({**model_fields, 'gen_ai.input.messages': SECRET,
                                            'gen_ai.output.messages': SECRET, 'gen_ai.usage.cache_read.input_tokens': SECRET,
                                            'fleet.ai.prompt.key': 'https://' + SECRET})})
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
                    assert set(fields) <= set(LOG_FIELDS)
                    assert str(fields['duration_ms']) == '7' and fields['outcome'] == 'success'
                    assert fields.get('trace_id') == (TRACE if traced else None)
                    assert fields.get('span_id') == (SPAN if traced else None)
                    if stage == 'java-call':
                        assert fields['summary'] == 'POST /api/rag/retrievals 200 7ms success'
                        assert fields['request_body'] == '{"topK":3}'
                        assert fields['http_status'] == '200' and fields['request_body_complete'] is True
                    if stage == 'python-call':
                        assert fields['ai_usage_available'] is True and fields['ai_output_tokens'] == '11'
                        assert not {'ai_input_tokens', 'request_bytes', 'request_body_complete'} & fields.keys()
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


def native_case(directory, case, base, overlay, assertion, *, offsets=None, after_start=None):
    overlay.setdefault('service', {}).setdefault('telemetry', {})['metrics'] = {'level': 'none'}
    output = directory / f'output-{case}'
    output.mkdir(mode=0o777)
    output.chmod(0o777)  # 仅临时合成输出，允许固定镜像的非 root UID 写入。
    overlay.setdefault('exporters', {})['file/assertion'] = {
        'path': '/output/events.jsonl', 'format': 'json', 'flush_interval': '100ms'}
    overlay_path = directory / f'{case}.json'
    overlay_path.write_text(json.dumps(overlay))
    overlay_path.chmod(0o644)
    offsets = offsets or directory / f'offsets-{case}'
    offsets.mkdir(mode=0o700, exist_ok=True)
    name = 'fleet-observability-fixture-' + uuid.uuid4().hex[:12]
    common = ['docker', 'run', '--rm', '--name', name, '--network', 'none', '--read-only',
              '--user', f'{os.getuid()}:{os.getgid()}',
              '--cap-drop', 'ALL', '-e', 'FLEET_LOG_DIR=/fixture/logs',
              '-e', 'FLEET_LOG_STATE_DIR=/offsets',
              '-e', 'OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:9', '-e', 'FLEET_OTLP_TOKEN=synthetic',
              '-e', 'GITHUB_ES_URL=http://127.0.0.1:9', '-e', 'GITHUB_ES_API_KEY=synthetic',
              '-v', f'{ROOT}:/config:ro', '-v', f'{directory}:/fixture:ro',
              '-v', f'{directory / "otlp_token"}:/run/secrets/otlp_token:ro',
              '-v', f'{offsets}:/offsets:rw',
              '-v', f'{output}:/output:rw']
    command = common + [IMAGE, '--config=/config/' + base, '--config=/fixture/' + overlay_path.name]
    environment = None
    if COLLECTOR_BIN is not None:
        # 已安装固定版本也可回放；仅文件receiver/exporter启动，不读取用户应用日志。
        environment = dict(os.environ, FLEET_LOG_DIR=str(directory / 'logs'),
                           FLEET_LOG_STATE_DIR=str(offsets),
                           OTEL_EXPORTER_OTLP_ENDPOINT='http://127.0.0.1:9', FLEET_OTLP_TOKEN='synthetic',
                           GITHUB_ES_URL='http://127.0.0.1:9', GITHUB_ES_API_KEY='synthetic')
        local_base = directory / f'{case}-base.yaml'
        local_base.write_text((ROOT / base).read_text().replace('/run/secrets/otlp_token', str(directory / 'otlp_token')))
        overlay_path.write_text(json.dumps(overlay).replace('/output/', str(output) + '/').replace('/fixture/', str(directory) + '/'))
        command = [str(COLLECTOR_BIN), '--config=' + str(local_base), '--config=' + str(overlay_path)]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=environment)
    deadline = time.monotonic() + 30
    failed = False
    try:
        if after_start is not None:
            after_start(process)
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
    except Exception:
        failed = True
        raise
    finally:
        if COLLECTOR_BIN is None:
            subprocess.run(['docker', 'stop', '--time', '3', name], capture_output=True, timeout=10)
        else:
            process.terminate()
        try:
            logs, _ = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            logs, _ = process.communicate()
        # 回放失败后主动停止可能正常退出，仍需保留合成 fixture 的 processor 诊断。
        if failed or process.returncode not in (0, None):
            print(logs)


def offset_event(stage):
    """仅合成文件输入；stage 能区分初装历史、在线和停机期间追加的记录。"""
    return {'@timestamp': '2026-11-09T00:00:00.000000000Z',
            'service': {'name': 'fixture-java', 'version': '1.2.3', 'environment': 'github-dev'},
            'stage': stage, 'event': 'http.server.exchange', 'log': {'level': 'INFO'},
            'summary': 'GET /api/fixture 200 7ms success', 'elapsed_ms': 7, 'result': 'success',
            'http_method': 'GET', 'http_route': '/api/fixture', 'http_status': 200,
            'private': SECRET, 'message': SECRET}


def assert_offset_records(path, expected):
    batches = read_batches(path)
    assert SECRET not in json.dumps(batches), 'secret survived resumed filelog'
    stages = []
    for batch in batches:
        for resource in batch.get('resourceLogs', []):
            assert decoded(resource.get('resource', {}).get('attributes', [])) == {
                'service.name': 'fixture-java', 'service.version': '1.2.3',
                'deployment.environment.name': 'github-dev'}
            for scope in resource.get('scopeLogs', []):
                for record in scope.get('logRecords', []):
                    assert record.get('body', {}).get('stringValue', '') == ''
                    fields = decoded(record.get('attributes', []))
                    assert fields['event'] == 'http.server.exchange'
                    stages.append(fields['stage'])
    assert sorted(stages) == sorted(expected), 'resumed filelog duplicated, skipped or replayed records'


def run_offset_resume(directory):
    logs = directory / 'restart-logs'
    logs.mkdir(mode=0o755)
    source = logs / 'runtime.jsonl'
    # 首段超过默认 fingerprint 长度，停机追加不能改变既有文件身份。
    initial = ''.join(json.dumps(offset_event(f'initial-history-{i}')) + '\n' for i in range(8))
    assert len(initial.encode()) > 2048
    source.write_text(initial)
    source.chmod(0o644)
    offsets = directory / 'offsets-restart'

    def overlay():
        return {'receivers': {'filelog/runtime': {'include': ['/fixture/restart-logs/*.jsonl'], 'poll_interval': '100ms'}},
                'service': {'pipelines': {'logs': {'exporters': ['file/assertion']}}}}

    def append_live(process):
        # 等固定 receiver 的初次扫描；若首轮尚未识别文件，后面的 online 断言会失败而不能假绿。
        time.sleep(1)
        assert process.poll() is None, 'initial offset collector exited'
        with source.open('a') as stream:
            stream.write(json.dumps(offset_event('online-before-stop')) + '\n')

    native_case(directory, 'offset-seed', 'collector-agent.yaml', overlay(),
                lambda path: assert_offset_records(path, ['online-before-stop']),
                offsets=offsets, after_start=append_live)
    files = list(offsets.iterdir())
    assert files and all(path.is_file() and path.stat().st_mode & 0o077 == 0 for path in files), 'offset files must remain private'
    # native_case 已正常停止 Collector 并释放 storage；离线追加尚未被任何 receiver 读取。
    unread = ['offline-first', 'offline-second']
    with source.open('a') as stream:
        for stage in unread:
            stream.write(json.dumps(offset_event(stage)) + '\n')
    native_case(directory, 'offset-resume', 'collector-agent.yaml', overlay(),
                lambda path: assert_offset_records(path, unread), offsets=offsets)
    print('PASS persisted offsets: initial history skipped; offline records=2; duplicates=0')


def run_native(directory):
    native_case(directory, 'filelog', 'collector-agent.yaml', {
        'receivers': {'filelog/runtime': {'start_at': 'beginning'}},
        'service': {'pipelines': {'logs': {'exporters': ['file/assertion']}}}}, assert_logs)
    native_case(directory, 'same-batch', 'collector-agent.yaml', {
        'receivers': {'otlp_json_file/fixture': {'include': ['/fixture/grouping.jsonl'], 'start_at': 'beginning'}},
        'service': {'pipelines': {'logs': {'receivers': ['otlp_json_file/fixture'],
                                         'exporters': ['file/assertion']}}}}, assert_logs)
    # 前一阶段真实filelog输出再穿中心端，证明安全I/O正向字段没有被第二道白名单吞掉。
    native_case(directory, 'central-logs', 'collector.yaml', {
        'receivers': {'otlp_json_file/fixture': {'include': ['/fixture/output-filelog/events.jsonl'], 'start_at': 'beginning'}},
        'exporters': {'file/unused': {'path': '/output/unused-traces.jsonl', 'format': 'json'}},
        'service': {'pipelines': {'logs': {'receivers': ['otlp_json_file/fixture'], 'exporters': ['file/assertion']},
                                  'traces': {'receivers': ['otlp_json_file/fixture'], 'exporters': ['file/unused']}}}}, assert_logs)
    native_case(directory, 'business-spans', 'collector.yaml', {
        'receivers': {'otlp_json_file/fixture': {'include': ['/fixture/traces.jsonl'], 'start_at': 'beginning'}},
        'exporters': {'file/unused': {'path': '/output/unused-logs.jsonl', 'format': 'json'}},
        'service': {'pipelines': {'traces': {'receivers': ['otlp_json_file/fixture'],
                                           'exporters': ['file/assertion']},
                                  'logs': {'receivers': ['otlp_json_file/fixture'],
                                           'exporters': ['file/unused']}}}}, assert_traces)
    run_offset_resume(directory)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare-only', type=Path, help='只生成合成 fixture；不代表原生验证通过')
    parser.add_argument('--collector-bin', type=Path, help='复用已核固定0.164.0的本机二进制；不安装工具')
    args = parser.parse_args()
    if args.collector_bin:
        COLLECTOR_BIN = args.collector_bin.resolve(strict=True)
        version = subprocess.run([str(COLLECTOR_BIN), '--version'], check=True, text=True, capture_output=True).stdout
        if '0.164.0' not in version:
            raise ValueError('Collector fixture requires pinned 0.164.0')
    if args.prepare_only:
        prepare(args.prepare_only)
        print('Prepared synthetic fixtures only; native Collector was not run')
    else:
        with tempfile.TemporaryDirectory(prefix='fleet-collector-fixture-') as temporary:
            run_native(prepare(temporary))
