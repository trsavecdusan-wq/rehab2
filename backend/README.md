# NovaRehab observation backend (protocol 1)

Deploy this code separately from the Android APK. No hosting or provider account was supplied for release 1.2.828. The repository does **not** contain a deployed endpoint, provider secret, or production device credential. HTTP contract tests use simulated provider replies; they do not prove microphone/camera operation.

## Required configuration

1. One Node.js 22+ process (Dockerfile uses Node 24), with public **HTTPS** termination, a trusted certificate and outbound HTTPS access to `api.openai.com`. Forward the root URL to port 8080. Do not expose port 8080 directly to the internet. Disable request-body and Authorization logging at the proxy. Use one replica: session tokens and quotas are in memory.
2. A funded OpenAI API project. Put its restricted provider key in the **server secret** `OPENAI_API_KEY`, never in the APK or device-key field.
3. Generate a separate random device credential of at least 32 bytes. Put only its SHA-256 hex digest in server secret `DEVICE_TOKEN_SHA256`. Comma-separated digests support multiple devices; each device needs its own credential. Removing a digest revokes new device sessions; restart the server to revoke already-issued session tokens immediately.
4. Optional server variables: `PORT=8080`, `TRANSCRIBE_MODEL=gpt-transcribe`, `VISION_MODEL=gpt-4.1-mini`.
5. Enter the HTTPS root URL and the **raw device credential** in NovaRehab → Nastavitve → PREIZKUS, NOVA MREŽA IN GOVOR → MIKROFON, KAMERA IN INDIVIDUALNI PROFIL. The app encrypts the device credential with Android Keystore in no-backup storage. Android 6+ is required for the protected backend connection.

Generate credentials on the administrator's machine (do not commit or post the output publicly):

```sh
node -e "const c=require('node:crypto');const t=c.randomBytes(32).toString('hex');console.log('DEVICE CREDENTIAL:',t);console.log('SERVER SHA256:',c.createHash('sha256').update(t).digest('hex'))"
docker build -t novarehab-observation backend
# Supply OPENAI_API_KEY and DEVICE_TOKEN_SHA256 through the host's secret manager.
docker run --rm -p 127.0.0.1:8080:8080 --env OPENAI_API_KEY --env DEVICE_TOKEN_SHA256 novarehab-observation
```

Hosting and API usage are separate paid resources; a ChatGPT subscription alone is not the server configuration. Exact charge depends on the chosen host and actual audio/image usage. Set a project spending limit before use. Consult [current API pricing](https://developers.openai.com/api/docs/pricing). No paid hosting was purchased by this change.

## API and provider choice

Official documentation reviewed on 2026-11-21: [file transcription](https://developers.openai.com/api/docs/guides/speech-to-text), [image inputs in Responses](https://developers.openai.com/api/docs/guides/images-vision), [GPT-4.1 mini](https://developers.openai.com/api/docs/models/gpt-4.1-mini). The transcription guide recommends `gpt-transcribe`; the server submits bounded WAV files with Slovenian and Ukrainian hints. Image analysis uses three chronological JPEG frames and constrained structured output. Model selection remains server-side and health checks verify model access; only successful media requests verify a particular capture path.

All routes require a bearer credential:

| Route | Credential | Purpose |
|---|---|---|
| `POST /v1/health` | device | Checks protocol and both provider model endpoints; no personal media |
| `POST /v1/sessions` | device | Explicit consent, microphone/camera flags, `sl`/`uk`, optional `replace:true` to recover a lost session response |
| `POST /v1/sessions/{id}/audio` | session | 16 kHz mono PCM WAV, at most 12 seconds, `startMs`, `endMs`, `appSpeechOverlap:false` |
| `POST /v1/sessions/{id}/camera` | session | Three bounded JPEGs, `startMs`, `endMs` |
| `DELETE /v1/sessions/{id}` | session or owner device | Aborts outstanding provider request and removes session state |

The session credential expires after 30 minutes. One active session per device, one in-flight analysis per session, at most 240 analyses per session and 420 per device per hour. Quotas reset on server restart; configure provider/project limits as a second bound. The client has no media backlog and drops busy/failed chunks. Switching patient or stopping invalidates callbacks. Replacement aborts the previous session, avoiding duplicated captures after lost responses.

## Privacy and interpretation

- Raw audio/images exist in server RAM only during processing; no database, file logging, or report storage. Response storage is disabled for Responses requests. This does not override OpenAI's own applicable [data controls and retention](https://developers.openai.com/api/docs/guides/your-data).
- The app keeps raw media in RAM by default. A separate explicit option allows private local storage for at most 24 hours and 20 MB per patient; expired files are purged when the application next runs and during subsequent capture. It never uploads a retained backlog.
- The timeline is memory-only, bounded to 2000 events / 24 hours. Reports can be explicitly saved for 7 or 30 days (at most 30 reports), exported through Android's document picker, or deleted. Patient profile evidence persists until corrected/deleted. The profile is not automatically sent to the provider.
- The default speaker is **unknown**. Every transcript/visual result is uncertain and pending confirmation. The app excludes an entire audio segment if application speech or its echo tail overlapped it. This also excludes patient speech that occurred simultaneously; it is not speaker diarization.
- Sparse camera frames can miss brief movements. The schema permits only visible movement categories; no inferred pain, emotion, understanding, agreement, identity, or gesture meaning. A confirmed individual sign is stored only in the current patient profile with its source and session evidence.
- Suggestions currently use transparent local rules for water/coffee/toilet topics, AI transcript evidence, navigation and existing catalogue paths. They show at most three proposals and include confirmed profile notes for caregiver review. They are not automatic AI adaptation. Confirmation opens the existing editor; it never rewrites the 25-icon first page.
- The legacy keyword and AI option files preserve existing user values. They do not become implicit consent for new capture. New startup/capture/network/storage consent is separate and patient-scoped. Legacy background-listening values are retained for compatibility but background capture is not implemented or permitted by this release.
- Legacy unassigned observation files are left untouched. New legacy-admin observations use patient-specific subdirectories; unassigned old observations are not silently assigned to a patient.

## Installation verification

Run `node --test backend/test.mjs`. These tests use a synthetic provider, never claim real transcription. After deployment, use **PREVERI POVEZAVO**, then explicitly start a consented session on the tablet. Verify a real 12-second microphone segment and real front-camera frames separately. Check Android privacy indicators, visible STOP, background/resume, revoked permissions, patient switch, app speech exclusion and offline behavior. No physical Android device was attached during preparation; none of these device-level outcomes are claimed as completed.
