# Status date/weather speech after 808

Base: v1.2.808, 8d769c772503729dec61d88ee7757c74bf709bc5.
Prepared version: 1.2.810 / 10998, com.rehab2.

Tablet report: long press displays text but is silent; other tested icons speak.
The exact tablet failure is NOT reproduced here; no tablet logs are available.

## Source findings and correction

- Only the date had a click listener. The weather had none; neither had an
  explicit long-click listener. Both now support click and consumed long click.
- Generated audio was irrevocably rejected after 1800ms when Android TTS fallback
  began. This can explain cached icons working while fresh status speech fails.
  Status speech now has its own 30s generation budget. Ordinary icon/sentence
  calls keep their existing 1800ms budget, cache, voices and fallback.
- The weather deadline was 1800ms although connection and reading each allow
  1800ms. It is now 4500ms. Timeout/failure still speaks the base orientation
  sentence, without inventing weather. Slow generation can still fall back after
  30s; unavailable API plus unavailable Android TTS cannot produce speech.
- Repeated status requests invalidate older pending weather callbacks. Leaving
  the activity or selecting an AAC tile also invalidates pending weather work.
  Existing player request serials continue to reject stale audio generation.
- Version 810 does not match the existing explicit 808-only personal test asset
  build gate. No renewed public inclusion of patient photographs is introduced.
  Already imported patient data is not deleted or migrated by this patch.

## Completed checks in this environment

- Patient image tests 33, seed tests 27, backup tests 9, tree migration tests 15:
  84 assertions passed. These do not test Android speech playback.
- Catalog: 479 IDs, 650 links, 13 paths, 283 mappings and hashes valid.
- Pixels: all 212 reviewed images decode; 11 anatomy/intensity checks pass.
- git diff --check passes.
- Gradle cannot download its distribution (Network is unreachable). No Kotlin/
  Android compilation, signed APK, publication or tablet test was completed here.

## Required checks before publication

1. Check remote versions and local changes; never overwrite an existing tag or
   discard newer work. 810/10998 was selected with 808 as latest published release.
2. Run assembleDebug and existing tests, including the 49 Kotlin-dependent checks
   run for 808. Compile both debug and release because private source sets differ.
3. Test cold (uncached) status speech, including generation completing after 1.8s
   and before 30s; confirm exactly one utterance. Verify a cache hit too.
4. Click and long press date and weather. Release after long press must not cause
   a duplicate. Confirm selected language and enabled orientation settings.
5. Delay weather beyond 1.8s but below 4.5s: include its sentence. Failure/offline:
   speak base date/time, no fabricated weather and no late duplicate.
6. Repeated presses, AAC icon pressed during weather fetch, and leaving the
   activity must not allow an old weather callback to override the newer action.
7. If still silent, inspect AAC audio diagnostics for generation timeout/error,
   TTS readiness/language/result, request serial and speech callback. Visible TTS
   text is a diagnostic Toast, not evidence that audio was heard. Do not expose
   API credentials or full private configuration in the report.
8. Verify other AAC speech/navigation, imported PROSIM/HVALA, UUID and manual edits.
9. Use existing GitHub Actions signing secrets. Cert SHA256 must remain
   b253edef6ab6de917e70961c65d4d8b3cbc4727cbab109c6e9319e521583c51b.
   No new key, main merge, data clearing, or remote tablet installation.
10. For normal OTA delivery publish a normal release with rehab-release.apk,
    even code, correct version metadata and existing updater conventions. Report
    Actions/build/signature separately from physical tablet playback verification.

## Local verification additions

`tests/status-speech/run.py` compiles the actual generation and orientation callback bodies with a deterministic JVM clock and fake transport/player/UI. It checks instant and delayed generation, the unchanged ordinary 1.8s budget, the status 30s budget, one-shot fallback, unavailable generation, cancellation, weather success at 2.5s, timeout at 4.5s, offline response, repeated requests, invalidation on AAC input/pause, and weather disabled. Listener wiring is checked separately in source. This is not a real cache, Android lifecycle, speech API or acoustic playback test.

Run with Python after the Kotlin 1.9.24 compiler is cached by the existing Android build. It uses no API credentials and creates only temporary JVM test files. Physical tablet tests in the checklist above remain required; successful compilation or a displayed Toast cannot confirm audible speech.
