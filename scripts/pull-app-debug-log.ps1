$ErrorActionPreference = "Stop"

& "$PSScriptRoot\android-env.ps1" | Out-Null

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$outPath = Join-Path $repoRoot "clipscribe-debug-device.log"

adb exec-out run-as com.aistudio.clipscribe.vfqmza cat files/clipscribe-debug.log > $outPath
Write-Host "Wrote $outPath"
Get-Content $outPath -Tail 120
