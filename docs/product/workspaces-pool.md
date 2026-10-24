# Workspaces: the shared placed-items pool (WS10 S3, as built)

Status: **domain, now wired read-only behind the preview switch by S4 (see [`workspaces-pool-cutover.md`](workspaces-pool-cutover.md))**. The text below describes the S3 state, updated for the S4 domain changes (widget rule, dock in the pool, fork on first edit; see [`workspaces-pool-cutover-s4.md`](workspaces-pool-cutover-s4.md)). Slice S3 of `docs/product/workspaces-configuration.md` section 9 (owner decisions N2, N9,
N12, N13). Nothing in `app/` uses it, no runtime behaviour changes, and `HomeLayoutSet` is still the source of truth for
the home screen. Code: `core/domain/.../launcher/workspace/pool/`. Tracking: #1363, slice issue #1383.

## What exists

| Piece | Where | Notes |
| --- | --- | --- |
| Model | `PoolModel.kt`, `PoolDock.kt` | `PoolItem` = `PoolApp` (app or shortcut), `PoolFolder` (value `FolderEntry`s), `PoolWidget` (provider, nullable host id). `Placement`, `ArrangementPage`, `Arrangement`, `PlacedItemPool` (items, arrangements, `dock`). `PoolDock` (pins, panel page, `PoolDockSettings`). |
| Fork on first edit | `PoolArrangements.kt` | `fork` gives a workspace its own arrangement copied from the one it borrows. |
| Standard-home projection | `PoolHomeProjection.kt` | `project` / `ingest` between the pool and a `HomeLayout` (S4 cutover). |
| Derived references, GC, results | `PoolResults.kt` | `PoolReferences` (counts are computed, never stored), `PoolGc`, `PoolEdit`, `PoolRejection`, `HostIdDeletionQueue`. |
| Engine adapter | `ArrangementAdapter.kt`, `ArrangementIngest.kt` | Joins an arrangement to `LauncherPage` for the existing engines; ingest maps an edited page back and remaps ids. |
| Operations | `PoolPlacement`, `PoolRemoval`, `PoolCopy`, `PoolWidgets`, `PoolNewApps` | Pure, return `PoolResult` (`Done(PoolEdit)` or a typed `Rejected`). |
| Validation | `PoolValidation.kt` | `repair` re-establishes the invariants on outside data and reports what it dropped. |
| Migration | `PoolMigration.kt` | `HomeLayoutSet` to one pool per device class. |
| Codec | `PoolCodec.kt` (+ item encode/decode) | Additive `pool` entry per layout in the workspace set blob. |
| Hook into existing types | ~~`LayoutWorkspaces.pool`~~ (removed: the pool store owns placed items), `WorkspaceSetCodec` | Schema version 1 to 2. A legacy `pool` key in a workspace blob is ignored on decode. |

## Decisions

1. **Arrangements live in the pool, not in `Workspace.pages`.** The design sketch put a `PlacedItemsPage` in the page list. That
   would add a `PageHost` variant and break every exhaustive `when` over `PageHost` in code that cannot be compiled in this
   sandbox. `PlacedItemPool.arrangements` is keyed by `WorkspaceId` instead; pool and arrangements are still one immutable
   value, so Undo is still "restore the previous value". Arrangements hold **home pages only** (no page type field).
2. **`NewAppPlacement` is stored on the `Arrangement`** (default `FINDER_ONLY`) rather than on `Workspace`, for the same reason
   (no edits to `Workspace` or `WorkspaceCodec`). Moving it to `Workspace` later is a mechanical change.
3. **Reference counts are derived** (`PoolReferences.index`), so they cannot drift. Every operation finishes with
   `PoolGc.finish(before, after)`, which collects zero-reference items and describes the change.
4. **Deferred widget deletion.** A collected widget's host id goes to `PoolEdit.releasedHostIds` (never to the pool). The app
   layer holds them in a `HostIdDeletionQueue` and calls `deleteHostedWidgetId` only after the Undo window; after an Undo,
   `withoutReferenced(restoredPool)` forgets ids that are live again. An id still held by a surviving widget is never released.
5. **Widget rule (changed in S4, owner decision 2026-10-24).** A widget is ONE platform instance (host id and
   configuration). Only one workspace is shown at a time, so the same instance may be placed in any number of
   arrangements, at most once per arrangement; its configuration is shared. `placeExisting` shares it like an app
   (`ALREADY_IN_ARRANGEMENT` for a second placement in one arrangement; `WIDGET_ALREADY_PLACED` is no longer produced);
   `moveToWorkspace` moves one placement and keeps the id; a host id is still held by one pool widget, and is released
   only when no arrangement and no dock references the instance. `PoolWidgets.separateCopy` stays the explicit "separate
   copy" for a divergent configuration (new pool id, same provider, new host id or a placeholder; refused when the
   provider is unknown).
6. **Folders are shared only by explicit action.** `placeExisting` refuses a folder; `PoolPlacement.shareFolder` shares one;
   `PoolCopy.makeFolderIndependent` splits it again. Apps are shared freely (once per arrangement).
7. **Copies clone apps and folders, and share widget instances.** `CopyMode.CLONE` (default) clones apps and folders;
   `KEEP_SHARED` references apps and folders; `FORK` (fork on first edit) references apps and clones folders. Every
   in-layout copy references the same widget instance (no placeholders). `copyFromOtherLayout` cannot share across
   pools, so its widgets are still unbound placeholders (same cell, span, provider); items two copied workspaces shared
   stay shared in the target; the target's arrangements are replaced (its dock is kept) and replaced widgets' host ids
   released, deferred.
8. **New apps (N9/N12).** `PoolNewApps.placeNewApp` creates ONE `PoolApp` and references it from the first free cell of the
   first page with room in every `HOME_AND_FINDER` arrangement. Idempotent per event; if nothing is placed the pool is
   unchanged. It never creates pages ("pages appear as you fill them" is arrangement-only and not built).
9. **Uninstall.** `PoolRemoval.pruneUninstalled(pool, packageName, profile)` is for a confirmed package-removed event only: it
   drops apps and folder entries of that package and profile (on home pages and in the dock) and any folder left empty.
   Widgets of the package are not touched (unbind them, `PoolWidgets.unbind`).
10. **Engines are reused, never reimplemented.** Bounds, collisions and first-free-cell come from `GridPlacementEngine`
    (`placeItem`, `placeItemInFirstAvailableCell`, `moveItem`), run on `LauncherPage` values built by the adapter.
11. **Id collisions.** Engines mint ids from a per-arrangement ordinal (`HomeShortcutEngine`). `ArrangementIngest` treats every id
    that was not in the adapted input as new and assigns a fresh pool-unique id; it never trusts an engine id as pool-global.
    Edits to an existing folder or widget write back to the single pool item (visible everywhere it is placed, by design).
12. **Invariants** (held by every operation, re-established by `PoolValidation.repair`): every reference resolves; an item is
    referenced at most once per arrangement and at most once by the dock; a host id is held by one pool widget; an item the
    dock references is referenced by no arrangement (`DOCK_ITEM_IN_ARRANGEMENT`, `ITEM_IN_DOCK`); placements are in bounds
    and do not collide; page ids are unique per arrangement; no item is left without a reference. Repair handles the dock
    first, then keeps the first occurrence in workspace, page and placement order, and is idempotent.
13. **Dock in the pool (S4).** `PlacedItemPool.dock` is the layout's one dock (pins are never per workspace, Q17): `pins`
    reference pool items in order, `panel` is the optional dock panel page, `settings` carries every `DockModel` field
    except items and panel, plus Library's edge. References and GC count the dock (`PoolReferences.inDock`, `referenced`,
    `count`), `removeEverywhere` and uninstall pruning reach it, and the codec writes it as an optional `dock` key.
14. **Fork on first edit (S4).** `PoolArrangements.fork(pool, workspace, candidates, ids)` gives a workspace that borrows an
    arrangement (`PoolHomeView.candidates`) its own copy: apps and widget instances by reference, folders cloned, pages copied
    with their ids, the new-app setting copied. Idempotent (a workspace with an arrangement is unchanged);
    `NOTHING_TO_FORK` when no candidate has pages.

## Migration

`PoolMigration.migrate(HomeLayoutSet)` returns a pool per device class plus issues. Each stored mode layout becomes the
arrangement of its workspace (`ws:<deviceclass>:<mode>`, the ids `WorkspaceMigration` uses). Only `Home` pages carry placed
items (generated and All-apps pages are lens pages). `PoolItemId` is `pi:<deviceclass>:<mode>:<LauncherItemId>`, so the
function is deterministic (run twice, equal) and nothing is shared across modes. A repeated `LauncherItemId` gets a `#n`
suffix. Widgets get `provider = null` (a later app-layer backfill) and host id 0 becomes an unbound placeholder. Unplaced
items, colliding placements and duplicate host ids are dropped and reported by kind and id, never labels.
`PoolMigration.homePages(pool, deviceClass, mode)` is the inverse used for the one-time check: it equals the original Home
pages for well-formed data (golden fixtures with apps, a folder, a widget, a pinned second page, All apps and a generated page,
plus a seeded property test over layouts built with `GridPlacementEngine`). The device class's shared dock (`dockFor`, plus
Library's edge) becomes the pool's dock (`PoolMigration.importDock`, ids `pi:<deviceclass>:dock:<LauncherItemId>`).
`HomeLayoutSet` is only read.

## Codec

`PoolCodec` writes `{items, arrangements}` as the `pool` entry of a layout. Decoding never throws: a missing or unreadable
value is an empty pool, undecodable items and arrangements are skipped, then `PoolValidation.repair` drops dangling
references (reported in `PoolDecodeResult.issues`). `WorkspaceSetCodec` additionally drops arrangements of workspaces that
are not stored. A version 1 blob decodes with an empty pool. A seeded fuzz test corrupts the encoded value and asserts the
result is always a valid pool.

## Tests

`PoolPlacementTest`, `PoolRemovalTest`, `PoolCopyTest`, `PoolWidgetsAndNewAppsTest`, `ArrangementIngestTest`,
`PoolMigrationTest`, `PoolCodecTest`, `PoolDockTest`, `PoolArrangementsTest`, `PoolHomeProjectionTest`, and
`PoolInvariantsPropertyTest` (80 seeds x 120 random operations covering every operation, including fork and projected home and
dock edits; after each step an independent invariant check, repair finds nothing, and every host id that left the pool is in
`releasedHostIds` and no released id is live).

## Not done

* **App wiring.** Since built: the pool lives in its own store (`riffle_pool`, see
  [`workspaces-pool-cutover.md`](workspaces-pool-cutover.md)) and is backed up as its own section
  ([`workspaces-backup.md`](workspaces-backup.md)). The `LayoutWorkspaces.pool` field this slice added was never populated
  and has been removed.
* **Home-surface rendering and the HomeLayout adapter.** The page-level adapter exists; the `HomeLayout`-level wrapper
  (settings, dock, selected page, edit mode) belongs with the wiring.
* **Sharing UI** (N13): "Also show in...", "Keep items shared", "Add a separate copy". The operations exist.
* **Uninstall observer.** The package-removed event source is not hooked up; widgets of a removed package are not unbound.
* **Provider backfill** is done (S4 read-only); restore-time rebinding (`isHostedWidgetBound`) is not. Startup reconciliation
  has its pure half (`PoolHostIds.referencedHostIds`, `PoolHostIds.unreferenced`); the app call is not wired.
* **Pool-aware `LayoutWorkspaces.remove`/`duplicate`.** Those still behave as before and ignore the pool. Use
  `PoolRemoval.deleteWorkspace` and `PoolCopy.duplicateWorkspace` alongside them when wired; decode drops stranded arrangements.
* **Auto-paging, `Workspace` field for the new-apps setting, per-workspace dock overrides.**
