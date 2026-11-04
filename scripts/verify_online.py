"""Explicit real-Qwen acceptance, separate from offline tests. Never supplies a fake key or answer."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys

if not os.environ.get('DASHSCOPE_API_KEY', '').strip():
    print('BLOCKED_BY_ENV: DASHSCOPE_API_KEY is missing. No online call attempted.', file=sys.stderr)
    sys.exit(2)
root = Path(__file__).resolve().parent.parent
java = Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java') if os.environ.get('JAVA_HOME') else shutil.which('java')
command = [str(java), '-Djava.io.tmpdir=' + str(root / 'target'), '-jar', str(root / 'target/devicetruth-core-0.1.0.jar'), '--scenario=B', '--reasoner=qwen']
run = subprocess.run(command, cwd=root, capture_output=True, text=True, encoding='utf-8')
print(run.stdout, end='')
if run.returncode:
    print('FAIL: real online CLI did not finish successfully; inspect the printed run directory.', file=sys.stderr)
    sys.exit(run.returncode)
match = re.search(r'report=(.+)', run.stdout)
assert match, 'CLI did not report an output directory'
directory = Path(match.group(1).strip())
result = json.loads((directory / 'result.json').read_text('utf-8'))
events = [json.loads(line) for line in (directory / 'events.jsonl').read_text('utf-8').splitlines()]
assert result['reasoner'] == 'qwen' and result['runStatus'] == 'COMPLETED'
assert result['outcome'] == 'RESOLVED' and result['appliedRuleIds'] == ['DEMO-D-120B'], result
assert any(e['type'] == 'MODEL_ATTEMPT' and e['data']['attempt']['outcome'] == 'VALID_PROPOSAL' for e in events)
assert any(e['type'] == 'CAPABILITY_RESULT' for e in events)
assert any(e['type'] == 'GUARD_RESULT' and e['data']['verdict']['allowed'] for e in events)
print('PASS: real Qwen B end-to-end acceptance; ' + str(directory))
