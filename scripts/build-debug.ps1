$ErrorActionPreference = "Stop"

$envInfo = & "$PSScriptRoot\android-env.ps1"
Set-Location $envInfo.RepoRoot

$envFile = Join-Path $envInfo.RepoRoot ".env"
if (Test-Path $envFile) {
  Get-Content $envFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') {
      return
    }
    $parts = $_ -split '=', 2
    $name = $parts[0].Trim()
    $value = $parts[1].Trim()
    if ($name) {
      [System.Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
  }
}

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
