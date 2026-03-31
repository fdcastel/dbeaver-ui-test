<#
.SYNOPSIS
    Verifies that all prerequisites for running UI tests are met.
#>
[CmdletBinding()]
param(
    [string]$PropertiesFile = "$PSScriptRoot\..\ui-test.local.properties"
)

$ErrorActionPreference = "Stop"
$allOk = $true

function Check($description, $condition) {
    if ($condition) {
        Write-Host "  [OK] $description" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $description" -ForegroundColor Red
        $script:allOk = $false
    }
}

Write-Host "Checking prerequisites..."

# Java
$javaVersion = (java -version 2>&1 | Select-Object -First 1) -replace '.*"(\d+).*', '$1'
Check "Java 21+ installed" ($javaVersion -ge 21)

# Maven
$mvnAvailable = Get-Command mvn -ErrorAction SilentlyContinue
Check "Maven available" ($null -ne $mvnAvailable)

# Properties file
Check "ui-test.local.properties exists" (Test-Path $PropertiesFile)

if (Test-Path $PropertiesFile) {
    $props = @{}
    Get-Content $PropertiesFile | Where-Object { $_ -match '^\s*[^#]' -and $_ -match '=' } | ForEach-Object {
        $key, $value = $_ -split '=', 2
        $props[$key.Trim()] = $value.Trim()
    }

    # DBeaver repo
    $dbeaverRepo = $props['DBEAVER_REPO']
    Check "DBeaver repo exists at $dbeaverRepo" (Test-Path $dbeaverRepo)

    # DBeaver P2 repo (local build)
    $p2Repo = $props['DBEAVER_P2_REPO']
    Check "DBeaver local P2 repo exists at $p2Repo" ((Test-Path "$p2Repo\content.jar") -or (Test-Path "$p2Repo\content.xml.xz"))

    # Firebird isql
    $isql = $props['FIREBIRD_ISQL']
    Check "Firebird isql found at $isql" (Test-Path $isql)
}

Write-Host ""
if ($allOk) {
    Write-Host "All prerequisites OK." -ForegroundColor Green
} else {
    Write-Host "Some prerequisites are missing. Fix them before running tests." -ForegroundColor Red
    exit 1
}
