# AAC review after published v1.2.798

Base: published commit 9f27025 (tag v1.2.798). This is prepared source, not a new APK/release.
No versionCode or tag is consumed. Apply only on this base or reconcile with newer local work.

## Scope and findings

All 479 starter IDs and all 650 explicit child references were inspected by the catalog audit.
Visual inspection covered the effective shipped picture set and every newly generated replacement.
The review found wrong body parts, incorrect cropped sheet fragments, missing per-ID artwork mappings,
indistinguishable anatomical locations and mismatches between requested objects and default category art.

Final scope: 181 new illustrations plus 31 already-valid reused pictures, 212 reviewed image files in total, bound to 283 existing AAC IDs.

The authoritative list of corrected IDs, artwork paths and SHA-256 values is:
NovaRehab/data/aac_review_after798_manifest.json.

Corrected groups include:

- Body/pain: abdomen instead of back, fingers instead of wrist, toes instead of hand, thigh instead
  of knee, upper/middle/lower back, upper/forearm/shin, anatomical left/right and upper/lower regions,
  eyes/ears/teeth, pain intensity, pain during movement and time of onset.
- Personal care: clothes, individual garments, pillow, blanket, crutch, wheelchair, positions,
  turning, sitting/lying, help with eating/drinking, diaper, medicine, therapy and urinary symptoms.
- Places: room, bed, bathroom, courtyard, terrace, balcony, restaurant, transport and vending machines.
- Everyday activities: music, television, walking, visits, animals, smoking objects and conversation.
- Environment: separate images for TV/light on and off and window open and closed.
- Food/drink: missing meat/snack/dessert images, dairy drinks, warm/cold water, flavoured/mineral
  water, rosehip tea, no sugar, honey WITH lemon, small amounts, refusal versus inability.
- Time: distinct day/relative-day images and object-specific "when" questions.
- Social/profile: message versus call, unfinished speech, little/strong missing, neutral photo and
  profile placeholders instead of unrelated default artwork.

## Logic and wording

- No new item IDs or duplicate communication nodes. Existing children, placement, action and speech
  timing/navigation code are preserved.
- System statement stroke_info changes from a current-stroke assertion to history of a stroke:
  SL "Prebolela sem možgansko kap." / UK "Я перенесла інсульт." / EN "I have had a stroke."
  This is a patient information statement, not acute symptom assessment or medical instructions.
- Manually edited/locked text is retained by the existing field policy.
- The image merge now also retains images explicitly selected on edited/locked items, even when
  their source is SYSTEM. Custom/patient photos keep their existing protection and display priority.
- The existing fail-closed migration backup is retained with a distinct icon-review-after-798 marker,
  so current 798 data is captured without overwriting its older backup. If backup fails, skip repairs.
- One shared AacReviewedIcons data map is merged into starter/bootstrap maps and the allowlist,
  avoiding inconsistent duplicated mappings and oversized existing Kotlin static initializers.
- Content-hashed new file paths avoid reusing old cached crops. Original old system files are not deleted.

## Privacy and identity

No actual patient photographs, records or credentials were added by this patch. Illustrations of
people are generic. Existing 798 PROSIM/HVALA import, UUID, private originals, gallery and DOM/TRGOVINA
behavior remain in the base unchanged. No arbitrary face is assigned as a real named relative.
Unconfigured real-person photos remain neutral placeholders. Vending step photo placeholders are
explicitly placeholders; real machine instructions/photos must come from the actual machine.

## Image production

New source illustrations were generated individually with the built-in image generation tool.
Common prompt: one clear standalone square AAC illustration, plain white background, large unclipped
subject, no words/numbers/grid/watermark. Meaning-specific prompts identified each object/action;
anatomical prompts specified the patient's left/right and the exact red-highlighted region.
Portraits used for intensity/refusal/ability are generic and do not identify Lana or relatives.
Some initial ambiguous variants were replaced before packaging, including turn directions,
refusal versus inability, missing intensity and warm water versus tea.

Generated originals are retained separately. Bundled copies are technically exported at maximum
512px, metadata stripped and PNG encoded. Already valid reused icons are copied without changing
content. Each final file is SHA-256 checked and decoded before acceptance. Temporary exports and
copies are verified before atomic replacement; incomplete exports are never accepted by the audit.

## Checks

- Catalog: 479 unique IDs; 650 child references resolve; 13 selected communication paths pass.
- Review manifest, shared Kotlin map, asset files and hashes agree.
- All final reviewed image files decode.
- Pixel checks: patient left is viewer right for belly/eye/ear/teeth/body side; back/abdomen levels
  differ vertically; pain-level dots progress 1/2/3/4. These checks supplement visual inspection.
- Existing Java checks: 33 patient image + 27 seed import + 9 migration backup + 15 daily-tree = 84.
- git diff --check.
- Full Android compilation and physical tablet UI/TTS tests remain required. No local Gradle success
  is claimed; the environment cannot download its Gradle distribution.

Run:

    python tests/daily-tree/audit_catalog.py
    python tests/daily-tree/audit_reviewed_pixels.py

The pixel audit requires Pillow in the development environment, not an Android app dependency.

## Release handoff

Do not rebuild/re-publish 1.2.798 / 10986 with changed contents. First incorporate the user's 798 tablet
feedback, inspect current remote tags/version codes, choose a free higher even code, build/test, verify
release signature, and publish with the unchanged normal GitHub Release workflow when proceeding to
that next upgrade. Do not import code from unrelated historical 800-804 branches. No merge to main.
Do not uninstall the app or clear patient data.

## Local release verification correction

Visual review found the supplied reused doctor/nurse files were incorrect cropped family labels (DEDEK/TETA). Before publication, call_doctor, call_nurse and wc_call_nurse were redirected to content-hashed copies of the existing, visually verified person_doctor_v2/person_nurse_v2 symbols. No new artwork was generated. The manifest hashes were updated; 283 mappings and 212 unique reviewed assets remain.

Release target: 1.2.808 / 10996. Existing development names 800-806 are avoided. The explicitly requested 798 personal-image seed and caregiver confirmation entry point remain enabled for this release only.
