"""Create an explicitly selected, local-only PNG asset package outside the checkout."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct

parser = argparse.ArgumentParser()
parser.add_argument('--please', type=Path, required=True)
parser.add_argument('--thank-you', type=Path, required=True)
parser.add_argument('--patient-name', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
output = args.output.resolve()
if output.is_relative_to(repo) or output.exists():
    raise SystemExit('Output must be a NEW private directory outside the repository.')
entries = {}
for item, source in [('please', args.please), ('thank_you', args.thank_you)]:
    source = source.resolve(strict=True)
    data = source.read_bytes()
    if source.is_relative_to(repo) or len(data) > 25 * 1024 * 1024:
        raise SystemExit('Source must be outside the repository and at most 25 MiB.')
    if data[:8] != b'\x89PNG\r\n\x1a\n' or data[12:16] != b'IHDR':
        raise SystemExit('Expected an actual PNG file, not only a PNG filename.')
    width, height = struct.unpack('>II', data[16:24])
    if not width or not height:
        raise SystemExit('Invalid PNG dimensions.')
    entries[item] = dict(source=str(source), sha256=hashlib.sha256(data).hexdigest(), width=width, height=height)
pack_id = hashlib.sha256(json.dumps({'version': 1, 'patientName': args.patient_name,
    'images': {key: value['sha256'] for key, value in entries.items()}}, sort_keys=True).encode()).hexdigest()
assets = output / 'assets' / 'private-patient-seed'
assets.mkdir(parents=True)
def escape(value):
    return ''.join(c if c.isascii() and c.isalnum() else '\\u%04x' % ord(c) for c in value)
lines = ['version=1', 'packId=' + pack_id, 'patientName=' + escape(args.patient_name)]
for item, metadata in entries.items():
    shutil.copyfile(metadata['source'], assets / (item + '.png'))
    lines += [item + '.file=' + item + '.png', item + '.sha256=' + metadata['sha256']]
(assets / 'manifest.properties').write_text('\n'.join(lines) + '\n', encoding='ascii')
(output / 'private-package-inventory.json').write_text(json.dumps({'packId': pack_id, 'images': entries}, indent=2), encoding='utf-8')
print('Created local-only package:', output)
