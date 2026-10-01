# Settings: Workspaces page (WS10 slice S2, part 1, as built)

Part of #1363 (design: [`workspaces-configuration.md`](workspaces-configuration.md), sections 2.2, 2.3, 2.4 and
2.7); issue #1384. This is the app UI over the domain operations that already exist. The Sources page and the
`OFF` source status are the next slice (S2 part 2) and are not described here yet. Nothing here is on by
default: **the page exists only while Settings > Developer > Workspaces (preview) is on**, see [`workspaces-dogfood.md`](../development/workspaces-dogfood.md). With the preview off the
standard launcher and every existing Settings page are unchanged and nothing in this slice runs.

## Where it is

Settings > **Developer** (top of the main page), with the preview switch on, now lists **Workspaces** after
"Open Workspaces (preview)". It is `SettingsPage.WORKSPACES`, deliberately *not* in `settingsMainPageEntries`,
so it never appears in the grouped main page, in Settings search or in launcher search. The page reads a
`WorkspaceSettingsHost` from a composition local that the shell provides only while the preview is on; without
it the page shows "Turn on Workspaces (preview)...".

## Workspaces page

- **Layout selector** (the existing segmented device tabs; it drives the existing
  `SelectSettingsLayoutDeviceClass` action) chooses which layout's workspaces are listed and edited. A note
  always says which layout is being edited and whether it is the one this device is showing now.
  Human names: Phone (folded), Phone (landscape), Foldable (unfolded), Tablet, Desktop.
- **List** of that layout's workspaces in order. The row marks *Active* and *Default*, the preset it came from
  and the page count. Compact: tapping a row switches to it (the same `activate` the dock menu uses) and a
  per-row menu holds the rest; the same actions are TalkBack custom actions on the row. Unfolded (width of
  600 dp and up): list on the left, the selected workspace's actions on the right; the radio button switches.
- **Actions**: Edit (the existing `EditWorkspace` route into the editor), Rename, Duplicate, Make default,
  Move up and Move down (no drag), Reset to preset or Install preset..., Delete.
  - **Edit** is enabled only for the layout this device is showing: the editor opens workspaces of the active
    device class. For another layout it is disabled with the reason.
  - **Delete** asks first, never removes the last workspace (disabled with the reason; the domain also
    refuses), never touches placed home items (workspaces hold none), and announces an **Undo** snackbar.
  - **Reset to preset** is offered only when the preset a workspace came from is recorded
    (`Workspace.presetId`, below); it re-installs that preset's variant for the layout's posture (fresh
    container ids), keeping the workspace id and name, after a confirmation, with Undo. Otherwise the same
    menu slot offers **Install preset...**. It is never guessed from the name or the content.
  - **Install preset...** opens a picker of the five presets with their descriptions (Nova marked as the default
    and pre-selected, an optional "Switch to it after installing"). Installing adds a copy named after the
    preset ("Nova 2" if the name is taken). It never requests a permission.
- **Copy from other layout...** (only when another layout exists): a dialog with the layouts to choose from and
  the explanation "A one-time copy, not a link. This replaces the N workspaces on X with copies of the M on Y.
  Afterwards the two layouts are independent... Your home screen items are not copied or touched. You can undo
  right after." Confirming runs `WorkspaceSet.copyFromOtherLayout`, announces an Undo.
- **Fall-back notice**: when the layout cannot draw its stored active workspace (the `WorkspaceResolver`
  reasons) a notice says "This layout can't draw Index (used by "X"), so it is using the default workspace,
  "Standard", instead." The marked row stays the stored active one, as in the menu.
- **Announcements**: every action's outcome is announced in a Settings snackbar (a polite live region); only
  the latest destructive change can be undone, and any later change, dismissal or leaving the page ends the
  offer, so Undo can never overwrite something newer. Undo restores exactly the layout the change replaced and
  nothing else.

All persistence goes through `WorkspaceRepository` (the cached repository, so the preview re-renders at once and
the dock menu re-plans). The page is a thin view: `WorkspacesSettingsPlanner` (model),
`WorkspacesSettingsAction.applyTo` (pure operations over `WorkspaceSet`) and `WorkspacesSettingsController`
(applies, saves, keeps the one Undo) are Compose-free and unit tested.

### `Workspace.presetId` (additive)

`Workspace` gained `presetId: String? = null`, encoded as `"preset"` only when present (so existing stored data
and round trips are unchanged). It is set when a preset is installed from this page and on the Nova default a
fresh install is seeded with (`WorkspaceBootstrap`); stored workspaces are never stamped. `WorkspacePresets.installPreset`
itself is unchanged.

## Accessibility and layout

48 dp touch targets, one focusable element per row with a spoken summary and the row actions as custom actions,
text status (never colour alone), no truncation of status text at 200% font, a polite live region for the
fall-back notice and snackbar, confirmations for destructive actions, no drag-only operations, no custom
animation (so reduced motion is respected; dialogs, menus and the snackbar use Material's own motion), system
bar and cutout insets from the Settings surface, two panes from 600 dp. Tokens only: `RiffleSpacing`,
`RiffleShapes`, `RiffleElevation`; no raw `spring(` or `RoundedCornerShape(`; no gesture code.

## Not in this slice

- **Return behaviour (Q7)**: the Restore / First page / Start page setting needs the start page, which does not
  exist yet. Placeholder only: nothing is shown. When the start page lands the setting belongs in Settings >
  Layout ("Returning to Home", default Restore).
- **Drawer presentation (Q6)** moves into the Finder page expression later; the App drawer page is unchanged.
- **New apps per preset (N9)**: needs the shared pool; no row.
- The Sources page and the OFF status (next slice), exclusion rules, saved lenses, per-workspace Dock and Start
  page rows.
- The editor closes back to the preview, not to Settings (Exit preview returns to Settings).

## Tests

Domain (`core/domain`): `WorkspacesSettingsTest` (planner, every action, Undo, last-workspace rule, codec round trip of `presetId`).
App JVM: `WorkspacesSettingsControllerTest`, `WorkspaceRowActionsTest` (the route table),
`WorkspacesSettingsTextTest`, `SettingsDeveloperPagesTest`, `WorkspaceBootstrapTest`. Screenshots (fakes only,
Roborazzi): `WorkspacesSettingsScreenshotTest` at compact (dark, large font) and unfolded, the fall-back notice,
another layout, the last workspace, and the dialogs; the Developer row in `WorkspacePreviewSettingScreenshotTest`.

## Checklist for the owner (on the device)

- [ ] Preview off: Settings looks exactly as before; no Workspaces row anywhere, not in search.
- [ ] Preview on: Settings > Developer shows Workspaces.
- [ ] Workspaces: the list shows this layout's workspaces with Active and Default marked; tap another row and
      Open Workspaces (preview) to see it drawn; the dock menu agrees.
- [ ] Rename, Duplicate, Move up/down and Make default do what they say; "Alpha copy" appears after the original.
- [ ] Delete a workspace: a confirmation first, the Undo snackbar, Undo brings it back in place. Try to delete
      the last one: it is disabled with the reason. Home items are untouched.
- [ ] Reset to preset on the seeded Nova workspace (after editing it in the editor): confirm, the edit is gone,
      the name stays, Undo restores your edit. A workspace with no recorded preset offers Install preset... instead.
- [ ] Install preset...: all five presets with descriptions, Nova marked; install one, with and without
      "Switch to it".
- [ ] Switch the layout selector to Unfolded: the note names it, Edit is disabled with a reason. Copy from other
      layout... explains a one-time copy and the counts; confirm, check the result, Undo.
- [ ] Edit (on this device's layout) opens the editor; Done returns to the preview, Exit preview returns here.
- [ ] If a layout falls back to the default, the notice names what it cannot draw (hard to provoke on a phone;
      note if you see it).
- [ ] TalkBack: rows read as one item; row actions are in the actions menu; snackbar messages are read;
      dialogs are reachable. Large font: nothing is cut off. Unfolded: two panes, rotation keeps the selected row.
- [ ] Turn the preview switch off and confirm the page is gone and Settings behaves as before.
