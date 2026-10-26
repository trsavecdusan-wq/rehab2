"""Check image files, anatomical red highlight direction and pain-level dots.

These are technical/content regression checks, not a patient-recognition or UI test.
"""
from pathlib import Path
import json
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
manifest = json.loads((ROOT / 'NovaRehab/data/aac_review_after798_manifest.json').read_text(encoding='utf-8-sig'))
rows = {r['id']: r for r in manifest['entries']}

def load(key):
    return Image.open(ROOT / 'app/src/main/assets/NovaRehab/icons' / rows[key]['imagePath']).convert('RGB')

def red_center(key):
    im = load(key)
    sx = sy = weight = 0
    for y in range(im.height):
        for x in range(im.width):
            r, g, b = im.getpixel((x, y))
            w = max(0, r - max(g, b) - 65)
            if r > 130 and w:
                weight += w; sx += x * w; sy += y * w
    assert weight > 1000, ('Missing red highlight', key)
    return sx / weight / im.width, sy / weight / im.height

unique = {}
for row in rows.values():
    unique[row['imagePath']] = row
for path, row in unique.items():
    im = load(row['id'])
    assert im.width > 0 and im.height > 0
    assert max(im.size) <= 1600, ('Oversized bundled image', path, im.size)

checks = 0
for left, right in [('belly_left','belly_right'), ('eye_left','eye_right'), ('ear_left','ear_right'),
                    ('tooth_left','tooth_right'), ('left_side','right_side')]:
    if left not in rows or right not in rows:
        continue
    lx, _ = red_center(left); rx, _ = red_center(right)
    assert lx > .52 and rx < .48, ('Patient left/right swapped or ambiguous', left, lx, right, rx)
    checks += 1
if all(k in rows for k in ('back_upper','back_middle','back_lower')):
    ys = [red_center(k)[1] for k in ('back_upper','back_middle','back_lower')]
    assert ys[0] < ys[1] < ys[2], ('Back levels not distinct', ys)
    checks += 1
if all(k in rows for k in ('belly_upper','belly_lower')):
    assert red_center('belly_upper')[1] < red_center('belly_lower')[1], 'Belly levels not distinct'
    checks += 1

# Count separated coloured dots only in the bottom band, not the face.
for key, expected in [('pain_light',1),('pain_medium',2),('pain_strong',3),('pain_very_strong',4)]:
    if key not in rows:
        continue
    im = load(key); pixels = set()
    for y in range(int(im.height * .78), im.height):
        for x in range(im.width):
            r,g,b = im.getpixel((x,y)); hi=max(r,g,b); lo=min(r,g,b)
            if hi > 110 and hi-lo > hi * .50 and r > g and r > b:
                pixels.add((x,y))
    components = []
    while pixels:
        p=pixels.pop(); stack=[p]; count=0
        while stack:
            x,y=stack.pop(); count+=1
            for q in ((x-1,y),(x+1,y),(x,y-1),(x,y+1)):
                if q in pixels: pixels.remove(q); stack.append(q)
        if count > 80: components.append(count)
    assert len(components) == expected, ('Wrong pain-level dot count', key, components)
    checks += 1
print(f'{len(unique)} image files decode; {checks} anatomical/intensity pixel checks pass')
