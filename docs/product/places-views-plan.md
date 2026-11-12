# Places and Views: implementation slices

Status: **as built** (P1 to P14 merged; P12 is this close-out). Written as a plan; each slice now opens with its outcome. For [ADR 0003](../architecture/adr/0003-places-and-views.md). Each slice is one coherent
change that leaves `./gradlew verify` green. Slices run in order; later ones depend on earlier ones.

The order is: build the material, build the new surfaces beside the old ones, cut over, then delete. Nothing
the user placed may be lost at any step.

## Rules for every slice

1. Read ADR 0003 first, then this slice, then `AGENTS.md` and `docs/product/design-language.md`.
2. Stay inside the slice. If something outside it must change to compile, make the smallest change and list
   it under "Outside the slice" in the commit message.
3. Domain logic goes in `core/domain` with no Android or Compose types, and has unit tests. UI goes in `app`.
4. No new hand-rolled pointer loops (ADR 0002). Use platform gesture primitives.
5. Use the design tokens (`RiffleSpacing`, `RiffleShapes`, `RiffleElevation`, `RiffleMotion`) and, from P1
   on, `GlassSurface` for every translucent surface. No literal surface alpha or blur radius in feature code.
6. Stored data from the current build must still decode. Any slice that removes a stored enum value, key or
   concept adds a codec test with old data. Item content is never persisted.
7. Every user-visible control keeps a 48 dp target, a spoken label and a non-gesture equivalent. Honour
   reduced motion and reduced transparency.
8. New user-visible text goes into string resources.
9. Run `./gradlew verify` before committing. Update or record JVM screenshots for surfaces the slice changes
   (`docs/development/screenshot-testing.md`).
10. One commit per slice, staged by explicit path (never `git add -A`; `.kotlin/` and `local.properties`
    are not committed). Do not push; the coordinating session pushes.
11. Add a short entry to `CHANGELOG.md`, and update the product document the slice names.
12. Report what was done, what was not, and what needs a device to confirm.

## Slices

### P1. One glass material, and the spotlight everywhere

**Outcome:** done (#1446). `GlassSurface` with Thin, Regular and Thick weights, the general sheen and spotlight, reduced transparency as a top-level setting, and the listed surfaces migrated. Left out: nothing in scope; the adaptive stage card surface stayed until P8. A later fix (#1450) found glass could not blur the system wallpaper and gave each weight a wallpaper tint floor.

Goal: every translucent surface is drawn by one primitive at one of three weights, with the dock's sheen on
all of them and the dock's spotlight wherever a surface holds coloured things (ADR 0003, Material).

- Extend `app/.../launcher/GlassSurface.kt` (or move it under `designsystem/`) to
  `GlassSurface(weight: GlassWeight, shape, tint)` with `GlassWeight { Thin, Regular, Thick }`. The weights
  own frost, tint alpha, scrim, hairline and shadow. Put the numeric values in
  `core/domain/.../designsystem/` beside the other tokens, unit tested.
- Generalise the sheen: `Modifier.glassSheen(shape, ...)` from `dockGlassSheen`
  (`DockGlassSheenStrength.kt`), without `DockVisualEffect` or the fixed 16 dp reference corner. The dock
  calls the general one.
- Generalise the spotlight (`DockSpotlight.kt`, `DockSpotlightFlow.kt`, `DockSpotlightGlowBaker.kt`):
  `GlassSpotlightState`, `Modifier.glassSpotlightSource(key, color)` and
  `Modifier.glassSpotlight(state, shape)` taking a `Shape` and sources anywhere inside the surface, not a
  `DockPosition` and one axis. Keep the behaviour: frames only while sources change, settle, stop; snap
  under reduced motion. The dock must look the same as before (screenshot test).
- Move `reducedTransparency` out of `cards.adaptiveStageAppearance.motion` to a top-level appearance
  setting (decode the old location) and make every glass surface, the sheen and the spotlight honour it.
- Migrate these surfaces to `GlassSurface`, removing their literal alphas: `SearchSurface`
  (`LauncherSearchSurface.kt`), `LauncherPanel` (`LauncherPanels.kt`), `FolderSurface` (`HomeFolders.kt`),
  `ShortcutContextMenu.kt`, `WidgetPickerSurface.kt`, `HomeWidgetPlaceholder.kt`, `ItemCard`
  (`expressions/ItemCard.kt`, which must also use `LocalLauncherCardShape`), `DockNotificationCards.kt`,
  the page indicator and overview cards (`HomePageControls.kt`), and the settings sections.
- Add the spotlight to: open folders (lit by the icons inside), the search results panel, and `ItemCard`
  (lit by the item's app colour, through `AppIconLoader.colorFor`).
- Update `docs/product/design-language.md` section 3 with the three weights.

Not in this slice: deleting theme presets (P2); the adaptive stage card surface, which P4 replaces.

### P2. Glass is the only look

**Outcome:** done (#1446). Non-glass presets, the Liquid glass switch, the dock effect and the skin override are gone; custom colours reduced to an accent. Left out: custom dock, label and background colours are ignored and were not restored.

- Remove `LauncherThemePreset` values `MATERIAL`, `MINIMAL`, `RETRO`, `TERMINAL`, `CUSTOM` and everything
  that exists only for them in `LauncherTheme.kt`; removed values decode to glass.
- Remove the Liquid glass on/off switch (`LiquidGlassSettings.enabled`); glass is always on and reduced
  transparency is the off switch. Remove `DockVisualEffect` (FLAT, ELEVATED, OUTLINED) and its setting.
- Remove the per-workspace skin override (`Workspace.skinOverrideId` stays decodable and is ignored;
  `WorkspaceSkin`, `SetSkin` and its dialog go).
- Reduce custom colours to a custom accent. Rename "Card corners" to "Corners".
- Update `AGENTS.md` ("Project intent" and "Product priorities") and `design-language.md` to say glass on
  top of the Material 3 component baseline, not Material design language.

### P3. Typed content

**Outcome:** done (#1446). `StreamContent`, the six sections, mappers and fixed queries, in the domain. Left out: real media, notification and alarm data (P3b); `Item` and the lens pipeline stayed as the source side.

- In `core/domain/.../launcher/stream/`: a sealed `StreamContent` with `NotificationContent`,
  `MediaContent` (with queue), `EventContent` (calendar, calendar feed, alarm), `ArticleContent`,
  `TaskContent` and `RecentAppContent`, each carrying its own fields, actions and privacy level; and the
  six `StreamSection` kinds (Now, Notifications, Recents, Agenda, Tasks, Feeds).
- Mappers from the existing sources (`workspace/sources/*Mapper.kt`, `SharedSourceStream`) to these types,
  keeping one shared subscription per source and redaction before anything reaches the UI. `Item` and the
  lens pipeline stay for now; they are removed in P8.
- One fixed query per section (what, grouped how, sorted how, how many) as plain code, with the two or
  three per-section options the ADR allows. Unit tests for each.

### P3b. Real data behind the typed content

**Outcome:** done (#1446). Media sessions with queue and transport, notification history, reply and snooze, next alarm, frequent apps, a Stream-only agenda window, article mark-read. Left out: nothing persisted; sensitive or hidden content exposes no history or reply.

P3 mapped the typed content from `Item`, so it carries no more than `Item` did. This slice gives each type
the data that makes it worth having (ADR 0003, finding F4).

- **Media:** read the active media sessions (`MediaSessionManager` through the existing notification
  listener access): title, artist, artwork handle, play state, position and duration, the queue when the
  session exposes one, and transport actions (play, pause, next, previous, seek). `MediaContent` actions
  become real. Nothing about media is persisted.
- **Notifications:** message history for messaging-style notifications (thread expansion), and the reply,
  dismiss and snooze actions, reusing `NotificationStageActionGateway` /
  `AndroidNotificationStageActionGateway` where they already do this for the adaptive stage.
- **Alarm:** a platform `NextAlarmProvider` from `AlarmManager.getNextAlarmClock()`.
- **Recents:** include frequent apps if the usage source can rank them; otherwise leave Recents as it is.
- **Agenda:** the Stream's Agenda query is not bound by the lens page limit of five device-calendar events;
  give it its own window (today and the next seven days), without changing lens behaviour.
- **Articles:** `MARK_READ` backed by the RSS store if it tracks read state; otherwise drop the action.
- Platform adapters live in `app` behind domain interfaces; domain logic is unit tested with fakes.

### P4. The Stream

**Outcome:** done (#1446), then reshaped by P13. Six sections, Stack and List, Show as with Undo, folded full-surface behaviour, permission states. Left out: Tasks content (P10) and the unfolded split (P6).

- Domain: `StreamLayout` per posture (sections on or off, order, `StreamView { Stack, List }` per section),
  its reducer, defaults and codec, stored in its own DataStore entry and included in backup.
- UI in `app/.../launcher/stream/`: the Stream surface with the six sections; a card renderer and a row
  renderer per content type (ADR 0003, Typed content); Stack reuses the existing card-stack physics
  (`cards/`, `CardStack.kt`); every surface is `GlassSurface`, with the spotlight lit by the app colour.
- "Show as" in place: long-press a section header, choose Stack or List, redraw at once, Undo snackbar.
- Folded: the Stream is a full surface that replaces Home, with the Dock under it. Home press and Back
  return to Home. Entry points for now: the `OPEN_STREAM` gesture action and a temporary Dock accessibility
  action; P5 adds the real handle.
- Permission states per section ("Allow access to show this") reuse the existing source access flows.
- Screenshot tests for every section in both views, compact.

Not in this slice: the unfolded split (P6), Tasks content (P10 supplies it; the section shows its empty
state), removal of the old lens pages and adaptive stage surface (P8).

### P5. The Dock is the handle

**Outcome:** done (#1447). Dynamic side of live apps, now playing and the Stream button; pull by side; Dock accessibility actions; `OPEN_STREAM` and `OPEN_LIBRARY`. Left out: the narrow-target fallback (pull anywhere on the Dock, choose by half) was not built and needs a device. The Dock's size was later restored (#1452).

- Dynamic side: apps with something new (from notifications), one now-playing entry while media is active,
  and the Stream button always last. Replace `showNotificationCards` / `notificationSlotCount` and the
  workspace `dynamicSection` with one "live app slots" setting; decode the old ones.
- Pull by side (`dockpull/`, `HomeDockPullBinding.kt`): a pull that starts on the pinned side opens the
  Library; one that starts on the dynamic side opens the Stream, tracking the finger, interruptible, with
  commit thresholds from `GestureThresholds`. The pull no longer opens the workspace menu.
- Tap the Stream button: toggle Home and Stream. Tap a live app: open the Stream on that app's stack.
- Accessibility: Dock custom actions "Open Stream" and "Open Library"; keyboard equivalents.
- Gesture actions: add `OPEN_STREAM` and `OPEN_LIBRARY`; `OPEN_WORKSPACE_MENU` and
  `SELECT_NEXT/PREVIOUS_APP_STAGE` decode to None.
- Update `docs/product/dock.md` and `docs/product/gestures.md` (arbitration table included).

### P6. Unfolded split

**Outcome:** done (#1447). Movable divider, Home only, split or Stream only, hinge snap, tabletop, split stored per posture. Left out: nothing in scope; not run on a foldable.

- On expanded postures, Home and the Stream can share the screen with a movable divider (platform
  `draggable` / anchored-draggable, ADR 0002); either can take the whole screen. A dynamic-side pull that
  stops partway leaves the split. Pane math accounts for the Dock and the hinge
  (`AdaptiveStagePaneLayoutPolicy` has the existing hinge logic).
- Store the split position per posture in `StreamLayout`.
- Screenshot tests: unfolded split, unfolded Stream only, tabletop.

### P7. The Library

**Outcome:** done (#1448). Categories, A-Z and Grid, search at the top, one hidden-apps list. Left out: drag from the Library onto Home (Add to Home and Pin to Dock are the equivalents); the Personal and Work split is a filter in the Library, not a stored setting.

- Views: Categories (iOS-style tiles, the default), A–Z list, Grid, from the existing `CATEGORIES`,
  `ALPHA_LIST` and `ICON_GRID` expressions; "Show as" by long-press on the header and in Settings.
- Search field at the top; results replace the list; swipe down on Home opens the Library with search
  focused. Category tiles are `GlassSurface` with the spotlight lit by their icons.
- Recents and frequent apps leave the Library (they are a Stream section).
- One hidden-apps list: merge `AppVisibilityRepository` hidden apps and app exclusion rules.
- The Library is no longer a workspace Finder page: it is its own surface, opened by the pinned-side pull.

### P8. Cut over and delete

Status: **built** (#1449). Left in place and listed in the commit message: the stored `LauncherSettings.cards` model and `cards/` stage code that
settings and shell state still read, `PageRole.FINDER` (stored only, never drawn), the lens evaluator (it is the vehicle of the
exclusion and content-level tests), the Search source plumbing (no Place uses it; its Settings page is gone) and the Sources pages
(P9 removes them).

- One arrangement per posture: Home is the pool's arrangement for the default workspace of each layout;
  the Dock is `PoolDock`. Migration: for each layout, the active workspace's arrangement becomes the one
  arrangement; other workspaces stay in storage, decodable, and are not shown.
- Remove: the workspace menu (`workspace/menu`, `WorkspaceMenuSurface.kt`), workspace switching and its
  settings page, presets (`workspace/preset`), the editor (`workspace/editor`), the wizard and goals
  (`workspace/wizard`), saved lenses and the lens library, the advanced lens builder, per-lens search
  queries, lens pages after the Home pages, the adaptive stage surface and its tuning overlay, the
  "Home from placed items" developer switch (the pool home is the only home), and `PageRole.FINDER` pages.
- `Item`, `Lens`, `LensEvaluator` and `workspace/container` remain only where P3's mappers still need
  them; delete what has no caller.
- Onboarding: four steps, one per Place (Home, Dock, Library, Stream), with permissions asked in the Stream
  step after an explanation, each skippable.
- Backup import of a version 1 document written by the current build must still succeed.
- Add the "superseded by ADR 0003" banner to the documents ADR 0003 lists.

### P9. Settings by Place

Status: **built** (#1450, with the glass fix); see [`settings.md`](settings.md), "as built", for the tree, the removals and the differences. Left out: the Stack feel page, "Show now playing", "New apps go to Home or Library only" and a per-calendar Agenda list.

Build the page tree, renames, removals and rules in [`settings.md`](settings.md). Slices P1 to P8 have
already removed the settings of what they removed; this slice does the reorganisation, the dead-setting
removal, shared rows everywhere, per-setting search, Back targets, reading the codec version, and backup
of calendar feeds and enabled sections.

### P10. Tasks from Todoist

**Outcome:** done (#1451). Todoist today and overdue tasks, complete, quick-add, encrypted local token excluded from backup. Left out: tasks without a due date are not fetched; the API details are only partly verified ([`stream-tasks.md`](stream-tasks.md)).

- `TaskContent` source for Todoist: today's and overdue tasks, complete, quick-add. Personal API token
  entered in Stream > Tasks, stored encrypted on the device, excluded from backup, never logged.
- Network access only to the Todoist API host, off the main thread, with a refresh policy like RSS
  (`rss-refresh.md`). No shipped secrets. Honest states: no token, offline, error.
- Tests against a fake HTTP layer; no real network in tests.

### P11. Glance widgets

**Outcome:** done (#1452). Now playing, Next event and Top story in the widget picker. Left out: a snackbar on a failed action, glance widgets in the Dock, more widgets ([`glance-widgets.md`](glance-widgets.md)).

- A fixed catalogue of Riffle's own widgets placed from the widget picker like any other: Now playing,
  Next event, Top story. Each renders the same typed card as the Stream, in a `GlassSurface`.
- They are pool widgets with a Riffle provider id; sizing and placement use the existing grid engines.

### P12. Documentation and close-out

**Outcome:** done on this branch (after #1452). ADR 0003 Accepted with an "As built" section, this plan, README, AGENTS.md, CHANGELOG, the device checklist (`workspaces-dogfood.md` replaced by [`places-views-device-checklist.md`](../development/places-views-device-checklist.md)) and small corrections to current product documents. Left out: any device run; the checklist is what must still be done by hand.

- Change ADR 0003 to Accepted. Update `README.md`, `AGENTS.md`, `CHANGELOG.md`.
- Replace `workspaces-dogfood.md` with a Places and Views device checklist: the Dock pull by side and the
  narrow-target question, each "Show as", the unfolded split, the spotlight on every surface, reduced
  motion and reduced transparency, data carried over from the previous build.

### P13. The Stream as pages

**Outcome:** done (#1452). Pages of vertical stacks, one page per app for notifications, the spine, stable page keys, remembered page. Left out: a spine marker animation between sections and a per-page stack-depth setting.

Status: **built** (#1452; owner decision 12 in ADR 0003, after the owner found the first Stream "basically one scrollable page
with horizontal card stacks"; the model is Calm's chapters, on TimeScape).

**Page model.** `StreamPagePlanner` (`core/domain/.../launcher/stream/StreamPages.kt`) turns the layout and each
section's result into the ordered pages. A page is one vertically scrolling stack or list of one section's content:

| Section | Pages | Key |
| --- | --- | --- |
| Now | one (media with its queue, next event, alarm) | `now` |
| Notifications | one per app with notifications, newest app first; one empty-state page when none | `notifications/<package:profile>`, `notifications` |
| Recents | one (recent apps, then "Often used") | `recents` |
| Agenda | one (by day) | `agenda` |
| Tasks | one (quick-add, then today's and overdue) | `tasks` |
| Feeds | one per feed with unread items, newest feed first; one empty-state page when none | `feeds/<feed id>`, `feeds` |

Pages follow the layout's section order and a hidden section contributes none. Loading, no access, not connected,
unavailable and off are one page for the section holding the existing honest state and its action; when every section is
hidden the surface says so and offers each back. Keys depend only on the section and the app or feed, so
`StreamPageNavigation.currentAfter` keeps the current page by key while pages come and go (a new notification never jumps
the pager) and, when the current page vanishes, takes the nearest survivor (the next on a tie). A request to land
(`StreamFocus.resolvePage`) resolves to a page key: Now to the Now page, a Section to its first page, an app's stack to
that app's page or, if its notifications were cleared, the first Notifications page; it waits while that page is still
loading. The last page is remembered per posture for the session (`StreamPageMemory`) and returned to when the Stream
opens with no target, found again once its section has delivered.

**UI** (`app/.../launcher/stream/`): `StreamSurface` hosts a platform `HorizontalPager` (`StreamPagesMode`) whose pages are
`StreamPageView`s, with `beyondViewportPageCount = 0` so only the pages on screen are composed. Each page has a header
(app icon or section glyph, name, item count, options button or long-press for Show as, Move and Hide) over its content.
Only the sections' observation is shared: `StreamContentProvider` still has one upstream per source, not per page.

**Stack geometry** (`StreamCardStackSpec`, from Calm's `CalmCardSpec` and default `CardStackTuning`): 168 dp cards, 56 dp
apart, the focused card in front with the next ones peeking out below it as strips (each shows its icon and one line),
two cards ahead and two behind visible, one more composed as a margin (at most seven cards composed whatever the page
holds), each step 4 percent smaller and 14 percent fainter. Calm puts the focused card at 20 percent of the viewport; the
Stream puts it at 12 percent of the free height because its header sits above. A pile is as tall as its tallest card so
the strips line up; media, an article with a lead image and the tasks card are taller than 168 dp because they hold
transport, an image or a checklist. The physics are the existing vertical `CardStack` (platform `scrollable`, spline
fling, magnet settle), run vertically again; a card that can expand (a notification's thread and reply, the queue)
expands in place and Back collapses it. Recents are app tiles, so its Stack is a wrapping grid of tiles that scrolls with
the page, and Tasks is one card under the quick-add row. A List is rows on one Thin glass panel per group. "Show as"
applies to the page's whole section and offers Undo; it is the same stored value as Settings > Stream.

**The spine** sits at the top of the Stream, above the page header, as Calm's does by default (the Dock owns the bottom
and may be on any edge): a Thin-glass strip with one 48 dp marker per page (the app's icon, or the section's glyph), a
hairline between sections, the current marker emphasised and the strip following the pager's swipe in flight. A tap jumps
to the page. It is a tab row (each marker is a selected or unselected tab, spoken as "Chat, page 2 of 8").

**Accessibility.** Each page is a labelled pane; the pager carries one custom-actions list ("Previous page", "Next page");
the focused card keeps its own list; all targets are 48 dp; page content stops at 640 dp; reduced motion snaps every page
change, reduced transparency is the material's. `StreamCallbacks` is now a data class and the surface wraps only the
layout callback with `copy`, so every other callback reaches every page; `StreamBehaviorTest` taps a control on each
section's page to prove it.

Removed: the single scrolling `LazyColumn`, the horizontal stack orientation, `StreamSectionBlock` and the group-focus
scroll plumbing.

Not in this slice: an animation of the spine's markers between sections, and a per-page stack-depth setting.

### P14. Stage view on unfolded

**Outcome:** done (#1452). Pages (two-up when wide) or Stage with a rail of thumbnails and a shared-element transition. Left out: dragging a stage out of the rail to reorder it, and thumbnails that are scaled copies of the live cards.

Status: **built** (#1452; owner decision 13 in ADR 0003).

On an expanded posture (foldable, tablet, desktop) `StreamLayout.wideView` (`StreamWideView`: Pages, the default, or
Stage, stored per posture as `wideView`; layouts stored before it decode to Pages) chooses how the whole Stream shows. It
is set with "Show as" in the Stream's own menu (long-press the spine, or the options button at its end, or on the Stage's
header; with Undo) and in Settings > Stream. A phone always shows Pages.

- **Pages**: the P13 pager, and when the Stream has the whole wide screen (at least 840 dp, `StreamPagesPerView`) two
  pages side by side, the spine following the first.
- **Stage** (`StreamStage`, `StreamStageView`): a rail along the leading edge with one live stage thumbnail per page
  except the selected one (a miniature of the top of the page's stack: the app icon or glyph, the name, a line of what is
  on top and up to two cards peeking out behind, lit by the app's colour), and the selected stage's stack large in the
  rest. The rail scrolls. Rail order: the stages the user brought to the centre most recently first (the one just left on
  top, as Stage Manager does), then the rest in page order (sections as laid out; within Notifications and Feeds, newest
  activity first). A tap on a thumbnail makes it the selection with a shared-element transition (Compose
  `SharedTransitionLayout`; the thumbnail's bounds become the page's and the old page's become a thumbnail; snap under
  reduced motion). Below 560 dp of pane width (`StreamStage.RAIL_COLLAPSE_BELOW_DP`; a split's Stream pane on a foldable is
  about 340 dp) the rail collapses to icon-only stages. It works in the split and with the Stream alone; the selection is
  kept by key like the pager's current page and shares the page memory.
- Rail thumbnails are miniatures built from the page's content, not scaled copies of the live cards, so they stay cheap
  and non-interactive; their tab semantics name the page and its item count.

Not built: dragging a stage out of the rail to reorder it, and a thumbnail that shows the actual card scaled down.
