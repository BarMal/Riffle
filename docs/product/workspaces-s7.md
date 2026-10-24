# Workspaces S7: skin override, workspace-menu gesture, dock overrides and new apps (as built)

> **S9 update ([`workspaces-default.md`](workspaces-default.md)).** The preview switch and `WorkspaceMenuFeature` are
> gone, and the shell now does what this page lists as not done: the launcher theme reads `WorkspaceSkin.resolve`,
> the home applies the workspace's `gestureBindings` and dock override (`withWorkspaceHome`), and a package-added
> event calls `PoolNewApps.placeNewApp(..., AppArrival.INSTALLED)`. The dock pull opens the menu.

Status: built behind Settings > Developer > "Workspaces (preview)" (off by default) and, for the gesture, the
workspace menu feature (`WorkspaceMenuFeature.enabled`). Standard launcher behaviour is unchanged with both off. This
page covers four rows of slice S7 in `workspaces-configuration.md` section 7: the **per-workspace skin override** (Q15,
section 8.7), the **`OPEN_WORKSPACE_MENU` gesture action** (section 11.4), **dock overrides with the hidden-dock
validation** (Q17/N6, section 11) and **New apps per preset with `autoPages`** (N9/N12, section 9.8). Sections 3 and 4
build on the S4 pool (the dock is `PoolDock` in `riffle_pool`) and the N10 fold. The S7 lens-library decision
("reinstalling a preset refreshes untouched lenses") is recorded in
[`workspaces-lens-library.md`](workspaces-lens-library.md), "Untouched preset lenses". Other S7 rows are elsewhere:
start page, Finder out of the pager and Return in [`workspaces-start-page.md`](workspaces-start-page.md); the
`ICON_GRID` Finder is other slices' work. Refs #1363.

## 1. Per-workspace skin override (Q15)


### What "skin" means (design 8.7, decided)

A skin is a theme preset, `LauncherThemePreset` (`MATERIAL`, `MINIMAL`, `RETRO`, `GLASS`, `TERMINAL`, `CUSTOM`), the
only appearance catalog in the repo. A workspace may override the global preset (Settings > Appearance > Theme preset)
while it is the displayed workspace; null follows the global one.

### Domain (`core/domain`, no Android types)

* `Workspace.skinOverrideId: String?` already existed (WS0) with the codec key `"skin"`; no stored-data change was
  needed. The id stored is the preset's **enum name** (`"GLASS"`), a stored contract that is never renamed.
* `WorkspaceSkin` (`workspace/WorkspaceSkin.kt`): `idOf(preset)`, `presetOf(id)`, `overrideOf(workspace)` and
  `resolve(global, workspace)`. **Resolution order:** the workspace's override when it names a preset, else the global
  preset. An id that names no preset (the old lower-case test ids, the presets' `skin.*` hints, blank, hostile data)
  follows the global preset; it is never an error and never rewritten on read.
* Settings: `WorkspaceRow.skin` (the applied override, null = follow), `WorkspacesSettingsAction.SetSkin(id, skin?)`
  (changes only that workspace's `skinOverrideId`; not undoable, like Rename; setting the current value changes
  nothing), message `SkinChanged(name, skin?)`.
* The editor's existing `WorkspaceEdit.SetSkinOverride` is unchanged (the editor UI does not offer it; Settings does).
* Presets still never set a skin on install (`skinHintId` stays a hint, `skinOverrideId` stays null).

### App

* Settings > Workspaces: every row's menu has **Appearance...**, offered on any layout (it is a property of the
  workspace, like Rename). It opens "Workspace appearance": radio rows "Follow global appearance" and each theme
  preset (named as Settings > Appearance names them), the current choice pre-selected, Save / Cancel. The row summary
  says "<Preset> theme" when an override is set; the announcement says `"Work" uses the Glass theme` or
  `"Work" follows the global appearance`.
* Applied by `WorkspacePreviewTheme(state, workspace)`: the preview surface uses the **displayed** workspace (so a
  fallback workspace's own skin applies), and the editor overlay uses the workspace being edited. Every other theme
  input (mode, accent, custom colours, corner style, typography, reduced transparency, liquid glass) stays global.

### Gaps (design leaves these open; minimal version built)

* Only the theme preset is overridable. A per-workspace accent, colours, corners or typography would need a richer
  skin model (or a skin catalog); not designed.
* The override applies to the Workspaces preview surfaces only. The standard home, the dock outside the preview, the
  app drawer and Settings keep the global theme. After the S9 flip the shell should read `WorkspaceSkin.resolve` for
  the active workspace in `LauncherShell`'s `RiffleLauncherTheme` call.
* Preset `skinHintId`s are still placeholders (`skin.nova`...) that name no preset. Mapping them to a
  `LauncherThemePreset` (or dropping them) and offering "Use the suggested look" in the preset picker is the small WS8
  follow-up the design mentions.
* Switching workspaces changes the theme preset without a dedicated transition (the theme recomposes; there is no
  animation to reduce).

## 2. `OPEN_WORKSPACE_MENU` gesture action (11.4)


### As built

* `LauncherGestureAction.OPEN_WORKSPACE_MENU` (stored by name like every gesture action; an older build decodes the
  name as "no action"). `needsWorkspaceMenu` marks it. **No default binding uses it**: `defaultHomeGestureActions` is
  unchanged. Making the dock pull open the menu is S9 (another wave).
* `HomeSwipeGestureActionMapper`: `OPEN_WORKSPACE_MENU -> LauncherShellAction.OpenWorkspaceMenu`.
* Routing: `OpenWorkspaceMenu` is an activity route (`LauncherActivityRoute.WorkspaceMenu`);
  `LauncherActivityActionHandler(openWorkspaceMenu = ...)`, wired in `MainActivity` to
  `shellViewModel.workspaceMenu.dispatch(WorkspaceMenuAction.Open)`.
* **Honoured only while the feature is on**: `WorkspaceMenuController` plans no model while
  `WorkspaceMenuFeature.enabled` is false (or nothing is stored), and the reducer never opens without a model, so the
  gesture is consumed and nothing happens. With the preview open, the same controller state drives the preview's menu.
* Gesture settings: "Workspace menu" is listed only while the feature is on, or when a gesture already uses it
  (`gestureActionOptions`), so turning the feature off never hides the current choice.
* Cards: `adaptiveStageAppStageActionFilter` lets it through (the menu layer is drawn above both mode surfaces).
* The hidden-dock rule (section 3) counts a gesture bound to this action as the reachable menu entry that lets a
  workspace hide its dock.

## 3. Per-workspace dock overrides (Q17, N6)


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
  the workspace menu, in the global home gestures (`globalActions(HomeGestureSettings)`, every gesture's resolved action) or in the workspace's own
  `gestureBindings`. The action is `LauncherGestureAction.OPEN_WORKSPACE_MENU` (section 2); global gestures are
  compared as enum values, and a workspace's `gestureBindings` (action ids stored by enum name) are decoded with
  `LauncherGestureAction.fromStoredName`, so an unknown name never counts. The TalkBack "Workspace menu" action and the keyboard shortcut don't count, so a touch user is never
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

## 4. New apps per preset (N9, N12)


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

Skin override and gesture (sections 1 and 2):

* Domain: `WorkspaceSkinSettingsTest` (resolution order, unknown ids follow global, the stored-id contract and codec
  round trip, row model, `SetSkin` sets/clears only that workspace, no-op cases, presets never set a skin). Domain
  suite run in a scratch JVM project: all tests pass, ktlint and detekt clean.
* App (JUnit, run in CI only): `WorkspaceRowActionsTest` (menu order includes Appearance, it opens the picker with the
  current override), `WorkspacesSettingsTextTest` (labels, row summary, announcements), `WorkspaceMenuGestureTest`
  (mapping, swipe-to-action, opens with the feature on, consumed but inert with it off, no default binding, settings
  options), `LauncherActionDomainTest` (route table), `AdaptiveStageAppStageActionFilterTest` (Cards).

Dock overrides and new apps (sections 3 and 4):

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

* The items listed under "Gaps" above.
* Per-workspace gesture bindings (`Workspace.gestureBindings`) are still not read by the shell; the gesture is a global
  binding only.
* No Compose UI test or screenshot for the Appearance dialog.
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

Skin override and gesture:

1. Developer: Workspaces (preview) on. Settings > Workspaces > a row's menu > Appearance...: pick Glass, Save. The row
   says "Glass theme"; TalkBack announces the change.
2. Open the preview with that workspace active: cards, panels and typography use Glass while Settings > Appearance
   stays on your global preset. Switch to another workspace from the menu: the preview returns to the global preset.
3. Appearance... > Follow global appearance > Save: the row loses "theme", the preview uses the global preset.
4. Edit that workspace: the editor overlay is drawn in its skin.
5. Turn the workspace menu feature on. Settings > Gestures: a home gesture (for example three-finger swipe up) lists
   "Workspace menu"; bind it. On Home (Standard and Cards) the gesture opens the workspace menu; Back closes it.
6. Turn the workspace menu feature off: the gesture does nothing; the gesture row still shows "Workspace menu" and can
   be changed. Turn it on again: it works again.
7. Defaults: on a fresh install no gesture is bound to the workspace menu, and the dock pull still switches Home and
   Library.
8. 200% font size and TalkBack: the Appearance dialog's rows are readable radio rows and the dialog scrolls.

Dock overrides and new apps:

9. Developer: Workspaces (preview) on. Settings > Workspaces > a row's menu > **Dock...**: "Hide the dock" is
   disabled with the gesture reason. Choose Left edge and 40 dp, Save; TalkBack announces it.
10. Open the preview on that workspace: the dock is a column on the left (right in RTL if you chose Right), pages are
   padded clear of it, pins scroll vertically, and the workspace menu opens from the left. Switch to another
   workspace: the dock goes back to the bottom with the same pins.
11. Workspace menu feature on. Settings > Gestures, bind a gesture to "Workspace menu". Dock... > Hide the dock is
   now enabled; Save. In the preview, that workspace shows no dock and the pages use the full height. TalkBack's
   "Workspace menu" action on the surface and the menu handle still open the menu.
12. Unbind the gesture: the hidden workspace draws its dock again (fallback); Dock... shows Hide disabled.
13. Install the Niagara preset and switch to it: no dock in the preview. Install unfolded TimeScape on the foldable
   layout: its dock is on the left.
14. Settings > Workspaces > an iOS-style workspace > **New apps...**: "Use the iOS-style preset's default" is checked.
   Uncheck it, choose "Only in All apps", Save; check it again, Save.
15. Rotate and fold/unfold with an override set: the phone layout keeps its override in landscape (N10), the foldable
   layout uses its own.
16. 200% font size: both dialogs scroll and every row stays readable with 48 dp targets.
