# Workspaces: pool cutover, read-only (WS10 S4, as built)

Status: **wired behind the preview switch, read-only**. Slice S4 of `docs/product/workspaces-configuration.md` section 9,
building on the pure pool domain in [`workspaces-pool.md`](workspaces-pool.md). With **Settings > Developer > Workspaces
(preview)** on, the preview's home pages show the user's real home items (apps, shortcuts, folders, widgets) read from the
placed-items pool. With the switch off nothing here is constructed, read or written. Tracking: #1363.

## What exists

| Piece | Where | Notes |
| --- | --- | --- |
| Store state and codec | `core/domain/.../pool/PoolStore.kt` | `PoolStoreState(migrated, pools by device class)`; `PoolStoreCodec` over `StoredValue`, version 1. Never throws: wrong shape or a future version reads as "not stored"; an unknown device class or undecodable pool is skipped; each pool goes through `PoolCodec.decode` (repair). |
| Cutover | `.../pool/PoolCutover.kt` | `ensureMigrated(stored, layoutSet, providerOf)`: pure, idempotent, flag-guarded; `reimport(...)`: fresh import; `PoolWidgetBackfill`. |
| Home view | `.../pool/PoolHomeView.kt` | Pure: which arrangement and page a `home.grid` page shows. |
| Store | `app/.../launcher/pool/DataStorePoolStore.kt`, `PoolStoreJsonCodec.kt` | Own DataStore file `riffle_pool`, one JSON blob, `org.json` bridge over the domain codec (same shape as the workspace and exclusion stores). |
| Repository | `app/.../launcher/pool/CachedPoolRepository.kt` | `PoolStorePort`; `initialize`, `reimport`, `pool(deviceClass)`, `version`. |
| Runtime holder | `app/.../launcher/pool/PoolRuntime.kt` | Repository, icon loader, widget view factory, tap actions, provider lookup. Built by `MainActivityDependencies.poolRuntime()` only when the preview opens. |
| Page host | `app/.../launcher/pool/PoolHomePage.kt` | `PlacedHomeContent` + `PoolHomePage`, a read-only grid. |
| Tap actions | `app/.../launcher/pool/PoolItemActions.kt` | App: `AndroidAppLauncher.launch`; shortcut: `launchShortcut` (same launchers as the standard home). |
| Provider lookup | `WidgetHostGateway.hostedWidgetProvider` (default null), `AndroidWidgetHostGateway` | Read-only: `AppWidgetManager.getAppWidgetInfo(id).provider`. |
| Preview wiring | `WorkspacePreviewHost` (`pool` param), `WorkspacePreviewSurface` (`placedHome` param), `MainActivity`, `MainActivityDependencies` | Additive. A surface without `placedHome` behaves exactly as before (placeholder). |

## How it behaves

1. **Nothing runs with the preview off.** `MainActivity` builds the host (and so `poolRuntime`) lazily and only the preview
   content calls `initialize`, so with the switch off, or on but never opened, no pool storage is touched.
2. **First open.** `CachedPoolRepository.initialize(layoutSet)` reads `riffle_pool`, runs `PoolCutover.ensureMigrated`
   (`PoolMigration.migrate` plus widget provider backfill) and writes the result once, with `migrated = true`.
3. **Later opens and restarts.** The flag is found, nothing is migrated, nothing is written. Running the migration twice is
   `==` the first run (tested), and `HomeLayoutSet` is never modified or deleted, only read.
4. **Which items a page shows.** A `home.grid` page (`GroupKeyIs(<page id>)`) shows, in order: the active workspace's own
   arrangement if it has one; else the migrated arrangement of the layout the user actually has: the shown mode, then
   Library, Standard, Cards. In that arrangement the page named by the lens, else the first page. Preset workspaces
   (`preset:nova:...`) have no arrangement yet, so on the owner's phone they show the migrated Library home.
5. **Rendering.** `HomeGridLayoutMetrics` sizes the cell, `LauncherAppIcon`, `FolderPreviewIcon` and
   `WallpaperReadableLabel` draw items with the user's own label settings (icon size, label text), exactly like the
   standard home. No drag, no edit mode, no context menus. A folder tap opens a read-only dialog listing its apps; tapping
   one launches it. Widgets draw the hosted view through the existing `HomeWidgetViewFactory` when the host id is bound, else
   a "Widget placeholder" tile with the label.
6. **Refresh home items** (preview top bar, only when a pool is present): `reimport` replaces the pool with a fresh import
   of the current `HomeLayoutSet`. See decision 3.
7. **Privacy.** The store holds apps, folders, widget providers and geometry. Item content (notification text, calendar
   titles, feed articles) is not part of the pool model and is never persisted or logged by this slice. A test asserts the
   encoded blob has no notification text.

## Decisions (please confirm)

1. **Separate store, not the workspace blob.** The pool lives in its own DataStore (`riffle_pool`), not in
   `LayoutWorkspaces.pool` inside `riffle_workspaces`, so resetting or restoring workspaces cannot lose placed items and a
   corrupt pool blob cannot take the workspace set down. `LayoutWorkspaces.pool` and its codec (S3) stay unused; they can be
   retired or become the backup shape later.
2. **A one-time snapshot, with one flag for all device classes.** The flag is per store, not per class. A class that gets a
   layout in `HomeLayoutSet` later (for example the first time the phone is unfolded) is not imported unless Refresh is used.
3. **Refresh home items.** Until pool editing exists, the standard home is still edited in `HomeLayoutSet`, so a snapshot goes
   stale as soon as the user rearranges Home. Rather than ship a preview that silently lies, a small explicit action
   re-imports. It replaces the pool wholesale (nothing in the pool is user-edited yet). Remove it when editing lands.
4. **Preset workspaces borrow the migrated arrangement.** Instead of creating an arrangement per preset workspace, the
   preview resolves "the real home" (shown mode first). That keeps the pool a faithful copy of `HomeLayoutSet` and avoids
   inventing sharing. Per-workspace arrangements arrive with pool editing.
5. **Widget provider backfill from the host id**, via `AppWidgetManager.getAppWidgetInfo(id).provider`, done once during
   the migration. The provider's profile is assumed personal (the binding target does not carry it), so a work-profile
   widget gets a personal-profile identity: harmless while rendering, but `PoolWidgets.separateCopy` could bind the wrong
   profile. Revisit before copy exists. A widget whose id the platform cannot resolve keeps `provider = null` and renders
   its hosted view if the host still has one, else a placeholder.
6. **Own read-only grid instead of `WorkspaceGrid`.** The existing `WorkspaceGrid` is wired to drag sessions, edit state
   and shell actions. A small read-only grid reusing the same sizing and item primitives is lower risk than threading inert
   versions of all that state through, and does not touch the standard home.
7. **Initialise when the preview opens**, not when the setting turns on, so a switched-on but unopened preview reads nothing.
8. **Storage failure policy** mirrors `CachedWorkspaceRepository`: a read that throws disables persistence for the process
   (never overwrite what is on disk); a blob that cannot be decoded reads as "not stored" and is re-derived from
   `HomeLayoutSet`.

## Tests

* Domain (`core/domain`): `PoolCutoverTest` (first run, idempotent second run, flag wins over a changed layout set,
  `HomeLayoutSet` untouched, stored non-empty pools survive, provider backfill, reimport), `PoolStoreCodecTest` (round trip,
  wrong shapes, future version, unknown device class, dangling reference, 300-seed fuzz asserting valid pools), `PoolHomeViewTest`
  (preset fallback, page choice, active arrangement wins, empty pool).
* App JVM (`app`): `CachedPoolRepositoryTest` (null before init, migrate-persist-publish, second process does not write,
  read failure never writes, write failure tolerated, reimport), `PoolStoreJsonCodecTest` (round trip, malformed text, truncated
  blobs, launcher data only), `PoolItemActionsTest`.
* Screenshots (`PoolHomePreviewScreenshotTest`, Roborazzi, CI): real items compact, unfolded, dark; empty notice; the old
  placeholder without a pool; tapping an app, a folder (dialog lookups use `hasAnyAncestor(isDialog())`) and Refresh.
* The author could not run `./gradlew verify` (dl.google.com blocked). The domain and Compose-free app tests, ktlint and detekt
  were run in a scratch Gradle project on Maven Central only; the Compose code, Roborazzi tests and the Android build were not
  compiled locally and first run in CI.

## Not done

* **Editing.** No drag, place, remove, copy or share in the pool; `Pool*` operations remain unused by the app.
* **Dock.** Not in the pool; the preview dock is still a placeholder.
* **Per-workspace arrangements** for presets, auto-paging, `NewAppPlacement` setting, new-app placement, uninstall pruning
  (`PoolRemoval.pruneUninstalled` has no event source), startup host-id reconciliation.
* **Live sync with `HomeLayoutSet`.** One-time import plus manual Refresh only (decision 3).
* **Pool in backup and restore.** Another agent owns the backup package; the pool store is not exported or restored yet.
  After a restore of `HomeLayoutSet`, use Refresh home items.
* **Widget interaction.** Widgets are drawn, not configurable or resizable; unbound widgets are placeholders.
* **Notification badges** on icons and the app context menu / shortcuts popup.
* **Work-profile widget provider profile** (decision 5).

## Owner device checklist

Take a backup export first (the app's existing backup export). The preview is off by default; nothing below changes your standard home.

- [ ] Settings > Developer: turn **Workspaces (preview)** on, open it. The first page shows your real Home items: same apps
      in the same cells, your folders, your widgets as tiles (live view if bound, else "Widget placeholder").
- [ ] Icons and labels match the standard home's icon size and label settings; nothing draws under the status bar, cutout or
      navigation bar.
- [ ] Tap an app: it launches. Return to the preview. Tap an app with a shortcut-launched entry if you have one.
- [ ] Tap a folder: a list of its apps opens; tapping one launches it; Close dismisses.
- [ ] Swipe to the next page (if you have a second Home page) and to the Finder page; paging still works.
- [ ] Rearrange Home in the standard launcher (for example move an icon), reopen the preview: it still shows the old
      arrangement (expected). Tap **Refresh home items**: it now matches.
- [ ] Force-stop and reopen the app, open the preview again: items are still there and appear immediately (no re-import).
- [ ] Rotate, and fold/unfold if you have a foldable: the right device class's items show (a class you never edited shows the
      default layout, or the "No home items found" notice).
- [ ] TalkBack: each icon is announced with its name and "Open <name>"; the folder dialog is readable.
- [ ] Turn the switch off: Home, drawer, dock and settings behave as before; the standard home is unchanged.
- [ ] Report anything that looks wrong with the device, the steps and a screenshot. Do not paste item content.
