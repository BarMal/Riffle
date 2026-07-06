param(
    [Parameter(Mandatory = $true)]
    [string] $ReleaseChannel,

    [Parameter(Mandatory = $true)]
    [string] $CommitSha,

    [Parameter(Mandatory = $true)]
    [string] $ApkPath,

    [Parameter(Mandatory = $true)]
    [string] $AabPath,

    [Parameter(Mandatory = $true)]
    [string] $NotesPath,

    [string] $ChangelogPath = "",

    [string] $ReleaseTitle = ""
)

$apk = Get-Item -LiteralPath $ApkPath
$aab = Get-Item -LiteralPath $AabPath

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

$changelogSection = Get-ChangelogSection -Path $ChangelogPath -Title $ReleaseTitle
$releaseNotesSection = ""
if (-not [string]::IsNullOrWhiteSpace($changelogSection)) {
    $releaseNotesSection = @"

## Release notes

$changelogSection
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
