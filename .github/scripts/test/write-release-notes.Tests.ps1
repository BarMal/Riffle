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

    It "writes only the APK row when AabPath is omitted" {
        $apkPath = Join-Path $TestDrive "riffle-alpha.apk"
        "apk-bytes" | Set-Content -LiteralPath $apkPath
        $notesPath = Join-Path $TestDrive "alpha-release-notes.md"

        & $scriptPath `
            -ReleaseChannel alpha `
            -CommitSha ("a" * 40) `
            -ApkPath $apkPath `
            -NotesPath $notesPath

        $notes = Get-Content -LiteralPath $notesPath -Raw
        $notes | Should -Match "riffle-alpha\.apk"
        $notes | Should -Not -Match "\.aab"
    }

    It "writes both artifact rows when AabPath is supplied" {
        $apkPath = Join-Path $TestDrive "riffle-stable.apk"
        $aabPath = Join-Path $TestDrive "riffle-stable.aab"
        "apk-bytes" | Set-Content -LiteralPath $apkPath
        "aab-bytes" | Set-Content -LiteralPath $aabPath
        $notesPath = Join-Path $TestDrive "stable-release-notes.md"

        & $scriptPath `
            -ReleaseChannel stable `
            -CommitSha ("a" * 40) `
            -ApkPath $apkPath `
            -AabPath $aabPath `
            -NotesPath $notesPath

        $notes = Get-Content -LiteralPath $notesPath -Raw
        $notes | Should -Match "riffle-stable\.apk"
        $notes | Should -Match "riffle-stable\.aab"
    }

    It "requires ApkPath when not validating only" {
        $notesPath = Join-Path $TestDrive "missing-apk-notes.md"

        {
            & $scriptPath `
                -ReleaseChannel alpha `
                -CommitSha ("a" * 40) `
                -NotesPath $notesPath
        } | Should -Throw "*ApkPath*"
    }
}
