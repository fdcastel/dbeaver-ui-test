<#
.SYNOPSIS
    Single-command entry point for the DBeaver UI test suite.
.DESCRIPTION
    Loads local config, verifies prerequisites, resets the Firebird test database,
    runs the Maven/Tycho UI test reactor, and collects artifacts.
.PARAMETER SkipDbReset
    Skip the Firebird database reset step.
.PARAMETER SkipPrereqCheck
    Skip the prerequisite verification step.
.PARAMETER TestFilter
    Run only tests matching this pattern (passed as -Dtest=...).
.PARAMETER MavenVerbose
    Enable verbose Maven output.
#>
[CmdletBinding()]
param(
    [switch]$SkipDbReset,
    [switch]$SkipPrereqCheck,
    [string]$TestFilter,
    [switch]$MavenVerbose
)

$ErrorActionPreference = "Stop"
$scriptDir = $PSScriptRoot
$startTime = Get-Date

Write-Host "===== DBeaver UI Test Suite =====" -ForegroundColor Cyan
Write-Host "Started: $startTime"
Write-Host ""

# --- Load properties ---
$propsFile = Join-Path $scriptDir "ui-test.local.properties"
if (-not (Test-Path $propsFile)) {
    Write-Error "Local properties file not found: $propsFile`nCopy ui-test.local.properties.example to ui-test.local.properties and edit it."
    exit 1
}

$props = @{}
Get-Content $propsFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
    $key, $value = $_ -split '=', 2
    $props[$key.Trim()] = $value.Trim()
}

$dbeaverRepo = $props['DBEAVER_REPO']
$dbeaverP2 = $props['DBEAVER_P2_REPO']
$artifactsDir = $props['ARTIFACTS_DIR']
if (-not $artifactsDir) { $artifactsDir = Join-Path $scriptDir "artifacts" }

# --- Verify prerequisites ---
if (-not $SkipPrereqCheck) {
    Write-Host "--- Verifying prerequisites ---" -ForegroundColor Yellow
    & "$scriptDir\scripts\verify-prerequisites.ps1" -PropertiesFile "$propsFile"
    if ($LASTEXITCODE -ne 0) { exit 1 }
    Write-Host ""
}

# --- Reset Firebird database ---
if (-not $SkipDbReset) {
    Write-Host "--- Resetting Firebird test database ---" -ForegroundColor Yellow
    & "$scriptDir\scripts\reset-firebird-db.ps1" -PropertiesFile "$propsFile"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Database reset failed."
        exit 1
    }
    Write-Host ""
}

# --- Prepare artifacts directory ---
if (Test-Path $artifactsDir) {
    Remove-Item "$artifactsDir\*" -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $artifactsDir -Force | Out-Null

# --- Build P2 URL ---
$p2Url = "file:///" + ($dbeaverP2 -replace '\\', '/')
Write-Host "DBeaver P2 repo: $p2Url"

# --- Run Maven ---
Write-Host ""
Write-Host "--- Running UI tests ---" -ForegroundColor Yellow

$mvnArgs = @(
    "clean", "verify",
    "-f", (Join-Path $scriptDir "pom.xml"),
    "-Ddbeaver.p2.url=$p2Url",
    "-Dfirebird.host=$($props['FIREBIRD_HOST'])",
    "-Dfirebird.port=$($props['FIREBIRD_PORT'])",
    "-Dfirebird.database=$($props['FIREBIRD_DATABASE'])",
    "-Dfirebird.user=$($props['FIREBIRD_USER'])",
    "-Dfirebird.password=$($props['FIREBIRD_PASSWORD'])"
)

if ($TestFilter) {
    $mvnArgs += "-Dtest=$TestFilter"
}

if ($MavenVerbose) {
    $mvnArgs += "-X"
}

Write-Host "mvn $($mvnArgs -join ' ')"
Write-Host ""

& mvn @mvnArgs
$mvnExitCode = $LASTEXITCODE

# --- Collect artifacts ---
Write-Host ""
Write-Host "--- Collecting artifacts ---" -ForegroundColor Yellow

$surefireReports = Get-ChildItem $scriptDir -Recurse -Filter "TEST-*.xml" -Path "*/surefire-reports/*" -ErrorAction SilentlyContinue
foreach ($report in $surefireReports) {
    Copy-Item $report.FullName $artifactsDir -Force
}

# Copy screenshots if any
Get-ChildItem $artifactsDir -Filter "*.png" -ErrorAction SilentlyContinue | ForEach-Object {
    Write-Host "  Screenshot: $($_.Name)"
}

# --- Summary ---
$endTime = Get-Date
$duration = $endTime - $startTime

Write-Host ""
Write-Host "===== Test Run Complete =====" -ForegroundColor Cyan
Write-Host "Duration: $($duration.ToString('mm\:ss'))"
Write-Host "Artifacts: $artifactsDir"

if ($mvnExitCode -eq 0) {
    Write-Host "Result: PASSED" -ForegroundColor Green
} else {
    Write-Host "Result: FAILED (exit code $mvnExitCode)" -ForegroundColor Red
}

exit $mvnExitCode
