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

function Require-String([object] $Value, [string] $Name, [bool] $AllowBlank = $false) {
    if ($Value -isnot [string]) {
        throw "$Name must be a string."
    }
    if (-not $AllowBlank) {
        Require-NonBlank $Value $Name
    }
}

function Require-Property([object] $Object, [string] $Name) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        throw "Missing required property: $Name"
    }
    return $property.Value
}

function Assert-OnlyProperties([object] $Object, [string[]] $AllowedProperties, [string] $Name) {
    foreach ($property in $Object.PSObject.Properties.Name) {
        if ($property -notin $AllowedProperties) {
            throw "$Name contains an unsupported property: $property"
        }
    }
}

function Require-Rfc3339Timestamp([object] $Value, [string] $Name) {
    Require-String $Value $Name
    if ($Value -notmatch "^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$") {
        throw "$Name must be an RFC3339 date-time."
    }
    try {
        [DateTimeOffset]::Parse($Value, [Globalization.CultureInfo]::InvariantCulture) | Out-Null
    } catch {
        throw "$Name must be an RFC3339 date-time."
    }
}

function Require-Enum([object] $Value, [string] $Name, [string[]] $Values) {
    Require-String $Value $Name
    if ($Value -notin $Values) {
        throw "$Name has an unsupported value: $Value"
    }
}

function Require-PositiveInteger([object] $Value, [string] $Name) {
    if ($Value -isnot [long] -and $Value -isnot [int]) {
        throw "$Name must be an integer."
    }
    if ($Value -lt 1) {
        throw "$Name must be at least 1."
    }
}

if ($ExpectedCommitSha -notmatch "^[0-9a-f]{40}$") {
    throw "ExpectedCommitSha must be a lowercase 40-character Git SHA."
}
if (-not (Test-Path -LiteralPath $EvidencePath)) {
    throw "Evidence file does not exist: $EvidencePath"
}

try {
    $evidence = Get-Content -LiteralPath $EvidencePath -Raw | ConvertFrom-Json -Depth 20 -DateKind String
} catch {
    throw "Evidence file is not valid JSON: $($_.Exception.Message)"
}

Assert-OnlyProperties $evidence @("schemaVersion", "candidate", "runs") "evidence"
$schemaVersion = Require-Property $evidence "schemaVersion"
if (($schemaVersion -isnot [long] -and $schemaVersion -isnot [int]) -or $schemaVersion -ne 1) {
    throw "Unsupported device evidence schemaVersion. Expected 1."
}
$candidate = Require-Property $evidence "candidate"
Assert-OnlyProperties $candidate @("commitSha", "buildIdentity", "generatedAt") "candidate"
$candidateSha = Require-Property $candidate "commitSha"
Require-String $candidateSha "candidate.commitSha"
if ($candidateSha -notmatch "^[0-9a-f]{40}$") {
    throw "candidate.commitSha must be a lowercase 40-character Git SHA."
}
if ($candidateSha -cne $ExpectedCommitSha) {
    throw "Evidence candidate SHA does not match ExpectedCommitSha."
}
Require-String (Require-Property $candidate "buildIdentity") "candidate.buildIdentity"
Require-Rfc3339Timestamp (Require-Property $candidate "generatedAt") "candidate.generatedAt"

$runsValue = Require-Property $evidence "runs"
if ($runsValue -isnot [array]) {
    throw "runs must be an array."
}
$runs = @($runsValue)
if ($runs.Count -eq 0) {
    throw "Evidence must contain at least one run."
}

foreach ($run in $runs) {
    $scenarioId = Require-Property $run "scenarioId"
    $result = Require-Property $run "result"
    $evidenceType = Require-Property $run "evidenceType"
    Assert-OnlyProperties $run @("scenarioId", "evidenceType", "result", "validator", "timestamp", "knownLimitation", "failureResolution", "device") "run"
    Require-String $scenarioId "run.scenarioId"
    if ($scenarioId -notmatch "^[a-z0-9-]+$") {
        throw "run.scenarioId must use lowercase letters, numbers, and hyphens."
    }
    Require-Enum $result "run.result" @("pass", "fail", "blocked", "not-applicable")
    Require-Enum $evidenceType "run.evidenceType" @("automated-emulator", "physical-device", "manual-device")
    Require-String (Require-Property $run "validator") "run.validator"
    Require-Rfc3339Timestamp (Require-Property $run "timestamp") "run.timestamp"
    Require-String (Require-Property $run "knownLimitation") "run.knownLimitation" $true
    $device = Require-Property $run "device"
    Assert-OnlyProperties $device @("manufacturer", "model", "androidApi", "build", "formFactor", "windowMode", "installType") "run.device"
    Require-String (Require-Property $device "manufacturer") "run.device.manufacturer"
    Require-String (Require-Property $device "model") "run.device.model"
    Require-PositiveInteger (Require-Property $device "androidApi") "run.device.androidApi"
    Require-String (Require-Property $device "build") "run.device.build"
    Require-Enum (Require-Property $device "formFactor") "run.device.formFactor" @("phone", "tablet", "foldable", "desktop")
    Require-Enum (Require-Property $device "windowMode") "run.device.windowMode" @("compact", "medium", "expanded", "split-screen", "fullscreen")
    Require-Enum (Require-Property $device "installType") "run.device.installType" @("clean", "upgrade", "debug", "release")
    if ($result -eq "fail") {
        Require-String (Require-Property $run "failureResolution") "run.failureResolution"
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
