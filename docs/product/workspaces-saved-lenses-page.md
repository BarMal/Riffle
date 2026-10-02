# Settings > Saved lenses (WS10, as built)

Issue #1420; part of #1363, #1364 and #1385. Design: [`workspaces-configuration.md`](workspaces-configuration.md) 2.1, 2.5 and
section 4; the domain it drives is [`workspaces-lens-library.md`](workspaces-lens-library.md). Like the Workspaces and Sources pages
it exists **only while Settings > Developer > Workspaces (preview) is on**: it is `SettingsPage.LENSES`, offered from the Developer
section, never in the main page, Settings search or launcher search. With the preview off nothing here runs.

## What it does

- **List** of the layout's saved lenses sorted by name (case-insensitive): name, source(s), shape (flat or grouped), "used in N
  places", "from a preset". The layout selector is the Workspaces page's (`SelectSettingsLayoutDeviceClass`); each layout has its own
  library. **+ New** (disabled with the reason at the 100-lens limit).
- **Row**: one focusable element (tap opens the lens) with a spoken summary ("Notes by app. Notifications · grouped · used in 2 places.
  Lens 2 of 5. Double tap to open"); Rename, Duplicate, Copy to each other layout and Delete are the overflow menu and TalkBack custom
  actions of the row.
- **Detail / builder**: name (counter, 1..40 chars, unique), the live preview, the shared lens builder (sources with honest status,
  presets, filter / group / sort / limit, search text), "Can be drawn as", the dry-run list of containers an edit would break, **Used by**,
  Save, and Duplicate / Copy to / Delete. Unfolded (600 dp and up): list left, builder right.
- **Sources** come from the registry (`SourceDescriptor`s), in the Sources page's order, with Settings > Sources' status as words:
  Needs access (rationale, and the existing explicit "Review access" button; nothing prompts by itself), Off ("turned off in Settings >
  Sources; a lens can still use it"), Unavailable (cannot be picked), Checking. Statuses are read through `SourcesSettingsController`
  only while a lens is open, then released.
- **Validity (4.4)**: Save is disabled with reasons from the domain (`LensProblem`: no source, nothing on this layout can draw the lens
  per `ExpressionOptions`/`LensExpressionValidity`, name problems, library full). When an edit would break containers
  (`LensLibraryOps.previewEdit`) they are listed with the domain's `WorkspaceIssue` reasons and Save becomes "Save...": **Save and detach
  those containers** (`BreakPolicy.DETACH_BROKEN`), **Save as a new lens** (original untouched), or Cancel. The action refuses
  (`BreakPolicy.REJECT`) unless the choice was made.
- **Used by** (`LensLibraryOps.dependents`): "Standard > Page 2 (a page per group), Card stack". Tapping a row opens the workspace
  editor through the existing `EditWorkspace` route, only when the viewed layout is the one this device shows (the editor opens the
  active layout's workspaces); otherwise rows are text with the reason.
- **Delete** asks first; with users it offers *keep the containers as they are* (`RemovePolicy.Detach`) or *use another saved lens*
  (`ReplaceWith`, with a dry run of how many it cannot serve; they stay detached). Never blocked. Undo snackbar.
- **Copy to <layout>**: a one-time copy with a fresh id, name collision suffix (" 2"), origin dropped, nothing linked; the dialog shows the
  target's count and the new name. Undo restores the target layout only.
- All changes announce in the Settings snackbar (polite live region). Save, Delete and Copy offer **Undo**; any later change or leaving
  the page ends the offer.

## Structure

- Domain (`core/domain`, `workspace/settings`): `LensesSettingsPlanner` (list, copy targets, replacements), `LensDetailPlanner` (detail
  model, `copyName`), `LensesSettingsAction` (`Create`, `Save`, `Rename`, `Duplicate`, `Delete`, `CopyToLayout`; pure over `WorkspaceSet`,
  returning `LensesSettingsChange` with `undo`), `LensSession`, `LensSourceChoices`. All edits go through `LensLibrary` and
  `LensLibraryOps`, so the library invariants hold after every action.
- App: `LensesSettingsController` = `LensesQueries` (read) + `LensBuilderSessions` (the lens being built; survives rotation, dropped when
  the page closes) + dispatch/Undo/announcements, over `WorkspaceRepository` (the cached repository in the app). Composables:
  `SettingsLensesPage.kt`, `LensesSettingsRows.kt`, `LensesSettingsDetail.kt`, `LensesSettingsUsedBy.kt`, `LensesSettingsDialogs.kt`,
  wording in `LensesSettingsText.kt`.
- Additive edits to shared files: `SettingsPage.LENSES`, one `SettingsPageContent` branch, a Developer row in `WorkspacePreviewSetting`,
  `WorkspaceSettingsHost.lenses` / `.previewServices`, their construction in `LauncherShell`.

## The reusable lens builder (for the editor-integration work)

The editor's lens step was entangled only by its action type, so the logic was extracted, not forked:

- `LensDraftAction` + `LensDraftReducer.reduce(draft, action, sources)` (domain, `editor` package): the rules for toggling sources
  (selectable only), presets (disabled when a source cannot group), filter, group, sort, limit and per-source query. `BindingFlowReducer`
  now delegates every lens action to it (`BindingFlowAction.toDraftAction()` / `LensDraftAction.toFlowAction()`), so behaviour is
  identical and the existing editor tests cover it.
- `LensBuilder(draft, sources, onAction, onRequestSourceAccess, modifier, queryFlush)` (app, `editor/LensBuilder.kt`, internal): the
  Source step's body. `EditorSourceStep` is now a thin wrapper. `SourceChoice.status` (additive, default null) lets a host show a source's
  status in words; the editor leaves it null and is unchanged.
- `previewTargetFor(lens, EditContext)` (`PreviewTarget.kt`) builds the live preview for a lens on its own; `EditorPreview` draws it.
- `LensSession` / `LensSourceChoices` (domain `settings`) are the host side used by this page; `LensLibraryEditor` (use / save as / detach)
  remains the domain operation set for `BindingFlow`, whose wiring is not part of this slice.

## Decisions to confirm

1. A lens copied to another layout drops its preset `origin` (it is a user lens there).
2. "Save as a new lens" names the copy with the typed name when free, else "<name> copy", "<name> copy 2".
3. Used-by rows open the editor only for the layout this device shows; there is no per-container deep link.
4. Switching the layout tab while a lens is open discards the unsaved draft (the builder belongs to one layout).
5. Detail changes ask "Discard changes?" on Back; the system Back leaves the lens first, then the page.
6. The preview is live over real data (same provider as the containers), so it can show private items on screen; it stores nothing.

## Not done

- "Use saved lens" / "Save as lens" in the editor `BindingFlow`, and "use it in the N other containers with an identical lens".
- Reordering lenses (the list is sorted by name; `LensLibrary.move` is unused here), per-container deep links, search in the list.
- Dangling-ref notice ("Saved lens 'X' is missing") and Make independent on this page.
- Roborazzi goldens were not generated here (CI records them); `./gradlew verify` was not run locally, only the scratch checks below.

## Verification

Scratch Gradle project (Maven Central only): domain and Compose-free app tests (`LensesSettingsTest`, `LensesSettingsPropertyTest` with
seeded sequences, `LensDraftReducerTest` incl. flow/builder parity, `LensesSettingsControllerTest`, `LensesSettingsTextTest`,
`PreviewTargetTest`), plus the existing editor and library tests; ktlint and detekt with the repo config; the Compose files compiled
against Compose Multiplatform 1.7 with stubs for the rest of the app. Roborazzi: `LensesSettingsScreenshotTest` (compact, dark, large
font, unfolded, empty, builder, breaks dialog; no typing, no dialog with a text field). CI runs `./gradlew verify deviceVerify`.

## Checklist for the owner (device)

- [ ] Preview off: no Saved lenses row anywhere; Settings unchanged. Preview on: Settings > Developer > Saved lenses.
- [ ] List shows this layout's lenses by name with sources, flat/grouped and "used in N places"; switch the layout tab.
- [ ] + New: Save is blocked until a source is chosen and the reason is spoken; create one, it opens.
- [ ] Builder: pick sources (Needs access / Off read as words, nothing prompts), presets and custom controls; the preview follows.
- [ ] Edit a lens used by a page-set to flat: the affected containers are listed; Save... offers detach / save as new / cancel.
- [ ] Used by: tap a row on this device's layout; the editor opens that workspace.
- [ ] Rename, Duplicate (unique "copy" name), Copy to another layout (counts, one-time), Delete (detach vs replace) and Undo each.
- [ ] 100 lenses / 40 characters: the messages are clear. TalkBack: rows read as one item with actions; announcements are polite.
- [ ] Large font, rotation (the lens being built survives), unfolded two panes, reduced motion.
