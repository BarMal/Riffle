# Workspaces are the default (WS10 S9, as built)

Status: **built, on for everyone; never run on a device.** Slice S9 of `workspaces-configuration.md` (sections 1, 3,
7, 10.2, 11.5). The launcher's home is the **active workspace of the current layout**, drawn by the existing standard
home surface over the placed-items pool. The "Workspaces (preview)" developer switch, `WorkspaceMenuFeature`, the
Library-only mode availability and the Standard/Library/Cards mode settings are gone. Builds on the S4 cutover
([`workspaces-pool-cutover-s4.md`](workspaces-pool-cutover-s4.md)), S7 ([`workspaces-s7.md`](workspaces-s7.md),
[`workspaces-start-page.md`](workspaces-start-page.md)) and S11 ([`workspaces-privacy.md`](workspaces-privacy.md)).
Refs #1363; closes #1323, #1324, #1325 as designed in section 3.

**Before installing this build: Settings > Backup > Export, and keep the file.** Nothing in S9 changes how placed items
are stored, but it is the first build whose home edits can land in a workspace's own arrangement (a fork) instead of
the migrated one.

## What the home is now

| Piece | As built |
| --- | --- |
| Which workspace | `WorkspaceSet.resolveActive(layout)` for the shown layout (N10: a rotated phone is the phone layout). A switch from the workspace menu or Settings > Workspaces changes the home at once (`LauncherShellViewModel.followActiveWorkspace`). |
| Placed pages | The standard home (`StandardHome`, all its engines: drag, folders, widgets add/resize/configure/remove, pages, dock editing) over `PoolHomeSync`'s projection of **that workspace's arrangement**. Domain: `WorkspaceHome.shownWorkspaces` (a `HomeShownWorkspaces` resolver) threaded through every `PoolHomeSync` call. |
| A workspace with no arrangement yet (the seeded Nova default, a new preset) | Shows the arrangement it borrows (`PoolHomeView.candidates`: itself, then the migrated workspace of the shown mode, then Library, Standard, Cards), so **the default renders the migrated home identically**. Its **first edit forks** (`PoolArrangements.fork`): apps and widget instances shared by reference, folders cloned (their ids remapped before the edit is ingested), and the edit lands in the fork. The migrated arrangement is left as it was, a recovery copy. |
| A migrated workspace (`ws:<layout>:<mode>`) | The home you had in that mode: the shell switches to its mode (`WorkspaceHome.homeModeFor`) and shows its arrangement, exactly as S4 did. |
| A workspace with no home-grid page (TimeScape, Kvaesitso) | Borrows nothing: no placed items (one empty stand-in page) plus its lens pages. |
| Lens and container pages | Drawn by the existing container hosts (`PageContainerHost`, `PageSetContainerHost`) as extra pages of the same pager, **after** the placed pages, in workspace order (`WorkspaceHome.extraPages`, `WorkspaceHomePages`). They are never a selected page (the stored selection stays on a placed page). Only for non-migrated workspaces: a migrated workspace's lens pages are its layout's generated pages, already drawn. Hidden while editing. Their items offer the S6 Hide menu (`PreviewHideHost` around the shell). |
| Finder | The app drawer (`OpenAppDrawer`), from the menu's Finder entry and every existing entry point. Search is unchanged. |
| Dock | The pool's shared dock with the workspace's override (`withWorkspaceHome` -> `EffectiveDock`, after the stranding rule). Render only: Settings > Dock edits the shared dock. |
| Gestures | Global home gestures with the workspace's `gestureBindings` on top (`WorkspaceHome.gestures`; launch actions, which need a target, are ignored). Render only: Settings > Gestures shows the global ones. |
| Skin | `WorkspaceSkin.resolve(global, workspace)` for the whole launcher theme (home, drawer, search, Settings). |
| Start page and Return | `HomeReturn` takes the workspace's start page when it is a placed page (`WorkspaceHome.startPageOf`). A Home press goes to the first page, then to the start page. A Return follows the setting (Restore / First page / Start page). |
| Dock pull | Opens the workspace menu (Q9), same claim rules; Ctrl+arrow and the "Workspace menu" accessibility action (dock and home root) too. See `gestures.md`. |
| New apps | A package-added event (`AppCatalogChange.PackageAdded`, `LauncherApps.Callback.onPackageAdded`, an install only) refreshes the app list and then calls `PoolNewApps.placeNewApp(..., AppArrival.INSTALLED)` for every layout (`PoolHomeSync.placeNewApp`, `PoolHomeLayoutRepository.placeNewApp`). A profile becoming available, an update or a restore are other events and place nothing. Only workspaces whose New apps setting is "Also add an icon" (iOS-style by default) get one. |
| Privacy | Content levels apply to the home's lens pages (same lens provider). The Privacy page always shows the controls. The screenshot switch is unchanged. |
| Settings | A "Workspaces" section at the top of Settings (Workspaces, Sources, Saved lenses); Settings > Layout keeps grid, labels and gains "Returning to Home"; a "Developer" section keeps one switch (below). |

### Startup (design 1.2)

The view model reads the workspace store **once, bounded to 500 ms**, before the first frame
(`loadWorkspacesAtStartup`), seeding the Nova default for a layout with nothing stored (`WorkspaceBootstrap.seed`),
then retargets the home. On a timeout the asynchronous load finishes the job and the home shows the migrated
workspace until then (an edit made in that window lands in the migrated arrangement; nothing is lost).

### Data safety

- A workspace switch only changes which arrangement is projected. Retargeting re-projects from the shell's set with
  **no `Home` page items** (`withoutHomeItems`), so a workspace that adopts an arrangement never adopts what another
  workspace showed (no duplicated widget instances).
- Only migrated workspaces and workspaces with nothing to show and nothing to borrow adopt a layout; a borrower only
  ever forks.
- The read-only home (W5, `ReadOnlyHomeLayoutRepository`) is untouched: every home edit still goes through the
  shell's repository, so a read-only process refuses it and writes nothing. Retarget and new-app placement exist only
  on the pool-backed repository, so they never run in a read-only process. No widget host id is deleted by S9 code.
- `LauncherViewMode` stays stored (migration input and the base of the projection); the stored "After leaving
  Library" value is kept and ignored.

## Removed settings and code

| Removed | Design | Replaced by |
| --- | --- | --- |
| Settings > Developer > "Workspaces (preview)", "Open Workspaces (preview)" (and the `riffle_workspace_preview` preference is no longer read) | 1.4, 10.2 (N11: the Classic toggle) | Nothing: workspaces are the home |
| `WorkspaceMenuFeature` | 1.4 | The menu is on whenever workspaces are loaded |
| `libraryOnlyLauncherViewModeAvailability` | 1.4, 3.1, 3.3 | `defaultLauncherViewModeAvailability` (every mode available, so no stored layout resolves to Library any more, #1324) |
| Settings > Layout > Mode, Layout template, Modes > Home screen | 3.1, 3.4 (#1325) | The active workspace (Settings > Workspaces, the menu) |
| Settings > Apps > "After leaving Library, return to" | 3.2 (#1323) | Settings > Layout > Returning to Home |
| Leaving Library on a cold start, Home press, app launch or Back | 3.2 (#1323) | Nothing: a return never changes the mode or the workspace |
| The full-screen Workspaces preview as an entry point | S9 | The standard home; the editor opens over it |
| "Switch to Library/Home" dock action, the Home/Library pull (where a menu exists) | 11.5 | "Workspace menu" action and pull |

**Kept: one developer fallback, "Home from placed items"** (Settings > Developer). The design says the developer toggle
goes at S9 and the S4 doc planned the legacy path's removal "next release, with S9". It is kept for now because it is
the only revert that does not need a backup restore while the pool storage is new, and the hand-back runs inside W5's
startup code, which is still changing. Off, the home runs on `HomeLayoutSet` (every workspace then shows the same
home; mode, skin, gestures, dock override and lens pages still follow the workspace). **To confirm:** delete it and
the legacy path in the release after the owner's device has run S9 for a while.

## Decisions to confirm

1. **Mode follows a migrated workspace only.** Presets keep the stored mode (Library on the owner's phone, Standard on
   a fresh install). So the Nova default on the owner's phone is the Library home it borrows, with Library's
   automatic placement of every app; switching to the migrated Standard workspace shows the Standard home.
2. **Lens pages follow the placed pages** in the pager (an iOS Today page is not to the left). Page indicator dots
   include them.
3. **The Finder is the app drawer.** A workspace's Finder expression is not drawn; the drawer presentation setting
   stays (design 3.1 hides it only once the Finder replaces the drawer).
4. **The skin applies to Settings too** (one theme for the launcher). S7 had kept Settings on the global preset.
5. **A borrower forks on its first edit, including the Library relayout** that re-places apps after an install or a
   refresh. That relayout is an edit of the Library arrangement it shows.
6. **The dock pull opens the menu on the claim** (past touch slop), not on a travel threshold.
7. **A Home press lands on the start page** after the first-page reset; with the drawer open it closes the drawer and
   goes to the start page (design: the page the Finder was opened from).
8. **Keep "Home from placed items"** for one more release (above).

## Not done

- The first-run "Choose your home style" sheet (1.3, a nice-to-have): the Nova default is seeded; the preset picker is
  in Settings > Workspaces.
- A workspace's Finder page as the drawer (its expression); the drawer is the Finder.
- The dock's dynamic section of a workspace on the standard dock (the standard dock keeps its own notification
  cards); content levels do not apply to the standard dock's cards or the generated notification-cards page.
- Lens pages before the placed pages; a lens page as the start page (the start page is used only when it is placed).
- Deleting the preview surface, its preview grid and pool editor (`WorkspacePreviewSurface`, `PoolHomePage`,
  `PoolEditController`, `MainActivityDependencies.poolRuntime`): unreachable now, kept for their screenshot tests.
- Renaming the `WorkspacePreview*` classes.
- Deleting the legacy path and "Home from placed items" (decision 8).
- `PoolAutoPages.trimTrailingEmpty` on leaving edit mode.
- Per-workspace `selectedPageId` persistence ("last page per workspace", 9.7).

## Tests

Domain (`core/domain`, JVM, all run here):

- `PoolHomeActiveWorkspaceTest`: the Nova default borrowing the migrated home renders it identically (same shown set,
  same pool); the first edit forks into the workspace and leaves the migrated arrangement unchanged, keeps every item
  and never duplicates a widget instance; a folder edit on the first edit renames the fork's folder only; a restart
  after the fork shows the edit; removing a shared widget from the fork releases nothing; a migrated active workspace
  is the S4 home exactly; other modes keep their migrated arrangements; the landscape phone shows the active
  workspace; a workspace without placed pages borrows and adopts nothing; an installed app is placed only in opted-in
  workspaces, never in the migrated home, and only on `INSTALLED`.
- `WorkspaceHomeTest`: migrated ids and modes (N10), mode follows only migrated workspaces, the shown-workspace rules,
  extra pages (Nova none, iOS Today, migrated none), layout page and start page mapping, gesture bindings layering
  (Niagara, hostile names, launch actions ignored), `withWorkspaceHome` (Niagara's hidden dock and swipe up, the
  stranding fallback, a left edge).
- `HomeReturnTest`: a workspace start page for Start page and Home press, ignored by Restore and First page, a stale
  one falls back.

App (JVM): `PoolHomeLayoutRepositoryTest` adds the Nova default showing the migrated home with its first edit forking
and surviving a restart, and an installed app placed in an opted-in workspace (not written to `HomeLayoutSet`, nothing
on `UPDATED`) (run here with the other Android-free pool tests); `MainActivityCatalogChangeTest` (an install reaches
the install handler); `LauncherShellLibraryReturnTest` rewritten (no trigger switches mode under either stored value);
`SettingsWorkspacesSectionScreenshotTest` replaces the preview-switch screenshot test.

Instrumented (`deviceVerify`, CI only): `DockPullWorkspaceMenuTest` (pull up opens the menu and switches no mode, a pull
into the edge or along the run opens nothing, the dock offers "Workspace menu" and no "Switch to Library");
`LibraryBackReturnTest` rewritten (Back never leaves Library); `StandardTemplateValidationTest` drives the template
through its action now that the row is gone. `DockPullInteractionTest` still covers the mode pull where no menu exists.

Removed with their code: `LibraryOnlyViewModeAvailabilityTest`, `HomeSurfaceModeSettingTest`, `HomeTemplateSettingTest`,
`WorkspacePreviewSettingScreenshotTest`.

## Verification

`./gradlew verify` was **not** run (dl.google.com is blocked here); CI runs `verify deviceVerify`. Run here, in a
scratch Gradle project on Maven Central: the whole `:core:domain` suite, the Android-free app pool files with their
JVM tests, ktlint on domain main and test and on every changed app file, detekt over domain and app (main and test).
The Compose and Android wiring (`LauncherShell`, `StandardHome`, `ImmediateHomePager`, `HomeDockPullBinding`,
`DockMenuPull`, `WorkspaceHomePages`, the view model, `MainActivity`, the settings pages) was **not compiled**; it was
reviewed by hand.

## Owner device checklist

**First: Settings > Backup > Export, keep the file.** Then install and open Riffle once.

- [ ] Home, drawer, dock and Settings are reachable. Settings has a "Workspaces" section at the top and no
      "Workspaces (preview)" switch; Settings > Layout has no Mode, Template or Modes rows, and has "Returning to Home".
- [ ] Every icon, folder and widget is where it was (the Nova default shows your migrated home). Force-stop and reopen:
      still there, and the home does not flash another layout on start. Reboot: still there.
- [ ] Move an icon: it moves. Force-stop, reopen: still moved. (This first edit gave the workspace its own copy.)
- [ ] Add a widget, move, resize, configure (if it offers it), remove it; add another and leave it; force-stop, reopen:
      live.
- [ ] Create a folder, rename it, take an app out; add, reorder and delete a Home page (one with a widget too).
- [ ] Pin, unpin and reorder dock apps; change a dock setting.
- [ ] Pull the dock up: the workspace menu opens. Ctrl+arrow with a keyboard and TalkBack's "Workspace menu" action
      open it too; there is no "Switch to Library" action.
- [ ] Menu: switch to another workspace (for example install TimeScape or iOS from Settings > Workspaces): the home
      changes at once; lens pages follow the home pages and scroll; a jump entry lands on its page; Finder opens the
      drawer. Switch back: your home is exactly as you left it.
- [ ] Switch to the migrated "Standard" workspace (if listed): the Standard home shows; back to Nova: Library again.
- [ ] Niagara (dock hidden, swipe up bound): no dock, swipe up opens the menu. A workspace with a left-edge dock: the
      dock is on the left. A workspace with an Appearance override: the launcher uses that theme.
- [ ] Settings > Workspaces > iOS > New apps: "Also add an icon". Install an app from the store: its icon appears on
      that workspace's home (switch to it). Update an app, or pause and unpause a work profile: no new icons.
- [ ] Returning to Home: Restore keeps your page after an app; First page and Start page go to page 1 or the
      workspace's start page; a Home press goes to the start page. Leaving an app never changes the workspace.
- [ ] Settings > Privacy: content levels are listed; "App icons only" for Notifications hides text on lens pages.
- [ ] Rotate, fold and unfold: you stay on your page; the landscape phone shows the same workspace.
- [ ] Backup export, then restore: everything comes back, widgets live.
- [ ] Settings > Developer > Home from placed items off, restart: the home is still there; on again, restart: still
      there.
- [ ] Report anything wrong with steps and a screenshot; do not paste item content.
