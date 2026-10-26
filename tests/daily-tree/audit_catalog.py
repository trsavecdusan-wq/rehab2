"""Static audit of the shipped starter graph; does not replace Android UI testing."""
from pathlib import Path
import re
from collections import Counter

ROOT = Path(__file__).resolve().parents[2]
s = (ROOT / 'app/src/main/java/com/rehab2/aac/AacStarterContentV1.kt').read_text(encoding='utf-8-sig')

def quoted(value):
    return re.findall(r'"([^"\n]*)"', value)

items = {}
for line in s.splitlines():
    match = re.search(r'\bstarter\("([^"]+)"', line)
    if not match:
        continue
    key = match[1]
    assert key not in items, f'Duplicate item ID: {key}'
    items[key] = line

edges = {}
for key, line in items.items():
    match = re.search(r'children = (\w+)\(([^)]*)\)', line)
    if not match:
        edges[key] = []
        continue
    name, args = match.groups()
    if name == 'listOf':
        children = quoted(args)
    else:
        body = re.search(r'private fun ' + name + r'\([^\n]*\{(.*?)\n    }', s, re.S)
        assert body, (key, name)
        children = quoted(body[1])
        if 'teaId' in body[1]:
            tea_id = quoted(args)[0]
            children = [v.replace('${teaId}', tea_id) for v in children]
    assert len(children) == len(set(children)), f'Duplicate child under {key}'
    assert key not in children, f'Self link: {key}'
    assert all(v in items for v in children), (key, [v for v in children if v not in items])
    edges[key] = children

paths = [
    ['people', 'family_group', 'person_zana'],
    ['people', 'family_group', 'person_sergej'],
    ['people', 'friends_group', 'person_dusan'],
    ['people', 'friends_group', 'person_julija'],
    ['place_group', 'shop'], ['i_want', 'shop'],
    ['i_want', 'thirsty', 'water'], ['i_want', 'hungry', 'hungry_fruit', 'hungry_apple'],
    ['thirsty', 'tea', 'tea_mint', 'tea_mint_honey'],
    ['hungry', 'hungry_main_dish', 'hungry_soup', 'hungry_beef_soup'],
    ['care', 'washing_help'], ['health', 'doctor'], ['feeling', 'tired'],
]
for path in paths:
    for parent, child in zip(path, path[1:]):
        assert child in edges[parent], f'Broken communication path: {path}'

# Every new picture must actually ship; all professional map entries are also checked.
bootstrap = (ROOT / 'app/src/main/java/com/rehab2/aac/AacContentBootstrap.kt').read_text(encoding='utf-8-sig')
block = bootstrap.split('private val PROFESSIONAL_SYSTEM_ICON_BY_STARTER_ID = mapOf(', 1)[1].split('\n    )', 1)[0]
for key, value in re.findall(r'"([^"]+)" to "([^"]+)"', block):
    assert (ROOT / 'app/src/main/assets/NovaRehab/icons' / value).is_file(), (key, value)
print(f'{len(items)} unique AAC IDs; {sum(map(len, edges.values()))} child links resolve; {len(paths)} communication paths pass; professional image files exist')
