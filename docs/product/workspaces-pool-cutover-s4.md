# Workspaces: pool cutover (WS10 S4, as built)

Status: **wired, on by default.** The standard home renders and edits FROM the placed-items pool: every Home page item
and the dock come from `riffle_pool`, and `HomeLayoutSet` is no longer written for placed items or dock pins. A
developer switch (Settings > Developer > **Home from placed items**) falls back to the legacy path for one release. The
first half of this doc is the domain contract (PR #1428); the [app half](#app-half-as-built) follows.

**Take a backup export before installing this build** (Settings > Backup > Export, keep the file). Builds on [`workspaces-pool.md`](workspaces-pool.md) (pure pool), [`workspaces-pool-cutover.md`](workspaces-pool-cutover.md)
(read-only store and import) and [`workspaces-pool-editing.md`](workspaces-pool-editing.md) (preview editing). Design:
`workspaces-configuration.md` sections 9 and 11. Owner decisions of 2026-10-23/24 on #1363 (the pool store is
authoritative; the dock moves into the pool now; the widget rule below).

## What changed in the domain

| Piece | Where | Notes |
| --- | --- | --- |
| Widget rule | `PoolPages`, `PoolPlacement`, `PoolValidation`, `PoolCopy`, `PoolResults` | A widget is ONE instance; it may sit in any number of arrangements, at most once per arrangement. |
| Dock in the pool | `PoolDock.kt` (`PoolDock`, `PoolDockSettings`), `PlacedItemPool.dock` | Pins (pool item refs, in order), optional panel page, every `DockModel` setting plus Library's edge. |
| Dock import | `PoolMigration.importDock`, `PoolCutover.ensureMigrated` | From `HomeLayoutSet.dockFor` + `libraryDockEdgesByDeviceClass`; once per store. |
| Codec | `PoolDockCodec.kt`, `PoolCodec` (`dock` key), `PoolStoreCodec` version 2 (`dockImported`) | Version 1 still decodes. |
| Fork on first edit | `PoolArrangements.fork` | Own arrangement copied from the borrowed one. |
| Projection and ingest | `PoolHomeProjection.project` / `ingest` / `projectDock` | `HomeLayout` <-> pool for Home pages and the dock. |
| Host-id reconciliation | `PoolHostIds.referencedHostIds`, `PoolHostIds.unreferenced` | Pure; across every layout, arrangement and dock. |

## The contract the app wave calls

### Rendering the standard home

1. Pick the workspace whose home is shown (the active one) and the pool of the active device class.
2. If the workspace has no arrangement of its own (`PoolArrangements.ownsArrangement` is false), either show the one it
   borrows (`PoolHomeView.arrangementOf(pool, PoolHomeView.candidates(...))`) or fork first (below).
3. `PoolHomeProjection.project(pool, workspaceId, base)` where `base` is the stored per-mode `HomeLayout` from
   `HomeLayoutSet` (`layoutFor(key)`). The result is the `HomeLayout` the standard home and its engines already use:
   * `Home` pages come from the arrangement, in order: the k-th `Home` page of `base` is replaced by the k-th arrangement
     page; extra arrangement pages follow the last `Home` page of `base` (or lead when it has none); surplus `Home` slots
     are dropped. Generated and All-apps pages stay where `base` has them.
   * The dock is the pool's (`projectDock`). A pool without a dock yet keeps `base.dock`.
   * View mode, template, settings, edit mode come from `base`; the selected page is `base.selectedPageId` when it still
     exists, else the first `Home` page.
   * A workspace without an arrangement gets no `Home` pages; if no page would be left, one empty `Home` page (the first
     `Home` id of `base`, or `home`, with the settings grid) stands in, so the layout is always valid.

### Editing

1. **Fork on first edit.** Before the first edit of a workspace that borrows an arrangement, call
   `PoolArrangements.fork(pool, workspaceId, PoolHomeView.candidates(active, deviceClass, shownMode), ids)`. Apps and widget
   instances are shared by reference, folders are cloned (the fork's folders are independent), pages are copied with their
   ids, grids and pinned flags, the new-app setting is copied, the dock is untouched. Idempotent. `NOTHING_TO_FORK` when no
   candidate has pages (then ingest creates an arrangement from the edited layout).
2. Run the existing engines on the projected layout exactly as today (`GridPlacementEngine`, `FolderEngine`, `WidgetEngine`,
   `HomeShortcutEngine`, `HomePageEngine`, `DockEngine`, `DockConfigurationEngine`).
3. `PoolHomeProjection.ingest(pool, workspaceId, before, after, ids)` with `before` the projected layout the engine was given
   and `after` its result. It returns one atomic `PoolEdit` (feed it to `PoolEditSession.apply(PoolResult.Done(edit))` for
   Undo/Redo and the deferred host-id queue):
   * `Home` pages of `after` become the arrangement's pages (add, delete, reorder, grid, pinned flag). Non-`Home` pages are
     not the pool's and are ignored: write them to `HomeLayoutSet` as today.
   * Moves and resizes are placement edits; a folder's label and entries, an app's label and a widget's resize constraints
     edit the one pool item (visible in every workspace that shows it).
   * The dock's pins (order), panel (widgets and shortcuts on it, or none) and settings become the pool's dock; Library's
     edge is kept from the pool (it is not on `DockModel`). A pool without a dock gets one.
   * An item that enters the dock while another workspace still shows it: an app or folder is cloned for the dock (the
     other workspace keeps its icon); a widget instance moves into the dock and leaves the other arrangements (the dock is
     drawn beside every workspace, so it would otherwise be on screen twice).
   * The result is repaired and garbage collected.
4. Write the non-`Home` remainder (`after` minus `Home` pages, its settings, selection) to `HomeLayoutSet`; do **not** write
   `Home` pages or the dock there any more.

### Id rules

* A projected `LauncherItemId` **is** the `PoolItemId` text. There is no prefix to add or strip, so an unchanged edit
  round-trips without re-minting (property test: `ingest(project(p), project(p)) == p` over 120 random pools).
* `ingest` trusts an id only when `before` showed it and the pool holds it in this arrangement or the dock. Any other id in
  `after` (engine-minted ids count per arrangement, so they can equal an id another arrangement uses) is new and gets a
  fresh id from the `WorkspaceIdFactory`.
* Page ids need only be unique within one arrangement; a fork keeps them.
* Migrated ids: `pi:<deviceclass>:<mode>:<LauncherItemId>` (home items), `pi:<deviceclass>:dock:<LauncherItemId>` (dock pins
  and panel items); a repeat or an id already in use gets a `#n` suffix. Deterministic, so a re-run is equal.
* **Changed by the app half:** a new id is `PoolIds.derived`: the engine's own text when no pool item uses it, else the
  first free `text#n` (it used to be a fresh factory id). Prefixes that mean something to the standard home survive
  (`library-app:`), and a Library relayout that strips and re-mints the same text lands on the same id instead of
  minting a new one every pass. Collisions are still never trusted (`only2` in another arrangement gives `only2#2`).

### Host-id rules

* A widget is ONE instance (host id plus configuration). It may be placed in any number of workspace arrangements, at most
  once per arrangement, or in the dock (then in no arrangement). A host id is held by one pool widget.
* `PoolEdit.releasedHostIds` holds exactly the ids whose widget no arrangement and no dock references any more. Removing a
  shared widget from one workspace releases nothing. Delete released ids only after the Undo window
  (`HostIdDeletionQueue`, `PoolEditSession.finish`), as today, and keep subtracting `PoolHostIds.standardHome` while
  `HomeLayoutSet` still holds widgets.
* Duplicate and fork share the instance (no placeholders). A divergent configuration is the explicit
  `PoolWidgets.separateCopy` (new pool widget, new host id or an unbound placeholder). Copy from another layout still makes
  placeholders: references cannot cross pools.
* Startup reconciliation: `PoolHostIds.unreferenced(allocated, state, standardHome, pending)` returns the allocated host ids
  that nothing references: not the pool store (`referencedHostIds(state)`: every layout, arrangement and dock), not the
  standard home, not an id still pending in a deletion queue. The app reads `allocated` from `AppWidgetHost.getAppWidgetIds`.

### Migration flags

* `PoolStoreState.migrated`: the one-time import of placed items has run (unchanged).
* `PoolStoreState.dockImportPending`: a dock import is still owed. Only decoding a **schema 1** value that was migrated sets
  it; `ensureMigrated` then imports each layout's dock once (`PoolMigration.importDock`) for every stored class and every
  class with a layout, keeps the stored, possibly edited arrangements, and clears the flag. A state built in code (migration,
  restore, tests) is never pending.
* Store schema 2 writes `dockImported: true` and the pool's `dock`. Version 1 values decode unchanged; a future version
  reads as "not stored" as before. A pool without a `dock` key decodes with `dock = null` (projection then keeps
  `base.dock`, and the next ingest writes one).
* `reimport` (Refresh home items) imports the docks too, so once the dock is edited in the pool it would overwrite those
  edits with `HomeLayoutSet`'s stale dock: the app wave should retire Refresh at the cutover.

### Uninstall

`PoolRemoval.pruneUninstalled(pool, packageName, profile)` (confirmed package-removed events only) now also drops dock pins of
that package and its entries in dock folders, and dock folders left empty. Widgets of the package are not touched (their
provider is gone: `PoolWidgets.unbind`).

## Decisions to confirm

1. **Dock items are exclusive to the dock.** An item is either pinned or on a home page, never both, so a projected
   `HomeLayout` never holds one `LauncherItemId` twice. Moving a shared app into the dock clones it; moving a shared widget
   into the dock takes it out of the other workspaces.
2. **Copy from other layout keeps widget placeholders.** The owner's rule covers forking and duplicating within a layout;
   across layouts the pools are separate stores of references, so the copy is a placeholder as before.
3. **`PoolStoreState.dockImportPending` instead of a `dockImported` field.** It defaults to false, so restores and states
   built in code never trigger a second import; only a decoded schema 1 value does.
4. **Library's dock edge moves into the pool** (`PoolDockSettings.libraryPosition`), so the dock needs nothing from
   `HomeLayoutSet`. `ingest` keeps it because `DockModel` does not carry it; the settings UI must write it to the pool.
5. **A workspace without an arrangement is accepted by `ingest`** (it gets one). The app should still fork first, so the
   user's borrowed home is copied rather than replaced by whatever the edited layout held.
6. **`PoolRejection.WIDGET_ALREADY_PLACED` and `PoolIssueKind.WIDGET_MULTIPLY_PLACED` are kept but no longer produced**, so
   app code that names them still compiles.

## Tests

`PoolHomeProjectionTest` (projection page merge, selection, stand-in page, pool without dock; identity property over 120
random pools; move; removing a shared app or widget releases nothing until the last reference; folder create, rename, add,
remove; widget add, resize, remove; page add, delete, reorder; non-`Home` pages ignored; dock pin add, remove, reorder; dock
panel widget add, remove, panel off; dock settings; shared app and widget moved into the dock; engine-minted id collision;
first ingest of a workspace without an arrangement), `PoolArrangementsTest` (fork sharing, idempotence, `NOTHING_TO_FORK`,
independence after the fork, dock untouched), `PoolDockTest` (dock migration, settings and Library edge, deterministic ids,
import into an edited pool, codec round trip, schema 1 store imports its dock once, schema 2 not pending, dock references in GC
and host ids, reconciliation, remove everywhere, uninstall pruning of pins and dock folders, dock exclusivity in placement and
repair, `addApp` never reuses a pin), updated `PoolPlacementTest`, `PoolCopyTest`, `PoolCodecTest`, `PoolHomeEditingTest`,
`PoolCutoverTest`, and `PoolInvariantsPropertyTest` (now with a dock, fork and projected home and dock edits).

## Verification

`./gradlew verify` was **not** run (dl.google.com is blocked here); CI runs `verify deviceVerify`. The whole `:core:domain`
test suite, ktlint (main and test) and detekt (pool package) ran in a scratch Gradle project on Maven Central only.

## App half (as built)

### The shape

The shell keeps its engines, reducers and its in-memory `LauncherShellState.homeLayoutSet`. What changed is the one
repository every layout write goes through:

| Piece | Where | What it does |
| --- | --- | --- |
| Write choke point | `HomeLayoutRepository.commitHomeLayoutSet` (domain interface, default = save and return the set) | Every reducer that wrote `saveHomeLayoutSet` now keeps what `commit` returns (`withHomeLayout`, `withSettingsTargetLayout`, mode/template/device-class selection, the dock edge, Home's reset, startup's device-class choice, backup import). A plain store returns the same set, so the legacy path is unchanged. |
| Whole-set mapping | `core/domain/.../pool/PoolHomeSync.kt` (pure) | `commit(state, previous, next)`: ingests every key whose `Home` pages or dock changed since `previous` (active key first), adopts a key it has not seen, projects every key again; `stored(shown)` is what `HomeLayoutSet` still gets; `pruneUninstalled`, `reproject`, `restore`. |
| Pool-backed repository | `app/.../pool/PoolHomeLayoutRepository.kt` | Implements `HomeLayoutRepository` over `CachedPoolRepository` (in memory at once, written behind after 250 ms and on `onStop`) and the legacy write-behind repository (now only told the stripped set). Host-id release, external-change signal, backup restore. |
| Startup | `app/.../pool/PoolHomeStartup.kt` (`openHomeLayouts`), `HomeLayoutStartupLoad.kt`, `DataStoreHomeLayoutStore.kt` (`HomeLayoutRepositories.runtime`) | Reads the layout set and the pool in the one blocking startup read the launcher already had, runs the cutover or the hand-back, builds the repositories. One `CachedPoolRepository` per process, shared by the home, the preview and backup. |
| Switch | `pool/PoolHomePreference.kt` (`riffle_pool_home`, default on), Developer row in `WorkspacePreviewSetting.kt` | Read once per process; flipping it applies at the next start. |

### Which arrangement a key shows

Every `HomeLayoutSet` key (`deviceClass`, `mode`) shows `ws:<layout class>:<mode>` in the pool of
`deviceClass.workspaceLayoutClass()` (N10): the landscape phone shows the phone pool's arrangement and dock, so rotating
changes geometry only. The standard home does not follow the Workspaces preview's active workspace (that stays a
preview concept until S9); it always shows the migrated workspace of the shown mode. Generated and All-apps pages
(including Library's `library:N` overflow pages), settings, template, selection, edit mode, mode choices and Library's
dock edge stay in `HomeLayoutSet`. Library's edge is mirrored into `PoolDockSettings.libraryPosition` on every commit.

### What `HomeLayoutSet` still holds

`PoolHomeSync.stored`: the same set with every `Home` page's items and every dock's pins and panel items removed in
the device classes the pool covers. `Home` pages stay as empty slots so their order among generated pages and the
selected page survive; dock settings stay. So moving, adding or removing an icon, folder or widget, or pinning to the
dock, writes the pool only (tested: the stored set is equal before and after a move).

### Startup, cutover and hand-back

* **First start with the switch on** (no snapshot): `CachedPoolRepository.initializeForCutover` reads the pool and
  brings it in line with the stored set (`PoolCutover.resync`: the migrated workspaces' arrangements and the docks are
  re-imported from `HomeLayoutSet`, everything else in the pool, such as the preview's own or forked arrangements, is
  kept). A pool imported earlier by the preview is stale by now, so the owner's current home wins. Only once that write
  has landed are the snapshot (`home_layout_pre_pool` in `riffle_home_layout`: the pool's full projection) and the
  stripped set written, in one DataStore transaction. If anything fails the home stays on the legacy path for that
  process and nothing is stripped.
* **The snapshot is a recovery copy, kept recent**: whenever the pool has been fully written, whoever wrote it (the
  home or the preview's editor), the pool's full projection of the shell's set (items and dock) is written to it. It is
  never the source of truth while the pool can be read.
* **Switch turned off**: on the next start the pool's view (with a fallback session's additions, below) is written back
  as the full `HomeLayoutSet` and the snapshot removed. Turning it on again cuts over again (resync from the
  then-current set).
* **A legacy fallback session** (a start that could not use the pool) keeps the marker. The next start that can use the
  pool prefers it: everything the pool holds stays, and only what that session added (ids no pool item has) is placed
  on top (`PoolHomeSync.open`, `PoolHomeFallbackMerge`). A removal or move made in the fallback session is not carried
  over, because the pool may be newer than what that session started from.
* The startup read stays the single blocking read the launcher had: the pool is read in the same step, so the first
  frame already shows the pool's items.
* **Recovery notice**: when part of the saved home could not be read (an undecodable or unreadable snapshot, an
  undecodable pool, a quarantined copy, or a failed `HomeLayoutSet` read), the user is told once (a toast) that a
  backup import restores it. The quarantined copies (`pool_corrupt`, `home_layout_pre_pool_corrupt`) are never
  overwritten; nothing reads them automatically.

### Failure handling

| Situation at start | What happens | Why nothing is lost |
| --- | --- | --- |
| `HomeLayoutSet` store cannot be read (I/O) | Legacy path on defaults; that store and the pool are not written this process; notice. | What is on disk is kept for the next start. |
| Snapshot absent, switch on, pool write or snapshot write fails | Legacy path this process; nothing stripped. | `HomeLayoutSet` still full. |
| Snapshot absent but `HomeLayoutSet` has no placed items and the pool holds a home (marker lost) | `PoolCutover.resync`'s hard guard keeps the pool (`holdsHome`: at least one placed item or dock pin); a fresh snapshot (the projection) is written. | A set without placed items never replaces a pool with items. |
| Snapshot present but undecodable (or its read fails) | Copied once to `home_layout_pre_pool_corrupt`; never cut over again and no pool is derived. Only a stored, readable pool that holds a home runs it (or is handed back with the switch off), with a fallback session's additions ingested; otherwise the legacy path runs on the stored set and nothing is written at startup. Notice. | Neither a resync nor a migration from a stripped set can write an empty home over what is left. |
| Pool blob undecodable, snapshot readable | Copied once to `pool_corrupt` (if that copy fails, persistence is off and the legacy path runs); then rebuilt from the recent snapshot and written. Notice. | The original stays in `pool_corrupt`; the snapshot follows every full pool write; ids already `pi:...` are kept as they are, so a rebuild gives the same ids every time. |
| Pool blob undecodable and snapshot unreadable | Both quarantined; nothing migrated, nothing written at startup; legacy path on the stored set. Notice. | Both originals are kept for a manual or backup recovery. |
| Pool read fails (I/O), snapshot present | Legacy path this process from the full set (the snapshot's, or a set an earlier fallback session wrote, which is kept); the marker stays. The next start that can use the pool prefers it and adds what this session added. | The pool home never runs without a writable pool; a fallback session cannot roll the pool back. |
| Fresh install (nothing stored) | The cutover writes a stripped default set and the snapshot; a repository opened without a set measures the first commit against the pool's projection, so the first add is ingested (and a widget's host id is held). | Tested: add, restart, the item is there. |

### Editing

Engines run unchanged on the projected layout; `commit` ingests the result. Ids are kept where free
(`PoolIds.derived`), so a reducer that refers to an item it just created still finds it. Two keys of one workspace in
one commit (portrait and landscape phone): the active key wins; once a key wrote a dock, the others compare against the
pool's dock. Selection and edit mode never touch the pool. The standard home adopts a key it has never seen (a new device
class, a template's seed) as that workspace's arrangement; it does not fork from the preview's workspaces.

Library: compact mode strips and re-places its own apps every pass; `isLibraryApp` now also recognises migrated ids
(`pi:...:library-app:...`), and the re-placed `library-app:` ids are kept from then on (tested: two relayouts give an
equal pool).

### Host ids

* Released ids are handed out after **any** successful pool write that leaves nothing unwritten (the preview's flushes
  too), and a restore reports the host ids of the current widgets the restored pool no longer holds, through the same
  referenced check.
* The shell's "delete the removed widget's host id" (remove, delete page, remove from dock) now goes through
  `deleteHostedWidgetIdUnlessReferenced`: an id any arrangement, the dock or the shell's set still references is kept.
* Ingest's released ids are collected and handed to the releaser only after the pool write landed, filtered again
  through `PoolHostIds.isReferenced` (pool store plus the shell's set, pages and docks).
* The startup orphan check on a discarded widget-add transaction is pool-aware (pool store plus set; Unknown, so nothing
  is deleted, while the pool home has not loaded).
* `PoolHostIds.standardHome` now includes docks and dock panels (it read pages only).
* The preview's releaser uses the same guard.

### Uninstall

A confirmed package-removed event calls `HomeLayoutRepository.withoutRemovedPackage` first: `PoolRemoval.pruneUninstalled`
on every pool (every workspace, dock pins, folder entries, emptied folders; widgets untouched), then the shell's own
pruning runs on the pruned projection. Never on "absent from a snapshot" or a paused profile (the only call site is the
confirmed-removal callback).

### Backup

* Export: `"homeLayouts"` is still written (the full projection, so an older build restores the home), the `"pool"`
  section as before, plus `"homeFromPool": true` when the home renders from the pool.
* Import with the pool home: `PoolHomeSync.restore`. A `homeFromPool` backup restores its pool section as the
  authority; its widgets take their host ids back from `"homeLayouts"` by item id (the pool section carries none). Any
  other backup (older builds, with or without a pool section) re-imports its `"homeLayouts"` into the pool through
  `PoolCutover.resync` over its pool section or, without one, the current pool. The port's separate pool restore is
  skipped then (the home's restore does it, in order).

### The preview

The preview's home pages already read the pool; its dock now reads the pool's dock while the pool home is active.
Its editor ends an edit session when the pool changed outside it (`PoolEditController.endIfChangedElsewhere`, checked
before every edit, Undo and Redo, and whenever the pool's version changes): what the session already wrote is kept, its
Undo history is dropped, a short notice says so, and a session pool built on an older base is never written.
`externalChanges` compares the pool with the state the home last published (not versions, which are conflated), and an
ingest drops ids its `before` showed that the pool no longer holds, so a home commit on a stale projection never
re-creates something the preview removed.
"Refresh home items" is retired then (it would replace edits with a re-import). Preview editing and the standard home
share one `CachedPoolRepository`; `PoolHomeLayoutRepository.externalChanges` fires when the preview changed the pool and
the shell reprojects (`LauncherShellViewModel.observePoolHome`); the preview observes `version` as before.

### Decisions to confirm (app half)

1. **`HomeLayoutSet` keeps empty `Home` page slots** rather than dropping `Home` pages, so page order among generated
   pages and the selection survive; plus a one-time pre-cutover snapshot as the disaster fallback.
2. **Resync at the cutover**: the stored `HomeLayoutSet` wins over a pool the preview imported earlier for the migrated
   workspaces; the preview's own arrangements are kept.
3. **Landscape phone follows N10**: it shows the phone arrangement and dock. Items that existed only in the old
   landscape layout are not shown any more (they stay in the snapshot; their widget host ids are not deleted).
4. **The standard home shows `ws:<class>:<mode>`**, not the preview's active workspace, until S9.
5. **The switch applies at the next start** (hand-over in either direction happens at startup, where it is atomic with
   the first read).
6. **New ids keep the engine's text** (`PoolIds.derived`, domain change above).
7. **No full sweep of allocated host ids at startup**: only the discarded-transaction check (now pool-aware) and the
   released-after-write path delete ids. A sweep over `AppWidgetHost.getAppWidgetIds` would also have to know every
   Workspaces widget slot; left out rather than risk a live widget.
8. **The backup marker `homeFromPool`** distinguishes a cut-over backup's projection from an older backup's layouts.

### Tests (app half)

Second review fixes: `PoolHomeSyncTest` adds `holdsHome` false for a pool migrated from a stripped set and
`PoolHomeSync.open` keeping the pool while adding a fallback session's item; `PoolHomeStartupTest` adds an undecodable
pool with an unreadable snapshot (nothing migrated or written), an unreadable `HomeLayoutSet` store (legacy on
defaults, nothing written), a clean start not flagged damaged, and a fallback session's added app and widget kept next
to a pool item newer than the snapshot (also after a further restart); `PoolHomeLayoutRepositoryTest` adds the snapshot
catching up with another writer's pool write.

Review fixes (PR #1431): `PoolHomeSyncTest` adds the resync hard guard, an edit on a stale projection not re-creating a
removed item, and a restore reporting dropped host ids; `PoolHomeStartupTest` adds an unreadable snapshot (never cut
over, pool kept), unreadable snapshot plus unreadable pool (legacy, nothing written), a lost marker over a stripped set
(pool kept, snapshot rewritten), an undecodable pool (quarantined, rebuilt from the snapshot), and a pool read failure
(legacy from the snapshot's full set, clean cutover next start); `PoolHomeLayoutRepositoryTest` adds the first edit after a
fresh install surviving a restart, the snapshot refreshed after a pool write, a stale home commit not reverting a preview
removal, a restore releasing dropped widgets, and release after another writer's flush; `PoolEditControllerTest` adds a
session ending on an outside change without writing its stale pool, and one that keeps going when nothing changed.

* Domain `PoolHomeSyncTest` (18): projection at startup equals the legacy layout (ids modulo the migration prefix, dock,
  Library edge); landscape shows the phone arrangement; the stored set holds no placed items and keeps page slots,
  All-apps pages, selection and dock settings; a restart (pool through its codec, the stored set) projects the same home;
  move (stored set unchanged, landscape key re-projected), add, folder rename/create, widget add/resize/remove (host id
  released only at the last reference), removing a widget a fork shows releases nothing, page add/reorder/delete, dock
  pins/settings/panel/Library edge, Library relayout stable, selection and edit mode do not touch the pool, active key
  wins over a stale sibling, a new device class is adopted, uninstall pruning (every workspace, dock, folder entries,
  widgets kept, other profile untouched), pre-cutover backup restore keeps other arrangements, cut-over backup restore
  takes host ids back, resync idempotent. `ArrangementIngestTest` and `PoolHomeProjectionTest` updated for
  `PoolIds.derived`.
* App JVM `PoolHomeStartupTest` (6): first cutover (pool written, then snapshot), parity of the first projection and the
  stripped store, a failing pool write keeps the legacy path untouched, the legacy flag path reads and writes no pool,
  hand-back and cutting over again, a lost pool re-imported from the snapshot. `PoolHomeLayoutRepositoryTest` (8): an edit
  is in memory, in the pool after a flush and after a restart, and does not write `HomeLayoutSet`; an unflushed edit is
  not durable (the restart shows the last written pool); released host ids only after the write and only unreferenced
  ones; a fork's widget is never released; uninstall; a preview edit is signalled and reprojected; an older backup is
  imported; a new widget keeps its engine id.

### Verification (app half)

`./gradlew verify` was **not** run (dl.google.com is blocked). In a scratch Gradle project on Maven Central: the whole
`:core:domain` suite (1859 tests), the Android-free app files (`CachedPoolRepository`, `PoolHomeLayoutRepository`,
`PoolHomeStartup`, `PoolEditController`) with their JVM tests and the existing `CachedPoolRepository*` and `PoolEditController` tests (69 tests), ktlint on domain main
and test, those app files and every other changed app file, detekt over the whole domain and app trees (main and test).
The Android wiring (`DataStoreHomeLayoutStore`, `MainActivity*`, the view model, Compose settings row, preview host) was
not compiled locally; CI runs `verify deviceVerify`.

## Not done

* Per-workspace homes on the standard home (it shows the migrated workspace of the shown mode until S9); fork on first
  edit is used by the preview only.
* A startup sweep of all allocated widget host ids (decision 7).
* An asynchronous startup with a loading state (the existing single blocking read is kept).
* Removing `HomeLayoutSet`'s `"homeLayouts"` from backups (kept so older builds can restore).
* The app's `PoolEditText` still has a message for `WIDGET_ALREADY_PLACED` (harmless, never shown now).
* Per-workspace dock overrides (`DockPresentation`, Q17).
* Deleting the legacy path and the switch (next release, with S9).

## Owner device checklist

**Before installing: Settings > Backup > Export, keep the file.** Then install and open Riffle once.

- [ ] Home, drawer, dock and Settings are reachable. Every icon, folder and widget is where it was; the dock has the
      same pins (and panel, if you use it).
- [ ] Force-stop Riffle and reopen: everything is still in place. Reboot the phone: still in place.
- [ ] Move an icon, force-stop, reopen: it stays moved.
- [ ] Add a widget, move it, resize it, remove it. Add another and leave it; force-stop and reopen: it is there and live.
- [ ] Create a folder (drop an app on an app), rename it, take an app out; force-stop and reopen.
- [ ] Add a Home page, reorder pages, delete a page with a widget on it.
- [ ] Pin and unpin an app in the dock, reorder pins, change a dock setting (icon size, edge, Library's edge).
- [ ] Library: install an app, it appears; uninstall it, it disappears from Home, the dock and any folder.
- [ ] Rotate, and fold/unfold if you have a foldable: you stay on your page; the landscape phone shows the same items as
      portrait.
- [ ] Workspaces (preview) on: its home pages and dock show the same items; "Refresh home items" is gone; an edit in the
      preview's editor shows on the standard home after you leave the preview.
- [ ] Backup export, then restore it: everything comes back, widgets live.
- [ ] Settings > Developer > Home from placed items off, restart Riffle: everything is still there (legacy path). On
      again, restart: still there.
- [ ] Report anything wrong with the steps and a screenshot; do not paste item content.
