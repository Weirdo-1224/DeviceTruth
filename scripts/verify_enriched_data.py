#!/usr/bin/env python3
"""Verify the imported business dataset using PostgreSQL and actual product tools."""
import json
import os
from pathlib import Path
import subprocess

from enrich_device_data import ROOT, TABLES, digest, psql
from verify_general_agent import Server, expect, create_run, await_run

OUT = ROOT / 'outputs/validation/domain-enrichment-20261114'


def main():
    checks, runs = {}, []
    data = json.loads((OUT / 'data.json').read_text())
    result = json.loads((OUT / 'import-result.json').read_text())
    source_root = Path(result['source_root'])
    manifest = json.loads((OUT / 'source-manifest.json').read_text())
    checks['all_source_checksums_match'] = all(digest(source_root / p) == m['sha256'] for p, m in manifest.items())
    counts = json.loads(psql(result['container'], 'SELECT json_build_object(' + ','.join(
        f"'{t}',(SELECT count(*) FROM {t} WHERE owner_id='local-owner' AND project_id='local-project')"
        for t in TABLES) + ');'))
    checks['all_five_tables_at_least_100'] = all(n >= 100 for n in counts.values())
    checks['database_counts_match_batch'] = counts == {t: len(data[t]) for t in TABLES}
    repeat = json.loads((OUT / 'reimport-result.json').read_text())
    checks['second_import_inserts_zero'] = all(n == 0 for n in repeat['inserted'].values())
    rules = {
        'no_orphan_components': "SELECT count(*) FROM dt_asset_component c LEFT JOIN dt_asset a ON (a.id,a.owner_id,a.project_id)=(c.asset_id,c.owner_id,c.project_id) WHERE a.id IS NULL",
        'no_real_sensor_observations': "SELECT count(*) FROM dt_asset_observation WHERE data_nature<>'SIMULATED'",
        'no_document_promoted_to_field_verification': "SELECT count(*) FROM dt_asset_configuration WHERE verification_status<>'DOCUMENTED' OR observed_at IS NOT NULL",
        'no_false_repair_values_in_public_workorders': "SELECT count(*) FROM dt_asset_maintenance WHERE data_nature='AUTHORIZED' AND (operation<>'WORK_ORDER_DOCUMENT_SNAPSHOT' OR before_value IS NOT NULL OR after_value IS NOT NULL OR observed_at IS NOT NULL)",
        'no_alternative_SN0245_worlds': "SELECT count(*) FROM dt_asset_configuration WHERE asset_id='SN-0245' AND source_id IN ('IND07:EV-000121','IND07:EV-000122','IND07:EV-000126','IND07:EV-000127','IND07:EV-000129','IND07:EV-000130')",
        'no_duplicate_configuration_source_facts': "SELECT count(*) FROM (SELECT asset_id,component_id,attribute,source_id,valid_from FROM dt_asset_configuration GROUP BY 1,2,3,4,5 HAVING count(*)>1) q",
        'no_duplicate_workorder_snapshots': "SELECT count(*) FROM (SELECT source_id FROM dt_asset_maintenance WHERE operation='WORK_ORDER_DOCUMENT_SNAPSHOT' GROUP BY 1 HAVING count(*)>1) q",
    }
    for name, sql in rules.items():
        checks[name] = psql(result['container'], 'BEGIN READ ONLY; ' + sql + '; COMMIT;') == '0'

    # Credentials stay in this process's memory and the child server environment.
    password = subprocess.run(['docker', 'exec', result['container'], 'printenv', 'POSTGRES_PASSWORD'],
                              capture_output=True, text=True, check=True).stdout.rstrip('\n')
    os.environ.update(JAVA_HOME=subprocess.check_output(['/usr/libexec/java_home', '-v', '21'], text=True).strip(),
                      DEVICETRUTH_TEST_PG_BASE_URL='jdbc:postgresql://127.0.0.1:54329/',
                      DEVICETRUTH_TEST_PG_USERNAME='devicetruth', DEVICETRUTH_TEST_PG_PASSWORD=password)
    server = None
    try:
        server = Server('local-owner', 'local-project', 'deterministic', 'scripted', True, OUT)
        assets = expect(server.request('GET', '/api/assets'), 200)
        checks['asset_list_contains_entire_batch'] = set(assets) == {a['id'] for a in data['dt_asset']}
        carrier = expect(server.request('GET', '/api/assets/NYC-AMPS-11870'), 200)
        checks['carrier_public_snapshot_identity'] = any(
            e['attribute'] == 'registryModelSnapshot' and e['value'] == '48TFE014-501'
            for e in carrier['configurations'])
        checks['public_snapshot_is_unconfirmed'] = carrier['installationStatus'] == 'UNCONFIRMED' and carrier['dataNature'] == 'AUTHORIZED'
        history = expect(server.request('GET', '/api/assets/NYC-AMPS-11870/maintenance'), 200)
        checks['carrier_two_original_workorders'] = {e['sourceId'] for e in history['entries']} == {'NYC-WO-2134627', 'NYC-WO-640725'}
        old = expect(server.request('GET', '/api/assets/NYC-AMPS-11870?asOf=2026-11-13T00:00:00Z'), 200)
        checks['snapshot_not_backdated_to_physical_history'] = old['configurations'] == [] and old['maintenance'] == []
        ev = expect(server.request('GET', '/api/assets/EV-DEMO-001/configuration?attribute=softwareVersion'), 200)
        checks['ev_configuration_readable'] = bool(ev['entries']) and ev['dataNature'] == 'SIMULATED'
        checks['unknown_asset_returns_404'] = server.request('GET', '/api/assets/NO-SUCH-ENRICHMENT-ASSET')[0] == 404
        for aid, skill, attr in [('EV-DEMO-001', 'automotive-diagnosis', 'softwareVersion'),
                                 ('SIM-ASSET-001', 'factory-equipment', 'firmwareVersion')]:
            rid = create_run(server, f'数据验收：{skill}，查询 {attr} 并核查已导入观测，保留模拟标记与引用。',
                             {'selectedAssetId': aid})
            run = await_run(server, rid, timeout=45)
            events = expect(server.request('GET', f'/api/runs/{rid}/tool-events'), 200)
            refs = expect(server.request('GET', f'/api/runs/{rid}/references'), 200)
            active = expect(server.request('GET', f'/api/runs/{rid}/skill'), 200)
            tools = {e.get('toolName') for e in events if e.get('success')}
            checks[aid + '_runtime_completed'] = run['status'] == 'COMPLETED'
            checks[aid + '_correct_skill'] = active['skillId'] == skill
            checks[aid + '_configuration_and_observations_tools'] = {'get_configuration', 'get_observations'} <= tools
            checks[aid + '_observation_references_persisted'] = any(r['sourceType'] == 'FIELD_OBSERVATION' for r in refs)
            answer = run.get('result') or {}
            checks[aid + '_answer_preserves_simulation'] = answer.get('dataNature') == 'SIMULATED'
            used = [r['referenceId'] for r in answer.get('references', [])]
            checks[aid + '_answer_citations_from_current_run'] = bool(used) and set(used) <= {r['referenceId'] for r in refs}
            runs.append(dict(asset_id=aid, run=run, tool_events=events, references=refs, skill=active))
    finally:
        if server:
            server.close()
        evidence = dict(counts=counts, checks=checks, passed=sum(checks.values()), total=len(checks),
                        failed=[k for k, v in checks.items() if not v],
                        provider='scripted; deterministic embedding; no external LLM or embedding calls', runs=runs)
        (OUT / 'product-verification.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({k: v for k, v in evidence.items() if k != 'runs'}, ensure_ascii=False, indent=2))
    if not all(checks.values()):
        raise SystemExit(1)


if __name__ == '__main__':
    main()
