# AAC 1.2.798: daily communication test

Base: codex/v1.2.796-aac-test, 0acd3f20c142ebd690d876e6c7705ee1e3592159.
Application: com.rehab2; versionCode 10986; versionName 1.2.798.

## Concrete fixes

- LJUDJE opens DRUZINA, PRIJATELJI, ZNANCI, O MENI, social expressions and POGRESAM.
  Existing person IDs remain unchanged. People are nested under their existing family/friends groups.
- RADA BI offers drinks, food, rest, a walk and the shop before the existing smoking choices.
- KAM ZELIM includes TRGOVINA using the existing shop ID.
- Ten individual, text-free system illustrations replace incorrect crops or ambiguous shared pictures:
  feeling, care, health, shop, family_group, friends_group, call, i_want,
  place_group, dont_understand. The latter has no coloured circle behind the question mark.
- New versioned image paths cause bootstrap to seed the new files rather than reusing old cached crops.
- Recognised old/incomplete system branch lists are migrated on startup. Unknown custom links are
  retained; branches explicitly marked userEdited/locked are not changed by this new migration.
- The ten new system pictures are generic illustrations, not photographs of the patient's relatives.
- The explicitly consented public test package additionally includes the two existing personal
  PROSIM/HVALA originals. Only version 1.2.798 / 10986 includes this source set in debug/release.
  The existing import mechanism requires caregiver confirmation before binding to the retained UUID;
  existing valid personal pictures are preserved. Later versions do not inherit this package automatically.
- Before startup migration, an immutable snapshot of existing AAC/profile data and affected preferences
  is saved in the patient's private store. Backup failure skips the startup migration without writing.
- NE in a submenu returns one level without speaking, including the fixed row on both AAC screens.
  NE at the root keeps its normal speech behavior.

## Preserved

Five fixed icons and WC slot 6; all 479 IDs; application ID; existing profile and item JSON formats;
patient UUID and private image store; speech texts and submenu links; OTA/rollback; status/GPS; power; radio;
companion; signing and GitHub Actions workflow. No merge into main.

## Validation

- Java migration tests: 15 checks (legacy, partial, repeated, empty, edited, unknown links,
  independent patient categories, no mutation of caller lists).
- Static catalog audit: 479 unique IDs, 650 child references resolve, 13 selected communication
  paths pass, all professional image mapping files exist. This is not an Android UI test.
- Visual contact sheet of the ten new illustrations inspected.
- git diff --check.
- Local patient-store tests: 33 checks; confirmed seed import: 27 checks; migration backup: 9 checks.
- The original patch's Android build was unverified at handoff. Local assembleDebug and signed
  Actions verification are performed separately by the receiving agent before publication.

Run local checks from the repository root:

    java -m jdk.compiler/com.sun.tools.javac.Main -d /tmp/aac-daily-tests app/src/main/java/com/rehab2/aac/AacDailyTreeUpgrade.java tests/daily-tree/AacDailyTreeUpgradeTest.java
    java -cp /tmp/aac-daily-tests AacDailyTreeUpgradeTest
    python tests/daily-tree/audit_catalog.py

## Tablet acceptance (still required)

Back up the current patient data before upgrading. Upgrade in place, without uninstalling or clearing data.
Check the fixed row and silent NE/back; LJUDJE > DRUZINA > ZANA; LJUDJE > PRIJATELJI > DUSAN;
RADA BI > PIJACA > VODA; HRANA > SADJE > JABOLKO; KAM ZELIM > TRGOVINA;
NEGA and ZDRAVJE pictures; restart and DOM/TRGOVINA patient image persistence; gallery import.
Some other catalog pictures may still be poor crops: this release repairs the ten inspected targets,
not all 479 pictures. Physical device speech, layout, and learned recognition remain unverified.

## Image provenance

The ten daily_*_798.png files were generated with the built-in image generation tool.
Prompt set: one standalone square AAC icon per meaning, realistic simplified 3D illustration,
plain white background, large centred subjects, no text/numbers/border/grid; subjects are a woman
expressing feelings, assisted hair care, a first-aid bag and stethoscope, a grocery shop, family,
friends, a phone, choosing a drink/food, destinations and a puzzled woman. Output files are bundled
under app/src/main/assets/NovaRehab/icons/system and referenced by starter content and bootstrap.
