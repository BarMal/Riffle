# Workspace presets (WS8)

Five presets ship as ordinary workspaces (see `workspaces-sources-lenses.md`): Nova-style (the default),
iOS-style, TimeScape, Niagara and Kvaesitso-style. They live in
`core/domain/.../launcher/workspace/preset/` as plain data and add nothing the model cannot already
express.

## What a preset is

- `WorkspacePreset(id, name, description, compact, expanded, skinHintId)`: one workspace per posture,
  because each layout holds its own workspaces. `PresetPosture.of(deviceClass)`: PHONE and
  PHONE_LANDSCAPE are compact; FOLDABLE (unfolded), TABLET and DESKTOP are expanded.
- Lenses and containers only: no item content, no permission prompts. Calendar, notification and media
  widgets merely reference their sources; they report `PermissionRequired` when observed, and the page
  shows the source's own permission affordance.
- Ids in the catalog are deterministic (`preset:<id>:<posture>[:<part>]`) and never reach a user.
  `WorkspacePresets.installPreset(preset, posture | deviceClass, ids)` returns a deep copy with fresh
  workspace and container ids (the WS5 copy helper). Clone-and-edit is therefore the same as editing any
  workspace; nothing links an installed workspace back to its preset.
- `skinHintId` is an opaque suggestion for the UI. It is not written into the installed workspace
  (`skinOverrideId` stays null).
- Default: `WorkspacePresets.defaultFor(deviceClass)` is the Nova-style workspace.
  `PresetInstaller.newInstallLayout` / `withDefaultsFor` / `addPreset` are the small helpers that put an
  installed preset into the existing `WorkspaceSet` APIs (a minimal integration aid; `WorkspaceMigration`
  is unchanged and still builds the migrated defaults for existing installs).

Home-grid pages use `home.grid` with `GroupKeyIs("home")`, the page id of `HomeLayoutDefaults.standard`,
so they show whatever the user placed on that HomeLayout page.

## Nova-style (default)

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Home | `home.grid` page `home` | IconGrid | Page |
| Finder | `apps.all` by title | AlphaList | Page, role Finder |

Dock: pinned items unchanged, no dynamic section. Finder choice: AlphaList, matching the Nova and Pixel
drawer (alphabetical, fast-scroll index, search). Compact and expanded variants are structurally identical;
grid sizes come from `HomeLayoutSettings`.

## iOS-style

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Today | Calendar next event; Recent apps; Media | Card; IconRow; Card | Widget grid (4x5 compact, 8x4 expanded) |
| Home | `home.grid` page `home` | IconGrid | Page |
| Library | `apps.all` grouped by group key (category) | Categories | Page, role Finder |

## TimeScape

Compact (folded):

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Now | Media; Calendar next event; Quick actions | Card; Card; IconRow | Widget grid 4x5 |
| Inbox | Notifications grouped by app, newest first | CardStack per group | Page-set |
| Recents | Recent apps | List | Page |
| Finder | `apps.all` grouped by group key | Categories | Page, role Finder |

Dock dynamic section: Notifications, newest first, 5 slots, IconRow.

Expanded (unfolded):

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Now | Media; Calendar next event; Quick actions; Recent apps | Card; Card; IconRow; List | Widget grid 8x6 |
| Inbox | Notifications grouped by app; Notifications (all) | Index (3 cols); CardStack (5 cols) | Widget grid 8x6 |
| Finder | `apps.all` grouped by group key | Categories | Page, role Finder |

Same dock dynamic section.

## Niagara

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Pinned | `home.grid` page `home` | List | Page |
| Recents | Recent apps | List | Page |
| Finder | `apps.all` by title | AlphaList | Page, role Finder |

## Kvaesitso-style

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Today | Calendar next event; Media; Recent apps (expanded also Quick actions) | Card; Card; List (IconRow) | Widget grid (4x6 compact, 8x4 expanded) |
| Finder | `apps.all` by title (the searchable source) | AlphaList | Page, role Finder |

## Gaps (nothing was invented; closest valid arrangement used)

1. **No master/detail link between containers.** The unfolded TimeScape Inbox cannot have the Index pane
   select which group the CardStack shows. The two are independent widgets: the Index lists notifications
   grouped by app, the CardStack shows all notifications newest first. The compact Inbox keeps the
   per-app page-set. Needs either a linked-pane container or a selection binding between widgets.
2. **Dock edge is not part of a workspace.** "Dock on the left edge" for unfolded TimeScape lives in
   `DockModel`, not `Workspace`; the preset only sets the dynamic section.
3. **Dock tap-to-jump** (a dock notification icon jumps to its group's page) is a runtime behaviour of the
   dynamic section, not expressible in the lens binding.
4. **No search source adapter and no query term in `Lens`.** `SourceIds.SEARCH` has no WS1 adapter and
   `LensFilter` has no text predicate, so "search-forward" is approximated by a Finder over `apps.all`
   (the `SEARCHABLE` source). Search results for contacts, files or web, and focusing the query field on
   open, are shell/Finder behaviour.
5. **No start page.** A workspace cannot declare which page it opens on, so Kvaesitso cannot open on its
   Finder.
6. **Favourites and frequent apps have no adapter.** `apps.favourite` and `apps.frequent` are reserved ids
   without a registered source, so no preset uses them. Niagara's pinned list is the user's placed
   `home.grid` items drawn as a List instead.
7. **Single home page.** Presets reference only HomeLayout page `home`; a user with more home pages adds
   pages in the editor. A preset cannot create HomeLayout pages (home items stay owned by `HomeLayout`).
8. **Finder is a pager page.** The model stores the Finder as a page with `PageRole.FINDER`, so Nova's
   drawer is also reachable by swiping to it. Whether the shell hides it from the pager and opens it only
   from the dock menu is a UI decision.
9. **Dock cannot be hidden or restyled by a workspace** (Niagara has no dock); only the dynamic section is
   workspace-owned.
10. **Widget grid sizes** are fixed per posture (4 columns compact, 8 expanded); they do not adapt to
    arbitrary window widths.

## Validation

Every variant is tested to: pass `WorkspaceValidation` with default `LayoutCapabilities` and the source
descriptors the WS1 adapters declare (plus `home.grid`), resolve through `WorkspaceResolver` without
fallback, round-trip through `WorkspaceCodec`, hold no item content, install twice into distinct,
structurally equal workspaces, and need no permission at install.
