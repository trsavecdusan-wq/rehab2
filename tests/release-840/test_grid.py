"""Checks the shipped v4 definition without any private PNGs or external service."""
import json
from pathlib import Path
r=Path(__file__).resolve().parents[2]
d=json.loads((r/'app/src/main/assets/lana-grid-v4.json').read_text(encoding='utf8'))
n={x['id']:x for x in d['nodes']}
assert len(n)==72
expected=['yes','no','thank_you','understand','dont_understand','wc','pain','feeling','hungry','thirsty','v4_try_myself','come_to_me','dont_know_problem','v4_look','v4_my_things','family_group','friends_group','activity_group','slower','faster','need','i_want','correct_message','lets_go','wait']
assert d['home']==expected and len(set(expected))==25
active=set();seen=set()
def walk(i):
 assert i not in active,('cycle',i)
 if i in seen:return
 active.add(i)
 for c in n[i]['children']:
  assert c in n,(i,c)
  walk(c)
 active.remove(i);seen.add(i)
for i in n:walk(i)
for row in n.values():
 assert row['labelUk'] and row['reason']
 if row['speech_mode']=='navigate_only':assert row['speechSl']==row['speechUk']==''
 else:assert row['speechSl'] and row['speechUk']
assert n['lets_go']['sha256']=='328c19aedc80b93032a9af785850e4e12380593b48938fc2575caa9bb2a0af84'
assert n['home']['speechSl']=='Želim, da gremo nazaj.'
assert set(n['family_group']['children'])==set(n['friends_group']['children'])
assert not {'choose_myself','look_at_me','shop','wc_itching','tv','call'} & set(n)
assert n['tired']['children']==['sleepy']
assert len(n['feeling']['children'])==11
print('PASS: 72 IDs, 25 exact slots, acyclic shared links, explicit SL/UK navigation and immutable GREMO hash')
