#!/usr/bin/env python3
"""Build, validate and explicitly import the supplied domain datasets (no external model).

Only the five asset business tables are written. Existing records are never updated.
Public AMPS rows are document snapshots, not assertions of physical configuration or repair.
"""
import argparse
from collections import Counter
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[1]
TABLES = ['dt_asset', 'dt_asset_component', 'dt_asset_configuration',
          'dt_asset_maintenance', 'dt_asset_observation']
PK = {'dt_asset': ['id'], 'dt_asset_component': ['asset_id', 'component_id'],
      'dt_asset_configuration': ['id'], 'dt_asset_maintenance': ['id'],
      'dt_asset_observation': ['asset_id', 'owner_id', 'project_id', 'signal', 'observed_at', 'source_id']}
ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}\Z')
SOURCE_TYPES = {'FACTORY', 'REPLACEMENT', 'CONFIGURATION', 'FIELD_CONFIRMATION',
                'FIELD_OBSERVATION', 'USER_STATEMENT'}
NAMESPACE = uuid.UUID('bf5cec66-ac10-4b05-81c0-2ca888c18ce8')


def encoded(value):
    return value if isinstance(value, str) else json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'))


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def instant(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00')).astimezone(timezone.utc)


class Builder:
    def __init__(self, source_root, owner, project):
        self.root = source_root.resolve()
        self.owner, self.project = owner, project
        self.created = datetime.now(timezone.utc).isoformat()
        self.rows = {t: [] for t in TABLES}
        self.provenance, self.files, self.exclusions = [], {}, []
        self.asset_ids, self.component_ids, self.signatures = set(), set(), set()
        self.legacy_worlds = {}

    def read(self, relative):
        path = self.root / relative
        self.files[str(relative)] = {'sha256': digest(path), 'bytes': path.stat().st_size}
        return json.loads(path.read_text(encoding='utf-8-sig'))

    def source(self, relative, pointer, nature, **details):
        return dict(file=str(relative), pointer=pointer, data_nature=nature, **details)

    def ref(self, source):
        # The URI remains resolvable on this workstation; manifest preserves portable relative paths.
        note = source.get('semantics', 'SIMULATED_SOURCE_CLAIM_NOT_REAL_FIELD_VERIFICATION')
        return (self.root / source['file']).as_uri() + '#' + source['pointer'] + '; semantics=' + note

    def add(self, table, row, source):
        row = dict(owner_id=self.owner, project_id=self.project, **row)
        key = (table, *(row[k] for k in PK[table]))
        if key in self.signatures:
            raise ValueError('Duplicate primary identity: ' + str(key))
        self.signatures.add(key)
        self.rows[table].append(row)
        self.provenance.append(dict(table=table, key={k: row[k] for k in PK[table]}, **source))

    def asset(self, asset_id, source):
        if asset_id in self.asset_ids:
            raise ValueError('Overlapping asset namespace: ' + asset_id)
        self.asset_ids.add(asset_id)
        self.add('dt_asset', dict(id=asset_id, created_at=self.created), source)

    def component(self, asset_id, component_id, label, source):
        key = (asset_id, component_id)
        if key in self.component_ids:
            return
        self.component_ids.add(key)
        self.add('dt_asset_component', dict(asset_id=asset_id, component_id=component_id,
                 label=label, created_at=self.created), source)

    def fact(self, table, asset_id, component_id, source_id, source, at,
             source_type='CONFIGURATION', valid_to=None, **values):
        at = instant(at).isoformat()
        identity = [table, self.owner, self.project, asset_id, component_id, source_id,
                    values.get('attribute', values.get('operation')), at]
        row = dict(id=str(uuid.uuid5(NAMESPACE, json.dumps(identity))), asset_id=asset_id,
                   component_id=component_id, source_type=source_type, source_id=source_id,
                   source_ref=self.ref(source), data_nature=source['data_nature'],
                   verification_status='DOCUMENTED', authority=1,
                   valid_from=at, valid_to=valid_to, recorded_at=self.created, observed_at=None, **values)
        self.add(table, row, source)

    def config(self, asset, component, attribute, value, source_id, source, at, **kwargs):
        if value is None:
            self.exclusions.append(dict(reason='UNKNOWN_VALUE', source=source, field=attribute))
            return
        value = encoded(value)
        if not ID.fullmatch(attribute) or not value.strip() or len(value) > 512:
            self.exclusions.append(dict(reason='CONFIGURATION_FIELD_OR_SIZE_LIMIT', source=source, field=attribute))
            return
        self.fact('dt_asset_configuration', asset, component, source_id, source, at,
                  attribute=attribute, value=value, **kwargs)

    def observation(self, asset, component, field, value, unit, at, source_id, source, quality):
        if value is None:
            self.exclusions.append(dict(reason='UNKNOWN_OBSERVATION', source=source, field=field))
            return
        signal = component + '.' + field
        value = encoded(value)
        if not ID.fullmatch(signal) or not value.strip() or len(value) > 256 or len(unit or '') > 32:
            self.exclusions.append(dict(reason='OBSERVATION_FIELD_OR_SIZE_LIMIT', source=source, field=field))
            return
        self.add('dt_asset_observation', dict(asset_id=asset, observed_at=instant(at).isoformat(),
                 signal=signal, value=value, unit=unit or '', source_id=source_id,
                 quality=quality, data_nature='SIMULATED'), source)

    def supplied_simulation(self, folder, namespace):
        assets = self.read(folder / 'assets.json')
        components = self.read(folder / 'components.json')
        sources = self.read(folder / 'source_records.json')
        evidence = self.read(folder / 'evidence_records.json')
        source_map = {s['source_record_id']: s for s in sources}
        # Validate every original payload, including records not projected into the five tables.
        raw_sources = {}
        for record in sources:
            raw = folder / record['artifact_path']
            raw_sources[record['source_record_id']] = self.read(raw)
            if digest(self.root / raw) != record['sha256']:
                raise ValueError('Source checksum mismatch: ' + str(raw))
        # Some legacy counterfactuals reuse SN-0245 in four incompatible scenario worlds.
        # Keep the first original world; do not merge histories or clone assets to raise counts.
        for record in sources:
            match = re.search(r'/((?:IND)-\d+)/', raw_sources[record['source_record_id']].get('legacy_locator', ''))
            if match:
                aid, world = record['asset_id'], match.group(1)
                self.legacy_worlds[aid] = min(world, self.legacy_worlds.get(aid, world))
        for i, asset in enumerate(assets):
            source = self.source(folder / 'assets.json', f'/{i}', 'SIMULATED', dataset=namespace)
            self.asset(asset['asset_id'], source)
        for i, comp in enumerate(components):
            source = self.source(folder / 'components.json', f'/{i}', 'SIMULATED', dataset=namespace)
            self.component(comp['asset_id'], comp['component_id'],
                           comp.get('component_type') or comp['component_id'], source)
        fields = {'brand': 'registeredManufacturer', 'manufacturer': 'registeredManufacturer',
                  'model': 'registeredModel', 'display_model': 'registeredModel',
                  'serial_number': 'registeredSerialNumber', 'model_year': 'modelYear',
                  'market': 'market', 'software_version': 'softwareVersion',
                  'firmware_version': 'firmwareVersion', 'engineering_version': 'engineeringVersion',
                  'order_number': 'orderNumber', 'commissioned_at': 'commissionDate',
                  'vehicle_alias': 'vehicleAlias', 'product_family': 'productFamily'}
        for asset in assets:
            origin = source_map[asset['metadata_source_record_id']]
            comp = origin['component_id']
            source = self.source(folder / origin['artifact_path'], '/payload/asset', 'SIMULATED', dataset=namespace)
            for field, attribute in fields.items():
                if asset.get(field) is not None:
                    self.config(asset['asset_id'], comp, attribute, asset[field],
                                namespace + ':' + origin['source_record_id'], source, origin['captured_at'])
        # Identical copies within a record are errors; independent source statements remain distinguishable.
        for e in evidence:
            origin = source_map[e['source_record_id']]
            raw = raw_sources[e['source_record_id']]
            match = re.search(r'/((?:IND)-\d+)/', raw.get('legacy_locator', ''))
            if match and match.group(1) != self.legacy_worlds[e['asset_id']]:
                self.exclusions.append(dict(reason='ALTERNATIVE_SCENARIO_WORLD_NOT_MERGED',
                                            evidence_id=e['evidence_id'], asset_id=e['asset_id'],
                                            world=match.group(1), retained_world=self.legacy_worlds[e['asset_id']]))
                continue
            if (e['asset_id'], e['component_id']) not in self.component_ids:
                raise ValueError('Unknown evidence component: ' + e['evidence_id'])
            source = self.source(folder / origin['artifact_path'], '/payload', 'SIMULATED',
                                 dataset=namespace, evidence_id=e['evidence_id'],
                                 original_source_type=e['source_type'], record_kind=e['record_kind'],
                                 clock_quality=e['clock_quality'], output_validity=e['output_validity'])
            sid = namespace + ':' + e['evidence_id']
            fields = {c['field']: c['value'] for c in e['claims']}
            if len(fields) != len(e['claims']):
                raise ValueError('Repeated evidence field: ' + e['evidence_id'])
            if e['record_kind'] == 'CONFIGURATION' and e['effective_at']:
                st = 'FACTORY' if e['source_type'] == 'FACTORY_BOM' else 'CONFIGURATION'
                for claim in e['claims']:
                    self.config(e['asset_id'], e['component_id'], claim['field'], claim['value'], sid,
                                source, e['effective_at'], source_type=st, valid_to=e['valid_to'])
            if e['record_kind'] in {'OBSERVATION', 'VERIFICATION', 'DIAGNOSTIC_CALL'}:
                quality = 'BAD' if e['output_validity'] == 'INVALID' else 'UNCERTAIN'
                if (e['output_validity'] == 'VALID' and e['clock_quality'] == 'SYNCHRONIZED'
                        and e['observation_basis'] in {'DIRECT_CAPTURE', 'DIRECT_OBSERVATION'}
                        and e['source_type'] not in {'USER_REPORT', 'OPERATOR_REPORT', 'OWNER_REPORT', 'OWNER_FEEDBACK'}):
                    quality = 'GOOD'
                # A capture timestamp remains usable even if the device clock is invalid;
                # quality prevents it from becoming a reliable device-time measurement.
                for claim in e['claims']:
                    self.observation(e['asset_id'], e['component_id'], claim['field'], claim['value'],
                                     claim['unit'], e['captured_at'], sid, source, quality)
            if e['record_kind'] in {'ACTION_RECEIPT', 'LIFECYCLE'}:
                operation = fields.get('action_performed') or fields.get('event_type')
                if not operation or not e['effective_at']:
                    self.exclusions.append(dict(reason='ACTION_HAS_NO_EXPLICIT_OPERATION_OR_TIME', source=source))
                    continue
                before = fields.get('before', fields.get('before_profinet_name'))
                after = fields.get('after', fields.get('after_profinet_name'))
                if fields.get('event_status') in {'APPROVED', 'REQUESTED', 'PLANNED'}:
                    operation = fields['event_status'] + '_' + operation
                    before, after = None, None
                    source['semantics'] = 'DOCUMENTED_PLAN_OR_APPROVAL_NOT_EXECUTION'
                before, after = (encoded(v) if v is not None else None for v in (before, after))
                if any(v is not None and len(v) > 128 for v in (before, after)):
                    self.exclusions.append(dict(reason='MAINTENANCE_VALUES_EXCEED_128', source=source))
                    continue
                self.fact('dt_asset_maintenance', e['asset_id'], e['component_id'], sid, source,
                          e['effective_at'], source_type='CONFIGURATION', operation=operation,
                          before_value=before, after_value=after)

    def public_registry(self, folder):
        assets = self.read(folder / 'nyc_assets_joined.json')
        orders = self.read(folder / 'nyc_workorders_joined.json')
        manifests = self.read(folder / 'download_manifest.json')
        capture_map, order_capture = {}, {}
        for item in manifests:
            if item['name'].startswith(('nyc_assets_', 'nyc_workorders_')):
                raw_path = folder / Path(item['local_path']).name
                if not (self.root / raw_path).exists():
                    continue
                rows = self.read(raw_path)
                if digest(self.root / raw_path) != item['sha256']:
                    raise ValueError('Public source checksum mismatch: ' + str(raw_path))
                for index, row in enumerate(rows):
                    mapping = capture_map if item['name'].startswith('nyc_assets_') else order_capture
                    key = row.get('obj_code') if mapping is capture_map else row.get('evt_code')
                    mapping[key] = (raw_path, index, item['retrieved_at_utc'], row)
        attributes = {'obj_manufact': 'registryManufacturerSnapshot', 'obj_manufactmodel': 'registryModelSnapshot',
                      'obj_serialno': 'registrySerialNumberSnapshot', 'obj_desc': 'registryDescriptionSnapshot',
                      'obj_commiss': 'registryCommissionDateTextSnapshot', 'obj_status': 'registryStatusCodeSnapshot'}
        for asset in assets:
            path, index, captured, original = capture_map[asset['obj_code']]
            if asset != original:
                raise ValueError('Joined asset differs from captured source')
            aid = 'NYC-AMPS-' + asset['obj_code']
            source = self.source(path, f'/{index}', 'AUTHORIZED', dataset='nyc-amps-public-20261114',
                                 semantics='REGISTRY_DOCUMENT_SNAPSHOT; valid_from=CAPTURE_TIME; physical_state=UNKNOWN',
                                 usage='User-authorized local dataset processing; no external model transmission')
            self.asset(aid, source)
            self.component(aid, 'ASSET', '整机登记对象（逻辑节点，不表示部件BOM）', source)
            for field, attribute in attributes.items():
                value = asset.get(field)
                if value and value != '-':
                    self.config(aid, 'ASSET', attribute, value, 'NYC-REGISTRY-' + asset['obj_code'],
                                dict(source, pointer=f'/{index}/{field}'), captured)
        for order in orders:
            if order['evt_type'] != 'JOB':
                self.exclusions.append(dict(reason='PUBLIC_EVENT_IS_NOT_JOB', work_order_id=order['evt_code'],
                                            event_type=order['evt_type']))
                continue
            path, index, captured, original = order_capture[order['evt_code']]
            if order != original:
                raise ValueError('Joined work order differs from captured source')
            description = re.sub(r'(?<!\d)(?:\+?1[- .]?)?\(?\d{3}\)?[- .]?\d{3}[- .]?\d{4}(?!\d)',
                                 '[PHONE_REDACTED]', order['evt_desc'])
            source = self.source(path, f'/{index}', 'AUTHORIZED', dataset='nyc-amps-public-20261114',
                                 semantics='WORK_ORDER_DOCUMENT_SNAPSHOT; valid_from=CAPTURE_TIME; execution_and_recovery=UNVERIFIED',
                                 work_order_id=order['evt_code'], description=description,
                                 reported_status=order.get('evt_udfchar13'),
                                 reported_at_raw=order.get('evt_reported'), completed_at_raw=order.get('evt_completed'))
            # Keep the recorded subject/status visible through the existing read-only tools.
            source['semantics'] += '; subject=' + description + '; recorded_status=' + str(source['reported_status'])
            self.fact('dt_asset_maintenance', 'NYC-AMPS-' + order['evt_object'], 'ASSET',
                      'NYC-WO-' + order['evt_code'], source, captured,
                      operation='WORK_ORDER_DOCUMENT_SNAPSHOT', before_value=None, after_value=None)

    def validate(self):
        for table, rows in self.rows.items():
            if len(rows) < 100:
                raise ValueError(f'{table}: only {len(rows)} rows')
            for row in rows:
                aid = row.get('asset_id', row.get('id'))
                if aid not in self.asset_ids or not ID.fullmatch(aid):
                    raise ValueError('Invalid asset reference')
                if 'component_id' in row and (aid, row['component_id']) not in self.component_ids:
                    raise ValueError('Invalid component reference')
                if table in {'dt_asset_configuration', 'dt_asset_maintenance'}:
                    if row['source_type'] not in SOURCE_TYPES or row['verification_status'] != 'DOCUMENTED':
                        raise ValueError('Invalid source/verification semantics')
                    if instant(row['recorded_at']) < instant(row['valid_from']):
                        raise ValueError('Record predates its effective interval')
                    if row['valid_to'] and instant(row['valid_to']) <= instant(row['valid_from']):
                        raise ValueError('Invalid validity interval')
                if table == 'dt_asset_observation' and row['data_nature'] != 'SIMULATED':
                    raise ValueError('Real observation forbidden by current schema')
        if len(self.provenance) != sum(map(len, self.rows.values())):
            raise ValueError('Missing provenance')
        # A content identity excludes source IDs and row UUIDs; independent measurements at
        # different times and independent document versions still retain their real identities.
        distinct = {}
        for table, rows in self.rows.items():
            keep = {'dt_asset': ['id'], 'dt_asset_component': ['asset_id', 'component_id', 'label'],
                    'dt_asset_configuration': ['asset_id', 'component_id', 'attribute', 'value'],
                    'dt_asset_maintenance': ['asset_id', 'component_id', 'operation', 'before_value', 'after_value', 'source_ref'],
                    'dt_asset_observation': ['asset_id', 'signal', 'value', 'unit', 'quality']}[table]
            distinct[table] = len({json.dumps([r[k] for k in keep], ensure_ascii=False) for r in rows})
            if distinct[table] < 100:
                raise ValueError('Insufficient distinct business content: ' + table)
        return dict(counts={t: len(v) for t, v in self.rows.items()},
                    distinct_business_content=distinct,
                    data_nature={t: dict(Counter(r.get('data_nature', 'SEE_PROVENANCE') for r in v))
                                 for t, v in self.rows.items()},
                    observation_quality=dict(Counter(r['quality'] for r in self.rows['dt_asset_observation'])),
                    maintenance_operations=dict(Counter(r['operation'] for r in self.rows['dt_asset_maintenance'])),
                    exclusions=dict(Counter(r['reason'] for r in self.exclusions)),
                    source_files=len(self.files), provenance_rows=len(self.provenance))


def psql(container, sql, database='devicetruth'):
    p = subprocess.run(['docker', 'exec', '-i', container, 'psql', '-U', 'devicetruth', '-d', database,
                        '-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1'], input=sql,
                       text=True, capture_output=True)
    if p.returncode:
        raise RuntimeError(p.stderr)
    return p.stdout.strip()


def sql_json(value):
    body = json.dumps(value, ensure_ascii=False)
    delimiter = '$data_' + uuid.uuid4().hex + '$'
    return delimiter + body + delimiter + '::jsonb'


def inventory(source_root):
    """Index every supplied file; identify copies without importing old/evaluation material."""
    files, hashes = [], Counter()
    for path in sorted(source_root.rglob('*')):
        if not path.is_file() or path.name == '.DS_Store':
            continue
        checksum = digest(path)
        hashes[checksum] += 1
        files.append(dict(path=str(path.relative_to(source_root)), bytes=path.stat().st_size,
                          extension=path.suffix, sha256=checksum))
    return dict(files=files, total_files=len(files), unique_file_contents=len(hashes),
                duplicate_copies=sum(n - 1 for n in hashes.values()),
                extensions=dict(Counter(x['extension'] for x in files)),
                selected=['EV v0.2', 'Industrial v0.7', 'NYC AMPS captured public records'],
                excluded_from_import=['Embedded duplicate EV delivery', 'Older v0.1/v0.6 projections',
                                      'Evaluation answers and release controllers',
                                      'Manufacturer manuals (reserved for separate knowledge ingestion)'])


def import_data(builder, folder, container):
    folder.mkdir(parents=True, exist_ok=True)
    before = json.loads(psql(container, 'SELECT json_build_object(' + ','.join(
        f"'{t}',(SELECT count(*) FROM {t})" for t in TABLES) + ');'))
    backup = folder / 'before-import.sql'
    if backup.exists():
        backup = folder / ('before-reimport-' + datetime.now(timezone.utc).strftime('%H%M%S') + '.sql')
    with backup.open('w') as out:
        subprocess.run(['docker', 'exec', container, 'pg_dump', '-U', 'devicetruth', '-d', 'devicetruth',
                        '--no-owner', '--no-privileges'], stdout=out, check=True)
    sql = ['BEGIN;', "SET LOCAL lock_timeout='10s';", "SET LOCAL statement_timeout='120s';",
           'LOCK TABLE ' + ','.join(TABLES) + ' IN SHARE ROW EXCLUSIVE MODE;']
    for table in TABLES:
        rows = builder.rows[table]
        temp = 'incoming_' + table
        sql += [f'CREATE TEMP TABLE {temp} (LIKE {table} INCLUDING DEFAULTS) ON COMMIT DROP;',
                f'INSERT INTO {temp} SELECT * FROM jsonb_populate_recordset(NULL::{table},{sql_json(rows)});',
                f'INSERT INTO {table} SELECT * FROM {temp} ON CONFLICT DO NOTHING;']
        columns = [k for k in rows[0] if k not in {'created_at', 'recorded_at'}]
        joins = ' AND '.join(f'a.{k}=b.{k}' for k in PK[table])
        differs = ' OR '.join(f'a.{k} IS DISTINCT FROM b.{k}' for k in columns)
        sql.append(f"DO $check$ BEGIN IF EXISTS(SELECT 1 FROM {temp} a LEFT JOIN {table} b ON {joins} "
                   f"WHERE b.{PK[table][0]} IS NULL OR {differs}) THEN "
                   f"RAISE EXCEPTION 'Existing data conflicts with incoming batch: {table}'; END IF; END $check$;")
        sql.append(f"DO $check$ BEGIN IF (SELECT count(*) FROM {temp})<100 THEN "
                   f"RAISE EXCEPTION 'Minimum row count not met: {table}'; END IF; END $check$;")
    sql.append('COMMIT;')
    psql(container, '\n'.join(sql))
    after = json.loads(psql(container, 'SELECT json_build_object(' + ','.join(
        f"'{t}',(SELECT count(*) FROM {t})" for t in TABLES) + ');'))
    return dict(database='devicetruth', container=container, before=before, after=after,
                inserted={t: after[t] - before[t] for t in TABLES}, backup=str(backup),
                transaction='COMMITTED', source_root=str(builder.root))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, default=Path('/Users/chiye/Desktop/工业设备'))
    parser.add_argument('--output', type=Path, default=ROOT / 'outputs/validation/domain-enrichment-20261114')
    parser.add_argument('--owner', default='local-owner')
    parser.add_argument('--project', default='local-project')
    parser.add_argument('--container', default='devicetruth-postgres-1')
    parser.add_argument('--apply', action='store_true', help='Explicitly write the validated batch to the existing database')
    args = parser.parse_args()
    for value in [args.owner, args.project]:
        if not ID.fullmatch(value):
            parser.error('Invalid owner/project identifier')
    b = Builder(args.source_root, args.owner, args.project)
    previous = args.output / 'data.json'
    if previous.exists():
        # Keep batch timestamps stable across reruns; existing data is never rewritten.
        b.created = json.loads(previous.read_text())['dt_asset'][0]['created_at']
    b.supplied_simulation(Path('DeviceTruth_EV_v0.1_领域数据与Skills/01_可用数据/EV调查数据_v0.2'), 'EV02')
    b.supplied_simulation(Path('DeviceTruth_Industrial_v0.6_数据扩充版/01_可用数据/工业调查数据_v0.7'), 'IND07')
    b.public_registry(Path('DeviceTruth_Industrial_v0.6_数据扩充版/07_真实公开资料采集_2026-11-14/B_真实公开业务数据'))
    report = b.validate()
    args.output.mkdir(parents=True, exist_ok=True)
    for name, data in [('data.json', b.rows), ('provenance.json', b.provenance),
                       ('source-manifest.json', b.files), ('exclusions.json', b.exclusions), ('validation.json', report),
                       ('source-inventory.json', inventory(args.source_root))]:
        (args.output / name).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n')
    if args.apply:
        applied = import_data(b, args.output, args.container)
        target = 'reimport-result.json' if (args.output / 'import-result.json').exists() else 'import-result.json'
        (args.output / target).write_text(json.dumps(applied, ensure_ascii=False, indent=2) + '\n')
        report['import'] = applied
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
