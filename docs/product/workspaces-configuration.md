# Workspaces: user configuration (WS10)

Status: proposed design for review, before any code. Tracking: #1363 (WS10 parent), #1364 (saved
lenses). Related: #1323, #1324, #1325 (legacy mode/settings problems this resolves), #1365 (RSS and
Search adapters), #1366 (other external sources, researched separately).

This document is about how a person *configures and lives with* the workspace system. The model itself
(sources, lenses, expressions, containers, workspaces) is in
[`workspaces-sources-lenses.md`](workspaces-sources-lenses.md); the editor (WS7), presets (WS8) and
the dock menu (WS6) are separate workstreams and are dependencies here, not scope.

Sections are split into **As built today** (verified against the code at the commit this was written)
and **Proposed**. Anything under "Needs owner decision" is collected again in
[Open questions](#open-questions-for-the-owner).

## Fixed decisions (owner)

1. The workspace system is **the default**, not opt-in, and part of normal configuration (Settings).
   New installs get a preset (Nova-style by default). Existing installs are migrated without loss. If
   anything fails to decode or resolve, Riffle falls back safely to the previous/default behaviour.
   Standard launcher mode keeps working throughout; nothing may block home, drawer, dock or settings.
2. Lenses are **saved and reusable** (a named lens library), not only inline.
3. RSS and Search adapters are in scope (#1365). Other external sources are explored in #1366.

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
| `HomeLayoutSet` stays the source of truth for placed items, pins, selected page and dock; migrated Home pages point at it through the `home.grid` source. | `workspaces-sources-lenses.md`, "Migration mapping" |

Consequence: "make it the default" is four distinct jobs, not one flag flip: wire storage and startup,
wire backup, build the Settings surface, and retire the mode settings that currently stand in for it.

---

## 1. Default-on rollout

### 1.1 What "default" means

On every launch of a release where the rollout is on:

1. The active layout's workspace is **resolved** (`WorkspaceSet.resolveActive(deviceClass, capabilities,
   sources)`), and the home surface draws that workspace.
2. The dock workspace menu exists (WS6) and Settings shows the Workspaces section.
3. The legacy mode pair (Home/Library) no longer drives what is shown (see section 3). Placed home items
   still live in `HomeLayoutSet` until the container cut-over (as documented in WS5).

"Default" does **not** mean workspaces are mandatory for the launcher to function. The classic path
(read `HomeLayoutSet`, draw as today) stays compiled in and is the fallback in every failure case below.
The classic path is retired only in the last slice, after at least one release in the wild.

### 1.2 Startup sequence

Keep the current constraint: no extra blocking work on the main thread beyond what the layout read
already does, and no blocking at all if the read is slow.

```
Process start
 ├─ loadHomeLayoutSetAtStartup()              (existing, blocking once, IO dispatcher)
 ├─ loadWorkspaceBlobAtStartup(timeout 500 ms) (new; same IO hop, bounded; null on timeout/error)
 │
 ▼  WorkspaceBootstrap.run(blob, layoutSet, isNewInstall)   (new, pure, never throws)
 │     1. decode blob            -> DecodeReport(set?, droppedWorkspaces, droppedLayouts, blobState)
 │     2. new install?           -> seed from the default preset (Nova-style) for every device class
 │     3. else ensureMigrated    -> WorkspaceMigration.ensureMigrated(decoded, layoutSet)
 │     4. rehydrate saved-lens refs (section 4)
 │     5. validate active workspace per device class (WorkspaceValidation), capabilities of the layout
 │  => BootstrapResult(set, outcome, notices)
 │
 ▼  outcome:
      Ready                 -> draw workspaces; write only if the set changed (idempotent)
      Repaired(notices)     -> draw workspaces; write; one dismissible notice
      Classic(reason)       -> draw the classic path; do not write; record reason for Settings
```

Rules:

- `isNewInstall` = no stored `HomeLayoutSet` **and** no workspace blob. Everything else (including a
  stored layout set with no blob, i.e. every current user) takes the `ensureMigrated` branch.
- `ensureMigrated` already never overwrites stored workspaces and is deterministic
  (`ws:<deviceclass>:<mode>`), so running it on every start is safe. The bootstrap writes only when
  `result != decoded`, so a normal launch performs no write.
- The first write after migration is the only place a user's data is created. It happens off the main
  thread (write-behind, mirroring `WriteBehindHomeLayoutRepository`), flushed in `onStop` like layouts.
- The blocking read is bounded. If it exceeds the bound the launcher starts in `Classic(timeout)` and
  loads workspaces asynchronously, then switches on the next resolution without a visible jump (the
  classic Standard default and the Nova-style preset are the same arrangement, by construction of
  `WorkspaceMigration.defaultFor`).

### 1.3 New installs: preset selection

- The home is drawn immediately from the **Nova-style preset** (WS8), with no gate. Until WS8 lands,
  `WorkspaceMigration.defaultFor` is the seed, which is already the Standard/Nova arrangement.
- Offer a **non-blocking** "Choose your home style" step in the existing first-run flow
  (`FirstRunRepository`; it already asks for the Home role). It is a sheet with the preset cards from
  the picker in section 2, Nova pre-selected; Skip keeps Nova. It must not delay the Home role request,
  and the launcher is fully usable while it is open or skipped.
- Choosing a preset replaces the layout's workspace list with that preset (one workspace) and marks it
  active and default. Presets are ordinary workspaces afterwards.

### 1.4 Flags: retired or replaced

| Today | Becomes |
| --- | --- |
| `WorkspaceMenuFeature.enabled` (global mutable, default `false`) | Deleted in slice S10. In between, replaced by `WorkspaceRollout` (below). Tests and previews keep a test-only override. |
| `DockShelfExpansion.enabled` | Untouched by WS10 (WS6 owns it). |
| `libraryOnlyLauncherViewModeAvailability()` | Retired in slice S9: the mode pair stops being a user concept, and the stored `LauncherViewMode` remains only as data to migrate from. |
| `LauncherSettings.contextual.enabled`, `DockModel.showNotificationCards` | Unchanged storage; their user-facing meaning is reconciled in section 3. |

```kotlin
// app layer, persisted next to the workspace blob (same DataStore, separate key)
enum class WorkspaceRolloutMode { ON, CLASSIC }          // user-visible escape hatch
internal data class WorkspaceRollout(
    val mode: WorkspaceRolloutMode = defaultMode,        // BuildConfig-level default; see 1.7
    val lastOutcome: BootstrapOutcomeCode? = null,       // for Settings diagnostics, never content
)
```

`WorkspaceRolloutMode.CLASSIC` is the escape hatch: it makes the bootstrap return `Classic(userChoice)`
immediately, does not delete workspaces, and the Workspaces settings page then shows a single row
"Use workspaces" to turn it back on. It sits under Settings > Workspaces > Advanced, always reachable
(also from the Settings search).

### 1.5 Failure handling and corrupt data

Principle: **no failure may leave the user with no home, drawer, dock or settings**, and no failure may
destroy user data silently.

| Situation | Behaviour |
| --- | --- |
| No blob, layout set present (every current user) | `ensureMigrated`; write once. |
| No blob, no layout set | New install path (1.3). |
| Blob is not a JSON object (`decodeWorkspaceSet` returns `null`) | Treated as corrupt: keep the raw blob once in a `workspaces_corrupt` slot (definitions only, no item content), rebuild with `ensureMigrated(null, layoutSet)`, outcome `Repaired`, notice "Your workspaces could not be read and were rebuilt from your layout". |
| Some workspaces dropped (codec drops undecodable ones and ones with no pages) | Layout still resolves (`repaired` fixes active/default). Outcome `Repaired`; the notice names the count. If a **previous-generation blob** is available, offer "Restore previous copy". |
| A layout (device class) ends up with no workspace | Reads as `WorkspaceMigration.defaultFor`; migration rebuilds it from the `HomeLayoutSet` entry on the next start. |
| Active workspace invalid for this layout's capabilities | `resolveActive` returns `FellBack`; the default is drawn; the menu and Settings say why (1.6). No write. |
| Active and default both invalid | Draw the built-in default (`WorkspaceMigration.defaultFor`) and say so. The user can still open Settings and Reset. |
| Blob has a newer schema than the app | Decode best-effort (codec does). Do **not** write back in a way that drops unknown fields: while the stored schema is newer than the app's, the app is read-only for workspaces (changes are kept in memory for the session only and a notice says why). Prevents a downgrade from destroying data. |
| Any exception escapes bootstrap | Caught at the boundary: `Classic(error)`. The error type is recorded, never the exception message or data. |

**Previous generation.** Each successful *changing* write first copies the current blob to
`workspaces_prev`. One generation only (bounded; definitions only). Cost is one extra string per edit.
This is what makes "Restore previous copy" and undo-after-restore cheap.

### 1.6 Per-layout behaviour and posture changes

- Workspaces are per device class (`HomeLayoutDeviceClass`) and independent, as already built.
  `LayoutCapabilities.expressions` is the per-layout "can this layout draw X" input; the compact layout
  can declare it does not draw, say, two-pane expressions.
- Posture change mid-use (fold/unfold, rotation that changes device class): the shell recomputes
  `resolveActive` for the new class. No write happens. The selected page is kept **by container id**
  (page-set selection is already by group key, `PageSetSelection`), so folding and unfolding keeps
  the user in place when the same workspace exists on both layouts, and goes to the new layout's
  active workspace's first page otherwise.
- A layout that falls back shows the message in the menu (already specified) **and** in Settings >
  Workspaces for that layout: "This layout can't draw *Index* on this screen, so *Standard* is shown.
  Edit workspace / Switch / Copy from other layout."
- Switching workspaces is a normal action on the current layout only; it changes that layout's
  `activeId` and nothing else.

### 1.7 Staged rollout, checkpoints, revert

| Stage | Ships | Default | Checkpoint before moving on |
| --- | --- | --- | --- |
| R0 | Nothing user-visible (today) | off | n/a |
| R1 **Shadow** (S1+S2) | Storage + bootstrap + `ensureMigrated` on every start; workspaces are written but **not drawn**. Local-only outcome counters in Settings > About diagnostics. | classic | Migration golden tests pass for every `HomeLayoutSet` fixture; cold start time unchanged within noise (benchmark); corrupt/timeout/newer-schema tests pass; a dogfood build ran a week with `lastOutcome = Ready` and no `Repaired` surprises. |
| R2 **Backup** (S3) | Backup export/import of workspaces and library | classic | Round-trip tests; manual restore on a second device. Independent of drawing, can ship alongside R1. |
| R3 **Preview** (S5, S6, S7, WS6/7/8 present) | Settings > Workspaces, Sources, Saved lenses, menu on; surface switch is a Settings toggle "Use workspaces (preview)" | classic, opt-in in Settings | Standard-mode checklist (`standard-launcher-mode.md`) passes with workspaces ON and OFF; accessibility and reduced-motion pass (section 2.6); screenshot tests green at compact and unfolded. |
| R4 **Default** (S9+S10) | Flip: new installs get the preset; existing installs migrated; mode settings retired; `CLASSIC` stays as an Advanced escape hatch | **workspaces ON** | All items in "Flip criteria" below. |
| R5 **Cleanup** (S11) | Delete `WorkspaceMenuFeature`, mode-pair UI, `libraryOnly...Availability` once a full release shipped at R4 with no `Classic(error)` reports | on | Zero unexplained `Classic(error)` in dogfood/beta. |

**Flip criteria (R3 to R4).** Every one must be true:

1. WS6 menu, WS7 editor, WS8 presets (at least Nova-style and the existing three migrated workspaces)
   are merged.
2. `./gradlew verify deviceVerify` green, including the migration golden tests and the standard-mode
   regression (home, drawer, dock, settings reachable) with workspaces ON, OFF, and corrupt.
3. A user on the shipped Library-only build upgrades with no change in what they see on first launch
   (the migrated shown-mode workspace equals the current screen) - verified manually and by a golden
   test using `libraryOnlyLauncherViewModeAvailability` data.
4. Backup from an R3 build restores into an R4 build and vice versa (older app ignores the new keys).
5. The Calendar/notification permission rules are unchanged: nothing new prompts at launch.
6. Rollback tested: flipping the default back to classic in a build leaves installs that did not touch
   the toggle on classic, and installs that did keep their choice.

**Revert.** Three levels, cheapest first: (a) user: Settings > Workspaces > Advanced > Classic;
(b) release: change the default constant to `CLASSIC` for users with `mode` unset (the choice is stored
only once the user changes it, so the default is a build constant, not a migration); (c) data:
workspaces are additive; `HomeLayoutSet` is untouched by workspace use until the container cut-over,
so deleting the workspace blob always returns to a working classic launcher. This is why the classic
path and the `HomeLayoutSet` source of truth are kept until R5.

Needs owner decision: whether R3 (opt-in preview in a release) is wanted, or internal builds only
before R4. Recommendation: internal/beta only, one release at most (see Open questions).

---

## 2. Settings information architecture

### 2.1 Where it lives

Today Settings has five groups (`SettingsPageGroup`): Home & layout, Appearance, Interaction &
accessibility, Apps & content, Permissions/privacy & backup. Proposal, keeping that structure:

| Group | Entry | Notes |
| --- | --- | --- |
| Home & layout | **Workspaces** (new, first row) | Switch, presets, rename, clone, delete, reset, copy to other layout, Advanced (Classic toggle). |
| Home & layout | Layout | Slimmed: grid, labels, dock-adjacent geometry. No mode, no template (section 3). |
| Home & layout | Dock | Pins, edge, size, appearance, and the dynamic section binding. |
| Home & layout | Floating dock | Unchanged. |
| Apps & content | **Sources** (new) | Replaces "RSS feeds" as a row; RSS becomes a source detail. Old route stays as an alias. |
| Apps & content | **Saved lenses** (new) | The library. |
| Apps & content | App drawer, Hidden apps | Unchanged (see section 3 for presentation overlap). |
| Permissions, privacy & backup | **Privacy** (new) | Section 6. |
| Permissions, privacy & backup | Permissions | Unchanged rows; Sources screen links to the same actions. |
| Permissions, privacy & backup | Backup | Extended (section 5). |

New `SettingsPage` entries: `WORKSPACES`, `WORKSPACE_DETAIL`, `SOURCES`, `SOURCE_DETAIL`, `LENSES`,
`LENS_DETAIL`, `PRIVACY`. Each gets a `SettingsPageEntry` with `searchAliases` (preset, workspace,
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

 Copy from other layout…        (unfolded -> this one, replaces this layout's workspaces)
 Advanced
   Use workspaces                [ on ]
   Restore previous copy         (only if one exists)
-------------------------------------------------------------
[ : ] = Rename · Duplicate · Make default · Reset to preset · Delete
```

- Radio list = switch workspace (one action, same `activate` as the menu). The marked row is the
  stored active one even while a fallback is displayed, matching the WS6 rule.
- **Reset to preset** needs to know which preset a workspace came from: `Workspace.presetId: String?`
  (additive, section 4.6). Without it the action is hidden, not guessed.
- **Delete** is disabled on the last workspace (domain rule: `remove` is a no-op) with the reason as
  supporting text, and asks for confirmation with an Undo snackbar. Deleting the default moves the
  default as `LayoutWorkspaces.remove` does.
- **Copy from other layout** shows what will be replaced ("Replaces your 3 workspaces on this layout"),
  confirms, and offers Undo (the previous generation blob makes undo exact).
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
`PERMISSION_REQUIRED`, `UNAVAILABLE`) plus a new `OFF` for a disabled source. Status is text, never
colour alone.

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
  - Notifications: hide rules list (existing `NotificationHidingSettings`, `AddNotificationHideRule` /
    `RemoveNotificationHideRule`), and the content level (section 6).
  - RSS: the existing feeds UI (`RssSettings`, refresh interval) moves here unchanged; storage is
    untouched. Refresh stays user-triggered as today.
  - Search (#1365): provider settings defined by that issue; the Sources page only hosts the row.
  - Apps: link to Hidden apps.
  - Calendar: only status/permission (calendar selection is not a thing today; out of scope).
- Source rows for sources #1365/#1366 have not shipped simply do not appear; the list is driven by the
  registry's descriptors (`SourceRegistry.descriptors()`), not hard-coded.

### 2.5 Saved lenses page

```
Saved lenses                                          [ + New ]
 Work notifications by app      Notifications · grouped · used in 2 places   >
 Recent, newest first           Recent apps · list · used in 1 place          >
 Unused lens                    Apps · no containers                          >
Lens detail
 Name [..........]   Sources: [Notifications x]   Filter / Group / Sort / Limit (WS7 lens builder, reused)
 Used by:  Standard > Inbox (page-set)   Work > Now (widget)       <- tap opens that container
 [Duplicate] [Delete]            Save is blocked with reasons if it would break a user (4.4)
```

The lens builder is WS7's lens step, hosted in a settings detail page, so the editing UI is written
once. Detail shows **Used by** from `LensLibrary.dependents`. Delete behaviour is in 4.3.

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
| Grid (columns, rows, visible dimensions) | `HomeGridSetting`, `HomeLayout.settings.grid` | Home grid page content, per `HomeLayout` | **Stays** (Layout page) until the placed-items container replaces `home.grid`. |
| Labels | `HomeLabelSetting`, `settings.labels` | Home grid and icon expressions | **Stays.** |
| Dock: pins, edge, size, appearance | `DockSetting`, `DockModel` | `DockModel` unchanged (workspace does not duplicate it) | **Stays.** |
| Dock: show notification cards, slot count | `DockModel.showNotificationCards`, `notificationSlotCount` | `WorkspaceDock.dynamicSection` (Notifications lens, limit = slots, IconRow), already how migration maps it | **Stays as one row**, rewritten to edit the active workspace's dynamic section: on = Notifications lens; off = `null`. Needs owner decision (3.3, Q5): per workspace or global. |
| Floating dock | `SettingsPage.FLOATING_DOCK`, `OverlayDockSettings` | None | **Stays.** |
| After leaving Library (`LibraryReturnTarget`) | `AppDrawerSettings.afterLeavingLibrary`, `LauncherShellLibraryReturn.kt` | Return rule in 3.2 | **Retire**; the stored value is ignored, the codec keeps reading and writing it for backup compatibility. |
| App drawer presentation (list/icons), icon grid columns | `AppDrawerSettings` | Finder page expression (`AlphaList` vs `IconGrid`) | **Stays** until the Finder page is the drawer; then maps to the Finder page's expression and the row is hidden. Needs owner decision (Q6). |
| Search result presentation | `SearchSettings.resultPresentation` | Search source (#1365) display | **Stays.** |
| Cards appearance (geometry, glass, colour) | `CardsSettings`, `SettingsPage.ADAPTIVE_STAGE_APPEARANCE` | Appearance of `Card`/`CardStack` expressions | **Stays**, renamed "Card appearance"; shown whenever any workspace uses a card expression, otherwise collapsed under Appearance. |
| Cards stage selector/spine, thread grouping, folded/unfolded show-all | `CardsSettings` fields | Page-set + dock dynamic section behaviour | **Stays** (dormant fields keep round-tripping); not exposed beyond what Cards appearance shows today. |
| Contextual behaviour (`ContextualSettings.enabled`) | `SettingsContextualPageContent.kt` | Independent: smart behaviour, not a layout choice | **Stays**; copy clarifies it is separate from workspaces. |
| Gestures | `GestureSettings`, `LauncherGestureMappings`, `Workspace.gestureBindings` | Global defaults, optional per-workspace overrides | **Stays**; per-workspace overrides are WS7. The dock-pull row follows the WS6 gesture decision (Q9). |
| Motion & haptics, reduced motion | `MotionSettings`, `ReducedMotionPreference` | Global | **Stays.** |
| RSS feeds page | `SettingsPage.RSS`, `RssSettings` | Source detail (RSS) | **Moves**; storage unchanged; old route aliased. |
| Notification hide rules | `NotificationHidingSettings` | Source detail (Notifications) | **Moves**; storage unchanged. |
| Permissions rows (Home app, notifications, overlay, calendar) | `SettingsPermissionsSection.kt` | Sources links to the same actions | **Stays** as the canonical place. |
| Hidden apps | `SettingsPage.HIDDEN_APPS` | Filter on the Apps source | **Stays.** |
| Backup | `SettingsPage.BACKUP` | Extended | **Stays**, section 5. |

Rule used for every row: if it expresses *which arrangement is on screen*, it is a workspace choice;
if it tunes how a standard Android launcher behaves (grid, dock, labels, permissions, hidden apps),
it stays; storage formats are never changed or removed by this, only their UI.

### 3.2 #1323: active mode is not preserved on return

Cause today: the return target is computed from a mode pair and a setting
(`afterLeavingLibrary`, `LibraryExitTrigger`, `modeAfterLeavingLibrary`), so leaving the launcher can
switch the mode. With workspaces there is no mode to switch: `LayoutWorkspaces.activeId` is the
durable "what was last active", written on switch, restored on every start.

Proposed rule (Needs owner decision, Q7):

1. App launch then return, or process restart: show the **active workspace** at the **page that was
   showing** (page position is already stored for the home layout as `selectedPageId`; for page-sets,
   by group key).
2. Home press while already on the launcher: go to the active workspace's **first page**, as a
   standard launcher does; a second press does nothing (standard).
3. Home press while the Finder (All apps) is open: close the Finder and return to the page it was
   opened from.
4. Nothing ever changes the active workspace except an explicit user action.

Testable as a pure reducer (`WorkspaceReturnReducer`) with the cases above, closing #1323 with
regression tests rather than a mode-specific patch. This refines, and should be reconciled with, the
Home-press semantics in #1176.

### 3.3 #1324: Standard mode unreachable

Standard is the **Nova-style preset**, which is also the new-install default. After S9 there is no
"Standard mode" to hide: the Standard arrangement is a workspace anyone can select in Workspaces or the
dock menu, and the migrated "Standard" workspace is preserved for users who had one. The stored
`STANDARD_APP_DRAWER` layout is no longer resolved to Library on load. Recorded as the deliberate
redesign decision that #1324 asked for.

### 3.4 #1325: redundant options

Resolved by the table: Home screen, view mode, template and Modes sections all express "which
arrangement", so they collapse into the Workspaces page and the preset picker; the Layout page keeps
only grid and labels. Until S9, the rule from #1325 stands ("hide options that don't apply while only
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

/** Global per install. Ordered for display. Bounded by MAX_SAVED_LENSES (100). */
data class LensLibrary(val lenses: List<SavedLens> = emptyList()) {
    fun find(id: LensId): SavedLens?
    // All operations return the receiver unchanged when they cannot apply (WS5 convention).
    fun add(name: String, lens: Lens, ids: WorkspaceIdFactory): LensLibrary
    fun rename(id: LensId, name: String): LensLibrary
    fun duplicate(id: LensId, ids: WorkspaceIdFactory): LensLibrary   // "<name> copy", fresh id
    fun move(id: LensId, toIndex: Int): LensLibrary
}

data class LensBinding(
    val lens: Lens,                    // UNCHANGED: always the lens to draw (see "snapshot")
    val expression: ExpressionKind,
    val ref: LensId? = null,           // NEW, default null = inline lens, exactly today's meaning
)

data class WorkspaceSet(
    val layouts: Map<HomeLayoutDeviceClass, LayoutWorkspaces> = emptyMap(),
    val library: LensLibrary = LensLibrary(),   // NEW, default empty
)
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
| Per-layout library | A lens is sources + filter + group + sort; nothing in it is layout specific, and expression validity is checked per binding anyway. Per-layout would force "copy to other layout" to copy lenses and break "edit in one place". |

**Global library.** One library per install, stored in the same blob as the workspaces (the
`WorkspaceSet`), so a library edit and the snapshot refresh of its dependents are written in one
atomic DataStore edit. This avoids the failure mode of a library and workspaces diverging after a
crash between two writes.

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
| **Delete** while used | Never blocked, never destructive: the dialog says "Used in N places" and offers **Detach** (default: every dependent becomes inline, keeping its lens and drawing exactly as before, then the entry is removed) or **Replace with...** (another saved lens, validity-checked per dependent; dependents it would break are listed and stay detached). The domain operation `LensLibrary.remove(id, policy)` returns the new library and the rewritten `WorkspaceSet`. |
| **Detach** a binding | `ref = null`; the snapshot becomes the inline lens. Always valid. |
| **Promote** ("Save as lens") on an inline binding | Adds a library entry from `binding.lens` with a name the user types, sets `ref` on that binding (and offers "use it in the N other containers with an identical lens", the exact-equality dependents). |
| **Dangling ref** (library lacks the id: tampered/old backup, partial restore, bug) | Never crashes and never blanks the container. The binding keeps its snapshot and draws normally, the ref is reported as dangling, and the editor/Settings show "Saved lens 'X' is missing: Using a copy" with **Save as lens** or **Detach**. This is stronger than a "missing lens" empty state: the user's screen does not change because a definition was lost. |
| **Clone workspace** (`LayoutWorkspaces.duplicate`) | Refs are **shared**, not deep-copied: the clone is a new workspace using the same saved lenses, so editing a lens updates both, which is what "reusable" means. A "Make independent" action detaches all refs in a workspace. |
| **Copy to other layout** (`copyFromOtherLayout`) | The library is global, so refs are preserved and shared. No lens copy needed. |
| **Preset install** (WS8) | Presets ship **inline** lenses (self-contained, no hidden library writes, no id collisions, trivially resettable). Users promote the ones they want. A preset *may* later declare suggested saved lenses; not needed for WS10. |

### 4.4 Validity when a saved lens is edited

The pairing rule is unchanged (`LensExpressionValidity`); what is new is a pure dry run.

```kotlin
data class LensDependent(
    val deviceClass: HomeLayoutDeviceClass,
    val workspaceId: WorkspaceId,
    val containerId: ContainerId?,        // null = the dock's dynamic section
    val expression: ExpressionKind,
    val perGroup: Boolean,                // page-set: checkPerGroup
)

data class EditImpact(
    val dependents: List<LensDependent>,
    val wouldBreak: List<Pair<LensDependent, List<LensIssue>>>,
)

object LensLibraryOps {
    fun dependents(set: WorkspaceSet, id: LensId): List<LensDependent>
    fun previewEdit(set: WorkspaceSet, id: LensId, newLens: Lens,
                    sources: List<SourceDescriptor>?): EditImpact
    fun applyEdit(set: WorkspaceSet, id: LensId, newLens: Lens): WorkspaceSet   // refresh snapshots
    fun remove(set: WorkspaceSet, id: LensId, policy: RemovePolicy): WorkspaceSet
    fun rehydrate(set: WorkspaceSet): Rehydrated    // set + dangling refs, used by decode and import
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

### 4.5 Persistence, codec versioning, migration

- `WorkspaceSetCodec`: set schema `1 -> 2`. Adds root key `"library": {"lenses": [{"id","name","lens"}]}`
  and per-binding optional `"ref": "<id>"` next to the existing `"lens"` and `"expression"`. A
  workspace's own schema version (`CURRENT_WORKSPACE_SCHEMA_VERSION`) can stay at 1 because the
  addition is optional and ignorable.
- **Migration from inline: none needed.** A v1 blob decodes with an empty library and `ref = null`
  everywhere, which is exactly the old meaning. "Inline stays valid" is a property, not a migration
  step. Promotion is the only way a ref appears.
- **Downgrade safety.** A v1 decoder ignores `"ref"` and `"library"` and reads the snapshot, so an
  older app still draws every container correctly (it just loses the sharing). This is also why the
  snapshot is stored rather than recomputed.
- Decode order: decode library, decode workspaces, then `rehydrate` (for each resolvable ref set
  `lens := library lens`, library wins; dangling refs keep the snapshot and are reported). A library
  entry that fails to decode is dropped, which turns its bindings into dangling-with-snapshot rather
  than data loss.
- Decode still never throws; the `DecodeReport` (1.5) gains `droppedLenses` and `danglingRefs`.
- Writes go through `LensLibraryOps` / the editor so the invariant holds; a property test asserts it
  after random operation sequences (4.8).
- Item content: the library stores `Lens` only. `Lens` has no item content by construction, and
  there is still no `Item` codec.

### 4.6 Exact contract changes and sequencing for in-flight PRs

All changes are additive with defaults; no existing call site or test must change.

| Area | Change | Impact on in-flight work |
| --- | --- | --- |
| WS0 contracts | `LensBinding.ref: LensId? = null`; new `LensId`, `SavedLens`, `LensLibrary`, `LensLibraryOps`; `WorkspaceSet.library` default empty; `Workspace.presetId: String? = null` (for Reset to preset) | Existing `LensBinding(lens, expression)` calls compile unchanged. `data class` `copy` and equality now include `ref`/`library`: tests comparing whole sets need no change when both are default. |
| WS5 codecs/migration | Set schema 2 with `library`; binding `ref`; workspace `"preset"` optional string. Migration (`HomeLayoutWorkspaceMapper`, `MigratedLenses`) untouched: they emit inline. `ensureMigrated(stored, layoutSet)` preserves `stored.library`: today it builds `WorkspaceSet(migrated.layouts + stored.layouts)`, which would drop a library, so it must become `stored.copy(layouts = ...)`. | One small fix in `WorkspaceMigration.ensureMigrated`, covered by a test. |
| WS7 editor | **Phase 1 (no dependency):** inline only, as now. **Phase 2:** adds "Use saved lens" and "Save as lens" to its lens step, and the impact list on edit. | WS7 can merge today; phase 2 is a follow-up PR after S4 below. It must route edits through `LensLibraryOps`, not mutate bindings of referenced lenses directly. |
| WS8 presets | Presets stay inline. Optionally set `Workspace.presetId`. | Zero rework. If WS8 stores presets via `LensBinding(...)` constructors, nothing changes. |
| WS6 menu | None. It reads `binding.lens` snapshots. Optionally shows nothing about refs. | None. |
| WS4 hosts/planners | None (snapshot). | None. |
| Backup | `workspaceSet` already carries the object; the library rides inside it (section 5). | None. |

Recommended merge order to minimise rework: WS6, WS7 (inline) and WS8 merge as they are -> S4 (library
domain + codec) -> WS7 phase 2 and S7 (library UI). S4 can also be written before those merge because
nothing it adds is required by them.

### 4.7 Limits

`MAX_SAVED_LENSES = 100`, name 1..40 chars. These follow the bounded-settings convention used by
`MAX_NOTIFICATION_HIDE_RULES` and `MAX_CONFIGURED_FEEDS`; revisit if a real need appears.

### 4.8 Tests specific to lenses

Domain: library ops (unique names, cap, no-op rules), `rehydrate` (resolved, dangling, library
wins), property test of the snapshot invariant after random edit/rename/duplicate/delete/promote/
detach sequences, `previewEdit` (per-group pairing, dock, page-set, widget), `remove` policies,
clone shares refs, `copyFromOtherLayout` preserves refs, `ensureMigrated` keeps the library, codec
golden files (v1 blob decodes unchanged; v2 blob; v2 blob read by a v1-style decode ignoring unknown
keys; dangling ref; corrupt library entry; hostile ids).

---

## 5. Backup and restore

**As built:** see the table at the top: the document and codec support `workspaces`, but neither export
nor import is wired. **Proposed:**

- **Export.** `LauncherBackupExportCoordinator` gets the workspace repository and passes
  `workspaceSet` (including the library) into `launcherBackupDocument(...)`. Written as today under
  `"workspaces"`. The set is the in-memory current one, not the possibly stale stored blob.
- **Document version.** Do **not** bump `LAUNCHER_BACKUP_DOCUMENT_VERSION` (an older app requires `== 1`
  and would reject the whole file). New data is optional keys inside `"workspaces"`, versioned by the
  set's own schema (`WorkspaceSetCodec`). An older app restoring a newer backup ignores workspaces and
  still restores layouts and settings, i.e. the classic data is always complete in the document.
- **Import.** Extend the restore path (`ImportLauncherBackup` -> `withImportedBackup`) with a
  workspace repository:
  1. Decode and `rehydrate` the document's set (never throws).
  2. If it decoded to at least one valid layout: **replace** the stored set (workspaces and library)
     atomically with it.
  3. If `workspaceSet` is absent (a pre-workspace backup) or unusable: **do not keep the existing
     workspaces** (they describe the previous install's layouts, not the imported ones); rebuild with
     `WorkspaceMigration.migrate(document.homeLayoutSet)` and an empty library. Outcome noted in the
     import summary.
  4. Keep the previous generation blob so "Undo restore" is exact.
  5. Re-resolve the active workspace for the current device class; a layout it cannot draw falls back
     with the usual message.
- **Conflict policy.** Restore is **replace**, consistent with how layouts, settings and hidden apps
  already behave (`saveHomeLayoutSet`, `saveLauncherSettings`, `replaceHiddenAppIdentities`). No merge
  in v1 (merging two libraries needs per-lens conflict UI for little value). The import dialog says
  what will be replaced and shows counts ("3 workspaces, 5 saved lenses").
- **Per-source and per-device data.** Disabled sources travel with the set. Permission grants never do
  (they cannot be restored); sources restored as enabled but ungranted show "Needs permission" and
  nothing prompts, per the Calendar policy.
- **Validation.** `isImportableBackup` stays about the classic data. Workspace problems never reject a
  backup; they downgrade to step 3.
- **Never item content.** Backups carry lens definitions, workspaces, settings, feed *configuration*
  (as today), never items, article cache (`FeedArticleCacheDocument` is separate and not backed up),
  notification content, calendar events or media state.
- **Single-workspace export/import (cheap, optional).** A workspace encodes standalone; because every
  binding carries its snapshot, a shared file is self-contained. On import, refs whose id exists with
  an identical lens stay refs; otherwise the binding is imported **inline** (no library mutation, no
  surprise), with an optional "also add its saved lenses to my library" (fresh ids, name-clash suffix).
  Gated behind the shared-file work; cut line if schedule is tight (Q10).

---

## 6. Privacy

### 6.1 As built

- `Item.privacy` is `VISIBLE` / `SENSITIVE`; the lens `project` step is the **only** redaction point
  (`SENSITIVE_ITEM_FIELDS` are cleared whatever `project` says; sort keys, pinned state and `ByExt`
  groups are computed from the redacted view so they cannot leak).
- Quiet profiles yield `SENSITIVE` items with no content; locked, unavailable and unknown profiles
  yield none. Private/confidential calendar events are `SENSITIVE`.
- Notification hide rules (`NotificationHidingSettings`) drop matching notifications before they
  become items; `hide-rule edits apply on the next refresh` (known gap).
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
2. **Hide sensitive content while the device is locked** (default **on**): while
   `KeyguardManager.isDeviceLocked`, every source declaring `PRIVACY_SENSITIVE` is evaluated as if
   its items were `SENSITIVE`. Covers Direct Boot / locked-profile cases where the launcher can be
   visible; the secure keyguard normally covers it anyway, so this is a seatbelt, not the main
   defence. Not a user-facing toggle unless an owner asks (Q8).
3. **Screenshots and recents** (default off): "Hide launcher content in screenshots and recents" sets
   the window's secure flag. It also blocks the user's own screenshots of Home, so it is opt-in with
   that said plainly. Needs owner decision (Q8).
4. **Notification hide rules** stay the strongest control (drop before it exists) and move to the
   Notifications source page; the page links them from Privacy.
5. **Work profile / quiet profile** behaviour is unchanged and described on the Privacy page.

### 6.3 What may appear where

| Surface | Visible items | Sensitive items | Hidden-content placeholder |
| --- | --- | --- | --- |
| Home pages and widgets | per content level | redacted per projection | "Hidden content" (WS3 contract) |
| Dock dynamic section | icons and counts | icon only | n/a |
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

## 7. Implementation slices

Each slice is small, independently shippable, and states what gates it. "Gate" = what keeps it
invisible or safe until its turn. Sizes are rough PR counts.

| ID | Slice | Depends on | Gate / ships as |
| --- | --- | --- | --- |
| S1 | **Bootstrap (domain).** `WorkspaceBootstrap`, `DecodeReport`, `BootstrapOutcome`, newer-schema read-only rule, fall-through rules; pure. Unit + golden tests. | WS5 | Not called yet. |
| S2 | **Storage and startup (app).** `WorkspaceRepository` implementation over the existing `DataStoreWorkspaceStore` with write-behind and one previous generation; bounded startup read; shadow `ensureMigrated` writes; `WorkspaceRollout`; outcome counters. Nothing drawn. | S1 | Rollout = classic. Ships R1. |
| S3 | **Backup wiring.** Export passes `workspaceSet`; import applies/rebuilds/undo; import summary; golden backup fixtures. | S2 | Ships R2, independent of UI. Fixes the existing gap. |
| S4 | **Lens library (domain).** `LensId`, `SavedLens`, `LensLibrary`, `LensBinding.ref`, `WorkspaceSet.library`, `LensLibraryOps`, set schema 2, `ensureMigrated` keeps library, `Workspace.presetId`. All additive. | WS0, WS5 | Pure; no UI. Can be built in parallel with S1-S3 and in-flight WS7/WS8. |
| S5 | **Workspaces settings page.** Planner + page + Advanced toggle, fallback message, copy-to-layout, undo. | S2, WS6 (shell holds `WorkspaceSet` and actions), WS8 for Reset to preset and preset picker | Behind rollout = classic until R3. |
| S6 | **Sources page.** `SourcesPlanner`, status rows, permission actions via existing flows, enable/disable (+ `LensAvailability.OFF`), Notifications and RSS detail (RSS page moved, route aliased), Search/other rows appear when registered. | S2, WS1; #1365 for RSS/Search rows | Same gate. |
| S7 | **Saved lenses page + editor integration.** Library list/detail, used-by, delete policies, impact list on edit. | S4, WS7 phase 2 | Same gate. |
| S8 | **Privacy controls.** Per-source content level, locked-device rule, optional screenshot flag, Privacy page. | S6 | Defaults equal today. Can ship before the flip. |
| S9 | **Legacy reconciliation.** Remove Home screen/view mode/template/Modes UI, add return rule (`WorkspaceReturnReducer`), retire `libraryOnly...Availability`, hide `afterLeavingLibrary`, hide template row early as a quick win. Closes #1323, #1324, #1325. | S5, WS8 | Flips with S10. |
| S10 | **Flip the default.** New-install preset (Nova), non-blocking first-run chooser, `WorkspaceRolloutMode.ON` default, release notes. | S1-S9, WS6, WS7, WS8; flip criteria 1.7 | R4. |
| S11 | **Cleanup.** Delete `WorkspaceMenuFeature`, dead mode code, unused settings UI; keep codec fields. | one release after S10 | R5. |

Parallelism: S1->S2->S3 is a chain that can start now; S4 is independent of that chain; S5/S6/S7/S8
can proceed in parallel once their inputs exist.

### 7.1 Test and validation plan

**Domain (JVM, no device).** Bootstrap decision table (every row of 1.5 including timeout, corrupt,
partial, newer schema, capability fallback); `ensureMigrated` idempotence and library preservation;
library ops and the snapshot invariant (4.8); `WorkspaceReturnReducer` (3.2 cases, a regression test
per #1323 scenario); planners for Workspaces/Sources/Lenses pages (rows, disabled reasons, last
workspace cannot be deleted, status mapping); backup import decision (valid set, absent set, corrupt
set, old-app backup) and "never item content" (assert the encoded document has no item fields);
privacy ceiling (`project` intersection never widens; SENSITIVE always wins; locked-device rule).

**Migration golden tests.** Fixture `HomeLayoutSet`s: new install, Library only (current shipped),
Standard, Cards, all three modes, multiple device classes, a stored hidden-mode layout, preferred-mode
map only, mode ring legacy data. Golden expected `WorkspaceSet` JSON checked in; plus golden blobs for
schema v1 (no library) decoding unchanged under v2 code, and a v2 blob decoded by v1-style rules.

**Screenshot tests (WS9 harness).** Workspaces page, preset picker, Sources page (each status), Saved
lenses page (with dependents, with a would-break list), fallback banner, privacy page; compact and
unfolded; light/dark; 200% font scale; reduced motion on. Fakes only.

**Instrumented (`deviceVerify`).** Standard-mode regression with workspaces ON, OFF and corrupt blob:
home, drawer, dock, settings reachable; startup with blob absent/corrupt/huge; backup export/import
round trip; posture change keeps the selected page; no permission prompt at launch (calendar and
notification access), per the existing policy.

**Manual device checklist (documented in each UI PR).**
1. Upgrade from the shipped Library-only build: first launch looks identical; Settings shows
   Workspaces; Standard is selectable.
2. Fresh install: Nova-style home, first-run chooser skippable, Home role flow unaffected.
3. Fold/unfold mid-use: no flicker to a different workspace, fallback message appears only when real.
4. Corrupt the blob (debug action): launcher still starts, notice shown, Restore previous works.
5. Turn Classic on and off: no data loss; Home/drawer/dock fine in both.
6. Sources: each needs-permission source shows rationale before any system dialog; deny, deny
   permanently, revoke in system settings, return: states correct, nothing prompts by itself.
7. Saved lens: create, use in two containers, edit (clean), edit (breaks one container), delete used.
8. Backup on device A, restore on device B and on an older build.
9. TalkBack: traverse the three new pages, perform every action without drag; font 200%; reduced
   motion; switch access.
10. Locked-device and quiet-profile redaction with sensitive notifications and a private calendar event.

---

## Open questions for the owner

| # | Question | Recommendation |
| --- | --- | --- |
| Q1 | **Preview release (R3):** ship an opt-in "Use workspaces (preview)" to all users for one release before the flip, or internal/beta only? | Internal and beta only. The classic path remains the escape hatch after the flip, and a public opt-in adds a state to support. |
| Q2 | **Escape hatch visibility:** keep "Use workspaces / Classic" in Settings permanently, or remove at R5? | Keep it under Advanced for at least two releases after R4, then reassess from usage of `Classic(error)`. |
| Q3 | **Lens scope:** global library (this design) versus per layout. | Global, stored with the workspaces. Revisit only if a lens needs layout-specific sources, which none does today. |
| Q4 | **Presets: inline or saved lenses?** | Inline. Self-contained, resettable, no hidden library writes; users promote what they want. |
| Q5 | **Dock notification cards toggle:** per workspace (edits `dynamicSection`) or one global switch? | Per workspace (matches the model and migration), shown as one row under Dock for the active workspace, labelled with its name. |
| Q6 | **App drawer presentation setting** after the Finder page is the drawer: keep as a global default, or move into the Finder page's expression? | Move into the Finder page (retire the row) once Finder replaces the drawer; keep until then. |
| Q7 | **Return rule (#1323):** restore active workspace and page; Home press goes to first page; Finder closes back to origin. | Adopt as written in 3.2 and reconcile with #1176. |
| Q8 | **Privacy defaults:** is "hide sensitive content while locked" on by default and silent (recommended), and is the optional screenshot/recents block wanted at all? | Locked rule on, no toggle. Screenshot block: include, off by default, clearly worded; drop if the owner dislikes blocking user screenshots. |
| Q9 | **Dock pull** while modes are retired: it is the Home/Library switch in `gestures.md`. After S9 should it open the workspace menu directly, instead of via the explicit dock affordance WS6 proposes? | Yes, at S9, with the same accessibility action and Ctrl+arrow equivalent. Needs the owner to confirm the gestures.md change since WS6 owns the interim. |
| Q10 | **Single-workspace share/export:** include in WS10 or defer? | Defer to after S7; the design (snapshots make it self-contained) allows it with no model change. |
| Q11 | **Restore policy:** replace (this design) or offer merge of saved lenses and workspaces? | Replace, with Undo restore. Merge is a later feature if people ask. |
| Q12 | **`home.grid` source and page ids:** the migrated Home pages reference `HomeLayout` pages by `GroupKeyIs(pageId)`. Since modes retire from the UI but three `HomeLayout`s still exist, are page ids unique across a device class's layouts? If not, `home.grid` needs the layout key. | Verify with a test over real fixtures before S10; add the layout key to the source if any collision is possible. This is the main correctness risk of the flip. |
| Q13 | **Source disabled state:** add `OFF` to `LensAvailability` (this design) versus reusing `UNAVAILABLE` with a settings-derived message. | Add `OFF`; reuse of `UNAVAILABLE` would show a misleading "unavailable". |

## Known limitations of this design

- Calendar selection, per-source scheduling and non-RSS/Search external sources are out of scope here
  (#1366 covers other sources).
- Recents' and Media's exact permission actions are declared by their adapters (`SourceAccess`) and
  were not enumerated here; the Sources page must read them from the adapters, and S6 must verify each
  against the existing explicit flows.
- The container cut-over (placed items leaving `HomeLayoutSet`) is not part of WS10; until then
  `HomeLayoutSet` stays the placement source of truth, which is what keeps revert cheap.
