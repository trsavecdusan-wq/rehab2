# Release Signing

This project uses a signed release APK pipeline for GitHub Releases.

## Required GitHub Secrets

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Important Rules

- Never commit the keystore file.
- Never commit passwords or aliases in code.
- The keystore must stay backed up safely outside the repository.
- Losing the keystore breaks future update continuity for already installed release APKs.
- All future signed releases must use the same keystore.

## Versioning

- `versionCode` must increase for every future release.
- `versionName` should stay aligned with the release tag when possible.
- First signed release baseline:
  - `versionCode = 1`
  - `versionName = "1.0.0"`

## GitHub Release Output

- Tag format: `v1.0.0`, `v1.0.1`, and so on.
- Stable release asset name: `rehab-release.apk`

## Tablet Install Note

- If the currently installed tablet build was signed with the debug key, the first move to the signed release APK may require uninstall and reinstall.
- After that, future signed release APKs should install over each other normally, as long as the same signing key is used.
