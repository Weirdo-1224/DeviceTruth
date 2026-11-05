"""Run the real packaged CLI and validate its persisted reports; no network or API key needed."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import tempfile
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

ROOT = Path(__file__).resolve().parent.parent
java = Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java') if os.environ.get('JAVA_HOME') else shutil.which('java')
jar = ROOT / 'target/devicetruth-core-0.1.0.jar'
assert jar.is_file(), 'Run mvn -q package first'
command = [str(java), '-Djava.io.tmpdir=' + str(ROOT / 'target'), '-jar', str(jar)]
help_run = subprocess.run(command + ['--help'], cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
assert help_run.returncode == 0 and '--scenario=' in help_run.stdout, help_run.stdout + help_run.stderr
summaries = []
for scenario, expected, rule in [('A', 'RESOLVED', 'DEMO-D-120A'), ('B', 'RESOLVED', 'DEMO-D-120B'), ('C', 'CONFLICT', None), ('D', 'NEED_EVIDENCE', None)]:
    run = subprocess.run(command + ['--scenario=' + scenario, '--reasoner=scripted'], cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
    assert run.returncode == 0, run.stdout + run.stderr
    match = re.search(r'report=(.+)', run.stdout)
    assert match, run.stdout
    directory = Path(match.group(1).strip())
    result = json.loads((directory / 'result.json').read_text('utf-8'))
    state = json.loads((directory / 'final_state.json').read_text('utf-8'))
    events = [json.loads(line) for line in (directory / 'events.jsonl').read_text('utf-8').splitlines()]
    evidence = json.loads((directory / 'evidence.json').read_text('utf-8'))
    assert result['outcome'] == expected and result['runStatus'] == 'COMPLETED', result
    assert result['appliedRuleIds'] == ([rule] if rule else []), result
    assert result['reasoner'] == 'scripted' and result['dataNature'] == 'SIMULATED'
    assert result['stateVersion'] == state['version'] == len(state['actionHistory']) == result['stepsUsed']
    assert all(e['assetId'] == result['assetId'] for e in evidence)
    ids = {e['evidenceId'] for e in evidence}
    assert set(result['supportingEvidenceIds']) <= ids
    assert events[-1]['type'] == 'TERMINATED'
    assert [e['sequence'] for e in events] == list(range(1, len(events) + 1))
    assert len([e for e in events if e['type'] == 'STATE_COMMITTED']) == result['stepsUsed']
    if scenario == 'B':
        assert any(h['status'] == 'INVALIDATED' for h in state['hypotheses'])
        assert any(e['type'] == 'HYPOTHESIS_INVALIDATED' for e in events)
    if scenario == 'C':
        assert len([f for f in state['facts'] if f['status'] == 'CONTESTED']) == 2
    if scenario == 'D':
        assert result['missingEvidenceNeeds']
    summary = dict(scenario=scenario, outcome=expected, rules=result['appliedRuleIds'], steps=result['stepsUsed'], runId=directory.name, events=len(events))
    summaries.append(summary)
    print(json.dumps(summary, ensure_ascii=False))
invalid = subprocess.run(command + ['--reasoner=invalid'], cwd=ROOT, capture_output=True, text=True, encoding='utf-8')
assert invalid.returncode == 2, 'Invalid CLI option must fail with exit 2'
offline_env = os.environ.copy()
for name in ['DASHSCOPE_API_KEY', 'OPENAI_API_KEY', 'OPENAI_BASE_URL', 'OPENAI_MODEL']:
    offline_env.pop(name, None)
with tempfile.TemporaryDirectory(prefix='cli-config-', dir=ROOT / 'target') as runtime:
    # Isolate the working directory so a developer's private key can never trigger a live call here.
    for reasoner, code in [('qwen', 'DASHSCOPE_API_KEY_MISSING'), ('openai', 'OPENAI_API_KEY_MISSING')]:
        failed = subprocess.run(command + ['--scenario=B', '--reasoner=' + reasoner], cwd=runtime,
                                env=offline_env, capture_output=True, text=True, encoding='utf-8')
        assert failed.returncode == 2, failed.stdout + failed.stderr
        match = re.search(r'report=(.+)', failed.stdout)
        assert match, failed.stdout
        directory = Path(match.group(1).strip())
        result = json.loads((directory / 'result.json').read_text('utf-8'))
        assert result['runStatus'] == 'FAILED' and result['outcome'] is None, result
        assert result['reasoner'] == reasoner and result['stopReason'] == code, result
        print('PASS: ' + reasoner + ' missing-key FAILED report, no fallback')

    requests = []
    class LocalModel(BaseHTTPRequestHandler):
        def do_POST(self):
            body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
            requests.append((self.path, self.headers.get('Authorization'), body))
            action = json.dumps({'type': 'ESCALATE', 'reason': 'local configuration contract'})
            response = json.dumps({'id': 'local-test', 'object': 'chat.completion', 'created': 0,
                                   'model': 'offline-model', 'choices': [{'index': 0, 'finish_reason': 'stop',
                                   'message': {'role': 'assistant', 'content': action}}]}).encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.end_headers()
            self.wfile.write(response)

        def log_message(self, *args):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), LocalModel)
    worker = threading.Thread(target=server.serve_forever, daemon=True)
    worker.start()
    try:
        (Path(runtime) / 'devicetruth.local.properties').write_text(
            'OPENAI_API_KEY=local-config-secret\nOPENAI_BASE_URL=http://127.0.0.1:'
            + str(server.server_port) + '/v1\nOPENAI_MODEL=offline-model\n', encoding='utf-8')
        configured = subprocess.run(command + ['--scenario=B', '--reasoner=openai'], cwd=runtime,
                                    env=offline_env, capture_output=True, text=True, encoding='utf-8')
        assert configured.returncode == 0, configured.stdout + configured.stderr
        match = re.search(r'report=(.+)', configured.stdout)
        assert match, configured.stdout
        directory = Path(match.group(1).strip())
        result = json.loads((directory / 'result.json').read_text('utf-8'))
        assert result['reasoner'] == 'openai' and result['outcome'] == 'ESCALATE', result
        assert len(requests) == 1 and requests[0][:2] == ('/v1/chat/completions', 'Bearer local-config-secret')
        assert requests[0][2]['model'] == 'offline-model'
        assert 'local-config-secret' not in configured.stdout + configured.stderr
        assert all('local-config-secret' not in p.read_text('utf-8') for p in directory.iterdir())
        print('PASS: packaged CLI reads private configuration without environment variables (local HTTP only)')
    finally:
        server.shutdown()
        server.server_close()
        worker.join(timeout=5)
destination = ROOT / 'target/cli-verification.json'
destination.write_text(json.dumps(summaries, indent=2) + '\n', encoding='utf-8')
print('PASS: help, A/B/C/D persisted reports, invalid CLI option, missing keys, private file; ' + str(destination))
