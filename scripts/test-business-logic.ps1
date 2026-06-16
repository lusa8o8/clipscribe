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

& $envInfo.Gradle --no-daemon --console=plain :app:testDebugUnitTest `
  --tests com.example.transcription.RemoteTranscriptionServiceIntegrationTest `
  --tests com.example.storage.TranscriptSaveControllerTest

if ($LASTEXITCODE -ne 0) {
  throw "Focused business-logic tests failed with exit code $LASTEXITCODE."
}
