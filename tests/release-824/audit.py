"""Check shipped assets/wiring; render the exact system-image grid for visual review."""
from pathlib import Path
import re, hashlib, json
from PIL import Image, ImageDraw, ImageFont
R=Path(__file__).resolve().parents[2]
J=R/'app/src/main/java/com/rehab2'
layout=(J/'aac/AacHomeLayout.kt').read_text(encoding='utf-8')
def values(key): return re.findall(r'"([^"]+)"',layout.split('val '+key+' = listOf(',1)[1].split(')',1)[0])
ids,labels,uk=values('ids'),values('labels'),values('ukLabels')
assert len(ids)==len(set(ids))==len(labels)==len(uk)==25
expected=['DA','NE','HVALA','RAZUMEM','NE RAZUMEM','TOALETA','BOLI ME','MRZLO','SLABO','UTRUJENA','PIJAČA','HRANA','VROČE','DOBRO','SPATI','DRUŽINA','PRIJATELJI','POČAKAJ','POČASNEJE','HITREJE','RABIM','ŽELIM','POPRAVI','GREMO','JEZNA']
assert labels==expected and 'help' not in ids
paths=dict(re.findall(r'"([^"]+)" to "([^"]+)"',(J/'aac/AacAuditedIcons.kt').read_text(encoding='utf-8')))
new={'faster','correct_message','lets_go','understand','hungry','need'}
sheet=Image.new('RGB',(1200,1350),'#17212b');draw=ImageDraw.Draw(sheet)
fontfile=Path('C:/Windows/Fonts/arialbd.ttf')
font=ImageFont.truetype(str(fontfile) if fontfile.exists() else 'DejaVuSans.ttf',21)
manifest=[]
boot=(J/'aac/AacContentBootstrap.kt').read_text(encoding='utf-8')
for i,(id,label) in enumerate(zip(ids,labels)):
    path='system/home824_'+id+'.png' if id in new else paths[id]
    file=R/'app/src/main/assets/NovaRehab/icons'/path
    im=Image.open(file);im.load();assert min(im.size)>=128 and max(im.size)<=1600
    if id in new: assert Path(path).name in boot
    manifest.append(dict(position=i+1,id=id,sl=label,uk=uk[i],image=path,sha256=hashlib.sha256(file.read_bytes()).hexdigest()))
    im=im.convert('RGBA');im.thumbnail((222,222));x=(i%5)*240;y=(i//5)*270
    tile=Image.new('RGBA',(230,230),'white');tile.alpha_composite(im,((230-im.width)//2,(230-im.height)//2))
    sheet.paste(tile.convert('RGB'),(x+5,y+5));draw.text((x+8,y+241),f'{i+1}. {label}',font=font,fill='white')
assert len({r['sha256'] for r in manifest})==25
main=(J/'MainActivity.kt').read_text(encoding='utf-8');other=(J/'AacCommunicatorActivity.kt').read_text(encoding='utf-8')
assert 'return AacHomeLayout.homeItems(items)' in main and 'return AacHomeLayout.homeItems(items)' in other
assert 'translationTap != mainAacTapId' in main
assert 'binding.view.isEnabled = true' in main
audio=(J/'aac/AacAudioPlayer.kt').read_text(encoding='utf-8')
assert 'prepareAsync()' in audio and 'prepare()' not in audio
assert 'duration in 1 until' not in audio
assert 'if (playAudioFileIfAvailable(cached)) return' in audio
assert 'if (!playAudioFileIfAvailable(legacy)) fallback()' in audio
session=(J/'aac/LiveTestSession.kt').read_text(encoding='utf-8')
assert 'events.size >= 2000' in session and '24 * 60 * 60 * 1000L' in session
assert 'note_observed' in (J/'LiveTestActivity.kt').read_text(encoding='utf-8')
assert not any(s in session for s in ['MediaRecorder','CameraManager','OkHttpClient','writeText','FileOutputStream'])
manifest_xml=(R/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert 'android.permission.CAMERA' in manifest_xml and 'android.permission.RECORD_AUDIO' in manifest_xml
gradle=(R/'app/build.gradle').read_text();assert 'versionCode 11016' in gradle and 'versionName "1.2.828"' in gradle
out=R/'tests/release-824';sheet.save(out/'home-grid.png')
(out/'home-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('PASS: 25 distinct images, exact bilingual grid, runtime wiring, stale response guard, async playback, bounded local observation and release version')
