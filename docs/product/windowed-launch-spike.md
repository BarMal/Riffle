# Windowed launch: feasibility spike

Status: report only (issue #1357, WS9). No product code. Answers open question 4 in
[workspaces-sources-lenses.md](workspaces-sources-lenses.md).

Every claim is tagged:

- **VERIFIED**: read in official Android documentation during this spike (URL cited).
- **UNVERIFIED**: from general platform knowledge or secondary sources; the sandbox could not open the
  AOSP source (`android.googlesource.com` is blocked) and the `developer.android.com` API reference pages
  were truncated by the fetch tool. Needs a device test (script at the end).

## Targets and what the code does today

- `build-logic/.../riffle.android.application.gradle.kts`: `minSdk = 31`, `targetSdk = 35`,
  `compileSdk = 35`. So the floor is Android 12 (API 31); Android 12L (API 32) behaviour is the
  large-screen baseline we can only assume on foldables and tablets that ship 12L or later.
- All app launches go through `AndroidAppLauncher` (`app/.../launcher/apps/AndroidAppLauncher.kt`):
  `LauncherApps.startMainActivity(component, user, null, null)` and
  `LauncherApps.startShortcut(pkg, id, null, null, user)`. The last-but-one argument pair is
  `sourceBounds` and `opts` and both are `null` today. Other `startActivity` calls open settings, app
  info, uninstall, browser and wallpaper intents, none of which are launcher-of-apps flows.
- A grep for `ActivityOptions`, `LAUNCH_ADJACENT`, `setLaunchBounds` and `FREEFORM` finds nothing in the
  repo. Today every launch is whatever the system chooses by default (full screen on phones).
- `MainActivity` is `launchMode="singleTask"` and declares `configChanges` for size changes; it does not
  declare `resizeableActivity` (defaults true for target 24+, VERIFIED below).
- Consequence for design: there is exactly one seam to add launch options, the `opts: Bundle?` parameter
  of `LauncherApps.startMainActivity` / `startShortcut` (public API since API 21, UNVERIFIED against the
  reference page, but this is the documented signature). Any later windowed work belongs in
  `AndroidAppLauncher`, behind an interface, not in UI code.

## 1. Full-screen launch (baseline)

What it is: the current behaviour. A launcher starts a main activity and the system decides placement.
On a phone in normal mode that is full screen in a new task; on a tablet or a device in desktop mode it
may be windowed by the system with no request from us.

- Honoured: always. This is the only mode Riffle can promise on every device.
- Failure mode: none we control (the target app may be disabled, suspended or in a locked profile; that
  already returns `false` from `launch`).

## 2. Adjacent / split launch

API: `Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT` combined with `FLAG_ACTIVITY_NEW_TASK`.

VERIFIED (https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode and
https://developer.android.com/develop/ui/views/layout/support-multi-window-mode):

- The flag "was introduced in Android 7.0 (API level 24)" and must be used "in conjunction with
  `FLAG_ACTIVITY_NEW_TASK`".
- "On Android 12L (API level 32) and higher, the flag allows an app running full screen to enter
  split-screen mode and launch a target activity into the adjacent window." Before 12L the caller had to
  already be in split-screen.
- "OEMs can enable 12L behavior on older Android versions, in which case `FLAG_ACTIVITY_LAUNCH_ADJACENT`
  functions as it does on API level 32." So behaviour on API 31 devices is OEM-dependent.

VERIFIED from the search-result summaries of the same page, treat as secondary: the system makes a best
effort, "it is not guaranteed", and the flag has no effect when not in split-screen mode (the latter
applies to pre-12L).

Multi-window rules that decide whether the target can appear next to us
(https://developer.android.com/guide/topics/large-screens/multi-window-support):

- VERIFIED: `resizeableActivity` defaults to `true` when targeting API 24+.
- VERIFIED: API 31+, large screens (medium or expanded window size class): "All apps support multi-window
  mode"; `resizeableActivity="false"` only puts the app into compatibility mode (letterboxed). Android 12
  "makes multi-window mode standard behavior... regardless of app configuration" on large screens.
- VERIFIED: API 31+, small screens: multi-window only if `resizeableActivity="true"` and the activity
  minimum width/height fit; `false` means no multi-window "regardless of activity minimum width and height".
- VERIFIED: apps targeting API 36+ have `resizeableActivity` ignored on displays with smallest width >= 600dp.
- VERIFIED: if the user shrinks a window below the activity's declared `<layout minWidth/minHeight>`, the
  system crops it. "Device manufacturers can override these multi-window behaviors."

Per device class (design expectation, all UNVERIFIED on hardware):

| Device | Expected result of a launch-adjacent request from Riffle |
| --- | --- |
| Phone (compact) | Riffle full screen on a 12L+ build: system may split, then place target adjacent. Apps with `resizeableActivity=false` or large minimum size refuse split on small screens, so they open full screen. Many phones have no split affordance at all. |
| Foldable unfolded | Most likely to work (large screen, 12L+ behaviour, all apps splittable). OEM split-screen policies still vary. |
| Tablet | As foldable unfolded. |
| Desktop mode | System policy decides; the flag may be ignored in favour of freeform placement. |

Activity embedding (Jetpack WindowManager) is not a route for us: VERIFIED it "splits activities within the
same app task" (same page), so it applies only to activities of one app. A launcher cannot embed a third
party's activity in its own task.

Honest conclusion: adjacent launch is a best-effort request. Riffle can ask; the system or the target app
may decline silently (the target simply opens full screen, replacing Riffle). There is no result callback
saying "was adjacent". Detect after the fact only (see section 6).

Question for the product: when Riffle itself is the full-screen app and the user launches adjacent, on 12L+
the system enters split-screen with Riffle in one pane. That is acceptable only if Riffle's layout is sound at
half width (it should be: it is already a window-size-class driven layout, but this is UNVERIFIED for the
Home/Library surfaces and worth a screenshot at ~half width).

## 3. Freeform / desktop windowing

API surface (be precise):

| API | Public? | API level | Status |
| --- | --- | --- | --- |
| `ActivityOptions.setLaunchBounds(Rect)` | Public SDK | 24 | VERIFIED public, documented as the way to size/position a new activity "in desktop windowing mode"; "no effect if the device is not in multi-window mode" (https://developer.android.com/develop/adaptive-apps/guides/support-multi-window-mode). Secondary source: ignored without `FEATURE_FREEFORM_WINDOW_MANAGEMENT` or PiP feature (UNVERIFIED wording). |
| `ActivityOptions.setLaunchDisplayId(int)` | Public SDK | 26 | UNVERIFIED (general knowledge). Also gated by device support for secondary displays and caller permission rules. |
| `ActivityOptions.setLaunchWindowingMode(int)` | `@hide` / `@TestApi` | n/a | UNVERIFIED (could not read source). Search results show it only inside platform source. Treat as not callable by a third-party app. A fetch tool answer claiming it is public is not trustworthy (it said "from standard Android documentation knowledge"); disregard it. |
| `WINDOWING_MODE_FREEFORM` and the `WindowConfiguration` constants | `@hide` | n/a | UNVERIFIED, same caveat. |
| `android.permission.MANAGE_ACTIVITY_TASKS` / `START_TASKS_FROM_RECENTS` | signature | n/a | UNVERIFIED. System-only. Not obtainable by a launcher, even as the default home app. |

So the only supported freeform lever is `setLaunchBounds`, and it only does anything on devices that are
already freeform-capable and currently in a windowed mode. It cannot force a phone into freeform.

Enabling freeform is device policy, not an app choice:

- UNVERIFIED: AOSP exposes Developer options "Enable freeform windows" (and, on newer builds, "Force
  desktop mode") and the system feature `android.software.freeform_window_management`. Chromebooks, some
  tablets and OEM desktop modes (e.g. Samsung DeX, Android 16 desktop windowing on large screens) enable
  it. The documentation fetched for this spike "does not contain information about enabling freeform mode
  in developer options or specific instructions for activating desktop windowing mode through system
  settings".
- A launcher cannot toggle these. `Settings.Global` freeform keys are not writable (needs
  `WRITE_SECURE_SETTINGS`, signature/privileged; UNVERIFIED but standard).

Honest conclusion: freeform is "where the device already supports and has enabled it". Riffle may pass
launch bounds there; elsewhere it must silently degrade.

## 4. Pop-up / bubble-like options

- VERIFIED (https://developer.android.com/develop/ui/views/notifications/bubbles): a bubble is created
  through the notification API: build `BubbleMetadata` (PendingIntent+Icon, or shortcut id), attach it
  with `setBubbleMetadata()`; targeting API 30+ it must reference a sharing shortcut; the bubble activity
  "must be resizeable and embedded" (`allowEmbedded="true"`, `resizeableActivity="true"`). The page offers
  no way for one app to open another app as a bubble.
- Consequence: bubbles are the posting app's own feature. A launcher can neither bubble a third-party app
  nor tell it to. Riffle could only bubble its **own** activities (for example a Riffle quick panel) by
  posting its own notification, which is a different feature and not a launcher tool. Do not promise it.
- Picture-in-picture is likewise initiated by the app that is playing media, not by the launcher.
- `SYSTEM_ALERT_WINDOW` overlays are already declared in the manifest, but that draws Riffle's own window
  over others; it does not host another app's activity. Not a route.

## 5. What shortcuts and widgets give instead (the fallback)

- Shortcuts: `LauncherApps.startShortcut` (and `getShortcuts` for static, dynamic and pinned shortcuts)
  gives deep links into an app's functions. Already implemented in `AndroidAppLauncher.launchShortcut`.
  These open the target app normally (full screen); they inherit sections 1-3 for placement.
- App widgets (`AppWidgetHost`) render the app's own `RemoteViews` inside Riffle's window. That is the
  only way to show a third party's live UI "in place" without windowing. Availability depends on whether
  the app ships a widget.
- Notifications and media sessions already feed Riffle cards (Notifications and Media sources) and give
  rich content for some apps.
- So the "card" strategy for an arbitrary app is: app icon + shortcuts + widget where one exists +
  notification content where present. This matches the existing feasibility note; the spike confirms it.

## 6. Runtime capability detection and safe failure

Detect, never assume. All are cheap and have no permissions.

| Need | API | Level | Tag |
| --- | --- | --- | --- |
| Freeform supported at all | `PackageManager.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)` | 24 | UNVERIFIED constant docs; widely used |
| Multi-window supported | `Activity.isInMultiWindowMode()` (are we in it now) | 24 | VERIFIED name/level via API reference summary; behaviour on resize via `onMultiWindowModeChanged` |
| PiP supported | `hasSystemFeature(FEATURE_PICTURE_IN_PICTURE)` | 24 | UNVERIFIED |
| Large-screen / window class | Jetpack `WindowSizeClass` / `WindowMetricsCalculator` (current repo already uses window-size driven layout) | n/a | VERIFIED pattern, size class thresholds 600dp/840dp from adaptive docs; not fetched here |
| Foldable posture | Jetpack WindowManager `WindowInfoTracker` (`FoldingFeature`) | n/a | UNVERIFIED here |
| Currently in a free window | `isInMultiWindowMode` plus window bounds vs display bounds via `WindowMetrics` | 30 | UNVERIFIED |
| Developer-options flags | `Settings.Global` freeform keys | n/a | Not a supported detection route: hidden/unstable keys; use `hasSystemFeature` instead. UNVERIFIED but we recommend against reading them. |

Safe failure rules to bake into any implementation:

1. Build launch options only when the capability probe says the mode is plausible; otherwise pass `null`
   options (today's behaviour).
2. Wrap in `runCatching` as the launcher already does. `startMainActivity` can throw
   `SecurityException` / `ActivityNotFoundException`; on failure retry once with `null` options, then
   report `false`.
3. Never block on confirmation: the system gives no result. Treat "requested" and "honoured" as different;
   the UI must not label something "opened beside" unless it was observed (for example by `Configuration`
   after the next resume), and should not offer the choice at all when the probe says no.
4. Behaviour is gated by device policy and may change per OEM/OS update; hide, do not disable-with-error.

## 7. Recommendation (describe only; do not implement in this PR)

### Domain model (pure Kotlin, no Android types, in `core/domain`)

```kotlin
enum class WindowedLaunchMode { FULL_SCREEN, ADJACENT, FREEFORM }

/** What the device is believed to honour right now. FULL_SCREEN is always present. */
data class WindowedLaunchCapability(
    val supported: Set<WindowedLaunchMode>,
    val confidence: Confidence, // PROBED_FEATURE vs ASSUMED_FROM_SIZE_CLASS
) {
    fun supports(mode: WindowedLaunchMode): Boolean = mode in supported
    fun resolve(requested: WindowedLaunchMode): WindowedLaunchMode =
        if (supports(requested)) requested else WindowedLaunchMode.FULL_SCREEN
}
```

`resolve` is the single place the "fail safely to full screen" rule lives and is unit-testable without a
device. The dock/workspace menu asks "does the capability support ADJACENT?" and shows or hides an
"Open beside" item accordingly.

### Platform interface (app layer)

```kotlin
interface WindowedLaunchCapabilityProvider { fun current(windowInfo: WindowInfo): WindowedLaunchCapability }

interface AppLauncher { // extends the existing AndroidAppLauncher surface
    fun launch(identity: AppIdentity, mode: WindowedLaunchMode = WindowedLaunchMode.FULL_SCREEN): LaunchOutcome
}
// LaunchOutcome: REQUESTED(mode), FELL_BACK_TO_FULL_SCREEN, FAILED
```

The Android implementation maps modes to `ActivityOptions` bundles passed as the existing `opts`
parameter of `LauncherApps.startMainActivity` (ADJACENT may need the flag via an intent route if
`LauncherApps` cannot carry it; UNVERIFIED, test below), and `setLaunchBounds` for FREEFORM. A fake
provider covers JVM tests.

### What the Riffle plan should promise

- Promise: full-screen launch everywhere; shortcuts, widgets and notification content as the in-card
  richness; unfolded layouts that make Riffle itself a good split-screen citizen.
- May offer, only after a runtime probe and best-effort: "Open beside" (adjacent) on large-screen and
  foldable-unfolded devices on Android 12L+; "Open in window" (launch bounds) on devices that report
  `FEATURE_FREEFORM_WINDOW_MANAGEMENT`.
- Must not promise: forcing split or freeform on phones; pinning apps to zones; Bubbles or PiP for third
  party apps; any `setLaunchWindowingMode`-style control (hidden API); anything the target app's
  `resizeableActivity`/minimum size can veto.
- Proposed doc edit: replace "Spike required before design" in the Feasibility notes with a pointer to
  this report, and keep "adjacent on unfolded second" as an enhancement, gated and best-effort.

### Open question 4 answer (provisional)

Adjacent: sometimes (12L+ large screens; best-effort). Freeform: only on devices already in a freeform
mode, with `setLaunchBounds` as the one public lever. Confirm on the owner's target devices with the
script below.

## Manual device test script

A throwaway debug build (not shipped) is needed: temporarily patch `AndroidAppLauncher.launch` to pass an
options bundle, or use `adb` from a second app. Alternative needing no code: use
`adb shell am start --activity-launch-adjacent -n <pkg>/<activity>` for adjacent and
`adb shell am start --windowingMode 5 -n ...` (5 = freeform; UNVERIFIED flag, shell has the hidden
permissions the app lacks, so adb success does NOT prove an app can do it). Record Android version, OEM,
and result for each row.

Common: set Riffle as default home. Pick three target apps: one stock Google app (resizable), one
third-party known to be non-resizable, one with a tiny minimum size.

1. Phone (compact, Android 12-15)
   - From Riffle full screen, request adjacent launch for each target app. Expected: full screen, or
     split only on 12L+ OEM builds. Note whether Riffle stays visible.
   - Request launch bounds. Expected: ignored, full screen.
   - Run `adb shell pm has-feature android.software.freeform_window_management` (or
     `adb shell cmd package has-feature ...`). Expected: false.
2. Foldable unfolded
   - Repeat adjacent for the three apps, in both orientations and each posture (flat, tabletop).
   - Check Riffle half-width layout; check that the divider can be dragged and that the non-resizable
     app is letterboxed rather than refused.
   - Check `hasSystemFeature` for freeform.
3. Tablet
   - Same as foldable. Also test with and without the taskbar. Repeat with Riffle already in split view.
4. Desktop mode (Developer options: enable freeform windows, then reboot; or a desktop-mode device)
   - Confirm feature flag is true. Request launch bounds with a 600x800dp rect at an offset; confirm
     placement and that a second request does not reuse the first window.
   - Request adjacent; note whether the system snaps or ignores it.
5. Every device: after each request, observe via `adb shell dumpsys activity activities | grep -E
   "windowingMode|mActivityType|Task"` which windowing mode the target task actually got, and compare
   with what the app requested. This is the evidence that settles each UNVERIFIED row above.
6. Failure checks: launch with an options bundle on a device where the probe says unsupported; confirm
   the null-options retry opens the app full screen and no crash or silent no-op occurs.
