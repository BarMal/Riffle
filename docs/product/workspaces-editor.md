# Workspace editor

Status: WS7 (issue #1356). Companion to `workspaces-sources-lenses.md`, which owns the model. This page
describes how a user edits a workspace and which rules keep every edit valid.

## Principle

The editor never lets the user build an invalid combination, and the domain rejects one anyway. The UI is a
view over domain options (`BindingFlow`, `ExpressionOptions`, `ContainerOptions`); an option is enabled
exactly when `WorkspaceEditor` would accept the resulting edit, so UI and domain cannot disagree.

## The flow: Source -> Expression -> Container -> Confirm

| Step | Offers | Rule |
|---|---|---|
| Source | **Use a saved lens** (see "Saved lenses in the flow"), or sources from the `SourceRegistry` descriptors, with capability badges, plus lens presets (everything, newest first, A to Z, with actions, grouped, grouped by day, latest five, latest one). A custom builder (filter, group, sort, limit, projected fields) reaches the full `Lens` model. | At least one selectable source. Grouped presets are disabled when a chosen source is not `GROUPABLE`. A source whose access is `REQUIRED` is flagged `needsPermission`: it can be chosen (a lens is only a definition) and the UI shows a "needs access" state that routes to the existing explicit user-initiated flow. The editor never requests access. A source whose access is `UNAVAILABLE` cannot be chosen. |
| Expression | Every expression, in catalogue order. | Enabled iff in `LensExpressionValidity.compatibleExpressions(lens, sources)` and the layout can draw it (`LayoutCapabilities`). Disabled entries carry the reasons and are not selectable. See "Grouped lenses" below for the one extension. |
| Container | Widget, Page, Finder page, Page-set, Dock section. | Each is enabled iff the corresponding `WorkspaceEdit` is accepted: Page-set needs a grouped lens and a per-group (`checkPerGroup`), non-sideways expression; Finder needs Categories or AlphaList and no other Finder page; Widget needs a free grid position (found with `GridPlacementEngine` through `WidgetSlots`) on an existing grid page, or a new widget page. |
| Confirm | Summary, **Save as a lens** (inline lenses only), and the single edit to apply. | Re-validated on confirm, so stale state cannot slip through. A Save as lens name must pass the library's rules. |

Re-binding an existing page, widget or dock section (`FlowMode.EditPage/EditWidget/EditDock`) skips the
Container step: the container is fixed, and the Expression step additionally requires that the container accepts
the new binding.

Progressive disclosure: presets first; the custom builder, per-field projection and page-set groupings only on request.

### Grouped lenses (proposal for the design doc)

`compatibleExpressions` for a grouped lens yields only expressions that accept `GROUPED` (Index, Categories).
A page-set, however, draws one flat group per page (`checkPerGroup`), so the useful page-set expressions
(Card stack, List, Icon grid) are never in that list: "notifications per app as card stacks" would be unbuildable.
The editor therefore also offers, for a grouped lens, expressions that pass `checkPerGroup` and do not scroll
sideways, marked `perGroupOnly`. They lead only to a page-set (every other container is rejected by
validation). Nothing invalid is selectable. If the design prefers the strict reading, drop the `perGroup` branch
in `ExpressionOptions.forLens`.

## Edit operations

`WorkspaceEdit` covers add, remove and move pages, set a page's binding, add, move, resize, re-bind and remove widgets,
rename, set the dock's dynamic section and set the skin override. Moves are plain index/cell parameters, so every
reorder has a button equivalent; drag is only a shortcut.

`WorkspaceEditor.apply` returns `Applied(workspace)` or `Rejected(reason)`. The gate: an edit is rejected when its
result has a `WorkspaceIssue` the workspace did not already have (`WorkspaceValidation`, with the layout's
capabilities and the known sources). A valid workspace stays valid; an already-broken stored one can be repaired
step by step.

## Draft, commit, undo

`WorkspaceEditSession` holds the committed workspace, a draft, and bounded undo/redo (50). Edits change only the
draft. `cancel()` returns to the last committed workspace; `commit()` re-checks and promotes the draft. A rejected
edit leaves the session untouched. Item content never enters any of this: previews are transient and are never stored.

## Saved lenses in the flow (issue #1423, preview only)

Saved lenses belong to the layout (`LayoutWorkspaces.library`), and the editor edits one workspace, so the editor session
carries a `LensScope` (the layout's library and its other workspaces) next to the draft: see
[`workspaces-lens-library.md`](workspaces-lens-library.md) "Editor flow". Everything below goes through the same session, so
validity gating, Undo/Redo, Revert and the discard prompt work as before, and a library change is never stored without the
references that need it.

* **Use a saved lens (Source step).** A "Use a saved lens (N)" button above the builder (a short hint instead when the layout
  has none) opens the list: every saved lens of this layout by name, with its sources, shape (flat or grouped) and "used in N
  places". A lens the container being configured cannot use stays in the list, disabled, with the domain's reason: a source
  that is not offered (not on this device, or gone), no expression can draw it here (the nearest miss's `LensIssue` /
  `WorkspaceIssue` reasons, so "A page per group needs a grouped lens" for a flat lens on a page-set), or a definition the
  builder cannot show. A lens is enabled exactly when the Expression step would have an enabled expression for it
  (`SavedLensFlow.choices`), so Next never leads nowhere. Picking one fills the draft with its lens and keeps the reference
  (`LensBinding.ref`, section 4.2/4.3 "Use"); a later edit of the saved lens in Settings propagates to the container.
* **Editing the lens of a referenced binding.** While a reference is held the builder is closed: the Source step shows the
  saved lens (name, sources, shape, use count), says it is shared, and offers **Detach** and **Choose a different saved lens**.
  Changing the lens itself means detaching first (the draft keeps the lens, the builder opens, the binding becomes inline on
  confirm) or changing the saved lens in Settings > Saved lenses, which already owns the dry-run impact preview and the
  detach / save-as-new / cancel choices of section 4.4. Lens edits sent to the reducer while a reference is held are ignored.
  This is the simpler of the two options in section 4.3 (one place decides what an edit to a shared lens breaks).
* **Save as a lens (Confirm step).** A checkbox row "Save as a lens" with a name field pre-filled with a unique suggestion
  (sources plus the preset, for example "Notifications, newest first", " 2", " 3" on a collision, cut to 40 characters).
  The field is capped at 40 characters with a counter and shows the library's reason (blank, taken, too long) as a polite live
  region; the Confirm button is disabled while the name is refused. A full library (100) disables the row with the reason. It
  is not offered for a binding that already uses a saved lens. Confirming creates the `SavedLens` and the binding that
  references it as one step, so one Undo takes both back.
* **Use it in the N other containers (offer).** After a confirmed Save as lens, if other containers of this layout (any
  workspace, including this one) hold an inline binding with exactly the same lens, a polite notice asks "Use "X" in the N other
  containers with an identical lens?" with **Use it there too** and **Not now**. Accepting applies `LensAdoption.adopt` as one
  step (one Undo reverts all of them; the next Undo reverts Save as lens itself). No offer when N = 0. The offer lasts for one
  step: any other action ends it. Adopting only sets the reference; nothing is redrawn differently.
* **Detach in the overview.** Every page, widget and dock section whose binding references a saved lens shows "Saved lens:
  Name" (or "Saved lens missing: using a copy" for a dangling reference) and a 48 dp **Detach** button
  (`LensLibraryEditor.detach`; the container keeps drawing the same lens, Undo reattaches). Re-binding a binding whose saved
  lens is missing detaches it.
* **Undo while a flow is open.** If Undo removes the saved lens an open flow is using (it was created in this session), the flow
  keeps the lens as its own (`SavedLensFlow.reconcile`); a reference that does not resolve is never confirmed.
* **Saving.** Done writes the edited workspace, the new library (only if it changed) and the other workspaces that adopted the
  lens in one repository save (`LensScopeChange.applyTo`), through the existing host (`saveEdited`). With nothing saved-lens
  related changed the write is exactly what it was.
* **Accessibility and wording.** Saved-lens rows are 48 dp single targets that read name, sources, shape, use count, the reason
  when disabled and "Saved lens 2 of 5" as one sentence; Detach buttons describe where and from which lens; the offer, the
  confirmations ("Saved as the lens ... Undo takes it back.", "... is now used in the 2 other containers. Undo reverts them
  together.", "Detached from ...") and the name problem are polite live regions; design tokens only. Under reduced motion nothing
  new animates.

## Testing

Unit tests per piece (saved lenses: `SavedLensFlowTest`, `LensSessionOpsTest`, `EditorSavedLensReducerTest`, `EditorLensTextTest`),
plus seeded property tests (saved lenses: `SavedLensEditorPropertyTest` over the session, `EditorSavedLensPropertyTest` through the
editor's own reducer: no dangling reference, every reference resolves to its library lens, no workspace gains an issue, each commit
is one exact undo step, the open flow never holds an unresolved reference): random edit sequences (valid and invalid) never produce a
workspace failing `WorkspaceValidation`, and for random flow action sequences every container option is enabled
exactly when its edit is accepted and the expression step equals `compatibleExpressions`.

## UI (slice 2)

Package `app/.../launcher/editor/`. `EditorEntry` is the route; `EditorScreen` is its stateless body and
`WorkspaceEditorReducer` its JVM-tested state machine (`(state, action) -> state + effect`). Composables only render
state and forward actions; every validity decision is the domain's.

- Overview: name, pages (move up/down, change content, remove), widgets in grid pages (move left/right/up/down,
  change content, remove), dock section, Undo/Redo/Revert, Done. Every reorder and move is a labelled button.
- Flow: one pane per step with Back/Next, a step counter, and a live preview built from the same expressions and
  containers the workspace uses (`LensBoundExpression`, `PageSetContainerHost`) over the caller's `ContainerServices`
  (the real provider in the app, `StaticLensResultProvider` in tests). Previews are transient and evaluate off the
  main thread inside those hosts; no bitmap decoding happens in the editor.
- Search text: a Source-step field under a chosen Search source sets that lens's own query (debounced, 128-character cap
  with counter, clear button, empty means the shared query); see [`workspaces-lens-queries.md`](workspaces-lens-queries.md).
- Layout: below 600 dp one pane (preview above the flow); at 600 dp and wider (unfolded foldables, tablets) two panes,
  with the preview beside the steps. Content respects `WindowInsets.safeDrawing`.
- Accessibility and motion: disabled choices stay visible with their reason and cannot be selected; rows are the
  whole 48 dp touch target; notices are polite live regions; the step change cross-fades with `RiffleMotion.standard`
  (a snap under reduced motion). Only design tokens (`RiffleSpacing/Shapes/Elevation/Motion`) are used.
- Permissions: a source whose access is `REQUIRED` shows "Needs access" with a rationale and a "Review access"
  button. The editor never prompts; the button calls `onRequestSourceAccess(sourceId)`.

### Integration contract (for the workspace menu / shell, WS6)

1. On `WorkspaceMenuEffect.EditWorkspace(id)`, load that workspace from the layout's `LayoutWorkspaces`.
2. Build `sources = SourceChoices.build(registry.descriptors(), access)` where `access` maps gated sources to
   `SourceAccess` (read from the existing status holders; reading never prompts).
3. Show `EditorEntry(workspace, sources, services, onSave, onClose, capabilities = layoutCapabilities,
   onRequestSourceAccess = { id -> if (id == SourceIds.CALENDAR) dispatch(LauncherShellAction.RequestCalendarAccess) },
   scope = LensScope.of(layout, id))`. `scope` is the layout's saved lenses and other workspaces (leave it default for a host
   without saved lenses).
4. `onSave(saved, change)` receives the committed `Workspace` and a `LensScopeChange` (the new library, if changed, and the other
   workspaces that adopted a lens): `change.applyTo(layout.replace(id) { saved })` and persist through `WorkspaceRepository`
   (the app's `saveEdited` does exactly this, in one save). `onClose` follows every exit, saved or not.

The editor is not wired into the shell in this slice (that is WS6's area); there is no settings/dev entry yet, because
a dev entry needs the shell to own the workspace and registry. Skin override has a domain edit
(`WorkspaceEdit.SetSkinOverride`) but no UI yet: the skin id vocabulary belongs to the skin settings.

### Manual validation

On a device or emulator with the editor hosted: add a notifications page-set and a calendar widget; confirm the
calendar shows "Needs access" and no system dialog appears until "Review access" is tapped; reorder pages with the
buttons only; undo, redo, revert; close with changes and confirm the discard prompt; repeat with TalkBack on, with
"Remove animations" on, rotated, and on an unfolded foldable (two panes).
