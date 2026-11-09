"""Read-only planning checks; writes only .planning/evidence/planning-validation.json."""
import argparse
from collections import Counter
from datetime import datetime, timedelta, timezone
import json
import hashlib
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parent.parent
planning = root / '.planning'
parser = argparse.ArgumentParser()
parser.add_argument('--gsd-tools', type=Path, default=Path.home() / '.codex/gsd-core/bin/gsd-tools.cjs')
args = parser.parse_args()
errors = []
commands = []


def check(condition, message):
    if not condition:
        errors.append(message)


def gsd(*parts):
    command = ['node', str(args.gsd_tools), *parts]
    result = subprocess.run(command, cwd=root, capture_output=True, text=True, encoding='utf-8')
    commands.append({'command': command, 'exit_code': result.returncode})
    check(result.returncode == 0, 'GSD failed: ' + ' '.join(parts))
    return json.loads(result.stdout)


def field(text, name):
    return re.search(r'^' + re.escape(name) + r': (\[[\s\S]*?\]|[^\n]+)', text, re.M)[1]


rows = {}
status_counts = Counter()
for line in (planning / 'REQUIREMENTS.md').read_text(encoding='utf-8').splitlines():
    cells = [cell.strip() for cell in line.strip('|').split('|')]
    if cells and re.fullmatch(r'[A-Z]+-\d+', cells[0]):
        check(cells[0] not in rows, 'Duplicate requirement: ' + cells[0])
        rows[cells[0]] = cells[5].split(' / ')[1]
        status_counts[cells[3].strip('*')] += 1
plans = {}
coverage = Counter()
structures = []
for path in sorted(planning.glob('phases/*/*-PLAN.md')):
    text = path.read_text(encoding='utf-8')
    key = path.name[:5]
    files = json.loads(field(text, 'files_modified'))
    reqs = json.loads(field(text, 'requirements'))
    tasks = re.findall(r'<task .*?</task>', text, re.S)
    used = set()
    for task in tasks:
        files_text = re.search(r'<files>(.*?)</files>', task, re.S)[1]
        used.update(re.findall(r'^- (.+)$', files_text, re.M))
    check(set(files) == used, key + ' task files mismatch: ' + str(sorted(set(files) ^ used)))
    for source in re.findall(r'    - from: "([^"]+)"', text):
        check(source in files, key + ' key_link source absent from files: ' + source)
    check(field(text, 'approved_for_execution') == 'false', key + ' execution flag changed')
    check(field(text, 'review_status') == 'pending', key + ' review flag changed')
    for req in reqs:
        coverage[req] += 1
        check(rows.get(req) == key, key + ' wrong primary requirement: ' + req)
    plans[key] = {'dependencies': json.loads(field(text, 'depends_on')), 'wave': int(field(text, 'wave')), 'requirements': reqs, 'task_count': len(tasks)}
    structure = gsd('verify', 'plan-structure', path.relative_to(root).as_posix())
    check(structure['valid'] and not structure['errors'] and not structure['warnings'], key + ' invalid structure')
    structures.append({'plan': key, **structure})
check(len(rows) == 63 and len(plans) == 13, 'Requirement/plan count changed')
check(set(coverage) == set(rows) and all(v == 1 for v in coverage.values()), 'Primary coverage must be exactly once')
visiting, visited = set(), set()


def visit(key):
    if key in visiting:
        errors.append('Dependency cycle: ' + key)
        return
    if key in visited:
        return
    visiting.add(key)
    for dep in plans[key]['dependencies']:
        check(dep in plans, key + ' missing dependency: ' + dep)
        if dep not in plans:
            continue
        check(dep[:2] <= key[:2], key + ' dependency points to later phase')
        if dep[:2] == key[:2]:
            check(plans[dep]['wave'] < plans[key]['wave'], key + ' wave order invalid')
        visit(dep)
    visiting.remove(key)
    visited.add(key)


for key in plans:
    visit(key)
for key, dep in {'02-01': '01-02', '03-01': '02-03', '04-01': '03-03', '05-01': '04-02'}.items():
    check(dep in plans[key]['dependencies'], key + ' missing phase exit dependency')
roadmap_text = (planning / 'ROADMAP.md').read_text(encoding='utf-8')
for phase, section in re.findall(r'### Phase (\d+):.*?\n(.*?)(?=\n### Phase|\n## 开发顺序)', roadmap_text, re.S):
    expected = set(re.search(r'\*\*Requirements:\*\* (.+)', section)[1].split(', '))
    actual = {req for key, plan in plans.items() if int(key[:2]) == int(phase) for req in plan['requirements']}
    check(actual == expected, 'Roadmap requirement mismatch phase ' + phase)
    for key, dependencies in re.findall(r'\[([0-9-]+)-PLAN.md\].*?依赖 (.*?)；wave', section):
        declared = [] if dependencies == '无' else dependencies.split(', ')
        check(declared == plans[key]['dependencies'], 'Roadmap dependency mismatch: ' + key)
state_text = (planning / 'STATE.md').read_text(encoding='utf-8')
check(field(state_text, 'status') == 'planning', 'State status changed')
check(field(state_text, 'review_status') == 'review_pending', 'Review state changed')
check(field(state_text, 'execution_authorized') == 'false', 'Execution authorized')
check(len(state_text.splitlines()) < 100, 'STATE too long')
check(not list(planning.glob('phases/**/*-SUMMARY.md')), 'Unexpected development summary')
for path in planning.rglob('*.md'):
    text = path.read_text(encoding='utf-8')
    check('\ufffd' not in text, 'Invalid text replacement in ' + str(path))
    for target in re.findall(r'\]\(([^)]+)\)', text):
        if '://' not in target and not target.startswith('#'):
            check((path.parent / target.split('#')[0]).exists(), 'Broken link: ' + str(path) + ' -> ' + target)
consistency = gsd('validate', 'consistency')
check(consistency['passed'] and not consistency['errors'] and not consistency['warnings'], 'GSD consistency failed')
roadmap = gsd('roadmap', 'analyze')
state = gsd('state-snapshot')
check(roadmap['total_summaries'] == 0 and roadmap['completed_phases'] == 0 and roadmap['progress_percent'] == 0, 'Roadmap development progress changed')
check(state['progress_percent'] == 0, 'State development progress changed')
baseline = json.loads((planning / 'evidence/baseline.json').read_text(encoding='utf-8'))
changed_sources = [name for name, digest in baseline['source_hashes'].items()
                   if not (root / name).is_file() or hashlib.sha256((root / name).read_bytes()).hexdigest() != digest]
protected_paths = ['src', 'pom.xml', 'scripts', 'datasets', 'skills', '.mvn', 'frontend']
git_changes = subprocess.run(['git', 'diff', 'HEAD', '--name-only', '--', *protected_paths], cwd=root, capture_output=True, text=True, encoding='utf-8')
git_new = subprocess.run(['git', 'ls-files', '--others', '--exclude-standard', '--', *protected_paths], cwd=root, capture_output=True, text=True, encoding='utf-8')
design = baseline['design']
design_unchanged = hashlib.sha256((root / design['path']).read_bytes()).hexdigest() == design['sha256']
scope_check = {'method': 'Recorded baseline SHA256 plus git tracked/untracked business paths',
               'protected_baseline_files': len(baseline['source_hashes']), 'changed_hashes': changed_sources,
               'tracked_changes': git_changes.stdout.splitlines(), 'new_business_files': git_new.stdout.splitlines(),
               'design_hash_unchanged': design_unchanged}
scope_check['passed'] = not changed_sources and not git_changes.stdout.strip() and not git_new.stdout.strip() and design_unchanged and git_changes.returncode == 0 and git_new.returncode == 0
check(scope_check['passed'], 'Business source/config scope changed')
result = {
    'date': datetime.now(timezone(timedelta(hours=8))).date().isoformat(), 'timezone': 'Asia/Shanghai',
    'requirements': len(rows), 'status_counts': dict(status_counts), 'plans': len(plans),
    'tasks': sum(plan['task_count'] for plan in plans.values()), 'coverage_exactly_once': set(coverage) == set(rows) and all(v == 1 for v in coverage.values()),
    'checks_passed': not errors, 'errors': errors, 'gsd_consistency': consistency,
    'plan_structures': structures, 'dependency_graph': plans, 'roadmap': roadmap, 'state': state,
    'commands': commands, 'semantic_independent_review': False, 'user_review': 'pending', 'execution_authorized': False,
    'revision_scope_check': scope_check, 'business_sources_unchanged': scope_check['passed'],
}
(planning / 'evidence/planning-validation.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'passed': not errors, 'requirements': len(rows), 'plans': len(plans), 'tasks': result['tasks'], 'errors': errors}, ensure_ascii=False))
raise SystemExit(0 if not errors else 1)
