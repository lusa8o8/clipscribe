$ErrorActionPreference = "Stop"

Set-Location (Split-Path $PSScriptRoot -Parent)

node --test cloudflare-worker/test/transcribe-endpoint.test.mjs
if ($LASTEXITCODE -ne 0) {
  throw "Worker integration tests failed with exit code $LASTEXITCODE."
}
