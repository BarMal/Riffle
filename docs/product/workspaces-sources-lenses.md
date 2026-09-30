# Workspaces: sources, lenses, expressions and containers

Status: proposed plan (supersedes parts of `modes-dock-handle-and-cards-plan.md`; see
[Relationship to the existing plan](#relationship-to-the-existing-plan)). Tracking issue: TBD.

## Why

Riffle's modes (Standard, Library, Cards) each bundle a data source, a way of drawing it, and a place
it lives. That bundling is why the model keeps changing shape and why mixing influences (TimeScape
cards, Niagara lists, iOS App Library, Nova grids, Kvaesitso search) is hard.

Goal: combinability and flexibility, with iOS-style and Nova-style as shipped defaults. Split the
bundle into four independent layers and let the user recombine them.

## The model

```
Source ──► Item (one universal shape)
             │
           Lens        filter / group / sort / limit / field mapping over one or more Sources
             │
        Expression     how a lens result is drawn: icons, list, cards, stack, grid, index, categories
             │
         Container     where it lives: widget (sized area) | page | page-set
             │
         Workspace     saved arrangement of containers, one per layout (posture)
```

Domain contracts live in `core/domain/.../launcher/workspace/`.

### Source

Produces a stream of `Item`s. Examples: All apps, Recent apps, Notifications, Quick actions
(shortcuts), Media, Calendar, RSS, an app's hosted widget, Search results.

A source declares capabilities: `live`, `groupable`, `actionable`, `searchable`, `privacySensitive`.
A source shares one upstream subscription however many observers attach, and replays its latest state.

### Item (the one universal shape)

All sources emit the same shape. Fields are optional except `id`, `sourceId` and a launch `target`.

| Field | Notes |
| --- | --- |
| `id`, `sourceId` | Stable identity |
| `title`, `subtitle`, `body` | Text at increasing length |
| `icon`, `image` | Lazy handles, never decoded on the main thread |
| `time` | Chronology, for sorting and grouping |
| `groupKey`, `groupLabel` | E.g. app id, category, day |
| `actions` | Open, dismiss, reply, snooze, custom |
| `target` | What a tap launches (app, shortcut, deep link, intent) |
| `privacy` | `VISIBLE` / `SENSITIVE` (respects the existing card privacy rules) |
| `ext` | Typed, namespaced primitive extras (see decision 1) |

Existing `LauncherCard` (`core/domain/.../launcher/cards/LauncherCard.kt`) is the ancestor. It is
transient and non-durable, and `Item` keeps that rule: **item content is never written to layout,
settings, backup or diagnostics.**

### Lens

A declarative, serializable view over sources. Expressions consume lenses, never raw sources.

```
Lens = {
  sources: [SourceRef]
  filter:  predicate over fields (source, group key, unread, age, has-actions, ...)
  group:   none | by groupKey | by day | by ext field
  sort:    field + direction (+ pinned-first)
  limit:   n | none
  project: which fields the expression may see (privacy redaction happens here)
}
LensResult = Flat(items) | Grouped(groups: [(key, label, items)])
```

The lens is where validity is decided: an expression declares the fields it `requires` and the result
shapes it `accepts` (flat, grouped, single). A lens/expression pairing is valid only if the lens
result satisfies both. The editor filters choices by this check, so invalid combinations cannot be
built. A flat lens with `limit = 1` yields a single-item result (and is also a valid flat result).

### Expression

Draws a `LensResult`. Each declares `requires`, `uses`, `accepts` and interaction `axes`. Initial set:
`IconRow`, `IconGrid`, `List`, `Index` (text-first, inline snippets), `Card`, `CardStack`,
`Categories` (App-Library style), `AlphaList` (A–Z scrubber).

### Container

| Container | Meaning |
| --- | --- |
| Widget | A sized area on a page (grid cells), hosts one lens + expression |
| Page | One full page hosting one lens + expression, or a free grid of widgets |
| Page-set | A grouped lens expands to one page per group (notifications per app, apps per category) |

A container declares which gesture axes it owns (scroll axis, horizontal pager, none). The arbitration
layer (`docs/product/gestures.md`) consumes this declaration instead of special cases. A page-set owns
the horizontal pager, so its expression may not also own a horizontal axis.

### Workspace

A saved arrangement of containers plus dock definition, gesture bindings and skin overrides.
Independent per layout. Folded and unfolded each hold their own workspace. No live link between them.
Provide a one-time *Copy from other layout* action.

A container must be drawable in both postures because posture can change mid-use. If a workspace uses
something a layout cannot draw, that layout falls back to a default workspace and says so.

Ships with presets: iOS-style, Nova-style (defaults), TimeScape, Niagara, Kvaesitso-style. Presets are
ordinary workspaces the user can clone and edit.

## Navigation

The dock keeps its job (pinned and dynamic entries). Pulling the dock up opens a workspace menu
(reviving the shelf, currently behind `DockShelfExpansion.enabled`) with: switch workspace, jump to a
page, Finder (an All apps lens, drawn as Categories or AlphaList per workspace), edit workspace.

Horizontal dock swipes to switch workspace are parked as an interaction detail for later.

## Worked example: folded "TimeScape" workspace

| Page | Lens | Expression | Container |
| --- | --- | --- | --- |
| Now | Media; Calendar (next event); Quick actions | Card; Card; IconRow | 3 widgets |
| Inbox | Notifications grouped by app | CardStack per group | Page-set |
| Recents | Recent apps | List | Page |

Dock: pinned icons + dynamic section (the Notifications lens drawn as icons; tap jumps to that
group's page). Finder: All apps as Categories.

Unfolded variant (separate workspace): left pane Notifications as `Index`, right pane the selected
group's `CardStack`, Now widgets and Recents alongside, dock on the left edge.

## Feasibility notes (Android constraints)

- Android has no general per-app activity API. Cards are rich only where an app exposes a widget,
  notifications or shortcuts; the fallback is app + shortcuts.
- Windowed/zoned app launching is system-controlled. Plan: full-screen launch first, adjacent (split)
  launch on unfolded second, freeform only where enabled. Spike required before design.

## Risks

1. Gesture arbitration with nested scrollers inside pagers inside dock surfaces. Mitigation:
   containers declare axes; no new hand-rolled pointer loops (ADR 0002).
2. Editing complexity. Mitigation: pick source, pick expression, pick container; presets; validity
   filtering; progressive disclosure.
3. Performance. Shared, cached, replayed source streams; one subscription per source, not per widget;
   lens evaluation off the main thread; bounded composition depth.
4. Privacy. Sensitive item content is redacted at the lens `project` step and never persisted.
5. Migration. Existing installs (Standard/Library/Cards, pages, templates, dock) must decode into
   default workspaces without loss.

## Relationship to the existing plan

| Existing | Change |
| --- | --- |
| Decision 1 (two surfaces, Home/Library) | Replaced by workspaces; Home/Library become a Finder role inside a workspace |
| Decision 3, 9 (dock pull = only mode trigger) | Pull now opens the workspace menu. The dock stays the handle |
| Decision 5, 6 (Cards stage selector, Index) | Stage selector = Notifications lens as icons; Index = an Expression |
| Decision 11 (shelf off) | Reversed: the shelf becomes the workspace menu |
| Decision 2, 12, 13 (shared dock, a11y equivalents, side docks) | Kept |
| #1226 card source framework, #1219 widgets as cards | Become the Source/Item layer |
| `LauncherTemplate`, `GeneratedLauncherPageSpec` | Evolve into Workspace templates |
| `HomeLayoutSet`, `LauncherPage(Type)` | Migrate into Workspace / Container |

## Workstreams

WS0 lands first and is small; it defines the contracts. Everything else can run in parallel against
those contracts and their fakes (`launcher/workspace/testing/`).

| ID | Workstream | Depends on |
| --- | --- | --- |
| WS0 | Domain contracts: `Item`, `Source`, `Lens`, `LensResult`, Expression descriptor, `Container`, `Workspace`, validity check, serialization, fakes | none |
| WS1 | Source adapters: All apps, Recents, Notifications (grouped), Quick actions, Media, Calendar; shared cached streams | WS0 |
| WS2 | Lens engine: filter/group/sort/limit/project, privacy redaction, off-main evaluation | WS0 |
| WS3 | Expressions: IconRow, IconGrid, List, Index, Card, CardStack (reuse existing stack), Categories, AlphaList | WS0 |
| WS4 | Containers + layout: widget, page, page-set; axis declarations; gesture arbitration integration | WS0 |
| WS5 | Workspace model: persistence, per-layout storage, copy-from-other-layout, migration from current settings | WS0 |
| WS6 | Dock workspace menu: reuse shelf; switch, jump, Finder, edit | WS0, WS5 |
| WS7 | Workspace editor: source → expression → container flow, validity filtering | WS0, WS2, WS3 |
| WS8 | Presets: iOS, Nova, TimeScape, Niagara, Kvaesitso | WS1–WS5 |
| WS9 | Screenshot tests for every expression and preset in compact and unfolded; feasibility spike for windowed launch | WS3 |

## Decisions on the original open questions

Provisional defaults adopted in WS0; revisit by editing this section.

1. **`ext` typing.** A map of namespaced keys (`namespace.name`) to primitives (text, number, flag)
   only. No source-specific classes in the universal shape. If an expression needs richer structure,
   promote the field to a first-class `Item` field instead.
2. **Finder.** Not a distinct model type: a `Page` with `role = FINDER`, bound (not a widget grid) and
   drawn as Categories or AlphaList. At most one per workspace.
3. **Skin.** Global default with an optional per-workspace override (`skinOverrideId`; null follows
   global).
4. **Windowed launch.** Still open; answered by the WS9 spike.
