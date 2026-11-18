param([string[]]$Tasks = @(':app:assembleDebug', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'JDK 17 or newer is required. Set JAVA_HOME and add its bin directory to PATH.' }
if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT -and -not (Test-Path -LiteralPath 'local.properties')) {
    throw 'Install Android SDK 36 using Android Studio, then set ANDROID_HOME or create local.properties with sdk.dir.'
}
$toolsDirectory = Join-Path $projectRoot '.tools'
$gradleVersion = '8.13'
$gradleRoot = Join-Path $toolsDirectory "gradle-$gradleVersion"
$gradleBat = Join-Path $gradleRoot 'bin\gradle.bat'
if (-not (Test-Path -LiteralPath $gradleBat)) {
    New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
    $archivePath = Join-Path $toolsDirectory "gradle-$gradleVersion-bin.zip"
    $downloadUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    Invoke-WebRequest -Uri $downloadUrl -OutFile $archivePath
    $expectedHash = (Invoke-WebRequest -Uri "$downloadUrl.sha256").Content.Trim().ToLowerInvariant()
    $actualHash = (Get-FileHash -LiteralPath $archivePath -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($expectedHash -ne $actualHash) { throw 'Gradle checksum mismatch. The archive was not extracted.' }
    Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsDirectory -Force
}
& $gradleBat @Tasks --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
