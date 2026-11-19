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
  `TaskContent` and `RecentAppContent` (and, since dock modes slice 4b, `AppShortcutContent`), each carrying its own
  fields, actions and privacy level; and the `StreamSection` kinds (Now, Notifications, Recents, Today, Feeds; Today
  replaced the separate Agenda and Tasks in slice 4b, and the derived Shortcuts section holds a pinned app's page).
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

**Outcome:** done (#1448). Categories, A-Z and Grid, search at the top, one hidden-apps list. Left out: drag from the Library onto Home (Add to Home and Add to Dock are the equivalents); the Personal and Work split is a filter in the Library, not a stored setting.

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
| Now | one (media only: players and recent players; amended, ADR 0003 Amendment 4) | `now` |
| Notifications | one per app with notifications, newest app first; one empty-state page when none | `notifications/<package:profile>`, `notifications` |
| Recents | one (recent apps, then "Often used") | `recents` |
| Today | one (the next event with a countdown, "To do today", later events, tomorrow, notices; List: the agenda by day, then the tasks) | `today` |
| Shortcuts (derived; not in the layout) | one per pinned app with nothing live (its launcher shortcuts, or one "Open <app>" card) | `shortcuts/<package:profile>` |
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
apart. Since dock modes slice 4a the stack is bottom-anchored (see
[`dock-modes-plan.md`](dock-modes-plan.md), "As built"): the focused card in front just above the Dock with the older
ones peeking above it as clipped strips, up to three, at most five cards composed. A pile is as tall as its tallest card so
the strips line up; media, an article with a lead image and the tasks card are taller than 168 dp because they hold
transport, an image or a checklist. The physics are the stack's own (platform `scrollable`, a spring with a magnet); a card that can expand (a notification's thread and reply, the queue)
expands in place and Back collapses it. Every Stack is cards of one pile (since slice 4b Recents is a pile of app cards,
not a grid of tiles, and the tasks are the "To do today" card of the Today page, with the add field in it). A List is rows
on one Thin glass panel per group. "Show as"
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

## First run: Home starts with something on it

ADR 0003, Amendment 8 said apps present at the first run are not placed, which left a fresh install with an empty Home and
an empty Dock. That is replaced (Amendment 11). Existing installs are never touched.

**The set-up asks "How should Home start?"** as its first step, on a fresh install only (origin `FRESH_INSTALL`, until it
is answered once; a re-run from Settings and an existing user never see it). Two small previews: **All my apps**
(recommended, preselected: every visible app A to Z by the Arrange rule with the stored Compact choice) and **Start empty**
(one empty page; the apps live in the Library). Next applies the choice. "Skip set-up" while the question is open applies
All my apps (the button's supporting text says "Skip: start with all apps on Home"), so skipping never leaves an empty
screen silently.

**Rules (`HomeSeeding`, `LauncherShellViewModel.seedHome`).**

- A seed only runs on a Home that is fresh (no item on any page, nothing in the Dock); otherwise it does nothing.
- The Dock is seeded in both cases with up to four apps resolved by role or intent, in this order: the default dialer
  (`ACTION_DIAL`), the default SMS app, the default browser (`ACTION_VIEW` of an https address) and the camera
  (`INTENT_ACTION_STILL_IMAGE_CAMERA`, then `ACTION_IMAGE_CAPTURE`). A role that resolves to nothing, to the system chooser
  or to an app that is not launchable is skipped silently. Resolution is behind `DockDefaultsResolver`
  (`AndroidDockDefaultsResolver` on the device, a fake in tests); the matching `<queries>` entries are in the manifest.
  No package names are listed anywhere.
- An app that the seed puts in the Dock is not also placed on Home.
- The seed waits for the app list (and the workspaces); it runs once. That the answer is stored
  (`OnboardingState.homeStartChosen`) and that nothing else seeds is what keeps a removed app removed after a refresh or a
  restart. The One Home and library-view migrations run for existing data only; a fresh install has nothing to migrate.
- Either choice marks the empty-Home hint as dismissed.

**The empty-Home hint.** An existing user whose Home has nothing on any page, who has apps to add, sees one line on Home:
"Home is empty." with an "Add all apps" button (and Undo afterwards) and a dismiss button. It is never shown again once
dismissed, once Home has held an item (the shell records that), in Simple mode or while the Home-role card is up.

**The set-up card** ("Set up Riffle", before the tour) is one sentence of purpose, "Set as Home app" and "Not now". The
caveat that the Home button may still open the current launcher appears only after the role request was declined
(`LauncherShellState.homeRoleRequestDeclined`).

## After-update starts

An investigation aid for "the swipe-up-to-home gesture stops reaching the launcher after an update until the navigation
mode is toggled" (no confirmed cause). The manifest declares on the Home activity `android:clearTaskOnLaunch`,
`android:stateNotNeeded` and `android:resumeWhilePausing`, as AOSP's Launcher3 does; the LAUNCHER intent filter is
unchanged. The first process start after the version code or the last-update time changes records one entry
(`AfterUpdateLog`, kept to the last ten, newest first; see Settings > About > After-update starts). The work
runs on its own thread, adds no receiver, background start or permission, and a failure there never reaches the launcher.
Without a stored earlier version (this build's first start) an update is recorded with the old version unknown; a fresh
install records nothing. The five-minute window for Home intents is wall-clock time since the start.

## Home drag

The rules are in `gestures.md`, "Dragging on Home"; the rule is `HomeDragPush` (`:core:domain`) and its numbers
`HomeDragTuning`, set in Settings > About > Developer options > Drag tuning.

| Number | Default | Range |
| --- | --- | --- |
| Push ring radius (outer, cells) | 0.75 | 0.5 to 1.2 |
| Dead-zone radius (cells) | 0.30 | 0.15 to 0.45 |
| Folder hold | 250 ms | 100 to 600 ms |
| Edge hold | 500 ms | 200 to 1000 ms |
| Page repeat | 700 ms | 300 to 1500 ms |
| Edge zone | 24 dp | fixed |
| Ring hysteresis | 0.12 cell | fixed |

**Widget stacks** (ADR 0003, Amendment 14) add no setting and no tuning number: a widget's dead zone is its span inset by
the dead-zone radius, and the stack uses the folder hold. Rules in `gestures.md`, "Widget stacks"; the model is
`WidgetItem.stack` and `WidgetStackEngine` (`:core:domain`), stored as `PoolWidget.stack` and in the layout JSON.

What the old pick-up got wrong (fixed): the pointer-input block captured the item, grid and page at the start of the
gesture; the page turn was measured from the drag's origin offset, in the wrong direction (a drag to the right went to the
previous page) and could happen once only; the pager dropped the page the drag started on after a turn, killing the gesture;
the menu opened only at release.

Second batch (this slice):

- **The Dock joins the same pick-up.** A pinned app or folder is lifted by a long-press then a move, with no edit mode
  (the menu unless it moves: `HoldMenuRule`). A Home item dropped on the Dock is pinned at the slot it hovers; a Dock item
  dragged out is placed on the Home cell under the finger. Both are moves. See `gestures.md`, "Pinned items".
- **A new page past the last.** Held at the trailing edge on the last page (one with items), a drag offers an empty page
  (`HomeDragPages`); it is created for the drag, kept when the item is dropped on it and discarded when the drag is
  cancelled or ends elsewhere. The page dots show the target page.
- **The dead zone works across pages.** After a page turn a drop on top of an item of the new page moves the item to that
  page's first free cell and makes the folder there (`DropHomeItem.dropOnItemId`).
- **Ghosts.** A folder leaves its desaturated, dimmed tile and four icons (built from the apps' baked ghosts); a widget
  leaves the outline of its span with the card corners, at the source and at the landing.
- **Frame stability.** Composition sees the drag without the finger's place; the lifted icon and the edge hold read it in the
  layout phase. Moving within a cell recomposes no cell; crossing cells recomposes only those it touches
  (`HomeDragFrameStabilityTest`).
- **Drop and cancel motion.** An item that arrives in a cell from elsewhere settles on the existing spring
  (`RiffleMotion.snappy`): the lifted item from where the finger let it go (a drop, or a cancel returning it to where it
  was lifted from), and pushed neighbours from where they were, each time the preview moves them. A translation read in
  the draw phase (`homeItemSettle`, `HomeItemMotion`); reduced motion jumps; further than four cells (another page) it just
  lands.
- **Done after this (ADR 0003, Amendment 15):** the Dock reorder wears the ghost treatment and a Dock item carried onto
  Home gets the push ring; drag from the Library; drop to remove; spill when the page is full; multi-select in Edit page;
  hosted platform widgets receive taps and scrolls. See `gestures.md`, "Dragging on Home".
- **Not done:** the Dock reorder shows the outline at the slot it would take and the dimmed icon while the finger is away
  from the Dock, but not a source ghost in the origin slot (the run reflows live, so a neighbour takes it); a Dock
  folder shows no ghost (an app does). "Move to page" for a selection (Home has no page picker). Group drops onto an
  app (a folder of the whole group). Widget and stack selection. Undo for removing a single item by drop (the menu has
  none). Every drag path was exercised through Robolectric touch injection; none has run on a device.

## The library view mode is retired

ADR 0003, Amendment 10. Amendment 8 retired the app-library Home *mode* (settings); this retires the layout *view mode*
`HOME_SCREEN_LIBRARY`, so nothing fills, sorts or compacts a Home the user can arrange.

### What the trace found

The view mode is read or written in: `HomeLayoutKey` and `HomeLayoutSet` (`layouts`, `activeKey`,
`preferredModesByDeviceClass`, `modePairsByDeviceClass`), the three codecs of the set, `WorkspaceHome` (`homeModeFor`,
`shown`, `borrowCandidates`), `HomeLayoutWorkspaceMapper.workspaceId` and `PoolHomeSync.workspaceOf` (the workspace and
pool arrangement id `ws:<layout class>:<mode>`), `PoolHomeLayoutRepository`, `withSelectedHomeLayoutMode`,
`LauncherShellViewModel.followActiveWorkspace`, `ModeSurface` and the dock edge of the Library surface, and the old
auto-fill (`withHomeScreenLibraryApps`, `withCompactedLibraryApps`, `withoutTrailingEmptyLibraryPages`,
`repackedForDockPosition`, run from refreshes, every home, dock, settings and folder edit, shortcut moves, restores) and
`withRefreshedGeneratedPages` (re-filled the generated pages on every app or notification refresh).

What a real install can be in, and what happened to it:

| State | Stored as | Before | Now |
| --- | --- | --- | --- |
| (a) Fresh | Standard key, no library layout, nothing to convert | Nothing re-filled | Unchanged |
| (b) Long-time Standard user | Standard key; maybe a dormant library layout from a visit long ago | Unchanged | Unchanged; the dormant layout is left alone |
| (c) Settings `HomeMode.AppLibrary` | Settings value; Home was whatever the layout was | One-time A to Z placed Home (Amendment 8) | Unchanged; if (d) also holds, the library layout is converted first and the fill replaces the items, no app is placed twice |
| (d) Layout view mode `HOME_SCREEN_LIBRARY` | `activeKey` or the device class's preferred mode, the library key's layout (Home pages in the pool; `library:n` pages, their widgets and folders only in the set store), the active workspace `ws:<class>:home_screen_library` or a preset forked from it | Re-filled and compacted on every refresh: removed items came back | Converted once to the Standard key as placed items; never re-filled |
| (d') Stored Standard, workspace brings the library mode back | Active workspace is the migrated library one (or a preset that forked from it) | The shell flipped to the library mode on load | Shown by the Standard key; a stored library layout, if any, is converted |

Per device class: every class of the stored set (phone, landscape phone, foldable, tablet, desktop) is converted by its own
current mode, each the first time the shell shows it (the phone at the first start, a foldable when it is first unfolded). A
class that is not shown is not converted yet, because the pool can only say which arrangement a layout edits for the layout
it shows; until then nothing touches it (nothing fills layouts any more) and its stored layout is intact. Per workspace: the
conversion edits the arrangement of the workspace the layout shows (the migrated library one, or the preset's own, after
its fork); other stored workspaces and arrangements are not touched.

### What changed

- `LibraryViewRetirement` (`:core:domain`) converts a stored set in two steps the shell runs together
  (`LauncherShellViewModel.retireLibraryViewMode`, called after the workspaces are retargeted, after every state change
  and after a settings action such as a backup import, until nothing is left): pages of retired types become placed pages,
  committed as an ordinary edit so the pool takes their items in; then the library layout moves to the Standard key (a
  projection only). The shell's whole layout set is kept first in `home_layout_before_one_home` (written once, never over an
  existing copy).
- Workspace ids are unchanged. `WorkspaceHome.shown` shows a migrated workspace of any mode for the active key;
  `homeModeFor` never answers the library mode; `legacyHomeModeFor` is the old derivation the conversion reads.
- Deleted: `withHomeScreenLibraryApps`, library compaction and repacking, `withoutTrailingEmptyLibraryPages`,
  `withRefreshedGeneratedPages`, the library reflow after a shortcut move, the actions `SelectLibraryPageCompaction` and
  `SelectSelectedHomePageType`, the "Page type" chips of the page overview, and the library mode from the default view mode
  availability (nothing can select it). Kept so stored data decodes: `LauncherViewMode.HOME_SCREEN_LIBRARY`,
  `LauncherPageType.AllApps` and `Generated`, `GridSettings.compactLibraryPages`, the library dock edge.

### Stored pages of retired types

| Type | Becomes | Why |
| --- | --- | --- |
| All apps (`AllApps`, `library:n`) | Placed page, items kept in place | What the user saw and may have arranged |
| App, Category, Work, Personal | Placed page with the items it holds now; dropped when empty | App lists the user saw; no longer regenerated |
| Favourites, Frequent | Placed page with the items it holds (usually none, then dropped) | Same |
| Today | Dropped | Recent apps are the Stream's Recents section |
| Cards (notification groups) | Dropped | Notifications are a Stream section; the items were notification groups, not apps |

### Tests

Deleted as obsolete (the behaviour is intentionally gone):

- `HomeScreenLibraryLayoutTest` (all 16: auto-fill, overflow pages, compaction, trimming, leaving the library mode) and
  `HomeScreenLibraryCompactionTest` (both: repacking for a side dock): the code is deleted.
- `HomePageControlsTest.pageTypeOptionsExposeClassicLibraryAndGeneratedPages`: the chooser is gone.
- `LauncherFolderEditReducerTest.createFolderUsesLibraryModeShortcutsBeforeEditing` and
  `LauncherShellFolderViewModelTest.createsHomeFolderFromLibraryModeShortcutsAndSavesLayout`: folders of auto-filled
  library shortcuts. Folder creation stays covered by the other tests of both files.
- `LauncherHomePageEditReducerTest`: `selectingCategoryPageImmediatelyPopulatesCategorizedInstalledApps`,
  `categoryRefreshClearsGeneratedShortcutsWhenCategoriesBecomeUnavailable`,
  `disabledContextualBehaviourPreservesTheConfiguredPageSelection`,
  `selectingCategoryPageFromSettingsRefreshesSettingsTargetLayout` (generated pages are no longer created or refreshed;
  editing the settings target layout of another device class stays covered by the grid-dimension test beside it) and
  `launcherViewModeSelectionReflowsHomeScreenLibraryApps`.
- `LauncherShellHomeGridViewModelTest.enablingLibraryPageCompactionImmediatelyReflowsGeneratedApps` and
  `compactLibraryPagesReflowGeneratedAppsAfterGridChanges`, and
  `LauncherShellMoveHomeShortcutToCellViewModelTest.reflowsCompactLibraryPagesAfterMovingGeneratedShortcut`: compaction.
- `LauncherShellPageOverviewViewModelTest.updatesSelectedPageTypeFromOverview` and `pinsSelectedPageFromOverview`: the
  chooser, and pinning a generated page.
- `LauncherShellViewModeViewModelTest`: `savesLauncherViewModeSelection`,
  `choosingTheHomeScreenInSettingsWhileOnLibraryKeepsLibraryAndChangesWhereThePullLeads`,
  `refreshAddsNewInstalledAppsWhenLibraryModeIsActive`, `switchingBackToStandardRemovesGeneratedLibraryApps`,
  `switchingModesPreservesSeparateModeLayouts` and `switchingDeviceClassPreservesSeparateDeviceLayoutsForCurrentMode`:
  they chose the library mode and expected it to fill. Switching device classes with separate layouts stays covered by
  `editsAfterInitialDeviceSelectionDoNotOverwritePreviousDeviceLayout` and its neighbours; "a refresh adds nothing" is
  now `LauncherShellLibraryRetirementViewModelTest.aRefreshNeverPlacesAnAppTheUserDidNotPlace`.
- `HomeLayoutUnavailableAppPrunerTest.removesTrailingEmptyAllAppsPagesAfterPruningUnavailableApps` and
  `HomeLayoutVisibilityFilterTest.removesTrailingEmptyAllAppsPagesAfterFiltering`: trimming of generated library pages.
  The tests that guard pruning of apps (data safety) are unchanged.

Rewritten: the two action lists (`LauncherActionDomainTest`, `LauncherShellHomePageActionRoutingTest`) lose the removed
actions; `WorkspaceHomeTest` and `PoolHomeActiveWorkspaceTest` assert the old derivation through `legacyHomeModeFor` and the
new answer through `homeModeFor`; `LauncherShellViewModeViewModelTest.importsBackupDocumentIntoStateAndRepositories` imports
a Standard backup (a library backup is in the new tests); `LauncherShellViewModeAvailabilityViewModelTest` starts with a
stored library layout and expects the converted Standard Home.

Added: `LibraryViewRetirementTest` (the rules on the stored set), `LibraryViewRetirementPoolTest` (the same path the shell
runs, with the pool: removal and moving stick across a refresh and a restart, a new install is appended and nothing else
moves, the widget keeps its host id, a workspace forked from the library arrangement is converted in its own
arrangement, the settings-level migration afterwards does not double-place), `LauncherShellLibraryRetirementViewModelTest`
(through the shell, from the stored form of the library layout including the oldest format without page types: convert once,
removal, move, refresh, restart, new install, Simple mode on and off, a second device class) and
`LibraryViewRetirementCodecTest` (every stored page type decodes and becomes what the table says).

## Saved Home layouts, import undo and Reset settings

ADR 0003, Amendment 17. Three slices, in this order:

1. **Saved Home layouts** (`core/domain/.../home/saved/`, `app/.../savedhome/`, `SettingsHomeLayoutsPage.kt`):
   `SavedHomeLayouts` (the list rules and the cap), `SavedHomeCapture` (Home pages to an unbound snapshot),
   `SavedHomeReconciler` (the pure rule applied on activation), `SavedHomeLayoutsCodec` (versioned, safe decode), the
   store, and `SavedHomeLayoutsController` (the only code that replaces Home from a layout). Order of writes: the
   automatic copy is durable first, then Home is replaced, so a crash at any point leaves the previous Home recoverable.
   `HomeLayoutRepository.holdWidgetIdReleases` keeps widget host ids until the Undo window closes.
2. **Undo for backup import** (`BeforeImportCopies`, `LauncherBackupDocumentHandler`): the copy is written before
   anything is replaced; failures carry a reason.
3. **Reset settings** (`SettingsReset`).

Not built: re-binding a widget that needs a permission prompt or a configuration screen (it stays a placeholder), a
tap-to-set-up action on the placeholder, resetting the Stream's sections, and keeping widget ids past the Undo window
for an import. Nothing here has run on a device.
