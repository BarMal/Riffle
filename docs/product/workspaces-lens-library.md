# Workspaces: saved lenses and the per-layout lens library (S5, as built)

Status: implemented in `core/domain` (slice S5 of the WS10 design, `workspaces-configuration.md` section 4).
This page records what exists, the decisions taken while building it, and what is not done. The design
rationale stays in section 4 of the configuration doc; this page does not repeat it.

## What exists

Package `com.riffle.core.domain.launcher.workspace` (all pure Kotlin, no Android types):

| Type | File | Role |
| --- | --- | --- |
| `LensId`, `SavedLens(id, name, lens, origin)`, `LensOrigin.Preset` | `LensLibrary.kt` | A named, reusable lens definition. `origin` marks a preset-installed lens. |
| `LensLibrary` | `LensLibrary.kt` | Ordered, per layout, cap 100, names trimmed 1..40 and unique case-insensitively. `tryAdd` (with reasons), `rename`, `update`, `duplicate`, `move`, `remove`, `addCopy`, `nameProblem`. Operations that cannot apply return the receiver. |
| `LensBinding.ref: LensId? = null` | `Container.kt` | Additive. `lens` stays the lens to draw (a snapshot of the library lens when `ref` resolves). |
| `LayoutWorkspaces.library` | `LayoutWorkspaces.kt` | Optional field, default empty, so existing behaviour is identical. `repaired(...)` gained an optional `library`. |
| `LensLibraryOps` | `LensLibraryOps.kt` | `dependents`, `resolve`, `danglingRefs`, `previewEdit`, `applyEdit`, `remove`, `rehydrate`, `detachDangling`, `makeIndependent`. All scoped to one `LayoutWorkspaces`. |
| `LensRetarget` (internal) | `LensRetarget.kt` | The shared dry run behind `previewEdit`, `applyEdit` and `remove(ReplaceWith)`. |
| `WorkspaceBindings` (internal) | `WorkspaceBindings.kt` | One walker over every binding site: bound page, page-set, grid widget, dock section. |
| `LensLibraryCodec` | `LensLibraryCodec.kt` | Encoding of the library. |
| `LensLibraryEditor` | `editor/LensLibraryEditor.kt` | "Use saved lens", "Save as lens", "Detach" and "Edit saved lens" as pure, gated operations. |
| `PresetLensInstaller` | `preset/PresetLensInstaller.kt` | Install a preset with saved lenses; reset to preset. |

### Resolution and the snapshot invariant

Effective lens = the library lens when `ref` resolves in this layout's library, else the inline snapshot.
The stored workspace stays self-contained: every library operation (`applyEdit`, `remove`) rewrites the
inline `lens` of each dependent in the same returned `LayoutWorkspaces`, so the invariant "if a ref resolves,
`binding.lens` equals the library lens" holds after every operation and is re-established on decode
(`rehydrate`). Nothing that reads `binding.lens` (validators, planners, evaluator, hosts) changed.

A dangling ref (library lacks the id) never crashes or blanks a container: it keeps drawing its snapshot and
is reported by `danglingRefs` / `rehydrate().dangling`. The remedies are `detachDangling` or "Save as lens".

### Edit, delete, preview

* `previewEdit(layout, id, newLens, sources, capabilities)` returns `EditImpact(dependents, wouldBreak)`. A
  dependent breaks when `WorkspaceValidation` reports an issue after the edit that the workspace did not
  have before (the same gate `WorkspaceEditor` uses), so page-set, widget, dock and axis rules are all
  covered without a second validator. `wouldBreak` carries the `WorkspaceIssue`s.
* `applyEdit(..., policy)`: `BreakPolicy.REJECT` (default) refuses with the impact;
  `DETACH_BROKEN` applies the edit, and broken dependents are detached (they keep their old lens inline).
* `remove(layout, id, policy)` is never blocked: `RemovePolicy.Detach` (default) or `ReplaceWith(id)`;
  dependents the replacement would break are detached instead and listed in `Applied.detached`.

### Copy semantics (Q3, N10)

* `WorkspaceSet.copyFromOtherLayout` copies the whole library with fresh `LensId`s (names, order, origins
  kept; unreferenced lenses included) and rewrites every ref through the old-to-new map. Nothing is shared
  with the source, and a ref that was already dangling stays dangling-with-snapshot.
  `WorkspaceCopy` itself is unchanged; remapping is `LensLibraryCopy.remap`.
* A lens can be copied to another layout with `LensLibrary.addCopy` (fresh id, " 2" collision suffix).
* **Duplicating a workspace inside one layout shares its refs** (section 4.3: the clone is a new workspace
  using the same saved lenses; that is what "reusable" means). `LensLibraryOps.makeIndependent` is the
  "Make independent" action. This deliberately differs from "duplicate copies lenses with fresh ids", which
  applies only across layouts; flagged for the owner (see Open questions).

### Presets (Q4)

Smallest correct rework: the catalog is unchanged and keeps inline lenses. `PresetLensInstaller.install`
promotes at install time:

* Every binding of the installed variant except home-grid pages becomes a library entry with origin
  `Preset(presetId, key)`, where `key` is the container's catalog part id (`finder`, `inbox`, `w-media`,
  `dock`...), stable across releases and across compact/expanded variants.
* An existing entry with that origin is **reused as the user left it** (reinstalling adds no lens). If the
  reused lens no longer pairs with the preset's expression, that binding stays inline.
* Name collisions with a different lens: `"<Label> (<Preset>)"`, then `"<Label> (<Preset> 2)"`. A preset never
  renames or overwrites a user lens; library full also falls back to inline.
* `PresetInstaller.addPreset` now goes through the installer; `installPreset`, `newInstallLayout` and
  `withDefaultsFor` are unchanged (the first-run Nova default stays inline).
* `reset(layout, workspaceId, preset, posture, restoreLenses)` rebuilds pages, dock section and gestures from
  the catalog keeping id and name. Saved lenses are kept as the user left them, except a deleted preset lens,
  which is re-added. `restoreLenses = true` also resets every lens with this preset's origin to the catalog
  definition (through `applyEdit(DETACH_BROKEN)`, so other dependents follow, and any it would break detach).
  `Workspace.presetId` was **not** added (it would touch the shared `Workspace`); the caller supplies the
  preset to reset to.

### Codec

`WorkspaceSetCodec` version is now 2. Per layout an optional `"library": {"lenses": [{"id","name","lens","origin"?}]}`
(omitted when empty), per binding an optional `"ref"`. A v1 blob has neither and decodes unchanged. Decode
never throws: library entries that fail to decode are dropped (their bindings become dangling-with-snapshot),
survivors are repaired (unique ids and names, cap, 40 chars), an unknown origin type is ignored, a blank or
non-string `ref` reads as inline, and after decoding each layout `rehydrate` makes resolving refs take the
library lens. Every ref-carrying binding also stores its lens, so a v1 reader that ignores `library` and
`ref` still draws every container. The per-workspace blob version (`WorkspaceCodec`) stays 1: `ref` is purely additive.

### Editor operations

`LensLibraryEditor` (domain `editor` package) over a `LayoutWorkspaces`, targeting a binding by `FlowMode`
(`EditPage`, `EditWidget`, `EditDock`):

* `useSavedLens(layout, workspaceId, target, id, expression?, context)`: sets `LensBinding(saved.lens, expression, id)`
  through `WorkspaceEditor.apply`, so an unpairable lens is rejected with the issues (`LibraryEditRejection.Editor`).
* `saveAsLens(..., name, ids, context)`: adds the binding's lens to the library and sets the ref; name problems
  come back as `LibraryEditRejection.Library(Problem(...))`; the result carries the new `lensId`.
* `detach(...)`, and `editSavedLens(layout, id, newLens, policy, context)` (gated by `previewEdit`).
* `bindingFor(layout, id, expression)` builds a binding for `FlowMode.Add`, whose container choice stays with the flow.

### Editor flow (as built, issue #1423)

The operations above are wired into the workspace editor flow; see [`workspaces-editor.md`](workspaces-editor.md)
("Saved lenses in the flow") for the user-facing behaviour. What was added to the domain (all in the `editor` package,
pure Kotlin):

* `LensScope(library, others)`: what a saved-lens operation touches besides the edited workspace (the layout's library
  and its other workspaces). `scope.layoutWith(draft)` builds the `LayoutWorkspaces` the library operations work on;
  `LensScope.of(layout, id)` splits one back. `WorkspaceEditSession` carries it (`scope`, `committedScope`) and every
  undo/redo snapshot holds it, so Save as lens and adoption are one exact undo step; `isDirty` includes it.
  `scope.changeFrom(base)` / `LensScopeChange.applyTo(layout)` give the host exactly what to write: the library if it
  changed and the other workspaces that changed, in the same single save as the edited workspace.
* `BindingFlowState` gained `ref` (the saved lens in use; while set the draft equals that library lens), `choosingSaved`
  and `saveAs`; `BindingFlowContext` gained `scope`. `SavedLensFlow` (choices with `SavedLensBlock` reasons, `use`,
  `canSaveAs`, `startSaveAs`, `plan`, `reconcile`) and the reducer actions `ShowSavedLenses`, `ShowBuilder`, `UseSavedLens`,
  `DetachSavedLens`, `StartSaveAsLens`, `CancelSaveAsLens`, `SetSaveAsName`. Lens edits are ignored while a reference is
  held. `BindingFlow.confirm` returns `FlowOutcome.Ready(edit, result, saved)`; the binding it builds carries the new or
  existing `ref`, and a reference that no longer resolves is never written (`SavedLensFlow.current`).
* `LensAdoption.identical(layout, id)` / `adopt(layout, id)`: the "N other containers with an identical lens" rule.
  A container qualifies when its binding is inline (`ref == null`) and its `Lens` equals the saved lens exactly
  (sources, filter, group, sort, limit, projection and per-source parameters). Adopting only sets `ref`, so nothing a
  container draws changes and none can become invalid. A binding that already uses another saved lens is left alone,
  even if that lens is identical. The expression need not match.
* `LensSessionOps`: `commit(session, flowState, context)` (the flow's edit plus the new library entry as one session step,
  with an `AdoptOffer` only when N > 0), `adopt`, `detach` (through `LensLibraryEditor.detach`), `savedLensAt`,
  `identicalCount`.

`LensLibraryEditor.useSavedLens` / `saveAsLens` are still the operations for hosts that edit a stored `LayoutWorkspaces`
directly; the editor goes through `LensSessionOps` because its draft is a session, not a layout.

## Tests

`LensLibraryTest`, `LensLibraryOpsTest`, `LensLibraryCodecTest`, `LensLibraryEditorTest`,
`PresetLensInstallerTest`, and the seeded `LensLibraryPropertyTest` (150 seeds x 40 operations: no dangling
refs, workspaces always pass `WorkspaceValidation`, snapshots always equal the library lens, unique names and
ids, copy-from-layout shares no ids and keeps drawing the same lenses, codec round trip of every reachable layout).

## Not done

* The library UI is built: Settings "Saved lenses" with the lens builder, "Used by" and Copy to layout, see
  [`workspaces-saved-lenses-page.md`](workspaces-saved-lenses-page.md), and the editor flow uses it (use a saved lens,
  Save as lens, the identical-containers offer, Detach): see "Editor flow" above.
* Changing the lens of a referenced binding inside the editor flow: it asks to detach first, or to change the saved lens
  in Settings > Saved lenses (which shows the dry-run impact). An in-flow "edit the saved lens" with the impact preview was not
  built (decision to confirm, see [`workspaces-editor.md`](workspaces-editor.md)).
* `Workspace.presetId` and a "Reset to preset" menu entry; the dry-run impact preview of `restoreLenses` is available
  through `previewEdit` per lens but `reset` itself applies with `DETACH_BROKEN` and does not ask.
* Placed-item (section 9) handling in reset; backup/restore of the library (section 5) beyond the workspace set codec.
* Preset catalog files still build inline lenses; promotion is derived from container part ids rather than declared
  `PresetLens` data. Declaring them per preset would let one lens be shared between two containers.

## Open questions

* Within-layout workspace duplicate shares refs (design 4.3) rather than copying lenses with fresh ids. If the owner
  wants the clone independent, `duplicate` can call `makeIndependent` or copy entries; one-line change plus tests.
* Should reinstalling a preset whose catalog lens changed in a newer release update the user's untouched entry?
  Today an entry is reused as is; only `reset(restoreLenses = true)` refreshes it.
