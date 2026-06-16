$ErrorActionPreference = "Stop"

& "$PSScriptRoot\android-env.ps1" | Out-Null

$pidOutput = adb shell pidof -s com.aistudio.clipscribe.vfqmza
$pidText = if ($null -eq $pidOutput) { "" } else { $pidOutput.Trim() }
if ($pidText) {
  adb logcat --pid=$pidText
} else {
  Write-Host "ClipScribe is not currently running. Showing recent crash logs instead."
  adb logcat -b crash -d -v time
  adb logcat -d -v time |
    Select-String "AndroidRuntime|FATAL EXCEPTION|Exception|SecurityException|RuntimeException|com.aistudio.clipscribe|ClipScribe|AudioCaptureService|MediaProjection|AudioRecord"
}
