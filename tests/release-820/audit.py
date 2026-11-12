"""Release-specific static checks. These do not replace tablet/UI/audio testing."""
from pathlib import Path
import re, hashlib, struct
R=Path(__file__).resolve().parents[2]
main=(R/'app/src/main/java/com/rehab2/MainActivity.kt').read_text(encoding='utf-8-sig')
boot=(R/'app/src/main/java/com/rehab2/aac/AacContentBootstrap.kt').read_text(encoding='utf-8-sig')
settings=(R/'app/src/main/java/com/rehab2/SettingsActivity.kt').read_text(encoding='utf-8-sig')
wc=main.split('"wc" to mapOf(',1)[1].split(')',1)[0]
slots=dict((key,int(n)+1) for key,n in re.findall(r'"([^"]+)" to ([0-9]+)',wc))
assert slots['wc_pee']==11 and slots['nurse_help']==13 and slots['wc_poop']==15,slots
hashes=[]
for name in ['wc_pee','wc_poop','please_more','please_cigarettes','please_buy_cigarettes','please_blanket','please_blanket_laundry','please_blanket_bed']:
    filename='aac_'+name+'.png'
    data=(R/'app/src/main/assets/NovaRehab/icons/system'/filename).read_bytes()
    assert data[:8]==bytes([137,80,78,71,13,10,26,10])
    width,height=struct.unpack('>II',data[16:24])
    assert width>0 and height>0
    assert '"'+filename+'"' in boot,('Missing runtime seed',filename)
    hashes.append(hashlib.sha256(data).hexdigest())
assert len(hashes)==len(set(hashes))
assert 'val previousPage = mainAacHistory.removeLast()' in main
assert 'currentMainAacConversationItems = previousPage.conversationItems' in main
assert 'currentMainAacModifierItemsByGroup.putAll(previousPage.modifierItemsByGroup)' in main
assert 'listOf("wc_diaper_change", "pain", "please").mapNotNull' in main
assert 'val showHelpShortcuts = pendingMainAacHelpShortcuts' in main
assert 'listOf("both", "symbol", "text")' in settings
assert 'Patterns.EMAIL_ADDRESS.matcher(address).matches()' in settings
for field in ['One','Two','Three']:
    assert 'findViewById<EditText>(R.id.editAiEmailRecipient'+field+')' in settings
print('PASS: 11/13/15 slots, eight distinct seeded PNG assets, navigation snapshots, help shortcuts and settings wiring (static)')