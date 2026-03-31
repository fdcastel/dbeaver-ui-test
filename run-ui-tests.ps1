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

function Get-IntAttributeValue {
    param(
        [System.Xml.XmlElement]$Element,
        [string]$Name
    )

    if ($null -eq $Element) {
        return 0
    }

    $rawValue = $Element.GetAttribute($Name)
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return 0
    }

    $parsedValue = 0
    if ([int]::TryParse($rawValue, [ref]$parsedValue)) {
        return $parsedValue
    }

    return 0
}

function Get-FirstMeaningfulLine {
    param([string]$Text)

    if ([string]::IsNullOrWhiteSpace($Text)) {
        return $null
    }

    $line = ($Text -split "`r?`n" | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -First 1)
    if ([string]::IsNullOrWhiteSpace($line)) {
        return $null
    }

    $normalized = ($line -replace '\s+', ' ').Trim()
    if ($normalized.Length -gt 200) {
        return $normalized.Substring(0, 197) + "..."
    }

    return $normalized
}

function Get-TestFailureReason {
    param([System.Xml.XmlElement]$FailureNode)

    if ($null -eq $FailureNode) {
        return "No failure details available"
    }

    $message = Get-FirstMeaningfulLine $FailureNode.GetAttribute("message")
    $type = Get-FirstMeaningfulLine $FailureNode.GetAttribute("type")
    $details = Get-FirstMeaningfulLine $FailureNode.InnerText

    if ($message) {
        if ($type -and $message -notlike "*$type*") {
            return "$message [$type]"
        }
        return $message
    }

    if ($details) {
        return $details
    }

    if ($type) {
        return $type
    }

    return "No failure details available"
}

function Get-TestCaseDisplayName {
    param(
        [System.Xml.XmlElement]$TestCase,
        [System.Xml.XmlElement]$FailureNode
    )

    $testName = Get-FirstMeaningfulLine $TestCase.GetAttribute("name")
    if ($testName) {
        return $testName
    }

    $stackTrace = $FailureNode.InnerText
    if (-not [string]::IsNullOrWhiteSpace($stackTrace)) {
        $className = Get-FirstMeaningfulLine $TestCase.GetAttribute("classname")
        if ($className) {
            $classPattern = 'at\s+' + [regex]::Escape($className) + '\.([\w$<>]+)\('
            $classMatch = [regex]::Match($stackTrace, $classPattern)
            if ($classMatch.Success) {
                return $classMatch.Groups[1].Value
            }
        }

        $match = [regex]::Match($stackTrace, 'at\s+([\w.$]+)\.([\w$<>]+)\(')
        if ($match.Success) {
            return $match.Groups[2].Value
        }
    }

    return "(unnamed test case)"
}

function Get-TestReportSummary {
    param([string]$ReportDir)

    $summary = [ordered]@{
        ReportCount = 0
        SuiteCount = 0
        TotalTests = 0
        Passed = 0
        Failed = 0
        Errors = 0
        Skipped = 0
        FailedTests = @()
        ParseWarnings = @()
    }

    if (-not (Test-Path $ReportDir)) {
        return [PSCustomObject]$summary
    }

    $reports = Get-ChildItem -Path $ReportDir -Filter "TEST-*.xml" -File -ErrorAction SilentlyContinue | Sort-Object Name
    $summary.ReportCount = @($reports).Count

    foreach ($report in $reports) {
        try {
            $document = New-Object System.Xml.XmlDocument
            $document.Load($report.FullName)

            $suites = @($document.SelectNodes("//testsuite"))
            foreach ($suite in $suites) {
                if ($suite -isnot [System.Xml.XmlElement]) {
                    continue
                }

                $summary.SuiteCount++

                $suiteTests = Get-IntAttributeValue -Element $suite -Name "tests"
                $suiteFailures = Get-IntAttributeValue -Element $suite -Name "failures"
                $suiteErrors = Get-IntAttributeValue -Element $suite -Name "errors"
                $suiteSkipped = Get-IntAttributeValue -Element $suite -Name "skipped"
                $suitePassed = [Math]::Max(0, $suiteTests - $suiteFailures - $suiteErrors - $suiteSkipped)

                $summary.TotalTests += $suiteTests
                $summary.Passed += $suitePassed
                $summary.Failed += $suiteFailures
                $summary.Errors += $suiteErrors
                $summary.Skipped += $suiteSkipped

                $suiteName = $suite.GetAttribute("name")
                foreach ($testCase in @($suite.SelectNodes("./testcase"))) {
                    if ($testCase -isnot [System.Xml.XmlElement]) {
                        continue
                    }

                    foreach ($failureNode in @($testCase.SelectNodes("./failure | ./error"))) {
                        if ($failureNode -isnot [System.Xml.XmlElement]) {
                            continue
                        }

                        $summary.FailedTests += [PSCustomObject]@{
                            Suite = if ([string]::IsNullOrWhiteSpace($suiteName)) { $testCase.GetAttribute("classname") } else { $suiteName }
                            Test = Get-TestCaseDisplayName -TestCase $testCase -FailureNode $failureNode
                            Status = $failureNode.Name.ToUpperInvariant()
                            Reason = Get-TestFailureReason -FailureNode $failureNode
                            Report = $report.Name
                        }
                    }
                }
            }
        } catch {
            $summary.ParseWarnings += "Failed to parse $($report.Name): $($_.Exception.Message)"
        }
    }

    return [PSCustomObject]$summary
}

# Do NOT use $ErrorActionPreference = "Stop" globally — it causes silent deaths.
# Each section handles its own errors with try/catch and clear messages.
$scriptDir = $PSScriptRoot
$startTime = Get-Date

Write-Host "===== DBeaver UI Test Suite =====" -ForegroundColor Cyan
Write-Host "Started: $startTime"
Write-Host "Script dir: $scriptDir"
Write-Host ""

# --- Load properties ---
$propsFile = Join-Path $scriptDir "ui-test.local.properties"
if (-not (Test-Path $propsFile)) {
    Write-Host "ERROR: Local properties file not found: $propsFile" -ForegroundColor Red
    Write-Host "  Copy ui-test.local.properties.example to ui-test.local.properties and edit it." -ForegroundColor Yellow
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
    try {
        & "$scriptDir\scripts\verify-prerequisites.ps1" -PropertiesFile "$propsFile"
        # verify-prerequisites.ps1 calls 'exit 1' on failure but falls through on success.
        # $LASTEXITCODE is unreliable after a PS script — check $? instead.
        if (-not $?) {
            Write-Host "ERROR: Prerequisite check script failed." -ForegroundColor Red
            exit 1
        }
    } catch {
        Write-Host "ERROR: Prerequisite check failed: $_" -ForegroundColor Red
        exit 1
    }
    Write-Host ""
}

# --- Reset Firebird database ---
if (-not $SkipDbReset) {
    Write-Host "--- Resetting Firebird test database ---" -ForegroundColor Yellow
    try {
        & "$scriptDir\scripts\reset-firebird-db.ps1" -PropertiesFile "$propsFile"
        if (-not $?) {
            Write-Host "ERROR: Database reset script reported a failure." -ForegroundColor Red
            Write-Host "  Check that Firebird is running and no other process has the database locked." -ForegroundColor Yellow
            Write-Host "  You can skip this step with -SkipDbReset if the database already exists." -ForegroundColor Yellow
            exit 1
        }
    } catch {
        Write-Host "ERROR: Database reset failed: $_" -ForegroundColor Red
        Write-Host "  Check that Firebird is running and no other process has the database locked." -ForegroundColor Yellow
        Write-Host "  You can skip this step with -SkipDbReset if the database already exists." -ForegroundColor Yellow
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
if (-not $dbeaverP2 -or -not (Test-Path $dbeaverP2)) {
    Write-Host "ERROR: DBeaver P2 repository not found at: $dbeaverP2" -ForegroundColor Red
    Write-Host "  Set DBEAVER_P2_REPO in $propsFile to the local P2 repository." -ForegroundColor Yellow
    Write-Host "  Build DBeaver first: cd $dbeaverRepo && mvn verify -pl product/repositories/org.jkiss.dbeaver.ce.repository" -ForegroundColor Yellow
    exit 1
}
$p2Url = "file:///" + ($dbeaverP2 -replace '\\', '/')
Write-Host "DBeaver P2 repo: $p2Url"

# --- Verify Maven is available ---
$mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
if (-not $mvnCmd) {
    Write-Host "ERROR: Maven (mvn) not found in PATH." -ForegroundColor Red
    Write-Host "  Install Maven and ensure 'mvn' is on the PATH." -ForegroundColor Yellow
    exit 1
}

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

if ($null -eq $mvnExitCode) { $mvnExitCode = 1 }

# --- Collect artifacts ---
Write-Host ""
Write-Host "--- Collecting artifacts ---" -ForegroundColor Yellow

$surefireReports = Get-ChildItem -Path "$scriptDir\plugins\*\target\surefire-reports\TEST-*.xml" -ErrorAction SilentlyContinue
if ($surefireReports) {
    foreach ($report in $surefireReports) {
        Copy-Item $report.FullName $artifactsDir -Force
        Write-Host "  Report: $($report.Name)"
    }
} else {
    Write-Host "  No surefire reports found — tests may not have run." -ForegroundColor Yellow
    Write-Host "  Check Maven output above for errors." -ForegroundColor Yellow
}

$testSummary = Get-TestReportSummary -ReportDir $artifactsDir

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
    Write-Host "Build result: PASSED" -ForegroundColor Green
} else {
    Write-Host "Build result: FAILED (exit code $mvnExitCode)" -ForegroundColor Red
}

if ($testSummary.ReportCount -eq 0) {
    Write-Host "Test result: UNKNOWN (no JUnit XML reports found)" -ForegroundColor Yellow
} elseif (($testSummary.Failed + $testSummary.Errors) -eq 0) {
    Write-Host "Test result: PASSED" -ForegroundColor Green
} else {
    Write-Host "Test result: FAILED ($($testSummary.Failed) failure(s), $($testSummary.Errors) error(s))" -ForegroundColor Red
}

if ($testSummary.ReportCount -gt 0) {
    Write-Host "Test summary: $($testSummary.TotalTests) total, $($testSummary.Passed) passed, $($testSummary.Failed) failed, $($testSummary.Errors) errors, $($testSummary.Skipped) skipped"
}

if ($testSummary.FailedTests.Count -gt 0) {
    Write-Host ""
    Write-Host "Failed tests:" -ForegroundColor Red
    foreach ($failedTest in $testSummary.FailedTests) {
        $qualifiedName = "$($failedTest.Suite).$($failedTest.Test)"
        Write-Host "  - [$($failedTest.Status)] $qualifiedName"
        Write-Host "    Reason: $($failedTest.Reason)"
    }
} elseif ($testSummary.ReportCount -gt 0) {
    Write-Host "Failed tests: none" -ForegroundColor Green
}

if ($testSummary.ParseWarnings.Count -gt 0) {
    Write-Host ""
    Write-Host "Report parsing warnings:" -ForegroundColor Yellow
    foreach ($warning in $testSummary.ParseWarnings) {
        Write-Host "  - $warning"
    }
}

exit $mvnExitCode
