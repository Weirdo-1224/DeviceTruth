#!/usr/bin/env python3
"""Exercise natural-language selection of the six product Skills on the running server."""
import concurrent.futures
import argparse
import json
from pathlib import Path
import time
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'outputs/validation/domain-skills-20261114'
BASE = 'http://127.0.0.1:8080'
CASES = [
    ('charging', 'ev-charging-triage', 'EV-DEMO-004',
     '我这辆车插上交流充电枪后一直充不上电，帮我看看是什么情况？之前有没有修过？会不会是车载充电机坏了？'),
    ('maintenance', 'ev-maintenance-inspection', 'EV-DEMO-017',
     '这辆车的胎压灯之前亮过，后来有没有处理，处理后胎压是什么情况？请先帮我核对已有记录。'),
    ('powertrain', 'ev-powertrain-safety-investigation', 'EV-DEMO-009',
     '这辆车之前出现过ICCU告警和动力受限，已经停在安全位置。请核对已有记录，能确定召回适用、已经维修了吗？'),
    ('configuration', 'industrial-configuration-investigation', 'SN-0245',
     '旧工单让我买D-120A，但听说控制器换过了。帮我核对一下型号和更换记录，现在能直接照旧工单买吗？'),
    ('controller', 'industrial-controller-fault-triage', 'SIM-ASSET-001',
     '换过远程I/O后，画面上的数据不再更新。帮我查查已有记录，能判断是通信模块坏了吗？'),
    ('carrier', 'carrier-rtu-evidence-triage', None,
     '我们有台Carrier 48TF屋顶机组，之前计划改用MERV13过滤器。只看到工单写着Completed，就能认为改造已经完成、风量也合格吗？'),
]


def request(method, path, body=None, headers=None):
    h = {'Content-Type': 'application/json; charset=UTF-8', **(headers or {})}
    r = urllib.request.Request(BASE + path, method=method, headers=h,
                               data=json.dumps(body, ensure_ascii=False).encode() if body is not None else None)
    with urllib.request.urlopen(r, timeout=15) as response:
        return json.load(response)


def run_case(case):
    name, skill, asset, question = case
    c = request('POST', '/api/conversations', {})
    payload = {'schemaVersion': 1, 'userMessage': question}
    if asset:
        payload['selectedAssetId'] = asset
    run = request('POST', f"/api/conversations/{c['conversationId']}/messages", payload,
                  {'Idempotency-Key': str(uuid.uuid4())})
    rid = run['runId']
    deadline = time.monotonic() + 160
    while time.monotonic() < deadline:
        run = request('GET', f'/api/runs/{rid}')
        if run['status'] not in {'QUEUED', 'RUNNING'}:
            break
        time.sleep(.6)
    events = request('GET', f'/api/runs/{rid}/tool-events')
    refs = request('GET', f'/api/runs/{rid}/references')
    try:
        active = request('GET', f'/api/runs/{rid}/skill')
    except Exception:
        active = {}
    success_tools = {e['toolName'] for e in events if e['success']}
    answer = run.get('result') or {}
    used = {r['referenceId'] for r in answer.get('references', [])}
    checks = dict(correct_skill=active.get('skillId') == skill,
                  current_version=active.get('version') == next(
                      s['adapted_version'] for s in json.loads((ROOT / 'docs/skills/domain-skill-sources.json').read_text())
                      if s['skill_id'] == skill),
                  usable_state=run['status'] in {'COMPLETED', 'WAITING_FOR_USER'},
                  activation_succeeded='activate_skill' in success_tools,
                  no_unregistered_tool_calls=all(e['toolName'] in {
                      'activate_skill', 'read_skill_reference', 'get_asset', 'get_configuration',
                      'get_maintenance_history', 'get_observations', 'search_knowledge', 'get_document_excerpt'} for e in events),
                  no_unverified_confirmation=answer.get('category') != 'CONFIRMED_FINDING',
                  citations_from_current_run=used <= {r['referenceId'] for r in refs})
    if asset:
        checks['answer_completed'] = run['status'] == 'COMPLETED'
        checks['structured_records_queried'] = bool(success_tools & {'get_configuration', 'get_maintenance_history', 'get_observations'})
        checks['record_references_obtained'] = bool(refs)
    cited = json.dumps(answer.get('references', []), ensure_ascii=False)
    if name == 'maintenance':
        checks['pressure_adjustment_cited'] = '250' in cited and '290' in cited
    if name == 'powertrain':
        checks['existing_iccu_observations_read'] = any(
            s in json.dumps(refs, ensure_ascii=False) for s in ['ICCU.applicability_status', 'ICCU.warning_text'])
    if name == 'configuration':
        checks['installed_part_configuration_cited'] = 'CTRL-01.installed_part' in cited
        checks['replacement_record_cited'] = 'D-120B' in cited and 'PART_REPLACEMENT' in cited
    if name == 'controller':
        checks['communication_observations_cited'] = 'PN-01.io_data_exchange' in cited
        checks['expected_and_online_names_read'] = all(s in json.dumps(refs, ensure_ascii=False) for s in [
            'PN-01.expected_profinet_name', 'PN-01.online_profinet_name'])
    if name == 'carrier':
        checks['no_arbitrary_asset_read'] = not success_tools & {
            'get_asset', 'get_configuration', 'get_maintenance_history', 'get_observations'}
    if name == 'charging' and run['status'] == 'COMPLETED':
        text = json.dumps(answer, ensure_ascii=False)
        checks['red_and_stopped_record_in_answer'] = ('红' in text or 'RED' in text) and ('停止' in text or 'STOPPED' in text)
        checks['no_maintenance_means_no_record'] = '维修' in text and any(s in text for s in ['未查', '没有查', '无记录', '未找到', '未返回', '没有维修记录', '未提供', '未发现'])
    result = dict(name=name, expected_skill=skill, asset_id=asset, question=question,
                  run=run, active_skill=active, tool_events=events, references=refs, checks=checks,
                  passed=all(checks.values()))
    (OUT / f'online-{name}.json').write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(dict(case=name, run_id=rid, status=run['status'], skill=active.get('skillId'),
                         passed=result['passed'], failed=[k for k, v in checks.items() if not v]), ensure_ascii=False), flush=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--case', choices=[c[0] for c in CASES], action='append')
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for result in pool.map(run_case, [c for c in CASES if not args.case or c[0] in args.case]):
            results.append(result)
    summary = dict(model='configured live provider', cases=len(results), passed=sum(r['passed'] for r in results),
                   checks=sum(len(r['checks']) for r in results),
                   checks_passed=sum(sum(r['checks'].values()) for r in results), results=results)
    name = 'online-summary-selected.json' if args.case else 'online-summary.json'
    (OUT / name).write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n')
    print('Cases:', summary['passed'], '/', summary['cases'], flush=True)
    raise SystemExit(0 if summary['passed'] == summary['cases'] else 1)


if __name__ == '__main__':
    main()
