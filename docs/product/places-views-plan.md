# Places and Views: implementation slices

Status: **plan**, for [ADR 0003](../architecture/adr/0003-places-and-views.md). Each slice is one coherent
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

- On expanded postures, Home and the Stream can share the screen with a movable divider (platform
  `draggable` / anchored-draggable, ADR 0002); either can take the whole screen. A dynamic-side pull that
  stops partway leaves the split. Pane math accounts for the Dock and the hinge
  (`AdaptiveStagePaneLayoutPolicy` has the existing hinge logic).
- Store the split position per posture in `StreamLayout`.
- Screenshot tests: unfolded split, unfolded Stream only, tabletop.

### P7. The Library

- Views: Categories (iOS-style tiles, the default), A–Z list, Grid, from the existing `CATEGORIES`,
  `ALPHA_LIST` and `ICON_GRID` expressions; "Show as" by long-press on the header and in Settings.
- Search field at the top; results replace the list; swipe down on Home opens the Library with search
  focused. Category tiles are `GlassSurface` with the spotlight lit by their icons.
- Recents and frequent apps leave the Library (they are a Stream section).
- One hidden-apps list: merge `AppVisibilityRepository` hidden apps and app exclusion rules.
- The Library is no longer a workspace Finder page: it is its own surface, opened by the pinned-side pull.

### P8. Cut over and delete

Status: **built.** Left in place and listed in the commit message: the stored `LauncherSettings.cards` model and `cards/` stage code that
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

Status: **built**; see [`settings.md`](settings.md), "as built", for the tree, the removals and the differences.

Build the page tree, renames, removals and rules in [`settings.md`](settings.md). Slices P1 to P8 have
already removed the settings of what they removed; this slice does the reorganisation, the dead-setting
removal, shared rows everywhere, per-setting search, Back targets, reading the codec version, and backup
of calendar feeds and enabled sections.

### P10. Tasks from Todoist

- `TaskContent` source for Todoist: today's and overdue tasks, complete, quick-add. Personal API token
  entered in Stream > Tasks, stored encrypted on the device, excluded from backup, never logged.
- Network access only to the Todoist API host, off the main thread, with a refresh policy like RSS
  (`rss-refresh.md`). No shipped secrets. Honest states: no token, offline, error.
- Tests against a fake HTTP layer; no real network in tests.

### P11. Glance widgets

- A fixed catalogue of Riffle's own widgets placed from the widget picker like any other: Now playing,
  Next event, Top story. Each renders the same typed card as the Stream, in a `GlassSurface`.
- They are pool widgets with a Riffle provider id; sizing and placement use the existing grid engines.

### P12. Documentation and close-out

- Change ADR 0003 to Accepted. Update `README.md`, `AGENTS.md`, `CHANGELOG.md`.
- Replace `workspaces-dogfood.md` with a Places and Views device checklist: the Dock pull by side and the
  narrow-target question, each "Show as", the unfolded split, the spotlight on every surface, reduced
  motion and reduced transparency, data carried over from the previous build.
