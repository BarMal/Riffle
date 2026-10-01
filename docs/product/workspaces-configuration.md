# Workspaces: user configuration (WS10)

Status: proposed design, **revised 2026-10-01 after the owner's decisions** (see the banner below), still
before any code. Tracking: #1363 (WS10 parent), #1364 (saved lenses). Related: #1323, #1324, #1325 (legacy
mode/settings problems this resolves), #1365 (RSS and Search adapters, merged), #1366 (other external
sources, researched, merged), #1374 (RSS refresh, separate issue, not WS10).

This document is about how a person *configures and lives with* the workspace system. The model itself
(sources, lenses, expressions, containers, workspaces) is in
[`workspaces-sources-lenses.md`](workspaces-sources-lenses.md); the editor (WS7), presets (WS8) and
the dock menu (WS6) are separate workstreams and are dependencies here, not scope.

Sections are split into **As built today** (verified against the code at the commit this was written)
and **Proposed**. Anything under "Needs owner decision" is collected again in
[Open questions](#open-questions-for-the-owner).

> **Revision 2026-10-01: owner decisions.** The owner answered Q1 to Q19 and the external-sources and
> search questions in a comment on #1363. Where the answer matched the earlier recommendation the text
> below was only updated in place. Rows marked **(differs)** changed the design and have real new work:
> they are the reason for sections 9 to 16 and for the re-sequenced slices (section 7) and rollout
> (section 1.7). A later requirement from the owner (source exclusion rules) is section 14.
> Sections still describing the *old* answer say so ("superseded by"), so nothing is silently wrong.
>
> | Q | Decision | What changed in this document | Why it matters |
> | --- | --- | --- | --- |
> | Q1 | Preview: internal/beta only | 1.7 stage R3 is internal/beta | None. |
> | Q2 | Keep "Use workspaces / Classic" permanently **(differs)** | 1.7, 1.1; new section 10 (what classic means, how to bound it) | The classic path is a supported product path, not a temporary fallback: cost and test matrix. |
> | Q3 | Lens library **per layout** **(differs)** | Section 4 rewritten (library lives in `LayoutWorkspaces`; copy-from-layout copies lenses) | Reverses the earlier "global, shared refs" design. |
> | Q4 | Presets **use saved lenses** **(differs)** | 4.3, 4.6 and 4.9 (install and reset write to the layout library) | The merged inline WS8 presets need a small rework. |
> | Q5 | Dock notification cards: per workspace | 3.1 row (no change) | Matches `WorkspaceDock.dynamicSection`. |
> | Q6 | Drawer presentation moves into the Finder page expression | 3.1; 8.3; `FINDER_EXPRESSIONS` must allow `ICON_GRID` | Today Finder accepts only Categories or AlphaList, drawer "Icons" has no home. |
> | Q7 | Return behaviour is a **Settings choice**: Restore / First page / Start page, default Restore **(differs)** | 3.2 rewritten; IA row in 2.1 | A setting, not a fixed rule. |
> | Q8 | Drop the locked-device rule; keep optional screenshot/recents setting **(differs)** | Section 6 (rule and setting removed), slice S16 | Profile locks and notification hide rules stay. |
> | Q9 | Dock pull opens the workspace menu once modes retire | 3.1; section 11.5; `gestures.md` note | Interacts with per-workspace dock hiding (11.4). |
> | Q10 | Single-workspace export/import: deferred | Section 5 | Cut line, not designed further. |
> | Q11 | Restore = replace, with Undo | Section 5 (unchanged) | None. |
> | Q12 | `home.grid` page-id uniqueness is not a decision | Becomes a test (section 7.1) and is **mooted** by section 9 once placed items move | |
> | Q13 | Add `OFF` source status | 2.4 (unchanged) | |
> | Q14 | Start page per workspace; Finder hidden from the pager unless start page. iOS-style home = home pages with placed items, Finder = All apps as Categories at the end | 8.3, 3.2, section 9 (placed items) | |
> | Q15 | Skin = existing theme preset, per-workspace override | 8.7 (unchanged) | |
> | Q16 | Placed items **move into workspaces now** **(differs)** | New section 9 (the largest item), staged migration, risk register | HomeLayoutSet stops being the long-term source of truth, so cheap revert is gone. |
> | Q17 | **Per-workspace dock overrides** **(differs)** | New section 11 | Edge, size, hidden, dynamic section, layered over the shared `DockModel`. |
> | Q18 | One default: Nova with Finder | 8.1 (unchanged) | |
> | Q19 | Favourite/frequent as **All-apps lenses**, no new adapters **(differs)** | Section 12; flip-blocker language removed | Needs a definition of "favourite" and "frequent". |
> | - | Tokenised feed/ICS URLs excluded from backup | Section 5 | |
> | - | External items allowed in the dock; ICS full recurrence **(differs)** | Section 15, plus a note in `workspaces-external-sources.md` | Trust model and dock budget; larger ICS scope. |
> | - | Play data-safety/privacy declarations owned by the owner | PR checklist line, section 7.2 | |
> | - | **Per-lens search queries now** **(differs)** | New section 13 | Additive WS0 hook; the query text is still never persisted. |
> | - | **Source exclusion rules** (added later): layered, unified model, contextual plus Settings authoring **(differs)** | New section 14 | Replaces hidden apps and notification hide rules with one model. |
> | - | Extension API: not yet; editor per-group expressions kept; RSS refresh is #1374 | Nothing (parked or out of scope) | |

## Fixed decisions (owner)

1. The workspace system is **the default**, not opt-in, and part of normal configuration (Settings).
   New installs get a preset (Nova-style by default). Existing installs are migrated without loss. If
   anything fails to decode or resolve, Riffle falls back safely to the previous/default behaviour.
   Standard launcher mode keeps working throughout; nothing may block home, drawer, dock or settings.
2. Lenses are **saved and reusable** (a named lens library), not only inline.
3. RSS and Search adapters are in scope (#1365). Other external sources are explored in #1366.
4. (2026-10-01) The classic path (the pre-workspace home, drawer and dock rendering) is **supported
   permanently** behind "Use workspaces / Classic" (Q2). It is a product path, not a transitional fallback.
5. (2026-10-01) Placed items (apps, folders, widgets, shortcuts on home pages) **move into workspaces now**
   (Q16); `HomeLayoutSet` stops being the long-term source of truth. Section 9.
6. (2026-10-01) The lens library is **per layout** (Q3), presets use saved lenses (Q4), the dock has
   **per-workspace overrides** (Q17), lenses may take **per-lens search queries** (section 13), and all
   hiding (hidden apps, notification hide rules, per-source hiding) becomes one **source exclusion** model
   (section 14).

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

## 1. Default-on rollout

### 1.1 What "default" means

On every launch of a release where the rollout is on:

1. The active layout's workspace is **resolved** (`WorkspaceSet.resolveActive(deviceClass, capabilities,
   sources)`), and the home surface draws that workspace.
2. The dock workspace menu exists (WS6) and Settings shows the Workspaces section.
3. The legacy mode pair (Home/Library) no longer drives what is shown (see section 3). Placed home items
   are owned by the workspace once the placed-items cut-over (section 9, slice S17) ships; until then they
   live in `HomeLayoutSet` (as documented in WS5).

"Default" does **not** mean workspaces are mandatory for the launcher to function. The classic path
(draw the classic home, drawer and dock as today) stays compiled in **permanently** (owner decision Q2)
and is the fallback in every failure case below. What the classic path *is* once placed items are owned
by workspaces, and how its cost is bounded, is section 10: it is a second **rendering** over the same
stored data, not a second copy of the data.

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

- The home is drawn immediately from the **Nova-style preset** (WS8, merged), with no gate, seeded by
  `PresetInstaller.withDefaultsFor(set, deviceClasses, ids)` (fills only device classes with nothing
  stored). See section 8, item 1, for why this must be the single built-in default.
- Offer a **non-blocking** "Choose your home style" step in the existing first-run flow
  (`FirstRunRepository`; it already asks for the Home role). It is a sheet with the preset cards from
  the picker in section 2, Nova pre-selected; Skip keeps Nova. It must not delay the Home role request,
  and the launcher is fully usable while it is open or skipped.
- Choosing a preset replaces the layout's workspace list with that preset (one workspace) and marks it
  active and default. Presets are ordinary workspaces afterwards.

### 1.4 Flags: retired or replaced

| Today | Becomes |
| --- | --- |
| `WorkspaceMenuFeature.enabled` (global mutable, default `false`) | Deleted in slice S20. In between, replaced by `WorkspaceRollout` (below). Tests and previews keep a test-only override. |
| `DockShelfExpansion.enabled` | Untouched by WS10 (WS6 owns it). |
| `libraryOnlyLauncherViewModeAvailability()` | Retired in slice S18: the mode pair stops being a user concept, and the stored `LauncherViewMode` remains only as data to migrate from. |
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
immediately, does not delete workspaces or placed items, and the Workspaces settings page then shows a
single row "Use workspaces" to turn it back on. It sits under Settings > Workspaces > Advanced, always
reachable (also from the Settings search). **It stays permanently** (Q2); it is not scheduled for removal.

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

### 1.7 Staged rollout, checkpoints, revert (revised 2026-10-01)

> Superseded: the earlier version of this section assumed `HomeLayoutSet` stays the placed-items source
> of truth through the flip, which made revert a one-line change. Q16 moves placed items into workspaces,
> so revert needs an explicit mechanism (section 9.7). Slice ids refer to the re-sequenced list in
> section 7.

| Stage | Ships (slices) | Default | Checkpoint before moving on |
| --- | --- | --- | --- |
| R0 | Nothing user-visible (today) | off | n/a |
| R1 **Shadow** | S1, S2 (workspace storage, bootstrap, migration written, not drawn), S6 (exclusion engine, pure), S11, S12 (placed items as workspace data, **shadow-written** from `HomeLayoutSet` on every save and compared on read; nothing drawn from it) | classic | Migration golden tests pass for every `HomeLayoutSet` fixture; **shadow compare reports zero mismatches over a week of dogfood** (counter in Settings > About diagnostics: counts only); cold start time unchanged within noise (benchmark); corrupt/timeout/newer-schema tests pass. |
| R2 **Data** | S3 (backup of workspaces, library, exclusions), S4 (per-layout lens library, preset rework), S5 (favourite/frequent lenses), S7 (exclusions wired, legacy stores mirrored) | classic | Round-trip backup tests; manual restore on a second device; hidden apps and hide rules behave identically before and after S7 (golden tests); no UI change for users. |
| R3 **Preview (internal/beta only)** | S8, S9, S10 (per-lens query contract, workspace additions, dock overrides), S13 to S16 (Settings pages, privacy), S17 (placed-items cut-over) behind a beta-only `PlacedItemsOwner` switch | classic; workspaces opt-in in Settings in beta builds | Standard-mode checklist (`standard-launcher-mode.md`) passes with workspaces ON and OFF; accessibility and reduced-motion pass (2.8); screenshot tests green at compact and unfolded; **the S17 rollback drill (9.7) executed once on a beta device**; manual home-edit regression (9.4). |
| R4 **Default** | S18, S19 (legacy reconciliation, flip). Placed items owned by workspaces (S17 on for everyone **iff** staging option C or B below, else R5) with the **legacy mirror still written** | **workspaces ON**; Classic one tap away, permanently | All items in "Flip criteria" below. |
| R5 **Settle** | S20 after one full release at R4: stop mirroring placed items back into `HomeLayoutSet` (the one-release rollback path ends); delete `WorkspaceMenuFeature`, dead mode code, unused settings UI; keep codec fields. **The classic path is not removed** (Q2). | on | Zero unexplained `Classic(error)` and zero shadow mismatches in beta/dogfood across R4. |

**Staging of the placed-items cut-over: options.** The owner wants placed items in workspaces now. There
is a genuine trade-off in *when* relative to the default flip, because two risky changes landing in one
release cannot be told apart in the field:

| Option | What ships when | For | Against |
| --- | --- | --- | --- |
| A. Flip first, cut over next release | R4: workspaces default-on while `HomeLayoutSet` still owns placed items (shadow-written). R5: cut over. | Smallest blast radius per release; the cheap revert survives the flip. | Two releases; owner's "now" slips by one release; `home.grid` adapter must exist for R4 anyway (it does in every option). |
| B. Together, no shadow | R4 does both at once, no R1 shadow period. | One release. | A migration bug loses placed items on the first launch of the default build. No evidence before shipping. **Not recommended.** |
| **C. Shadow now, then together (recommended)** | Ship S11/S12 invisibly in R1 (placed items written into workspace form on every save and compared, never drawn), run them through dogfood and beta; then in R4 flip the default **and** make workspace placed items the source of truth, still mirroring writes back into `HomeLayoutSet` for one release. | Respects "now" (the data move starts in R1, the cut-over is at the flip); the migration is proven on real layouts before it is authoritative; rollback is real (9.7). | Needs the shadow period to be honest: if compare finds mismatches, fall back to option A without redesign (the slices are the same, only the flip order changes). |

Recommendation: **C, with A as the pre-agreed fallback** if the shadow compare is not at zero mismatches
by the R3 checkpoint. Listed as new open question N1.

**Flip criteria (R3 to R4).** Every one must be true:

1. WS6 menu, WS7 editor, WS8 presets (at least Nova-style and the existing three migrated workspaces)
   are merged, and the editor and presets have the saved-lens rework of section 4 (S4).
2. `./gradlew verify deviceVerify` green, including the migration golden tests (workspaces **and placed
   items**) and the standard-mode regression (home, drawer, dock, settings reachable) with workspaces ON,
   OFF (classic), and corrupt.
3. A user on the shipped Library-only build upgrades with no change in what they see on first launch
   (the migrated shown-mode workspace equals the current screen, **including every placed icon, folder,
   widget and its position**) - verified manually and by a golden test using
   `libraryOnlyLauncherViewModeAvailability` data.
4. Backup from an R3 build restores into an R4 build and vice versa (older app ignores the new keys and
   still restores layouts, settings and hidden apps from the unchanged legacy keys, section 5).
5. The Calendar/notification permission rules are unchanged: nothing new prompts at launch.
5a. Every `SourceId` that migration output or any preset references has a registered source (a test
   over `WorkspaceMigration.migrate` fixtures and `WorkspacePresets`): in particular `home.grid` (until
   section 9 replaces it, an id only, no adapter exists today). With Q19 the migrated Favourites and
   Frequent pages map to `apps.all`/`apps.recent` lenses (section 12), so `apps.favourite` and
   `apps.frequent` are no longer referenced and are **not** flip blockers.
5b. The default a fresh install gets and the default a missing layout reads as are the same workspace
   (section 8, item 1).
6. Rollback tested (two kinds): flipping the default back to classic in a build leaves installs that did
   not touch the toggle on classic and installs that did keep their choice; **and** the S17 rollback drill
   (9.7): a build with the placed-items owner switched back restores every placed item from the mirror.
7. Shadow compare (R1) at zero mismatches across the dogfood and beta population for the agreed window
   (proposal: two weeks, at least N real layouts; N is a beta-size question, see N1).

**Revert, as revised.** Four levels, cheapest first: (a) user: Settings > Workspaces > Advanced >
Classic: draws the classic rendering over the **same** stored data, so nothing is lost and nothing needs
reverting (section 10). (b) release: change the default constant to `CLASSIC` for users with `mode`
unset. (c) data, placed items: a build that switches `PlacedItemsOwner` back to `LEGACY` re-reads
`HomeLayoutSet`, which was mirrored on every write during R4 (9.7). (d) data, workspaces: deleting the
workspace blob alone returns to a working classic launcher **only while the mirror is on**; after R5 it
does not, because the blob then holds the only copy of placed items, so from R5 the blob has a
`workspaces_prev` generation and the backup (section 5) as its safety nets, and R5 must not ship until the
mirror has had a full release without mismatches.

Q1: preview is internal and beta builds only, one release at most (owner decision).

---

## 2. Settings information architecture

### 2.1 Where it lives

Today Settings has five groups (`SettingsPageGroup`): Home & layout, Appearance, Interaction &
accessibility, Apps & content, Permissions/privacy & backup. Proposal, keeping that structure:

| Group | Entry | Notes |
| --- | --- | --- |
| Home & layout | **Workspaces** (new, first row) | Switch, presets, rename, clone, delete, reset, copy to other layout, per-workspace **Dock** and **Start page** rows, Advanced (Classic toggle, permanent). |
| Home & layout | Layout | Slimmed: grid, labels, dock-adjacent geometry, **"Returning to Home" (Restore / First page / Start page, default Restore; Q7, 3.2)**, "New apps" (placement, 9.5). No mode, no template (section 3). |
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
   Use workspaces                [ on ]        (off = Classic; permanent choice, Q2)
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
  default as `LayoutWorkspaces.remove` does. **Revised for Q16:** a workspace now *owns* its placed items
  (section 9), so the confirmation states what goes with it ("Deletes this workspace and its 24 placed
  items, 2 widgets"); Undo is exact (the workspace value is restored whole) and released widget host ids
  are only deleted after the Undo window closes (9.3).
- **Copy from other layout** shows what will be replaced ("Replaces your 3 workspaces and 5 saved lenses
  on this layout"), confirms, and offers Undo (the previous generation blob makes undo exact). Widgets are
  not copied (9.3); the dialog says how many were left out.
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
| **Home screen** (Home side of the Home/Library pair: Cards or Standard) | `HomeSurfaceModeSetting.kt`, `ModePair`, `SettingsPageContent.kt:212` ("Modes" section) | The active workspace of the layout (Workspaces page) | **Retire.** Hidden today when only Library is available; removed at S18. |
| **Home layout > view mode** | `HomeViewModeSetting`, `SettingsPageContent.kt:191` | Same | **Retire.** `LauncherViewMode` stays as migration input and in `HomeLayoutKey`, not as a user choice. |
| **Layout template** | `HomeTemplateSetting.kt`, `LauncherTemplateCatalog` | Preset picker (WS8). `LauncherTemplate` already "evolves into workspace templates". | **Retire** the row; the catalog is data WS8 consumes. |
| `viewModeAvailability` (library only) | `LauncherShellPlatformDependencies.kt:43`, `MainActivityDependencies.kt:108` | n/a | **Retire** at S18 (stored hidden-mode layouts are migrated to workspaces instead of resolving to Library). |
| Grid (columns, rows, visible dimensions) | `HomeGridSetting`, `HomeLayout.settings.grid` | The grid of each placed-items page (section 9: `LauncherPage.grid`) | **Stays** (Layout page); after S17 it edits the active workspace's placed pages, with the device-class default (`HomeLayoutSettings`) kept as the template for new pages. |
| Labels | `HomeLabelSetting`, `settings.labels` | Home grid and icon expressions | **Stays.** |
| Dock: pins, edge, size, appearance | `DockSetting`, `DockModel` | `DockModel` stays the shared per-device-class base; a workspace may carry a `DockPresentation` **override** of edge, size and visibility (section 11); pins are never per workspace | **Stays** as the base; overrides edited on the workspace page. |
| Dock: show notification cards, slot count | `DockModel.showNotificationCards`, `notificationSlotCount` | `WorkspaceDock.dynamicSection` (Notifications lens, limit = slots, IconRow), already how migration maps it | **Stays as one row**, rewritten to edit the active workspace's dynamic section: on = Notifications lens; off = `null`. Needs owner decision (3.3, Q5): per workspace or global. |
| Floating dock | `SettingsPage.FLOATING_DOCK`, `OverlayDockSettings` | None | **Stays.** |
| After leaving Library (`LibraryReturnTarget`) | `AppDrawerSettings.afterLeavingLibrary`, `LauncherShellLibraryReturn.kt` | `ReturnBehavior` setting in 3.2 (Restore / First page / Start page) | **Replace**; the stored value is ignored, the codec keeps reading and writing it for backup compatibility. |
| App drawer presentation (list/icons), icon grid columns | `AppDrawerSettings` | The Finder page's expression (`ALPHA_LIST`, `CATEGORIES`, or `ICON_GRID` after the validation widening in 8.3a) (Q6, decided) | **Moves into the Finder page expression**; the row is hidden once the Finder replaces the drawer (S18). Migration: `LIST` maps to `ALPHA_LIST`, `ICONS` to `ICON_GRID`; `iconGridColumns` stays a global setting until expressions have per-expression options. |
| Search result presentation | `SearchSettings.resultPresentation` | Search source (#1365) display | **Stays.** |
| Cards appearance (geometry, glass, colour) | `CardsSettings`, `SettingsPage.ADAPTIVE_STAGE_APPEARANCE` | Appearance of `Card`/`CardStack` expressions | **Stays**, renamed "Card appearance"; shown whenever any workspace uses a card expression, otherwise collapsed under Appearance. |
| Cards stage selector/spine, thread grouping, folded/unfolded show-all | `CardsSettings` fields | Page-set + dock dynamic section behaviour | **Stays** (dormant fields keep round-tripping); not exposed beyond what Cards appearance shows today. |
| Contextual behaviour (`ContextualSettings.enabled`) | `SettingsContextualPageContent.kt` | Independent: smart behaviour, not a layout choice | **Stays**; copy clarifies it is separate from workspaces. |
| Gestures | `GestureSettings`, `LauncherGestureMappings`, `Workspace.gestureBindings` | Global defaults, optional per-workspace overrides | **Stays**; per-workspace overrides are WS7. The dock pull **opens the workspace menu** once modes retire (Q9, decided; 11.5, `gestures.md` change in S18). |
| Hidden apps | `AppVisibilityRepository`, `SettingsPage.HIDDEN_APPS` | App exclusion rules (section 14) | **Unified** into source exclusion rules; the Hidden apps page stays as a filtered view. Storage keys kept readable and mirrored (14.6). |
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

Standard is the **Nova-style preset**, which is also the new-install default. After S18 there is no
"Standard mode" to hide: the Standard arrangement is a workspace anyone can select in Workspaces or the
dock menu, and the migrated "Standard" workspace is preserved for users who had one. The stored
`STANDARD_APP_DRAWER` layout is no longer resolved to Library on load. Recorded as the deliberate
redesign decision that #1324 asked for.

### 3.4 #1325: redundant options

Resolved by the table: Home screen, view mode, template and Modes sections all express "which
arrangement", so they collapse into the Workspaces page and the preset picker; the Layout page keeps
grid, labels and the new "Returning to Home" choice. Until S18, the rule from #1325 stands ("hide options that don't apply while only
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

### 4.5 Persistence, codec versioning, migration

- `WorkspaceSetCodec`: set schema `1 -> 2`. Adds, **inside each layout entry** (revised for Q3), a key
  `"library": {"lenses": [{"id","name","lens","origin"?}]}` and per-binding optional `"ref": "<id>"` next
  to the existing `"lens"` and `"expression"`. A
  workspace's own schema version (`CURRENT_WORKSPACE_SCHEMA_VERSION`) can stay at 1 because the
  addition is optional and ignorable.
- **Migration from inline: none needed.** A v1 blob decodes with an empty library and `ref = null`
  everywhere, which is exactly the old meaning. "Inline stays valid" is a property, not a migration
  step. Promotion is the only way a ref appears.
- **Downgrade safety.** A v1 decoder ignores `"ref"` and `"library"` and reads the snapshot, so an
  older app still draws every container correctly (it just loses the sharing). This is also why the
  snapshot is stored rather than recomputed.
- Decode order, per layout: decode that layout's library, decode its workspaces, then `rehydrate` (for each
  ref that resolves **in this layout's library** set `lens := library lens`, library wins; a ref that does
  not resolve there keeps the snapshot and is reported dangling; a ref that names a lens that only exists
  in another layout is the same case). A library
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
| WS0 contracts | `LensBinding.ref: LensId? = null`; new `LensId`, `SavedLens` (with optional `origin: LensOrigin?`, 4.9), `LensLibrary`, `LensLibraryOps`; `LayoutWorkspaces.library` default empty (per layout); `Workspace.presetId: String? = null` (for Reset to preset) | Existing `LensBinding(lens, expression)` calls compile unchanged. `data class` `copy` and equality now include `ref`/`library`: tests comparing whole sets need no change when both are default. |
| WS5 codecs/migration | Set schema 2 with a per-layout `library`; binding `ref`; workspace `"preset"` optional string. Migration (`HomeLayoutWorkspaceMapper`, `MigratedLenses`) untouched: they emit inline. `ensureMigrated(stored, layoutSet)` preserves each stored layout's library: it builds `WorkspaceSet(migrated.layouts + stored.layouts)` (verified in `WorkspaceMigration.kt`), which keeps stored `LayoutWorkspaces` values whole once `library` is a field of `LayoutWorkspaces` (a test pins this, replacing the earlier "must become `stored.copy(...)`" note). `WorkspaceSet.copyFromOtherLayout` is the one function that must change (4.3). | `copyFromOtherLayout` change plus tests. |
| WS7 editor | **Phase 1 (no dependency):** inline only, as now. **Phase 2:** adds "Use saved lens" and "Save as lens" to its lens step, and the impact list on edit. | WS7 can merge today; phase 2 is a follow-up PR after S4 below. It must route edits through `LensLibraryOps`, not mutate bindings of referenced lenses directly. |
| WS8 presets (merged, `workspace/preset/`) | **Rework (Q4), small:** see 4.9. The catalog data stays; bindings gain a stable lens key and a display name; `installPreset` and `PresetInstaller` become library-aware and set `Workspace.presetId`. `skinHintId` stays unwritten (section 8, item 7). | Preset tests change in three places (install writes the library, install twice reuses lenses, reset). |
| WS6 menu | None. It reads `binding.lens` snapshots. Optionally shows nothing about refs. | None. |
| WS4 hosts/planners | None (snapshot). | None. |
| Backup | `workspaceSet` already carries the object; each layout's library rides inside it (section 5). | None. |

Recommended merge order to minimise rework: WS6, WS7 (inline) and WS8 (merged) stay as they are -> S4
(library domain + codec + the small preset rework) -> WS7 phase 2 and S15 (library UI). S4 can also be
written before those merge because nothing it adds is required by them.

### 4.7 Limits

`MAX_SAVED_LENSES = 100` **per layout**, name 1..40 chars. These follow the bounded-settings convention used by
`MAX_NOTIFICATION_HIDE_RULES` and `MAX_CONFIGURED_FEEDS`; revisit if a real need appears.

### 4.8 Tests specific to lenses

Domain: library ops (unique names, cap, no-op rules), `rehydrate` (resolved, dangling, library
wins), property test of the snapshot invariant after random edit/rename/duplicate/delete/promote/
detach sequences, `previewEdit` (per-group pairing, dock, page-set, widget), `remove` policies,
clone shares refs within a layout, **`copyFromOtherLayout` copies referenced lenses with fresh ids and
rewrites refs (including unreferenced lenses, dangling refs, and a property test that the target never
holds an id that is also in the source's library)**, a binding cannot resolve against another layout's
library, preset install/reset (4.9), `ensureMigrated` keeps each layout's library, codec
golden files (v1 blob decodes unchanged; v2 blob; v2 blob read by a v1-style decode ignoring unknown
keys; dangling ref; corrupt library entry; hostile ids).

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
* **Placed items and presets (Q16).** An installed preset creates its placed-item pages empty (9.6): a
  preset arrangement never moves or deletes placed items of other workspaces. Reset keeps the placed items
  of pages that still exist in the preset arrangement and asks before dropping any.
* **Tests.** `workspaces-presets.md`'s validation list stays true (validates, resolves, round-trips, no
  item content, needs no permission); three tests change: install writes the expected library entries,
  install twice reuses entries (workspaces structurally equal apart from ids), reset restores the
  arrangement and, when asked, only preset-origin lenses.
* **Merged WS8 impact:** `PresetBindings` call sites (five preset files) pass a `PresetLens` for each lens
  they already build; `PresetInstaller` gains the library-aware functions beside the existing ones, which
  stay as thin wrappers for the inline form until S4 removes their last callers.

---

## 5. Backup and restore

**As built:** see the table at the top: the document and codec support `workspaces`, but neither export
nor import is wired. **Proposed:**

- **Export.** `LauncherBackupExportCoordinator` gets the workspace repository and passes
  `workspaceSet` (including each layout's library) into `launcherBackupDocument(...)`. Written as today
  under `"workspaces"`. The set is the in-memory current one, not the possibly stale stored blob.
  **Revised for Q16 and section 14:** (a) once placed items are owned by workspaces (S17), the placed pages
  ride inside `"workspaces"`, **and** `"homeLayouts"` keeps being written from the mirror (9.7) for as
  long as the rollback path exists, so an older app restoring a newer backup still gets its home screen;
  (b) source exclusion rules are written under a new optional top-level `"exclusions"` key, **and** the
  legacy `"hiddenApps"` key and the settings' notification hide rules keep being written in their old
  shape (14.6), so an older app restores hidden apps and hide rules unchanged.
- **Document version.** Do **not** bump `LAUNCHER_BACKUP_DOCUMENT_VERSION` (an older app requires `== 1`
  exactly and would reject the whole file; verified in `decodeLauncherBackupDocument`). New data is optional
  keys (`"workspaces"` contents, `"exclusions"`), versioned by their own schema. An older app restoring a
  newer backup ignores them and still restores layouts, settings and hidden apps, i.e. the classic data is
  always complete in the document. This is also what keeps the permanent classic path (section 10)
  restorable on any build.
- **Import.** Extend the restore path (`ImportLauncherBackup` -> `withImportedBackup`) with a
  workspace repository:
  1. Decode and `rehydrate` the document's set (never throws).
  2. If it decoded to at least one valid layout: **replace** the stored set (workspaces, every layout's
     library, and, after S17, their placed items) atomically with it. Exclusion rules are replaced the same
     way (from `"exclusions"`, else migrated from the legacy keys in the same document, 14.6).
  3. If `workspaceSet` is absent (a pre-workspace backup) or unusable: **do not keep the existing
     workspaces** (they describe the previous install's layouts, not the imported ones); rebuild with
     `WorkspaceMigration.migrate(document.homeLayoutSet)` (which, after section 9, also builds the placed
     pages from the imported layouts) and empty libraries. Outcome noted in the import summary.
  4. Keep the previous generation blob so "Undo restore" is exact (Q11: restore is replace **with Undo**;
     the Undo snackbar stays for the whole session and the previous generation survives until the next
     changing write). Hosted widget ids are deleted only after the Undo window closes (9.3).
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
- **Tokenised URLs are excluded (decided).** Feed and ICS URLs that embed a token are bearer secrets: the
  backup carries the feed *configuration without such URLs* and the Sources page shows "Re-enter the
  address" for them after restore. Concretely, any configured external-source URL that contains userinfo,
  a query string or a long opaque path segment is treated as tokenised and omitted (the rule is a pure
  function with a test over hostile samples; erring on the side of omission). Today's RSS feed backup
  behaviour is unchanged for plain URLs. Placed items contain no URLs.
- **Widgets in a backup (as built, flagged).** `HomeWidgetJsonCodec` encodes `WidgetItem.appWidgetId`, a
  host-local integer that is meaningless on another device, and `WidgetItem` carries no provider
  component, so there is nothing to rebind from. I found no rebind step in the import path (searched
  `LauncherBackupImportCoordinator`/`LauncherBackupImportValidator`); verify on a device. Section 9.3
  specifies the placed-items behaviour (restored widgets are shown as "re-add" placeholders), which is no
  worse than today and must be tested.
- **Single-workspace export/import: deferred (Q10, decided).** Not designed further here. The only
  constraint kept: workspaces still encode standalone and carry snapshots, so the feature can be added
  later without a model change. Section 9 makes this slightly harder (a workspace now owns placed items
  and widgets that cannot be shared), which is a further reason to defer.

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

## 7. Implementation slices (re-sequenced 2026-10-01)

> Superseded: the earlier S1 to S12 list (one "container cut-over is not part of WS10" note, a global
> library, an optional dock slice, a locked-device rule, no exclusions or per-lens queries). The new list
> has 20 slices because the owner pulled placed items, dock overrides, per-lens queries and exclusions
> into scope. Each slice is small, independently shippable, and states what gates it. "Gate" = what keeps
> it invisible or safe until its turn. "Pure" means domain-only, no app wiring, JVM tests only.

| ID | Slice | Depends on | Gate / ships as | Rollout |
| --- | --- | --- | --- | --- |
| S1 | **Bootstrap (domain).** `WorkspaceBootstrap`, `DecodeReport`, `BootstrapOutcome`, newer-schema read-only rule, fall-through rules; default unification (8.1: `defaultFor` delegates to the Nova preset, migration marks a single `AllApps` page `FINDER`). Pure. Unit + golden tests. | WS5 | Not called yet. | R1 |
| S2 | **Storage and startup (app).** `WorkspaceRepository` over the existing `DataStoreWorkspaceStore` with write-behind and one previous generation; bounded startup read; shadow `ensureMigrated` writes; `WorkspaceRollout`; outcome counters. Nothing drawn. | S1 | Rollout = classic. | R1 |
| S3 | **Backup wiring.** Export passes `workspaceSet` (with libraries, and placed pages after S17); optional `"exclusions"` key; legacy keys still written; import applies/rebuilds with Undo; tokenised-URL omission; import summary; golden backup fixtures. | S2 (S4, S7 extend it) | Independent of UI. Fixes the existing gap (section 5). | R2 |
| S4 | **Per-layout lens library (domain) and preset rework.** `LensId`, `SavedLens` (+ `origin`), `LensLibrary`, `LensBinding.ref`, `LayoutWorkspaces.library`, `LensLibraryOps`, set schema 2, `copyFromOtherLayout` copies lenses, `Workspace.presetId`, `PresetLens`/library-aware `PresetInstaller` (4.9). All additive. | WS0, WS5 | Pure; no UI. Parallel with S1-S3. | R2 |
| S5 | **Favourite and frequent lenses.** Migration maps `FAVOURITES`/`FREQUENTLY_USED` pages to lenses over existing app sources (section 12); apps adapters emit the `launcher.pinned` flag; golden tests. No new adapters. | S1 | Removes the old flip blocker. | R2 |
| S6 | **Source exclusion engine (domain).** `SourceExclusionRule`, matchers, `SourceExclusions.apply`, legacy-to-unified migration (hidden apps, hide rules), codec, golden tests (section 14). Pure. | WS0 | Not called yet. | R1 |
| S7 | **Exclusions wired (app).** `SourceExclusionRepository`, first step in `SourceBackedLensResultProvider`, legacy consumers (drawer/search/badges/dock cards) moved onto the engine, **write-through mirror** to `AppVisibilityRepository` and `NotificationHidingSettings`. Behaviour identical (golden). | S6, S2 | No UI change. | R2 |
| S8 | **Per-lens query contract (WS0, additive).** `ParameterizedItemSource`, `LensSourceParam`/binding, `SearchQueryHolder` as default value, query-bearing container expression (section 13). A thin first implementation: one search box container feeding named query slots. | #1365 (merged), WS0 | Additive; no stored query. Independent of S1-S7. | R3 |
| S9 | **Workspace additions.** `Workspace.startPageId`, Finder out of the pager, `FINDER_EXPRESSIONS` widened to `ICON_GRID` (8.3a), `skinOverrideId` = theme preset, `ReturnBehavior` + `WorkspaceReturnReducer`, drawer-presentation migration (Q6). Additive codec keys. | S1, S4 pattern | Return reducer pure first; UI in S13. | R3 |
| S10 | **Per-workspace dock overrides.** `WorkspaceDock.presentation` (`DockPresentation`), `DockModel.applying(...)`, codec, resolver in `HomeDockHost`, validation (`DockHiddenWithoutMenuEntry`), preset updates (Niagara, unfolded TimeScape), migration (none needed), tests (section 11). | S1, S9 (menu trigger) | Overrides default to null = follow. | R3 |
| S11 | **Placed items A: domain.** `PlacedItemsPage` page host, `PlacedPage` value over `LauncherPage`, engine adapters (`PlacedItemsEditor` reusing `GridPlacementEngine`, `FolderEngine`, `WidgetEngine`, `HomePageEngine`), `HomeLayoutWorkspaceMapper` emits placed pages with items, idempotent migration, golden tests (section 9). Pure. | S1 | Not drawn. | R1 |
| S12 | **Placed items B: shadow.** Every `HomeLayoutSet` save also writes the workspace form (write-behind), a compare job reports mismatches (counts only); nothing is drawn from workspaces. | S11, S2 | Diagnostics only. | R1 |
| S13 | **Workspaces settings page.** Planner + page + Advanced toggle, fallback message, copy-to-layout, undo, per-workspace Dock and Start page rows, "Returning to Home" row, "New apps" row. | S2, S9, S10, WS6, WS8 | Behind rollout = classic until R3. | R3 |
| S14 | **Sources page and exclusions UI.** `SourcesPlanner`, status rows, permission actions via existing flows, enable/disable (+ `LensAvailability.OFF`), Notifications and RSS detail (RSS page moved, route aliased), exclusion rule list/edit/disable/delete with match counts, **contextual "Hide ..." item action** with Undo (section 14.7), Hidden apps as a filtered view. | S2, S7, WS1; #1365 | Same gate. | R3 |
| S15 | **Saved lenses page + editor integration.** Per-layout library list/detail, used-by, delete policies, "Copy to layout", impact list on edit (WS7 phase 2). | S4, WS7 phase 2 | Same gate. | R3 |
| S16 | **Privacy controls.** Per-source content level, optional screenshot/recents flag (default off), Privacy page. **No locked-device rule (Q8).** | S14 | Defaults equal today. | R3 |
| S17 | **Placed items C: cut-over.** Workspace placed pages become the source of truth; home edit mode, drag, folders and widget add/remove operate on them via the adapter; `HomeLayoutSet` becomes a **derived mirror** written on every change (9.7); `PlacedItemsOwner` switch (`WORKSPACE` / `LEGACY`) for the rollback drill; "pages appear as you fill them" and "New apps" placement setting (9.5). | S11, S12 (zero mismatches), S9, S2 | Beta-only switch in R3, on for all in R4 (option C). | R3 beta, R4 |
| S18 | **Legacy reconciliation.** Remove Home screen/view mode/template/Modes UI, drawer presentation row, `afterLeavingLibrary` row; dock pull opens the workspace menu (`gestures.md`, `dock.md` updates, a11y action, Ctrl+arrow); retire `libraryOnly...Availability`; hide template row early as a quick win. Closes #1323, #1324, #1325. | S13, S9, S10, WS8 | Flips with S19. | R4 |
| S19 | **Flip the default.** New-install preset (Nova with Finder), non-blocking first-run chooser, `WorkspaceRolloutMode.ON` default, release notes, Play declaration check (7.2). | S1-S18, WS6, WS7, WS8; flip criteria 1.7 | **R4.** | R4 |
| S20 | **Settle.** After one release at R4 with zero shadow/mirror mismatches: stop mirroring placed items to `HomeLayoutSet` and exclusions to the legacy stores (keep the codecs and the **backup legacy keys**), delete `WorkspaceMenuFeature`, dead mode code, unused settings UI. **The classic path is kept** (section 10). | S19 + one release | R5. | R5 |

Dependency summary (arrows are "must land before"):
`S1 -> S2 -> S3`; `S1 -> S5, S9, S11`; `S11 -> S12 -> S17`; `S6 -> S7 -> S14`; `S4 -> S15`;
`S9, S10 -> S13`; `S9 -> S10`; `S13, S14, S15, S16 -> S18 -> S19 -> S20`; `S17 -> S19` (option C);
`S8` has no incoming edge beyond merged work and can land any time before R3 closes.

Parallelism: three independent chains can start now: (S1 -> S2 -> S3, then S5), (S4), and (S6 -> S7).
S11/S12 start after S1 and are the **critical path** because S17 needs a clean shadow period before R4.
S8, S9 and S10 are independent of one another except S10 needing S9's menu entry for the hidden dock rule.

### 7.1 Test and validation plan (revised)

**Domain (JVM, no device).** Bootstrap decision table (every row of 1.5 including timeout, corrupt,
partial, newer schema, capability fallback); `ensureMigrated` idempotence and library preservation;
library ops and the snapshot invariant (4.8); `WorkspaceReturnReducer` (the 3.2 table, a regression test
per #1323 scenario, and "never changes workspace id"); planners for Workspaces/Sources/Lenses pages (rows,
disabled reasons, last workspace cannot be deleted, status mapping incl. `OFF`); backup import decision
(valid set, absent set, corrupt set, old-app backup) and "never item content" (assert the encoded document
has no item fields); privacy ceiling (`project` intersection never widens; `SENSITIVE` always wins);
exclusions (14.9); per-lens parameter binding (13.7); dock override resolution (11.7); favourite/frequent
lens evaluation against fake sources (12).

**Migration golden tests (extended).** Fixture `HomeLayoutSet`s: new install, Library only (current
shipped), Standard, Cards, all three modes, multiple device classes, a stored hidden-mode layout,
preferred-mode map only, mode ring legacy data. Golden expected `WorkspaceSet` JSON checked in; golden
blobs for schema v1 (no library) decoding unchanged under v2 code, and a v2 blob decoded by v1-style rules.
**New:**
* *Placed-items goldens* (S11): for each fixture, an expected placed-pages JSON including every item type
  (app, shortcut with `AppShortcutId`, folder with children, widget with `HostedWidgetId` and
  `WidgetResizeConstraints`), positions and spans, page order, `selectedPageId`, pinned pages,
  generated pages, a page with `generatedContentOverflowCount`, multi-page, tablet grid, dock panel page
  (`DockModel.panel`), duplicate page ids across modes (the Q12 collision case). Migration run twice gives
  byte-identical output (idempotent); `HomeLayoutSet -> workspace form -> HomeLayoutSet` round trip is
  **exactly equal** for every fixture (the lossless property; this is also the mirror's correctness).
* *Real-fixture sweep*: a test over every `HomeLayoutSet` JSON fixture in `app/src/test` and
  `app/src/androidTest` asserting the round trip and answering Q12.
* *Per-layout library goldens* (S4): copy-from-layout with referenced, unreferenced and dangling lenses;
  preset install into a layout with a clashing user lens name; install twice; reset with and without lens
  reset; a layout-A blob whose binding refs a lens that only layout B has (dangling with snapshot).
* *Dock override goldens* (S10): Niagara ("no dock") and unfolded TimeScape (left edge) resolve to the
  expected effective `DockModel`; unknown override keys are ignored by a v1 decoder; override over a
  device-class dock with `isEnabled=false` stays hidden.
* *Exclusion goldens* (S6): a settings blob with 200 hide rules of every kind and a 60-app hidden set
  migrate to the expected rule list; the unified evaluator yields item-for-item the same notifications and
  apps as `NotificationHideRuleFilter` and `withHiddenApps` on a shared fixture corpus (equivalence test);
  mirror write-through reproduces the original legacy encodings byte-for-byte.

**Screenshot tests (WS9 harness).** Workspaces page, preset picker, Sources page (each status), exclusions
list, Saved lenses page (with dependents, with a would-break list), fallback banner, privacy page, dock
override rows ("Overridden by workspace"); compact and unfolded; light/dark; 200% font scale; reduced motion
on. Fakes only.

**Instrumented (`deviceVerify`).** Standard-mode regression with workspaces ON, **classic**, and corrupt
blob: home, drawer, dock, settings reachable; startup with blob absent/corrupt/huge; backup export/import
round trip including placed items and exclusions; posture change keeps the selected page; no permission
prompt at launch (calendar and notification access); **placed-items edit regression** (add/move/resize
widget, create folder, drag between pages, delete page) against the workspace-owned store, then restart and
compare; **classic-mode parity** smoke (see 10.3); mirror check: after each edit sequence the derived
`HomeLayoutSet` equals the legacy-engine result.

**Manual device checklist (documented in each UI PR).**
1. Upgrade from the shipped Library-only build: first launch looks identical, **every icon, folder and
   widget in the same cell**; Settings shows Workspaces; Standard is selectable.
2. Fresh install: Nova-style home with Finder, first-run chooser skippable, Home role flow unaffected.
3. Fold/unfold mid-use: no flicker to a different workspace, fallback message appears only when real; a
   workspace with a left-edge dock override shows it only on the layout that has it.
4. Corrupt the blob (debug action): launcher still starts, notice shown, Restore previous works.
5. Turn Classic on and off repeatedly with edits in each: no data loss; Home/drawer/dock fine in both;
   items placed in Classic appear in the workspace and vice versa (10.2).
6. Sources: each needs-permission source shows rationale before any system dialog; deny, deny
   permanently, revoke in system settings, return: states correct, nothing prompts by itself.
7. Saved lens: create, use in two containers, edit (clean), edit (breaks one container), delete used;
   copy a workspace to the other layout and confirm its lenses came along and are independent.
8. Backup on device A, restore on device B and on an older build (hidden apps and hide rules still restore).
9. TalkBack: traverse the new pages, perform every action without drag (including "Hide this app" from the
   item actions); font 200%; reduced motion; switch access.
10. Quiet-profile redaction with sensitive notifications and a private calendar event. (No locked-device
    case: removed, Q8.)
11. Hide the dock on a workspace and confirm the workspace menu is reachable by gesture, TalkBack action and
    Ctrl+arrow (11.4).
12. **Rollback drill (S17):** edit placed items with the workspace owner on, switch the build to the legacy
    owner, confirm every placed item is present, repeat in the other direction.

### 7.2 PR checklist additions

Every PR that adds a permission or a new data class (a new source kind, a new stored setting that holds user
text, a new network use) must carry this line in its description, **owned by the owner**:

`- [ ] Play Console data-safety and privacy declarations reviewed/updated by the owner for this change (or N/A: no new permission or data class).`

Applies in particular to S8 (query handling: text stays in memory, so likely N/A, state why), S14
(exclusion rules hold user text), the ICS and JSON Feed sources (network use), and S16 (secure flag).

---

## 8. Findings from the presets (WS8) that bear on default-on

The merged presets doc (`workspaces-presets.md`, "Gaps") lists limits of the current model. Each is
addressed here as in scope for WS10, deferred, or owner-decided. Nothing below changes behaviour in the
flip except items 1, 3 and 8.

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
   Slice: folded into S1 (small change plus tests); no stored data changes.
2. **Dock edge, size, pins and "no dock" are `DockModel`, not `Workspace`.** A workspace owns only the
   dynamic section, so Niagara ("no dock") and unfolded TimeScape ("dock on the left edge") are not
   expressible. Proposal: **out of scope for WS10.** Pins must stay shared across workspaces
   (`dock.md`: one dock per device class) and standard-launcher parity does not need a per-workspace
   dock. If wanted later, add an *override only* type, never a second pin list:
   ```kotlin
   data class WorkspaceDock(
       val dynamicSection: LensBinding? = null,
       val presentation: DockPresentation? = null,     // NEW, null = follow the device class dock
   )
   data class DockPresentation(val visible: Boolean = true, val edge: DockPosition? = null)
   ```
   (codec: optional `"presentation"` key, additive like `ref`.) Optional slice S12.
3. **No start page; the Finder is a pager page.** A workspace cannot say which page it opens on, and a
   `FINDER` page sits in the pager (the WS6 menu already excludes it from Jump to page). Proposal, both
   additive:
   - `Workspace.startPageId: ContainerId? = null` (null = first non-Finder page; an id that does not
     exist is ignored, never an error; codec key `"start"`). It may name the Finder, which is how
     Kvaesitso opens on search.
   - **The shell excludes the Finder page from the pager** and opens it only through the Finder entry
     or the gesture bound to it, unless it is the start page. This matches standard-launcher parity
     (the drawer is not a home page) and keeps page indicators honest; it is a UI rule with no model
     change. The start page feeds the return rule in 3.2.
   Owner decision (Q14).
4. **No master/detail link between containers** (unfolded TimeScape: the Index picks which group the
   CardStack shows). Deferred; not a configuration concern. When designed, keep lenses static and
   saved-lens friendly: the detail container's filter is composed at evaluation time as
   `AllOf(lens.filter, GroupKeyIs(selected))` from a runtime selection, never by parameterising a
   stored or saved lens or writing a selection into it. Recorded so the library never grows lens
   parameters.
5. **Search and query.** `LensFilter` has no text predicate and `SourceIds.SEARCH` has no adapter yet.
   PR #1370 (RSS/Search, domain slice) proposes a transient, never-persisted `SearchQueryHolder` that
   the search box writes and the search source observes, with no change to `Lens`. WS10 aligns:
   - A saved lens over `search` stores **no query**, ever; the query is runtime state (consistent with
     "lenses hold definitions only" and with queries never being logged).
   - The Sources page shows Search as a source with provider settings only; the query is typed in the
     existing search UI.
   - If #1370's open question (per-lens queries) is answered yes later, the query lives in the binding
     at runtime, not in the library.
6. **`apps.favourite` / `apps.frequent` have no adapter.** They are reserved ids that migrated
   `Generated(FAVOURITES/FREQUENTLY_USED)` pages already reference (`WorkspaceSourceIds`). Until an
   adapter is registered those pages are `UNAVAILABLE`. WS10 treats this as a **flip blocker**
   (criterion 5a) with two acceptable outcomes: register thin adapters over the existing favourites
   and usage data (preferred, small), or have migration map such pages to a lens over `apps.all` with
   the closest filter, recorded in the golden test. The Sources page lists only registered sources, so
   it never shows one that cannot work.
7. **Skin model.** `skinOverrideId` exists (null follows global) and presets carry placeholder
   `skinHintId`s, but **no skin ids exist in the repo**. The only appearance catalog is
   `LauncherThemePreset` (`AppearanceSettings.themePreset`, default `MATERIAL`). Proposal: a skin is a
   theme preset; `skinOverrideId` stores the `LauncherThemePreset` name (a stored contract, never
   renamed; unknown ids read as "follow global"). Settings: per workspace an Appearance row
   "Follow global / <preset>". Preset `skinHintId`s are mapped to a `LauncherThemePreset` or dropped
   (hints only; installing a preset still never changes the theme). Owner decision (Q15) plus a small
   WS8 follow-up replacing the placeholders.
8. **Long-term ownership of placed items.** Presets cannot create `HomeLayout` pages and reference only
   page `home` through `home.grid` + `GroupKeyIs("home")`; migrated pages use their own page ids.
   `home.grid` has **no adapter yet**, and `HomeLayoutSet` still keeps one `HomeLayout` per mode per
   device class. Proposed for the default-on world:
   - **`HomeLayout` remains the owner of placed items** (apps, folders, widgets, shortcuts, pins,
     selected page) through the flip and until a separate placed-items container project; workspaces
     reference them, never copy them. This is also why revert is cheap (1.7) and why backup already
     contains them.
   - After the flip each device class has **one canonical `HomeLayout` for placed items**: the layout
     of the mode it showed at migration (that workspace is already active and default). Migrated
     workspaces that came from other modes keep reading their own `HomeLayout`s; `home.grid` takes its
     layout from a layout key carried with the page binding (`GroupKeyIs(pageId)` stays), so there is
     no collision even if page ids repeat across layouts. Q12 asks the narrower question whether ids
     are unique; the key makes the answer irrelevant.
   - **Creating a page** in the editor (WS7) creates the `HomeLayout` page in the workspace's
     canonical layout and then the container referencing it. **Deleting a workspace never deletes
     `HomeLayout` pages or placed items** (it removes only the arrangement); an unreferenced page stays
     in `HomeLayout` and can be re-added.
   - New-install presets: `HomeLayoutDefaults.standard` always has page `home`, so a Nova, iOS or
     Niagara install shows the user's placed items from the first frame.
   Owner confirmation needed (Q16) because it fixes the long-term ownership boundary.

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
| Q14 | **Start page and Finder in the pager (8.3):** add `Workspace.startPageId` and hide the Finder page from the pager, opening it only from the Finder entry or gesture unless it is the start page? | Yes to both: the drawer is not a home page in standard launchers; additive and codec-safe. |
| Q15 | **Skin model (8.7):** skin = existing `LauncherThemePreset`; per-workspace override, null follows global; placeholder `skinHintId`s mapped or dropped. | Adopt. No separate skin catalog until a second skin concept exists. |
| Q16 | **Placed-items ownership (8.8):** `HomeLayout` stays the owner through the flip; one canonical layout per device class; `home.grid` carries a layout key; deleting a workspace never deletes placed items. | Adopt; revisit only with a dedicated placed-items container project. |
| Q17 | **Workspace-level dock (8.2):** in scope? | No for WS10; optional override-only `DockPresentation` later. Niagara and unfolded TimeScape dock fidelity is a preset limitation, not a parity issue. |
| Q18 | **Default unification (8.1):** one built-in default (Nova with Finder); migration marks a single `AllApps` page `FINDER`. | Adopt; golden tests in S1. |
| Q19 | **`apps.favourite`/`apps.frequent` (8.6):** register adapters or map migrated pages to `apps.all` lenses? | Adapters (small), as a flip blocker; the mapping only if adapters slip. |

## Known limitations of this design

- Calendar selection, per-source scheduling and non-RSS/Search external sources are out of scope here
  (#1366 covers other sources).
- Recents' and Media's exact permission actions are declared by their adapters (`SourceAccess`) and
  were not enumerated here; the Sources page must read them from the adapters, and S6 must verify each
  against the existing explicit flows.
- The container cut-over (placed items leaving `HomeLayoutSet`) is not part of WS10; until then
  `HomeLayoutSet` stays the placement source of truth, which is what keeps revert cheap.
