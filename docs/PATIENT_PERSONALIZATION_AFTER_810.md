# Personalization after 810 — development handoff

Base: b883cd4b3675e56322b914b317b7088ea36457ec (published v1.2.810).
Prepared version: 1.2.812 / 11000. This is source prepared for review/build,
NOT an already built, signed, published or tablet-verified APK.

## Patient flow

Settings → Patient → OSEBNE IKONE IZ FOTOGRAFIJE IN GLASOVA.

1. Select female/male explicitly. Never infer this setting from a photograph.
2. Import a reference using the Android gallery picker (no broad storage access).
3. Select applicable person/action icons, or all applicable icons.
4. Confirm sending the reference and exact system icons to the configured service.
5. Review each system/personal pair: approve, reject or regenerate.
6. Accepted images use the existing PatientImageStore UUID and private overlay;
   they do not need internet for subsequent display. System assets, placements,
   479 IDs and 650 links are untouched. Fixed row, locked and user-edited items
   are excluded. Existing personal images are replaced only by explicit approval;
   their old original files are retained by the existing store.

The selectable list is intentionally limited to person/action concepts. Object-only
icons are not redrawn just to add a face. The generator attempts to preserve the
system icon's style and meaning, not just the patient's face. Exact visual fidelity
is not guaranteed by a prompt; human review is required, particularly body side,
anatomy, pose, and which person in a multi-person image is the patient.

## Gender and two speech roles

- Existing patient gender picker remains; profile age/birth sentences now use it.
- Gender and voice selections are scoped to the existing patient UUID. Original
  installation gender supplies the initial legacy value; a new patient does not
  inherit another patient's stored voice selections.
- Default patient/guide OpenAI voice IDs: female coral/onyx, male onyx/coral.
  These are auditionable defaults, not a claim that an API voice identifier
  formally guarantees a gender. Each voice can be changed and previewed.
- Main AAC spoken prompts use GUIDE, patient utterances use PATIENT. Cached
  generated speech already includes voice ID, language and speed in its cache key.
- The separate AacCommunicatorActivity retains its existing prompt-display
  behavior; it does not gain a new automatic spoken-question sequence. Its spoken
  patient utterances use PATIENT. Do not introduce overlapping question speech.
- Gender agreement is a reviewed finite SL/UK phrase map, not a universal grammar
  engine. It covers common wishes, tired/hungry/thirsty, post-stroke and profile
  phrases, and selected gendered questions. Manual stored content is not rewritten.
  The item resolver skips protected/manual translation items. Composed text is
  normalized for speech/display; unusual manually written phrases still need review.
- Changing the guide's voice does NOT change the grammatical sex of the person
  being addressed. A male guide still asks a female patient "Kaj bi jedla?".
- Android fallback uses the existing available engine voice. Two distinct gendered
  voices are NOT guaranteed in offline fallback. Do not label this verified without
  listening. Cold requests for the new role voices get the 30s generation budget;
  the legacy speakText entry point keeps its 1.8s default. Icon cache hits remain fast.

## Image service

The previous PR #4 is still an unfinished separate branch. It was inspected, not
merged; no old catalog/profile replacements or its assets were copied over 810.
This patch supplies a smaller compatible client plus a runnable service:

- tools/personalization_service/service.py
- tools/personalization_service/requirements.txt
- tools/personalization_service/test_service.py

The service requires an actual host with HTTPS and an operator-owned OpenAI API
account authorized for image editing. Neither a host nor credentials are available
in this chat. It has NOT been deployed and no real photo/image generation has run.
The existing app's speech credentials are NOT copied into this image client.

On the chosen host, install requirements in an isolated Python environment. Set
OPENAI_API_KEY and a separate random PERSONALIZATION_ACCESS_TOKEN (at least 32
characters) in the service environment; never put either into Git or a public APK.
Run service.py as a managed process. It binds only to 127.0.0.1:8765 (PORT override).
Expose POST /personalize through authenticated HTTPS reverse-proxy infrastructure.
The proxy must retain Content-Length, permit up to 16 MiB, allow about 210 seconds,
and disable request-body logging/persistence. Verify those conditions on the real
host. Provider credentials remain on that host. Enter only the service's own access
token and HTTPS URL in the caregiver's advanced service configuration on the tablet.
Rotate that token as needed. The client stores it under noBackupFilesDir.

Wire contract: multipart metadata JSON + reference PNG + system PNG. It sends
no patient name, birth date, UUID, contacts or history. Reference pixels are sampled,
oriented and re-encoded to strip EXIF. Backend re-encodes inputs too. Responses are
scoped to requestId/itemId, bounded PNG, square, nonempty and with transparency.
Redirects are forbidden on both clients. Candidates are private and unapproved.
Gallery return, generation result and approval all check captured patient identity.
Cancellation/stale UI results cannot activate images. No automatic retry/bulk
acceptance is performed. Provider error bodies are not returned or logged.

Provider API reference checked during implementation:
https://developers.openai.com/api/reference/python/resources/images/methods/edit
Default IMAGE_MODEL is gpt-image-1.5, with two image attachments, high input fidelity,
transparent PNG output. Test availability on the chosen account; do not silently
switch model or pretend a local filter is identity-aware generation.

## Verification completed here

- 22 Java checks: SL/UK gender agreement, retained female/English/neutral phrases,
  patient vs guide role defaults.
- 14 Python tests: multipart, both references, prompt roles/laterality, EXIF removal,
  invalid and opaque images, provider failure, real local HTTP auth/size/path checks,
  request scope and no-store response. Provider generation is mocked in these tests.
- Existing 84 image/seed/backup/tree migration assertions pass.
- Catalog: 479 IDs, 650 links, 13 paths, 283 image mappings/hashes; all 212 reviewed
  images decode and 11 anatomy/intensity checks pass.
- Manifest/settings XML parse. git diff --check passes.
- Gradle distribution download fails here: Network is unreachable. Kotlin/Android
  compilation, 49 Kotlin-dependent checks and the updated 15 scheduler checks
  have NOT run here. Never equate the above checks with a successful Android build.

## Local Codex completion checklist

1. Preserve local work; apply the patch on a separate branch from actual 810.
2. Compile debug and release; run the existing 49 runtime/data checks and updated
   tests/status-speech/run.py, plus all tests above. Review the role API call sites.
3. Verify selection survives gallery activity recreation without binding to a new
   patient. Test cancel before request starts, while uploading, and before approval;
   switching patient, failed generation and app rotation must preserve old images.
4. Test on a configured real image service using an explicitly approved reference;
   review one tired/walk/pain example against its exact system image. No production
   release claim for a disconnected endpoint or mocked-only generation.
5. Listen to patient and guide voices in SL and UK for both gender settings, including
   cold/warm caches, generation beyond 1.8s, offline fallback and rapid selections.
   Confirm no stale cached female phrase/voice for a male profile, no double speech,
   and no grammatical change of the addressee based on guide voice.
6. Verify updater/status speech 810 fixes, silent NE/back, fixed row and existing
   PROSIM/HVALA remain intact. No locked module redesign or main merge.
7. Check latest versions; 812/11000 must still be free before build/publication.
   Use existing Actions signing secrets and verify certificate as 794/808/810.
   No personal photos, generated candidates, service tokens or provider keys in Git,
   APK or public artifacts. No privatePatientTest source set enablement for 812.
8. State separately what is source-ready, compiled, service-configured, generated,
   signed/published and physically verified. Report the missing host/API configuration
   as a real blocker, not a successful feature.

## Local review on 2026-10-27

The generation batch now captures the patient UUID at icon selection and preserves
it through consent, retries and subsequent items. A patient switch stops the queue;
a stale result is deleted before preview. The audio tile path also preserves manual
translation overrides instead of applying gender conversion a second time.

`tests/personalization/run_runtime.py` compiles the actual speech cache/coordinator
and generation queue body with JVM boundary stubs. Its 26 checks cover SL/UK,
female/male patient and guide voices, cold/warm caches, cancellation, destruction
and patient changes between items and before preview. This is not an Android
lifecycle test or a live provider/listening test.

The 84 existing assertions, 49 field-preservation checks, 22 grammar checks,
14 mocked-provider service tests, 15 timing checks, 26 new runtime checks and
catalog/pixel audits passed locally. Local release configuration rejects missing
release-signing environment variables; no signing configuration was changed.

No image-service endpoint/token or local speech configuration was found in the
reviewed NovaRehab documents and two working copies. Real HTTPS deployment,
provider account access, an explicitly approved reference photo and device
listening/UI tests remain outstanding. No claim of working live personalization.

Official API references reviewed on 2026-10-27:
- https://developers.openai.com/api/reference/ruby/resources/images/methods/edit
- https://developers.openai.com/api/docs/guides/text-to-speech
The existing gpt-4o-mini-tts speech endpoint is documented for shutdown on
2027-01-06; migration requires a separate scoped task. No locked speech transport
or updater migration was attempted here.
