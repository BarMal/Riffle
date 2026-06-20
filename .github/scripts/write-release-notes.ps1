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
    [string] $NotesPath
)

$apk = Get-Item -LiteralPath $ApkPath
$aab = Get-Item -LiteralPath $AabPath

function Format-Mib([long] $Bytes) {
    return "{0:N2}" -f ($Bytes / 1MB)
}

$notes = @"
Signed $ReleaseChannel build from commit $CommitSha.

## Artifact sizes

| Artifact | Size |
| --- | ---: |
| $($apk.Name) | $(Format-Mib $apk.Length) MiB ($($apk.Length) bytes) |
| $($aab.Name) | $(Format-Mib $aab.Length) MiB ($($aab.Length) bytes) |

APK size budget tracking: #53
"@

Set-Content -LiteralPath $NotesPath -Value $notes -NoNewline
