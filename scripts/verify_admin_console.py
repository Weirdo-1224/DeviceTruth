#!/usr/bin/env python3
"""Only random test databases; credentials stay in process memory and child environment."""
import os
from pathlib import Path
import subprocess
import sys
import time
import json
import hashlib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / 'outputs/validation/platform-admin'
out.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env['JAVA_HOME'] = '/opt/homebrew/opt/openjdk@21'
env['PATH'] = env['JAVA_HOME'] + '/bin:' + env['PATH']
env.setdefault('DEVICETRUTH_TEST_PG_BASE_URL', 'jdbc:postgresql://127.0.0.1:54329/')
env.setdefault('DEVICETRUTH_TEST_PG_USERNAME', 'devicetruth')
if not env.get('DEVICETRUTH_TEST_PG_PASSWORD'):
    env['DEVICETRUTH_TEST_PG_PASSWORD'] = subprocess.run(
        ['docker', 'exec', 'devicetruth-postgres-1', 'printenv', 'POSTGRES_PASSWORD'],
        capture_output=True, text=True, check=True).stdout.rstrip('\n')
full = sys.argv[1:] == ['--full']
args = [] if full else sys.argv[1:] or ['-Dtest=PlatformAdminTest']
build_dir = 'outputs/validation/platform-admin/full-maven' if full else 'outputs/validation/platform-admin/maven'
log_path = out / ('full-maven.log' if full else 'maven.log')
started = time.time()
source_files = sorted([*root.glob('src/main/java/**/*.java'), *root.glob('src/main/resources/db/migration/*.sql'), *root.glob('frontend/src/**/*')])
fingerprints = {str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest() for p in source_files if p.is_file()}
with log_path.open('w') as log:
    result = subprocess.run(['mvn', '-o', '-q', '-Ddevicetruth.build-directory=' + build_dir, *args, 'test'],
                            cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
counts = {k: 0 for k in ['tests', 'failures', 'errors', 'skipped']}
reports = []
for report in (root / build_dir / 'surefire-reports').glob('TEST-*.xml'):
    if report.stat().st_mtime < started - 1:
        continue
    suite = ET.parse(report).getroot()
    reports.append(suite.get('name'))
    for key in counts:
        counts[key] += int(suite.get(key, 0))
counts['passed'] = counts['tests'] - counts['failures'] - counts['errors'] - counts['skipped']
summary = {'exitCode': result.returncode, 'counts': counts, 'suiteCount': len(reports), 'suites': sorted(reports), 'sourceFingerprints': fingerprints, 'externalModelTestsExplicitlyEnabled': any(arg == '-Ddevicetruth.test.admin-real=true' for arg in args)}
(out / ('full-summary.json' if full else 'summary.json')).write_text(json.dumps(summary, ensure_ascii=False, indent=2))
print('Maven exit:', result.returncode, 'counts:', counts, 'log:', log_path)
sys.exit(result.returncode)
