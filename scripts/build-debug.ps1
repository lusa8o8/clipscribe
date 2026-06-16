$ErrorActionPreference = "Stop"

$envInfo = & "$PSScriptRoot\android-env.ps1"
Set-Location $envInfo.RepoRoot

& $envInfo.Gradle --no-daemon --console=plain --stacktrace :app:assembleDebug
if ($LASTEXITCODE -ne 0) {
  throw "Gradle build failed with exit code $LASTEXITCODE."
}

$apk = Join-Path $envInfo.RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
  Write-Host "Built APK: $apk"
} else {
  throw "Build finished but APK was not found at $apk"
}
