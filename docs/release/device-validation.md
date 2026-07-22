# Launcher Device Acceptance Evidence

`ReleaseDeviceEvidenceManifestV1` is Riffle's authoritative, SHA-bound device-acceptance contract
for launcher-critical validation. It complements, and never replaces, `./gradlew verify`.
Routine alphas are gated automatically by successful exact-SHA CI; device evidence remains the
manual acceptance record for milestone and stable-release decisions.

The contract has three repository-owned parts:

- [`device-evidence.schema.json`](device-evidence.schema.json) defines the versioned JSON shape.
- [`device-evidence-template.json`](device-evidence-template.json) starts a candidate artifact.
- [`validate-device-evidence.ps1`](../../.github/scripts/validate-device-evidence.ps1) rejects incomplete,
  stale, failed, or wrong-SHA evidence.

The template is intentionally incomplete and is not evidence. Replace every placeholder with the
candidate's exact identity and append one run for every required scenario.

## Required baseline matrix

Every launcher-critical candidate requires passing rows for all of these stable IDs. Feature work
adds rows using a `feature-` prefix; it never creates a different evidence format.

| ID | Required coverage |
| --- | --- |
| `clean-install-first-open` | Clean install, first open before Home role, and preview-first flow. |
| `home-role-lifecycle` | Home-role grant/cancel, Home, cold start, app update, reboot, switch away/back, and new intent. Must pass on a physical Honor device. |
| `catalog-resilience` | Valid, empty, partial, and unavailable catalog data without losing layout. |
| `home-navigation` | Home, drawer/search, launch, Settings, Back, and Home navigation. |
| `notification-access` | Grant/revoke with zero and non-zero notifications. Must pass on a physical Honor device. Never record notification contents. |
| `widget-hosting` | Picker, direct/approved bind, configuration, cancellation, interaction, resize, removal, unavailable provider, recreation, and reboot when widgets are affected. Must pass on a physical Honor device. |
| `adaptive-layout` | Rotation, landscape, split-screen/runtime resize, and affected large/foldable layouts. |
| `accessibility-reduced-motion` | Light/dark, large font/display size, TalkBack traversal/actions, and reduced motion where UI or motion changes. |
| `backup-layout-migration` | Backup/import and stored-layout migration whenever persistence changes. |
| `release-upgrade` | Upgrade from the latest published alpha without clearing data. |

At least one passing `automated-emulator` row proves repeatable current-AOSP coverage. Physical
Honor rows are required for `home-role-lifecycle`, `notification-access`, and `widget-hosting`.
Use phone plus a large/foldable target when shared layout primitives are affected, and record the
actual form factor and window mode on every run.

## Evidence rules

Each artifact has one exact 40-character candidate SHA, build identity, and UTC generation time.
Every run records its evidence type, result, validator, timestamp, manufacturer/model/API/build,
form factor, fullscreen/split-screen window mode, install type, and any known limitation. Runs may
also record window size class, orientation, and fold posture; the TimeScape MVP profile requires
them for the scenarios that depend on them.

- `pass` means the named scenario completed on the recorded candidate and device.
- `fail` blocks the candidate. A retry may add evidence but cannot erase the original failed row;
  include a non-empty `failureResolution` describing its disposition.
- `blocked` also blocks a required baseline row. State the reason in `knownLimitation`.
- `not-applicable` is permitted only for feature-specific rows. Required baseline rows must pass.

The validator rejects missing rows, blank identity fields, a different candidate SHA, failed or
blocked baseline runs, missing Honor coverage, and missing automated-emulator coverage. This
makes stale evidence unusable for another candidate.

## TimeScape MVP closeout profile

For #894, validate the candidate with `-RequireTimeScapeMvp`. This opt-in profile keeps the
baseline contract reusable while requiring these additional passing rows:

| ID | Required coverage |
| --- | --- |
| `feature-timescape-mvp-compact-portrait` | `phone` + `compact` size class + `fullscreen` + `portrait`; focus-first tap, vertical Spline, stage selection, contextual actions, detail/Back, and Standard Home mode return. |
| `feature-timescape-mvp-compact-landscape` | `phone` + `compact` size class + `fullscreen` + `landscape` with the same card and stage behavior. |
| `feature-timescape-mvp-folded-cover` | `foldable` + `compact` size class + `fullscreen` + `cover` posture. |
| `feature-timescape-mvp-expanded-adaptive` | A `foldable` + `expanded` size class + actual fullscreen/split-screen mode in `flat`, `book`, or `tabletop`, and a separate `tablet` or `desktop` + `expanded` size-class run. |
| `feature-timescape-mvp-appearance-fallbacks` | Light/dark, difficult artwork or wallpaper, minimum/default/maximum values, presets, Reset, persistence, and effect fallbacks. |
| `feature-timescape-mvp-notification-lifecycle` | Checking/granted/revoked/unavailable access; zero/one/many cards and stages; pinned/dynamic lifecycle; profile lock/removal; process death; and notification/media churn. |
| `feature-timescape-mvp-accessibility-input` | TalkBack, touch, keyboard/D-pad/mouse, reduced motion/transparency, large font/display scale, RTL, and high contrast. |
| `feature-timescape-mvp-performance` | Separate compact-size fullscreen phone and expanded-size foldable/tablet/desktop runs meet the TimeScape card-update target, or a failed/blocked result documents the fallback and blocks closeout. |
| `feature-timescape-mvp-standard-home` | Standard Home remains the independent default with layout, Dock, widgets, selected page, and wallpaper unchanged. |

Do not include screenshots, device serials, account identifiers, app lists, notification content,
system tokens, or secrets. On failure, retain sanitized logs outside the manifest and link only a
non-sensitive artifact reference in the release operator's record.

## Producing an artifact

Run the smoke helper once per physical-device scenario after installing the exact candidate. It
checks that the Riffle package is installed, captures only public build metadata, and writes one
privacy-safe JSON artifact. It does not claim UI behavior passed on its own.

```bash
.github/scripts/run-device-smoke.sh \
  --serial DEVICE_SERIAL \
  --candidate-sha 0123456789abcdef0123456789abcdef01234567 \
  --build-identity '0.1.0-alpha.300 (300)' \
  --validator 'Release operator' \
  --scenario home-role-lifecycle \
  --result pass \
  --form-factor phone \
  --window-mode fullscreen \
  --install-type clean \
  --output evidence/home-role-lifecycle-honor.json
```

Merge the individual run objects into the candidate manifest, preserve each attempt, then validate
the generic launcher baseline against the exact selected SHA:

```powershell
pwsh .github/scripts/validate-device-evidence.ps1 `
  -EvidencePath evidence/candidate.json `
  -ExpectedCommitSha 0123456789abcdef0123456789abcdef01234567
```

For the #894 TimeScape MVP closeout, validate the same manifest with the additional profile:

```powershell
pwsh .github/scripts/validate-device-evidence.ps1 `
  -EvidencePath evidence/candidate.json `
  -ExpectedCommitSha 0123456789abcdef0123456789abcdef01234567 `
  -RequireTimeScapeMvp
```

The release operator must publish the exact same SHA that appears in this manifest, the build, and
the release notes. A genuine device limitation remains visible in release notes and blocks a
launcher-critical candidate; it cannot be converted into a pass by an unstructured checkbox.

## Validation ownership

Implementation workers add feature-specific rows and run the relevant emulator/device cases.
Release operators verify the candidate SHA, execute the validator, confirm physical Honor and
adaptive coverage, and summarize passed coverage plus any blocked limitation in release notes.
The Pester contract tests live beside the validator at
`.github/scripts/test/validate-device-evidence.Tests.ps1`.
