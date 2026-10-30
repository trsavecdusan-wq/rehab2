"""Verify all reviewed icons and exact public asset contents of a built APK."""
from pathlib import Path
import csv, hashlib, io, json, sys, zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
manifest = json.loads((ROOT / 'NovaRehab/data/aac_full_icon_review_manifest.json').read_text(encoding='utf-8'))
rows = manifest['entries']
with (ROOT / 'docs/icon-review-816/PREGLED_VSEH_479_IKON.csv').open(encoding='utf-8-sig', newline='') as stream:
    review = list(csv.DictReader(stream, delimiter=';'))
assert len(review) == len(rows) == 479
by_id = {row['id']: row for row in rows}
assert {row['ID'] for row in review} == set(by_id)
for row in review:
    entry = by_id[row['ID']]
    assert row['SLIKA'] == entry['imagePath'], row['ID']
    assert [s.strip() for s in row['PODIKONE'].split(',') if s.strip()] == entry['children'], row['ID']
    assert (row['SPOLNI RAZLICICI'] == 'DA') == ('genderVariants' in entry), row['ID']

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert len(names) == len(set(names)), 'Duplicate APK members'
    expected = {'assets/' + p.relative_to(ROOT / 'app/src/main/assets').as_posix(): p
                for p in (ROOT / 'app/src/main/assets').rglob('*') if p.is_file()}
    actual = {name for name in names if name.startswith('assets/') and not name.endswith('/')}
    generated_profiles = {'assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'}
    assert actual - generated_profiles == set(expected), ('Unexpected/missing assets', (actual - generated_profiles) ^ set(expected))
    for name, source in expected.items():
        assert archive.read(name) == source.read_bytes(), name
    checked = set()
    pairs = 0
    for row in rows:
        variants = row.get('genderVariants', {})
        if variants:
            assert set(variants) == {'female', 'male'}
            pairs += 1
        for entry in [row] + list(variants.values()):
            name = 'assets/NovaRehab/icons/' + entry['imagePath']
            data = archive.read(name)
            assert hashlib.sha256(data).hexdigest() == entry['sha256'], name
            if name not in checked:
                with Image.open(io.BytesIO(data)) as picture:
                    picture.load()
                checked.add(name)
    assert pairs == 98 and len(checked) == 417
    assert not any(name.lower().endswith(('.jks', '.keystore', '.zip', '.env')) for name in names)
    for name in names:
        if name.endswith('.dex'):
            data = archive.read(name)
            assert b'Lcom/rehab2/patient/PrivatePatientTestApplication;' not in data
    private = ROOT / 'app/src/consentedPatientTest798/assets'
    private_paths = {'assets/' + p.relative_to(private).as_posix() for p in private.rglob('*') if p.is_file()}
    assert not (actual & private_paths), 'Private seed assets included'
print(f'PASS: APK {apk.name}: 479 CSV rows, all {len(expected)} public assets byte-identical, 417 decoded reviewed images, 98 complete gender pairs; no private seed/entry point or key archives')
print('SHA256=' + hashlib.sha256(apk.read_bytes()).hexdigest())
