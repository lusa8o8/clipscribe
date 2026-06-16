# ClipScribe

ClipScribe is an Android MVP for capturing the last chunk of playback audio from another app, then turning it into a transcript with a floating bubble workflow.

Current status:

- Floating bubble capture flow works on-device
- MediaProjection consent and playback capture work
- Frozen audio preparation works
- Debug transcript flow works end-to-end
- Firebase anonymous auth is wired in for beta identity
- Cloud transcription backend is not connected yet

## Current product flow

1. Open ClipScribe
2. Tap `Start Capture Mode`
3. Approve screen/audio capture for the current session
4. Open YouTube, Spotify, a lecture, or another supported source
5. Tap the floating bubble when you hear something worth saving
6. ClipScribe freezes the rolling buffer, prepares audio, and shows a transcript result

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

Build and install:

```powershell
.\scripts\build-debug.ps1
.\scripts\install-debug.ps1
```

## Firebase setup

Firebase is currently used for anonymous beta auth only.

Required steps:

1. Create a Firebase project
2. Add the Android app package `com.aistudio.clipscribe.vfqmza`
3. Place `google-services.json` at `app/google-services.json`
4. Enable `Authentication -> Sign-in method -> Anonymous`

Expected app behavior after setup:

- The top badge shows `On-device • Beta account`
- In Developer Mode, `Auth` shows an anonymous user
- In Developer Mode, `Auth token` eventually shows `Ready`

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

## Architecture notes

Main pieces in the current MVP:

- `capture/`: MediaProjection, playback capture, rolling buffer, freeze flow
- `overlay/`: floating bubble service and tap behavior
- `transcription/`: prepared audio, debug/local transcription flow, result holders
- `auth/`: Firebase anonymous auth state
- `ui/`: Compose screens and diagnostics

Important current limitation:

- The native Whisper JNI implementation is still a stub for debug-mode transcript output
- Cloud transcription has not been connected yet

## Next backend step

The next planned step is:

- Android app sends prepared WAV audio plus Firebase ID token to a backend
- Backend verifies identity and forwards audio to a transcription provider
- Transcript is returned to the app and optionally stored with user feedback metadata

Planned provider path:

- Firebase Auth for identity
- Supabase for data and edge functions
- Cloudflare Workers AI Whisper for free-tier cloud transcription
