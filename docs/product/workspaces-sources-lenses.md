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

#### Expression rendering contract (WS3)

Expressions live in `app/.../launcher/expressions/`, one composable per kind (`ListExpression`,
`IndexExpression`, `AlphaListExpression`, ...). Each takes a redacted `LensResult`, an
`ExpressionState` (`Ready`, `Loading`, `Unavailable(message)`; an empty ready result draws the empty
message) and an `ExpressionEnvironment` (image loader, time formatter, content padding for insets,
resolved reduced-motion flag). They never see a source or its `SourceState`; the hosting container
maps source state onto `ExpressionState`.

- Images resolve through the injected `ExpressionImageLoader` (main-safe `suspend`, placeholder until
  it returns); expressions never decode bitmaps.
- Redacted titles (null or blank) draw as "Hidden content".
- Text-heavy expressions cap their width at 640 dp so unfolded and tablet windows stay readable.
- AlphaList buckets through the pure `AlphaIndex` (domain). Its scrubber is a platform `draggable`
  plus a labelled button per letter, so a drag is never the only way to jump. Letters are thinned
  to fit the font scale. Letter targets are full container width by about 24 dp tall rather than
  48 dp, because 27 targets cannot each be 48 dp on a phone; TalkBack users reach sections by
  activating letters or heading navigation.

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

### Workspace menu (WS6, #1351)

`WorkspaceMenuPlanner` (domain, `launcher/workspace/menu/`) turns the active layout's `WorkspaceSet`
into a `WorkspaceMenuModel`; `WorkspaceMenuReducer` holds the open/closed state and turns an action
(`Open`, `Close`, `SwitchWorkspace`, `JumpToPage`, `OpenFinder`, `EditWorkspace`) into a
`WorkspaceMenuEffect` the shell performs. Both are pure.

| Entry | Rule |
| --- | --- |
| Switch workspace | Every workspace of the layout in display order; the stored active one is marked. Re-choosing it only closes the menu. A single workspace shows no switch choice. |
| Jump to page | The displayed workspace's pages in order (the Finder page excluded: it has its own entry). A page-set expands to its evaluated groups, at most `maxGroupsPerPageSet` (default 8) each; the rest are counted (`omittedGroupCount`) and reached by paging. Group labels are transient lens output and are never stored. |
| Finder | The displayed workspace's `PageRole.FINDER` page. **Hidden when the workspace has none**: Riffle does not invent a default Finder page, because opening something the workspace never defined would be a surprise. |
| Edit workspace | Always present; targets the displayed workspace. |
| Fallback notice | When the active workspace cannot be drawn on this layout, the default is displayed and the menu says so with the validation reasons (`WorkspaceMenuFallback`). Jump, Finder and Edit act on the displayed workspace; the stored active one stays marked. |

With the workspace system off (no stored `WorkspaceSet`, or none with a layout) there is no model, the
menu cannot open and nothing is drawn or blocked: standard launcher mode is unaffected.

**Enabling.** The menu is off by default behind `WorkspaceMenuFeature.enabled` (app layer), separate
from `DockShelfExpansion.enabled`. Off, the dock pull and the dock behave exactly as today.

**Proposed doc change (open question for review).** The dock pull away from the dock edge is today
the Home <-> Library mode switch, and the arbitration table in `gestures.md` reserves it. "Pull the
dock up opens the menu" therefore cannot ship while modes exist without taking that gesture from the
mode switch. Proposal: while both exist, the menu opens from an explicit dock affordance (a button on
the dock, plus a dock accessibility action "Workspace menu") that reuses the shelf's composables but
not its swipe; once workspaces replace the mode pair, the dock pull opens the menu, with the same
accessibility action and Ctrl+arrow equivalent. No new pointer loop is added either way.

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

## Persistence and migration (WS5)

### Storage

`WorkspaceSet` holds a `LayoutWorkspaces` per `HomeLayoutDeviceClass`: the workspaces in display order,
the active id and the default id. Layouts are independent; `copyFromOtherLayout(source, target)` is a
one-time deep copy (fresh workspace and container ids) that replaces the target's workspaces and maps
active and default onto their copies. Invariants hold by construction: at least one workspace, unique
ids, active and default ids exist. An operation that cannot apply (remove the last workspace, unknown
id, blank rename, duplicate id) returns the set unchanged. A device class with nothing stored reads as
the built-in Standard default. `resolveActive(deviceClass, capabilities, sources)` returns the active
workspace, or the layout's default plus the issues when this layout cannot draw it.

Serialization is `WorkspaceSetCodec` over `StoredValue` (framework-free, schema version 1, per
workspace versions inside). Decoding never throws: unknown device classes, undecodable workspaces and
workspaces with no drawable page are dropped, stale active/default ids fall back, and a layout left
empty reads as the default (and is rebuilt by migration). Only lenses and workspaces are stored; items
have no codec.

### Migration mapping

`WorkspaceMigration.migrate(HomeLayoutSet)` runs per device class. Each mode that has a stored layout
(plus the mode the device class currently shows) becomes one workspace, so nothing a user set up is
lost. The shown mode's workspace is active and default. Ids are deterministic
(`ws:<deviceclass>:<mode>`), so migrating again gives the same result, and `ensureMigrated` never
overwrites workspaces that already exist (it only fills device classes that have none).

| Old | New |
| --- | --- |
| `STANDARD_APP_DRAWER` layout | Workspace "Standard" (the Nova-style default): one Page per `LauncherPage` |
| `HOME_SCREEN_LIBRARY` layout | Workspace "Library": its pages plus a last Finder page (All apps, grouped by group key, drawn as Categories) |
| `CARD_INTERFACE` layout | Workspace "Cards": a Notifications page-set (grouped by app) first, then its pages |
| Dock `showNotificationCards` / `notificationSlotCount` | `dock.dynamicSection`: Notifications, newest first, limit = slot count, drawn as IconRow |
| Dock pinned items, edge, size, appearance | Unchanged: they stay in `DockModel`, which the workspace does not duplicate |

Page mapping (container id `page:<pageId>`, deduplicated):

| `LauncherPageType` | Lens | Expression |
| --- | --- | --- |
| `Home` | `home.grid`, filter `GroupKeyIs(pageId)` | IconGrid |
| `AllApps` | `apps.all`, by title | AlphaList |
| `Generated(APP)` | `apps.all`, by title | IconGrid |
| `Generated(CATEGORY)` | `apps.all`, grouped by group key | Categories |
| `Generated(TODAY)` | `apps.recent`, newest first | List |
| `Generated(WORK / PERSONAL)` | `apps.all`, filter `ExtEquals(app.profile, work / personal)` | IconGrid |
| `Generated(FAVOURITES)` | `apps.favourite` | IconGrid |
| `Generated(FREQUENTLY_USED)` | `apps.frequent` | IconGrid |
| `Generated(NOTIFICATION_CARDS)` | `notifications`, newest first | CardStack |

Placed home items (apps, folders, widgets, shortcuts) are user content that is not the output of a
lens, so they stay in `HomeLayout` and are not copied. A migrated home page references its
`HomeLayout` page through the `home.grid` source and `GroupKeyIs(pageId)`; `home.grid` is therefore a
source the home grid must provide (or WS1/WS4 replace with a placed-items container) before migrated
Home pages draw items. `HomeLayoutSet` remains the source of truth for placement, selected page, pins
and dock until that cut-over, so migration loses nothing and is safe to re-run.

### Source ids

Part of the storage format, never renamed. Canonical ids live in `SourceIds` (owned by the source
adapters, WS1): `apps.all`, `apps.recent`, `notifications`, `shortcuts`, `media`, `calendar`. The ids
WS5 adds live in `WorkspaceSourceIds`, same dotted scheme: `apps.frequent`, `apps.favourite`,
`home.grid`, plus the ext key `app.profile` (`work` / `personal`). The adapters must register
descriptors under these ids (and emit `app.profile`), or tell WS5 which to change.

### Open questions raised by WS5

1. **Mode names are stored data.** Migrated workspaces are named "Standard", "Library" and "Cards"
   (unlocalized, user-renamable). The editor may want to localize defaults.
2. **`isPinned` on `LauncherPage`** has no workspace counterpart; it stays in `HomeLayout`.

## Containers and layout (WS4, as built)

Pure planning lives in `core/domain/.../workspace/container/`; the Compose hosts (later slice) only draw plans.

- **Page-set expansion** (`PageSetPlanner`). A grouped result becomes one page per non-empty group, in group
  order. The page key is `group:<groupKey>` (the ungrouped trailing group is `group:`), so it depends on the
  group and never on its position: a pager keeps its selection and per-page state when groups reorder.
  Empty groups and repeated group keys are skipped; at most `MAX_PAGES` (32) pages are produced and the
  overflow is counted (`truncatedGroupCount`) for the host to say so. A flat result is reported as a shape
  mismatch with no pages. `reconcileSelection(previousKey, previousIndex)` keeps the selected page by key,
  else the page now at the old index (clamped).
- **Widget grid** (`WidgetGridPlanner`). Stored grids are validated by `ContainerValidation`, but drawing must
  tolerate a broken one, so the planner places widgets through `GridPlacementEngine` (the same collision and
  bounds rules as the home grid): the first placement in stored order wins, later offenders are dropped and
  reported with a reason, and at most `MAX_WIDGETS` (48) are drawn. The plan is in reading order, which is
  also the accessibility traversal order. `rectFor` divides an area evenly into cells; a span covers its cells
  plus the gaps between them.
- **Axis declaration** (`AxisDeclarations.resolve`). Combines a container's `ownedAxes` with its children's
  (widgets in a grid page) into an `AxisDeclaration`: `owned`, per-child `childAxes`, `effective`, and
  `conflicts`. Conflicts are the same as `ContainerValidation`'s `AxisConflict` (a page-set's expression may
  not scroll horizontally), checked for every expression by a test. Sibling widgets never conflict with each
  other: each owns its own region. A horizontally scrolling widget on a grid page is allowed; it consumes
  first and the home pager takes the drag at its edge.

Proposed doc change, not new behaviour: the page cap and widget cap above are bounds chosen here (the design
only asked for bounded composition); revisit if a preset needs more.

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
- Calendar is a real adapter: `AndroidCalendarEventRepository` reads the next 7 days of visible calendars
  through `CalendarContract.Instances` (recurring instances expanded, at most 200 rows, off the main thread
  only). Private and confidential events become `SENSITIVE`. A `ContentObserver` (plus a permission-flip
  notifier fed by the shell's status refresh) makes the source `LIVE`; it is registered only while the
  stream has observers. Without `READ_CALENDAR` the source stays `PermissionRequired`; see the policy below.

Known gaps, to revisit rather than assume: package and shortcut changes are not observed yet (the
lifecycle-bound observer is not wired), hide-rule edits apply on the next refresh, media items have no
transport actions (no media-session repository exists), and calendar events that end while the stream is
running leave on the next calendar change or restart (no timer-driven refresh). `androidItemSources` is
still not wired into any UI (WS3/WS6); `androidCalendarSourceDependencies` is ready for that wiring.

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
