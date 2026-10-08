#!/usr/bin/env python3
"""Extract the factory PICO OS 5.13.7 files this device tree needs (not stored in Git).

Usage: extract-files.py STOCK_DIR
  STOCK_DIR holds raw ext4 images of the factory 5.13.7 partitions read from the headset:
  system.img, product.img, vendor.img, odm.img.

Each file listed in proprietary-files.json is dumped with debugfs and checked against its
SHA-256. An entry with a "patch" field is a factory file we changed: the bsdiff patch
(patches/<name>.bsdiff in this repository) is applied with bspatch and the result is checked
against "patched_sha256". vendor/product/odm images are linked into stock/ (BoardConfig.mk
PICO_STOCK_DIR). Requires debugfs (e2fsprogs) and, for patched entries, bspatch.
"""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent


def sha256(path):
    digest = hashlib.sha256()
    with open(path, 'rb') as reader:
        for block in iter(lambda: reader.read(1 << 20), b''):
            digest.update(block)
    return digest.hexdigest()


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    stock = Path(sys.argv[1]).resolve()
    spec = json.loads((HERE / 'proprietary-files.json').read_text())
    for entry in spec['files']:
        image = stock / (entry['partition'] + '.img')
        if not image.is_file():
            raise SystemExit('Missing factory image: ' + str(image))
        out = HERE / entry['dest']
        out.parent.mkdir(parents=True, exist_ok=True)
        temporary = out.with_name(out.name + '.tmp')
        subprocess.run(['debugfs', '-R', 'dump ' + entry['path'] + ' ' + str(temporary), str(image)],
                       check=True, capture_output=True)
        if not temporary.is_file() or sha256(temporary) != entry['sha256']:
            temporary.unlink(missing_ok=True)
            raise SystemExit('Unexpected factory %s file: %s (not PICO OS %s?)'
                             % (entry['partition'], entry['path'], spec['firmware']))
        if 'patch' in entry:
            patched = out.with_name(out.name + '.patched')
            subprocess.run(['bspatch', str(temporary), str(patched), str(HERE / entry['patch'])], check=True)
            temporary.unlink()
            if sha256(patched) != entry['patched_sha256']:
                patched.unlink()
                raise SystemExit('Patched file differs from the expected result: ' + entry['dest'])
            temporary = patched
        temporary.replace(out)
        print('extracted', entry['dest'])
    links = HERE / 'stock'
    links.mkdir(exist_ok=True)
    for partition in ['vendor', 'product', 'odm']:
        image = stock / (partition + '.img')
        if not image.is_file():
            raise SystemExit('Missing factory image: ' + str(image))
        link = links / image.name
        if link.is_symlink() or link.exists():
            link.unlink()
        link.symlink_to(image)
        print('linked', 'stock/' + image.name)


if __name__ == '__main__':
    main()
