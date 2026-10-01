# Workspaces: the shared placed-items pool (WS10 S3, as built)

Status: **domain only, not wired**. Slice S3 of `docs/product/workspaces-configuration.md` section 9 (owner decisions N2, N9,
N12, N13). Nothing in `app/` uses it, no runtime behaviour changes, and `HomeLayoutSet` is still the source of truth for
the home screen. Code: `core/domain/.../launcher/workspace/pool/`. Tracking: #1363, slice issue #1383.

## What exists

| Piece | Where | Notes |
| --- | --- | --- |
| Model | `PoolModel.kt` | `PoolItem` = `PoolApp` (app or shortcut), `PoolFolder` (value `FolderEntry`s), `PoolWidget` (provider, nullable host id). `Placement`, `ArrangementPage`, `Arrangement`, `PlacedItemPool`. |
| Derived references, GC, results | `PoolResults.kt` | `PoolReferences` (counts are computed, never stored), `PoolGc`, `PoolEdit`, `PoolRejection`, `HostIdDeletionQueue`. |
| Engine adapter | `ArrangementAdapter.kt`, `ArrangementIngest.kt` | Joins an arrangement to `LauncherPage` for the existing engines; ingest maps an edited page back and remaps ids. |
| Operations | `PoolPlacement`, `PoolRemoval`, `PoolCopy`, `PoolWidgets`, `PoolNewApps` | Pure, return `PoolResult` (`Done(PoolEdit)` or a typed `Rejected`). |
| Validation | `PoolValidation.kt` | `repair` re-establishes the invariants on outside data and reports what it dropped. |
| Migration | `PoolMigration.kt` | `HomeLayoutSet` to one pool per device class. |
| Codec | `PoolCodec.kt` (+ item encode/decode) | Additive `pool` entry per layout in the workspace set blob. |
| Hook into existing types | `LayoutWorkspaces.pool` (default empty), `WorkspaceSetCodec` | Schema version 1 to 2. An empty pool is not written, so existing blobs are byte-identical. |

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
5. **Widget rule.** A widget has at most one placement in the layout and a host id is held by one widget. `placeExisting`
   refuses a second placement (`WIDGET_ALREADY_PLACED`); `moveToWorkspace` moves the single placement and keeps the id;
   `PoolWidgets.separateCopy` is the explicit "separate copy" (new pool id, same provider, new host id or a placeholder;
   refused when the provider is unknown).
6. **Folders are shared only by explicit action.** `placeExisting` refuses a folder; `PoolPlacement.shareFolder` shares one;
   `PoolCopy.makeFolderIndependent` splits it again. Apps are shared freely (once per arrangement).
7. **Copies clone** (`CopyMode.CLONE`); `KEEP_SHARED` references apps and folders but never widgets. Widgets are always
   unbound placeholders (same cell, span, provider). `duplicateWorkspace`, `copyArrangement` and `copyFromOtherLayout`
   (items two copied workspaces shared stay shared in the target; the target pool is replaced and its widget host ids
   released, deferred).
8. **New apps (N9/N12).** `PoolNewApps.placeNewApp` creates ONE `PoolApp` and references it from the first free cell of the
   first page with room in every `HOME_AND_FINDER` arrangement. Idempotent per event; if nothing is placed the pool is
   unchanged. It never creates pages ("pages appear as you fill them" is arrangement-only and not built).
9. **Uninstall.** `PoolRemoval.pruneUninstalled(pool, packageName, profile)` is for a confirmed package-removed event only: it
   drops apps and folder entries of that package and profile and any folder left empty. Widgets of the package and dock pins
   are not touched.
10. **Engines are reused, never reimplemented.** Bounds, collisions and first-free-cell come from `GridPlacementEngine`
    (`placeItem`, `placeItemInFirstAvailableCell`, `moveItem`), run on `LauncherPage` values built by the adapter.
11. **Id collisions.** Engines mint ids from a per-arrangement ordinal (`HomeShortcutEngine`). `ArrangementIngest` treats every id
    that was not in the adapted input as new and assigns a fresh pool-unique id; it never trusts an engine id as pool-global.
    Edits to an existing folder or widget write back to the single pool item (visible everywhere it is placed, by design).
12. **Invariants** (held by every operation, re-established by `PoolValidation.repair`): every placement resolves; an item is
    placed at most once per arrangement; a widget is placed once per layout and a host id is held once; placements are in
    bounds and do not collide; page ids are unique per arrangement; no item is left without a reference. Repair keeps the
    first occurrence in workspace, page and placement order, and is idempotent.

## Migration

`PoolMigration.migrate(HomeLayoutSet)` returns a pool per device class plus issues. Each stored mode layout becomes the
arrangement of its workspace (`ws:<deviceclass>:<mode>`, the ids `WorkspaceMigration` uses). Only `Home` pages carry placed
items (generated and All-apps pages are lens pages). `PoolItemId` is `pi:<deviceclass>:<mode>:<LauncherItemId>`, so the
function is deterministic (run twice, equal) and nothing is shared across modes. A repeated `LauncherItemId` gets a `#n`
suffix. Widgets get `provider = null` (a later app-layer backfill) and host id 0 becomes an unbound placeholder. Unplaced
items, colliding placements and duplicate host ids are dropped and reported by kind and id, never labels.
`PoolMigration.homePages(pool, deviceClass, mode)` is the inverse used for the one-time check: it equals the original Home
pages for well-formed data (golden fixtures with apps, a folder, a widget, a pinned second page, All apps and a generated page,
plus a seeded property test over layouts built with `GridPlacementEngine`). The dock is not in the pool and `HomeLayoutSet` is
only read.

## Codec

`PoolCodec` writes `{items, arrangements}` as the `pool` entry of a layout. Decoding never throws: a missing or unreadable
value is an empty pool, undecodable items and arrangements are skipped, then `PoolValidation.repair` drops dangling
references (reported in `PoolDecodeResult.issues`). `WorkspaceSetCodec` additionally drops arrangements of workspaces that
are not stored. A version 1 blob decodes with an empty pool. A seeded fuzz test corrupts the encoded value and asserts the
result is always a valid pool.

## Tests

`PoolPlacementTest`, `PoolRemovalTest`, `PoolCopyTest`, `PoolWidgetsAndNewAppsTest`, `ArrangementIngestTest`,
`PoolMigrationTest`, `PoolCodecTest`, and `PoolInvariantsPropertyTest` (80 seeds x 120 random operations covering every
operation; after each step an independent invariant check, repair finds nothing, and every host id that left the pool is in
`releasedHostIds` and no released id is live).

## Not done

* **App wiring.** Nothing constructs a pool at runtime; `LayoutWorkspaces.pool` is always empty. No bootstrap, repository,
  or backup integration.
* **Home-surface rendering and the HomeLayout adapter.** The page-level adapter exists; the `HomeLayout`-level wrapper
  (settings, dock, selected page, edit mode) belongs with the wiring.
* **Sharing UI** (N13): "Also show in...", "Keep items shared", "Add a separate copy". The operations exist.
* **Uninstall observer.** The package-removed event source is not hooked up; dock pins and widgets of a removed package are
  not pruned.
* **Provider backfill** for migrated widgets, restore-time rebinding (`isHostedWidgetBound`), and startup reconciliation of
  leaked host ids.
* **Pool-aware `LayoutWorkspaces.remove`/`duplicate`.** Those still behave as before and ignore the pool. Use
  `PoolRemoval.deleteWorkspace` and `PoolCopy.duplicateWorkspace` alongside them when wired; decode drops stranded arrangements.
* **Auto-paging, `Workspace` field for the new-apps setting, per-workspace dock overrides.**
