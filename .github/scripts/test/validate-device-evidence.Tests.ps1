Describe "validate-device-evidence" {
    BeforeAll {
        $scriptPath = Join-Path $PSScriptRoot ".." "validate-device-evidence.ps1"
        $candidateSha = "0123456789abcdef0123456789abcdef01234567"

        function New-CompleteEvidence {
            $scenarioIds = @(
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
            $runs =
                foreach ($scenarioId in $scenarioIds) {
                    [ordered]@{
                        scenarioId = $scenarioId
                        evidenceType = if ($scenarioId -in @("home-role-lifecycle", "notification-access", "widget-hosting")) { "physical-device" } else { "automated-emulator" }
                        result = "pass"
                        validator = "Release validation"
                        timestamp = "2026-07-18T12:00:00Z"
                        knownLimitation = ""
                        device = [ordered]@{
                            manufacturer = if ($scenarioId -in @("home-role-lifecycle", "notification-access", "widget-hosting")) { "Honor" } else { "Google" }
                            model = "Test device"
                            androidApi = 35
                            build = "test-build"
                            formFactor = "phone"
                            windowMode = "fullscreen"
                            installType = "release"
                        }
                    }
                }
            return [ordered]@{
                schemaVersion = 1
                candidate = [ordered]@{
                    commitSha = $candidateSha
                    buildIdentity = "0.1.0-alpha.1 (1)"
                    generatedAt = "2026-07-18T12:00:00Z"
                }
                runs = @($runs)
            }
        }

        function Add-TimeScapeMvpEvidence([object] $evidence) {
            $scenarioIds = @(
                "feature-timescape-mvp-compact-portrait",
                "feature-timescape-mvp-compact-landscape",
                "feature-timescape-mvp-folded-cover",
                "feature-timescape-mvp-expanded-adaptive",
                "feature-timescape-mvp-appearance-fallbacks",
                "feature-timescape-mvp-notification-lifecycle",
                "feature-timescape-mvp-accessibility-input",
                "feature-timescape-mvp-performance",
                "feature-timescape-mvp-standard-home"
            )
            foreach ($scenarioId in $scenarioIds) {
                $device = [ordered]@{
                    manufacturer = "Google"
                    model = "Test device"
                    androidApi = 35
                    build = "test-build"
                    formFactor = "phone"
                    windowMode = "fullscreen"
                    windowSizeClass = "compact"
                    orientation = "portrait"
                    installType = "release"
                }
                switch ($scenarioId) {
                    "feature-timescape-mvp-compact-landscape" { $device.orientation = "landscape" }
                    "feature-timescape-mvp-folded-cover" {
                        $device.formFactor = "foldable"
                        $device["posture"] = "cover"
                    }
                    "feature-timescape-mvp-expanded-adaptive" {
                        $device.formFactor = "foldable"
                        $device.windowSizeClass = "expanded"
                        $device.orientation = "landscape"
                        $device["posture"] = "flat"
                    }
                    "feature-timescape-mvp-performance" {
                        $device.orientation = "landscape"
                    }
                }
                $evidence.runs += [ordered]@{
                    scenarioId = $scenarioId
                    evidenceType = "manual-device"
                    result = "pass"
                    validator = "TimeScape MVP validation"
                    timestamp = "2026-07-18T12:00:00Z"
                    knownLimitation = ""
                    device = $device
                }
                if ($scenarioId -eq "feature-timescape-mvp-expanded-adaptive") {
                    $evidence.runs += [ordered]@{
                        scenarioId = $scenarioId
                        evidenceType = "manual-device"
                        result = "pass"
                        validator = "TimeScape MVP validation"
                        timestamp = "2026-07-18T12:00:00Z"
                        knownLimitation = ""
                        device = [ordered]@{
                            manufacturer = "Google"
                            model = "Large test device"
                            androidApi = 35
                            build = "test-build"
                            formFactor = "tablet"
                            windowMode = "fullscreen"
                            windowSizeClass = "expanded"
                            orientation = "landscape"
                            installType = "release"
                        }
                    }
                }
                if ($scenarioId -eq "feature-timescape-mvp-performance") {
                    $evidence.runs += [ordered]@{
                        scenarioId = $scenarioId
                        evidenceType = "manual-device"
                        result = "pass"
                        validator = "TimeScape MVP validation"
                        timestamp = "2026-07-18T12:00:00Z"
                        knownLimitation = ""
                        device = [ordered]@{
                            manufacturer = "Google"
                            model = "Expanded test device"
                            androidApi = 35
                            build = "test-build"
                            formFactor = "foldable"
                            windowMode = "split-screen"
                            windowSizeClass = "expanded"
                            orientation = "landscape"
                            posture = "flat"
                            installType = "release"
                        }
                    }
                }
            }
            return $evidence
        }
    }

    BeforeEach {
        $evidencePath = Join-Path $TestDrive "evidence.json"
    }

    It "accepts complete SHA-bound emulator and Honor evidence" {
        New-CompleteEvidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha
    }

    It "keeps legacy V1 window size values valid outside the TimeScape profile" {
        $evidence = New-CompleteEvidence
        $evidence.runs[0].device.windowMode = "compact"
        $evidence.runs[1].device.windowMode = "medium"
        $evidence.runs[2].device.windowMode = "expanded"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha
    }

    It "rejects evidence for a different candidate SHA" {
        $evidence = New-CompleteEvidence
        $evidence.candidate.commitSha = "fedcba9876543210fedcba9876543210fedcba98"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha } | Should -Throw "*does not match*"
    }

    It "rejects a failed baseline run even when every other run passes" {
        $evidence = New-CompleteEvidence
        $evidence.runs[0].result = "fail"
        $evidence.runs[0].failureResolution = "Tracked separately; candidate remains blocked."
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha } | Should -Throw "*cannot be overridden*"
    }

    It "rejects non-integer Android API values" {
        $evidence = New-CompleteEvidence
        $evidence.runs[0].device.androidApi = "not-an-api"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha } | Should -Throw "*androidApi must be an integer*"
    }

    It "rejects undocumented device enum values" {
        $evidence = New-CompleteEvidence
        $evidence.runs[0].device.formFactor = "unknown"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha } | Should -Throw "*formFactor has an unsupported value*"
    }

    It "rejects non-RFC3339 run timestamps" {
        $evidence = New-CompleteEvidence
        $evidence.runs[0].timestamp = "tomorrow morning"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha } | Should -Throw "*timestamp must be an RFC3339 date-time*"
    }

    It "requires every TimeScape MVP scenario when requested" {
        New-CompleteEvidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp } | Should -Throw "*feature-timescape-mvp-compact-portrait*"
    }

    It "accepts complete TimeScape MVP evidence when requested" {
        Add-TimeScapeMvpEvidence (New-CompleteEvidence) |
            ConvertTo-Json -Depth 10 |
            Set-Content -LiteralPath $evidencePath

        & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp
    }

    It "rejects identical expanded metadata for the TimeScape matrix" {
        $evidence = Add-TimeScapeMvpEvidence (New-CompleteEvidence)
        foreach ($run in $evidence.runs | Where-Object { $_.scenarioId -like "feature-timescape-mvp-*" }) {
            $run.device.formFactor = "foldable"
            $run.device.windowMode = "fullscreen"
            $run.device.windowSizeClass = "expanded"
            $run.device.orientation = "landscape"
            $run.device["posture"] = "flat"
        }
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp } | Should -Throw "*compact-portrait requires a passing phone/compact/fullscreen/portrait*"
    }

    It "rejects TimeScape performance evidence without compact coverage" {
        $evidence = Add-TimeScapeMvpEvidence (New-CompleteEvidence)
        foreach ($run in $evidence.runs | Where-Object { $_.scenarioId -eq "feature-timescape-mvp-performance" }) {
            $run.device.formFactor = "foldable"
            $run.device.windowMode = "split-screen"
            $run.device.windowSizeClass = "expanded"
            $run.device.orientation = "landscape"
            $run.device["posture"] = "flat"
        }
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp } | Should -Throw "*performance requires a passing compact performance*"
    }

    It "validates window size class independently from fullscreen mode" {
        $evidence = Add-TimeScapeMvpEvidence (New-CompleteEvidence)
        $compactRun = $evidence.runs | Where-Object { $_.scenarioId -eq "feature-timescape-mvp-compact-portrait" }
        $tabletRun = $evidence.runs | Where-Object {
            $_.scenarioId -eq "feature-timescape-mvp-expanded-adaptive" -and $_.device.formFactor -eq "tablet"
        }

        $compactRun.device.windowMode | Should -Be "fullscreen"
        $compactRun.device.windowSizeClass | Should -Be "compact"
        $tabletRun.device.windowMode | Should -Be "fullscreen"
        $tabletRun.device.windowSizeClass | Should -Be "expanded"

        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath
        & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp
    }

    It "rejects a compact scenario with an expanded size class even when fullscreen" {
        $evidence = Add-TimeScapeMvpEvidence (New-CompleteEvidence)
        ($evidence.runs | Where-Object {
            $_.scenarioId -eq "feature-timescape-mvp-compact-portrait"
        }).device.windowSizeClass = "expanded"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp } | Should -Throw "*compact-portrait requires a passing phone/compact/fullscreen/portrait*"
    }

    It "rejects legacy window size values in the TimeScape profile" {
        $evidence = Add-TimeScapeMvpEvidence (New-CompleteEvidence)
        ($evidence.runs | Where-Object {
            $_.scenarioId -eq "feature-timescape-mvp-compact-portrait"
        }).device.windowMode = "compact"
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp } | Should -Throw "*compact-portrait requires a passing phone/compact/fullscreen/portrait*"
    }

    It "rejects blocked TimeScape MVP evidence when requested" {
        $evidence = Add-TimeScapeMvpEvidence (New-CompleteEvidence)
        $evidence.runs[-1].result = "blocked"
        $evidence.runs[-1].knownLimitation = "Performance validation is pending."
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath

        { & $scriptPath -EvidencePath $evidencePath -ExpectedCommitSha $candidateSha -RequireTimeScapeMvp } | Should -Throw "*feature-timescape-mvp-standard-home*"
    }
}
