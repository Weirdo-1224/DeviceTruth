"""Explicit real-model acceptance, separate from offline tests. Never supplies a fake key or answer."""
import argparse
from pathlib import Path
import json
import os
import re
import shutil
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--reasoner', choices=['qwen', 'openai'], default='qwen')
reasoner = parser.parse_args().reasoner
root = Path(__file__).resolve().parent.parent
java = Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java') if os.environ.get('JAVA_HOME') else shutil.which('java')
command = [str(java), '-Djava.io.tmpdir=' + str(root / 'target'), '-jar', str(root / 'target/devicetruth-core-0.1.0.jar'), '--scenario=B', '--reasoner=' + reasoner]
run = subprocess.run(command, cwd=root, capture_output=True, text=True, encoding='utf-8')
print(run.stdout, end='')
match = re.search(r'report=(.+)', run.stdout)
if run.returncode:
    if match:
        failed_result = json.loads((Path(match.group(1).strip()) / 'result.json').read_text('utf-8'))
        code = failed_result['stopReason']
        configuration_errors = {'DASHSCOPE_API_KEY_MISSING', 'OPENAI_API_KEY_MISSING', 'OPENAI_BASE_URL_MISSING',
                                'OPENAI_MODEL_MISSING', 'OPENAI_CONFIGURATION_INVALID', 'MODEL_CONFIGURATION_UNREADABLE'}
        if code in configuration_errors:
            print('BLOCKED_BY_ENV: ' + code + '. Check devicetruth.local.properties or environment variables. No online call attempted.', file=sys.stderr)
            sys.exit(2)
    print('FAIL: real online CLI did not finish successfully; inspect the printed run directory.', file=sys.stderr)
    sys.exit(run.returncode)
assert match, 'CLI did not report an output directory'
directory = Path(match.group(1).strip())
result = json.loads((directory / 'result.json').read_text('utf-8'))
events = [json.loads(line) for line in (directory / 'events.jsonl').read_text('utf-8').splitlines()]
assert result['reasoner'] == reasoner and result['runStatus'] == 'COMPLETED'
assert result['outcome'] == 'RESOLVED' and result['appliedRuleIds'] == ['DEMO-D-120B'], result
assert any(e['type'] == 'MODEL_ATTEMPT' and e['data']['attempt']['outcome'] == 'VALID_PROPOSAL' for e in events)
assert any(e['type'] == 'CAPABILITY_RESULT' for e in events)
assert any(e['type'] == 'GUARD_RESULT' and e['data']['verdict']['allowed'] for e in events)
print('PASS: real ' + reasoner + ' B end-to-end acceptance; ' + str(directory))
