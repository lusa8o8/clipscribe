$ErrorActionPreference = "Stop"

& "$PSScriptRoot\android-env.ps1" | Out-Null

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$logPath = Join-Path $repoRoot "crash-log.txt"
$filteredPath = Join-Path $repoRoot "crash-log-filtered.txt"

adb logcat -c
Write-Host "Logcat cleared. Reproduce the crash on your phone now."
Read-Host "After the app crashes, press Enter here to collect logs"

adb logcat -b main,system,crash,events -d -v time > $logPath
Write-Host "Wrote $logPath"
Write-Host ""
Write-Host "Writing filtered crash context to $filteredPath"
Select-String -Path $logPath -Pattern "AndroidRuntime|FATAL EXCEPTION|am_crash|Force finishing|Process com.aistudio.clipscribe|com.aistudio.clipscribe|ClipScribe|MainActivity|AudioCaptureService|MediaProjection|AudioRecord|ForegroundService|SecurityException|RuntimeException|IllegalStateException|Exception" -Context 12,18 |
  Out-File -FilePath $filteredPath -Encoding utf8

Get-Content $filteredPath -Tail 260
