param(
    [Parameter(Mandatory = $true)]
    [string] $ReleaseChannel,

    [Parameter(Mandatory = $true)]
    [string] $CommitSha,

    [string] $ApkPath = "",

    [string] $AabPath = "",

    [string] $NotesPath = "",

    [string] $ChangelogPath = "",

    [string] $ReleaseTitle = "",

    [string] $ReleaseNotesPath = "",

    [switch] $RequireReleaseNotes,

    [switch] $ValidateOnly
)

function Format-Mib([long] $Bytes) {
    return "{0:N2}" -f ($Bytes / 1MB)
}

function Get-ChangelogSection([string] $Path, [string] $Title) {
    if ([string]::IsNullOrWhiteSpace($Path) -or [string]::IsNullOrWhiteSpace($Title)) {
        return $null
    }

    if (-not (Test-Path -LiteralPath $Path)) {
        return $null
    }

    $lines = Get-Content -LiteralPath $Path
    $headingPattern = "^\s*##\s+$([regex]::Escape($Title))\s*$"
    $nextHeadingPattern = "^\s*##\s+"
    $startIndex = -1

    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match $headingPattern) {
            $startIndex = $index + 1
            break
        }
    }

    if ($startIndex -lt 0) {
        return $null
    }

    $section = [System.Collections.Generic.List[string]]::new()
    for ($index = $startIndex; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match $nextHeadingPattern) {
            break
        }
        $section.Add($lines[$index])
    }

    return ($section -join [Environment]::NewLine).Trim()
}

function Get-ReleaseNotes {
    if (-not [string]::IsNullOrWhiteSpace($ReleaseNotesPath)) {
        if (-not (Test-Path -LiteralPath $ReleaseNotesPath)) {
            throw "Release notes file does not exist: $ReleaseNotesPath"
        }
        return (Get-Content -LiteralPath $ReleaseNotesPath -Raw).Trim()
    }

    return Get-ChangelogSection -Path $ChangelogPath -Title $ReleaseTitle
}

function Assert-RequiredReleaseNotesSections([string] $ReleaseNotes) {
    foreach ($sectionName in @("Changed", "Verification", "Known Limitations")) {
        $escapedSectionName = [regex]::Escape($sectionName)
        $sectionPattern = "(?ms)^\s*#{2,6}\s+$escapedSectionName\s*$\r?\n(?<content>.*?)(?=^\s*#{1,6}\s+|\z)"
        $section = [regex]::Match($ReleaseNotes, $sectionPattern)

        if (-not $section.Success -or [string]::IsNullOrWhiteSpace($section.Groups["content"].Value)) {
            throw "Release notes must contain a non-blank '$sectionName' section."
        }
    }
}

$releaseNotes = Get-ReleaseNotes
if ($RequireReleaseNotes) {
    if ([string]::IsNullOrWhiteSpace($releaseNotes)) {
        throw "Release notes are required for this release."
    }
    Assert-RequiredReleaseNotesSections -ReleaseNotes $releaseNotes
}

if ($ValidateOnly) {
    return
}

if ([string]::IsNullOrWhiteSpace($ApkPath) -or [string]::IsNullOrWhiteSpace($AabPath) -or [string]::IsNullOrWhiteSpace($NotesPath)) {
    throw "ApkPath, AabPath, and NotesPath are required unless ValidateOnly is specified."
}

$apk = Get-Item -LiteralPath $ApkPath
$aab = Get-Item -LiteralPath $AabPath

$releaseNotesSection = ""
if (-not [string]::IsNullOrWhiteSpace($releaseNotes)) {
    $releaseNotesSection = @"

## Release notes

$releaseNotes
"@
}

$notes = @"
Signed $ReleaseChannel build from commit $CommitSha.
$releaseNotesSection

## Artifact sizes

| Artifact | Size |
| --- | ---: |
| $($apk.Name) | $(Format-Mib $apk.Length) MiB ($($apk.Length) bytes) |
| $($aab.Name) | $(Format-Mib $aab.Length) MiB ($($aab.Length) bytes) |

APK size budget tracking: #53
"@

Set-Content -LiteralPath $NotesPath -Value $notes -NoNewline
