$ErrorActionPreference = "Stop"

$envInfo = & "$PSScriptRoot\android-env.ps1"
$apk = Join-Path $envInfo.RepoRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not (Test-Path $apk)) {
  & "$PSScriptRoot\build-debug.ps1"
}

& adb install -r $apk
