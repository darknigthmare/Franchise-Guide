#!/usr/bin/env python3
"""Record or verify SHA-256 for the files tracked in this repository."""
from pathlib import Path
import argparse
import hashlib
import subprocess

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'MANIFEST.sha256'

def tracked_files():
    output = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT)
    return sorted(name for name in output.decode('utf-8').split('\0')
                  if name and name != 'MANIFEST.sha256')

def digest(name):
    return hashlib.sha256((ROOT / name).read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Verify without changing files')
    args = parser.parse_args()
    files = tracked_files()
    if args.check:
        entries = dict(line.split('  ', 1)[::-1] for line in MANIFEST.read_text().splitlines() if line)
        failures = []
        if set(entries) != set(files):
            failures.append('Tracked file set differs from the manifest')
        for name, expected in entries.items():
            if not (ROOT / name).is_file() or digest(name) != expected:
                failures.append(name)
        if failures:
            raise SystemExit('Manifest mismatch:\n' + '\n'.join(failures))
        print(f'{len(entries)} tracked files verified by SHA-256.')
    else:
        MANIFEST.write_text(''.join(f'{digest(name)}  {name}\n' for name in files))
        print(f'Manifest written for {len(files)} tracked files. Stage it before committing.')

if __name__ == '__main__':
    main()
