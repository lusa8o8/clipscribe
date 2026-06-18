# ClipScribe

ClipScribe is an Android MVP for capturing the last chunk of playback audio from another app, then turning it into a transcript with a floating bubble workflow.

Launch privacy posture:

- ClipScribe sends captured audio clips to a cloud transcription endpoint.
- Users should not use the MVP for private, confidential, regulated, or sensitive audio.
- Saved transcript history is only available after sign-in and is still part of the MVP launch work.
- The native on-device Whisper path is not production-ready in this build.

Current status:

- Floating bubble capture flow works on-device
- MediaProjection consent and playback capture work
- Frozen audio preparation works
- Debug/local transcript flow exists for development
- Firebase auth is wired in for beta identity
- Cloud transcription works end-to-end through Cloudflare Workers AI

## Current product flow

1. Open ClipScribe
2. Tap `Start Capture Mode`
3. Approve screen/audio capture for the current session
4. Open YouTube, Spotify, a lecture, or another supported source
5. Tap the floating bubble when you hear something worth saving
6. ClipScribe freezes the rolling buffer, prepares audio, uploads the clip for cloud transcription, and shows a transcript result

## Local build

This repo can be built from the command line. Android Studio is not required.

Prerequisites:

- Android SDK installed locally
- A JDK available locally
- `adb` available through the Android SDK
- A connected Android device with USB debugging enabled

Useful scripts:

- `.\scripts\build-debug.ps1`
- `.\scripts\install-debug.ps1`
- `.\scripts\logcat-clipscribe.ps1`
- `.\scripts\pull-app-debug-log.ps1`
- `.\scripts\test-android-integration.ps1`
- `.\scripts\test-worker.ps1`

Build and install:

```powershell
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1
```

## Firebase setup

Firebase is currently used for beta identity. Anonymous auth is required for basic use; Google sign-in is used when users want saved transcript history.

Required steps:

1. Create a Firebase project
2. Add the Android app package `com.aistudio.clipscribe.vfqmza`
3. Place `google-services.json` at `app/google-services.json`
4. Enable `Authentication -> Sign-in method -> Anonymous`
5. Enable `Authentication -> Sign-in method -> Google`
6. Make sure the Firebase project has a Web client ID in the generated `google-services.json`

Expected app behavior after setup:

- The top badge shows `Cloud transcription - Beta account` for anonymous users
- The top badge shows `Cloud transcription - Signed in` for Google users
- In Developer Mode, `Auth` shows the current Firebase user
- In Developer Mode, `Auth token` eventually shows `Ready`

## User-facing privacy copy

Keep launch copy plain and consistent:

- Audio is sent to the cloud for transcription.
- Do not capture private or sensitive audio.
- Saved transcript history is only available for Google signed-in users.
- Debug/local transcription is not the production path.

## Debugging

Pull the app debug log:

```powershell
.\scripts\pull-app-debug-log.ps1
```

This writes `clipscribe-debug-device.log` in the repo root.

The app also writes an internal debug log file at:

- `files/clipscribe-debug.log`

Current debug logging covers:

- Firebase anonymous auth
- MediaProjection consent handoff
- Capture service startup
- AudioRecord startup
- MediaProjection shutdown

## Cloud endpoint

This repo includes a direct Cloudflare Worker under `cloudflare-worker/`.

Current contract:

- App sends raw WAV bytes as the request body
- App sends Firebase ID token as `Authorization: Bearer <token>`
- Worker verifies the Firebase ID token against Google's Secure Token signing certs
- Worker returns transcript text as plain text
- Worker may return `X-ClipScribe-Transcription-Duration-Ms` when available
- Signed-in transcript persistence uses `POST /transcripts`, `GET /transcripts`, and `DELETE /transcripts/:id`
- Transcript persistence requires the same Firebase bearer token and stores rows by Firebase `uid`

Current provider modes:

- `mock`: local development and contract testing
- `cloudflare-binding`: uses a real Workers AI binding exposed as `env.AI`

Recommended Workers AI model:

- `@cf/openai/whisper-large-v3-turbo`

Alternative cheaper / smaller English-only model:

- `@cf/openai/whisper-tiny-en`

The Android app will only use the remote path when `TRANSCRIPTION_ENDPOINT_URL` is set in the environment at build time.

Required worker variables:

- `FIREBASE_PROJECT_ID`
- `FREE_TIER_DAILY_TRANSCRIPT_LIMIT`
- `TRANSCRIPTION_PROVIDER`
- `TRANSCRIPTION_MODEL`
- `TRANSCRIPTION_LANGUAGE` (optional)

Optional worker bindings:

- `USAGE_KV`: Cloudflare KV namespace used for soft per-user daily transcript counting

Required transcript persistence binding:

- `TRANSCRIPTS_DB`: Cloudflare D1 database for signed-in saved transcripts

Create/apply the D1 schema:

```powershell
wrangler d1 create clipscribe_transcripts
wrangler d1 migrations apply clipscribe_transcripts
```

After creating the database, replace `REPLACE_WITH_D1_DATABASE_ID` in `cloudflare-worker/wrangler.jsonc`.

## Focused integration tests

Instead of running the full test surface for every backend change, use the targeted business-path checks:

```powershell
.\scripts\test-android-integration.ps1
.\scripts\test-worker.ps1
```

These currently cover:

- prepared audio + Firebase token -> remote request contract
- remote success response -> transcript mapping
- auth failure -> user-facing auth error path
- worker request validation and success/error HTTP behavior
- Firebase token verification gate at the worker boundary
- per-user free-tier quota enforcement behavior
- Workers AI binding payload shape for the supported Whisper models
- signed-in transcript save/list/delete ownership behavior

## Architecture notes

Main pieces in the current MVP:

- `capture/`: MediaProjection, playback capture, rolling buffer, freeze flow
- `overlay/`: floating bubble service and tap behavior
- `transcription/`: prepared audio, cloud transcription flow, debug/local fallback, result holders
- `auth/`: Firebase auth state
- `ui/`: Compose screens and diagnostics

Important current limitation:

- The native Whisper JNI implementation is still a stub for debug-mode transcript output
- Anonymous users reset on uninstall or app-data wipe

## Next backend step

The next planned step is:

- Provision the Cloudflare D1 database and deploy the Worker with `TRANSCRIPTS_DB`
- Keep anonymous transcripts ephemeral in the MVP
- Keep soft usage limits for verified Firebase users

Planned provider path:

- Firebase Auth for identity
- Cloudflare D1 or a small cloud datastore for saved transcripts
- Cloudflare Workers AI Whisper for free-tier cloud transcription

## Website & Landing Page (Vercel)

The `website/` directory contains the polished, responsive static landing page, privacy policy, and the downloadable Android package.

### Features
- **Landing Page (`index.html`):** Marketing copy, interactive CSS phone mockup, pricing tables, waitlist signups, and FAQ.
- **Privacy Page (`privacy.html`):** Privacy policy for store compliance.
- **Vercel Config (`vercel.json`):** Configures clean URLs and forces the correct MIME type (`application/vnd.android.package-archive`) and content-disposition headers for `app-debug.apk` to ensure seamless downloads.

### Deployment Steps
1. Log in to [Vercel](https://vercel.com) and click **Add New > Project**.
2. Import your GitHub repository (`lusa8o8/clipscribe`).
3. Under **Project Settings**, set the **Root Directory** to `website`.
4. Leave the Build Command and Output Directory as default (since it is a static HTML project).
5. Click **Deploy**. Vercel will build and serve your static landing page, including your compiled APK download link!

