# Workspaces S7: skin override and the workspace-menu gesture (as built)

Status: built behind Settings > Developer > "Workspaces (preview)" (off by default) and, for the gesture, the
workspace menu feature (`WorkspaceMenuFeature.enabled`). Standard launcher behaviour is unchanged with both off. This
page covers two rows of slice S7 in `workspaces-configuration.md` section 7: the **per-workspace skin override** (Q15,
section 8.7) and the **`OPEN_WORKSPACE_MENU` gesture action** (section 11.4). The S7 lens-library decision ("reinstalling
a preset refreshes untouched lenses") is recorded in [`workspaces-lens-library.md`](workspaces-lens-library.md),
"Untouched preset lenses". Other S7 rows are elsewhere: start page, Finder out of the pager and Return in
[`workspaces-start-page.md`](workspaces-start-page.md); dock overrides and hidden-dock validation, `ICON_GRID` Finder
and new apps per preset are other slices' work. Refs #1363.

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
* Not a stranding fix on its own: the hidden-dock validation (`DockHiddenWithoutMenuEntry`) that will count this
  gesture as a reachable menu entry belongs to the dock-overrides work.

## Tests

* Domain: `WorkspaceSkinSettingsTest` (resolution order, unknown ids follow global, the stored-id contract and codec
  round trip, row model, `SetSkin` sets/clears only that workspace, no-op cases, presets never set a skin). Domain
  suite run in a scratch JVM project: all tests pass, ktlint and detekt clean.
* App (JUnit, run in CI only): `WorkspaceRowActionsTest` (menu order includes Appearance, it opens the picker with the
  current override), `WorkspacesSettingsTextTest` (labels, row summary, announcements), `WorkspaceMenuGestureTest`
  (mapping, swipe-to-action, opens with the feature on, consumed but inert with it off, no default binding, settings
  options), `LauncherActionDomainTest` (route table), `AdaptiveStageAppStageActionFilterTest` (Cards).

## Not done

* The items listed under "Gaps" above.
* Per-workspace gesture bindings (`Workspace.gestureBindings`) are still not read by the shell; the gesture is a global
  binding only.
* No Compose UI test or screenshot for the Appearance dialog.

## Owner device checklist

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
