<#
.SYNOPSIS
  Build Roon Shield and sideload it onto an Nvidia Shield over wireless ADB.

.DESCRIPTION
  1. Connects to the Shield via ADB over the network (adb connect <ip>:<port>).
  2. Builds the debug APK (./gradlew assembleDebug).
  3. Installs it and launches the leanback activity.

  On the Shield first: Settings -> Device Preferences -> About -> tap "Build" 7x to unlock
  Developer options, then enable "Network debugging". Note the Shield's IP address.

.EXAMPLE
  ./scripts/deploy.ps1 -ShieldIp 192.168.1.42
  ./scripts/deploy.ps1 -ShieldIp 192.168.1.42 -Port 5555 -SkipBuild
#>
param(
    [Parameter(Mandatory = $true)] [string] $ShieldIp,
    [int] $Port = 5555,
    [switch] $SkipBuild,
    [string] $Adb = "C:\Android\platform-tools\adb.exe"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "com.roonshield"
$target = "${ShieldIp}:${Port}"

Write-Host "==> Connecting to Shield at $target" -ForegroundColor Cyan
& $Adb connect $target

if (-not $SkipBuild) {
    Write-Host "==> Building debug APK" -ForegroundColor Cyan
    & (Join-Path $root "gradlew.bat") --no-daemon assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
}

if (-not (Test-Path $apk)) { throw "APK not found at $apk (build first, or drop -SkipBuild)" }

Write-Host "==> Installing $apk" -ForegroundColor Cyan
& $Adb -s $target install -r -g $apk
if ($LASTEXITCODE -ne 0) { throw "adb install failed" }

Write-Host "==> Launching $pkg" -ForegroundColor Cyan
& $Adb -s $target shell monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 | Out-Null

Write-Host "Done. Roon Shield is installed and launched on $ShieldIp." -ForegroundColor Green
Write-Host "First run: in any Roon remote, open Settings -> Extensions and Enable 'Roon Shield'." -ForegroundColor Yellow
