# Workspaces: user configuration (WS10)

Status: proposed design, **second revision 2026-10-01** (owner answers N1 to N9, then rescoped for a solo
alpha), still before any code. Tracking: #1363 (WS10 parent), #1364 (saved lenses). Related: #1323, #1324,
#1325 (legacy mode/settings problems this resolves), #1365 (RSS and Search adapters, merged), #1366 (other
external sources, researched, merged), #1374 (RSS refresh, separate issue, not WS10).

This document is about how a person *configures and lives with* the workspace system. The model itself
(sources, lenses, expressions, containers, workspaces) is in
[`workspaces-sources-lenses.md`](workspaces-sources-lenses.md); the editor (WS7), presets (WS8) and
the dock menu (WS6) are separate workstreams and are dependencies here, not scope.

Sections are split into **As built today** (verified against the code at the commit this was written)
and **Proposed**. Open decisions are collected in [Open questions](#open-questions-for-the-owner).

## Scope for a solo alpha

Riffle has **no user base**: it is a personal alpha with one developer, and the owner's own phone is the only
install. This changes what the design must protect, so the document is scoped accordingly.

**In scope (all feature decisions stand):** shared placed-items pool with per-workspace arrangements (section
9), per-layout saved lenses (4), per-layout exclusion rules (14), per-workspace dock overrides (11),
per-lens search queries (13), the Return setting and start page (3.2, 8.3), New-apps-per-preset (9.8),
recency and favourite lenses (12), ICS recurrence through a library (15), settings IA and backup (2, 5).

**Out of scope, deliberately:**

| Dropped | Why it is not needed here |
| --- | --- |
| Staged rollout R0 to R5, "zero mismatches across the beta population", flip criteria beyond a short checklist | The owner's device is the whole population. |
| Shadow compare, dual-write mirror, `PlacedItemsOwner` rollback switch, rollback drills, a one-release revert window | A **backup export plus git** is the revert (1.7). |
| Compatibility with old stored formats beyond the owner's own data | A one-time migration of the owner's layout, or a **destructive reset**, is acceptable (1.7 says where). |
| Golden tests whose only job is to protect other users' data | Tests that guard real logic bugs are kept (7.1). |
| Play data-safety and privacy-policy checklist lines | Only matter if the app is ever published; revisit before that (7.2). |
| "Classic" as a supported product path | Default plan: at most a developer toggle, then dropped (section 10). Full parity is an explicit option with its cost. |

**Working rules that replace the rollout machinery.** (1) Get a **runnable, dogfoodable debug build** onto the
owner's phone first (S1, S2 in section 7), then add one slice at a time, each run on the device before the
next builds on it: the main danger is a large amount of agent-written code that has never run on a device.
(2) Take a backup export before any slice that changes how data is stored. (3) Fix forward.

> **Revision 2026-10-01 (2): N1-N9 answers.** The owner answered the nine open questions of the first revision
> (comment on #1363). Where an answer matched the recommendation the text was updated in place; the rows
> marked **(differs)** redesigned a section. A later instruction rescoped the whole document for a solo alpha
> (above), which removed most rollout text rather than marking it.
>
> | N | Decision | What changed in this document |
> | --- | --- | --- |
> | N1 | Shadow now, cut over with the flip (option C), fall back to A | **Replaced by the solo plan** (1.7): take a backup, run the migration on the owner's device, flip, fix forward. No shadow compare, no mirror, no R-stages. The staging table and flip criteria are deleted. |
> | N2 | **(differs)** Shared pool per layout, per-workspace arrangements | Section 9 rewritten: pool model, widget single-placement rule, reference derivation and GC, deletion and Undo, copy semantics, uninstall, restore, migration, adapter. The workspace-owned `PlacedItemsPage(page: LauncherPage)` design is superseded and deleted. Q12 and Q16 text updated. |
> | N3 | Recency lens now, counter later | Section 12 (kept; UI name "Recently used"). |
> | N4 | **(differs)** Exclusion rules are per layout | 14.6 rewritten (storage, migration, copy semantics, UI cues). Layered evaluation order kept. One consequence needs a decision: rotating a phone changes the layout (10.2, new question N10). |
> | N5 | Channel/thread/calendar-id keys after the flip | 14.4, cut line (7). |
> | N6 | Hidden dock requires a bound gesture | 11.4 (kept). |
> | N7 | **(differs)** Classic gets every feature | Section 10 rewritten: what Classic would be, its cost, why the default plan is a developer toggle only, naming recommendation. Presented as an option for the owner to confirm. |
> | N8 | **(differs)** Use a library for ICS recurrence | Section 15.2: criteria, web-verified shortlist, recommended pick, spike plan, isolation behind a domain interface. Note in `workspaces-external-sources.md`. |
> | N9 | New-apps default per preset, as a setting | 9.8. |
> | - | Solo alpha rescope | New "Scope for a solo alpha"; sections 1, 5, 7, 9, 10, 16 and the open questions rewritten shorter; slices cut from 20 to 12. |

> **Revision 2026-10-01 (1): owner decisions Q1-Q19.** Kept as a short history; rows marked *(rev 2)* were changed again.
>
> | Q | Decision | Where |
> | --- | --- | --- |
> | Q1 | Preview: internal/beta only | Moot for a solo alpha. |
> | Q2 | Keep "Use workspaces / Classic" permanently | *(rev 2)* superseded by N7 and the solo-alpha scope: section 10. |
> | Q3 | Lens library **per layout** | Section 4. |
> | Q4 | Presets **use saved lenses** | 4.9. |
> | Q5 | Dock notification cards per workspace | 3.1. |
> | Q6 | Drawer presentation moves into the Finder page expression (`FINDER_EXPRESSIONS` gains `ICON_GRID`) | 3.1, 8.3a. |
> | Q7 | Return behaviour is a setting: Restore / First page / Start page | 3.2. |
> | Q8 | Drop the locked-device rule; optional screenshot/recents setting | Section 6. |
> | Q9 | Dock pull opens the workspace menu once modes retire | 11.5. |
> | Q10, Q11 | Single-workspace export deferred; restore = replace with Undo | Section 5. |
> | Q12 | `home.grid` page-id uniqueness: a test, now **mooted** by the pool | 9.7. |
> | Q13 | Add `OFF` source status | 2.4. |
> | Q14 | Start page per workspace; Finder out of the pager; iOS-style home | 8.3, 9.8. |
> | Q15 | Skin = theme preset, per-workspace override | 8.7. |
> | Q16 | Placed items move into workspaces now | *(rev 2)* redesigned as a shared pool: section 9. |
> | Q17 | Per-workspace dock overrides | Section 11. |
> | Q18 | One default: Nova with Finder | 8.1. |
> | Q19 | Favourite/frequent as All-apps lenses | Section 12. |
> | - | Exclusion rules: layered, unified, contextual + Settings authoring | *(rev 2)* scope changed to per layout: section 14. |
> | - | Per-lens search queries now; external items allowed in the dock; ICS full recurrence; tokenised URLs excluded from backup | Sections 13, 15, 5. |

## Fixed decisions (owner)

1. The workspace system is **the default**, not opt-in, and part of normal configuration (Settings). New
   installs get a preset (Nova-style by default). If anything fails to decode or resolve, Riffle falls back to
   the built-in default workspace rather than a blank home. Standard launcher parity (home, drawer, dock,
   settings) must keep working throughout.
2. Lenses are **saved and reusable** (a named lens library, **per layout**, Q3), and presets use saved lenses (Q4).
3. RSS and Search adapters are in scope (#1365). Other external sources are explored in #1366.
4. Placed items (apps, folders, widgets, shortcuts on home pages) live in a **shared pool per layout with
   per-workspace arrangements** (N2, section 9). `HomeLayoutSet` stops being the source of truth.
5. The dock has **per-workspace overrides** (Q17), lenses may take **per-lens search queries** (section 13), and
   all hiding becomes one **source exclusion** model that is **per layout** (N4, section 14).
6. **"Classic gets every feature" (N7)** is recorded, but for a solo alpha the default plan is a developer
   toggle only; full parity is an option the owner confirms (section 10).

These are not re-argued below.

## What exists today (as built)

Verified in the code, because it changes what "default" has to mean:

| Fact | Where |
| --- | --- |
| The workspace menu is gated by `WorkspaceMenuFeature.enabled`, `false`, referenced nowhere else in `app/` yet. | `app/.../launcher/WorkspaceMenuFeature.kt` |
| `DataStoreWorkspaceStore` (one JSON blob, key `workspaces`, DataStore `riffle_workspaces`) exists but is **not constructed or called anywhere**. `WorkspaceMigration.ensureMigrated` is not called by the app either. | `app/.../launcher/DataStoreWorkspaceStore.kt`; grep of `app/src/main` |
| Home layout is read once, blocking, at startup (`loadHomeLayoutSetAtStartup`), then served from memory and written behind. | `app/.../launcher/HomeLayoutStartupLoad.kt` |
| The shipped app offers **Library only**: `libraryOnlyLauncherViewModeAvailability()` (`alwaysAvailableModes = emptySet()`, fallback Library). Stored layouts in a hidden mode resolve to Library on load. This is why Standard is unreachable (#1324) and why mode settings are hidden. | `LauncherShellPlatformDependencies.kt:43`, `MainActivityDependencies.kt:108` |
| `LauncherBackupDocument.workspaceSet` exists and `encode/decodeLauncherBackupDocument` read and write a `"workspaces"` object. **But nothing fills it on export** (`launcherBackupDocument()` and `LauncherBackupExportCoordinator` do not pass it) **and import ignores it** (`withImportedBackup` applies only layouts, settings, hidden apps; `isImportableBackup` does not look at it). So the claim "export already writes workspaces" is true of the document type only, not the shipped flow. | `LauncherBackupDocument.kt`, `LauncherBackupSnapshot.kt`, `LauncherBackupExportCoordinator.kt`, `LauncherBackupImportValidator.kt` |
| `decodeLauncherBackupDocument` requires `version == 1` exactly, so bumping the document version would make every older app reject newer backups. | `LauncherBackupDocument.kt` |
| `WorkspaceSetCodec` (schema 1) never throws; drops undecodable workspaces and empty layouts; `LayoutWorkspaces.repaired` fixes stale active/default ids. `decodeWorkspaceSet` in the app returns `null` for a non-object. | `WorkspaceSetCodec.kt`, `LayoutWorkspaces.kt`, `WorkspaceSetJsonCodec.kt` |
| `WorkspaceSet.resolveActive` already falls back to the layout default with reasons (`WorkspaceResolution.FellBack`), and the WS6 menu model carries `WorkspaceMenuFallback`. | `WorkspaceSet.kt`, `menu/WorkspaceMenuPlanner.kt` |
| Settings is a paged surface (`SettingsPage` enum, grouped main page with search aliases). RSS feeds, Permissions (incl. Calendar), Backup, Hidden apps, Gestures, Contextual, Motion each have a page. | `SettingsPages.kt`, `SettingsMainPageEntries.kt` |
| **As built:** `HomeLayoutSet` is the source of truth for placed items, pins, selected page and dock; migrated Home pages point at it through the `home.grid` source (no adapter exists for that id). Per owner decision Q16 this is the thing section 9 moves; "as built" here is what is on `main` today. | `workspaces-sources-lenses.md`, "Migration mapping" |
| `LauncherPage` / `LauncherItem` (`AppShortcutItem`, `FolderItem`, `WidgetItem` with `HostedWidgetId`) are plain Kotlin in `core/domain` with no Compose or Android types; the placement, collision, folder, widget and page engines (`GridPlacementEngine`, `FolderEngine`, `WidgetEngine`, `HomePageEngine`) operate on `HomeLayout`/`LauncherPage` values. This is what makes reuse in section 9 possible. | `core/domain/.../launcher/home/` |
| `HomePageEngine.duplicatePage` already **rejects** pages containing widgets (`CANNOT_DUPLICATE_PAGE_WITH_WIDGETS`): a hosted widget id is a live platform instance and cannot be cloned. | `HomePageEngine.kt` |
| Widget host ids are allocated and deleted through `WidgetHostGateway` (`allocateHostedWidgetId`, `deleteHostedWidgetId`); removal of a widget, a page or a dock widget calls `deleteHostedWidgetId`. | `app/.../launcher/widgets/AndroidWidgetHostGateway.kt`, `LauncherShellViewModel.kt` |
| `ContainerValidation.FINDER_EXPRESSIONS = {CATEGORIES, ALPHA_LIST}`: a Finder page cannot be an icon grid, while the drawer setting `AppDrawerPresentation` has `LIST` and `ICONS`. | `ContainerValidation.kt`, `LauncherSettings.kt` |
| `LauncherNotification` carries package, profile, category, title, text and key, but no channel id or conversation/thread id. `Item` ext keys exist for `app.profile`, `calendar.end`, `calendar.all_day`, and the `launcher.pinned` flag read by `LensSort.pinnedFirst` is defined but **no adapter sets it**. | `LauncherNotification.kt`, `AppItemMapper.kt`, `LensSorting.kt` |
| `FAVOURITES` / `FREQUENTLY_USED` generated pages produce **no items** today (`GeneratedLauncherPageContentPlan` returns empty; `favouriteAppsAvailable` defaults false); `RecentAppUsage` is `(package, lastUsedAtMillis)` only, there is no launch count. | `GeneratedLauncherPageContentPlan.kt`, `RecentAppRepository.kt` |
| Hiding today is two unrelated mechanisms: `AppVisibilityRepository` (hidden `AppIdentity` set, applied by `withHiddenApps` in `LauncherShellViewModel` and in `BuiltInItemSources`) and `NotificationHideRule` (app / title / body / empty content, exact / contains / wildcard, capped at `MAX_NOTIFICATION_HIDE_RULES = 200`, stored in settings and applied by `NotificationHideRuleFilter` in `NotificationItemMapper` and `NotificationCounterState`). | `AppVisibilityRepository.kt`, `NotificationHideRule.kt`, `NotificationHidingSettings.kt` |
| No auto-placement of newly installed apps on home pages exists in `core/domain` (searched); "pages appear as you fill them" and "new apps go to Home or Library only" are both new behaviour. | grep of `core/domain/.../home/` |

Consequence: "make it the default" is four distinct jobs, not one flag flip: wire storage and startup,
wire backup, build the Settings surface, and retire the mode settings that currently stand in for it.

---

## 1. Making workspaces the default

### 1.1 What "default" means

On every launch: the active layout's workspace is **resolved** (`WorkspaceSet.resolveActive(deviceClass,
capabilities, sources)`), the home surface draws it, the dock workspace menu exists (WS6) and Settings shows the
Workspaces section. The mode pair (Home/Library) stops driving what is shown (section 3). Until the pool
cut-over (S4) the placed items still come from `HomeLayoutSet` (the existing home surface is hosted as the
workspace's home page, 9.9); after it they come from the pool.

"Default" does not mean workspaces may leave the user without a home: bootstrap never throws and always has a
built-in default workspace to draw (1.5).

### 1.2 Startup sequence

Keep the existing constraint: no extra blocking work on the main thread beyond the layout read already done
(`loadHomeLayoutSetAtStartup`, one blocking read, IO dispatcher), and a bounded read for the workspace blob.

```
Process start
 ├─ loadHomeLayoutSetAtStartup()                (existing)
 ├─ loadWorkspaceBlobAtStartup(timeout 500 ms)  (new; null on timeout or error)
 ▼  WorkspaceBootstrap.run(blob, layoutSet)     (new, pure, never throws)
      decode -> (nothing stored? seed Nova preset) -> ensureMigrated -> validate active workspace
 => BootstrapResult(set, outcome)   outcome: Ready | Repaired(notice) | Fallback(reason)
```

`ensureMigrated` already never overwrites stored workspaces and is deterministic (`ws:<deviceclass>:<mode>`),
so it can run on every start; the bootstrap writes only when the result differs from what was decoded, off the
main thread (write-behind like `WriteBehindHomeLayoutRepository`, flushed in `onStop`). On timeout the
launcher draws the built-in Nova default and loads asynchronously.

### 1.3 Fresh data and the first-run chooser

With nothing stored the home is the **Nova-style preset** (WS8), seeded by `PresetInstaller.withDefaultsFor`
(fills only layouts with nothing stored). A non-blocking "Choose your home style" sheet in the existing
first-run flow offers the preset cards (2.3); Skip keeps Nova; it never delays the Home role request. This is
a nice-to-have for the alpha: with one user it can slip behind the Workspaces page's preset picker.

### 1.4 Flags

| Today | Becomes |
| --- | --- |
| `WorkspaceMenuFeature.enabled` (global mutable, default `false`) | **A developer toggle** "Use workspaces" under Settings > Developer (debug builds), default on in debug from S1. Deleted at S9 when workspaces are the only path. Tests keep a test-only override. |
| `libraryOnlyLauncherViewModeAvailability()` | Retired in S9: the mode pair stops being a user concept; the stored `LauncherViewMode` remains only as migration input. |
| `LauncherSettings.contextual.enabled`, `DockModel.showNotificationCards` | Unchanged storage; user-facing meaning reconciled in section 3. |

There is no persisted `WorkspaceRollout` record and no "Classic" user setting in the default plan (section 10).

### 1.5 Failure handling

Principle: **no failure may leave a blank home**, and a failure may not silently destroy the owner's data.

| Situation | Behaviour |
| --- | --- |
| No blob, layout set present (the owner's phone today) | `ensureMigrated`; write once. |
| No blob, no layout set | Seed the Nova preset (1.3). |
| Blob unreadable (not a JSON object) | Rebuild from `HomeLayoutSet` (before S4) or from the built-in Nova default (after S4, where the pool lives in the blob); keep the raw blob once in `workspaces_corrupt` for debugging; show one notice. **A destructive reset is acceptable here**: the owner has a backup export (1.7). |
| Some workspaces dropped by the codec | Layout still resolves (`LayoutWorkspaces.repaired`); notice names the count. |
| Active workspace invalid for this layout | `resolveActive` returns `FellBack`; default drawn; menu and Settings say why (1.6). No write. |
| Active and default both invalid | Draw `WorkspaceMigration.defaultFor`; the user can open Settings and Reset. |
| Blob has a newer schema than the app | Best-effort decode; do not write back. Only relevant if the owner downgrades a debug build; one line of code, keep it. |
| Any exception escapes bootstrap | Caught at the boundary: draw the built-in default; the error *type* is logged, never data. |

One previous generation of the blob (`workspaces_prev`) is cheap (one extra string per changing write) and
makes Undo after restore or after a bad edit exact; keep it, nothing more elaborate.

### 1.6 Per-layout behaviour and posture changes

- Workspaces, the pool (9), the lens library (4) and exclusion rules (14) are **per layout**
  (`HomeLayoutDeviceClass`), independent, as already built. Note the layout is derived from the window size
  at runtime (`HomeLayoutDeviceClassClassifier`: `PHONE`, `PHONE_LANDSCAPE`, `FOLDABLE`, `TABLET`, `DESKTOP`), so
  rotating a phone to landscape *changes layout*. That is pre-existing for layouts and is a real consequence
  for exclusions (14.6, N10).
- Posture change mid-use: the shell recomputes `resolveActive` for the new class, no write. The selected page
  is kept by container id (`PageSetSelection`) when the same workspace exists on both layouts, else the new
  layout's active workspace's first page.
- A layout that falls back shows the message in the menu and in Settings > Workspaces: "This layout can't draw
  *Index* on this screen, so *Standard* is shown. Edit workspace / Switch / Copy from other layout."
- Switching workspaces changes that layout's `activeId` and nothing else.

### 1.7 Plan for the owner's device (replaces the staged rollout and N1)

Option C of the first revision (shadow, then cut over with the flip) existed to protect users who cannot be
asked to re-place their icons. There are none. Plan instead:

1. **S1 and S2** give a debug build with workspaces drawn from a stored blob and the existing home hosted as
   the home page (9.9): nothing about the owner's placed items changes yet. Dogfood it.
2. **Before S4 (pool cut-over)**: Settings > Backup > Export on the phone, and keep the file. The migration of
   `HomeLayoutSet` into the pool (9.9) runs once, idempotently. Check by eye that every icon, folder and widget is
   where it was. If it is wrong: **reset and re-place by hand, or restore the backup export** on the previous
   build. The migration round-trip unit test (7.1) is the only automated guard; there is no shadow period.
3. **Flip** (S9): delete the developer toggle, retire the mode UI. Fix forward.

**Revert story.** git (revert the slice), plus the backup export (restore replaces everything, with Undo, 5).
Destructive resets are acceptable for: the workspace blob at any time; the owner's `HomeLayoutSet` at S4 *only after
a backup export*; exclusion rules at S6 (hidden apps and hide rules are re-creatable in minutes).

**"It works on my device" checklist before moving past S4 and again before S9:** home, drawer, dock, settings
reachable; every icon/folder/widget in place after a force-stop and a reboot; add, move, resize, remove a
widget; create a folder; switch workspace via the dock menu; fold/rotate keeps you in place; calendar
permission flow shows rationale before any system dialog; backup export then restore round-trips.

---

## 2. Settings information architecture

### 2.1 Where it lives

Today Settings has five groups (`SettingsPageGroup`): Home & layout, Appearance, Interaction &
accessibility, Apps & content, Permissions/privacy & backup. Proposal, keeping that structure:

| Group | Entry | Notes |
| --- | --- | --- |
| Home & layout | **Workspaces** (new, first row) | Switch, presets, rename, clone, delete, reset, copy to other layout, per-workspace **Dock** and **Start page** rows, Advanced (restore previous copy; the developer toggle lives under Settings > Developer, 1.4). |
| Home & layout | Layout | Slimmed: grid, labels, dock-adjacent geometry, **"Returning to Home" (Restore / First page / Start page, default Restore; Q7, 3.2)**, "New apps" (placement, 9.8). No mode, no template (section 3). |
| Home & layout | Dock | Device-class dock: pins, edge, size, appearance. A workspace may override edge, size, visibility and the dynamic section (section 11); this page shows "Overridden by workspace X" where one applies. |
| Home & layout | Floating dock | Unchanged. |
| Apps & content | **Sources** (new) | Replaces "RSS feeds" as a row; RSS becomes a source detail. Old route stays as an alias. Hosts **exclusion rules** (section 14): per-source list plus an "All exclusions" row. |
| Apps & content | **Saved lenses** (new) | The library **for the layout shown in the device tab** (per layout, Q3). |
| Apps & content | App drawer, Hidden apps | App drawer: presentation moves into the Finder page expression (Q6, 3.1). **Hidden apps** becomes a filtered view of the app exclusion rules (section 14); same page, same behaviour. |
| Permissions, privacy & backup | **Privacy** (new) | Section 6 (no locked-device rule; optional screenshot/recents setting; content level per source; links to exclusions). |
| Permissions, privacy & backup | Permissions | Unchanged rows; Sources screen links to the same actions. |
| Permissions, privacy & backup | Backup | Extended (section 5). |

New `SettingsPage` entries: `WORKSPACES`, `WORKSPACE_DETAIL`, `SOURCES`, `SOURCE_DETAIL`, `EXCLUSIONS`,
`LENSES`, `LENS_DETAIL`, `PRIVACY`. Each gets a `SettingsPageEntry` with `searchAliases` (preset, workspace,
lens, source, redact, ...) so search finds them, following `settingsMainPageEntries`.

Nothing here owns domain logic: each page is a thin Compose view over pure planners in the domain
(mirroring `WorkspaceMenuPlanner`/`WorkspaceMenuReducer`): `WorkspacesSettingsPlanner`,
`SourcesPlanner`, `LensLibraryPlanner`, producing models that are unit-tested without a device.

### 2.2 Workspaces page (compact)

```
Workspaces                       [layout: Phone folded v]   <- existing device tabs
-------------------------------------------------------------
This layout can't draw "Index" here, so "Standard" is shown.   (only when FellBack)
                                          [Edit] [Switch] [Copy from Unfolded]
 Active
 (o) Standard            Nova-style · default      [ : ]
 ( ) Inbox               Cards · 3 pages           [ : ]
 ( ) Work                Custom · 2 pages          [ : ]
 + New workspace  (choose a preset)

 Copy from other layout…        (unfolded -> this one, replaces this layout's workspaces and saved lenses)
 Advanced
   Restore previous copy         (only if one exists)
-------------------------------------------------------------
[ : ] = Rename · Duplicate · Make default · Reset to preset · Start page… · Dock… · Delete
Workspace detail adds: Dock (Follow device dock / Override: edge, size, hidden), Start page,
Appearance (Follow global / theme preset, Q15).
```

- Radio list = switch workspace (one action, same `activate` as the menu). The marked row is the
  stored active one even while a fallback is displayed, matching the WS6 rule.
- **Reset to preset** needs to know which preset a workspace came from: `Workspace.presetId: String?`
  (additive, section 4.6). Without it the action is hidden, not guessed.
- **Delete** is disabled on the last workspace (domain rule: `remove` is a no-op) with the reason as
  supporting text, and asks for confirmation with an Undo snackbar. Deleting the default moves the
  default as `LayoutWorkspaces.remove` does. **Revised for N2:** a workspace's *arrangement* goes with it (section 9), so the confirmation states what is
  removed and what stays ("N items exist only here and will be removed, M are shared and stay; 2 widgets");
  Undo is exact (pool and arrangements are one value) and released widget host ids are only deleted after the
  Undo window closes (9.4).
- **Copy from other layout** shows what will be replaced ("Replaces your 3 workspaces and 5 saved lenses
  on this layout"), confirms, and offers Undo (the previous generation blob makes undo exact). Widgets become
  placeholders to set up again (9.5), exclusion rules are not replaced (14.6); the dialog says how many widgets
  need setting up.
- **New workspace** opens the preset picker (below), then the editor (WS7) if the user chooses Custom.

### 2.3 Preset picker

```
Choose a preset
 +----------+ +----------+ +----------+
 | [thumb ] | | [thumb ] | | [thumb ] |   static previews (no live animation)
 | Nova     | | iOS      | | TimeScape|
 | default  | |          | |          |
 +----------+ +----------+ +----------+
 | Niagara  | | Kvaesitso| | Blank    |
 Each card: name, one line, "Needs: notification access" if a source in it needs a permission
 [Use preset]                       (never requests the permission itself)
```

Compact: 2-column grid; unfolded: 3 to 4 columns, max content width 840 dp (matches container hosts).
A card that uses a permission-gated source states it and nothing more; permission is only requested
from Sources or from an explicit "needs access" state, as in the Calendar access policy.

### 2.4 Sources page

```
Sources
 Apps (all)           Ready                          [on ]
 Recent apps          Needs permission  [Allow]      [on ]
 Notifications        Ready · 2 hide rules  >        [on ]
 Media                Needs notification access [Allow] [on]
 Calendar             Needs permission  [Allow]      [on ]
 Quick actions        Ready                          [on ]
 RSS                  3 feeds · Ready  >             [on ]       (#1365)
 Search               Ready  >                       [on ]       (#1365)
 Used by: shows how many containers read it (tap: see which)
```

Status values come from `LensAvailability` already used by containers (`LOADING`, `READY`,
`PERMISSION_REQUIRED`, `UNAVAILABLE`) plus a new `OFF` for a disabled source (Q13, adopted). Status is
text, never colour alone. A source whose items are all excluded (section 14) is `READY` with an
"Excluding N items" line, never `OFF` or `UNAVAILABLE`.

- **Permission affordances** reuse the existing explicit flows, unchanged and never auto-prompting: an
  Allow button dispatches `LauncherShellAction.RequestNotificationAccess` / `RequestCalendarAccess`
  and shows the same rationale text as Settings > Permissions (`calendarAccessSettingsLabel`) next to
  the button, then `calendarAccessStep(status, rationaleVisible)` decides rationale, dialog or app
  settings. The Sources page adds no new permission and no new prompt trigger. Which action a source
  needs is declared by its adapter (`SourceAccess`); the page does not hard-code package permissions.
- **Enable/disable**: a per-install set of disabled `SourceId`s. Disabled means the shared registry
  does not expose the source: no subscription, no data read, and no permission use. Containers that
  read it show the existing "unavailable" expression state with the message "Turned off in Settings >
  Sources" and a button to turn it on (an explicit user action). Stored as part of the workspace blob
  (`"disabledSources": [...]`, additive) so it travels with backup.
- **Per-source settings** (detail pages):
  - Notifications: **exclusion rules** list (section 14: the former hide rules, migrated; same match kinds
    and the same contextual creation), and the content level (section 6).
  - RSS: the existing feeds UI (`RssSettings`, refresh interval) moves here unchanged; storage is
    untouched. Refresh stays user-triggered as today.
  - Search (#1365): provider settings defined by that issue; the Sources page only hosts the row.
  - Apps: Hidden apps (a filtered view of app exclusion rules, section 14).
  - Calendar: only status/permission (calendar selection is not a thing today; out of scope).
- Source rows for sources #1365/#1366 have not shipped simply do not appear; the list is driven by the
  registry's descriptors (`SourceRegistry.descriptors()`), not hard-coded.

### 2.5 Saved lenses page

```
Saved lenses                 [layout: Phone folded v]         [ + New ]   <- per layout (Q3)
 Work notifications by app      Notifications · grouped · used in 2 places   >
 Recent, newest first           Recent apps · list · used in 1 place          >
 Unused lens                    Apps · no containers                          >
Lens detail
 Name [..........]   Sources: [Notifications x]   Filter / Group / Sort / Limit (WS7 lens builder, reused)
 Used by:  Standard > Inbox (page-set)   Work > Now (widget)       <- tap opens that container
 [Duplicate] [Delete]            Save is blocked with reasons if it would break a user (4.4)
```

The lens builder is WS7's lens step, hosted in a settings detail page, so the editing UI is written
once. Detail shows **Used by** from `LensLibrary.dependents`, which is bounded to this layout's
workspaces. Delete behaviour is in 4.3. "Copy to <other layout>" on a lens is an explicit one-time copy
with a fresh id (4.3); nothing is shared between layouts.

### 2.6 Privacy and Backup pages

Wireframes in sections 6 and 5 respectively. Backup page gains: "Include workspaces and saved lenses"
(informational, always included) and the import summary dialog.

### 2.7 Unfolded layout

At medium and expanded window width classes the Settings surface uses list-detail: left pane is the
page list (or the workspace list), right pane is the detail, so the Workspaces page becomes:

```
+-----------------------------+------------------------------------------+
| Workspaces                  | Standard                                 |
|  (o) Standard   (default)   |  Pages: Home · Finder                    |
|  ( ) Inbox                  |  [Edit] [Duplicate] [Reset to preset]    |
|  ( ) Work                   |  Draws on this layout: ok                |
|  + New                      |  Preview (static)                        |
| Copy from Phone folded…     |                                          |
+-----------------------------+------------------------------------------+
```

Same planners, wider arrangement; text columns stay capped (640 dp) and previews do not scale past
their natural size. Rotation and fold keep the selected row by workspace id.

### 2.8 Accessibility and reduced motion

- Every row is one focusable element with a spoken summary ("Inbox, Cards, 3 pages, not active").
  Overflow menus are also reachable as TalkBack custom actions on the row.
- No drag-only operations: reordering workspaces and saved lenses uses Move up / Move down actions
  (`LayoutWorkspaces.move` exists). Pull-to-anything is not used.
- Status chips carry text; "Needs permission" is announced with its action; status changes are
  announced politely (live region) and do not steal focus.
- Destructive actions (delete workspace/lens, copy over layout, restore) confirm and offer Undo.
- 48 dp minimum targets, support 200% font scale (rows wrap, no truncation of status).
- Reduced motion (existing `ReducedMotionPreference`): preset thumbnails are static; no animated
  transitions between panes (snap), and no auto-playing previews. Default motion is Material
  container transforms only.
- Standard Material 3 components (list items, switches, dialogs, snackbars) per `design-language.md`
  guardrails for standard UI; custom visuals stay in card expressions.

---

## 3. Legacy settings reconciliation

### 3.1 Inventory and mapping

| Legacy setting | Where (code) | Maps to in workspaces | Disposition |
| --- | --- | --- | --- |
| **Home screen** (Home side of the Home/Library pair: Cards or Standard) | `HomeSurfaceModeSetting.kt`, `ModePair`, `SettingsPageContent.kt:212` ("Modes" section) | The active workspace of the layout (Workspaces page) | **Retire.** Hidden today when only Library is available; removed at S9. |
| **Home layout > view mode** | `HomeViewModeSetting`, `SettingsPageContent.kt:191` | Same | **Retire.** `LauncherViewMode` stays as migration input and in `HomeLayoutKey`, not as a user choice. |
| **Layout template** | `HomeTemplateSetting.kt`, `LauncherTemplateCatalog` | Preset picker (WS8). `LauncherTemplate` already "evolves into workspace templates". | **Retire** the row; the catalog is data WS8 consumes. |
| `viewModeAvailability` (library only) | `LauncherShellPlatformDependencies.kt:43`, `MainActivityDependencies.kt:108` | n/a | **Retire** at S9 (stored hidden-mode layouts are migrated to workspaces instead of resolving to Library). |
| Grid (columns, rows, visible dimensions) | `HomeGridSetting`, `HomeLayout.settings.grid` | The grid of each placed-items page (section 9: `ArrangementPage.grid`) | **Stays** (Layout page); after S4 it edits the active workspace's placed pages, with the device-class default (`HomeLayoutSettings`) kept as the template for new pages. |
| Labels | `HomeLabelSetting`, `settings.labels` | Home grid and icon expressions | **Stays.** |
| Dock: pins, edge, size, appearance | `DockSetting`, `DockModel` | `DockModel` stays the shared per-device-class base; a workspace may carry a `DockPresentation` **override** of edge, size and visibility (section 11); pins are never per workspace | **Stays** as the base; overrides edited on the workspace page. |
| Dock: show notification cards, slot count | `DockModel.showNotificationCards`, `notificationSlotCount` | `WorkspaceDock.dynamicSection` (Notifications lens, limit = slots, IconRow), already how migration maps it | **Stays as one row**, rewritten to edit the active workspace's dynamic section: on = Notifications lens; off = `null`. Per workspace (Q5, decided). |
| Floating dock | `SettingsPage.FLOATING_DOCK`, `OverlayDockSettings` | None | **Stays.** |
| After leaving Library (`LibraryReturnTarget`) | `AppDrawerSettings.afterLeavingLibrary`, `LauncherShellLibraryReturn.kt` | `ReturnBehavior` setting in 3.2 (Restore / First page / Start page) | **Replace**; the stored value is ignored, the codec keeps reading and writing it for backup compatibility. |
| App drawer presentation (list/icons), icon grid columns | `AppDrawerSettings` | The Finder page's expression (`ALPHA_LIST`, `CATEGORIES`, or `ICON_GRID` after the validation widening in 8.3a) (Q6, decided) | **Moves into the Finder page expression**; the row is hidden once the Finder replaces the drawer (S9). Migration: `LIST` maps to `ALPHA_LIST`, `ICONS` to `ICON_GRID`; `iconGridColumns` stays a global setting until expressions have per-expression options. |
| Search result presentation | `SearchSettings.resultPresentation` | Search source (#1365) display | **Stays.** |
| Cards appearance (geometry, glass, colour) | `CardsSettings`, `SettingsPage.ADAPTIVE_STAGE_APPEARANCE` | Appearance of `Card`/`CardStack` expressions | **Stays**, renamed "Card appearance"; shown whenever any workspace uses a card expression, otherwise collapsed under Appearance. |
| Cards stage selector/spine, thread grouping, folded/unfolded show-all | `CardsSettings` fields | Page-set + dock dynamic section behaviour | **Stays** (dormant fields keep round-tripping); not exposed beyond what Cards appearance shows today. |
| Contextual behaviour (`ContextualSettings.enabled`) | `SettingsContextualPageContent.kt` | Independent: smart behaviour, not a layout choice | **Stays**; copy clarifies it is separate from workspaces. |
| Gestures | `GestureSettings`, `LauncherGestureMappings`, `Workspace.gestureBindings` | Global defaults, optional per-workspace overrides | **Stays**; per-workspace overrides are WS7. The dock pull **opens the workspace menu** once modes retire (Q9, decided; 11.5, `gestures.md` change in S9). |
| Hidden apps | `AppVisibilityRepository`, `SettingsPage.HIDDEN_APPS` | App exclusion rules (section 14) | **Unified** into source exclusion rules; the Hidden apps page stays as a filtered view. Per layout, migrated once from the global store (14.6). |
| Notification hide rules | `NotificationHidingSettings` | Notification exclusion rules (section 14) | **Unified**, same match kinds, migrated without loss. |
| Motion & haptics, reduced motion | `MotionSettings`, `ReducedMotionPreference` | Global | **Stays.** |
| RSS feeds page | `SettingsPage.RSS`, `RssSettings` | Source detail (RSS) | **Moves**; storage unchanged; old route aliased. |
| Permissions rows (Home app, notifications, overlay, calendar) | `SettingsPermissionsSection.kt` | Sources links to the same actions | **Stays** as the canonical place. |
| Backup | `SettingsPage.BACKUP` | Extended | **Stays**, section 5. |

Rule used for every row: if it expresses *which arrangement is on screen*, it is a workspace choice;
if it tunes how a standard Android launcher behaves (grid, dock, labels, permissions, hidden apps),
it stays; storage formats are never changed or removed by this, only their UI.

### 3.2 #1323: active mode is not preserved on return (revised: Return behaviour is a setting, Q7)

Cause today: the return target is computed from a mode pair and a setting
(`afterLeavingLibrary`, `LibraryExitTrigger`, `modeAfterLeavingLibrary`), so leaving the launcher can
switch the mode. With workspaces there is no mode to switch: `LayoutWorkspaces.activeId` is the
durable "what was last active", written on switch, restored on every start. **Nothing a return does ever
changes the active workspace** (only an explicit user action does); the setting below governs only *which
page* of the active workspace is shown.

**As built:** `AppDrawerSettings.afterLeavingLibrary: LibraryReturnTarget` (`HOME` or `LIBRARY`, default
`LIBRARY`) in `LauncherSettings.kt`, consumed by `LauncherShellLibraryReturn.kt`. It is mode-shaped and has
no page notion.

**Proposed (owner decision):** a Settings choice, replacing `afterLeavingLibrary`:

```kotlin
// core/domain/.../settings: additive field, default RESTORE
enum class ReturnBehavior { RESTORE, FIRST_PAGE, START_PAGE }
data class HomeBehaviourSettings(val returnBehavior: ReturnBehavior = ReturnBehavior.RESTORE)
```

Two events are distinguished, because Android makes them different:

* **E1 Return**: the launcher comes back to the foreground after an app launch, a process restart, or
  a posture change (Home button while another app is in front, Recents, back out of an app).
* **E2 Home press while already home**: the Home button pressed while the launcher is the foreground app.

| Setting | E1 Return | E2 Home press at home | Home press with Finder open |
| --- | --- | --- | --- |
| **Restore** (default) | The active workspace at the page that was showing (`selectedPageId` for placed-item pages, group key for page-sets via `PageSetSelection`) | The workspace's start page; a second press does nothing | Close the Finder; go to the page it was opened from (to the start page if the Finder itself is the start page) |
| **First page** | The first non-Finder page | Same | Same as Restore |
| **Start page** | The workspace's start page (`startPageId`, else first non-Finder page) | Same | Same as Restore |

Rules common to all three: the active workspace is never changed; a start page id that no longer exists is
ignored (falls to the first non-Finder page, never an error); a posture change keeps the selected page by
container id when the same workspace exists on both layouts (1.6). The Finder is not in the pager (Q14), so
"first page" never means the Finder.

Where it lives in the IA: **Settings > Home & layout > Layout > "Returning to Home"**, a three-option radio
with one line of supporting text each. It is a global (per install) setting, not per workspace, because
it describes the person's habit, not an arrangement; the per-workspace part is only the start page, set on
the workspace detail page. Reconciliation: this closes #1323 (a return never switches mode or workspace,
by construction and by test), supersedes the Home-press rule in #1176 for the page target (E2 is the same
as #1176 for the Restore default), and retires `afterLeavingLibrary` (the stored value is ignored; the codec
keeps reading and writing it for backup compatibility, as in 3.1).

Testable as a pure reducer (`WorkspaceReturnReducer(settings, workspace, lastPage, event, finderOpen) ->
target`): the full table above as parametrised cases, plus one regression test per #1323 scenario, plus a
property test that no input ever yields a different `workspaceId`.

### 3.3 #1324: Standard mode unreachable

Standard is the **Nova-style preset**, which is also the new-install default. After S9 there is no
"Standard mode" to hide: the Standard arrangement is a workspace anyone can select in Workspaces or the
dock menu, and the migrated "Standard" workspace is preserved for users who had one. The stored
`STANDARD_APP_DRAWER` layout is no longer resolved to Library on load. Recorded as the deliberate
redesign decision that #1324 asked for.

### 3.4 #1325: redundant options

Resolved by the table: Home screen, view mode, template and Modes sections all express "which
arrangement", so they collapse into the Workspaces page and the preset picker; the Layout page keeps
grid, labels and the new "Returning to Home" choice. Until S9, the rule from #1325 stands ("hide options that don't apply while only
one mode is available"), which the code already does for view mode and Modes, but **not** for
`HomeTemplateSetting`, which renders unconditionally on the Layout page (`SettingsPageContent.kt:198`).
Quick win independent of this design: hide the template row while a single mode is available.

---

## 4. Saved lenses

### 4.1 Today (as built)

`LensBinding(lens: Lens, expression: ExpressionKind)` holds the lens inline and is used by
`WidgetContainer`, `PageContent.Bound`, `PageSetContainer` and `WorkspaceDock.dynamicSection`. It is
encoded by `LensCodec.encodeBinding` (`{"lens": ..., "expression": ...}`) and decoded by
`decodeBinding` (unknown expression falls back to `LIST`). `WorkspaceCopy` copies bindings by value.
Validity is `LensExpressionValidity.check` / `checkPerGroup`, called from `ContainerValidation` and
`WorkspaceValidation`. Nothing has an id or a name.

> **Revision 2026-10-01 (Q3, Q4):** the library is **per layout** (device class), not global, and
> presets use saved lenses. Everything below that still says "global" is marked superseded; the
> per-layout design is 4.2 (data), 4.3 (semantics) and 4.9 (presets).

### 4.2 Design: references are additive, with a snapshot

Constraint: WS7 (editor) and WS8 (presets) are in flight and build `LensBinding(lens, expression)`.
The least disruptive design keeps that constructor valid and adds an optional reference.

```kotlin
@JvmInline value class LensId(val value: String) { init { require(value.isNotBlank()) } }

/** A named, reusable lens definition. Definition only: never item content. */
data class SavedLens(
    val id: LensId,          // random, immutable, a stored contract like SourceIds
    val name: String,        // trimmed, 1..40 chars, unique case-insensitively in the library
    val lens: Lens,
)

/**
 * One per layout (device class), owned by that layout's [LayoutWorkspaces]. Ordered for display.
 * Bounded by MAX_SAVED_LENSES (100) per layout.
 */
data class LensLibrary(val lenses: List<SavedLens> = emptyList()) {
    fun find(id: LensId): SavedLens?
    // All operations return the receiver unchanged when they cannot apply (WS5 convention).
    fun add(name: String, lens: Lens, ids: WorkspaceIdFactory): LensLibrary
    fun rename(id: LensId, name: String): LensLibrary
    fun duplicate(id: LensId, ids: WorkspaceIdFactory): LensLibrary   // "<name> copy", fresh id
    fun move(id: LensId, toIndex: Int): LensLibrary
    /** Copies [lens] in with a fresh id and a collision-free name; used by copy-to-layout and presets. */
    fun addCopy(lens: SavedLens, ids: WorkspaceIdFactory): Pair<LensLibrary, LensId>
}

data class LensBinding(
    val lens: Lens,                    // UNCHANGED: always the lens to draw (see "snapshot")
    val expression: ExpressionKind,
    val ref: LensId? = null,           // NEW, default null = inline lens, exactly today's meaning
)

// LayoutWorkspaces (existing) gains the library; the invariant "refs resolve in this layout's library"
// is enforced by construction and re-established on decode.
data class LayoutWorkspaces(
    val workspaces: List<Workspace>,
    val activeId: WorkspaceId,
    val defaultId: WorkspaceId,
    val library: LensLibrary = LensLibrary(),   // NEW, default empty (per layout, Q3)
)
// WorkspaceSet itself does not change: its `layouts` map carries one library per device class.
```

**Snapshot semantics.** A binding with a `ref` still carries a full `lens`. The invariant, enforced at
the write boundary and re-established on decode, is: *if `ref` resolves, `binding.lens` equals the
library's lens*. Everything that reads `binding.lens` (validators, planners, the evaluator, the menu
planner, the container hosts, WS7, WS8) keeps working with no change and no library lookup. The
library is the **source of truth**; the inline copy is a cache that is also a safe fallback.

Alternatives considered:

| Option | Why not |
| --- | --- |
| `sealed interface LensSource { Inline; Ref }` replacing `LensBinding.lens` | Every consumer of `binding.lens`, every WS7/WS8 constructor call and every test changes; needs a resolver threaded through pure planners. High rework for in-flight PRs. |
| Resolve at draw time only, store just the id | Dangling refs become an empty container; backup of a single workspace loses its lens; every validator needs the library. |
| Global library (the earlier recommendation) | Superseded by the owner's decision (Q3): per layout. Kept here because it is the simpler model and the cost of the choice is concrete: copy-from-layout must copy lenses (below), and there is no "edit once, both layouts follow". |

**Per-layout library (replaces the earlier "global library", Q3).** Each device class's library lives
inside its `LayoutWorkspaces`, in the same blob as the workspaces, so a library edit and the snapshot
refresh of its dependents are one atomic DataStore edit (no divergence after a crash between two writes).
Consequences, all deliberate: a lens edited on the folded layout never changes the unfolded layout; a
binding can only reference a lens in **its own layout's** library (a ref to anything else is dangling by
definition and falls back to its snapshot); **copy-from-other-layout must copy the referenced lenses**
(4.3) because refs cannot cross layouts; and "Used by" is naturally bounded to one layout. The cost is
duplication: the same "Work notifications by app" lens exists once per layout the user wants it on,
and there is an explicit "Copy to <layout>" on a lens for convenience. Posture-specific lenses (a
compact "latest three" versus an expanded "latest ten") are now easy, which was the argument for
per-layout.

**Ids and names.** `LensId` is a random id (the same `WorkspaceIdFactory`), never reused, never shown.
Names are user-facing, trimmed, 1..40 characters, case-insensitively unique; a clashing rename or add
is rejected with the reason shown; duplicates get " copy" (then " copy 2"...). Library cap 100.

### 4.3 Semantics

| Action | Behaviour |
| --- | --- |
| **Use** (WS7 picks a saved lens for a container) | `binding = LensBinding(saved.lens, expression, ref = saved.id)`; only offered if `LensExpressionValidity` accepts the pairing (`checkPerGroup` for a page-set). |
| **Edit** a saved lens | Computes `EditImpact` first (4.4). If clean, writes the new lens to the library and refreshes the snapshot of every dependent in one `WorkspaceSet` update. If not clean, see 4.4. |
| **Rename** | Library only; bindings hold the id, so nothing else changes. |
| **Duplicate** | New library entry, new id, same definition; no binding changes. |
| **Delete** while unused | Removes the entry. |
| **Delete** while used | Never blocked, never destructive: the dialog says "Used in N places" and offers **Detach** (default: every dependent becomes inline, keeping its lens and drawing exactly as before, then the entry is removed) or **Replace with...** (another saved lens, validity-checked per dependent; dependents it would break are listed and stay detached). The domain operation `LensLibraryOps.remove(layout, id, policy)` returns the rewritten `LayoutWorkspaces` (library and dependents together). |
| **Detach** a binding | `ref = null`; the snapshot becomes the inline lens. Always valid. |
| **Promote** ("Save as lens") on an inline binding | Adds a library entry from `binding.lens` with a name the user types, sets `ref` on that binding (and offers "use it in the N other containers with an identical lens", the exact-equality dependents). |
| **Dangling ref** (library lacks the id: tampered/old backup, partial restore, bug) | Never crashes and never blanks the container. The binding keeps its snapshot and draws normally, the ref is reported as dangling, and the editor/Settings show "Saved lens 'X' is missing: Using a copy" with **Save as lens** or **Detach**. This is stronger than a "missing lens" empty state: the user's screen does not change because a definition was lost. |
| **Clone workspace** (`LayoutWorkspaces.duplicate`) | Within one layout, refs are **shared**, not deep-copied: the clone is a new workspace using the same saved lenses, so editing a lens updates both, which is what "reusable" means. A "Make independent" action detaches all refs in a workspace. |
| **Copy from other layout** (`WorkspaceSet.copyFromOtherLayout`) | **Revised (Q3).** The target's workspaces *and library* are replaced by deep copies of the source's: every library lens is copied with a **fresh `LensId`**, names kept (the target library is being replaced, so there are no collisions), and every ref in every copied workspace is rewritten through the old-id to new-id map. Lenses that no workspace references are copied too (the user's library is part of what is being copied). Refs that were already dangling stay dangling-with-snapshot. Result: nothing is shared, and the target satisfies the per-layout invariant by construction. Implementation: `LayoutWorkspaces.repaired(...)` gains a `library` parameter and `WorkspaceCopy.withFreshIds` gains an id-map overload. |
| **Copy a lens to another layout** (explicit, from the library page) | `LensLibrary.addCopy` in the target: fresh id, name collision resolved by suffix (" 2"). One-time copy; no link. |
| **Preset install** (WS8) | **Revised (Q4).** Installing a preset writes the lenses it uses into **the target layout's library** and installs bindings that reference them; see 4.9 for naming, idempotence and reset-to-preset. |

### 4.4 Validity when a saved lens is edited

The pairing rule is unchanged (`LensExpressionValidity`); what is new is a pure dry run.

```kotlin
data class LensDependent(
    val deviceClass: HomeLayoutDeviceClass,     // always the library's own layout (per-layout library)
    val workspaceId: WorkspaceId,
    val containerId: ContainerId?,        // null = the dock's dynamic section
    val expression: ExpressionKind,
    val perGroup: Boolean,                // page-set: checkPerGroup
)

data class EditImpact(
    val dependents: List<LensDependent>,
    val wouldBreak: List<Pair<LensDependent, List<LensIssue>>>,
)

/** Every operation is scoped to one layout: it takes and returns that layout's [LayoutWorkspaces]. */
object LensLibraryOps {
    fun dependents(layout: LayoutWorkspaces, id: LensId): List<LensDependent>
    fun previewEdit(layout: LayoutWorkspaces, id: LensId, newLens: Lens,
                    sources: List<SourceDescriptor>?): EditImpact
    fun applyEdit(layout: LayoutWorkspaces, id: LensId, newLens: Lens): LayoutWorkspaces   // refresh snapshots
    fun remove(layout: LayoutWorkspaces, id: LensId, policy: RemovePolicy): LayoutWorkspaces
    fun rehydrate(set: WorkspaceSet): Rehydrated    // per layout; set + dangling refs, used by decode and import
}
```

Editor behaviour (WS7 hosts it; WS10 provides the model): the lens builder shows "Used in N places"
and, as the user changes the lens, lists the dependents that would become invalid with the reasons
("Inbox: Cards needs Grouped, this lens is now Flat"). Save is offered three ways when there is a
conflict: **Save as a new lens** (original untouched, this container switches to the copy),
**Detach the N broken containers** (they keep the old lens inline, the rest follow the edit), or
**Cancel**. It never silently saves something that breaks a container, and `applyEdit` is only called
with a clean or explicitly resolved impact.

If invalid data gets in anyway (restore from a tampered file), the existing `WorkspaceValidation` runs
after `rehydrate`; an invalid active workspace falls back through `resolveActive` as today, so the
safety net already exists without changes to `WorkspaceValidation`.

### 4.5 Persistence and in-flight work

- `WorkspaceSetCodec`: schema `1 -> 2` adds, **inside each layout entry**, `"library": {"lenses": [...]}` (and, from section 9,
  `"pool"`), per-binding optional `"ref"`, and a workspace `"preset"` string. All additive. An existing inline binding decodes
  with `ref = null`, which is its old meaning, so "inline stays valid" is a property, not a migration. No release has written
  workspaces yet, so schema 2 can simply be the first schema anyone stores.
- Decode order per layout: library, workspaces, then `rehydrate` (a ref that resolves in **this layout's** library sets
  `lens := library lens`; otherwise the snapshot is kept and the ref reported dangling). A library entry that fails to decode
  is dropped, which makes its bindings dangling-with-snapshot rather than lost. Decode never throws.
- The library stores `Lens` only: no item content, no query text (13.2).

### 4.6 Contract changes, and the in-flight WS7/WS8 work

All additive with defaults: `LensBinding.ref: LensId? = null`; new `LensId`, `SavedLens` (+ `origin`), `LensLibrary`,
`LensLibraryOps`; `LayoutWorkspaces.library`; `Workspace.presetId`. Existing `LensBinding(lens, expression)` calls compile
unchanged. `WorkspaceSet.copyFromOtherLayout` is the one function that must change (4.3). `ensureMigrated` keeps stored
layouts whole (`WorkspaceSet(migrated.layouts + stored.layouts)`, verified in `WorkspaceMigration.kt`), so it keeps libraries.
WS7 phase 1 stays inline; phase 2 (Use saved lens, Save as lens, impact list) follows S5 and must route edits through
`LensLibraryOps`. WS8 presets get the small rework in 4.9. WS6 and the hosts read `binding.lens` snapshots and do not change.

### 4.7 Limits

`MAX_SAVED_LENSES = 100` **per layout**, name 1..40 chars (the bounded-settings convention of `MAX_NOTIFICATION_HIDE_RULES`).

### 4.8 Tests specific to lenses

Library ops (unique names, cap, no-ops); `rehydrate` (resolved, dangling, library wins); a property test of the snapshot
invariant after random edit/rename/duplicate/delete/promote/detach sequences; `previewEdit` (per-group pairing, dock,
page-set, widget); `remove` policies; clone shares refs within a layout; `copyFromOtherLayout` copies referenced lenses
with fresh ids and rewrites refs (target never holds an id that is in the source's library); a binding cannot resolve against
another layout's library; preset install/reset (4.9); one codec round trip with a dangling ref and a hostile id.

### 4.9 Presets with saved lenses (Q4)

**As built:** `WorkspacePresets.installPreset(preset, posture | deviceClass, ids)` returns a deep copy with
fresh workspace and container ids; bindings are inline; `PresetInstaller.newInstallLayout` / `withDefaultsFor`
/ `addPreset` put results into the `WorkspaceSet`; nothing links a workspace back to its preset
(`workspaces-presets.md`). `PresetBindings.kt` builds the bindings.

**Proposed rework (kept small, catalog data unchanged):**

```kotlin
/** A lens a preset uses, with a key stable across releases and a name for the library. */
data class PresetLens(val key: String, val name: String, val lens: Lens)   // key e.g. "nova.finder"

sealed interface LensOrigin { data class Preset(val presetId: String, val key: String) : LensOrigin }
// SavedLens.origin: LensOrigin? = null   (optional codec key "origin"; ignorable by older decoders)

data class InstalledPreset(val layout: LayoutWorkspaces, val workspaceId: WorkspaceId)

object PresetInstaller {
    fun install(layout: LayoutWorkspaces, preset: WorkspacePreset, posture: PresetPosture,
                ids: WorkspaceIdFactory, activate: Boolean): InstalledPreset
    fun reset(layout: LayoutWorkspaces, workspaceId: WorkspaceId, resetLenses: Boolean,
              ids: WorkspaceIdFactory): LayoutWorkspaces
}
```

* **Install** adds the preset's workspace and, for each `PresetLens`, ensures a library entry in **that
  layout's library** with `origin = Preset(presetId, key)`. If an entry with the same origin already
  exists it is **reused** (idempotent: installing Nova twice does not create a second "Finder (A to Z)");
  otherwise it is added with the preset's name. Bindings in the installed workspace get `ref` set and
  carry the snapshot. Bindings built from `PresetBindings` helpers pass a `PresetLens`; any binding a
  preset builds directly stays inline (no promotion of one-off lenses).
* **Naming collisions.** Library names are unique case-insensitively (4.2). A preset lens name that
  collides with a *user* lens (different origin) gets a suffix " (Nova)" then " (Nova 2)"; the preset
  never renames or overwrites a user's lens. A user renaming a preset lens is fine; the origin key, not the
  name, is the identity.
* **Reset to preset** (workspace menu: "Reset to preset", needs `Workspace.presetId`): rebuilds the
  workspace's *arrangement* (pages, containers, dock section, gesture bindings) from the current catalog,
  keeping the workspace id and name. Saved lenses: by default **kept as the user left them** (they may be
  shared with other workspaces); an explicit second choice "Also restore the N saved lenses this preset
  created" shows `previewEdit` impact on every dependent and resets only lenses whose origin matches. A
  workspace without `presetId` has no Reset (hidden, not guessed). A preset-origin lens deleted by the user
  is re-added on reset.
* **Placed items and presets (N2).** An installed preset creates its placed-item pages empty: a preset arrangement never
  moves or deletes pool items or other workspaces' arrangements. Reset keeps the references of pages that still exist
  in the preset arrangement and asks before dropping any.
* **Tests.** Install writes the expected library entries, installing twice reuses them (workspaces equal apart from ids), reset
  restores the arrangement and, when asked, only preset-origin lenses. The five preset files pass a `PresetLens` for each lens
  they already build.

---

## 5. Backup and restore

For a solo alpha the backup export **is** the safety net (1.7), so this is the one place worth getting right.

**As built:** the document type and codec support a `"workspaces"` object, but neither export nor import is wired
(`launcherBackupDocument()` / `LauncherBackupExportCoordinator` do not pass it; `withImportedBackup` applies only
layouts, settings and hidden apps; `isImportableBackup` ignores it). `decodeLauncherBackupDocument` requires
`version == 1` exactly. `HomeWidgetJsonCodec.encodeWidget` writes `appWidgetId`, a host-local integer.

**Proposed (S8):**

- **Export** passes the in-memory `WorkspaceSet` (workspaces, **pool and arrangements**, per-layout lens libraries)
  under `"workspaces"`, and the per-layout exclusion rules under a new optional `"exclusions"` key. Keep the document
  version at 1 (bumping would make an older build reject the file, and that costs nothing to avoid). While
  `HomeLayoutSet` still exists (before S4 completes) it continues to be written under `"homeLayouts"` as today.
  After S4 `"homeLayouts"` is not written; a pre-S4 backup is imported through the migration (9.9). Nothing else is kept
  for old readers: there are none.
- **Import** is **replace**, with Undo (Q11), consistent with layouts, settings and hidden apps today: decode (never
  throws), replace the stored set atomically (pool and arrangements are one value, so they cannot diverge), replace
  exclusions, keep the previous blob for Undo, re-resolve the active workspace. A document with no usable
  `"workspaces"` is rebuilt with `WorkspaceMigration.migrate(document.homeLayoutSet)` (9.9) and empty libraries.
  Workspace problems never reject a backup. The import dialog shows counts ("3 workspaces, 5 saved lenses").
- **Widgets in a backup (9.6).** Host ids are meaningless on another device (and after "clear data" on this one).
  Export writes each widget's provider and size; import marks every widget as an **unbound placeholder** unless
  `WidgetHostGateway.isHostedWidgetBoundTo(id, provider)` says the id is still bound to that provider on this
  device. Placeholders keep their cell and span; "Set up widget" runs the normal add flow.
- **Never item content**: lens definitions, workspaces, pool entries (app identities, labels, folder entries, widget
  providers), exclusion rules and settings; never feed articles, notifications, events, media state or search
  queries. Disabled sources travel with the set; permission grants never do (sources restore as enabled but
  ungranted and show "Needs permission", nothing prompts).
- **Tokenised URLs are excluded (decided).** A feed or ICS URL with userinfo, a query string or a long opaque path
  segment is omitted from the backup and the Sources page shows "Re-enter the address" after restore (a small pure
  function with a test over hostile samples). Plain URLs behave as today.
- **Single-workspace export/import: deferred (Q10).** The shared pool makes it harder (a workspace's items may be
  shared with others and widgets cannot travel), which is a further reason to defer.

---

## 6. Privacy

### 6.1 As built

- `Item.privacy` is `VISIBLE` / `SENSITIVE`; the lens `project` step is the **only** redaction point
  (`SENSITIVE_ITEM_FIELDS` are cleared whatever `project` says; sort keys, pinned state and `ByExt`
  groups are computed from the redacted view so they cannot leak).
- Quiet profiles yield `SENSITIVE` items with no content; locked, unavailable and unknown profiles
  yield none. Private/confidential calendar events are `SENSITIVE`.
- Notification hide rules (`NotificationHidingSettings`) drop matching notifications before they
  become items; `hide-rule edits apply on the next refresh` (known gap). Per section 14 they become one
  kind of source exclusion rule, evaluated as the first step of lens evaluation.
- No item content is stored anywhere; permission-gated sources emit `PermissionRequired` without
  reading.

### 6.2 Proposed controls

All are additive, default to today's behaviour, and live on Settings > Privacy plus the matching
source detail page.

1. **Content level per source** (default "Show"): `SHOW`, `HIDE_BODY` (drop `body`, `subtitle`,
   `image`; keep title and icon), `HIDE_TEXT` (title shows as "Hidden content", app icon and count
   remain). Implemented as a per-source ceiling intersected with the lens's own `project` at the
   same projection step: it can only remove fields, never widen a lens, and `SENSITIVE_ITEM_FIELDS`
   still always win. No new redaction point.
2. ~~**Hide sensitive content while the device is locked**~~ **Removed (Q8, decided).** The lock screen
   already covers the launcher, so the `KeyguardManager.isDeviceLocked` rule is not built and has no
   setting anywhere: no code, no Settings row, no slice item, no test. (It also removes a source of
   surprising redaction when a person looks at their own unlocked home.) What stays: **profile-lock
   handling** (a locked or quiet work profile yields no or `SENSITIVE` items exactly as the adapters do
   today, 6.1) and notification redaction through hide rules and content level.
3. **Screenshots and recents** (default **off**, decided to include): "Hide launcher content in screenshots
   and recents" sets the window's secure flag. It also blocks the user's own screenshots of Home, so it is
   opt-in with that said plainly in the supporting text. A single boolean in `LauncherSettings` (additive),
   applied by the activity; the Privacy page is its only home.
4. **Exclusion rules** (section 14, including the former notification hide rules) stay the strongest
   control (drop before the lens sees it) and are managed on Settings > Sources; the Privacy page links to
   them.
5. **Work profile / quiet profile** behaviour is unchanged and described on the Privacy page.

### 6.3 What may appear where

| Surface | Visible items | Sensitive items | Hidden-content placeholder |
| --- | --- | --- | --- |
| Home pages and widgets | per content level | redacted per projection | "Hidden content" (WS3 contract) |
| Dock dynamic section | icons and counts (after exclusions; external-source items allowed, section 15) | icon only | n/a |
| Workspace menu / page jump | group labels (transient lens output) | labels are lens structure, never stored | n/a |
| Settings, saved lenses, backups | **never items** | never | n/a |
| Settings previews of presets | static art, fake data | n/a | n/a |

### 6.4 What a screenshot of a workspace may show

- Screenshots in docs, tests and the preset picker use **fakes only** (`launcher/workspace/testing`
  fixtures, WS9); a screenshot test must fail if it renders a real item source.
- A user's own screenshot shows exactly what the home shows, redacted per above; no extra overlay.
- Diagnostics, crash reports and the `lastOutcome` record contain outcome codes and counts only, never
  item text, titles, feed URLs or lens names. (Lens names are user-authored text; treat them as
  user content: not in diagnostics.)

---

## 7. Implementation slices (re-sequenced for a solo alpha)

> Superseded: the earlier S1 to S20 list and the R0 to R5 stages. Twelve slices remain, ordered so that a
> **runnable debug build exists after S1 and S2** and every later slice is run on the owner's phone before the next
> one builds on it. "Pure" means domain-only, JVM tests, no app wiring.

| ID | Slice | Depends on | Notes |
| --- | --- | --- | --- |
| **S1** | **Runnable debug build.** `WorkspaceBootstrap` (pure, 1.2/1.5), `WorkspaceRepository` over the existing `DataStoreWorkspaceStore` (write-behind, one previous generation), bounded startup read, developer toggle "Use workspaces" (1.4), the shell **draws the active workspace** with the WS6 menu enabled and the WS7 editor reachable from it, Nova preset for fresh data (default unification, 8.1), **the existing home surface hosted as the workspace's home page** (placed items still `HomeLayoutSet`, 9.9). Calendar/notification sources use today's explicit permission flows. | WS4, WS5, WS6, WS7, WS8 (merged) | **First dogfood build.** Nothing about placed items changes. |
| **S2** | **Settings basics.** Workspaces page (switch, preset picker, rename/duplicate/delete/reset, copy from other layout), Sources page (status rows, permission actions through the existing flows, `OFF`, 2.4). | S1 | Completes the dogfood build: everything reachable without code changes. |
| S3 | **Shared pool (domain).** `PlacedItemPool`, `PoolItem`, arrangement pages, `PoolOps` (references, remove, remove-everywhere, add-reference, clone-widget plan, make-independent, delete-workspace, copy-arrangement, prune-uninstalled), `PoolIndex` (derived), validation, `PlacedItemsAdapter`, `HomeLayoutSet` to pool mapper (9). Pure. | WS5 | Can be built in parallel with S1/S2. |
| **S4** | **Pool cut-over (app).** Home edit, drag, folders and widgets operate on the pool through the adapter; widget lifecycle (deferred host-id deletion, provider backfill, placeholders); uninstall pruning; `HomeLayoutSet` is no longer written (**backup first**, 1.7). Sharing actions ("Also show in...", share-mode duplicate) may follow the cut-over. | S1, S3 | The risky slice (9.10). |
| S5 | **Per-layout lens library and preset rework** (4), plus recency and favourite lenses (12, N3). Library UI (Saved lenses page, WS7 phase 2) can follow. | WS5, WS8 | Domain part is pure and parallel. |
| S6 | **Per-layout exclusions** (14): engine (pure), store, copy of the owner's hidden apps and hide rules into every layout's set, provider pre-step, legacy consumers moved, Sources exclusions UI, contextual "Hide" action. | S2 for UI; engine now | Channel/thread/calendar-id keys slip (N5). |
| S7 | **Workspace additions.** Start page, Finder out of the pager, `FINDER_EXPRESSIONS` plus `ICON_GRID` (8.3), Return setting and `WorkspaceReturnReducer` (3.2), skin override (8.7), **dock overrides** with the hidden-dock validation and `OPEN_WORKSPACE_MENU` gesture (11), New apps per preset and `autoPages` (9.8). | S1; S4 for 9.8 | Several small independent PRs. |
| S8 | **Backup wiring** (5): export/import of workspaces, pool, exclusions; widget placeholders; tokenised-URL omission; Undo. | S1, S4, S6 | Do before relying on the phone for anything else. |
| **S9** | **Legacy reconciliation and flip.** Remove Home screen/view mode/template/Modes UI, drawer-presentation and `afterLeavingLibrary` rows; dock pull opens the workspace menu (`gestures.md`, `dock.md`, a11y action, Ctrl+arrow); retire `libraryOnlyLauncherViewModeAvailability`; delete `WorkspaceMenuFeature` and the developer toggle. Closes #1323, #1324, #1325. | S2, S4, S7 | After this the workspace path is the only path. |
| S10 | Per-lens search queries (13). | #1365 | Slips freely. |
| S11 | Privacy controls: content level per source, screenshot/recents flag (6). | S6 | Slips freely. |
| S12 | ICS recurrence through a library: spike, then the ICS source (15.2). | external-sources scaffolding | Slips indefinitely. |
| S-C | *Only if the owner confirms full-parity Classic:* the second navigation style (10.3). | S9 | Not in the default plan. |

Dependency summary ("must land before"): `S1 -> S2`; `S1, S3 -> S4`; `S4 -> S8`; `S6 -> S8`; `S2, S4, S7 -> S9`.
Three chains can start immediately: S1 then S2; S3 (pure); S5 domain and S6 engine (pure).

**Cut line.** *Needed to use workspaces daily:* S1, S2, S4, S6, S8, S9. *Can slip past the flip, indefinitely:* S10,
S11, S12, channel/thread/calendar-id exclusion keys, the sharing-creation UI (the model and `PoolOps` still ship in
S3/S4, so nothing is rebuilt later), `autoPages`, the first-run chooser, the Saved lenses page UI, per-workspace skin
override, favourite (as opposed to recency) lens polish.

### 7.1 Test and validation plan

Tests are kept where they guard **real logic**; fixtures and goldens that only protect other people's stored data
are dropped (the owner's own layout is covered by the migration round-trip test and the by-eye check, 1.7).

**Domain (JVM).** Bootstrap decision table (1.5); pool: derived references, GC on last-reference removal, widget
single-placement rule, clone-widget consent plan, deletion and Undo restore the exact value, `copyArrangement`
id-map (sharing preserved inside the copy, nothing shared across layouts), uninstall pruning only on a real
removal, `validate` self-healing (dangling, orphan, multiply-placed widget, duplicate reference in one
arrangement); **adapter**: engine-assigned ids are remapped to pool-unique ids, shared-folder edits write the pool item
once; **differential test**: random edit sequences run through the legacy engines on a `HomeLayout` and through
`PlacedItemsAdapter`+`PoolOps` give equal results; **migration round trip** `HomeLayoutSet -> pool -> HomeLayoutSet`
equal for fixtures with every item kind (widgets, folders, shortcuts, dock panel) and idempotent; lens library
snapshot invariant (4.8); `WorkspaceReturnReducer` table and "never changes workspace id"; dock `applying` and the
stranding validation; exclusions (14.9); per-lens parameter leak sentinel (13.4); backup import decision (valid,
absent, corrupt).

**Instrumented (`deviceVerify`, existing emulator CI).** Keep the existing standard-mode suite green; add startup
with a corrupt blob; widget add/move/resize/remove and folder create then restart-and-compare against the pool;
backup export then import round trip including widget placeholders.

**Screenshots (WS9 harness).** Only the new pages (Workspaces, preset picker, Sources, exclusions list) at compact
size, fakes only. No matrices.

**Manual (the "works on my device" checklist, 1.7)**, plus TalkBack through the new pages and the contextual Hide
action, 200% font, and hiding the dock on a workspace with the gesture, TalkBack action and Ctrl+arrow all working.

### 7.2 Play declarations

Not tracked during the solo alpha. If the app is ever published, the permissions and data classes added here
(calendar and notification access, stored exclusion text, external feed/ICS network use, the secure-window flag)
need a data-safety and privacy-policy review first.
---

## 8. Findings from the presets (WS8) that bear on default-on

The merged presets doc (`workspaces-presets.md`, "Gaps") lists limits of the current model. Each is
addressed here as in scope for WS10, deferred, or owner-decided. **Revised 2026-10-01:** items 2, 5, 6 and 8
were answered differently from the original recommendation and are now designed in sections 11, 13, 12 and
9 respectively; their text here is reduced to a pointer. Items 1, 3 and 7 are adopted as proposed.

1. **Two different built-in defaults.** `PresetInstaller.newInstallLayout`/`withDefaultsFor` seeds the
   Nova-style workspace **with a Finder page** (Home + a `FINDER` AlphaList page).
   `WorkspaceMigration.defaultFor` (what `WorkspaceSet.workspacesFor` returns for a layout with nothing
   stored) maps `HomeLayoutDefaults.standard` through the mapper and has **no** `FINDER` page: an
   `AllApps` page keeps `PageRole.STANDARD`. The dock menu hides its Finder entry when there is no
   `FINDER` page, so a migrated Standard user would have no Finder entry while a new install does.
   Rule proposed:
   - **Fresh install** gets `WorkspacePresets.defaultFor` (Nova-style with Finder) through
     `PresetInstaller.withDefaultsFor`.
   - **Missing layout read** (`workspacesFor` fallback) returns the same Nova default:
     `WorkspaceMigration.defaultFor` delegates to `WorkspacePresets.defaultFor`, so there is one
     built-in default and a fresh install is never different from "nothing stored".
   - **Migrated existing install** still uses the mapper (it must reproduce the user's own pages) but
     marks the single `AllApps` page of a layout `PageRole.FINDER` (an `AllApps` page is the drawer
     and already AlphaList), so the Finder entry exists for migrated users too. A layout with two
     `AllApps` pages marks none. Covered by a golden test.
   Slice: folded into S1 (small change plus a test); no stored data changes.
2. **Dock edge, size, pins and "no dock" are `DockModel`, not `Workspace`.** *(Superseded by Q17.)* The
   earlier recommendation was "out of scope". The owner decided on per-workspace overrides; the design
   (override-only `DockPresentation`, pins stay shared) is section 11.
3. **No start page; the Finder is a pager page.** Adopted (Q14), unchanged:
   - `Workspace.startPageId: ContainerId? = null` (null = first non-Finder page; an id that does not
     exist is ignored, never an error; codec key `"start"`). It may name the Finder, which is how
     Kvaesitso opens on search.
   - **The shell excludes the Finder page from the pager** and opens it only through the Finder entry
     or the gesture bound to it, unless it is the start page. This matches standard-launcher parity
     (the drawer is not a home page) and keeps page indicators honest; it is a UI rule with no model
     change. The start page feeds the return setting in 3.2.
   - **iOS-style home (Q14):** the home pages are placed-items pages (section 9) and the Finder is
     "All apps as Categories" as the last (non-pager) entry; the iOS preset already has this shape
     (Today, Home, Library with role Finder) and only changes in that the Home page now *owns* its items.
   - **8.3a Finder expression widening (Q6).** `ContainerValidation.FINDER_EXPRESSIONS` is
     `{CATEGORIES, ALPHA_LIST}` (verified). Moving the drawer's presentation into the Finder page needs the
     drawer's "Icons" choice, so add `ICON_GRID` to the set (a validation change with a test; no
     stored data changes, existing Finders stay valid). `iconGridColumns` stays a global setting.
4. **No master/detail link between containers** (unfolded TimeScape: the Index picks the group the CardStack shows). Deferred.
   When designed, compose the detail filter at evaluation time as `AllOf(lens.filter, GroupKeyIs(selected))` from a runtime
   selection, never by writing a selection into a stored lens.
5. **Search and query.** *(Superseded by per-lens queries, section 13.)* Kept: the query text is never persisted, logged or
   backed up; a saved lens stores a parameter *binding*, not text.
6. **`apps.favourite` / `apps.frequent` have no adapter.** *(Superseded by Q19: lenses, section 12.)* No flip blocker.
7. **Skin model.** `skinOverrideId` exists (null follows global) and presets carry placeholder
   `skinHintId`s, but **no skin ids exist in the repo**. The only appearance catalog is
   `LauncherThemePreset` (`AppearanceSettings.themePreset`, default `MATERIAL`). Proposal: a skin is a
   theme preset; `skinOverrideId` stores the `LauncherThemePreset` name (a stored contract, never
   renamed; unknown ids read as "follow global"). Settings: per workspace an Appearance row
   "Follow global / <preset>". Preset `skinHintId`s are mapped to a `LauncherThemePreset` or dropped
   (hints only; installing a preset still never changes the theme). Owner decision (Q15) plus a small
   WS8 follow-up replacing the placeholders.
8. **Long-term ownership of placed items.** *(Superseded twice: Q16 moved them into workspaces, N2 made them a shared pool
   with per-workspace arrangements. Section 9.)*

---

## 9. Placed items: a shared pool per layout with per-workspace arrangements (N2)

> Superseded: the first revision made each workspace *own* its placed pages (`PlacedItemsPage(page: LauncherPage)`)
> and rejected a shared pool. The owner chose the pool (N2). That design was never built and is deleted rather than
> kept; the engine-reuse idea survives (9.7).

### 9.1 As built today (verified)

* `HomeLayoutSet` holds one `HomeLayout` per `HomeLayoutKey(viewMode, deviceClass)` and one shared `DockModel` per
  device class (`core/domain/.../home/HomeLayoutSet.kt`). A `HomeLayout` is `(pages, selectedPageId, dock,
  templateId, settings, editMode)`; a `LauncherPage` is `(id, type, grid, items, generatedContentOverflowCount,
  isPinned)` (`HomeLayout.kt`, `LauncherPage.kt`).
* **Items fuse identity, content and position.** `AppShortcutItem`, `FolderItem` (holds its `AppShortcutItem`
  children) and `WidgetItem` each carry their own `placement: GridPlacement?` (`LauncherItem.kt`). Engine-assigned
  ids look like `app:<profile>:<package>/<activity>:<ordinal>`, where the ordinal counts same-app items **in that
  layout** (`HomeShortcutEngine.kt`), so ids are unique per layout, not globally.
* **The honest reading of "HomeLayout is already a pool".** Every item is held by exactly one layout and the only
  shared object is the per-device-class dock. So today the structure is "a pool in which every item has exactly
  one reference, one arrangement per (mode x device class)". That is what makes migration simple (9.9): each
  stored `HomeLayout` becomes one arrangement and its items become pool entries with one reference each. No
  sharing exists in the data today and the migration creates none.
* `HomePageEngine.duplicatePage` clones items with fresh ids and **rejects pages containing widgets**
  (`CANNOT_DUPLICATE_PAGE_WITH_WIDGETS`). `GridPlacementEngine`, `FolderEngine`, `WidgetEngine`, `HomeShortcutEngine`,
  `HomePageEngine`, `GridReflowEngine` and `HomeLayoutAppMembership` operate on `HomeLayout`/`LauncherPage` values,
  plain Kotlin with no Compose or Android types.
* Widgets: `WidgetItem.appWidgetId` (`HostedWidgetId`) is an `AppWidgetHost` allocation made through
  `WidgetHostGateway.allocateHostedWidgetId`, released with `deleteHostedWidgetId` (called from
  `LauncherShellViewModel` when a widget, a page with widgets or a dock widget is removed). `WidgetItem` records
  resize constraints but **no provider component**; the gateway can answer `isHostedWidgetBoundTo(id, provider)`;
  `WidgetProviderIdentity` is a plain domain type (`core/domain/.../widgets/InstalledWidgetProvider.kt`).
* Uninstall: no pruning of home items was found by searching `app/src/main` and `core/domain` (UNVERIFIED beyond
  the search); `HomeScreenLibraryCompaction.kt` notes that "a hidden or uninstalled app's shortcut stays in the
  layout until something prunes it" and rendering filters it.
* Backup: `HomeWidgetJsonCodec` writes `appWidgetId`; no rebind step was found in the import path.
* Workspaces reference placed items only through the `home.grid` source id plus `GroupKeyIs(pageId)`
  (`HomeLayoutWorkspaceMapper.homeGridLens`); no `home.grid` adapter exists.
* No auto-placement of new apps and no "pages appear as you fill them" exist in `core/domain`.

### 9.2 Model

Principle: **split an item into what it is (pool) and where it sits (arrangement)**, reuse the existing engines and
value types through an adapter (9.7), and derive everything else.

```kotlin
// workspace package. One pool per layout (device class), stored in LayoutWorkspaces next to the lens library,
// so pool and arrangements are always written together (one DataStore edit, one backup object).
@JvmInline value class PoolItemId(val value: String)            // random or migration-deterministic; never reused

sealed interface PoolItem { val id: PoolItemId }
data class PoolApp(                                              // an app or an app shortcut
    override val id: PoolItemId, val appIdentity: AppIdentity, val label: String,
    val appShortcutId: AppShortcutId? = null,
) : PoolItem
data class PoolFolder(
    override val id: PoolItemId, val label: String,
    val entries: List<FolderEntry>,                              // value entries, not pool items: no nested references
) : PoolItem
data class FolderEntry(val appIdentity: AppIdentity, val label: String, val appShortcutId: AppShortcutId? = null)
data class PoolWidget(
    override val id: PoolItemId, val label: String, val resizeConstraints: WidgetResizeConstraints,
    val provider: WidgetProviderIdentity?,                       // NEW: needed to clone, restore and rebind; null = unknown (legacy)
    val hostedId: HostedWidgetId?,                               // null = unbound placeholder (restored, copied, or provider gone)
) : PoolItem

data class PlacedItemPool(val items: Map<PoolItemId, PoolItem> = emptyMap())

// One reference: "this item sits here". GridPlacement is the existing cell+span type.
data class Placement(val item: PoolItemId, val at: GridPlacement)

/** A page of an arrangement: LauncherPage minus the items, plus references. */
data class ArrangementPage(
    val id: LauncherPageId, val type: LauncherPageType, val grid: GridDimensions,
    val placements: List<Placement>, val generatedContentOverflowCount: Int = 0, val isPinned: Boolean = false,
)

/** Replaces PageContainer(Bound(home.grid ...)). One per home page of a workspace: the workspace's ARRANGEMENT is its list of these. */
data class PlacedItemsPage(
    override val id: ContainerId, val page: ArrangementPage,
    val role: PageRole = PageRole.STANDARD,                     // never FINDER
    val expression: ExpressionKind = ExpressionKind.ICON_GRID,  // IconGrid (home) or List (Niagara "Pinned")
) : PageHost { override val ownedAxes: Set<GestureAxis> get() = emptySet() }

data class LayoutWorkspaces(/* existing fields */ val library: LensLibrary = LensLibrary(),
                            val pool: PlacedItemPool = PlacedItemPool())   // NEW, additive, default empty
```

The dock stays outside the pool: `DockModel` (pins, `panel`) is already "one object per layout shared by every
workspace" (11.1), so it is a pool with no arrangement. Its panel widgets still take part in the widget uniqueness
check (9.3).

**Invariants (enforced by `PoolOps`, re-established on decode by `validate`):**

1. Every `Placement.item` resolves in the layout's pool (no dangling references).
2. A pool item is referenced **at most once per arrangement** (so `LauncherItemId == PoolItemId` is unique within
   any materialised `HomeLayout`, which the engines assume; two *different* pool apps for the same application
   are fine, exactly as today where the ordinal exists for that reason).
3. A `PoolWidget` is referenced by **at most one placement in the whole layout** and its `hostedId` is not
   used by any other `PoolWidget` or dock panel widget (9.3).
4. Reference counts are **derived, never stored** (9.4). Every pool item has at least one reference after any
   `PoolOps` call (no orphans), except inside an open Undo window.

### 9.3 What may be shared, and the widget rule

| Kind | In several arrangements? | Rule and reason |
| --- | --- | --- |
| App / app shortcut (`PoolApp`) | **Yes.** | Stateless: sharing is invisible apart from "remove everywhere". Cheap; this is the natural use of the pool. |
| Folder (`PoolFolder`) | **Yes, by explicit action.** | One folder, edits (rename, add, remove entry) show everywhere it is placed; edit mode shows "Shared with N workspaces". "Make independent" clones it. Never shared implicitly (copy operations clone, 9.5). |
| Widget (`PoolWidget`) | **No: one placement in the layout.** | An `AppWidgetHost` id is a single live instance with its own state and configuration; two placements would share state and double-bind. Moving a widget between workspaces **moves** its single placement (host id kept). |
| Widget "in two workspaces" | **Clone with explicit consent.** | Action "Add a separate copy here" explains: "A widget can't be in two places. This adds a new copy that you configure separately." It allocates a new host id (via the gateway) for the same `provider` and starts the normal bind/configure flow. A provider-less legacy widget cannot be cloned (action hidden, with the reason). |

`validate` enforces rule 3 by keeping the first placement (by workspace order) and dropping later ones with a notice:
never two bound views of one host id.

### 9.4 References, GC, deletion and Undo

* **Reference counts are derived**: `PoolIndex = PoolOps.references(layout)` scans every arrangement page once
  (a launcher holds a few hundred placements) and returns `Map<PoolItemId, List<ReferenceSite>>`. Nothing is
  stored, so counters cannot drift, which is the usual reference-counting bug. The index is rebuilt after each
  mutation and cached in memory.
* **GC rule.** `PoolOps` finishes every mutation with `collect(layout)`, which removes pool items with zero
  references. Folder entries go with their folder. A collected `PoolWidget` with a `hostedId` adds that id to
  `PoolEdit.releasedHostIds`.
* **Deletion semantics.**
  - *Remove from this workspace* (the normal remove, today's remove icon): drops the placement. If other
    arrangements still reference the item it stays; if it was the last reference it is collected.
  - *Remove everywhere* (offered only when references > 1, with the count in the confirmation): drops every
    placement across arrangements.
  - *Delete workspace*: drops its arrangement; the confirmation says "N items exist only here and will be removed,
    M are shared and stay; 2 widgets will be removed".
  - There is no user-visible "unplaced items" tray: an app is always reachable from the Finder, so a zero-reference
    pool item has no value to keep.
* **Undo.** `PoolOps` functions return `PoolEdit(layout: LayoutWorkspaces, releasedHostIds: Set<HostedWidgetId>,
  summary)`. Because pool and arrangements are one immutable value, Undo restores the previous `LayoutWorkspaces`
  exactly. The app layer holds `releasedHostIds` in a deferred queue and calls `deleteHostedWidgetId` only after
  the Undo window closes or the next changing write commits (today the delete is immediate, which workspace-level
  Undo forbids). Startup reconciliation deletes host ids that are referenced by neither the stored blob nor
  `workspaces_prev`, bounded and idempotent, which also covers a crash inside the window.

### 9.5 Copy semantics

Rule of thumb: **copies clone, sharing is always an explicit choice.** That keeps "duplicate to try a variation"
from silently editing the original.

| Operation | Apps/shortcuts | Folders | Widgets |
| --- | --- | --- | --- |
| **Duplicate workspace** (`LayoutWorkspaces.duplicate`) | Cloned (new `PoolApp`s). | Cloned (independent). | **Placeholders**: same cell, span and provider, `hostedId = null`; a banner "2 widgets need setting up" offers the add flow per widget. |
| Duplicate workspace, option "Keep items shared" | Shared references. | Shared references. | Placeholders (never shared). |
| **Duplicate page** (`HomePageEngine.duplicatePage`, in one arrangement) | Cloned (the engine already gives fresh ids). | Cloned. | Still rejected (existing behaviour). |
| **Copy from other layout** | Cloned into the **target pool** with fresh ids. | Cloned. | Placeholders. |
| **Preset install** | Preset arrangements start with empty placed pages; nothing is moved or deleted in other workspaces. | n/a | n/a |

*Copy from other layout* replaces the target layout's workspaces, library **and pool** (the pool is part of the layout,
like the library); the replaced widgets' host ids go to `releasedHostIds` (deferred, Undo exact). References never cross
layouts, so the copy applies an old-id to new-id map like the lens copy (4.3): items that two *copied* workspaces shared
stay shared in the target (via the map), sharing with anything outside the copied set is not possible. Exclusion rules
are separate and are **not** replaced (14.6).

### 9.6 Uninstall, restore and backup

* **Uninstall.** On a confirmed *package removed* event (not "absent from a snapshot", and not a work-profile pause,
  because the installed-app snapshot starts empty and profiles disappear temporarily, the same caution
  `HomeScreenLibraryCompaction.kt` documents), run `PoolOps.pruneUninstalled(layout, identity)`: drop every placement
  of every `PoolApp` with that identity and every folder entry, remove folders left empty, collect. Until the event,
  unresolvable apps are hidden at render time, as today. Dock pins are not in the pool and are handled by the same
  event in the dock path (verify; not found today).
* **Restore.** Pool and arrangements arrive together. All widgets are unbound placeholders unless
  `isHostedWidgetBoundTo(id, provider)` confirms the id on this device (5). A placeholder keeps its cell and span and
  offers "Set up widget"; a widget whose provider is not installed offers "Remove".
* **Legacy widgets without a provider.** The migration cannot fill `provider` (pure code). A one-time app-layer
  backfill reads `AppWidgetManager` info for each bound host id and writes `provider`; ids it cannot resolve stay
  `provider = null` (not cloneable, not rebindable after a restore: shown as "Widget unavailable, remove").

### 9.7 Reusing the existing engines through an adapter; the effect on `home.grid`

```kotlin
/** Pure. Presents ONE arrangement to the engines as the HomeLayout they already edit, and writes the result back. */
object PlacedItemsAdapter {
    fun toHomeLayout(layout: LayoutWorkspaces, workspace: WorkspaceId, settings: HomeLayoutSettings,
                     dock: DockModel, selectedPageId: LauncherPageId, edit: HomeEditMode): HomeLayout
    /** Diff-based: placement moves become placement edits; changed folder content/widget constraints edit the pool item;
     *  new ids become new pool items; vanished ids drop a reference; then collect(). */
    fun fromHomeLayout(before: LayoutWorkspaces, workspace: WorkspaceId, edited: HomeLayout): PoolEdit
}
```

* `toHomeLayout` joins each `Placement` with its `PoolItem` into the `AppShortcutItem` / `FolderItem` / `WidgetItem`
  the engines expect (`LauncherItemId = PoolItemId.value`; an unbound widget gets `HostedWidgetId(0)`, Android's
  `INVALID_APPWIDGET_ID`, which the host draws as the placeholder). The engines (`GridPlacementEngine`,
  `FolderEngine`, `WidgetEngine`, `HomeShortcutEngine`, `HomePageEngine`) are called **unchanged**, so their existing
  tests keep their meaning.
* **Id collision (new, found by reading `HomeShortcutEngine`).** Engines mint ids from an ordinal counted in the
  adapted layout, which contains only *one* arrangement, so a new id can equal an id already used by an item in
  another arrangement. `fromHomeLayout` therefore treats every id absent from the adapted input as new and assigns a
  fresh pool-unique `PoolItemId`; it never trusts an engine id as pool-global.
* **Edits to shared folders** change the one pool item (visible everywhere) by design; dragging an app *out of* a shared
  folder creates a new `PoolApp` in the arrangement and removes the entry from the shared folder.
* Edit mode (`HomeEditMode`) and drag stay shell state as today; dropping on a non-placed page (Finder, page-set) is not a
  target. WS7 edits non-placed containers; the home-edit engines edit placed pages; they do not overlap.
* **`home.grid`** retires as a *model* concept: `PlacedItemsPage` replaces `PageContainer(Bound(home.grid ...))`. The source
  id stays reserved (never renamed); a stored page that still references it is upgraded on decode from `HomeLayoutSet`
  while that exists, else drawn as an empty placed page with a notice. **Q12** (page-id uniqueness across modes) is
  mooted: page ids only need to be unique inside one workspace, and pool item ids are unique per layout by construction.
* **Selected page** is shell state keyed by container id, persisted as `LayoutWorkspaces`-level "last page per
  workspace" (`Map<WorkspaceId, ContainerId>`, key `"lastPage"`) so Restore (3.2) survives process death.

### 9.8 iOS-style behaviours and new apps (N9)

* **Pages appear as you fill them.** `PlacedItemsAutoPaging` (pure, over the adapted layout): when an item is placed and
  every placed page of the workspace is full, append a new empty page; remove an empty trailing page on leaving edit mode
  (never the last page). Per-workspace flag `autoPages: Boolean` (default true for iOS-style, false for Nova-style).
  It is arrangement-only; the pool is untouched.
* **New apps (N9).** `NewAppPlacement { FINDER_ONLY, HOME_AND_FINDER }` is a **setting stored per workspace**
  (`Workspace.newAppPlacement`), **defaulted by the preset** (Nova-style `FINDER_ONLY`, iOS-style `HOME_AND_FINDER`,
  migrated workspaces `FINDER_ONLY`, which is today's behaviour), edited in Settings > Home & layout > Layout > "New
  apps" for the active workspace. On an install event (the existing package-change hook feeding the installed-app
  repository) create **one `PoolApp`** and reference it from the first free cell of every workspace of the current
  layout whose setting is `HOME_AND_FINDER` (creating a page when `autoPages`); this is the pool doing what it is for.
  The placement is a pure function (`PlacedItemsEditor.placeNewApp`). Removing the icon from one workspace leaves the
  others (9.4).
* **Finder = All apps as Categories at the end (iOS).** Unchanged (8.3): the Finder is a `FINDER`-role page outside the
  pager and never owns placed items. A placed icon and the Finder entry are related only through the app identity.

### 9.9 Migration from `HomeLayoutSet`, and what to do with the owner's data

Pure function in `core/domain`, extending `HomeLayoutWorkspaceMapper`: for each device class, the pool is the union of
the items of all its stored mode layouts; each `HomeLayout` becomes the arrangement of its migrated workspace
(`ws:<deviceclass>:<mode>`); `Home` pages become `PlacedItemsPage`; other page types map as before (generated pages
become lens pages, an `AllApps` page becomes the Finder when it is the only one, 8.1).

* **Deterministic and idempotent:** `PoolItemId = "pi:<deviceclass>:<mode>:<LauncherItemId>"`, so running it twice gives the
  same value and `ensureMigrated` never overwrites an existing workspace. **No de-duplication across modes**: two modes
  holding "the same" app create two pool entries with one reference each (this is what they were; no sharing is created
  that the user never asked for).
* **Lossless where it matters:** one round-trip unit test, `toHomeLayoutSet(migrate(x)) == x` for placed data (pages,
  items, placements, selected page, pinned flags, dock panel) over fixtures with every item kind, plus a property test over
  layouts built with `GridPlacementEngine`. When sharing exists the reverse mapping materialises a shared item into each
  layout; the round trip then equals the original *up to flattening sharing*, which is why the reverse mapping is only
  used for the one-time migration check and for importing old backups, never as a live mirror.
* **The owner's data (solo alpha, 1.7):** export a backup, run the migration, check by eye. A destructive reset and
  re-placing icons by hand is an acceptable outcome if the migration misbehaves; no shadow period is run.
* **Library-only shipped build:** the build resolves every layout to Library but stores layouts per mode; migration
  creates workspaces for every stored mode, so the Standard-mode home stays recoverable. Which is active is `shownMode`.
* **Failure:** a layout that fails to decode is rebuilt from the built-in default for that device class.
* **Before S4** the home page is the existing surface reading `HomeLayoutSet` (9.7/S1), so nothing needs migrating for the
  S1/S2 dogfood build.

### 9.10 New risks the shared pool introduces

| Risk | Mitigation | Test |
| --- | --- | --- |
| **Dangling reference** (a placement whose item is missing: bad edit, partial restore, bug) | Placement is dropped at decode with a notice; the cell renders empty, never a crash; all mutations go through `PoolOps` | `validate` self-heals every case; property test: after random `PoolOps` sequences invariants 1-4 hold |
| **Orphan / leak** (zero-reference item, or a widget host id never released) | `collect` after every mutation; startup reconciliation of host ids against blob and `workspaces_prev` | GC on last-reference removal; reconciliation is idempotent |
| **Widget ownership** (two bound views of one host id, wrong id deleted on remove) | Invariant 3 in `validate`; moves keep the single placement; clone allocates a new id; deletion only via `releasedHostIds` after Undo | multiply-placed widget keeps first; clone creates distinct id; remove-everywhere releases exactly the referenced ids |
| **Reference-counting bugs** (premature collect, stale count) | Counts are derived from the arrangements, never stored | removing one of two references keeps the item; removing the last collects it |
| **Surprise sharing** (editing a folder "in one place" changes another workspace) | Sharing only by explicit action; copies clone; "Shared with N" label; "Make independent" | duplicate workspace yields independent folders; shared-folder edit visible in both |
| **Adapter desync / id collision** between engine ids and pool ids | Remap every new id; differential test against the legacy engines | engine-assigned id equal to another arrangement's id is remapped |
| **Undo vs host ids** | Deferred deletion queue; Undo restores the immutable value | delete workspace with widget then Undo: widget still bound |

---

## 10. Classic (N7)

> Superseded: the first revision defined Classic as standard-launcher parity only, a frozen UI over the same data,
> supported permanently (Q2). The owner then decided Classic gets **every feature, including the editor and the menu**
> (N7), and later described the project as a solo alpha. Both are handled below: the default plan is the cheap one, and
> full parity is an explicit option with its cost.

**As built:** "classic" is simply how the launcher works today (home from `HomeLayoutSet`, drawer, dock);
workspaces are not wired to the shell (`WorkspaceMenuFeature.enabled = false`).

### 10.1 The tension, stated plainly

If Classic has every feature it shares **all the data and all the engines** with the workspace path: bootstrap, the
pool, exclusions, the lens library, dock overrides, the editor. A bug in any of those is a bug in Classic too. So
full-parity Classic is **no longer a lower-risk fallback from workspace bugs**. What it can still protect against is
only a bug in the *navigation chrome* (workspace menu surface, pager, dock-pull handling). A decode or bootstrap
failure already falls back to the built-in default workspace in either style (1.5), so Classic adds nothing there.
This is not an argument against the decision; it is what the escape hatch can honestly promise.

### 10.2 Default plan (recommended): a developer toggle, then dropped

* S1 adds **Settings > Developer > "Use workspaces"** (debug builds, 1.4). Off draws the existing home from
  `HomeLayoutSet` (which exists until S4); after S4 it draws the layout's default workspace's placed pages as a plain home
  with the drawer and no menu. Purpose: "is this a workspace bug or a launcher bug?" while building. It is not a user
  feature, has no migration or backup surface, and is not part of the test matrix.
* S9 deletes the toggle. **Standard launcher parity** (home, drawer/search, dock, folders, widgets, wallpaper, grid
  editing, settings, backup, profiles, hidden apps, notification indicators, `standard-launcher-mode.md`) becomes a
  requirement on the **default Nova-style workspace**, tested once, instead of on a second mode.
* What this protects: nothing for end users (there are none); for the owner it protects the ability to bisect.
  What it does not: data bugs. Revert is git and a backup export (1.7).

### 10.3 Option: full-parity Classic, if the owner confirms

**What it would be.** Not a second copy of the data and not a different launcher, but an alternative **navigation
style** over the same workspace data, stored as one per-install value (`NavigationStyle`), never in the workspace blob:

| | Workspace navigation | Home-and-drawer navigation |
| --- | --- | --- |
| Data | the same workspaces, pool, library, exclusions, dock overrides, queries | identical |
| Dock pull / dock menu | opens the workspace menu (11.5) | opens the same menu from an explicit dock button and the bound gesture; the pull keeps its standard-launcher meaning |
| Pages | pager across the workspace's pages; Finder via menu entry or gesture | placed pages in the pager; **Finder as the full-screen drawer** (swipe up); lens pages and page-sets appear as extra pager pages in workspace order |
| Editor, presets, saved lenses | via the menu and Settings | **the same**, reachable from the menu and Settings (N7: every feature) |
| Hidden dock | validated (11.4) | same validation |

**What the setting switches:** navigation chrome only: where the Finder lives, whether the dock pull is the menu, how
non-placed pages are reached. It never changes, copies or hides data.

**What it still protects against:** a defect specific to one chrome (menu surface, pager, pull handling). **Not**
against: bootstrap, decode, pool, exclusion, library, editor or source bugs (shared).

**What it costs (the honest list):**
* two navigation shells compiled and maintained; every UI slice (S2, S6 contextual actions, S7 dock overrides and start
  page, S9 gestures) needs an entry point and a design for both;
* a test matrix: engine and domain tests run once (shared); the standard-mode instrumented suite, the new-page
  screenshot tests and the manual checklist run **in both styles**;
* accessibility work twice (TalkBack actions for "Workspace menu" and "All apps" in each style);
* every new feature must state its behaviour in each style (a lens page in the drawer style is "an extra pager page");
* the risk of drift between styles, which is the main long-term cost for one developer.

**Effect on the plan if chosen:** adds slice S-C after S9; "works on my device" gains "...in both styles"; the revert
story is unchanged (git and backup) with one more cheap lever (switch style) for chrome bugs only; risk register
adds the two-styles maintenance risk (16).

### 10.4 Recommendation on the name

**Do not call it "Classic."** With full parity it is no longer the old launcher, and "Classic" promises a safe
fallback that it cannot give (10.1). If the option is ever built, name the setting by what it changes: **Settings >
Navigation style: "Home and drawer" / "Workspace menu"**. If the owner accepts the default plan, nothing user-visible
is named at all. Recommendation: **default plan now, revisit the option only if the workspace navigation proves
unpleasant to live with.**

---

## 11. Per-workspace dock overrides (Q17)

### 11.1 As built

`DockModel` (per device class, `HomeLayoutSet.docks`; `core/domain/.../home/DockModel.kt`) holds pins
(`items`), `capacity`, `isEnabled`, `position: DockPosition?` (null = template/default edge), `iconSizeDp`,
appearance, `showNotificationCards`/`notificationSlotCount`, expandability and the `panel`. Library's edge is
a separate `libraryDockEdgesByDeviceClass` entry. `WorkspaceDock` holds only
`dynamicSection: LensBinding?` (`Workspace.kt`). `docs/product/dock.md`: one dock per device class,
shared by every mode; the dock pull is the Home/Library switch and "no dock, no pull". Presets could not
express "no dock" (Niagara) or "dock on the left edge" (unfolded TimeScape): `workspaces-presets.md` gaps 2
and 9.

### 11.2 Proposed model

Override-only, never a second pin list:

```kotlin
data class WorkspaceDock(
    val dynamicSection: LensBinding? = null,
    val presentation: DockPresentation? = null,          // NEW. null = follow the device-class DockModel
)

data class DockPresentation(
    val hidden: Boolean = false,                         // true = no dock on this workspace
    val edge: DockPosition? = null,                      // null = follow the base dock's edge
    val iconSizeDp: Int? = null,                         // coerced to MIN..MAX_DOCK_ICON_SIZE_DP
) {
    init { require(iconSizeDp == null || iconSizeDp in MIN_DOCK_ICON_SIZE_DP..MAX_DOCK_ICON_SIZE_DP) }
}

/** Pure: the dock actually drawn for this workspace. Pins, capacity, appearance are never overridden. */
fun DockModel.applying(p: DockPresentation?): DockModel =
    if (p == null) this else copy(
        isEnabled = isEnabled && !p.hidden,
        position = p.edge ?: position,
        iconSizeDp = p.iconSizeDp ?: iconSizeDp,
    )
```

The dynamic section already is per workspace (`dynamicSection`); Q5 (notification cards per workspace)
stays as designed: the Dock row edits the active workspace's dynamic section. `DockModel.showNotificationCards`
remains as the migration input and for a bare home without workspaces (10.2).

Codec: optional key `"presentation": {"hidden"?, "edge"?, "iconSize"?}` inside the workspace's dock, additive
(a v1 decoder ignores it and draws the shared dock). Workspace schema version stays 1.

### 11.3 Migration

None needed for stored data: `presentation = null` is "follow", which is today's behaviour for every migrated
workspace. The two behaviour changes are preset-side: Niagara sets `hidden = true`; unfolded TimeScape sets
`edge = LEFT`. These apply on **install** of the preset (and Reset), not retroactively to installed copies.

### 11.4 Layering, accessibility, and the dock-pull menu trigger

* **Resolution order** (pure, `EffectiveDock`): device-class `DockModel` -> workspace `presentation` ->
  (the developer toggle's plain home ignores the override). `HomeDockHost` and `reservedExtent`/`dockInteractionRegionExtentDp` read
  the effective dock, so reserved space follows the override (a hidden dock reserves nothing, a left-edge
  dock reserves width), and `resolveDockPosition` takes the effective `position`.
* **Switching workspace changes the dock.** Transitions are the existing dock re-orientation animation;
  reduced motion is a crossfade (dock.md). Posture changes recompute the effective dock for the new layout.
* **Stranding rule.** The dock is today the gesture handle for the workspace menu (and after Q9 the dock pull
  opens it). A hidden dock removes that handle. Therefore: `hidden = true` is valid only if the workspace
  has a reachable menu entry that is **not the dock**, enforced as a `WorkspaceIssue.DockHiddenWithoutMenuEntry`
  (the editor blocks it, a decoded violation falls back to a visible dock with a notice, never an invisible
  menu). Reachable means any of: a gesture mapped to a new `LauncherGestureAction.OPEN_WORKSPACE_MENU`
  (global mapping or `Workspace.gestureBindings`; Niagara binds a swipe up from the bottom), and always, for
  every workspace regardless: the TalkBack custom action "Workspace menu" on the home surface root and the
  Ctrl+arrow keyboard shortcut (these existing equivalents of the dock pull move to the surface, so they
  exist without a dock).
* **Accessibility.** The dock row on the workspace page is a plain list item: "Dock: Follow device dock /
  Hidden / Left edge, 44 dp", with the stranding reason as supporting text when Hidden is disabled. Focus
  order and traversal of a moved dock follow the effective edge (side dock: along-run vertical scrolling is
  unchanged). A hidden dock announces nothing; the a11y action label names the target ("Workspace menu").
  200% font and 48 dp targets unchanged (`iconSizeDp` minimum 32 is the existing floor; the touch target
  stays 48 dp).
* **Floating dock** (`OverlayDockSettings`, over other apps) is unrelated and is **not** overridden per
  workspace.

### 11.5 Dock pull opens the workspace menu (Q9) and the `gestures.md` change

Once the mode pair retires (S9) the dock pull has no Home/Library to switch. Decision: the same pull (a drag
away from the dock edge, same claim rules, thresholds and `DockPullTransitionController` direction logic)
**opens the workspace menu**; the explicit dock affordance WS6 proposes stays as the discoverable control and
the accessibility action and Ctrl+arrow equivalent are the ones that already exist. `gestures.md` changes:
"Mode transitions" becomes "Workspace menu"; the dock-pull row and "Dock body" rows say "opens the workspace
menu"; "No dock, no pull" gains "(the menu stays reachable through the surface action and the bound gesture,
11.4)"; the plan-revision banner gets a 2026-10-01 note. `dock.md` loses the Home/Library re-orientation text
and gains the override layering. Until S9 the pull stays the mode switch and nothing in those docs
changes. These doc edits are part of S9, not of the design PRs. Interaction with a dock override: the pull needs a visible dock, so with `hidden` the
bound gesture is the trigger; with a moved edge the pull direction follows the effective edge.

### 11.6 Effect on presets

Niagara: `presentation = DockPresentation(hidden = true)` plus a swipe-up binding to `OPEN_WORKSPACE_MENU`.
Unfolded TimeScape: `presentation = DockPresentation(edge = DockPosition.LEFT)`. Compact TimeScape, Nova,
iOS, Kvaesitso: `null`. `workspaces-presets.md` gaps 2 and 9 close; the presets doc is updated by S7.

### 11.7 Tests

Resolver (`applying`) table; `hidden` + stranding validation (hidden without a bound gesture is invalid; the
a11y/keyboard paths do not count as the *gesture*, they are always there but the editor still requires one so
touch users are not stranded); codec round trip and v1-decoder ignore; reserved extent follows the effective
dock; Niagara/TimeScape presets resolve to the expected effective dock; switching workspaces changes the effective
dock but never `DockModel.items`.

---

## 12. Favourite and frequent apps as lenses (Q19)

**As built:** the generated pages `FAVOURITES` and `FREQUENTLY_USED` produce no items
(`GeneratedLauncherPageContentPlan`), no favourite store exists (`favouriteAppsAvailable` defaults false) and
usage data is `RecentAppUsage(package, lastUsedAtMillis)` only. `LensSort(pinnedFirst)` reads the ext flag
`launcher.pinned`, which no adapter sets. `HomeLayoutWorkspaceMapper` maps them to the reserved ids
`apps.favourite`/`apps.frequent`, which have no adapter.

**Decision:** lenses over the existing built-in app sources; **no new adapters**, so nothing blocks making workspaces the default.

| Page | Definition | Lens | Expression |
| --- | --- | --- | --- |
| Favourites | "Favourite" = **pinned**: an app the user keeps in the dock or on a placed home page. | `apps.all`, `ExtEquals(launcher.pinned, Flag(true))`, sort title | IconGrid |
| Frequently used | "Frequent" = **usage-based**: the apps most recently used, as far as the platform tells us. | `apps.recent`, sort `TIME` descending, limit 12 | IconGrid |

Honest limits: (1) "pinned" needs `launcher.pinned` set. That is an additive change inside the existing
apps adapters (an ext flag, not an adapter): `apps.all` items for apps present in the dock or on placed
pages get `Flag(true)`, computed from `DockModel`/placed pages through the existing
`HomeLayoutAppMembership` helpers. (2) "Frequent" is a **recency** proxy because there is no launch counter;
true frequency needs a counter (new stored data; N3: counter later, only on demand). (3) The "usage stats
unavailable" case stays: `apps.recent` reports `PermissionRequired`/empty exactly as the Recents page does,
and the page says so (no new prompt).

**Migration mapping:** a migrated `Generated(FAVOURITES)` page becomes the Favourites lens above, a
`Generated(FREQUENTLY_USED)` page the Frequent lens; the migration test changes accordingly (it currently
expects `apps.favourite`/`apps.frequent`). Because those pages produced no items before, no user sees a
regression, and they start working. Presets still do not use them (Niagara's pinned list is its placed
page). Flip criterion 5a no longer mentions them. Old stored workspaces that already reference
`apps.favourite`/`apps.frequent` (none exist outside tests) read `UNAVAILABLE`, as today.

---

## 13. Per-lens search queries (owner decision: now)

### 13.1 As built

`SearchQueryHolder` (`workspace/sources/SearchQueryHolder.kt`): one in-memory query for the whole process,
trimmed and cut at `MAX_SEARCH_QUERY_LENGTH = 128`; the search source observes it; `toString()` never
reveals it; nothing serializes or logs it (`workspaces-sources-rss-search.md`). `ItemSource.subscribe(observer)`
has no input. The doc named the minimal hook: an optional `ParameterizedItemSource` plus a lens source
parameter, with the shared holder as the default value.

### 13.2 Design goals and the persistence rule

1. Two search boxes on one page can drive two different lenses.
2. **The query text is never persisted, logged, backed up, put in diagnostics, or placed in an `Item`.** A
   saved or inline lens stores only a parameter *binding* (a slot name), never the text.
3. Additive: existing `ItemSource`s, `Lens` consumers and stored lenses keep working; a lens with no
   parameters behaves exactly as today.

### 13.3 Contract sketch (WS0, additive)

```kotlin
/** Names a runtime value slot, not the value. Stored in lenses. e.g. "search.main", "search.inbox". */
@JvmInline value class ParameterSlot(val name: String) {
    init { require(name.matches(Regex("[a-z][a-z0-9._-]{0,31}"))) }
}

/** What a source parameter is bound to. Stored. */
sealed interface ParameterBinding {
    /** Use the shared default value (today's single SearchQueryHolder). The only binding for stored legacy lenses. */
    data object Default : ParameterBinding
    /** Use the value currently supplied for [slot] by whichever search box/container owns it. */
    data class Slot(val slot: ParameterSlot) : ParameterBinding
}

/** A source that takes a runtime parameter. Optional: sources that do not implement it are unchanged. */
interface ParameterizedItemSource : ItemSource {
    val parameterKind: ParameterKind              // TEXT_QUERY for search; others later
    fun subscribe(parameter: ParameterValue, observer: SourceObserver): SourceSubscription
}

/** Runtime only: never stored, redacted toString. */
class ParameterValue private constructor(private val text: String) {
    fun text(): String = text
    override fun toString() = "ParameterValue"                 // never reveals the text
    companion object { fun query(raw: String): ParameterValue /* trim + cut at MAX_SEARCH_QUERY_LENGTH */ }
}

// Lens: ONE additive field. Default = no parameters = today's behaviour.
data class Lens(
    val sources: List<SourceId>,
    /* ... existing fields unchanged ... */
    val parameters: Map<SourceId, ParameterBinding> = emptyMap(),
)
```

* `LensSourceRef`-style resolution: for each source in `lens.sources` that has a binding, the provider
  resolves it to a `ParameterValue` from a runtime **`ParameterStore`**; a source without a binding that
  *is* parameterised uses `Default`, i.e. the shared `SearchQueryHolder` value, so every existing lens over
  `search` keeps working with the same single query.
* **`ParameterStore`** (runtime, in-memory, never stored): `value(slot): ParameterValue`, `set(slot, raw)`,
  `observe(slot, ...)`; `Default` maps to the existing `SearchQueryHolder` (it *becomes* the default slot,
  not a separate holder). Backing storage for process death: **none by design**; after process death every
  slot is empty (a search box starts empty), which is the privacy-preserving and least surprising choice.
* **The shared-source rule** ("one subscription per source however many lenses"): a parameterised source is
  shared **per distinct parameter value**, not per source: `SharedSourceRegistry` keys the upstream on
  `(sourceId, parameterValueKey)` where the key is an in-memory hash used only as a map key (never logged or
  stored). Two lenses bound to the same slot share one upstream; different slots get different upstreams.
  A bounded cap on live parameterised upstreams (proposal: 4) protects battery; a fifth is `Unavailable`
  with a reason, never silent.
* **How a search box supplies it.** The search box is not a lens expression; it is a *container option*:
  a `SearchBoxContainer(id, slot)` (a widget-sized container hosting a text field, no lens) that calls
  `ParameterStore.set(slot, text)`. A page may also bind its **own** query through an expression that carries
  a text field (the Finder page's search field supplies `ParameterBinding.Default`, i.e. today's behaviour).
  Containers whose lens binds `Slot(s)` re-evaluate when `s` changes (debounced, off the main thread, the
  existing `AsyncLensEvaluator` path). A lens bound to a slot that no container supplies shows the empty
  state "Type to search" (a `Ready(empty)` with a hint, not an error).
* **Validation:** `ParameterBinding.Slot` is valid only on a source whose descriptor declares
  `SourceCapability.SEARCHABLE`; `WorkspaceValidation` reports `UnknownParameterSlot` when no container in the
  workspace supplies a bound slot (the editor warns, saving is allowed because a slot may be supplied later).
* **Saved lenses (per layout, 4):** a saved lens holds the **binding** (`Slot("search.inbox")`), never text.
  Using it in two containers makes both follow the same slot unless the editor re-binds. Copying a lens to
  another layout copies the binding; the slot name resolves against that layout's own search boxes.

### 13.4 Persistence and privacy checklist

* Stored: `Lens.parameters` (slot names), `SearchBoxContainer.slot`. Never stored: any `ParameterValue`.
* `ParameterValue.toString()` and `ParameterStore.toString()` are constant strings; unit test asserts a
  sentinel query never appears in `toString()` of any related object, in the workspace codec output, in the
  backup document, or in a captured log (reuse the `SearchQueryHolder` test pattern).
* Items produced for a query never echo the text (existing rule); the dedupe/upstream key is a non-reversible
  in-memory map key.
* The Sources page and diagnostics report counts of live parameterised subscriptions, never values.
* Backup: unchanged apart from the new optional keys, which carry no text.

### 13.5 Interaction with exclusions and OFF

Exclusion rules (14) apply to a parameterised source's results exactly like any other (they are the first step
after the source emits). A search source set `OFF` yields nothing for every slot, and no upstream exists.

### 13.6 Compatibility and slice (S10)

A stored lens with no `parameters` key decodes with `emptyMap()`: today's meaning; `SearchQueryHolder` stays as the `Default`
slot's backing store, so #1365's adapter and tests keep passing. Slice S10 (slips freely): the types above, codec key
`"params"`, `ParameterStore` (in-memory, `Default` delegating to `SearchQueryHolder`), `SharedSourceRegistry` keyed on
`(source, parameter key)` for `ParameterizedItemSource` only, the search adapter implementing it, `SearchBoxContainer` and
its host (a11y label, clear action), editor options "Search box" and "Bind to slot". Tests: two slots receive different
results from one fake source; same slot shares one upstream; unbound lens uses the default holder; sentinel-text leak
tests (13.4); the live-upstream cap; codec round trip. No Settings page, no stored query, no new permission.

---

## 14. Source exclusion rules (owner requirement, 2026-10-01)

Three owner decisions: (1) **layered**: source-level exclusions apply to every lens and are evaluated
**before** the lens filter, so a lens can never show something excluded globally, while a lens may add its own
filters on top; (2) **unified model**: one `SourceExclusionRule` keyed by source replaces the scattered
mechanisms; existing hidden apps and hide rules migrate into it with compatible storage and behaviour and no
loss; (3) **authoring**: contextual creation from any item plus management in Settings > Sources; never
silent.

### 14.1 As built (verified)

Two mechanisms: hidden apps (`AppVisibilityRepository.hiddenAppIdentities(): Set<AppIdentity>`; applied by
`withHiddenApps` in `LauncherShellViewModel` and `BuiltInItemSources`, which turns apps `HIDDEN` so
`InstalledAppCatalog.visibleApps` drops them from drawer, search and the app-backed sources) and
`NotificationHideRule` (`id`, `packageName`, `profileId`, `Kind` APP/TITLE/BODY/EMPTY_CONTENT, `value`,
`MatchMode` EXACT/CONTAINS/WILDCARD; always scoped to one source app; created contextually; applied by
`NotificationHideRuleFilter` in `NotificationItemMapper` and `NotificationCounterState`; stored in
`NotificationHidingSettings` capped at 200; hide-rule edits apply on the next refresh). Hidden apps and hide
rules both ride in settings/backup today (`"hiddenApps"` and the settings document). RSS has feed
configuration (a configured feed is removed, not hidden); calendar and media have no hiding.

### 14.2 Data model (Kotlin sketch)

```kotlin
@JvmInline value class ExclusionRuleId(val value: String)       // random; migrated rules get deterministic ids

data class SourceExclusionRule(
    val id: ExclusionRuleId,
    val source: SourceId,                  // rules are keyed by source
    val matcher: ExclusionMatcher,
    val enabled: Boolean = true,           // "disable" without deleting (decision 3)
    val label: String? = null,             // optional user-visible name; matcher always has a generated description
    val origin: ExclusionOrigin = ExclusionOrigin.USER,   // USER | MIGRATED_HIDDEN_APP | MIGRATED_HIDE_RULE
)

enum class ExclusionTextField { TITLE, BODY, SUBTITLE }
enum class MatchMode { EXACT, CONTAINS, WILDCARD }              // same semantics as NotificationHideRule.MatchMode

sealed interface ExclusionMatcher {
    /** An app, optionally one profile and one launcher activity: apps, notifications, shortcuts, media. */
    data class App(val packageName: String, val profileId: String? = null,
                   val activityName: String? = null) : ExclusionMatcher
    /** Everything in an item group: a feed id (rss), a category, a search section (apps / settings). */
    data class Group(val groupKey: String) : ExclusionMatcher
    /** One specific item by its source-stable identity (an RSS article, a calendar event, a shortcut). */
    data class ItemKey(val key: String) : ExclusionMatcher
    /** Text in a field, optionally narrowed to an app. */
    data class Text(val field: ExclusionTextField, val value: String, val mode: MatchMode = MatchMode.EXACT,
                    val app: App? = null) : ExclusionMatcher
    /** Item with no title and no body (NotificationHideRule.EMPTY_CONTENT), optionally for one app. */
    data class EmptyContent(val app: App? = null) : ExclusionMatcher
    /** Source-specific extension key (declared by the source): e.g. notification channel, calendar id. */
    data class SourceKey(val key: ItemExtKey, val value: ItemExtValue) : ExclusionMatcher
}

data class SourceExclusions(val rules: List<SourceExclusionRule> = emptyList()) {
    fun forSource(source: SourceId): List<SourceExclusionRule>
}

object SourceExclusionEngine {
    /** Pure. Items not matched by any enabled rule for [source], order preserved. Empty rules = same list (fast path). */
    fun apply(source: SourceId, items: List<Item>, rules: SourceExclusions): List<Item>
    fun matches(rule: SourceExclusionRule, item: Item): Boolean
    /** For Settings: how many of [items] each rule hides (runtime only, never stored). */
    fun matchCounts(source: SourceId, items: List<Item>, rules: SourceExclusions): Map<ExclusionRuleId, Int>
}
```

`MAX_EXCLUSION_RULES = 500` for user-created rules (the old notification cap was 200 plus an unbounded
hidden-app set); **migrated rules are exempt from the cap** so migration never drops anything.

### 14.3 Where it hooks in, and why

Options: (A) wrap each `ItemSource` (a decorating `ExcludingSourceRegistry`), or (B) make it the first step of
lens evaluation in the provider. **Recommendation: B**, implemented as a pure pre-step in
`SourceBackedLensResultProvider` (the existing seam containers use) right after a source's `Ready(items)` is
read and **before** the lens filter, group, sort, limit and `project`:

```
source emits Ready(items)  ->  SourceExclusionEngine.apply(source, items, rules)  ->  lens.filter -> group -> sort -> limit -> project (redaction)
```

Why B and not A: a wrapper bakes rules into the single shared upstream, so a rule edit would have to
re-emit the shared stream for everyone and the Settings "hides N items" count could not be computed; with B
the shared upstream stays raw (still one subscription per source), a rule edit just re-runs evaluation for
observers of that source, and counts are computed from the same raw items. It also keeps the step **pure and
unit-tested in the domain** and keeps adapters unaware of exclusions (they stop calling
`withHiddenApps`/`NotificationHideRuleFilter` after S6, with the equivalence tests in 14.9 proving no
behaviour change). Non-lens consumers (the app drawer and search, notification badges and the dock cards
planner, which today call the legacy helpers) call the same `SourceExclusionEngine` through two thin helpers
(`SourceExclusions.isAppHidden(identity)` and the notification variant), so there is one definition of
"hidden".

Redaction ordering (invariants, each with a test): exclusion runs on the **redacted view** for text
matching, mirroring "sort keys are computed from the redacted view so they cannot leak": a `SENSITIVE`
item has no title/body, so `Text` rules never match it and cannot confirm its content; `App`, `Group`,
`ItemKey` and `SourceKey` rules (which depend on structure, not content) still apply. Exclusion only
*removes* items; it never adds, reorders or exposes fields, and `SENSITIVE_ITEM_FIELDS` are still cleared at
`project` for everything that remains.

**Layering with lenses (decision 1):** the lens sees only what survived exclusion; a lens `filter` can only
narrow further. There is no per-lens "include excluded" and no override; the saved-lens UI says "Hidden items
are never shown, see Sources". A property test: for random lenses and rules, `lensOutput(items) ∩ excluded(items) = ∅`.

### 14.4 Per-source key vocabulary

| Source | Contextual action | Matchers used | Notes |
| --- | --- | --- | --- |
| `apps.all`, `apps.recent`, `shortcuts` | "Hide this app" | `App(package, profile)` (hidden apps), `Group(category)` for "Hide this category" | Hidden apps = `App` rules on `apps.all`; the engine applies the same rule to `apps.recent` and `shortcuts` (an `App` rule is keyed by app, and the vocabulary treats the three app-backed sources as one *source family* for `App` matchers, so "hide this app" hides it everywhere it appears as an app). Placed home icons are **not** removed by hiding (as today). |
| `notifications` | "Hide notifications from this app", "Hide notifications like this" | `App`, `Text(TITLE/BODY, mode)`, `EmptyContent`, `SourceKey("notification.channel", ...)`, `SourceKey("notification.conversation", ...)` | App/Text/Empty are what `NotificationHideRule` does today. **Channel and thread/conversation are not available today** (`LauncherNotification` has no channel or conversation field): they need an additive platform field and ext keys in `NotificationItemMapper`; included in the vocabulary but delivered after S7 (N5). |
| `rss` | "Hide this feed", "Hide this article" | `Group(feedId)`, `ItemKey("rss:<feed>:<digest>")`, `Text(TITLE)` | "Hide this feed" is an exclusion (reversible, the feed stays configured), distinct from "Remove feed" in the RSS settings. No URL is stored in a rule, only the feed id and the digest. |
| `calendar` | "Hide this event", "Hide events like this" | `ItemKey(event id)`, `Text(TITLE, EXACT)` (recurring series), `SourceKey("calendar.all_day", Flag)` | A calendar-id matcher needs a new ext key `calendar.calendar` (additive, mapper change; not today). |
| `media` | "Hide media from this app" | `App(package)` | |
| `search` | "Hide this result", "Hide this section" | `ItemKey`, `Group("apps" / "settings")` | The query is never involved; rules never contain query text. |
| External sources (future, section 15) | "Hide from <provider>" | `Group`, `ItemKey`, `Text` with the provider's `ext.<source>.` keys only | A rule can only reference keys in the source's own namespace. |

### 14.5 Interaction with OFF, the dock, and other consumers

* **Sources OFF (Q13).** `OFF` means no subscription and no items, so no exclusion work happens; **rules
  are kept** and shown on the Sources page as "Source is off" with the count shown as an em dash. Turning the
  source back on applies them with no migration. Exclusion never changes a source's status: all-items-excluded
  is `READY` with an "Excluding N items" line, never `OFF` or `UNAVAILABLE`.
* **Dock dynamic section.** The dock's dynamic section is a lens binding evaluated through the same provider,
  so exclusions apply there automatically, as do the notification badges and dock notification cards, which
  today use `NotificationHideRuleFilter` (`NotificationCounterState`, `DockNotificationCardPlanner`) and move
  to the engine in S6. Dock overrides (11) do not interact.
* **Per-lens queries (13).** Applied to a parameterised source's results like any other.
* **Privacy content level (6.2 item 1)** is a *projection ceiling* applied after the lens; exclusions are
  removal before it. They compose and do not overlap.

### 14.6 Scope: per layout (N4)

> Superseded: the first revision proposed one global rule set and argued that per-layout would make a hidden app
> reappear when the phone is unfolded. The owner decided **per layout** and accepts that consequence: **hidden things may
> reappear on another layout, by design.** This section makes that safe and visible.

**Model.** Each layout has its own rule set. The rule type (14.2) is unchanged and carries no layout field; membership in a
set is the scope. Evaluation order is unchanged: the provider takes the rule set of the layout being drawn, applies source
exclusions first, then the lens filter. Non-lens consumers (app drawer and search, notification badges, dock cards) use
the rule set of the **current** layout.

**What "layout" means for exclusions (needs a decision, N10).** The layout is derived from the window size at runtime
(`HomeLayoutDeviceClassClassifier`: `PHONE`, `PHONE_LANDSCAPE`, `FOLDABLE`, `TABLET`, `DESKTOP`), so a phone rotated to
landscape is a different layout and, taken literally, un-hides everything. Recommendation: key the rule sets by an
`ExclusionScope` = device class with `PHONE_LANDSCAPE` folded into `PHONE` (rotation never changes what is hidden; folding or
using a tablet does). Workspaces, pool and lens library keep the plain device class. Everything below says "layout" for
"exclusion scope".

**Storage.** A separate `source_exclusions` key in the workspace DataStore holding `Map<ExclusionScope, List<SourceExclusionRule>>`,
**not** inside the workspace blob or `LayoutWorkspaces`. Reason: hiding is a privacy statement, so resetting workspaces,
a corrupt blob, "Copy from other layout" or a restore of a workspace-only backup must never un-hide something. Rules have no
dependents (unlike lenses), so nothing is gained by storing them with the library. Same `_prev` generation as the blob.
(Considered and rejected: a field next to the lens library; atomic with workspace copy, but exactly the coupling that makes
reset un-hide things.)

**Migration (the owner's data; lossless, idempotent).** Hidden apps (`AppVisibilityRepository`) and `NotificationHideRule`s
are global today. On first start of S6 they are **copied into every scope's rule set**: a hidden app becomes `App(package, profile,
activityName)` on `apps.all` (the activity name keeps it exactly as narrow as the stored `AppIdentity`); a hide rule keeps
its kind, value and mode (`APP` -> `App`, `TITLE`/`BODY` -> `Text(field, value, mode, app)`, `EMPTY_CONTENT` ->
`EmptyContent(app)`), always scoped to its one app as today. Ids are deterministic
(`mig:<scope>:app:<profile>:<package>/<activity>`, `mig:<scope>:notif:<ruleId>`) so a second run changes nothing, and
**exempt from the rule cap**. Importing a backup that predates this (`"hiddenApps"` and the settings' hide rules) runs the same
function. Nothing is mirrored back to the legacy stores (no older reader exists): after migration `AppVisibilityRepository` is a
thin adapter over the current scope's rules so un-converted callers still compile, and the legacy stores are ignored. A
destructive reset of exclusions is acceptable (re-creatable in minutes, 1.7).

**Making the consequence obvious.**
* Every exclusion surface carries the layout: Sources > exclusions and Hidden apps show layout tabs (the same device tabs as
  Settings > Workspaces) and the header "Rules for Phone". A rule row that has no equal rule on another layout shows
  "Only on this layout".
* The contextual **Hide** snackbar says where it applied: "Hidden on Phone. Undo | Manage | Hide on all layouts".
* **Apply to all layouts** exists on a rule and as a bulk action ("Copy these rules to the other layouts"); it adds copies
  with fresh ids, de-duplicated by matcher equality, so it is idempotent.
* Settings > Privacy states "Hiding rules apply to one layout at a time".

**Copy from other layout (workspaces and pool replaced, 9.5).** Exclusion rules are **not** replaced (a copy must not
un-hide). The confirm dialog has an unchecked option "Also add the hiding rules from <layout>", which **adds** them
(union, de-duplicated, fresh ids). "Reset workspaces" keeps rules.

**Backup.** Optional top-level `"exclusions": {"<scope>": [...]}`, restored as replace with Undo (5). Rules hold user-authored
text and app/feed identifiers, never item content or URLs.

**Diagnostics.** Counts only (rules per source per layout), never match text, labels, packages or feed ids.

Tests: scope mapping (`PHONE_LANDSCAPE` -> `PHONE`); migration copies into every scope, twice gives the same value, cap exempt;
isolation (a rule in one scope never hides in another); layered invariant per scope; copy-from-layout leaves rules untouched and
the optional merge de-duplicates; apply-to-all de-duplicates; the `AppVisibilityRepository` adapter reflects the current scope.

### 14.7 Authoring

* **Contextual** (like notification hide rules today): every item surface offers a **Hide** action in its
  item actions/overflow or long-press menu: "Hide this app", "Hide this feed", "Hide this event type", "Hide
  notifications like this", with the exact vocabulary in 14.4. Tapping creates the rule **immediately and
  visibly**: the item disappears with a snackbar "Hidden on <layout>: <what>. Undo | Manage | Hide on all layouts" (never silent). Broad rules
  show a one-line confirmation first (a text rule shorter than 3 characters is rejected; "Hide this app" is
  not confirmed because Undo is one tap).
* **Settings > Sources:** a list per source and an "All exclusions" page, **for the layout shown in the layout tab** (14.6). Each row shows what it matches
  in words ("Notifications from Slack (Work)", "Feed: Example blog", "Title contains 'sale'"), the current
  hidden count (computed from live source items, shown "-" when the source is off or has no permission), an
  enable/disable switch, edit (change mode, value) and delete with Undo. Hidden apps appears as the "Apps"
  entry of this list and keeps its own page (filtered view) so nothing is relocated for users who know it.
  The creating item's overflow is the only place a rule is *created without typing*; typed rules (text
  matchers) are authored on the edit page with a live preview of matching items (transient, fakes in tests).
* **Accessibility of the contextual action:** it must exist as a TalkBack custom action on the item (not only
  a long-press and never drag-only), have a unique spoken label ("Hide Slack notifications"), announce the
  result politely ("Hidden. Undo available"), keep focus on the next item, and the snackbar action is
  reachable and long enough (>= 10 s with an a11y timeout); 48 dp targets; reduced motion removes the
  item-collapse animation; switch access can reach Undo and Manage. Rows in Settings follow 2.8.
* **Never silent** is enforced structurally: the only domain function that adds a rule is
  `SourceExclusions.add(rule)`, called from a use-case that always returns the `Undo` token and the summary
  string; there is no background or heuristic creation.

### 14.8 Slice

All of section 14 is slice S6 (engine and migration first and pure; store, provider pre-step, legacy consumers and UI
after S2). Backup carries it in S8. Channel/thread and calendar-id keys are an additive follow-up after the flip (N5).

### 14.9 Tests

Engine truth table per matcher and match mode (EXACT/CONTAINS/WILDCARD semantics lifted from `NotificationHideRule` and
tested against `NotificationHideRuleFilter` and `withHiddenApps` on one shared corpus, which is the equivalence test that
catches real regressions); the per-layout tests of 14.6; layered invariant property test (lens output never contains an
excluded item); redaction invariants (`Text` never matches `SENSITIVE`; counts never include sensitive content); `OFF` keeps
rules; a disabled rule does not hide; add/edit/delete/Undo use-case; codec tolerance (unknown matcher kind dropped, never
throws); a11y action present on the contextual Hide (UI test).

---

## 15. External items in the dock, and ICS recurrence through a library (owner decisions; not WS10 implementation)

### 15.1 External items in the dock (decided; the earlier recommendation was "containers only")

Trust model: the rules in `workspaces-external-sources.md` section 4 (plain data, `Open`-only actions, target allowlist,
provenance, `SENSITIVE`-capable) were written for containers; the dock adds constraints: (1) the dock is always visible, so
external items are ordered **after** built-in items (a hard rule here) and draw as icon plus count only (the dynamic section
is an IconRow); (2) **dock budget**: external items **share** the dynamic section's slot budget (`notificationSlotCount`,
1..5), never extend it; proposal: external sources together at most half the slots, one slot always kept for built-ins when
any exist; (3) provenance reachable from a dock icon (long-press "From <app name>", an a11y custom action); (4) exclusions (14)
and `OFF` (Q13) apply; (5) `PRIVACY_SENSITIVE` external items show the icon only; (6) no auto-launch beyond the single
explicit tap. These become validation rules (`DockPairing` plus a source-trust check) in the slice that adds the first
external source; nothing in WS10 ships one.

### 15.2 ICS full recurrence: use a library (N8, decided; the earlier assumption was an RRULE subset)

Scope (unchanged by the choice): `RRULE` (`FREQ` including `YEARLY`/`MONTHLY` with `BYDAY`/`BYMONTHDAY`/`BYSETPOS`,
`INTERVAL`, `COUNT`/`UNTIL`), `EXDATE`/`RDATE`, `RECURRENCE-ID` overrides, all-day versus timed, time zones (`VTIMEZONE`,
DST), bounded expansion (the source asks for a window and an instance cap, never an unbounded expansion). Slice S12; slips
indefinitely, but the choice is recorded now so the contract is shaped for it.

**Selection criteria.** (1) Licence compatible with this project (MIT, `LICENSE`): permissive (Apache-2.0, BSD, MIT). (2) Size
and method count acceptable for an app that R8 shrinks; transitive dependencies listed and none Android-hostile (a second
logging or collections stack, reflection-only loading, Java 8 time back-ports that conflict with the platform; **minSdk is
31, so `java.time` is available and a `threetenbp`-style back-port would be a defect**). (3) Maintenance: a release in the last
two years and an active tracker. (4) Correctness against the RFC 5545 example corpus (below), especially `EXDATE`/`RDATE`,
`BYSETPOS`, and time zones/DST. (5) Can be isolated behind an interface so it is swappable (15.3). (6) Android evidence
(a real Android app uses it).

**Shortlist.** Facts marked **VERIFIED** were read on 2026-10-01 from Maven Central metadata/POMs and the project READMEs;
everything else is **UNVERIFIED** and must be settled in the spike.

| Library | Licence | Latest (Maven Central) | Size, dependencies | Notes |
| --- | --- | --- | --- | --- |
| **ical4j** `org.mnode.ical4j:ical4j` | BSD-3-Clause (VERIFIED, README) | 4.3.0, 2026-06-27 (VERIFIED); eight recent versions 4.1.1 to 4.3.0 listed | jar 1,675,672 bytes; compile deps `slf4j-api`, `commons-codec`, `commons-lang3`, `threeten-extra`; optional `caffeine`, `jparsec`, `groovy` (VERIFIED, POM). README: 4.x needs Java 11+, 3.x Java 8+ (VERIFIED) | Parser **and** recurrence engine in one; README references RFC 5545 and non-Gregorian rules but does not itemise `BYSETPOS`/`EXDATE`/`VTIMEZONE` (UNVERIFIED in detail). Used by the Android app jtx Board for RRULE/RDATE/EXDATE expansion (VERIFIED by search result; DAVx5's use of ical4j is UNVERIFIED). Largest of the three; Java 11 bytecode on Android needs a D8/desugaring check (UNVERIFIED). |
| **lib-recur** `org.dmfs:lib-recur` | Apache-2.0 (VERIFIED, POM) | 0.17.1, 2024-04-07 (VERIFIED) | jar 164,789 bytes; deps `rfc5545-datetime` 0.3, `jems2` 2.23.1 (VERIFIED, POM) | Recurrence **only** (no iCalendar parser, so a bounded parser of our own or another library is still needed). README: `EXDATE`/`RDATE` lists via `OfList`, time zones in recurrence sets, four calendar scales incl. RSCALE (VERIFIED); `BYSETPOS` not mentioned in the README (UNVERIFIED). README says the API "is not finalized yet and subject to change" (VERIFIED). Last release over two years old on the day of writing: weakest on criterion 3. |
| **biweekly** `net.sf.biweekly:biweekly` | BSD-2-Clause ("FreeBSD", VERIFIED, POM) | 0.6.8, 2024-01-07 (VERIFIED) | jar 639,121 bytes; deps `vinnie`, `jackson-core` (compile), `jackson-databind` (optional) (VERIFIED, POM); Java 1.6 target (VERIFIED) | iCalendar parser/writer; README states Android compatibility (VERIFIED). Whether and how well it **expands** RRULE (its recurrence iterator) is UNVERIFIED: the wiki pages could not be read. Last release over two years old. |

**Recommended pick: ical4j 4.x**, with lib-recur as the documented fallback. Reasons: it is the only one of the three
with a recent release, it covers parser, time zones and recurrence in one dependency (so no bounded parser of our own to
get wrong), and it has Android precedent. Costs to accept: the largest size (R8 will shrink it; irrelevant for a personal
build), four compile dependencies, and a Java 11 target that must be proven on-device. If the spike fails criteria 2 or 4,
use lib-recur for expansion plus a small bounded parser.

**Spike result (2026-10-01, #1395): ical4j 4.3.0, confined to its `Recur` engine behind a domain `RecurrenceExpander`;
lib-recur passed the same 52-case corpus and is the proven fallback.** Evidence, size estimate, decision and the not-done list
(Android size/R8/device run, parser) are in [`workspaces-ics-recurrence.md`](workspaces-ics-recurrence.md). The UNVERIFIED items
above that concern recurrence correctness are now settled by that corpus; the Android-specific ones are not.

**Spike plan (before S12 writes any source code; about a day, JVM plus one device run).**
1. Add the library to a throw-away module. Confirm it builds with the project's toolchain, check the APK size delta and
   that R8 keeps it working (run the corpus once on a release-shrunk build on the phone).
2. **Acceptance corpus** (a JUnit parameter table of ICS snippets with expected instance lists, each case cross-checked by
   hand against the RFC text): every example in RFC 5545 section 3.8.5.3 (daily/weekly/monthly/yearly with `COUNT`,
   `UNTIL`, `INTERVAL`, `BYDAY` with ordinals, `BYMONTHDAY`, `BYYEARDAY`, `BYWEEKNO`, **`BYSETPOS`** such as "last weekday of the
   month" and "second-to-last weekday", `WKST` effects), `EXDATE` and `RDATE` (including `RDATE` with `PERIOD`, and `EXDATE` of
   an instance that is also in `RDATE`), `RECURRENCE-ID` overrides (moved and cancelled instances), all-day (`VALUE=DATE`)
   versus floating versus UTC versus `TZID`, `UNTIL` as date vs UTC date-time, **DST transitions** (a daily 02:30 event on
   a spring-forward day, a 01:30 event on a fall-back day, a zone that changed its rules), an infinite `RRULE` with the
   window and instance cap (must stop at the cap), an invalid rule (must fail closed with a reason, never loop), and a hostile
   feed (huge `COUNT`, deeply nested, oversized). Note the RFC's example list has published errata: use the errata-corrected
   expectations (which entries are affected is UNVERIFIED; check while writing the table).
3. Record per case pass/fail for each candidate, expansion time for a 10 000-instance window, and heap. Decide with the
   criteria above; write the result into this section.

### 15.3 Isolation behind a domain interface

> Built so far (#1395): the narrower `RecurrenceExpander` seam in `core/domain` and the module `core/recurrence-ical4j`
> (not yet a dependency of `:app`). The `IcsEngine` below, which adds parsing, is still to build.

The domain never imports the library. In `core/domain` (pure):

```kotlin
/** A calendar feed's events with recurrence expanded for a bounded window. No library types cross this boundary. */
interface IcsEngine {
    fun expand(source: IcsDocument, window: TimeWindow, maxInstances: Int): IcsExpansion
}
@JvmInline value class IcsDocument(val bytes: ByteArray)               // size-capped by the caller
data class TimeWindow(val from: Instant, val to: Instant)              // java.time (minSdk 31)
data class IcsInstance(val uid: String, val start: ZonedDateTime?, val end: ZonedDateTime?, val allDay: Boolean,
                       val title: String, val recurrenceId: String?)
sealed interface IcsExpansion {
    data class Ok(val instances: List<IcsInstance>, val truncated: Boolean) : IcsExpansion
    data class Failed(val reason: IcsFailure) : IcsExpansion           // PARSE, RULE_INVALID, TOO_LARGE; never a message with content
}
```

The implementation lives in its own Gradle module (for example `external-ics-ical4j`) that is the **only** module depending on
the library; the app wires it through the source registry. The acceptance corpus is written against `IcsEngine`, so the same
tests run against any implementation, and swapping to lib-recur plus a bounded parser (or an in-repo engine later) is a module
swap with a green corpus as the gate. The source built on it maps `IcsInstance` to `Item`s with the `calendar.*` ext keys,
applies exclusions (14) and never stores event content.

---

## 16. Risk register (solo alpha)

L = likelihood, I = impact (H/M/L). Only the risks that matter for one developer on one device.

| # | Risk | L | I | Mitigation | Where |
| --- | --- | --- | --- | --- | --- |
| 1 | **Too much agent-written code that has never run on a device.** Workspaces, menu, editor, hosts and presets are merged but not wired into the shell; adding a pool, exclusions and dock overrides on top multiplies the unknowns. | H | H | S1 and S2 first (a runnable debug build), one slice at a time with the "works on my device" checklist before the next, developer toggle to bisect, existing `deviceVerify` suite kept green | 1.7, 7 |
| 2 | **Scope sprawl**: five subsystems (pool, per-layout library, per-layout exclusions, dock overrides, per-lens queries) plus ICS and a possible second navigation style | H | M | 12 slices with an explicit cut line; S10, S11, S12 and the sharing-creation UI slip indefinitely; Classic parity is opt-in (S-C) | 7, 10 |
| 3 | **Shared-pool bugs: dangling references, widget ownership, reference counting.** A widget host id bound twice or released while still placed; an item collected too early; a folder edited "in one place" changing another workspace | M | H | Reference counts derived, never stored; invariants enforced and self-healed by `validate`; widget single-placement rule and clone-with-consent; deferred host-id deletion with Undo; sharing only by explicit action; differential test of the adapter against the legacy engines | 9.2 to 9.10 |
| 4 | **Data loss on the owner's own layout** at the pool cut-over (S4) and exclusion migration (S6) | M | M | Backup export first; one round-trip unit test; by-eye check; destructive reset explicitly acceptable; git for code | 1.7, 9.9 |
| 5 | **Two navigation styles drift apart** (only if full-parity Classic is chosen, S-C) | M | M | Default plan avoids it (developer toggle, then dropped); if chosen: shared data and engines, chrome-only difference, matrix of UI tests, name it "Navigation style" | 10 |
| 6 | **Per-layout exclusions surprise the owner**: hidden items reappear on rotation, fold or tablet | M | L | Scope folds landscape into portrait (N10); layout label on every surface; "Hide on all layouts"; copy-from-layout never un-hides | 14.6 |

---

## Open questions for the owner

Decided: Q1 to Q19 and N1 to N9 (banners at the top). The first revision's questions N1 to N9 are answered and removed. New
questions raised by the second revision; each has a recommendation and none blocks S1 to S3.

| # | Question | Recommendation |
| --- | --- | --- |
| N10 | **Exclusion scope under rotation.** The layout is derived from window size, so a phone in landscape is `PHONE_LANDSCAPE`, a different layout. Per-layout exclusions taken literally un-hide everything on rotate (and the same already holds for workspaces and the pool). | Fold `PHONE_LANDSCAPE` into `PHONE` for exclusions (14.6); consider doing the same for workspaces, pool and lens library, since a landscape phone having its own home is surprising. Confirm. |
| N11 | **Classic (N7).** Confirm the default plan: a debug-only developer toggle that is deleted at S9, with full-parity Classic as the option in 10.3. | Default plan. If a second navigation style is ever wanted, build it as "Navigation style", not "Classic". |
| N12 | **New apps in a pool world (9.8).** With several `HOME_AND_FINDER` workspaces, place the new app in all of them (one shared `PoolApp`) or only the active one? | All opted-in workspaces of the layout, so no workspace silently lacks the app. |
| N13 | **Sharing creation UI.** Duplicate workspace clones by default (9.5); "Also show in...", "Keep items shared" and "Add a separate copy" are explicit actions. Ship them with the pool cut-over, or after it works? | After. The model and `PoolOps` ship in S3/S4; the UI actions follow once the cut-over has run on the phone. |

## Known limitations of this design

- Calendar selection, per-source scheduling and non-RSS/Search external sources are out of scope (#1366, #1374).
- Recents' and Media's exact permission actions are declared by their adapters (`SourceAccess`); S2 must verify each
  against the existing explicit flows.
- Widget-in-backup behaviour (5, 9.6) is from reading the codec and import path; check on the device before S4 and S8 rely on
  the placeholder design. Dock pin handling on uninstall (9.6) was not found in the code and must be verified.
- Section 9 is design level; adapter and `PoolOps` signatures are sketches to be settled in S3.
- ICS library facts marked UNVERIFIED (15.2) are settled only by the spike.
- Nothing in this document has been compiled or run; claims about code were checked by reading the files cited (Gradle is
  unavailable in the authoring sandbox).
