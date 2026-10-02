# R8 Minification And Resource Shrinking

Status: investigation and stage 1 (opt-in, non-publishing build). The shipped alpha and stable
builds are unchanged: `release` still has `isMinifyEnabled = false`. Tracks #1268, relates to the
[`APK Size Budget`](apk-size-budget.md) (#53).

## Why

The release APK grew from 18.05 MiB (alpha 71) to about 25 MiB, and the alpha size gate was raised
to unblock publishing. The size is not asset or native-library weight. It is almost entirely
unshrunk, uncompressed bytecode, which is exactly what R8 removes.

## Evidence

Labels: **MEASURED** means read from a real artifact; **ESTIMATED** means reasoned from the
measurements and typical R8 behaviour and must be confirmed by the first CI run of the check.

### What is in the APK (MEASURED)

Source: published `riffle-alpha.apk` of release `alpha-f698face48bb` (26,060,379 bytes, 24.85 MiB),
inspected with Python `zipfile` (metadata only).

| Entry group | Size (MiB) | Share |
| --- | ---: | ---: |
| `classes.dex` | 12.85 | 51.9% |
| `classes2.dex` | 10.52 | 42.5% |
| `classes3.dex` | 0.90 | 3.7% |
| `resources.arsc` | 0.40 | 1.6% |
| `lib/` (4 ABIs: `libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`) | 0.06 | 0.2% |
| `res/`, `assets/` (only a 7 KB `baseline.prof`), `kotlin/`, `META-INF/`, manifest | 0.04 | 0.2% |

Findings:

- 98% of the APK is dex (24.27 MiB). Everything else is under 0.5 MiB.
- All three dex files are **stored uncompressed** (zip method 0), which AGP does for `minSdk >= 28`
  (this app: 31). Deflating the same dex files at level 9 gives 8.26 MiB (MEASURED). So the APK is
  about 3x larger than the same content compressed. See "Cheaper wins".
- Method references by package (MEASURED, all 135,444 across the three dex files): Compose
  (`ui`, `foundation`, `material3`, `runtime`, `animation`, `material`) about 53,000 (39%);
  `androidx.datastore.preferences` 10,682 (8%, mostly bundled lite-protobuf); app
  `com.riffle.app` 18,363 plus `com.riffle.core.domain` 8,790 (20%); the rest is AndroidX core,
  lifecycle, window, emoji2, coroutines and Kotlin stdlib.
- No `material-icons-extended`: only `material-icons-core` is declared, and the app uses 10 core
  icons. Nothing to gain there. No image-loading library, no Gson/Moshi/kotlinx.serialization, and no
  Room of its own (WorkManager, added in #1393, bundles one; see the keep-rule review). There is no `profileinstaller` dependency or baseline-profile module (a
  7 KB profile arrives from libraries only).
- Native libs total 0.06 MiB across four ABIs. ABI splits would save about 0.04 MiB. Not worth it.
- `app/src/main/res` holds one string; `res/` is 13 KB. `resources.arsc` (0.40 MiB) is library
  string tables in every locale (Material 3, activity, core, emoji2, window).
- `:core:recurrence-ical4j` became an `:app` dependency with the ICS feed source (#1409, see
  [`workspaces-ics-source.md`](../product/workspaces-ics-source.md)). MEASURED on that PR (CI run 36989803449):
  unminified 27.88 MiB (about +2.2 MiB over the 25.66 MiB above), minified 4.68 MiB (about +0.3 MiB), R8 completed
  with the extra `-dontwarn` lines below and the emulator smoke passed.

### Estimated savings per lever

| Lever | Estimate | Basis |
| --- | --- | --- |
| R8 shrink + optimise (this work) | **MEASURED (CI run 36896105589, same commit): 25.66 MiB to 4.38 MiB, saved 21.28 MiB (82.9%).** dex 25.08 to 4.10 MiB (3 files to 1), `resources.arsc` 0.40 to 0.12 MiB. The earlier 11 to 16 MiB estimate was far too conservative. | Built with `-Priffle.minify=true`; R8 completed without errors. |
| `isShrinkResources` | MEASURED about 0.28 MiB (arsc 0.40 to 0.12), included in the figure above | Enabled with minify. |
| Compress dex in the APK | **MEASURED upper bound: 24.27 to 8.26 MiB of dex** (zlib level 9). Independent of R8; on top of R8 it would save roughly 1.5 MiB more (ESTIMATED: 4.10 MiB of dex compresses about 3x), so it is now much less compelling. | Trade-off: larger on-device install footprint and a slightly slower install, because the system extracts and optimises dex instead of mapping it. Reasonable for a sideloaded alpha; decide separately. |
| `localeFilters` (English only) | ESTIMATED 0.2 to 0.3 MiB | Only the arsc library string tables. Costs localised accessibility strings in library UI, so not recommended while a11y is a priority. |
| ABI splits / drop ABIs | MEASURED about 0.04 MiB | Negligible. |
| Extended icons removal | 0 MiB | Not present. |
| Baseline profile | 0 MiB size (adds a small `baseline.prof`) | Startup/jank lever, not a size lever; #1268 covers it separately. |

The first CI run of the Minified Release Check replaces every ESTIMATED figure for R8 with a
MEASURED one in its job summary.

## Keep-rule risk review

R8 in AGP 8.7 runs in full mode. Reviewed against `app/src/main`, `core/domain/src/main` and the
dependency list:

| Risk area | Finding | Needed rule? |
| --- | --- | --- |
| Manifest components (`MainActivity`, `RiffleNotificationListenerService`, `OverlayDockService`) | Kept automatically by AAPT2-generated rules. | No. A defensive explicit keep for the notification listener is in `proguard-rules.pro`, because the system matches it by component name against the enabled-listeners setting. |
| BroadcastReceiver / AppWidgetProvider / ContentProvider | None declared. Widget hosting uses the platform `AppWidgetHost`/`AppWidgetManager` and hosts other apps' providers, which are never in our dex. | No |
| WorkManager (`FeedRefreshWorker`, #1393) | `androidx.work:work-runtime` ships consumer rules (keeps the `(Context, WorkerParameters)` constructor of every `ListenableWorker`). WorkManager stores the worker class name in its database and instantiates it by name, so the name must be stable across releases. | One defensive `-keepnames` for `FeedRefreshWorker` in `proguard-rules.pro`. VERIFY on a minified build: enable a background interval, then `adb shell cmd jobscheduler run -f com.riffle.app <job id>` (see rss-refresh.md). |
| `NotificationListenerService`, `AccessibilityService` | Listener: see above. No AccessibilityService. | No |
| Enums persisted by name (settings/layout/notification JSON codecs: `valueOf(...)` and `enumValues<T>().firstOrNull { it.name == ... }`) | Names come from the `Enum` name string passed to the enum constructor, which R8 keeps for `name`, `valueOf` and `values()`. Both access paths are direct, not reflective. Unknown or missing values are already handled with `runCatching`/`firstOrNull`. | No. High-priority on-device check: backup import and upgrade-in-place with existing data (checklist below). |
| `org.json` | Platform classes on device, not bundled; unit tests use `org.json:json` as `testImplementation` only. | No |
| kotlinx.serialization / Gson / Moshi / Room | Not used. | No |
| `Class.forName`, `ServiceLoader`, JNI, `kotlin.reflect`, `@Keep`, `Serializable`, custom `Parcelable` | No matches in app or domain sources. Native libs belong to AndroidX (`graphics-path`, `datastore-shared-counter`), which ship their own rules. | No |
| Compose runtime/UI | No reflection; ships consumer rules. `ui-tooling-preview` is a compile-time annotation only. | No |
| DataStore Preferences | Bundles lite protobuf; the library ships consumer rules for its generated messages. Used by five DataStore-backed stores. | No (verify via the on-device persistence checks) |
| `androidx.window` (1.4.0) | Loads its OEM extensions reflectively and compiles against stubs. Ships its own rules. | Defensive `-dontwarn` for `androidx.window.extensions.**` and `androidx.window.sidecar.**` only, marked VERIFY: delete if R8 does not need them. |
| coroutines, emoji2, lifecycle, activity, palette, core | Ship consumer rules. | No |
| ical4j (`Recur` only, via `core/recurrence-ical4j`) | No reflection on the used path: the calendar parser, `TimeZoneRegistry` and the `ServiceLoader` factories are never called. The jar names optional Groovy classes, the JDK-only `java.beans.Transient`, `java.time.zone.ZoneRulesProvider` (not in the Android SDK), and, through threeten-extra, `org.joda.convert.*`. | `-dontwarn` for those four (see below); no keep rule. Time-zone resources, the `ZoneRulesProvider` service entry and the Groovy extension descriptor are excluded from the APK with `packaging.resources.excludes`. |
| Roborazzi, Robolectric, Compose test, junit | `testImplementation`/`androidTestImplementation`/`debugImplementation` only; not in the release variant. | No |
| `allowBackup` / `data_extraction_rules` | Reference a DataStore file path, not class names. | No |

Rules in `app/proguard-rules.pro` and why:

| Rule | Justification |
| --- | --- |
| `-keepattributes SourceFile,LineNumberTable` and `-renamesourcefileattribute SourceFile` | Keeps stack traces mappable with the retained `mapping.txt`. No size or behaviour effect of note. |
| `-keep class ...RiffleNotificationListenerService { *; }` | Defensive and redundant with the manifest rule; protects the name-matched system binding from future refactors. |
| `-dontwarn androidx.window.extensions.**`, `...sidecar.**` | Classes that exist only on device; prevents a missing-class build failure under full mode. VERIFY. |
| `-dontwarn groovy.**`, `-dontwarn java.beans.Transient`, `-dontwarn java.time.zone.ZoneRulesProvider`, `-dontwarn org.joda.convert.**` | ical4j (ICS source, #1409): classes named by the jar that are absent from Android or not dependencies, and are not on the `Recur` path. Found by the first CI run (R8 reported the last two as missing). |

The goal is a small rule file. Blanket keeps (for example `-keep class com.riffle.** { *; }`) would
defeat the purpose and are not used.

## Staged plan

### Stage 1 (this PR): measure and smoke, change nothing shipped

- `app/build.gradle.kts`: `riffle.minify` Gradle property (default `false`) drives
  `isMinifyEnabled` and `isShrinkResources` on the existing `release` build type. A separate
  `minifiedRelease` build type was rejected: it would add a variant to `check`, doubling unit
  tests, lint, and screenshot tasks in `./gradlew verify`, and signing/`matchingFallbacks` for
  every dependency. The property keeps `verify` identical and the shipped workflows untouched.
  Neither `alpha-release.yml` nor `stable-release.yml` passes the property, so they cannot minify.
- `.github/workflows/minified-release-check.yml` (never publishes):
  1. builds release twice from the same commit (unminified, then `-Priffle.minify=true`) and writes
     a size delta plus per-entry composition to the job summary (MEASURED, same commit);
  2. uploads the minified APK and R8 `mapping.txt`/`usage.txt`/`seeds.txt` as a 3-day artifact;
  3. installs the minified APK on an API 35 emulator, launches it as Home, runs 400 seeded monkey
     events, and fails on any crash or `ClassNotFound`/`NoSuchMethod`/`NoSuchField` signal.
  It runs on pull requests that touch the build, rules, or this workflow, and on demand.
  By default it signs with a throwaway per-run key. A manual dispatch with `use_release_key=true`
  signs with the release secrets and a `version_code` input so the owner can install it over the
  published alpha for the device checklist below.
- `app/proguard-rules.pro`: the draft rules above, applied only when R8 runs.
- JVM unit tests are not run against the minified output: AGP unit tests always run against
  unminified classes, so that would prove nothing. Equivalent coverage needs instrumented tests on a
  minified test build type (the `ui-test-manifest` dependency is `debugImplementation`), which is
  out of scope here; the emulator smoke plus the owner's device pass is the stage-1 safety net.

### Stage 2: the flip PR (after the owner's device pass)

One-line change in `app/build.gradle.kts`:

```kotlin
.getOrElse(true)   // was false; or delete the property and set isMinifyEnabled/isShrinkResources = true
```

plus: record the measured APK size in `apk-size-budget.md` as the new baseline, lower the size budget
in `alpha-release.yml` and `stable-release.yml` (`-MaxBytes`) to measured size plus about 15%
headroom, and keep `minified-release-check.yml` (rename to a release-contract check) or fold the
emulator smoke into `ci.yml`. Retain `mapping.txt` for every published alpha: add it as a release
asset or a longer-retention artifact in `alpha-release.yml`, otherwise crash traces from installed
builds cannot be deobfuscated. Do not flip before that is in the same PR.

Rollback is the same one-line revert; no data format changes.

### Stage 3: follow-ups

- Dex compression decision (see below) and a `:baselineprofile` module (#1268).
- Re-review keep rules when RSS parsing or any serialization library is added (ical4j: reviewed with #1409).

## Owner device checklist (before the flip)

Install the signed minified APK from a `use_release_key` dispatch over the current alpha (same key,
upgrade in place, no clearing data), then work through:

1. Cold launch and first open. Set/confirm Riffle as the Home app; press Home from another app.
2. Existing data survives the upgrade: layouts, pages, dock, settings, workspaces, exclusions, RSS
   cache all intact (this exercises DataStore and enum-by-name decoding).
3. Home pager and card stack navigation, animations, reduced-motion setting.
4. App drawer, search, launching apps, app shortcuts/long-press menus, icon rendering and dominant
   colour (Palette).
5. Widgets: open the picker, bind (including one that needs the bind-permission dialog), configure,
   resize, remove; reopen after process death.
6. Notification access: grant, notifications appear, tap/dismiss/actions, media session cards;
   revoke and re-grant.
7. Calendar permission: request from Settings, grant, deny, and the calendar-driven surfaces.
8. Settings backup export, then import it back (and import a file exported by the previous alpha).
9. Workspaces: preview, switch, edit, exclusions.
10. Overlay dock service (overlay permission, edge, expanded orientation).
11. Every Settings page opens without a crash.
12. Rotate, split screen, and fold/unfold if available; font-size and display-size changes; TalkBack
    on home and drawer.
13. Leave it installed for a day: `adb logcat -b crash` (or the system bug report) shows no
    `ClassNotFoundException`, `NoSuchMethodError`, or `NoSuchFieldError`.

Any crash: fetch the artifact's `mapping.txt`, deobfuscate with R8 `retrace`, and add a targeted keep
rule with a justification comment. Do not add a broad keep.

## Risks

- A missing keep rule only fails at runtime in the code path that needs it. Mitigation: no
  publishing from this workflow, emulator smoke, the checklist above, and a small rule surface.
  The app has no reflection, so the likeliest miss is a third-party library edge (window extensions,
  DataStore protobuf).
- R8 can change behaviour that depends on undefined timing or on `assert`-like code; Compose
  performance generally improves, but verify jank on the device.
- Obfuscated crash traces without `mapping.txt`. Retain it per build before the flip.
- Resource shrinking cannot see resources referenced by name; the app has none
  (`getIdentifier` is unused), but check before adding any.
- First CI run of this workflow was not run by the author (the build environment could not reach
  the Android Gradle plugin repository). Workflow, rules, and Gradle edits are unverified until CI
  executes them; the `-dontwarn` lines may be unnecessary and the emulator step may need tuning.

## Cheaper wins independent of R8 (not applied)

- **Compress dex** (largest measured lever, 24.27 to 8.26 MiB of dex): AGP's packaging options
  expose a dex legacy-packaging switch (believed to be `packaging { dex { useLegacyPackaging = true } }`;
  confirm the exact DSL against AGP 8.7 before use). Separate small PR, verified by CI size output.
  Install footprint grows; acceptable for a sideloaded alpha, and moot if shipped as an AAB.
- **`localeFilters`**: about 0.2 to 0.3 MiB; rejected for now (accessibility strings).
- Material icons extended, image libraries, extra ABIs, bundled fonts: none present, nothing to do.
- Remove unused dependencies: nothing is clearly unused. `androidx.palette` is used by
  `AppIconDominantColor.kt`; `androidx.window` by device-class/widget sizing.
