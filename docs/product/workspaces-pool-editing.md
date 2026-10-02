# Workspaces: editing the preview home (WS10 S5, as built)

Status: **behind the Workspaces (preview) switch only**. Builds on [`workspaces-pool.md`](workspaces-pool.md) (pure pool
operations) and [`workspaces-pool-cutover.md`](workspaces-pool-cutover.md) (read-only home). With the switch off nothing
here is constructed or run. The standard Home (`HomeLayoutSet`) is **not modified**; it stays the source for
"Refresh home items". Tracking: #1363.

## What you can do

Enter edit mode with **Edit** in the preview top bar, or by touching and holding empty space (or an item) on a home
page. **Done** (or Back) leaves it. In edit mode:

| Action | How | Rule (domain operation) |
| --- | --- | --- |
| Select | Tap an item | Panel at the bottom shows its actions |
| Move one cell | Move left/right/up/down buttons, or touch and hold then drag | `GridPlacementEngine` via `PoolPlacement.move`; collisions and edges are refused with a message |
| Move to another page | Move to previous/next page button | Same cell if free, else the first free cell (`PoolHomeEditing.moveToAdjacentPage`) |
| Remove | Remove from this workspace | `PoolRemoval.remove`: the item stays in the pool if another workspace shows it |
| Delete everywhere | Delete everywhere, then confirm | `PoolRemoval.removeEverywhere`; the confirm says how many other workspaces show it |
| Add app | Add app, pick from the installed list | `PoolHomeEditing.addApp`: first free cell, reuses the pool app, refuses a duplicate |
| Separate copy (widget) | Add a separate copy | `PoolWidgets.separateCopy`: a new unbound placeholder; a second placement of the same widget is refused |
| Add / remove page | Add page; Remove empty page on an empty page | Last page and non-empty pages are refused |
| Undo / Redo | Buttons, or the snackbar Undo | `PoolEditSession`, 50 steps |

Every edit is one atomic immutable `PoolEdit`; Undo restores the previous pool value. A rejected edit changes nothing and
says why in the snackbar. A one-line notice in the edit bar says edits change this preview only, not the standard home.
**Refresh home items** now asks first: it replaces everything in the preview, including your edits.

## How it is built

* Domain (`core/domain/.../workspace/pool/`): `PoolEditSession` (undo/redo plus the host-id deletion queue),
  `PoolHomeEditing` (move to cell, nudge, adjacent page, add app, add/remove page, delete impact), `PoolHostIds`
  (standard home host ids), `PoolHomeView.resolveTarget/arrangementOf` (the edit target is exactly the page drawn).
* App (`app/.../launcher/pool/`): `PoolEditController` (Compose-free, JVM tested), `PoolHomeEditor` (bar, panel, snackbar),
  `PoolEditDialogs`, `PoolEditableCell` (select, drag, TalkBack actions), `PoolHomePage` (additive), `CachedPoolRepository`
  (`update`, `flush`). Preview host and surface changes are additive (an edit overlay and an Edit button).
* Persistence: each edit is published in memory at once; one atomic DataStore write follows after 400 ms of quiet, on Done,
  and when the app stops. A failed write keeps the change pending for the next flush. A read failure disables writing for
  the process, so edits can never overwrite what is on disk.
* Host ids: widget host ids that leave the pool wait in the session's `HostIdDeletionQueue`. They are deleted only after
  Done, only after the final write succeeded, and **never if the standard home still holds the id** (migrated widgets
  share their id with the standard home's widget, which is only read). Undo forgets ids that are live again.

## Decisions (please confirm)

1. **Edits go to the arrangement shown.** Preset workspaces have no arrangement yet and show the migrated Library home, so
   editing one edits that arrangement (shared by every preset that borrows it). Per-workspace arrangements (fork on first
   edit) are not built; they need a decision on cloning widgets, which are single-placement.
2. **No live sync with the standard home.** Edits and `HomeLayoutSet` diverge by design; the top-bar notice and the
   Refresh confirm say so.
3. **Buttons first, drag as a shortcut.** Drag moves within a page only; moving between pages is the page buttons.
4. **Separate copy is a placeholder.** Binding a new host id needs the widget configure flow, not built.
5. **Edit entry is the top-bar Edit button and long press**, not the workspace menu: a menu entry needs a new shell action
   (and its route-ownership row), left for the menu owner.

## Not done

* Folder sharing UI ("Also show in...", make independent): operations exist, no UI. Folder contents are not editable
  (no create folder, drag onto a folder, rename).
* Resizing widgets, binding/configuring a separate-copy widget.
* Drag between pages; the pager does not follow a moved item.
* Edit entry from the workspace menu; per-workspace arrangements; edits on the Finder page.
* A host id queued when the process dies in edit mode is leaked, not deleted (startup reconciliation not built).
* Pool in backup/restore (unchanged).
* Screenshot and device tests were not run locally (CI only).

## Verification

`./gradlew verify` was **not** run locally (dl.google.com blocked); CI runs it. Domain and Compose-free app tests, ktlint
and detekt ran in a scratch Gradle project on Maven Central. Tests: `PoolEditSessionTest` (incl. 60 seeded random edit
sequences: invariants, exact undo/redo, host-id accounting), `PoolHomeEditingTest`, `PoolHomeViewTargetTest`,
`PoolEditControllerTest`, `CachedPoolRepositoryEditingTest`, Roborazzi `PoolEditScreenshotTest`.

## Owner device checklist

Back up first. Preview is off by default; the standard home must stay unchanged throughout.

- [ ] Preview on, open it, tap **Edit** (also try touch and hold on empty space and on an icon).
- [ ] The notice says edits do not change the standard home. Move an icon with the arrow buttons and by drag; try an
      occupied cell and the page edge (message, nothing changes).
- [ ] Move an icon to the next page and back. Add a page; remove it while empty.
- [ ] Remove an icon, tap Undo in the snackbar and Undo/Redo in the bar.
- [ ] Add an app from the list; a second add of the same app is refused.
- [ ] Delete everywhere asks first. A widget: remove it, Undo; then Done and check the standard Home widget still works.
- [ ] Done, force-stop, reopen the preview: edits persist. Standard Home unchanged.
- [ ] **Refresh home items** asks first and replaces edits.
- [ ] TalkBack: item custom actions (move, remove, delete); 48dp targets; "Remove animations" on.
- [ ] Switch off: Home, drawer, dock behave as before.
