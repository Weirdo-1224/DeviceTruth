"""Verify and pack only the released knowledge inputs, using Python's standard library."""
import argparse
import hashlib
import io
import json
from pathlib import Path, PurePosixPath
import tarfile

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = 'datasets/knowledge/package-manifest.json'


def verify(root):
    manifest = json.loads((root / MANIFEST).read_text(encoding='utf-8'))
    paths = set()
    for entry in manifest['files']:
        relative = PurePosixPath(entry['path'])
        if relative.is_absolute() or '..' in relative.parts or str(relative) in paths:
            raise ValueError('Invalid or duplicate package path')
        paths.add(str(relative))
        path = root / str(relative)
        if not path.resolve().is_relative_to(root.resolve()) or path.is_symlink():
            raise ValueError('Package path leaves data root')
        if path.stat().st_size != entry['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != entry['sha256']:
            raise ValueError('Knowledge input checksum mismatch: ' + str(relative))
    return manifest


def pack(root, output):
    manifest = verify(root)
    output.parent.mkdir(parents=True, exist_ok=True)
    paths = [MANIFEST] + [entry['path'] for entry in manifest['files']]
    # Fixed tar headers make the uncompressed payload reproducible across operating systems.
    with tarfile.open(output, 'w:gz', format=tarfile.PAX_FORMAT) as archive:
        for name in sorted(paths):
            data = (root / name).read_bytes()
            header = tarfile.TarInfo(name)
            header.size, header.mode, header.mtime = len(data), 0o644, 0
            archive.addfile(header, io.BytesIO(data))
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_name(output.name + '.sha256').write_text(digest + '  ' + output.name + '\n', encoding='utf-8')
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['verify', 'pack'])
    parser.add_argument('--root', type=Path, default=ROOT, help='Project root, or an extracted data package root')
    parser.add_argument('--output', type=Path, default=ROOT / 'outputs/packages/devicetruth-knowledge-v1.tar.gz')
    args = parser.parse_args()
    manifest = pack(args.root, args.output) if args.action == 'pack' else verify(args.root)
    print('PASS:', manifest['document_count'], 'documents,', len(manifest['files']), 'verified input files')
    if args.action == 'pack':
        print('Data package:', args.output)


if __name__ == '__main__':
    main()
