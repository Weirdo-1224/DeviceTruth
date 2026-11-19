"""Portable knowledge operations through the built JAR. Credentials are inherited, never printed."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['preflight', 'import', 'status', 'activate', 'rollback', 'build-index'])
    parser.add_argument('--jar', type=Path, default=ROOT / 'target/devicetruth-core-0.1.0.jar')
    parser.add_argument('--knowledge-root', type=Path, default=ROOT / 'datasets/knowledge')
    parser.add_argument('--state-root', type=Path, default=ROOT / 'outputs/knowledge-admin')
    parser.add_argument('--release-id')
    parser.add_argument('--budget-cny')
    parser.add_argument('--rate-cny-per-1000-tokens')
    args = parser.parse_args()
    java_home = os.environ.get('JAVA_HOME')
    java = str(Path(java_home) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')) if java_home else shutil.which('java')
    if not java or not args.jar.is_file():
        parser.error('JDK 21 and a built JAR are required; run mvn package first')
    if args.action in ('activate', 'rollback', 'build-index') and not args.release_id:
        parser.error('--release-id is required for this operation')
    if args.action == 'import' and (not args.budget_cny or not args.rate_cny_per_1000_tokens):
        parser.error('import requires an explicit --budget-cny and --rate-cny-per-1000-tokens')
    command = [java, '-Dloader.main=io.devicetruth.knowledge.KnowledgeOperations', '-cp', str(args.jar.resolve()),
               'org.springframework.boot.loader.launch.PropertiesLauncher', '--action=' + args.action,
               '--knowledge-root=' + str(args.knowledge_root.resolve()), '--state-root=' + str(args.state_root.resolve())]
    for name in ['release_id', 'budget_cny', 'rate_cny_per_1000_tokens']:
        value = getattr(args, name)
        if value:
            command.append('--' + name.replace('_', '-') + '=' + value)
    return subprocess.run(command, cwd=ROOT, env=os.environ.copy()).returncode


if __name__ == '__main__':
    sys.exit(main())
