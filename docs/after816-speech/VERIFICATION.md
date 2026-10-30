# NovaRehab 818 speech release verification

Version: com.rehab2 / 1.2.818 / 11006. Isolated branch codex/v1.2.818-speech-lifecycle, based on 6cc48e1, retaining the verification follow-up to published 816 (2466ddc).

All four delivery SHA-256 entries passed before git apply --check and git apply. Remote tags/releases confirmed 818 was unused.

Changed application files: app/build.gradle (version only), AacAudioPlayer.kt, and new AacSpeechRequestPolicy.java. Added delivery report/instructions, SpeechRequestPolicyTest.java, and tts_lifecycle.py. No asset, private data, patient storage, manual settings, MainActivity, radio/GPS/status module, updater, workflow, application ID or signing configuration changes.

Local verification:
- assembleDebug completed successfully against Android SDK 34. Callback overrides match actual android.jar interfaces. No new compiler warning reported for AacAudioPlayer.
- 27 actual-body TTS lifecycle checks; 12 real Java callback policy checks; 15 status speech callback checks; 26 speech cache/personalization runtime checks.
- 33 personal image checks; 27 seed checks; 9 migration backup checks; 31 grammar checks; 21 image protection/gender/contact checks; 15 tree migration checks.
- 58 upgrade field preservation checks and all 479 actual compiled runtime mappings, UK repairs, gender pairs, fixed row and toilet slot 6.
- Full icon audit: 479 IDs, 650 links, 13 paths, 417 decoded images, 98 gender pairs, 113 exports, 26 anatomy/intensity checks.
- Debug APK audit: 1002 public assets match, all reviewed image hashes/pairs verified, no private seed or private application entry point.

Reference 816 was downloaded again: SHA-256 6a09cabcbe4bf9a68770cf4e35d294fe0d4b296f0be98dd5c764c4ca9feb259d, signer SHA-256 b253edef6ab6de917e70961c65d4d8b3cbc4727cbab109c6e9319e521583c51b. Its final tag workflow 41137325990 rebuilt the same source after the earlier branch artifact, explaining its different APK digest. All reference asset checks pass. Verification of 818 must wait until its tag workflow has completed, then download the final published APK and compare it against the advertised digest and this signer.

JVM tests use fake Android engines/schedulers, not actual playback. No device was installed or uninstalled. Tablet acceptance still requires first tap after opening, short/long date/weather press, rapid different icons, interruption, both languages/voices, fallback without web speech, closure while waiting, and preservation of real photos/settings.
