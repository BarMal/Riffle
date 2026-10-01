# Workspaces: start page, Return setting, Finder out of the pager

Status: built behind the Workspaces (preview) developer setting. Standard launcher behaviour is unchanged with the
preview off. Implements owner decisions Q6 (Finder is its own surface), Q7 (Return is a setting) and Q14 (a start
page per workspace) from `workspaces-configuration.md` (sections 3.2 and 8.3). Refs #1363.

## As built

### Domain (`core/domain`, no Android types)

* `Workspace.startPageId: ContainerId? = null`. Optional and additive: the codec writes the key `"start"` only when
  a start page is set, and decoding a missing, blank or wrongly typed value gives "no start page". The workspace
  schema version is **not** bumped (`CURRENT_WORKSPACE_SCHEMA_VERSION` stays 1, set schema stays 2); older builds
  ignore the key.
* `WorkspacePaging.kt`: the Finder stays a page *role* but is not a pager position.
  * `Workspace.pagerPages`: every page except the Finder (a Finder-only workspace keeps it, so the pager is never
    empty), `Workspace.finderPage`, `Workspace.firstPageId` (first pager page), `Workspace.effectiveStartPageId`
    (the start page when it names a page, else the first pager page), `Workspace.isFinder(id)`.
* `WorkspaceReturn.kt`:
  * `ReturnBehavior { RESTORE, FIRST_PAGE, START_PAGE }`, default `RESTORE`.
  * `ReturnEvent { RETURN, HOME_PRESS, HOME_PRESS_FINDER_OPEN }`.
  * `WorkspaceReturnResolver.resolve(workspace, behavior, lastVisited, event)`: pure, never throws, always returns a
    page of the same workspace or null (no pages). It cannot change the workspace, only the page. Table:

    | Event | RESTORE | FIRST_PAGE | START_PAGE |
    | --- | --- | --- | --- |
    | RETURN | last page, else start page | first pager page | start page |
    | HOME_PRESS | start page | start page | start page |
    | HOME_PRESS_FINDER_OPEN | page the Finder was opened from, else start page | same | same |

    A last page that vanished, or that names the Finder, is ignored (the Finder is a surface, not a place to come
    back to). A stale start page falls to the first pager page.
* `WorkspaceValidation` is unchanged on purpose: no new issue is raised for an unknown start page (doc: ignored,
  never an error), a Finder start page, or a Finder-only workspace, so no stored workspace becomes invalid. Tests
  pin this.
* `WorkspaceEdit.SetStartPage(pageId?)` through `WorkspaceEditor`: rejects an unknown page (`UnknownPage`), accepts
  the Finder, clears on null. `RemovePage` clears a start page that pointed at the removed page. Undo and redo come
  from `WorkspaceEditSession` like every other edit.
* `WorkspaceCopy.withFreshIds` re-points the start page at the copied page's new id.
* `WorkspaceMenuPlanner`: jump entries' `pageNumber` is now the pager position, so it skips the Finder ("Page 1" is
  the first swipeable page). Previously the Finder counted.
* `HomeBehaviourSettings(returnBehavior = RESTORE)` as `LauncherSettings.home`.

### App

* Persistence: `LauncherSettingsJsonCodec` writes `"home": {"returnBehavior": ...}`; unknown or missing values decode
  to `RESTORE`. It rides the existing launcher-settings document, so backup/restore carries it with no extra work.
  Action `LauncherShellAction.SelectReturnBehavior` is reduced and saved by `LauncherSettingsStateReducer`.
* Preview shell (`WorkspacePreviewSurface`):
  * The pager swipes through `pagerPages` only. The Finder opens as its own surface over the pager (menu Finder
    entry, or as the start page), with a titled bar and a 48dp "Close Finder" button; system Back closes it first,
    then Back exits the preview as before.
  * `PreviewPositions` (pure) tracks `PreviewPosition(page, finderOpen)` and applies the Return rule. The surface
    lands on the Return-rule page when it opens.
  * `WorkspacePreviewController.returnRequest`: a lifecycle observer in `WorkspacePreviewLayer` (stop then start) raises a Return (back from an app, Recents,
    Home from another app); `onNewIntent` for a Home press raises a Home press only when the launcher was already
    resumed. Requests are ignored while the preview is closed.
  * Reduced motion: page changes use an instant scroll, as the existing navigation does.
* Settings > Workspaces: a "Returning to Home" radio group under the workspace list (below).
* Editor: each page card shows "Start page" on the current start page and a "Set as start page" button (48dp, with
  a per-page content description) on the others. It goes through `EditorAction.Apply`, so it validates and has Undo.
  The first page counts as the start page when none is set.

## Decisions

1. **Return lives in a new `home` settings group**, not in `AppDrawerSettings`, matching the doc's
   `HomeBehaviourSettings`. `afterLeavingLibrary` is untouched (it still drives the standard shell); retiring it is
   the pool/S9 cut-over's job.
2. **The Return row lives in Settings > Workspaces**, because that page now exists on main (#1391) and is
   preview-gated; the doc's eventual home (Home & layout > Layout > "Returning to Home") can move it later. A
   three-option radio (`SettingsReturnBehaviorSection`) bound to `LauncherShellAction.SelectReturnBehavior`,
   Restore default, one supporting line each, 48dp rows, selected state spoken.
3. **A Home press no longer closes the open preview.** It used to leave the preview for the standard launcher; now it
   goes to the page the Return rule names and Exit stays one tap away. This follows E2 in section 3.2.
4. **Home press from another app is a Return, not E2.** `onNewIntent` runs before the activity restarts in that case, so it is
   only treated as E2 when the lifecycle is already RESUMED.
5. **Jump entry page numbers skip the Finder.**
6. **No schema bump** (optional field).

## Not done

* Per-workspace start page row on a workspace detail page (the doc's Settings > Workspaces detail); only the editor
  action exists.
* The Finder gesture binding and the Finder as the dock-menu-only entry for real home pages: the preview opens it
  from the menu entry only. No change to the standard shell, which still uses the drawer.
* Return/Home-press landing in the standard (non-preview) shell. The pool/WorkspaceRuntime cut-over owns that.
* Persisting the last visited page across process death (`selectedPageId` durability, 3.2). Not needed while the
  preview is never persisted open.
* A "reset start page to first page" control; moving the start page to page 1 is equivalent.
* `FINDER_EXPRESSIONS` widening with `ICON_GRID` (8.3a) is a separate slice.
* Roborazzi golden for the Finder surface and the editor start-page action; goldens are generated in CI.

## Owner device checklist

1. Turn Workspaces (preview) on, open the preview on a workspace with several pages and a Finder (the Nova default).
   The pager shows only non-Finder pages and the page indicator count matches; the Finder is not reachable by swipe.
2. Dock menu > Finder opens the Finder surface over the pager. "Close Finder" and system Back both close it without
   leaving the preview; Back again exits.
3. Edit the workspace, "Set as start page" on page 3, save, Exit and reopen the preview: it lands on page 3 (Restore
   and Start page) or page 1 (First page, chosen in Settings > Workspaces > Returning to Home).
4. Swipe to page 2, launch an app, return with Back: lands on page 2 (Restore). Return with the Home button from the
   app: also page 2.
5. With the preview open on page 2, press Home: moves to the start page (all three settings), does not leave the
   preview.
6. Open the Finder from page 2, press Home: the Finder closes and page 2 shows.
7. Make the Finder the start page: opening the preview shows the Finder over page 1; Home from it returns to page 1.
8. In the editor, set a start page, Undo: the start page reverts. Remove the start page: the first page shows as the
   start page.
9. TalkBack: "Set as start page: Page N" and "Start page: Page N" are announced; the Close Finder button is 48dp.
10. With the preview off nothing above exists; the standard launcher behaves as before.
