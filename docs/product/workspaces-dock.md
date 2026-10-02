# Workspaces: the preview dock, read-only (WS10, as built)

Status: **wired behind the preview switch, read-only**. Replaces the dock placeholder in the Workspaces (preview).
With **Settings > Developer > Workspaces (preview)** off nothing here runs. Tracking: #1363, slice #1408.

## What exists

| Piece | Where | Notes |
| --- | --- | --- |
| Pure rules | `core/domain/.../workspace/dock/PreviewDock.kt` | `PreviewDock.from(DockModel?)` gives a `PreviewDockModel`: pinned apps and folders in dock order (duplicates by id dropped), hosted widgets counted in `unsupportedCount` and not drawn, icon size and spacing clamped to the documented ranges, `isShown` from `DockModel.isEnabled`. Also the bar height, the 48dp target size, and the spoken and action labels. |
| Dock content | `app/.../launcher/workspace/WorkspacePreviewDock.kt` | `PreviewDockContent`: the pinned run (horizontal scroll if it does not fit) and the dynamic section in one row; a read-only folder list dialog. |
| Wiring | `WorkspacePreviewSurface` (`dockPins` param, `DockBar`), `WorkspacePreviewHost` (one `remember` line) | Additive. `dockPins = null` or no pool keeps the old placeholder exactly. |
| Menu action | `workspaceMenuActionModifier` in `HomeDockPullBinding.kt` (made `internal`, nothing else changed) | The dock bar carries the same "Workspace menu" custom action as the standard dock. |

## How it behaves

1. **Pinned items.** The shared dock of the active device class (`HomeLayoutSet.dockFor`), drawn with `LauncherAppIcon`
   and `FolderPreviewIcon` at the user's dock icon size and item spacing, like the standard dock. Tapping an app launches
   it through the same `PoolItemActions` as the home page; a folder opens a read-only list and tapping an entry launches it.
2. **Dynamic section.** When the workspace has `dock.dynamicSection` it is drawn by `LensBoundExpression`, the same pipeline
   as pages, taking half the run beside the pins (all of it when nothing is pinned). Without one, only the pins show.
3. **Nothing pinned.** An honest note ("Nothing pinned to the dock"); a hidden dock (`isEnabled = false`) draws no pins and
   says "Dock is hidden in Settings", but keeps the bar so the menu handle stays anchored.
4. **Menu.** The existing `WorkspaceMenuLayer` handle is unchanged and anchored to the (now variable) bar height. The dock
   bar also offers the TalkBack custom action **Workspace menu**. **No gesture was added**: the dock carries no drag, long
   press or expand handler, so there is nothing to arbitrate with the pager or the menu (ADR 0002). The dock pull itself is
   a standard-home wiring (`HomeDockPullBinding`) and is not part of the preview.
5. **Accessibility.** Each pin is a 48dp-or-larger button with a spoken label ("Phone", "Social folder, 2 apps") and a
   click label ("Open Phone"); the icon's own description is cleared so it is not read twice. The bar uses the preview's
   existing safe-drawing insets (bottom and horizontal), so cutouts, rotation and the unfolded width are handled as for the
   rest of the preview, and the pager's bottom padding follows the bar height.

## Decisions (please confirm)

1. **Pins are read live from `HomeLayoutSet`, not from the pool.** The brief asked for pool dock pins "if the pool domain
   lacks them: extend additively". Not done, on purpose: the pool is a one-time snapshot and `migrated` is already true on
   dogfood devices, so a new field would need a second migration path and would go stale the moment the user pins something
   on Home (the placed-home page has the Refresh action for exactly that reason). Reading the live dock has no staleness,
   needs no codec change and no schema bump. When pool editing owns the dock, pins can move into the pool then, with the
   migration done once for real.
2. **No per-workspace dock override model.** Q17 says pins are never per workspace and a workspace overrides edge, size and
   visibility (`DockPresentation`). That is a persisted-model change with its own validation (hidden dock needs a bound
   gesture, 11.4), not cheap, and the preview dock is always at the bottom. Deferred to S7.
3. **Bottom edge only.** The preview keeps its bottom dock (the menu layer already takes `DockPosition.BOTTOM`); the
   device-class edge setting is not applied yet.
4. **No expand shelf, no panel, no dynamic-entry badges beyond what the expression draws.**

## Not done

- Editing pins (pin, unpin, reorder, move to Home): waits for the pool-editing slice.
- Per-workspace overrides of edge, size, visibility and a "Follow device dock / Override" Settings row (Q17, S7).
- The device-class dock edge (left, right, top), background alpha, corner radius, visual effect and liquid glass.
- The expanded shelf and dock panel; hosted widgets pinned in the dock are skipped.
- The dock pull as a menu trigger inside the preview (the handle and the TalkBack action open the menu).
- Pin removal when an app is uninstalled is whatever `HomeLayoutSet` already does (verify; not found, see
  `workspaces-configuration.md` 9.6).

## Tests

- JVM: `PreviewDockTest` (mapping, clamping, 48dp targets, hidden dock, labels).
- Roborazzi: `PreviewDockScreenshotTest` (compact, unfolded, dark; with and without the dynamic section; empty, hidden and
  placeholder states; tap an app, tap a folder; spoken labels and 48dp targets; the "Workspace menu" action beside the
  handle). Goldens are recorded in CI.

## Owner device checklist

- [ ] Pinned apps and folders match the standard dock in order, size and spacing; icons load.
- [ ] Tap an app: launches. Tap a folder: lists its apps; tapping one launches it.
- [ ] A workspace with a dynamic section (Notifications) shows it beside the pins; without one, only pins.
- [ ] The *Workspaces* handle sits just above the dock and opens the menu; with TalkBack the dock offers **Workspace menu**
      and each pin is read as its app (once) with "Open ..." as the action.
- [ ] Change the dock icon size in Settings > Dock: the preview bar follows after reopening the preview.
- [ ] Hide the dock in Settings: the preview shows the note and no pins; the handle still works.
- [ ] Rotate, fold and unfold: the dock respects the bars and cutout, 48dp targets, nothing clipped; many pins scroll.
- [ ] Preview off: Home and the standard dock behave exactly as before.
