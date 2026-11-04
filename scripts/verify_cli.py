"""Run the real packaged CLI and validate its persisted reports; no network or API key needed."""
from pathlib import Path
import json
import os
import re
import shutil
import subprocess

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
destination = ROOT / 'target/cli-verification.json'
destination.write_text(json.dumps(summaries, indent=2) + '\n', encoding='utf-8')
print('PASS: help, A/B/C/D persisted reports, invalid CLI option; ' + str(destination))
