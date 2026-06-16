$ErrorActionPreference = "Stop"

$envInfo = & "$PSScriptRoot\android-env.ps1"
Set-Location $envInfo.RepoRoot

& $envInfo.Gradle --no-daemon --console=plain :app:testDebugUnitTest --tests com.example.transcription.RemoteTranscriptionServiceIntegrationTest
if ($LASTEXITCODE -ne 0) {
  throw "Android integration tests failed with exit code $LASTEXITCODE."
}
