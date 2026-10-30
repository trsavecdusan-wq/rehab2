# Verification of NovaRehab 1.2.816 / 11004

Base: published 812, commit 72ade87ebab8214ed4ba0d4f9b792744a1e4d56d.
Isolated branch: codex/v1.2.816-full-icons. No main merge or tablet operation.

The complete extracted delivery passed all 20 SHA-256 entries before git apply --check and git apply. The binary patch supplies all 113 PNG exports. No 814 branch/tag or occupied 816 release existed when remote refs and releases were checked.

Local verification (assembleDebug: BUILD SUCCESSFUL, 36 tasks):
- Full icon audit: 479 IDs, 650 unchanged edges, 13 communication paths, 417 decoded images, 98 gender pairs, 113 exports, 26 anatomy/intensity checks; unique gallery hashes.
- Existing catalog and pixel checks: 283 reviewed mappings; 212 decoded images and 11 pixel checks.
- Patient images: 33 checks; seed import: 27; migration backup: 9.
- Speech grammar: 31; system image protection/gender/contact aliases: 21; tree migration: 15.
- Actual compiled debug classes with JVM org.json 20240303: 58 field-preservation checks and all 479 effective paths/sources, UK repairs, 98 pairs, fixed row and WC position 6.
- Speech runtime: 26 checks; status speech callbacks: 15.
- New APK audit checks every public bundled asset byte-for-byte, all 479 CSV rows, every reviewed image hash, all 98 pairs, absence of private seed assets and test application class.
- Full icon audit repeated after making its UTF-8 decoding explicit for Windows.

Refresh review: MainActivity.onResume calls resetMainAacRootOnResume, reloads JSON and rebinds icons using AacPatientImages.resolve. AacCommunicatorActivity compares the patient voice settings stamp and refreshes its items. Selection reads the current gender and gives personal bindings priority; it does not write gender bindings to the patient index. Existing user edit flags and locked items are preserved by mergeUserFields and recognized by the loader.

Changed areas: starter/bootstrap image mapping, gender selection and contact photo lookup, curated gallery, field protection, SL/UK grammar, system images, review manifest/CSV, tests and version metadata. Workflow, updater, application ID, signing configuration and unrelated modules are unchanged.

Release signing and download verification are recorded in the GitHub Release notes after verification. Baseline 812 signer SHA-256: b253edef6ab6de917e70961c65d4d8b3cbc4727cbab109c6e9319e521583c51b.

Not a tablet test: recognition of illustrations, retained real personal photos on the tablet, both audible voices, date/weather and normal AAC speech still need the requested device check. The generator endpoint remains outside this task.
