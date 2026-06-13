#!/usr/bin/env python3
import json
from collections import defaultdict, deque
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
AAC_ITEMS_PATH = ROOT / "NovaRehab" / "data" / "aac_items.json"
DOM_PROFILE_PATH = ROOT / "NovaRehab" / "data" / "profiles" / "dom.json"
OUT_JSON = ROOT / "NovaRehab" / "data" / "aac_patient_test_checklist_v1.json"
OUT_TXT = ROOT / "NovaRehab" / "data" / "aac_patient_test_checklist_print_v1.txt"


def fail_missing() -> None:
    missing = [str(path) for path in (AAC_ITEMS_PATH, DOM_PROFILE_PATH) if not path.exists()]
    message = {
        "ok": False,
        "reason": "runtime AAC data missing",
        "missingFiles": missing,
        "copyFromTablet": [
            "Android/data/com.rehab2/files/NovaRehab/data/aac_items.json",
            "Android/data/com.rehab2/files/NovaRehab/data/profiles/dom.json",
        ],
    }
    print(json.dumps(message, ensure_ascii=False, indent=2))
    raise SystemExit(2)


def load_json(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def item_list(raw):
    if isinstance(raw, list):
        return raw
    if isinstance(raw, dict):
        for key in ("items", "aacItems", "data"):
            value = raw.get(key)
            if isinstance(value, list):
                return value
    raise ValueError("aac_items.json must contain an item list")


def profile_item_ids(raw):
    if isinstance(raw, dict):
        value = raw.get("itemIds")
        if isinstance(value, list):
            return [str(item_id) for item_id in value]
        for key in ("profile", "data"):
            nested = raw.get(key)
            if isinstance(nested, dict) and isinstance(nested.get("itemIds"), list):
                return [str(item_id) for item_id in nested["itemIds"]]
    raise ValueError("dom.json must contain itemIds")


def text_map(item, key):
    value = item.get(key)
    return value if isinstance(value, dict) else {}


def clean(value):
    return str(value).strip() if value is not None else ""


def speech_for(item, language):
    speech_by_language = text_map(item, "speechTextByLanguage")
    label_by_language = text_map(item, "labelByLanguage")
    if language == "sl":
        return (
            clean(speech_by_language.get("sl"))
            or clean(item.get("speakTextSl"))
            or clean(item.get("speechText"))
            or clean(label_by_language.get("sl"))
            or clean(item.get("labelSl"))
        )
    return (
        clean(speech_by_language.get(language))
        or clean(item.get(f"speakText{language.capitalize()}"))
        or clean(label_by_language.get(language))
    )


def label_for(item, language):
    label_by_language = text_map(item, "labelByLanguage")
    if language == "sl":
        return clean(label_by_language.get("sl")) or clean(item.get("labelSl"))
    return clean(label_by_language.get(language)) or clean(item.get(f"label{language.capitalize()}"))


def placements_for(item):
    placements = item.get("placements")
    return placements if isinstance(placements, list) else []


def position_for(item, root_ids):
    fixed = item.get("fixedTopRowPosition")
    if isinstance(fixed, int):
        return fixed
    for placement in placements_for(item):
        if isinstance(placement, dict) and placement.get("pageId") == "page_1":
            position = placement.get("position5x5")
            if isinstance(position, int):
                return position
    item_id = clean(item.get("id"))
    if item_id in root_ids:
        return root_ids.index(item_id) + 1
    return None


def build_checklist(items, root_ids):
    by_id = {clean(item.get("id")): item for item in items if clean(item.get("id"))}
    parent_ids = defaultdict(list)
    for item in items:
        parent_id = clean(item.get("id"))
        for child_id in item.get("children") or []:
            if clean(child_id):
                parent_ids[clean(child_id)].append(parent_id)
        for parent_hint in item.get("visibleUnderIds") or []:
            if clean(parent_hint):
                parent_ids[clean(item.get("id"))].append(clean(parent_hint))

    seen = set()
    queue = deque((item_id, 0) for item_id in root_ids if item_id in by_id)
    ordered_ids = []
    depths = {}
    while queue:
        item_id, depth = queue.popleft()
        if item_id in seen:
            continue
        seen.add(item_id)
        ordered_ids.append(item_id)
        depths[item_id] = depth
        item = by_id[item_id]
        child_ids = [clean(child_id) for child_id in item.get("children") or [] if clean(child_id)]
        visible_child_ids = [
            child_id for child_id, child in by_id.items()
            if item_id in [clean(parent) for parent in child.get("visibleUnderIds") or []]
        ]
        for child_id in child_ids + visible_child_ids:
            if child_id in by_id and child_id not in seen:
                queue.append((child_id, depth + 1))

    rows = []
    for item_id in ordered_ids:
        item = by_id[item_id]
        child_ids = [clean(child_id) for child_id in item.get("children") or [] if clean(child_id)]
        visible_child_ids = [
            child_id for child_id, child in by_id.items()
            if item_id in [clean(parent) for parent in child.get("visibleUnderIds") or []]
        ]
        all_child_ids = sorted(set(child_ids + visible_child_ids), key=lambda value: child_ids.index(value) if value in child_ids else 9999)
        is_branch = bool(all_child_ids)
        depth = depths.get(item_id, 0)
        speech_sl = speech_for(item, "sl")
        speech_uk = speech_for(item, "uk")
        missing = []
        if not speech_sl:
            missing.append("SL govor")
        if not speech_uk:
            missing.append("UA govor")
        if not label_for(item, "uk"):
            missing.append("UA tekst")
        rows.append({
            "id": item_id,
            "position": position_for(item, root_ids),
            "parent": parent_ids.get(item_id, []),
            "depth": depth,
            "labelSl": label_for(item, "sl"),
            "labelUa": label_for(item, "uk"),
            "speechSl": speech_sl,
            "speechUa": speech_uk,
            "speaksOnce": "TEST",
            "opensExpectedBranch": "DA" if is_branch else "NE",
            "terminalSelection": "NE" if is_branch else "DA",
            "buildsSentence": "TEST" if depth > 0 else ("DA" if is_branch else "NE"),
            "missing": missing,
            "extra": [],
            "notes": "",
            "childrenIds": all_child_ids,
        })
    return rows


def write_print(rows):
    lines = []
    for row in rows:
        title = "GLAVNA IKONA" if row["depth"] == 0 else "PODIKONA"
        lines.extend([
            f"{title}: {row['labelSl']} [{row['id']}]",
            f"POZICIJA: {row['position'] or '-'}",
            f"PARENT: {', '.join(row['parent']) if row['parent'] else '-'}",
            f"GLOBINA: {row['depth']}",
            f"SL: {row['labelSl']}",
            f"UA: {row['labelUa'] or '-'}",
            f"SL govor: {row['speechSl'] or '-'}",
            f"UA govor: {row['speechUa'] or '-'}",
            f"Govori enkrat: {row['speaksOnce']}",
            f"Odpira pravo vejo: {row['opensExpectedBranch']}",
            f"Terminalni izbor: {row['terminalSelection']}",
            f"Tvori stavek: {row['buildsSentence']}",
            f"Kaj manjka: {', '.join(row['missing']) if row['missing'] else '-'}",
            f"Kaj je odvec: {', '.join(row['extra']) if row['extra'] else '-'}",
            f"Opombe: {row['notes'] or '-'}",
            "",
        ])
    OUT_TXT.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    if not AAC_ITEMS_PATH.exists() or not DOM_PROFILE_PATH.exists():
        fail_missing()
    items = item_list(load_json(AAC_ITEMS_PATH))
    root_ids = profile_item_ids(load_json(DOM_PROFILE_PATH))[:25]
    rows = build_checklist(items, root_ids)
    result = {
        "metadata": {
            "version": "v1",
            "sourceAacItems": str(AAC_ITEMS_PATH),
            "sourceDomProfile": str(DOM_PROFILE_PATH),
            "runtimeDataUsed": True,
        },
        "summary": {
            "totalItems": len(rows),
            "mainIcons": sum(1 for row in rows if row["depth"] == 0),
            "childItems": sum(1 for row in rows if row["depth"] > 0),
            "terminalItems": sum(1 for row in rows if row["terminalSelection"] == "DA"),
            "branchItems": sum(1 for row in rows if row["opensExpectedBranch"] == "DA"),
        },
        "items": rows,
    }
    OUT_JSON.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    write_print(rows)
    print(json.dumps(result["summary"], ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
