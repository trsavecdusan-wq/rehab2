"""Validate shipped alpha assets and render contrasting-background samples."""
from pathlib import Path
import hashlib,json
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[2]
icons=root/'app/src/main/assets/NovaRehab/icons'
manifest=json.loads((root/'NovaRehab/data/alpha832_manifest.json').read_text(encoding='utf-8'))
previous=json.loads((root/'app/src/main/assets/NovaRehab/data/system_icon_previous_hashes.json').read_text())
files=list(icons.rglob('*.png'))
assert len(files)==1007
for path in files:
    im=Image.open(path).convert('RGBA');lo,hi=im.getchannel('A').getextrema()
    assert lo==0 and hi>=240,(path,lo,hi)
for row in manifest['entries']:
    path=icons/row['path']
    assert hashlib.sha256(path.read_bytes()).hexdigest()==row['sha256'],path
    assert row['previousSha256'] in previous[path.name]
    assert row['rgbPreserved'] or (row['method']=='reviewed-system-motif-repair' and row.get('source'))
assert len(manifest['entries'])==763 and manifest['uniqueCandidates']==742
assert sum(row['rgbPreserved'] for row in manifest['entries'])==742
plate=Image.open(icons/'system/home824_hungry.png').convert('RGBA')
assert plate.getpixel((int(plate.width*.5),int(plate.height*.13)))[3]>240,'White plate lost'
preview=Image.new('RGB',(1200,800),'white');draw=ImageDraw.Draw(preview)
names=['home824_hungry.png','home824_lets_go.png','daily_dont_understand_798.png',
       'review_bed_f302e2c2.png','review_tooth_add82ec1.png','review_pain_very_strong_ec6c4eca.png']
for row,bg in enumerate(['#263746','#e5edf4']):
    draw.rectangle((0,row*400,1200,row*400+399),fill=bg)
    for col,name in enumerate(names):
        im=Image.open(icons/'system'/name).convert('RGBA');im.thumbnail((192,330))
        preview.paste(im,(col*200,row*400+20),im)
        draw.text((col*200+4,row*400+365),name[:24],fill='white' if row==0 else 'black')
preview.save(root/'tests/alpha832/background-review.png')
print('PASS: 1007 alpha PNGs; 763 revision hashes; 742 alpha-only changes; 21 reviewed motif repairs; opaque white plate; two background preview')
