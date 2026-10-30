"""Full icon export/graph/gender regression audit; requires Pillow, no Android or network."""
from pathlib import Path
import json, hashlib, re, runpy
from PIL import Image
R=Path(__file__).resolve().parents[2]
M=json.loads((R/'NovaRehab/data/aac_full_icon_review_manifest.json').read_text(encoding="utf-8"))
I=R/'app/src/main/assets/NovaRehab/icons'
S=(R/'app/src/main/java/com/rehab2/aac/AacAuditedIcons.kt').read_text(encoding="utf-8")
rows={r['id']:r for r in M['entries']}
assert len(rows)==479 and sum(len(r['children']) for r in rows.values())==650
assert not M['pendingGeneration']
assert dict(re.findall(r'"([^"]+)" to "([^"]+)"',S))=={k:r['imagePath'] for k,r in rows.items()}
graph=runpy.run_path(str(R/'tests/daily-tree/audit_catalog.py'))
assert graph['edges']=={k:r['children'] for k,r in rows.items()}
visited=set()
def visit(k,active):
 assert k not in active,('Cycle',k)
 if k in visited:return
 for child in rows[k]['children']:visit(child,active|{k})
 visited.add(k)
for k in rows:visit(k,set())
paths={}
for row in rows.values():
 for v in [row]+list(row.get('genderVariants',{}).values()):
  p=v['imagePath'];assert hashlib.sha256((I/p).read_bytes()).hexdigest()==v['sha256'],p
  paths[p]=v
for p in paths:
 with Image.open(I/p) as im:
  im.load();assert 0<max(im.size)<=1600,(p,im.size)
assert sum('genderVariants' in r for r in rows.values())==98
for r in rows.values():
 if 'genderVariants' in r:
  assert set(r['genderVariants'])=={'female','male'}
  assert r['genderVariants']['female']['imagePath']==r['imagePath']
  assert r['genderVariants']['female']['sha256']!=r['genderVariants']['male']['sha256']
exports={a['imagePath'] for a in M['assets']}
assert exports=={'system/'+p.name for p in (I/'system').glob('full816_*.png')}
assert len(exports)==M['generatedAssetCount']==113
assert exports<=paths.keys()
hashes=[hashlib.sha256((I/p).read_bytes()).hexdigest() for p in M['galleryFiles']]
assert len(hashes)==len(set(hashes))
assert set(M['galleryFiles'])|set(M['suppressedDuplicatePaths'])==set(paths)

def load(k,sex):
 row=rows[k];p=row.get('genderVariants',{}).get(sex,row)['imagePath']
 return Image.open(I/p).convert('RGBA')
def red(k,sex):
 im=load(k,sex);sx=sy=w=0
 for y in range(im.height):
  for x in range(im.width):
   r,g,b,a=im.getpixel((x,y));v=max(0,r-max(g,b)-65) if r>130 and a>180 else 0
   sx+=x*v;sy+=y*v;w+=v
 assert w>1000,(k,sex)
 return sx/w/im.width,sy/w/im.height
checks=0
for sex in ['female','male']:
 for l,r in [('belly_left','belly_right'),('eye_left','eye_right'),('ear_left','ear_right'),('tooth_left','tooth_right'),('left_side','right_side')]:
  lx,_=red(l,sex);rx,_=red(r,sex)
  assert lx>.52 and rx<.48,(sex,l,lx,r,rx)
  checks+=1
 ys=[red(k,sex)[1] for k in ['back_upper','back_middle','back_lower']]
 assert ys==sorted(ys) and len(set(ys))==3,(sex,ys)
 assert red('belly_upper',sex)[1]<red('belly_lower',sex)[1]
 checks+=2
 for k,count in [('pain_light',1),('pain_medium',2),('pain_strong',3),('pain_very_strong',4),('slower_little',1),('slower_much',3)]:
  if k not in rows:continue
  im=load(k,sex);pixels=set()
  for y in range(int(im.height*.78),im.height):
   for x in range(im.width):
    r,g,b,a=im.getpixel((x,y));hi=max(r,g,b);lo=min(r,g,b)
    color=(b>r and b>g) if k.startswith('slower') else (r>g and r>b)
    if a>180 and hi>110 and hi-lo>hi*.5 and color:pixels.add((x,y))
  sizes=[]
  while pixels:
   todo=[pixels.pop()];n=0;xs=[];ys=[]
   while todo:
    x,y=todo.pop();n+=1;xs.append(x);ys.append(y)
    for p in [(x-1,y),(x+1,y),(x,y-1),(x,y+1)]:
     if p in pixels:pixels.remove(p);todo.append(p)
   # Ignore tiny anti-alias fragments; blue shirt regions are not round markers.
   if n>300 and (not k.startswith('slower') or .7 < (max(xs)-min(xs)+1)/(max(ys)-min(ys)+1) < 1.3):sizes.append(n)
  assert len(sizes)==count,(sex,k,sizes,count)
  checks+=1
print(f'PASS: 479 IDs, 650 unchanged links, {len(paths)} decoded images, 98 gender pairs, 113 exports, {checks} anatomy/intensity checks; gallery hashes unique')
