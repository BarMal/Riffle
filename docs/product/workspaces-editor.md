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
| Source | Sources from the `SourceRegistry` descriptors, with capability badges, plus lens presets (everything, newest first, A to Z, with actions, grouped, grouped by day, latest five, latest one). A custom builder (filter, group, sort, limit, projected fields) reaches the full `Lens` model. | At least one selectable source. Grouped presets are disabled when a chosen source is not `GROUPABLE`. A source whose access is `REQUIRED` is flagged `needsPermission`: it can be chosen (a lens is only a definition) and the UI shows a "needs access" state that routes to the existing explicit user-initiated flow. The editor never requests access. A source whose access is `UNAVAILABLE` cannot be chosen. |
| Expression | Every expression, in catalogue order. | Enabled iff in `LensExpressionValidity.compatibleExpressions(lens, sources)` and the layout can draw it (`LayoutCapabilities`). Disabled entries carry the reasons and are not selectable. See "Grouped lenses" below for the one extension. |
| Container | Widget, Page, Finder page, Page-set, Dock section. | Each is enabled iff the corresponding `WorkspaceEdit` is accepted: Page-set needs a grouped lens and a per-group (`checkPerGroup`), non-sideways expression; Finder needs Categories or AlphaList and no other Finder page; Widget needs a free grid position (found with `GridPlacementEngine` through `WidgetSlots`) on an existing grid page, or a new widget page. |
| Confirm | Summary and the single edit to apply. | Re-validated on confirm, so stale state cannot slip through. |

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

## Testing

Unit tests per piece, plus seeded property tests: random edit sequences (valid and invalid) never produce a
workspace failing `WorkspaceValidation`, and for random flow action sequences every container option is enabled
exactly when its edit is accepted and the expression step equals `compatibleExpressions`.
