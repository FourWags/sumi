#!/usr/bin/env python3
"""Restore omitted binary build assets from the recorded source archive, offline."""
import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tempfile
import zipfile


def digest(data):
    return hashlib.sha256(data).hexdigest()


def restore(root, archive):
    manifest = json.loads((root / 'source-build-assets.json').read_text())
    if digest(archive.read_bytes()) != manifest['archiveSha256']:
        raise ValueError('Source archive hash differs from the recorded archive. Nothing was written.')
    pending = []
    with zipfile.ZipFile(archive) as source:
        for entry in manifest['assets']:
            relative = PurePosixPath(entry['path'])
            if relative.is_absolute() or '..' in relative.parts or not relative.parts or relative.parts[0] == '.git':
                raise ValueError('Unsafe asset path; nothing was written.')
            target = root.joinpath(*relative.parts)
            for part in [target, *target.parents]:
                if part == root.parent:
                    break
                if part.is_symlink():
                    raise ValueError('Asset path contains a symlink; nothing was written.')
            data = source.read(entry['archiveEntry'])
            if len(data) != entry['size'] or digest(data) != entry['sha256']:
                raise ValueError('Asset hash differs from the recorded source; nothing was written.')
            if target.exists():
                if not target.is_file() or digest(target.read_bytes()) != entry['sha256']:
                    raise ValueError('An existing asset differs; it was preserved. Nothing was written.')
            else:
                pending.append((target, data))
    # Validate the complete archive and every destination before writing any file.
    for target, data in pending:
        target.parent.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=target.parent, prefix='.restore-asset-', delete=False) as output:
            temporary = Path(output.name)
            try:
                output.write(data)
                output.flush()
                os.fsync(output.fileno())
            except BaseException:
                temporary.unlink(missing_ok=True)
                raise
        temporary.chmod(0o644)
        os.replace(temporary, target)
    return len(pending), len(manifest['assets'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--archive', type=Path, required=True, help='Retained source ZIP named in source-build-assets.json')
    args = parser.parse_args()
    restored, total = restore(Path(__file__).resolve().parents[1], args.archive)
    print(f'Verified {total} binary build assets; restored {restored}. No network request was made.')
