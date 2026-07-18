param(
    [Parameter(Mandatory = $true)]
    [string] $EvidencePath,

    [Parameter(Mandatory = $true)]
    [string] $ExpectedCommitSha
)

$ErrorActionPreference = "Stop"

$requiredScenarioIds = @(
    "clean-install-first-open",
    "home-role-lifecycle",
    "catalog-resilience",
    "home-navigation",
    "notification-access",
    "widget-hosting",
    "adaptive-layout",
    "accessibility-reduced-motion",
    "backup-layout-migration",
    "release-upgrade"
)
$honorScenarioIds = @("home-role-lifecycle", "notification-access", "widget-hosting")

function Require-NonBlank([object] $Value, [string] $Name) {
    if ($null -eq $Value -or [string]::IsNullOrWhiteSpace([string] $Value)) {
        throw "$Name must not be blank."
    }
}

function Require-Property([object] $Object, [string] $Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "Missing required property: $Name"
    }
    return $property.Value
}

if ($ExpectedCommitSha -notmatch "^[0-9a-f]{40}$") {
    throw "ExpectedCommitSha must be a lowercase 40-character Git SHA."
}
if (-not (Test-Path -LiteralPath $EvidencePath)) {
    throw "Evidence file does not exist: $EvidencePath"
}

try {
    $evidence = Get-Content -LiteralPath $EvidencePath -Raw | ConvertFrom-Json -Depth 20
} catch {
    throw "Evidence file is not valid JSON: $($_.Exception.Message)"
}

if ((Require-Property $evidence "schemaVersion") -ne 1) {
    throw "Unsupported device evidence schemaVersion. Expected 1."
}
$candidate = Require-Property $evidence "candidate"
$candidateSha = Require-Property $candidate "commitSha"
if ($candidateSha -cne $ExpectedCommitSha) {
    throw "Evidence candidate SHA does not match ExpectedCommitSha."
}
Require-NonBlank (Require-Property $candidate "buildIdentity") "candidate.buildIdentity"
Require-NonBlank (Require-Property $candidate "generatedAt") "candidate.generatedAt"

$runs = @(Require-Property $evidence "runs")
if ($runs.Count -eq 0) {
    throw "Evidence must contain at least one run."
}

foreach ($run in $runs) {
    $scenarioId = Require-Property $run "scenarioId"
    $result = Require-Property $run "result"
    $evidenceType = Require-Property $run "evidenceType"
    if ($result -notin @("pass", "fail", "blocked", "not-applicable")) {
        throw "Run $scenarioId has an unsupported result."
    }
    if ($evidenceType -notin @("automated-emulator", "physical-device", "manual-device")) {
        throw "Run $scenarioId has an unsupported evidenceType."
    }
    Require-NonBlank (Require-Property $run "validator") "run.validator"
    Require-NonBlank (Require-Property $run "timestamp") "run.timestamp"
    Require-Property $run "knownLimitation" | Out-Null
    $device = Require-Property $run "device"
    foreach ($property in @("manufacturer", "model", "androidApi", "build", "formFactor", "windowMode", "installType")) {
        Require-NonBlank (Require-Property $device $property) "run.device.$property"
    }
    if ($result -eq "fail") {
        Require-NonBlank (Require-Property $run "failureResolution") "run.failureResolution"
        throw "Run $scenarioId failed. Failed product evidence cannot be overridden by a retry."
    }
}

foreach ($scenarioId in $requiredScenarioIds) {
    $scenarioRuns = @($runs | Where-Object { $_.scenarioId -eq $scenarioId })
    if ($scenarioRuns.Count -eq 0) {
        throw "Missing required baseline evidence: $scenarioId"
    }
    if (@($scenarioRuns | Where-Object { $_.result -ne "pass" }).Count -gt 0) {
        throw "Required baseline evidence must pass without blocked or not-applicable runs: $scenarioId"
    }
}

if (@($runs | Where-Object { $_.evidenceType -eq "automated-emulator" -and $_.result -eq "pass" }).Count -eq 0) {
    throw "At least one passing automated-emulator run is required."
}
foreach ($scenarioId in $honorScenarioIds) {
    if (@($runs | Where-Object {
        $_.scenarioId -eq $scenarioId -and $_.result -eq "pass" -and
            $_.evidenceType -eq "physical-device" -and $_.device.manufacturer -match "(?i)^honor$"
    }).Count -eq 0) {
        throw "Missing passing physical Honor evidence for $scenarioId."
    }
}

Write-Output "Device evidence is complete for candidate $ExpectedCommitSha."
