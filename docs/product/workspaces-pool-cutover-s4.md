# Workspaces: pool cutover, domain half (WS10 S4, as built)

Status: **domain only (`:core:domain`), not wired.** Nothing in `app/` calls the new API yet and no runtime behaviour
changes; the app wave wires the standard home onto it. This slice makes it possible for the standard home to render and
edit FROM the placed-items pool through a projection, so `HomeLayoutSet` stops being written for placed items and the
dock. Builds on [`workspaces-pool.md`](workspaces-pool.md) (pure pool), [`workspaces-pool-cutover.md`](workspaces-pool-cutover.md)
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

## Not done

* **App wiring** (next wave): render the standard home through `project`, route engine edits through `ingest`, stop writing
  `Home` pages and the dock to `HomeLayoutSet`, fork on first edit, settings UI writing dock settings (and Library's edge) to
  the pool, the startup reconciliation call, the uninstall event source.
* The app's `PoolEditText` still has a message for `WIDGET_ALREADY_PLACED` (harmless, never shown now).
* N10 (folding `PHONE_LANDSCAPE` into `PHONE`) is a separate slice; the dock import follows whatever device classes the
  migration and the layout set yield.
* Per-workspace dock overrides (`DockPresentation`, Q17) are not part of this slice.

## Owner device checklist

Nothing to check on a device for this slice: it changes no runtime behaviour. The checklist for the cutover comes with the
app wave.
