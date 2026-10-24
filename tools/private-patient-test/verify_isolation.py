"""Verify private image bytes in the private APK, absent from ordinary APK and Git."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

p = argparse.ArgumentParser()
p.add_argument('--package-root', type=Path, required=True)
p.add_argument('--private-apk', type=Path, required=True)
p.add_argument('--ordinary-apk', type=Path, required=True)
p.add_argument('--report', type=Path, required=True)
a = p.parse_args()
repo = Path(__file__).resolve().parents[2]
if a.report.resolve().is_relative_to(repo):
    raise SystemExit('Inspection report must remain outside the checkout.')
inventory = json.loads((a.package_root / 'private-package-inventory.json').read_text(encoding='utf-8'))
hashes = {entry['sha256'] for entry in inventory['images'].values()}
sizes = {Path(entry['source']).stat().st_size for entry in inventory['images'].values()}
report = {}
with zipfile.ZipFile(a.private_apk) as archive:
    for item, entry in inventory['images'].items():
        data = archive.read('assets/private-patient-seed/' + item + '.png')
        assert hashlib.sha256(data).hexdigest() == entry['sha256'], 'Private APK original differs'
        assert hashlib.sha256(Path(entry['source']).read_bytes()).hexdigest() == entry['sha256'], 'Source original changed'
    assert any(b'PrivatePatientTestApplication' in archive.read(name) for name in archive.namelist() if name.endswith('.dex'))
    report['private_apk_exact_originals'] = True
with zipfile.ZipFile(a.ordinary_apk) as archive:
    assert not any('private-patient-seed/' in name for name in archive.namelist()), 'Private asset leaked into ordinary APK'
    for entry in archive.infolist():
        if entry.file_size in sizes:
            assert hashlib.sha256(archive.read(entry)).hexdigest() not in hashes, 'Photo bytes leaked into ordinary APK'
        if entry.filename.endswith('.dex'):
            assert b'PrivatePatientTestApplication' not in archive.read(entry), 'Test-only entry point leaked'
    report['ordinary_apk_no_private_images_or_entry_point'] = True
tracked = subprocess.check_output(['git', 'ls-files', '-z'], cwd=repo).decode().split('\0')
for name in filter(None, tracked):
    file = repo / name
    if file.is_file() and file.stat().st_size in sizes:
        assert hashlib.sha256(file.read_bytes()).hexdigest() not in hashes, 'Photo tracked in Git'
report['git_tracked_photo_hash_matches'] = 0
report['private_apk_sha256'] = hashlib.sha256(a.private_apk.read_bytes()).hexdigest()
report['ordinary_apk_sha256'] = hashlib.sha256(a.ordinary_apk.read_bytes()).hexdigest()
a.report.write_text(json.dumps(report, indent=2), encoding='utf-8')
print(json.dumps(report, indent=2))
