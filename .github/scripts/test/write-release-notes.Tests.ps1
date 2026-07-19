BeforeAll {
    $scriptPath = Join-Path $PSScriptRoot ".." "write-release-notes.ps1"
}

Describe "write-release-notes" {
    It "accepts complete required release-note sections" {
        $releaseNotesPath = Join-Path $TestDrive "complete-notes.md"
        @'
### Changed

- Added candidate release gating.

### Verification

- `./gradlew verify` passed.

### Known Limitations

- Device evidence remains manual.
'@ | Set-Content -LiteralPath $releaseNotesPath

        {
            & $scriptPath `
                -ReleaseChannel alpha `
                -CommitSha ("a" * 40) `
                -ReleaseNotesPath $releaseNotesPath `
                -RequireReleaseNotes `
                -ValidateOnly
        } | Should -Not -Throw
    }

    It "rejects release notes with a blank required section" {
        $releaseNotesPath = Join-Path $TestDrive "blank-notes.md"
        @'
### Changed

- Added candidate release gating.

### Verification

- `./gradlew verify` passed.

### Known Limitations
'@ | Set-Content -LiteralPath $releaseNotesPath

        {
            & $scriptPath `
                -ReleaseChannel alpha `
                -CommitSha ("a" * 40) `
                -ReleaseNotesPath $releaseNotesPath `
                -RequireReleaseNotes `
                -ValidateOnly
        } | Should -Throw "*Known Limitations*"
    }
}
