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
   Slice: folded into S1 (small change plus tests); no stored data changes.
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
     drawer's "Icons" choice, so add `ICON_GRID` to the set (a validation change with a golden test; no
     stored data changes, existing Finders stay valid). `iconGridColumns` stays a global setting.
4. **No master/detail link between containers** (unfolded TimeScape: the Index picks which group the
   CardStack shows). Deferred; not a configuration concern. When designed, keep lenses static and
   saved-lens friendly: the detail container's filter is composed at evaluation time as
   `AllOf(lens.filter, GroupKeyIs(selected))` from a runtime selection, never by parameterising a
   stored or saved lens or writing a selection into it. Recorded so the library never grows lens
   parameters.
5. **Search and query.** *(Superseded by the per-lens query decision.)* The original text kept the query
   outside lenses entirely. The owner wants per-lens queries now: section 13 specifies the additive WS0
   hook. What is **kept**: the query text is never persisted, logged or backed up, and a saved lens stores
   no query text, only a parameter *binding*.
6. **`apps.favourite` / `apps.frequent` have no adapter.** *(Superseded by Q19.)* The owner decided on
   lenses over the existing app sources with no new adapters, so there is **no flip blocker**: the ids stay
   reserved but unused. Definitions and the migration mapping are section 12.
7. **Skin model.** `skinOverrideId` exists (null follows global) and presets carry placeholder
   `skinHintId`s, but **no skin ids exist in the repo**. The only appearance catalog is
   `LauncherThemePreset` (`AppearanceSettings.themePreset`, default `MATERIAL`). Proposal: a skin is a
   theme preset; `skinOverrideId` stores the `LauncherThemePreset` name (a stored contract, never
   renamed; unknown ids read as "follow global"). Settings: per workspace an Appearance row
   "Follow global / <preset>". Preset `skinHintId`s are mapped to a `LauncherThemePreset` or dropped
   (hints only; installing a preset still never changes the theme). Owner decision (Q15) plus a small
   WS8 follow-up replacing the placeholders.
8. **Long-term ownership of placed items.** *(Superseded by Q16.)* The original proposal kept `HomeLayout`
   as owner "through the flip and until a separate placed-items container project", which is what made
   revert cheap. The owner decided placed items move into workspaces now. The design, migration, staging
   and rollback are section 9; the escape-hatch consequence is section 10.

---

## 9. Placed items move into workspaces (Q16)

This is the largest change in this revision. It is written as a design addendum with the same split as the
rest of the document.

### 9.1 As built today (verified)

* `HomeLayoutSet` holds one `HomeLayout` per `HomeLayoutKey(viewMode, deviceClass)`, plus one shared
  `DockModel` per device class, `preferredModesByDeviceClass`, `modePairsByDeviceClass` and
  `libraryDockEdgesByDeviceClass` (`core/domain/.../home/HomeLayoutSet.kt`).
* A `HomeLayout` is `(viewMode, pages: List<LauncherPage>, selectedPageId, dock, templateId, settings,
  editMode)`. A `LauncherPage` is `(id: LauncherPageId, type: LauncherPageType, grid: GridDimensions,
  items: List<LauncherItem>, generatedContentOverflowCount, isPinned)`. Items are
  `AppShortcutItem(appIdentity, label, appShortcutId?)`, `FolderItem(items: List<AppShortcutItem>)` and
  `WidgetItem(appWidgetId: HostedWidgetId, resizeConstraints)`, each with an optional `GridPlacement`
  (`LauncherItem.kt`). All plain Kotlin; none depends on Compose or Android types.
* Behaviour lives in pure engines operating on those values: `GridPlacementEngine` (place, move with and
  without anchor shifting, resize, remove; bounds and collisions), `FolderEngine` (create, rename, add and
  remove shortcuts), `WidgetEngine` (add to page or dock panel, resize against `WidgetResizeConstraints`),
  `HomePageEngine` (add, select, delete, duplicate, move, retype, resize grid, pin, edit mode),
  `GridReflowEngine`, `HomeLayoutAppMembership`. Edit mode is `HomeEditMode` (`Browsing`, `EditingPage`,
  `ManagingPages`) on the layout itself. Drag is app-layer (`HomeGridItems.kt`, `HomeDragPlaceholderState`,
  `HomeGridDragPreview`) and ends in an engine call.
* Persistence: `HomeLayoutSetJsonCodec` (`app/.../launcher/`), `HomeWidgetJsonCodec`, `HomeShortcutJsonCodec`;
  `WriteBehindHomeLayoutRepository` (in-memory authoritative, debounced writes, flushed in `onStop`);
  startup read `loadHomeLayoutSetAtStartup`. Backup writes `"homeLayouts"`.
* Widgets: `WidgetItem.appWidgetId` is an `AppWidgetHost` id allocated through
  `WidgetHostGateway.allocateHostedWidgetId`, bound with `bindHostedWidget`, and released with
  `deleteHostedWidgetId`, which `LauncherShellViewModel` calls when a widget, a page with widgets, or a dock
  widget is removed.
* Workspaces reference placed items only through the `home.grid` source id plus `GroupKeyIs(pageId)`
  (`HomeLayoutWorkspaceMapper.homeGridLens`); **no `home.grid` adapter exists**, and presets reference only
  page `home`.
* No auto-placement of new apps and no "pages appear as you fill them" behaviour exists in `core/domain`.

### 9.2 Proposed model

Principle: **reuse the existing engines and value types; change who owns the value and where it is stored.**
Do not reinvent grid placement, collisions, folders or widgets.

```kotlin
// workspace package: a new page host that OWNS a placed page (reusing LauncherPage as the value)
data class PlacedItemsPage(
    override val id: ContainerId,
    val page: LauncherPage,                  // items, grid, type, isPinned, generatedContentOverflowCount: unchanged
    val role: PageRole = PageRole.STANDARD,  // never FINDER
    val expression: ExpressionKind = ExpressionKind.ICON_GRID,   // IconGrid (home) or List (Niagara "Pinned")
) : PageHost {
    override val ownedAxes: Set<GestureAxis> get() = emptySet()  // the grid pager owns horizontal paging; items own long-press/drag
}

// Replaces `PageContainer(content = Bound(home.grid lens))` for home pages. One per home page.
```

Decisions inside the model:

1. **A placed-items container per page, owned by its workspace.** The `LauncherPage` value moves *whole*
   into the container, so every existing engine keeps working on a `LauncherPage`. Each workspace has its
   own placed pages: **per-workspace arrangements** are "different workspaces have different home pages and
   contents", exactly what a migrated Standard/Library/Cards trio already is today (one `HomeLayout` each).
   Ownership by workspace (not a shared pool referenced by many workspaces) is chosen because it is what the
   data already is (no migration merges anything), widgets are single-instance, and a shared pool would need
   its own reference-counting, lifetime and conflict rules for no stored-data benefit. Alternative
   considered, **shared pool with per-workspace arrangements** (placed items live in a device-class pool and
   workspaces hold only positions): rejected for now because it changes the item identity model and makes
   "delete workspace" and "duplicate workspace" reference-count problems; see N2.
2. **The engines are reused through a thin adapter, not forked.** An adapter presents the *active
   workspace's placed pages* to the engines as a `HomeLayout`:
   ```kotlin
   /** Pure. Lens between a workspace's placed pages and the HomeLayout the engines edit. */
   object PlacedItemsAdapter {
       fun toHomeLayout(ws: Workspace, settings: HomeLayoutSettings, dock: DockModel,
                        selectedPageId: LauncherPageId, edit: HomeEditMode): HomeLayout
       fun fromHomeLayout(ws: Workspace, edited: HomeLayout): Workspace   // writes pages back, keeps non-placed containers
   }
   ```
   `GridPlacementEngine`, `FolderEngine`, `WidgetEngine` and `HomePageEngine` are called exactly as today on the
   adapted layout; the result is written back with `fromHomeLayout`. This means the whole existing engine test
   suite keeps its meaning, and the classic UI (which consumes a `HomeLayout`) keeps working unchanged (section
   10). Non-placed containers of the workspace (a Finder, a notifications page-set, widget grids) are left
   untouched by the adapter and are not editable through the home-edit engines (they have WS7).
3. **Edit mode and drag.** Edit mode stays `HomeEditMode`, held in UI state as today (it is a transient mode
   of the *shell*, not stored workspace data; `HomeLayout.editMode` remains the field the engines read, filled
   by the adapter from shell state). Dragging an item calls `GridPlacementEngine.moveItem(...)` on the adapted
   layout; cross-page drag across placed pages works as today. Dragging onto a non-placed page (Finder or
   page-set) is not a drop target. Entering edit mode on a workspace with no placed page offers "Add a home
   page" (creates a `PlacedItemsPage`). WS7 editing of non-placed containers remains the editor; the two
   editors do not overlap (placed pages: home edit mode; everything else: WS7 editor).
4. **Dock panel and pins.** `DockModel` (pins, `panel`, appearance) stays per device class (11.1); the dock
   panel page remains a `LauncherPage` inside `DockModel` and keeps using `WidgetEngine.addWidgetToDockPanel`.
   Placed items on home pages and dock pins stay consistent through the existing `HomeLayoutAppMembership`
   helpers.
5. **Selected page.** Today `HomeLayout.selectedPageId`; after the move, selection is shell state keyed by
   container id (already how page-sets work, `PageSetSelection`), persisted as `LayoutWorkspaces`-level
   "last page per workspace" (`Map<WorkspaceId, ContainerId>`, additive codec key `"lastPage"`) so Restore
   (3.2) works across process death.
6. **`home.grid` retires as a *model* concept.** It is replaced by `PlacedItemsPage`. The source id stays
   reserved (never renamed) and a decoder still understands pages that reference it: such a page, found
   in stored data, is **upgraded on decode** by looking up `GroupKeyIs(pageId)` in the layout's mirror
   (while the mirror exists) and otherwise drawn as an empty placed page with a notice. This also **moots
   Q12**: ownership is inside the container, so page-id collisions across modes cannot occur; the
   real-fixture sweep (7.1) still runs as a safety net for the migration.

### 9.3 Ownership of widgets and folders

* **Folders** are `FolderItem` values inside the page (no external id): they move with the page, are
  duplicated with fresh `LauncherItemId`s by the existing `HomePageEngine.duplicate` path, and cost nothing
  extra.
* **Widgets** are the sharp edge. A `HostedWidgetId` is a live `AppWidgetHost` allocation owned by *this
  app installation*. Rules (all follow behaviour that already exists):
  - The workspace that holds the `WidgetItem` owns the host id. Exactly one `WidgetItem` may reference an id
    across the whole `WorkspaceSet` (a uniqueness check in `WorkspaceValidation`; a duplicate is reported and
    the later one is dropped from drawing, never both bound).
  - **Never cloned.** Duplicate workspace, copy-from-other-layout and preset install **do not copy widgets**
    (precedent: `HomePageEngine.duplicatePage` already rejects pages with widgets). The user is told how many
    widgets were left out ("2 widgets weren't copied; add them again from the widget picker").
  - **Delete workspace/page** with widgets: the host ids are *queued* for deletion, and
    `deleteHostedWidgetId` runs only when the Undo window closes or the next changing write commits
    (today it is immediate on removal; the workspace-level Undo makes deferral necessary). A crash before the
    queue drains leaks an id at worst (host ids are reclaimed by `AppWidgetHost.deleteHost` on reinstall and
    `deleteAppWidgetId` sweeps; add a startup reconciliation that deletes host ids not referenced by any
    stored workspace or mirror, bounded and idempotent).
  - **Backup/restore:** see 5 (widgets in a backup, flagged as an existing gap): restored `WidgetItem`s whose
    id is not bound on this device are shown as "re-add" placeholders in their cell (retaining position and
    span) rather than dropped, so a restore never silently loses layout. This is no worse than today and gets
    its own test.
  - **Switching workspaces keeps widgets bound** (the host keeps all allocated ids; only visibility changes),
    so there is no rebinding cost on a switch. Memory cost of N workspaces with widgets is the cost of N
    bound widgets, which the host already pays for multi-page home screens; the shell should not inflate
    views for off-screen workspaces (it composes only the active one).

### 9.4 What the move changes in behaviour, honestly

* Deleting a workspace now deletes its placed items (previously "never"): confirmation + Undo (2.2).
* "Duplicate workspace" and "Copy from other layout" copy apps, folders and shortcuts but not widgets.
* Two workspaces on one layout are independent home screens; placing an app in both is allowed (as today
  across modes).
* `HomeLayoutSettings` (grid dimensions, labels) is per layout today; it stays a per-device-class default
  used when creating pages, and each placed page carries its own `GridDimensions` (already true).

### 9.5 iOS-style behaviours and the Finder (Q14, and your questions about "pages appear as you fill them")

* **Pages appear as you fill them.** New behaviour in the home-edit flow, expressed as a pure rule in a
  `PlacedItemsAutoPaging` helper over the adapted layout: when an item is dropped or placed and *every*
  placed page of the workspace is full (no free cell for the item's span, by `GridPlacementEngine`), a new
  empty `PlacedItemsPage` is appended and the item goes there; an empty trailing page is removed when
  leaving edit mode if it has no items and there is at least one other page (never the last page). It is a
  **per-workspace flag** `autoPages: Boolean` (default true for iOS-style, false for Nova-style, matching
  those launchers). Additive codec key.
* **"New apps go to Home or Library only" (a setting).** On install of an app, `NewAppPlacement` decides:
  `FINDER_ONLY` (default: nothing placed, the app appears in the Finder/All apps only, which is today's
  behaviour) or `HOME_AND_FINDER` (also placed in the first free cell of the first placed page, creating a
  page if `autoPages`). Setting in Settings > Home & layout > Layout > "New apps", **global**, with a
  per-workspace override later if asked. Because no auto-placement exists today, `FINDER_ONLY` as the default
  is a zero-change default (Settings/default behaviour considered). It needs an app-install signal; the
  existing package-change handling that feeds the installed-app repository is the hook; the placement itself
  is a pure function (`PlacedItemsEditor.placeNewApp`).
* **Finder = All apps as Categories at the end (iOS).** Unchanged from 8.3: the Finder is a `FINDER`-role page
  (Categories for iOS-style, AlphaList for Nova-style) that is not in the pager; the Finder never owns placed
  items, so "Library" cannot hold home icons. A placed icon is just a launcher for an app that is *also* in
  the Finder: the relation is the shared app identity, not a link (removing an icon from Home never uninstalls
  or hides the app, as today).

### 9.6 Migration from `HomeLayoutSet` (idempotent, lossless, golden-tested)

Pure function in `core/domain`, extending `HomeLayoutWorkspaceMapper`: `Home` pages become
`PlacedItemsPage(page = layoutPage)`, a `LauncherPage` copied **by value** (items, placements, spans, grid,
`isPinned`, `generatedContentOverflowCount`, host ids). Other page types map as in the existing table (the
generated pages become lens pages as today; `AllApps` becomes the Finder when it is the only one, 8.1).

* **Idempotent:** deterministic ids (`page:<pageId>` as today, `ws:<deviceclass>:<mode>`); `ensureMigrated`
  never overwrites a workspace that exists; running it twice is identical (byte-equal golden).
* **Lossless:** the round-trip property `toHomeLayoutSet(migrate(x)) == x` for the placed data (pages, items,
  placements, selected page, pinned flags, dock panel), with the per-mode layouts, `preferredModes` and
  `modePairs` kept in the mirror (the mirror is exactly the unmigrated remainder). Verified by golden
  fixtures plus a property test over generated layouts using `GridPlacementEngine` itself to build them.
* **Order of operations on first run (R1):** migrate, write workspace form, **do not draw it**; every
  `HomeLayoutSet` save after that re-derives the placed pages and compares (shadow, S12). The compare is on
  canonical encodings; a mismatch increments a counter and records the *kind* (page count, item count, a
  placement), never item text or labels.
* **Hidden-mode layouts and the Library-only shipped build:** the shipped build stores layouts per mode but
  resolves everything to Library; the migration creates workspaces for every stored mode (as today), so a
  user's Standard-mode home stays recoverable as the "Standard" workspace even though it is not currently
  shown. Which one is active is `shownMode` (existing), unchanged.
* **Failure:** a layout that fails to decode keeps the classic path for that device class (bootstrap
  `Classic(reason)`), never an empty home.

### 9.7 The loss of cheap revert, and the staged approach

Today revert is one line because `HomeLayoutSet` is untouched. After the cut-over the workspace blob is the
only copy unless we keep one. Design:

1. **Shadow (R1, S12).** Workspace form written and compared; nothing reads it. Zero risk to users; produces
   the evidence.
2. **Dual-write after cut-over (R4, S17).** Placed pages are authoritative in the workspace blob. Every
   change **also derives and writes `HomeLayoutSet`** (the mirror: `toHomeLayoutSet(workspaceSet)`), through
   the existing `WriteBehindHomeLayoutRepository` so the mirror costs one extra write per debounce, not per
   gesture. The mirror is what an older build, the `LEGACY` owner and the legacy backup key read.
3. **Read-through safety.** At startup, if the workspace blob is missing or fails to decode but the mirror
   exists, the bootstrap rebuilds workspaces from the mirror (this is exactly today's `ensureMigrated`), so
   the first fallback is automatic.
4. **`PlacedItemsOwner` (`WORKSPACE` | `LEGACY`).** One persisted switch (and a build constant) selecting
   which store the *shell* edits. `LEGACY` is the rollback: the home draws from `HomeLayoutSet` and writes
   it, and the workspace form is re-derived from it (shadow again). The rollback drill (7.1 item 12) flips
   it both ways with edits in between and asserts equality.
5. **One-release rollback window.** The mirror is kept for the whole of R4 and removed only in R5 (S20),
   after a release with zero mismatches. A downgrade to a pre-R4 build during R4 therefore opens with every
   placed item intact.
6. **After R5.** The blob is the only copy; safety nets are the `workspaces_prev` generation (1.5) and
   backup, plus the legacy `"homeLayouts"` backup key, which **stays written** from a derived view for as long
   as the classic path is supported (section 10), so a backup is always restorable on a classic-only build.

**What is risky.** (a) Migration bugs losing a placed item or a widget binding: mitigated by lossless
golden/property tests and the shadow compare, but only real layouts find the odd cases, hence the beta
shadow window. (b) Write amplification and ordering between two stores (blob and mirror): both go through
write-behind; the mirror is derived deterministically from the blob so it can always be regenerated, and a
crash between the two is repaired at next start by re-deriving (blob wins while the owner is `WORKSPACE`).
(c) Widget host-id lifetime with Undo and workspace deletion (9.3). (d) Cold-start cost: the blob now holds
all placed items (bounded by the home grid sizes; benchmark in R1 against today's layout read). (e)
Any in-flight work that edits `HomeLayout` directly (home grid UI) must go through the adapter or it will
desync the mirror: enforce by making the workspace store the only writer once `WORKSPACE` is on and the
legacy repository write-only.

**Recommendation on sequencing** is option C in 1.7 (shadow in R1, cut over together with the default flip in
R4, mirror kept for one release), with option A as the fallback. It respects "now" by starting the data move
immediately while keeping the first release in which anything is read from the new store behind evidence.

---

## 10. The classic path is permanent (Q2)

**As built:** classic is simply how the launcher works today (home from `HomeLayoutSet`, drawer, dock);
workspaces are not wired to the shell yet (`WorkspaceMenuFeature.enabled = false`).

**Decision:** "Use workspaces / Classic" stays in Settings > Workspaces > Advanced permanently.

### 10.1 What "classic" means once placed items move

Classic must **not** be a second copy of the user's data (that would diverge, double the backup and migration
surface, and make "turn it off and on" lossy). Define it as a **rendering mode over the same stored data**:

* The data is the workspace blob (placed pages, dock, settings, exclusions, library). `ClassicProjection`
  is a pure function that selects the layout's **classic workspace** (the default workspace, usually
  "Standard") and presents its placed pages through `PlacedItemsAdapter.toHomeLayout` as the `HomeLayout` the
  existing classic UI consumes, with the drawer drawn by the existing app drawer surface instead of the
  Finder page, and the dock drawn as the device-class `DockModel` with no workspace override applied.
* Classic edits (place an icon, make a folder, add a widget, change the dock pins) go through the same
  engines and are written back through `PlacedItemsAdapter.fromHomeLayout`, i.e. into the workspace blob,
  so **an icon placed in Classic is on the home screen when the user turns workspaces back on**, and vice
  versa. Only one workspace is visible in Classic; the others are untouched and still there.
* Classic ignores everything that is not classic: other pages (Finder, page-sets, widget grids, lens
  pages), dock overrides, per-lens queries, the workspace menu. It does honour exclusion rules (they are
  data preferences, not arrangements) and the return behaviour setting.
* Before S17 (placed items still owned by `HomeLayoutSet`) classic is literally today's path. After S17 the
  `LEGACY` owner (9.7) plus the mirror give the same behaviour for a rollback, and `ClassicProjection`
  replaces it as the steady state; S20 removes the mirror, not the classic rendering.

### 10.2 What it costs

* **Two rendering paths** over one data path: the classic home surface (`StandardHome`, the existing
  `ImmediateHomePager`, app drawer, dock host) stays compiled and maintained alongside the workspace shell.
* **A test matrix dimension**: every standard-launcher behaviour must hold in both. The existing standard-mode
  checklist (`standard-launcher-mode.md`) already is that matrix; it simply runs twice.
* **Feature gating.** New workspace features must degrade in classic by being absent, never by erroring
  (a lens-only page does not exist in classic).
* **Support surface:** bug reports need "which mode" (the diagnostics outcome code includes
  `Classic(userChoice)`).

### 10.3 How to bound it

1. **One data path.** Classic has no storage of its own after S17 (the whole point of 10.1). The only
   classic-only persisted thing is the `WorkspaceRolloutMode` flag.
2. **A contract, not a promise.** Classic supports exactly the **standard-launcher parity list** in
   `AGENTS.md` and `standard-launcher-mode.md`: home screens, drawer/search, dock, folders, widgets,
   wallpaper, grid editing, settings, backup/restore, profiles, hidden apps, notification indicators. New
   features are not required to exist in classic.
3. **Bounded test matrix.** Domain tests are shared (the engines are the same). UI tests: the full
   standard-mode instrumented suite runs in both modes (`deviceVerify`), everything else (editor, presets,
   page-sets, dock overrides, queries) runs workspaces-only. Screenshot tests for classic are limited to the
   existing set and are not extended.
4. **Frozen classic UI.** Classic surfaces take bug fixes, platform changes and accessibility fixes only; no
   feature work. A CI check lists the classic entry points so a PR touching them states why.
5. **Shared engines.** Because both renderings call the same engines through `PlacedItemsAdapter`, a fix to
   collision or folder logic benefits both and cannot fork.
6. **Review point.** The decision is permanent, but the *cost* is measured: counters of `Classic(userChoice)`
   in dogfood/beta diagnostics and the classic-only defect count are reviewed at R5; if classic usage is tiny
   and defects cluster there, the owner can decide to narrow the contract (never to delete data).

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
remains for classic (10.1) and as the migration input.

Codec: optional key `"presentation": {"hidden"?, "edge"?, "iconSize"?}` inside the workspace's dock, additive
(a v1 decoder ignores it and draws the shared dock). Workspace schema version stays 1.

### 11.3 Migration

None needed for stored data: `presentation = null` is "follow", which is today's behaviour for every migrated
workspace. The two behaviour changes are preset-side: Niagara sets `hidden = true`; unfolded TimeScape sets
`edge = LEFT`. These apply on **install** of the preset (and Reset), not retroactively to installed copies.

### 11.4 Layering, accessibility, and the dock-pull menu trigger

* **Resolution order** (pure, `EffectiveDock`): device-class `DockModel` -> workspace `presentation` ->
  (classic ignores the override). `HomeDockHost` and `reservedExtent`/`dockInteractionRegionExtentDp` read
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

Once the mode pair retires (S18) the dock pull has no Home/Library to switch. Decision: the same pull (a drag
away from the dock edge, same claim rules, thresholds and `DockPullTransitionController` direction logic)
**opens the workspace menu**; the explicit dock affordance WS6 proposes stays as the discoverable control and
the accessibility action and Ctrl+arrow equivalent are the ones that already exist. `gestures.md` changes:
"Mode transitions" becomes "Workspace menu"; the dock-pull row and "Dock body" rows say "opens the workspace
menu"; "No dock, no pull" gains "(the menu stays reachable through the surface action and the bound gesture,
11.4)"; the plan-revision banner gets a 2026-10-01 note. `dock.md` loses the Home/Library re-orientation text
and gains the override layering. Until S18 the pull stays the mode switch and nothing in those docs
changes. These doc edits are part of S18, not of this PR (this PR changes only this document and the
external-sources note). Interaction with a dock override: the pull needs a visible dock, so with `hidden` the
bound gesture is the trigger; with a moved edge the pull direction follows the effective edge.

### 11.6 Effect on presets

Niagara: `presentation = DockPresentation(hidden = true)` plus a swipe-up binding to `OPEN_WORKSPACE_MENU`.
Unfolded TimeScape: `presentation = DockPresentation(edge = DockPosition.LEFT)`. Compact TimeScape, Nova,
iOS, Kvaesitso: `null`. `workspaces-presets.md` gaps 2 and 9 close; the presets doc is updated by S10.

### 11.7 Tests

Resolver (`applying`) table; `hidden` + stranding validation (hidden without a bound gesture is invalid; the
a11y/keyboard paths do not count as the *gesture*, they are always there but the editor still requires one so
touch users are not stranded); codec round trip and v1-decoder ignore; reserved extent follows the effective
dock; Niagara/TimeScape preset goldens; classic ignores overrides; switching workspaces changes the effective
dock but never `DockModel.items`.

---

## 12. Favourite and frequent apps as lenses (Q19)

**As built:** the generated pages `FAVOURITES` and `FREQUENTLY_USED` produce no items
(`GeneratedLauncherPageContentPlan`), no favourite store exists (`favouriteAppsAvailable` defaults false) and
usage data is `RecentAppUsage(package, lastUsedAtMillis)` only. `LensSort(pinnedFirst)` reads the ext flag
`launcher.pinned`, which no adapter sets. `HomeLayoutWorkspaceMapper` maps them to the reserved ids
`apps.favourite`/`apps.frequent`, which have no adapter.

**Decision:** lenses over the existing built-in app sources; **no new adapters**, so no flip blocker.

| Page | Definition | Lens | Expression |
| --- | --- | --- | --- |
| Favourites | "Favourite" = **pinned**: an app the user keeps in the dock or on a placed home page. | `apps.all`, `ExtEquals(launcher.pinned, Flag(true))`, sort title | IconGrid |
| Frequently used | "Frequent" = **usage-based**: the apps most recently used, as far as the platform tells us. | `apps.recent`, sort `TIME` descending, limit 12 | IconGrid |

Honest limits: (1) "pinned" needs `launcher.pinned` set. That is an additive change inside the existing
apps adapters (an ext flag, not an adapter): `apps.all` items for apps present in the dock or on placed
pages get `Flag(true)`, computed from `DockModel`/placed pages through the existing
`HomeLayoutAppMembership` helpers. (2) "Frequent" is a **recency** proxy because there is no launch counter;
true frequency needs a counter (new stored data and a Play-declaration line), see N3. (3) The "usage stats
unavailable" case stays: `apps.recent` reports `PermissionRequired`/empty exactly as the Recents page does,
and the page says so (no new prompt).

**Migration mapping:** a migrated `Generated(FAVOURITES)` page becomes the Favourites lens above, a
`Generated(FREQUENTLY_USED)` page the Frequent lens; the golden tests change accordingly (they currently
expect `apps.favourite`/`apps.frequent`). Because those pages produced no items before, no user sees a
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

### 13.6 Compatibility

A stored lens with no `parameters` key decodes with `emptyMap()`: today's meaning. A v1 decoder ignores the
key and draws the lens using the shared query (acceptable degradation: a slot-bound lens follows the default
query). `SearchQueryHolder` stays as the `Default` slot's backing store, so #1365's adapter and tests keep
passing.

### 13.7 Proposed small implementation slice (S8)

1. `ParameterSlot`, `ParameterBinding`, `ParameterKind`, `ParameterValue`, `ParameterizedItemSource`,
   `Lens.parameters` (default empty), codec key `"params"` (additive), `ParameterStore` interface with an
   in-memory implementation whose `Default` slot delegates to `SearchQueryHolder`. Pure, JVM tests only.
2. `SharedSourceRegistry` keyed on `(source, parameter key)` for sources implementing
   `ParameterizedItemSource`; unchanged for all others (a test pins that existing sources are untouched).
3. The search adapter implements `ParameterizedItemSource` (its current `subscribe(observer)` becomes the
   `Default`-bound call), still no network.
4. `SearchBoxContainer` and its host (a text field that writes the store, debounced, with an a11y label and a
   clear action); editor option "Search box" and "Bind to slot" in the lens step.
5. Tests: two lenses with two slots receive different results from one fake source; same slot shares one
   upstream; unbound lens uses the default holder; sentinel-text leak tests (13.4); cap of live upstreams;
   codec round trip and v1-ignore.
No Settings page, no stored query, no new permission; Play declaration line: N/A (text stays in memory).

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
`withHiddenApps`/`NotificationHideRuleFilter` after S7, with the equivalence tests in 14.9 proving no
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
  to the engine in S7. Dock overrides (11) do not interact.
* **Per-lens queries (13).** Applied to a parameterised source's results like any other.
* **Privacy content level (6.2 item 1)** is a *projection ceiling* applied after the lens; exclusions are
  removal before it. They compose and do not overlap.

### 14.6 Scope: global, not per layout (proposal)

The lens library is per layout (Q3) because a lens is part of an *arrangement*. Exclusions are a statement
about **the data the person does not want to see**, like hidden apps and hide rules today (both global).
Per-layout exclusion would let a hidden app reappear when the person unfolds the phone, which reads as a
bug, and would make "Hide this app" ambiguous. **Recommendation: one set of rules per install, applied on every
layout and in classic.** Storage: a `source_exclusions` key in the workspace DataStore, **separate from the
workspace blob** (so a corrupt workspace blob never loses hiding, and hiding survives "Reset workspaces"),
with the same `_prev` generation. The rule type carries no layout field; if per-layout is later wanted it is an
additive optional `layouts: Set<HomeLayoutDeviceClass>?` (null = all). Question N4.

**Migration and compatibility (no loss):**

* **Hidden apps** -> `SourceExclusionRule(source = apps.all, App(package, profile), origin =
  MIGRATED_HIDDEN_APP)` with id `mig:app:<profile>:<package>/<activity>`. Hidden apps are keyed by
  `AppIdentity` (package + activity + profile), so a package-and-profile matcher would be *wider* than the
  stored identity for an app with several launcher activities. To stay behaviour-compatible the `App`
  matcher carries an optional `activityName` (see the sketch in 14.2): migrated rules and the contextual
  "Hide this app" set it, an explicit "Hide all of this app" leaves it null.
* **Notification hide rules** -> same-source rules with the same kind/value/mode: `APP` -> `App`; `TITLE`/`BODY`
  -> `Text(field, value, mode, app = App(package, profile))`; `EMPTY_CONTENT` -> `EmptyContent(app)`. Id
  `mig:notif:<ruleId>`. Existing semantics (rules scoped to one app) are preserved exactly.
* **Idempotent and mirrored.** Migration runs on every start like `ensureMigrated` and never duplicates (ids
  are deterministic). Until R5 the legacy stores stay the **mirror**: every rule change that corresponds to a
  legacy shape is written through to `AppVisibilityRepository` and `NotificationHidingSettings` (so a
  downgrade or an older build sees its own data, and the backup keeps writing `"hiddenApps"` and the settings'
  hide rules unchanged). After R5 the mirror write stays only for **backup compatibility** (an older app
  restoring a newer backup must still get its hidden apps), not for runtime. New rule kinds with no legacy
  shape (Group, ItemKey, SourceKey, Text on other sources) live only in `"exclusions"` and an older app
  ignores them (the data they hide reappears there, an acceptable degradation).
* **Backup:** rules back up as definitions (they hold user-authored text and app/feed identifiers, never item
  content), under optional `"exclusions"`; restore is replace with Undo like everything else (5). Rules never
  embed a URL.
* **Diagnostics:** counts only (rules per source, hidden count), never match text, labels, packages or feed ids.

### 14.7 Authoring

* **Contextual** (like notification hide rules today): every item surface offers a **Hide** action in its
  item actions/overflow or long-press menu: "Hide this app", "Hide this feed", "Hide this event type", "Hide
  notifications like this", with the exact vocabulary in 14.4. Tapping creates the rule **immediately and
  visibly**: the item disappears with a snackbar "Hidden: <what>. Undo | Manage" (never silent). Broad rules
  show a one-line confirmation first (a text rule shorter than 3 characters is rejected; "Hide this app" is
  not confirmed because Undo is one tap).
* **Settings > Sources:** a list per source and an "All exclusions" page. Each row shows what it matches
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

### 14.8 Slices

S6 (engine, migration, codec, goldens; pure), S7 (store, provider pre-step, legacy consumers moved, mirror),
S14 (Sources UI, contextual action, Hidden apps view). S3 carries backup. Channel/thread and calendar-id keys
are an additive follow-up (N5).

### 14.9 Tests

Engine truth table per matcher and match mode (EXACT/CONTAINS/WILDCARD semantics lifted from
`NotificationHideRule` and tested against it); **equivalence tests**: on a shared fixture corpus the unified
engine returns exactly what `NotificationHideRuleFilter` and `withHiddenApps` returned; migration goldens (a
settings blob with all rule kinds and 200 rules, a 60-app hidden set, a pre-existing mixed state, running
twice); mirror byte-equality; layered invariant property test (lens output never contains an excluded item);
redaction invariants (`Text` never matches `SENSITIVE`; counts never include sensitive content);
dock/badge consumers parity; `OFF` keeps rules; disabled rule does not hide; add/edit/delete/Undo use-case;
cap behaviour with migrated rules exempt; codec tolerance (unknown matcher kind dropped, never throws);
backup old-app compatibility (legacy keys still written); a11y action present (UI test) and screenshot test of
the rule list.

---

## 15. External items in the dock, and ICS full recurrence (owner decisions; not WS10 implementation)

Recorded here because both change constraints this design depends on; the external-sources doc carries the
matching "Owner decisions" note.

* **External-source items may appear in the dock's dynamic section (decided; the earlier recommendation was
  "containers only").** Trust model: the trust rules in `workspaces-external-sources.md` section 4 (plain
  data, `Open`-only actions, target allowlist, provenance, `SENSITIVE`-capable) were written for containers;
  the dock adds constraints: (1) the dock is always visible, so spoofed or noisy external items get maximal
  exposure: external items are ordered **after** built-in items in the dynamic section (the existing
  "built-ins ahead of external" rule becomes a hard rule here), and an item may show only icon plus count
  (the dock dynamic section is IconRow today; no title/body is drawn there, so the exposure is the icon);
  (2) **dock budget**: the dynamic section already has a slot budget (`notificationSlotCount`, 1..5); external
  items **share** that budget, they never extend it, and an optional per-source cap (proposal: external
  sources together may occupy at most half the slots, at least one slot always reserved for built-ins when
  any exist); (3) provenance must be reachable from a dock icon (long-press shows "From <app name>", an a11y
  custom action); (4) exclusions (14) and `OFF` (Q13) apply; (5) `PRIVACY_SENSITIVE` external items show icon
  only; (6) no auto-launch on tap inside the dock beyond the single explicit tap, same as containers. These
  become validation rules (`DockPairing` plus a source-trust check) in the slice that adds the first external
  source; nothing in WS10 ships an external source.
* **ICS recurrence: full support (decided; the earlier assumption was a documented RRULE subset).** Scope is
  larger: RFC 5545 `RRULE` expansion (`FREQ` including `YEARLY`/`MONTHLY` with `BYDAY`/`BYMONTHDAY`/`BYSETPOS`,
  `INTERVAL`, `COUNT`/`UNTIL`), `EXDATE`/`RDATE`, `RECURRENCE-ID` overrides, all-day versus timed, **time
  zones** (`VTIMEZONE` and DST), and bounded expansion windows (a source asks for the next N days, never an
  unbounded expansion; hard cap on instances per refresh). Implications: a correctness-heavy pure
  component with its own test corpus (RFC examples), a decision on an in-repo implementation versus a
  dependency (new dependency means supply-chain and size review, Play-side none), and it moves the
  "ICS source" from a small slice to a medium one. Open question N8. Outside WS10; sequenced after the
  external-scaffolding step in `workspaces-external-sources.md` section 8.

---

## 16. Risk register

Likelihood (L) and impact (I): H/M/L. "Owner" is the slice or doc section that carries the mitigation.

| # | Risk | L | I | Mitigation | Where |
| --- | --- | --- | --- | --- | --- |
| 1 | **Placed-items migration loses or moves an item, or breaks a widget binding** (the data move that Q16 makes mandatory) | M | H | Lossless round-trip property and golden fixtures including widgets, folders, dock panel and the id-collision case; **shadow compare at zero mismatches** before anything reads the new store; real-fixture sweep; dual-write mirror; rollback drill executed on a beta device before R4; option A fallback if the shadow is not clean | 9.6, 9.7, S11, S12, S17 |
| 2 | **Cheap revert is gone; a bad cut-over cannot be undone** | M | H | `PlacedItemsOwner` switch, mirror kept through R4, `workspaces_prev`, legacy backup key, bootstrap rebuild-from-mirror, R5 gated on a clean release | 9.7, 1.7 |
| 3 | **Permanent classic path doubles maintenance** (two renderings, test matrix, drift) | H | M | Classic as a *rendering* over one data path, frozen UI contract, shared engines, standard-mode suite runs in both modes only, defect/usage review at R5 | section 10 |
| 4 | **Dock overrides interact badly with the shared dock model** (reserved extent, pull direction, a hidden dock stranding the menu, per-class vs per-workspace confusion, classic ignoring overrides) | M | M | Override-only type, pure `applying`, stranding validation with an always-on a11y/keyboard path, effective-dock used by host and extent code, golden presets, classic ignores overrides | 11 |
| 5 | **Scope growth**: 20 slices, five new subsystems (placed items, library per layout, dock overrides, per-lens queries, exclusions) landing while WS6/WS7/WS8 are in flight | H | H | Strict slice dependencies, R1-R3 invisible to users, parallel chains with one critical path (S11 -> S12 -> S17), per-slice gates; **cut line**: S8 (per-lens queries), the channel/thread exclusion keys and the `autoPages`/`NewAppPlacement` settings can slip past R4 without blocking the flip | 7 |
| 6 | **Widget host-id lifetime** (Undo, workspace deletion, restore, switching) leaks or double-binds ids | M | M | Single-owner uniqueness validation, deferred deletion queue, startup reconciliation, restore placeholders, never clone | 9.3 |
| 7 | **Write amplification and cold start** (larger blob, mirror write, exclusions) | M | M | Write-behind for both stores, bounded read with timeout and `Classic(timeout)`, benchmark gate in R1, separate keys for exclusions | 1.2, 9.7 |
| 8 | **Exclusion unification changes hiding behaviour** (hidden apps, hide rules; badges, drawer, search, dock cards) | M | M | Equivalence tests against the legacy helpers, migration goldens, mirror byte-equality, backup legacy keys, same match semantics | 14 |
| 9 | **Per-lens query leaks text** (log, backup, diagnostics, `toString`) | L | H | Redacting `toString`, sentinel leak tests, store never persisted, key hashed in memory only, review checklist line | 13.4 |
| 10 | **Per-layout lens library drifts and surprises** (two copies of "the same" lens) | M | L | Explicit "Copy to layout", clear UI ("only this layout"), copy-from-layout copies lenses | 4 |
| 11 | **Backup of widgets and tokenised URLs** (existing gap; secrets) | M | M | Placeholders, token-omission rule with tests, owner-run Play declarations | 5, 7.2 |
| 12 | **Return behaviour regressions** (#1323/#1176 re-opened by a third rule) | L | M | Pure reducer with the full table and property test (never changes workspace) | 3.2 |

---

## Open questions for the owner

### Decided (2026-10-01)

Q1 to Q19 are answered in the banner at the top; the table is not repeated here. Q12 is not a decision (it
became a test and is mooted by section 9). The extension-API, tokenised-URL, dock-external-items, ICS, Play
declaration, per-lens query and exclusion decisions are in the same banner.

### New questions raised by the changed scope

Kept tight; each has a recommendation, and none blocks starting S1 to S7.

| # | Question | Recommendation |
| --- | --- | --- |
| N1 | **Staging of the placed-items cut-over relative to the default flip** (1.7 options A, B, C). | **C**: shadow now (R1), cut over together with the flip (R4) with the mirror kept for a release, and **A** as the pre-agreed fallback if shadow compare is not clean. Also decide the clean-shadow bar: proposal is zero mismatches for two weeks across the beta population. |
| N2 | **Ownership of placed items: workspace-owned (9.2) versus a shared per-layout pool with per-workspace arrangements.** | Workspace-owned: it matches the data as it is today, keeps widgets single-owner, and avoids reference counting. Revisit only if users ask to share one home between workspaces. Consequence to accept: duplicating a workspace or copying a layout does **not** copy widgets (they are re-added), and deleting a workspace deletes its placed items (with Undo). |
| N3 | **What does "frequent" mean?** Today only last-used time exists, so the lens (section 12) is a recency list. True frequency needs a launch counter (new stored data, Play declaration line). | Ship the recency lens now, named "Recently used" in the UI where it would mislead; add a counter later only on demand. |
| N4 | **Exclusion scope: global (14.6) or per layout?** | Global, separate store from the workspace blob. Per layout would resurface hidden apps when unfolding. Leave an additive optional per-layout restriction for later. |
| N5 | **Notification channel/thread and calendar-id exclusion keys** need new platform fields and ext keys (not available today). In S7 or after? | After the flip (cut line, risk 5): app, title/body and empty-content rules already cover today's hide rules; add channel/thread/calendar-id as an additive follow-up. |
| N6 | **Hidden dock and the menu entry** (11.4): require a bound gesture for a workspace that hides its dock? | Yes, enforced by validation, plus the always-on TalkBack action and Ctrl+arrow, so no touch user is stranded. Niagara ships a swipe-up binding. |
| N7 | **How much of classic do we promise** (10.3)? | The standard-launcher parity list only, frozen UI, standard-mode suite in both modes; review cost at R5. Never delete classic data. |
| N8 | **ICS full recurrence: in-repo implementation or a dependency?** | Evaluate once, before the ICS slice, with the RFC examples as the acceptance corpus; prefer in-repo if the dependency is large or pulls Android-unfriendly parts. Out of WS10. |
| N9 | **Default for "New apps"** (9.5): Finder only (today's behaviour) or Home and Finder? | Finder only for Nova-style (unchanged behaviour), Home and Finder for iOS-style, as a setting with that per-preset default. |

## Known limitations of this design

- Calendar selection, per-source scheduling and non-RSS/Search external sources are out of scope here
  (#1366 covers other sources; #1374 covers RSS refresh).
- Recents' and Media's exact permission actions are declared by their adapters (`SourceAccess`) and
  were not enumerated here; the Sources page must read them from the adapters, and S14 must verify each
  against the existing explicit flows.
- The widget-in-backup behaviour (5) is flagged from reading the codec and the import path; it needs a
  device check before S3 and S17 rely on the placeholder design.
- Section 9 specifies the placed-items move at design level; engine adapter signatures are sketches and will
  be settled in S11. The shadow period is what turns the lossless claim from a design property into
  evidence, so it is not skippable.
- Dock override fidelity for presets depends on the S9/S10 gesture binding (`OPEN_WORKSPACE_MENU` does not
  exist in `LauncherGestureAction` today and must be added).
- Nothing in this document has been compiled or run; claims about code were checked by reading the files
  cited (Gradle is unavailable in the authoring sandbox).
