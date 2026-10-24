# Workspaces S7: per-workspace dock overrides and new apps per preset (as built)

Status: built behind Settings > Developer > "Workspaces (preview)" (off by default). The standard home is unchanged.
This page covers two rows of slice S7 in `workspaces-configuration.md` section 7: **dock overrides with the
hidden-dock validation** (Q17/N6, section 11) and **New apps per preset with `autoPages`** (N9/N12, section 9.8). It
builds on the S4 pool (the dock is `PoolDock` in `riffle_pool`) and the N10 fold. The skin override and the
`OPEN_WORKSPACE_MENU` gesture are the other S7 PR. Refs #1363.

## 1. Per-workspace dock overrides (Q17, N6)

### Domain (`core/domain`, no Android types)

* `WorkspaceDock.presentation: DockPresentation?` (`workspace/DockPresentation.kt`): `hidden`, `edge: DockPosition?`,
  `iconSizeDp: Int?` (must lie in `MIN..MAX_DOCK_ICON_SIZE_DP`, 32..56). Override-only: pins, capacity, appearance and
  the panel are never per workspace. `DockPresentation()` overrides nothing (`followsShared`).
* Codec: optional `"presentation": {"hidden"?, "edge"?, "iconSize"?}` inside the workspace's `"dock"`
  (`WorkspaceExtrasCodec`). Additive, schema version stays 1, and "follow" is stored as **no key**, so a workspace
  without an override encodes exactly as before. An older build ignores the key and draws the shared dock. Decoding
  never throws: an unknown edge is dropped, an out-of-range size is clamped, a non-boolean `hidden` reads as false.
* Resolution (`dock/EffectiveDock.kt`): shared dock, then the shown workspace's override.
  `EffectiveDock.apply(PoolDockSettings | DockModel, presentation)` sets `isEnabled = isEnabled && !hidden` (an
  override can hide, never show, a dock the user turned off), `position = edge ?: position`,
  `iconSizeDp = iconSizeDp ?: iconSizeDp`. `resolveDock(poolDock, workspace, globalGestureActions)` returns the
  `PoolDock` with the same pins and panel. Library's edge (`libraryPosition`) is not overridden.
* **Stranding rule (N6, 11.4).** `DockMenuReachability`: a workspace may hide its dock only if a home gesture opens
  the workspace menu, in the global home gestures (`globalActions(HomeGestureSettings)`) or in the workspace's own
  `gestureBindings`. The action is matched by its stored name `OPEN_WORKSPACE_MENU`
  (`LauncherGestureAction.OPEN_WORKSPACE_MENU` from the S7 gesture PR, #1429). It is not on this branch's base, so it
  is matched as text: until that PR is merged no gesture can name it and hiding is refused, which is the safe
  direction. The TalkBack "Workspace menu" action and the keyboard shortcut don't count, so a touch user is never
  stranded.
  * `DockMenuReachability.issue` reports `WorkspaceIssue.DockHiddenWithoutMenuEntry`. It is **not** part of
    `WorkspaceValidation.validate`, because it depends on global settings and a stored violation must draw the dock,
    not send you to another workspace.
  * `EffectiveDock.presentationFor` applies the fallback: a stranded `hidden` is drawn visible
    (`strandedFallback = true`); the edge and size still apply.
* Presets (applied on install and Reset, not to copies already installed): **Niagara** `hidden = true` plus
  `gestureBindings = {"ONE_FINGER_UP": "OPEN_WORKSPACE_MENU"}`; **unfolded TimeScape** `edge = LEFT`; the others
  `null`. `workspaces-presets.md` gaps 2 and 9 are closed.
* Settings: `WorkspaceRow.dock`, `WorkspaceRow.canHideDock` (`WorkspacesSettingsPlanner.plan(...,
  globalGestureActions)`), `WorkspacesSettingsAction.SetDockPresentation(id, presentation, globalGestureActions)`
  (refuses a stranded hide with `DockHiddenNeedsMenuGesture`; "follow" is stored as null; not undoable, like Rename),
  message `DockChanged(name, presentation)`.

### App

* Settings > Workspaces > a row's menu > **Dock...**: "Dock for <name>" with radio rows Show / Hide the dock (Hide
  disabled with "To hide the dock, first set a gesture to open the workspace menu in Settings > Gestures."), Edge
  (Same as the layout's dock, Bottom, Left, Right, Top) and Icon size (Same as the layout's dock, 32/40/48/56 dp).
  The page passes the global home gestures' action names.
* Workspaces preview (`WorkspacePreviewHost`, `WorkspacePreviewSurface`, `WorkspacePreviewDock`):
  `PreviewDock.from(dock, presentation)` with the presentation after the stranding rule.
  * **Hidden:** no dock bar and nothing reserved. The "Workspace menu" accessibility action moves to the surface
    root, and the menu handle stays (the menu layer gets extent 0).
  * **Left / right:** a side bar, the height of the dock bar as its width, below the preview's top bar. Pins scroll
    vertically and the dynamic section sits underneath. Page content padding and the menu panel follow the physical
    edge (RTL aware).
  * **Icon size:** the bar and targets resize (48 dp minimum touch target).
  * **Top:** drawn at the bottom in the preview, because the preview's top bar holds that edge.
  * **No override:** the preview's dock stays at the bottom, as before.
* `EditorReasonText.workspace` words the new issue the same way.

## 2. New apps per preset (N9, N12)

### Decision: the setting lives on the workspace

Design 9.8 says "a setting stored per workspace, defaulted by the preset". S4 had put a `newAppPlacement` on the pool's
`Arrangement` (default `FINDER_ONLY`). A preset workspace often has no arrangement of its own (it borrows the migrated
one), and Settings > Workspaces edits the workspace store, not the pool. So the setting is
**`Workspace.newApps: WorkspaceNewApps?`** (`placement: NewAppPlacement`, `autoPages: Boolean`), codec key
`"newApps": {"placement", "autoPages"}`. It is additive, and an unknown placement reads as "follow the preset". Null
follows the preset. `Arrangement.newAppPlacement` stays stored and decoded but is **superseded**: only the older
`placeNewApp(pool, identity, label, ids)` overload still reads it. Owner to confirm.

* `NewAppDefaults.resolve(workspace)` = own choice, else `forPreset(presetId)`, else `MIGRATED`
  (`FINDER_ONLY`, no auto pages, which is today's behaviour).
* Preset defaults: only **iOS-style** opts in (`HOME_AND_FINDER`, `autoPages = true`). Nova-style keeps the drawer
  habit. Niagara's pinned list is hand-picked. TimeScape and Kvaesitso-style have no icon grid. Migrated workspaces
  stay `FINDER_ONLY`.
* Duplicate copies the setting. Reset to preset keeps it, like the name; "Use the preset's default" in the dialog
  clears it.

### Placement (`pool/PoolNewApps.kt`, `pool/PoolAutoPages.kt`)

`PoolNewApps.placeNewApp(pool, identity, label, ids, layout: LayoutWorkspaces, arrival: AppArrival, candidatesOf)` is
the **app-facing entry point for the install event**:

* Only `AppArrival.INSTALLED` places. `PROFILE_AVAILABLE` (a work or private profile unpausing), `UPDATED` and
  `RESTORED` change nothing.
* It creates **one** `PoolApp` and references it in every workspace of the layout whose resolved placement is
  `HOME_AND_FINDER`, at the first free cell of the first page with room.
* When every page is full: with `autoPages`, it appends a page (the last page's grid, fresh id) and places the icon
  there (`pagesAdded`). Otherwise that workspace is skipped.
* A workspace without its own arrangement shows the first of `candidatesOf(id)` with pages (pass
  `PoolHomeView.candidates(id, layoutClass, shownMode)`). It **forks** that arrangement first (an edit forks, as S4
  decided), unless the arrangement it shows already has the app. A fork that then can't take the icon is dropped
  again. With the default `candidatesOf` a borrower is skipped.
* Idempotent: a workspace that already shows the app (same identity incl. profile, not a shortcut) is skipped. When
  nothing is placed the pool is returned unchanged. Other layouts and arrangements of workspaces not in `layout` are
  never touched.
* Result: `NewAppEdit(edit, placedIn, skipped, forked, pagesAdded)`.
* `PoolAutoPages.trimTrailingEmpty(pool, workspaceId)` is for leaving edit mode: it removes empty, unpinned trailing
  pages, never the first. Nothing calls it yet.

### Wiring (not done here)

The install event isn't wired. The Workspaces preview has no package-added signal; the installed-app repository's
change hook is in the shell, which another wave owns. The intended call on a package-added (not replaced) broadcast
for an available profile:
`PoolNewApps.placeNewApp(pool, identity, label, WorkspaceIdFactory.Random, set.workspacesFor(layoutClass), AppArrival.INSTALLED) { PoolHomeView.candidates(it, layoutClass, shownMode) }`,
then save `edit.pool`, once per layout that has a pool.

### Settings

`WorkspaceRow.newApps` (effective) and `newAppsFollowPreset`; `WorkspacesSettingsAction.SetNewApps(id, newApps?)`;
message `NewAppsChanged(name, newApps, followsPreset)`. Settings > Workspaces > row menu > **New apps...**: "Use the
<preset> preset's default" (only when a preset is recorded), "Only in All apps" / "Also add an icon to the home
pages", and "Add a page when the pages are full" (enabled only with home icons).

## Tests

* Domain:
  * `EffectiveDockTest`: the layering table over `PoolDockSettings` and `DockModel`; pins, panel and capacity are
    never touched; the stranding fallback and issue; gestures counted from the workspace or the global gestures,
    other gestures not counted; `globalActions` lists every gesture; the size bounds; Niagara and TimeScape resolve
    as expected and the others follow; switching workspaces changes the effective dock but not the pins; the preview
    model reserves nothing when hidden and runs vertically on a side.
  * `WorkspaceDockAndNewAppsCodecTest`: round trips; follow is stored as no key (encoding unchanged); an older decoder
    sees "follow"; hostile values are dropped or clamped; preset defaults; duplicate keeps the overrides.
  * `PoolNewAppsPerWorkspaceTest`: the workspace setting wins over the arrangement's; only INSTALLED places; repeats
    are idempotent and a work-profile twin is a different app; a full workspace gets a page only with `autoPages`; a
    borrower forks unless it already shows the app; a fork that can't take the icon is dropped; trailing-page trim.
  * `WorkspaceDockAndNewAppsSettingsTest`: hiding refused without a gesture and allowed with one; edge and size need
    none; follow is stored as null; rows carry `dock`/`canHideDock`/`newApps`; `SetNewApps` sets and resets.
  * Result: the full domain suite ran in a scratch JVM project, 1862 tests, all pass; ktlint and detekt (main and
    test of domain and app) are clean.
* App (JUnit, CI only): `WorkspaceRowActionsTest` (menu order with Dock... and New apps..., the dialogs they open),
  `WorkspaceDockSettingsTextTest` (summaries, announcements, the shared stranding reason, the choice ranges).

## Not done

* The install event isn't wired to `placeNewApp` (see Wiring). `trimTrailingEmpty` isn't called on leaving edit mode,
  and pages aren't added during manual placement in edit mode. Only new apps add pages.
* The standard shell and the floating dock ignore overrides. After the S9 flip, `HomeDockHost`, `reservedExtent` and
  `resolveDockPosition` should read `resolveDock(poolDock, activeWorkspace, ...)`.
* Per-workspace `gestureBindings` (Niagara's swipe up) aren't read by the shell yet, so on a real home that gesture
  does nothing until they are. The preview keeps its menu handle, so nothing is stranded there.
* The preview dock still reads the home layout's dock (`dockFor`), not `PoolDock`; the override layers on either.
* A top-edge override is drawn at the bottom in the preview. There is no transition animation when switching
  workspaces changes the dock (it recomposes).
* The workspace editor has no dock control; Settings has it. No Compose UI or screenshot test for the two dialogs.

## Owner device checklist

1. Developer: Workspaces (preview) on. Settings > Workspaces > a row's menu > **Dock...**: "Hide the dock" is
   disabled with the gesture reason. Choose Left edge and 40 dp, Save; TalkBack announces it.
2. Open the preview on that workspace: the dock is a column on the left (right in RTL if you chose Right), pages are
   padded clear of it, pins scroll vertically, and the workspace menu opens from the left. Switch to another
   workspace: the dock goes back to the bottom with the same pins.
3. With the S7 gesture PR merged: Settings > Gestures, bind a gesture to "Workspace menu". Dock... > Hide the dock is
   now enabled; Save. In the preview, that workspace shows no dock and the pages use the full height. TalkBack's
   "Workspace menu" action on the surface and the menu handle still open the menu.
4. Unbind the gesture: the hidden workspace draws its dock again (fallback); Dock... shows Hide disabled.
5. Install the Niagara preset and switch to it: no dock in the preview. Install unfolded TimeScape on the foldable
   layout: its dock is on the left.
6. Settings > Workspaces > an iOS-style workspace > **New apps...**: "Use the iOS-style preset's default" is checked.
   Uncheck it, choose "Only in All apps", Save; check it again, Save.
7. Rotate and fold/unfold with an override set: the phone layout keeps its override in landscape (N10), the foldable
   layout uses its own.
8. 200% font size: both dialogs scroll and every row stays readable with 48 dp targets.
