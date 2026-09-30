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

#### Lens evaluation semantics (WS2)

Implemented by `DefaultLensEvaluator` in `core/domain/.../launcher/workspace/lens/`. It is a pure
function of `(lens, items, context)`; the context injects `nowEpochMillis` (age filters) and a
`DayBucketer` (time zone for `ByDay`). `AsyncLensEvaluator` runs any evaluator on a caller-supplied
`Executor`, so evaluation stays off the main thread without a coroutines dependency. Pipeline:

1. **Merge.** Items from sources not in the lens are ignored. Order is the source's position in
   `lens.sources`, then emission order within the source.
2. **Dedupe by `ItemId`.** First in merged order wins (earlier source in the lens beats later; within
   a source the first emitted). Runs before filtering, so `FromSource(b)` will not resurrect an id that
   source `a` already won.
3. **Bound.** At most `maxInputItems` (default 10 000) merged items are considered, and filters nested
   deeper than `maxFilterDepth` (default 32) match nothing.
4. **Filter** on the unredacted item. `AgeAtMost/AtLeast` use `now - time`, bounds inclusive; items
   with no time match neither. Empty `AllOf` is true, empty `AnyOf` is false.
5. **Sort.** `TITLE` compares case-insensitively, then by raw title; missing values sort last in both
   directions; ties always break by item id ascending, so results do not depend on input order
   (`SOURCE_ORDER` is input order by definition). `pinnedFirst` puts items with ext flag
   `launcher.pinned = Flag(true)` first, then applies the field sort within each band.
6. **Limit.** Applied after sort and before grouping, as a **total across the result**, not per
   group. `limit = n` therefore never yields more than n items whatever the shape, and matches the
   existing rule that a flat `limit = 1` lens is the SINGLE shape. A per-group cap, if wanted later,
   should be a new lens field rather than a change of meaning.
7. **Group.** Groups appear in order of their first member in the sorted list (so `TIME` descending
   puts the newest day first). `ByGroupKey`: key is `groupKey`, label is the first non-blank
   `groupLabel` in the group, else the key. `ByDay`: key is ISO `yyyy-MM-dd` from the bucketer, label =
   key. `ByExt`: key is the ext value as text (`Number`/`Flag` via `toString`), label = key. Items with
   no key go to one trailing group with key `""` and a null label.
8. **Project and redact.** Fields not in `project` are cleared; SENSITIVE items additionally lose
   `SENSITIVE_ITEM_FIELDS` whatever `project` says. `id`, `sourceId`, `target` and `privacy` are always
   kept. This is the only redaction point.

Privacy consequences of evaluating before redaction: sort keys and pinned state read the item as it
will be seen after redaction (a SENSITIVE item sorts as title-less and is never pinned), and `ByExt`
puts SENSITIVE items in the ungrouped group, so ordering and group keys cannot leak redacted content.
Filters see the raw item, so a filter can reveal membership (a boolean) but never content. `ItemGroup`
keys and labels are lens structure, not item fields: they exist even if `project` omits `GROUP`/`TIME`.

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
Each page draws one group's items, so a page-set's expression is checked against a flat per-group
result (e.g. `CardStack` or `IconGrid`; not `Categories`, which draws the grouped result itself).

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

## WS1 source adapters (as built)

Canonical ids live in `SourceIds` (`apps.all`, `apps.recent`, `notifications`, `shortcuts`, `media`,
`calendar`, plus `rss`, `search`); they are a stored contract and are never renamed.

- Every source is a `SharedSourceStream`: one upstream however many observers, latest state replayed,
  started on the first observer and stopped on the last. Platform reads run on one background executor.
- Notifications and Media share the existing notification pipeline (hide rules, stale filter, grouper,
  profile content visibility). A notification with a media session appears in Media only. Groups use
  `groupKey = package:profile` and `groupLabel = app name`. Quiet profiles yield `SENSITIVE` items with
  no content; locked, unavailable and unknown profiles yield none.
- Permission-gated sources (notifications, media, recents, calendar) emit `PermissionRequired` and never
  read gated data or prompt. `UNKNOWN` notification access counts as not granted.
- Capabilities are declared honestly: `LIVE` only where a change source exists.

Known gaps, to revisit rather than assume: package and shortcut changes are not observed yet (the
lifecycle-bound observer is not wired), hide-rule edits apply on the next refresh, media items have no
transport actions (no media-session repository exists), and there is no platform calendar adapter, so the
calendar source reports `Unavailable` until one is designed (it needs an explicit, user-initiated
`READ_CALENDAR` flow, which is out of scope here).

## Calendar access policy (`READ_CALENDAR`)

`READ_CALENDAR` is a dangerous runtime permission and is the only one the workspace sources add. It is
requested only by an explicit user action, never at launch, on install, on first run, or because a
workspace or lens merely contains the Calendar source.

- **Until granted** the Calendar source emits `PermissionRequired`, does not query the calendar and does
  not prompt. `CalendarAccessStatus` (`UNKNOWN`, `GRANTED`, `NOT_GRANTED`, `DENIED_PERMANENTLY`) maps to
  `SourceAccess`; only `GRANTED` reads data. `UNKNOWN` counts as not granted.
- **Entry points.** (a) Settings > Permissions > "Calendar", which always shows what the access is used
  for next to an Allow button. (b) `LauncherShellAction.RequestCalendarAccess`, for a surface (an
  expression, the editor) that shows a "needs calendar access" state. Such a surface must show the
  rationale beside its button; it never triggers the request itself.
- **Next step** is decided by the pure `calendarAccessStep(status, rationaleVisible)`: rationale first if
  not yet visible, then the system dialog, and for a permanent denial the app's system settings page
  instead of a dialog that can no longer appear. A denial is recorded by the launcher (a flag, not
  calendar data) because Android cannot otherwise tell "never asked" from "don't ask again".
- **Revocation** returns the source to `PermissionRequired` on the next status refresh; the stream drops
  the events it held. Revoking in system settings normally restarts the process anyway.
- **Privacy.** Events are read into memory only, never persisted or sent anywhere. Events the calendar
  marks private or confidential become `SENSITIVE` items and are redacted by the usual privacy
  projection.
- **Standard launcher mode** never depends on this permission: with access absent nothing is empty,
  blocked or prompted.
- **Store implication.** Adding `READ_CALENDAR` to the manifest requires declaring calendar access in the
  Play Console data-safety form and privacy policy (data accessed on device, not collected or shared,
  used for on-device display only) before a release that includes it.
