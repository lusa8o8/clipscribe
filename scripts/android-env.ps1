$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

$androidSdk = $env:ANDROID_HOME
if (-not $androidSdk) {
  $androidSdk = $env:ANDROID_SDK_ROOT
}
if (-not $androidSdk) {
  $androidSdk = Join-Path $env:LOCALAPPDATA "Android\sdk"
}
if (-not (Test-Path $androidSdk)) {
  throw "Android SDK not found. Expected ANDROID_HOME/ANDROID_SDK_ROOT or $androidSdk"
}

$jbr = "C:\Program Files\Android\Android Studio\jbr"
if (Test-Path (Join-Path $jbr "bin\java.exe")) {
  $env:JAVA_HOME = $jbr
} elseif (-not $env:JAVA_HOME) {
  throw "JAVA_HOME is not set and Android Studio JBR was not found."
}

$env:ANDROID_HOME = $androidSdk
$env:ANDROID_SDK_ROOT = $androidSdk
$env:PATH = "$env:JAVA_HOME\bin;$androidSdk\platform-tools;$androidSdk\cmdline-tools\latest\bin;$env:PATH"

$gradleBat = Get-ChildItem -Path "$env:USERPROFILE\.gradle\wrapper\dists" -Filter "gradle.bat" -Recurse -ErrorAction SilentlyContinue |
  Where-Object { $_.FullName -match "gradle-8\.13" } |
  Select-Object -First 1 -ExpandProperty FullName

if (-not $gradleBat) {
  $gradleCommand = Get-Command gradle -ErrorAction SilentlyContinue
  if ($gradleCommand) {
    $gradleBat = $gradleCommand.Source
  }
}

if (-not $gradleBat) {
  throw "Gradle not found. Install Gradle CLI or add a Gradle wrapper."
}

[pscustomobject]@{
  RepoRoot = $repoRoot
  AndroidSdk = $androidSdk
  JavaHome = $env:JAVA_HOME
  Gradle = $gradleBat
}
