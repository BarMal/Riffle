# Workspaces: saved lenses and the per-layout lens library (S5, as built)

Status: implemented in `core/domain` (slice S5 of the WS10 design, `workspaces-configuration.md` section 4).
This page records what exists, the decisions taken while building it, and what is not done. The design
rationale stays in section 4 of the configuration doc; this page does not repeat it.

## What exists

Package `com.riffle.core.domain.launcher.workspace` (all pure Kotlin, no Android types):

| Type | File | Role |
| --- | --- | --- |
| `LensId`, `SavedLens(id, name, lens, origin)`, `LensOrigin.Preset` | `LensLibrary.kt` | A named, reusable lens definition. `origin` marks a preset-installed lens and the definition it was installed with. |
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
  `Preset(presetId, key, installed)`, where `key` is the container's catalog part id (`finder`, `inbox`, `w-media`,
  `dock`...), stable across releases and across compact/expanded variants, and `installed` is the catalog
  definition the entry was installed (or last refreshed) with. Identity is `presetId` + `key`
  (`Preset.sameCatalogLens`); `installed` only says whether the user edited it.
* An existing entry with that origin is **reused** (reinstalling adds no lens), and **refreshed when untouched**
  (owner decision, built in S7; see "Untouched preset lenses" below). If the reused lens no longer pairs with the
  preset's expression, that binding stays inline.
* Name collisions with a different lens: `"<Label> (<Preset>)"`, then `"<Label> (<Preset> 2)"`. A preset never
  renames or overwrites a user lens; library full also falls back to inline.
* `PresetInstaller.addPreset` now goes through the installer; `installPreset`, `newInstallLayout` and
  `withDefaultsFor` are unchanged (the first-run Nova default stays inline).
* `reset(layout, workspaceId, preset, posture, restoreLenses)` rebuilds pages, dock section and gestures from
  the catalog keeping id and name. Edited saved lenses are kept as the user left them, untouched ones are
  refreshed (as on install), and a deleted preset lens is re-added. `restoreLenses = true` resets every lens with
  this preset's origin to the catalog definition, edited or not (through `applyEdit(DETACH_BROKEN)`, so other
  dependents follow, and any it would break detach), which makes them untouched again. The caller supplies the
  preset to reset to (Settings reads it from `Workspace.presetId`, added later by the settings slice).

### Untouched preset lenses (owner decision 2026-10-23, built in S7)

"Reinstalling a preset refreshes library lenses the user hasn't edited."

* **Tracking.** `LensOrigin.Preset.installed: Lens?` is the catalog definition the entry was installed or last
  refreshed with. `SavedLens.isUntouchedPresetLens` is `origin.installed == lens`. Entries without an origin are the
  user's own and are never touched by a preset.
* **Rule** (`PresetLensInstaller.install` and `reset`, one pre-pass over the variant's planned lenses before
  promotion): for each existing entry with the same `presetId` + `key`:
  * lens equals the current catalog lens: nothing changes except recording `installed` (so a legacy entry becomes
    trackable);
  * untouched (lens equals `installed`) and the catalog changed: the entry takes the new definition through
    `LensLibraryOps.applyEdit(DETACH_BROKEN)`, so every dependent's snapshot follows in the same returned layout
    (refs stay valid; a dependent the new definition would break is detached and keeps drawing the old lens), and
    `installed` becomes the new definition;
  * edited (lens differs from `installed`), or `installed` unknown: left exactly as it is.
* **Rename-only is untouched (decision).** Only the definition is compared, never the name: the name is the user's
  label, and a renamed lens still behaves as the catalog shipped it, so it is refreshed and keeps the user's name.
  A stored per-lens search query is part of the definition (section 13), so typing one into a preset lens counts as
  an edit.
* **Legacy entries** (installed before `installed` was recorded) count as edited, because nothing proves the user
  left them alone; the first reinstall where the entry already equals the catalog records `installed`, so later
  releases can refresh it.
* Copies: `copyFromOtherLayout` keeps origins including `installed`; Duplicate and Copy to layout drop the origin
  (the copy is the user's own).

### Codec

`WorkspaceSetCodec` version is now 2. Per layout an optional `"library": {"lenses": [{"id","name","lens","origin"?}]}`
(omitted when empty), per binding an optional `"ref"`. A v1 blob has neither and decodes unchanged. Decode
never throws: library entries that fail to decode are dropped (their bindings become dangling-with-snapshot),
survivors are repaired (unique ids and names, cap, 40 chars), an unknown origin type is ignored, a blank or
non-string `ref` reads as inline, and after decoding each layout `rehydrate` makes resolving refs take the
library lens. Every ref-carrying binding also stores its lens, so a v1 reader that ignores `library` and
`ref` still draws every container. The per-workspace blob version (`WorkspaceCodec`) stays 1: `ref` is purely additive.

A preset origin is `{"type": "preset", "preset", "key", "installed"?}`; `"installed"` (S7) is a lens in the
`LensCodec` format, written only when known. It is additive: the set version stays 2, an older blob decodes with it
unknown (counts as edited), an undecodable one reads as unknown, and an older reader ignores it. It holds a catalog
lens definition only, never item content. Backup carries it unchanged (the library rides the workspace set section).

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
`PresetLensRefreshTest` (S7): the installed definition is recorded; reinstall refreshes an untouched entry and its
dependents follow through the same ref; an edited entry is left alone; rename-only stays untouched and keeps the
name; reset refreshes untouched entries and keeps edited ones unless `restoreLenses`; a legacy entry without a
recorded definition is not refreshed but is recorded once it equals the catalog; codec and backup round trips keep
`installed`, and a restored untouched entry still refreshes. `LensLibraryCodecTest` covers older and broken
`installed` values.

## Not done

* The library UI is built: Settings "Saved lenses" with the lens builder, "Used by" and Copy to layout, see
  [`workspaces-saved-lenses-page.md`](workspaces-saved-lenses-page.md), and the editor flow uses it (use a saved lens,
  Save as lens, the identical-containers offer, Detach): see "Editor flow" above.
* Changing the lens of a referenced binding inside the editor flow: it asks to detach first, or to change the saved lens
  in Settings > Saved lenses (which shows the dry-run impact). An in-flow "edit the saved lens" with the impact preview was not
  built (decision to confirm, see [`workspaces-editor.md`](workspaces-editor.md)).
* The dry-run impact preview of `restoreLenses` is available through `previewEdit` per lens but `reset` itself
  applies with `DETACH_BROKEN` and does not ask. The automatic refresh of untouched entries (S7) also applies with
  `DETACH_BROKEN` and does not ask or announce which entries it refreshed; Settings > Saved lenses has no "from
  preset, unedited" badge yet.
* Refresh happens only when the user installs or resets a preset; nothing refreshes untouched entries when the app
  updates on its own.
* Placed-item (section 9) handling in reset; backup/restore of the library (section 5) beyond the workspace set codec.
* Preset catalog files still build inline lenses; promotion is derived from container part ids rather than declared
  `PresetLens` data. Declaring them per preset would let one lens be shared between two containers.

## Owner decisions (2026-10-23, #1363)

* **Duplicate shares refs** (as built, design 4.3). Per-binding Detach stays available for an independent copy.
* **Reinstalling a preset refreshes untouched entries.** When a preset's catalog lens changed in a newer release, an
  entry the user never edited takes the new definition; an edited entry is left alone. **Built in S7**: see "Untouched
  preset lenses" above (tracking by `LensOrigin.Preset.installed`; a rename alone keeps an entry untouched, a decision
  for the owner to confirm).
