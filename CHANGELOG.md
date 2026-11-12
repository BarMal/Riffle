# Changelog

## Unreleased

### Added

- Glance widgets (ADR 0003, [`docs/product/glance-widgets.md`](docs/product/glance-widgets.md)): three Riffle widgets, Now playing, Next event and Top story, in a new "Riffle" group at the top of the widget picker. Add one like any widget (drag it, or use "Add to Home"); it lives on a Home page, moves, resizes and is removed in edit mode like any other, and draws the same card the Stream does on Regular glass lit by the app's colour: now playing with artwork and previous, play or pause and next; the next event with its time, place and countdown; the newest unread article with its lead image, feed and age. A small widget shows a compact layout and keeps the controls it has no room for in a "More actions" menu and in accessibility actions. Each has an honest empty state ("Nothing playing"), the Stream's "Allow access" when a permission is missing, and "Hidden content" for private items. Tapping now playing opens the Stream on Now; tapping the next event or the top story opens it in its own app, and "Open Stream" in the menu lands on Agenda or Feeds. They read through the Stream's own sources only while their page is showing. Nothing is bound through Android's widget host, and they come back after a backup restore as the widgets they were.
- Tasks from Todoist (ADR 0003, [`docs/product/stream-tasks.md`](docs/product/stream-tasks.md)): paste your Todoist personal API token in Settings > Stream > Tasks and the Tasks section shows what is due today and overdue (overdue first, then by time, then priority). Tick a task to complete it (it ticks at once and comes back if Todoist refuses), tap its title to open it in the Todoist app or on the web, and type in the quick-add field to add a task due today. It refreshes when the Stream opens, when you tap refresh or Retry, after each change, and every five minutes while the Stream is open (not on a metered network or in battery saver). Offline it keeps showing the last tasks, marked as such; a rejected token says so and offers to replace it. The token is encrypted with an Android Keystore key, kept on this phone only, and left out of Android's backup, device transfer and Riffle's backup file. Task text follows a new Tasks level on Settings > Privacy. Riffle contacts Todoist only after you save a token.
- Settings by Place (ADR 0003, [`docs/product/settings.md`](docs/product/settings.md)): the main page is a search field and ten rows (Home, Dock, Library, Stream, Appearance, Gestures, Motion and haptics, Privacy and access, Backup, About), with the Floating dock under Dock, Hidden apps under Library and Notifications (with Hidden items), Agenda (with Calendar feeds), Tasks and Feeds under Stream. Back always goes to the parent page. Settings search finds every individual setting by its name, its path or its keywords and opens its page scrolled to it; the launcher's own search offers the same entries. The Stream page sets each of the six sections on or off, its order and "Show as" Stack or List, which are the same stored values the Stream's own header menus change; Library > Show as is the same stored view as the Library's long-press. Tasks connects Todoist.
- Backups now include your calendar feeds (name, link and on or off, never the events read from them) and, as before, which Stream sections are on. Calendar feed links can carry a private key, so keep the backup file private; Android's own backup still leaves them out. Backups from earlier builds still import.

- Typed Stream content in the domain: `StreamContent` (notification, media with an optional queue, event including calendar feeds and the next alarm, article, task, recent app), the six `StreamSection`s and `StreamContentProvider`, which observes a section as loading, ready, empty, permission required, not connected, unavailable or off, with Hide rules and content levels applied and each section's fixed grouping, sorting and limits. Nothing draws it yet.
- Real data behind the typed content: live media sessions (play state, position, duration, queue, artwork and transport controls) on media cards, message history, reply and snooze on notification cards, the next alarm from `AlarmManager`, frequent apps after the recent ones, a Stream-only agenda window of today and the next seven days, and mark-read for articles backed by the RSS cache. `StreamActionGateway` is the one entry point for acting on Stream content. None of it is persisted, and sensitive or hidden content exposes no history and no reply.

- The Stream (ADR 0003): a full surface that replaces Home, with the Dock still drawn under it, holding six sections (Now, Notifications, Recents, Agenda, Tasks, Feeds), each drawn as a Stack of cards or a List of rows. Cards and rows are built per content type: now playing has transport controls, a scrubber and an expandable queue; a notification expands to its conversation with reply; events show their time span and a countdown; articles carry a lead image. Long-press a section header (or use its options button) for "Show as" Stack or List, which redraws at once and offers Undo; sections can also be moved and hidden there. Each section has honest empty, permission ("Allow access to show this"), not-connected (Tasks, until P10), off and unavailable states. Reach it with the new "Stream" home gesture action or the Dock's "Open Stream" accessibility action; Back and the Home button return to Home. Which sections are on, their order, their view and their options are kept per posture in their own storage and are included in backups (older backups still import).

- The Dock is the handle (ADR 0003): its second side now holds live apps (apps with something new, newest first), one now-playing entry while media is active, and the Stream button, always last. Tap the Stream button to switch between Home and the Stream, tap a live app to open the Stream on that app's stack, tap now playing to open it on Now. A pull away from the dock's edge that starts on the pinned side opens the Library; one that starts on the dynamic side opens the Stream, following your finger, and the same pull from the Stream returns Home. It works on every dock edge. The Dock's accessibility actions are "Open Stream" (or "Back to Home") and "Open Library", with Ctrl + the arrow of the pull direction (Shift for the Library) on a keyboard. New "Library" home gesture action.
- The Stream draws notification pictures, article lead images and app icons.
- The unfolded split (ADR 0003): on an unfolded foldable, a tablet or a desktop window, Home and the Stream can share the screen with a movable divider, or either can take the whole screen (Home only, split, Stream only). The Stream takes the side nearer the Dock's dynamic side for a bottom or top Dock and the side away from a side Dock; over a hinge the divider snaps to it and no pane crosses it, and a tabletop stacks the panes above and below it. A Dock pull on the dynamic side tracks your finger and settles to the nearest of the three states by position and speed (stopping partway leaves the split); the Stream button toggles Home only and the last-used Stream state; Home returns to Home only. The divider is a 48 dp target you can focus, move with the arrow keys or with the actions "Home only", "Split" and "Stream only", and it snaps instead of animating under reduced motion. The split's fraction and the last-used state are kept per posture with the Stream's layout and in backups (older layouts and backups read as a 40% split). Folded and phone behaviour is unchanged.

- The Library (ADR 0003): every installed app, and search, on its own surface: a Thick glass sheet that rises over Home or the Stream with the Dock still visible under it. Pull up on the Dock's pinned side and the sheet follows your finger (interruptible, with the Stream pull's thresholds; it fades under reduced motion); a pull down on its header, Back, Home or launching an app closes it. It has three views: Categories (the default; iOS-style glass tiles with each category's first icons, lit by their colours, and the name opening the full category), an A-Z list with the alphabet scrubber, and a Grid. Long-press the header (or use its options button) for "Show as", which redraws at once and offers Undo; the choice is stored per device class and also set in Settings > Library. Categories come from the app's platform category, with "Other" for the rest. The search field sits at the top: apps first, then shortcuts and settings, Enter launches the top hit, clearing restores the view. Swipe down on Home (the "Search" gesture) opens it with the keyboard up. Recents and frequent apps are only in the Stream.
- One list of hidden apps: app exclusion rules that hide an app from all apps are folded into Hidden apps at start-up (nothing hidden becomes visible, and unhiding an app now sticks); other rules, such as a Recents-only hide, are untouched.

### Fixed

- Glass surfaces on Home (the Dock, the Search pill, the page indicator, folders, widget frames) showed as opaque near-black instead of translucent glass: the glass shader ignored that the system wallpaper is not part of the app's content. Glass now keeps the wallpaper visible through it, at the weight's tint alpha (raised where needed to keep text readable over a bright wallpaper), and the Dock's translucency is exactly your "Dock background" setting again (0% clear, 100% opaque).

### Changed

- Settings are reorganised by Place and use one set of rows: choices are radio groups of wrapping options, grid columns and rows are a stepper, long subtitles wrap instead of being cut, and every row speaks its state. Renamed: "Overlay edge" is "Edge" under Floating dock, "Hidden items and rules" is "Hidden items", "Card corners" is "Corners", "Color mode" is "Colour mode", "Dock height" is "Icon size", "Calendar feeds (ICS)" is "Calendar feeds", "App drawer" is "Library" and the gesture actions read "Open notifications", "Open search", "Open Library", "Open Stream", "Open settings", "Edit Home", "Page overview" and "Full-screen Home". Wallpaper and the two system bar switches moved from Appearance to Home; Default home app and every permission moved to Privacy and access, which also holds the screen protection switch and the content level of each source; the Sources pages are folded into the Stream's sub-pages and RSS feeds moved under Stream > Feeds.
- Settings saved by this build carry version 11. The settings codec now reads the version: older blobs decode as before, a blob from a newer build decodes what this build understands (a changed or unknown section reads as its default) and a copy of it is kept before this build overwrites it.
- The old "Fullscreen home" flag is the same as hiding both system bars: saved settings that have it hide both, and the "Full-screen Home" gesture hides both.
- Swipe down on Home is now bound to "Search" by default (it was Notifications), which opens the Library with its field focused; two-finger swipe up opens Notifications instead of Search. Saved bindings keep their value.
- The app drawer and the search page are gone as full-screen surfaces: the "Apps" button, the search pill and the Search and Library gestures open the Library. The drawer's "App presentation" setting is replaced by the Library's view (List becomes the A-Z list, Icons the Grid, anything else Categories). The app menu's "Add to dock" reads "Pin to Dock".
- The dock's "Dock notifications" switch and "Notification space" slider are one "Live apps" setting (none to five). Saved docks decode: notification cards on keep their count, off stays none. The workspace dock dynamic section is no longer written for new migrations.
- The dock pull no longer opens the workspace menu or switches Home and Library; the menu stays reachable through its "Workspace menu" accessibility action until it is removed. Cards no longer lists stages on the dock (its stage chips are always shown).
- A workspace may hide its dock only while a gesture opens the Stream or the Library (it used to be the workspace menu).
- The home gesture actions "Workspace menu", "Next stage" and "Previous stage" are removed; settings that name them decode as no action.
- One glass material: `GlassSurface` now takes a weight (Thin, Regular, Thick) that owns frost, tint, contrast scrim, hairline and shadow, and draws the dock's glass sheen on every surface.
- The dock's icon-colour spotlight is generalised to any glass surface and now also lights open folders, the search results panel and Stream-style item cards from the colours of what they hold.
- The search pill and results panel, the launcher panel, folders, the widget picker, widget frames, the context menu, dock notification cards, the page indicator, page overview cards and Settings sections all use `GlassSurface`.
- Reduced transparency is now one top-level Appearance setting honoured by every glass surface, the sheen and the spotlight. Settings saved with it under the card stack's motion options are migrated.
- Glass is the only look. The Material, Minimal, Retro, Terminal and Custom theme presets are gone (a saved preset becomes glass), as is the per-workspace Appearance override (a saved skin is ignored). Wallpaper-derived colour stays.
- The Liquid glass on/off switch is gone: glass is always on and Reduced transparency is the off switch. The Settings section is now "Glass".
- The dock effect choice (flat, elevated, outlined) is gone; the dock uses the standard glass sheen.
- Custom colours are reduced to a custom accent; the background, dock, label and label background colours are ignored. "Card corners" is now "Corners".
- First run is four short steps, one per Place: Home, the Dock, the Library and the Stream. The Stream step asks for notification access, calendar and usage access, each after a one-line explanation and each skippable; a skipped section shows "Allow access". Existing users are shown nothing; "Set up Riffle again" in Settings re-runs it and changes nothing you placed.
- Backups written by earlier builds still import (Home, Dock, settings, hidden apps, exclusions, the Stream layout and the stored workspaces are restored), and a backup still writes the stored workspaces, so a round trip loses nothing.

### Removed

- Settings that did nothing, with their stored fields (saved settings that name them still decode): the card stack's appearance, pane arrangement, thread grouping and order, stage spine, page offset and "all notifications" toggles (the Stream's Stack has fixed physics, so there is no "Stack feel" page), Contextual behaviour, "After leaving Library", "Expandable dock", "Open the shelf with" and "Dock panel" in Settings, "Compact library pages" in Settings, and the tap-to-cycle motion rows. The Sources, Permissions, Privacy, RSS feeds, Calendar feeds, Contextual and Developer pages are no longer separate top-level pages.
- One arrangement per posture (ADR 0003): Home is the arrangement of each layout's active workspace as stored, and the Dock is the pool's dock. Nothing switches workspaces any more, and nothing shows or edits the others: they stay in storage, decodable and untouched (including their pool items, which are never collected), so switching can return later. A layout with nothing stored shows the home you had.
- The workspace menu and its "Workspace menu" accessibility action, workspace switching and Settings > Workspaces (switch, rename, duplicate, make default, reorder, install preset, reset to preset, copy between layouts, dock and new-apps overrides), the presets (iOS-style, Nova-style, TimeScape, Niagara, Kvaesitso-style), the workspace editor, the content wizard and goals, saved lenses with the lens library and Settings > Saved lenses, the advanced lens builder, per-lens search queries and the Search source's settings page, lens pages after the Home pages with their Hide menu, and Finder pages. The Library and the Stream replaced them.
- The adaptive stage surface (the Cards home mode, its stage pager, card detail, dock stage selector and "Pin as a card" menu items), its live tuning overlay and Settings > Cards appearance, the generated notification-cards page (a saved one now says the notifications are in the Stream and opens it), and the RSS stage. A layout saved in the Cards mode now shows its placed items on the standard home. The card-stack physics the Stream's Stack view uses are kept.
- The Developer switch "Home from placed items": the placed-items pool is the only home, and the one-time move into it runs for everyone. If it cannot be written the home is shown read-only from what is stored instead of falling back to the old storage.
- The "Set up your home" offer and the wizard-based walkthrough.

## Alpha 192

Release notes for the next alpha after Alpha 191.

### Changed

- Expanded dock shelves can now show up to three compact notification-backed cards above the overflow dock row.
- Shelf cards reuse the existing notification overview grouping and launch the matching app when tapped.
- When notification access is unavailable, the expanded shelf now degrades to a compact permission prompt card instead of showing stale or empty notification content.

### Verification

- Full GitHub CI `Verify` passed for PR #582.
- Local verification included focused dock notification card tests and full `./gradlew verify`.

### Known Limitations

- Notification cards only appear when the dock already has overflow and the expanded shelf is open.
- Shelf cards are fixed for now: no configuration, no inline dismiss action, and no main-dock card presentation yet.

## Alpha 191

Release notes for the next alpha after Alpha 190.

### Changed

- Overflowing docks can now be swiped up to reveal an expanded shelf with the additional dock items.
- Collapsed docks keep the existing horizontal overflow scrolling behavior.
- The expanded shelf splits primary dock items from overflow items without changing persisted dock data.

### Verification

- Full GitHub CI `Verify` passed for PR #580.
- Local verification included focused dock overflow shelf tests, ktlint/detekt checks, and full `./gradlew verify`.

### Known Limitations

- Expanded shelf state is local UI state and is not persisted.
- Card-style and notification-backed dock items remain future #579 slices.
- Manual validation is still needed on physical devices with gesture navigation and foldable widths.

## Alpha 190

Release notes for the next alpha after Alpha 189.

### Changed

- Widget picker tiles now offer explicit Home and Dock targets.
- Dock widgets now use the existing Android widget bind, permission, and configuration flow before being persisted.
- Dock widget removals and rejected dock placements now clean up hosted widget IDs.
- Duplicate dock widget completion actions are rejected to avoid duplicate dock item IDs.

### Verification

- Full GitHub CI `Verify` passed for PR #577.
- Local verification included focused widget binding, dock editing, hosted-widget cleanup, picker action, route ownership, and full `./gradlew verify`.

### Known Limitations

- Dock widget sizing still relies on the existing dock renderer and settings.
- OEM widget bind/config activities still need physical-device validation.

## Alpha 189

Release notes for the next alpha after Alpha 188.

### Changed

- Home gestures can now be configured to enter Fullscreen home using the existing fullscreen appearance setting.
- Hosted home widgets now expose context-menu resize actions for one-cell width and height changes.
- Persisted dock widgets with hosted widget IDs now render through the existing Android widget host factory instead of only showing a placeholder.
- Notification overview empty states now explain notification access status and offer a notification access shortcut when needed.
- Search app and shortcut results now prefix Work and Private profile labels when needed.

### Verification

- Full GitHub CI `Verify` passed for PRs #571, #572, #573, #574, and #575.
- Local verification included focused gesture/settings tests and full `./gradlew verify`.

### Known Limitations

- Fullscreen gesture support enters fullscreen home; it does not add a separate exit or toggle gesture.
- Widget resizing is context-menu based and still relies on existing grid collision rejection.
- Dock widget add/reorder/resizing flows remain future dock work.

## Alpha 188

Release notes for the next alpha after Alpha 187.

### Changed

- Settings > Gestures now surfaces conflicting home gesture assignments using the existing gesture conflict detector.
- Page overview now has regression coverage for configured gesture entry into Manage pages.
- Wallpaper support documentation now includes Static and Scroll motion validation steps and current native wallpaper offset limitations.
- Settings > Layout now hides unavailable launcher view modes for the selected device class and falls back to an available mode for previewing settings.
- Search setting results now show the matched concrete setting alias as their subtitle instead of a generic setting label.

### Verification

- Full GitHub CI `Verify` passed for PRs #565, #566, #567, #568, and #569.
- Local verification included focused gesture tests and full `./gradlew verify`.

### Known Limitations

- Gesture conflicts are surfaced but not blocked.
- Wallpaper issue #7 still needs physical-device validation before closure.
- Card interface mode remains unavailable until its availability policy opts it in.

## Alpha 187

Release notes for the next alpha after Alpha 186.

### Changed

- Launcher search now matches more concrete Settings options, including dock spacing, label sizing, wallpaper motion, floating dock handle controls, gesture actions, permissions, and backup actions.
- Android widgets that require provider configuration now launch the standard widget configuration flow before being added to the home layout.

### Verification

- Full GitHub CI `Verify` passed for PRs #562 and #563.
- Local verification included the release-notes `./gradlew verify`; worker verification covered settings search and widget coordinator/request-handler tests.

### Known Limitations

- Settings search still opens the owning Settings page rather than scrolling to a specific row.
- Widget configuration was covered by unit tests and platform intent wiring, but still needs physical-device validation with real configurable widget providers.

## Alpha 186

Release notes for the next alpha after Alpha 185.

### Changed

- Settings > Gestures can now bind a launcher gesture to Manage pages, opening the home page overview directly.
- App search now matches Android app categories such as Game, Audio, Video, Image, Social, News, Maps, and Productivity when the system provides category metadata.

### Verification

- Full GitHub CI `Verify` passed for PRs #559 and #560.
- Local verification included focused gesture action, app category search, package-manager mapping tests, and full `./gradlew verify`.

### Known Limitations

- Category search depends on Android app category metadata and does not add category chips or grouping yet.
- The Manage pages gesture action is configurable, but defaults are unchanged.

## Alpha 185

Release notes for the next alpha after Alpha 184.

### Changed

- Empty bottom home areas now expose the launcher context menu on long press, including the area around the dock and the search/indicator strip.
- Page indicators now open launcher search when tapped.
- The page indicator returns to the search pill sooner after paging settles.

### Verification

- Full GitHub CI `Verify` passed for PR #557.
- Local verification included focused app compile, app unit tests, ktlint, detekt, and full `./gradlew verify`.

### Known Limitations

- Long-press and tap target behaviour still needs manual validation on device because the app module does not currently include a Compose UI gesture test harness.

## Alpha 184

Release notes for the next alpha after Alpha 183.

### Changed

- Notification overview now includes category filter chips so active notification groups can be narrowed by their latest category.
- Category filter chips show the current total count and category counts from existing notification grouping data.

### Verification

- Full GitHub CI `Verify` passed for PR #555.
- Local verification included focused notification overview tests, ktlint, detekt, and full `./gradlew verify`.

### Known Limitations

- Notification category filters are local to the notification overview UI and are not persisted.
- Filtering uses each app group's latest notification category instead of splitting a single app into multiple category-specific rows.

## Alpha 183

Release notes for the next alpha after Alpha 182.

### Changed

- Settings > Appearance now includes a Wallpaper motion control with Static and Scroll modes.
- System wallpapers can now follow the selected home page using Android wallpaper offset APIs when Wallpaper motion is set to Scroll.
- Launcher search web results now use a distinct "Search web" button and show clearer generated example searches for images, news, and videos.
- Dock render metrics now apply the existing compaction policy on narrow widths, so five-icon folded-mode docks shrink spacing and icon size before falling back to overflow navigation.

### Verification

- Full GitHub CI `Verify` passed for PRs #551, #552, and #553.
- Local verification included targeted wallpaper controller, search grid/web launcher, dock metrics, detekt, and full `./gradlew verify` runs.

### Known Limitations

- Wallpaper scroll support depends on the active wallpaper and OEM honoring Android wallpaper offset APIs, and it updates settled page offsets rather than drag-frame parallax.
- Web search examples are generated delegated searches, not live-fetched online result snippets.
- Dock folded-width behavior still needs manual validation on physical foldable hardware.

## Alpha 182

Release notes for the next alpha after Alpha 181.

### Changed

- Launcher search settings results now open Settings directly on the matched top-level settings page instead of always opening the Settings index.
- Generic Settings entry points still open the main Settings page, and unknown settings search IDs fall back to the main Settings page.

### Verification

- Full GitHub CI `Verify` passed for PR #549.
- Local verification included targeted search result, settings page mapping, action routing, and settings page navigation tests plus full `./gradlew verify`.

### Known Limitations

- Settings search deep links target top-level settings pages only; row-level highlighting or automatic scrolling within a settings page remains future work under #20.

## Alpha 181

Release notes for the next alpha after Alpha 180.

### Changed

- Launcher search now presents web search as a dedicated full-width Google search panel instead of mixing it into the app icon grid as a square pseudo-icon.
- Web search now shows compact local preview refinements for images, news, and videos, each delegating to the existing Android web search flow.
- Launcher search result counts now reflect local launcher results only: apps, shortcuts, and settings.

### Verification

- Full GitHub CI `Verify` passed for PR #547.
- Local verification included targeted search grid and search surface text tests plus full `./gradlew verify`.

### Known Limitations

- Web previews are local delegated Google searches, not network-fetched result snippets. Real online provider prefetch remains a separate provider/privacy/loading-state slice under #20.

## Alpha 180

Release notes for the next alpha after Alpha 179.

### Changed

- Settings > Appearance now launches the Android wallpaper picker through a standard chooser titled "Change wallpaper" while still checking the base wallpaper intent for availability.
- Launcher search now includes a web result for non-blank queries, labeled as a Google search, and delegates to Android web search with a Google URL fallback.
- Dock folder items are now actionable: tapping a dock folder opens the existing folder surface instead of leaving the placeholder inert.

### Verification

- Full GitHub CI `Verify` passed for PRs #543, #544, and #545.
- Local verification included targeted tests for wallpaper picker chooser behavior, web search intent fallback and action routing, launcher search grid results, dock folder item state, dock folder resolution, and full `./gradlew verify`.

### Known Limitations

- Web search has no provider settings UI yet; the fallback URL is Google-specific, and on-device validation of installed search/browser handling is still useful.
- Riffle still delegates wallpaper selection to Android and does not receive or persist picker results.
- Dock widget items remain placeholders; dock widget hosting and extra dock folder edit/context affordances remain open under #6.

## Alpha 179

Release notes for the next alpha after Alpha 178.

### Changed

- Settings > Appearance now includes a Change wallpaper action that opens Android's wallpaper picker and reports unavailable or failed launches.
- Launcher search now shows matching Settings results alongside app and shortcut results, with settings results opening the Settings destination.
- Home gesture settings now have a domain conflict detector for duplicate non-disabled gesture actions.
- Dock rendering now handles non-app dock items explicitly; folders and widgets render as placeholder dock items instead of disappearing.
- Page overview now supports selected-page grid dimension changes without changing sibling pages or the layout default grid.
- Hosted widget IDs are now deleted when a widget bind succeeds but placement into the selected page is rejected.

### Verification

- Full GitHub CI `Verify` passed for PRs #536, #537, #538, #539, #540, and #541.
- Local verification included targeted tests for wallpaper picker routing/gateway behavior, settings search state and grid rendering, gesture conflict detection, non-app dock item state and JSON round trips, selected-page grid edits, widget placement rejection cleanup, and full `./gradlew verify`.

### Known Limitations

- Wallpaper selection is delegated to Android; Riffle does not receive or persist picker results, and manual OEM/API validation remains pending under #7.
- Settings search results open Settings generally; deep-linking to the matched settings page remains follow-up work under #20.
- Dock folders/widgets are placeholder-only in this alpha; opening/hosting/add flows from the dock remain open under #6.
- Page overview grid controls are compact one-step chips; richer numeric controls and deeper page customisation remain open under #17.
- Gesture conflict detection is domain-only; conflict UI and broader gesture types remain open under #14.
- Widget configuration flows, host size option updates, provider-aware resize behavior, and dock widgets remain open under #5.

## Alpha 178

Release notes for the next alpha after Alpha 177.

### Changed

- Launcher search now has a domain-owned provider and extensible result model for mixed global app and settings results.
- Settings page entries can now be projected into launcher search results, including dynamic status text such as hidden-app counts.
- Over-capacity dock layouts now render enough slots for all persisted dock items when capacity is positive, so existing items stay reachable through dock scrolling instead of disappearing.

### Verification

- Full GitHub CI `Verify` passed for PRs #533 and #534.
- Local verification included targeted tests for mixed app/settings search ranking, settings search projection, hidden app exclusion, blank global searches, dock over-capacity slot rendering, and full `./gradlew verify`.

### Known Limitations

- The new global search provider is not yet wired into the Compose search UI; app drawer and existing search surfaces keep their current presentation for this alpha.
- Dock folders, widgets/static items, explicit paging controls, and future card dock modes remain open under #6.
- Wallpaper picker integration remains open under #7.

## Alpha 177

Release notes for the next alpha after Alpha 176.

### Changed

- Reduced-motion home page changes now use the same short animated settle path for external page selections instead of snapping abruptly, reducing visual stutter between the pager and page indicator.
- Wallpaper source application now returns explicit success/failure results behind the platform controller boundary.
- Direct Settings > Appearance wallpaper source changes now show fallback feedback when system wallpaper cannot be applied.
- If system wallpaper application fails and Riffle falls back to the solid background mode, launcher settings are synced to that fallback source so persisted state and rendered background stay aligned.

### Verification

- Full GitHub CI `Verify` passed for PRs #530 and #531.
- Local verification included targeted tests for reduced-motion home pager policy, wallpaper apply/fallback behavior, fallback user-facing copy, and full `./gradlew verify`.

### Known Limitations

- Wallpaper picking/setting APIs and OEM-specific wallpaper device validation remain open under #7.
- Reduced-motion polish is still page-pager specific; the broader shared motion system remains open under #24.

## Alpha 176

Release notes for the next alpha after Alpha 175.

### Changed

- Saved Home Screen Library layouts now remain available at cold start, so the launcher does not fall back to Standard until the user toggles layout mode away and back again.
- Settings > Dock now projects the selected folded/unfolded device-class layout through a testable state helper, keeping dock edits independent across device tabs.
- Missing dock fields in stored layout JSON now inherit the layout entry's device-class defaults instead of always borrowing phone dock sizing.
- Visible home docks now preserve configured capacity, icon size, and spacing; overflowing dock content scrolls inside the capped dock instead of compacting away the selected settings or hanging off-screen.

### Verification

- Full GitHub CI `Verify` passed for PRs #527 and #528.
- Local verification included targeted tests for launcher view-mode startup restoration, device-specific dock settings projection, dock JSON defaults, dock render metrics, and full `./gradlew verify`.

### Known Limitations

- Manual foldable device/emulator validation is still needed for exact folded/unfolded dock feel.
- Dock paging and explicit overflow controls remain future work; this alpha uses horizontal scrolling for overflow.
- Reduced-motion pager polish remains a known follow-up outside this urgent regression release.

## Alpha 175

Release notes for the next alpha after Alpha 174.

### Changed

- Main branch quality gates now run CI `Verify` on pushes to `main`, and root `./gradlew verify` explicitly includes app debug assembly.
- Startup home layout selection now preserves saved single-layout home pages when the current device class does not yet have its own stored layout.
- Folded, unfolded, and tablet home layouts now start with distinct dock icon sizes and spacing, and dock settings edits target the selected settings device class without mutating the active layout.
- The initial launcher shell has JVM coverage for a completed first run with no installed app data, closing the initial app-shell epic.
- Wallpaper validation evidence now documents current support, manual device checks, and remaining picker/scrolling/failure-state gaps.

### Verification

- Full GitHub CI `Verify` passed for PRs #521, #522, #523, #524, and #525.
- Local verification included targeted tests for startup device-class layout restoration, dock settings target selection, device-specific dock defaults, launcher empty-state evidence, wallpaper/settings support, and root `./gradlew verify`.

### Known Limitations

- Dock paging and explicit overflow navigation remain future work; current behaviour still relies on compaction and scrolling guards when users configure too many dock items.
- Wallpaper picker integration, static/scrolling wallpaper controls, and explicit wallpaper API/policy failure messages remain open under #7.
- Reduced-motion pager polish remains a known follow-up; this alpha focuses on layout loading, dock settings, and epic closure evidence.

## Alpha 174

Release notes for the next alpha after Alpha 173.

### Changed

- Contextual behaviour gained a domain signal planner for personal/work profile presence, notification activity, and day-start inputs.
- Generated-page content plans can now materialize app content into deterministic home shortcut items while safely skipping unsupported notification-group content.
- Dock render metrics now expose the domain overflow mode classification while preserving the existing compact/scroll visual behaviour.
- Widget add completion now fits oversized preferred widget spans to the selected grid before adding the widget to home.
- App, hidden-app, folder-add, widget-picker, and shortcut search paths now share a common search-token normalizer.

### Verification

- Full GitHub CI `Verify` passed for PRs #515, #516, #517, #518, and #519.
- Local worker verification included targeted tests for contextual signals, generated-page item materialization, dock render metrics, widget span fitting, and search token normalization.

### Known Limitations

- Contextual signal planning is domain-only and is not yet wired to platform observers or model/action execution.
- Generated notification-group content is still intentionally skipped until card-backed generated content is implemented.
- Dock overflow navigation remains reporting/scaffolding only; paging or explicit overflow controls are future work.

## Alpha 173

Release notes for the next alpha after Alpha 172.

### Changed

- Reduced-motion home page selection now snaps directly to the selected page instead of running the external settle animation, avoiding intermediate page indicator drift.
- Settings now includes a Contextual page with a persisted default-off toggle for contextual launcher behaviour.
- Built-in launcher template catalog defaults now provide stable domain templates for standard app-drawer and conservative generated-page layouts.
- Generated page content plans can now be applied to generated page descriptors with defensive rejection for unavailable or mismatched plans.
- Dock overflow handling gained a domain policy that classifies fitted, compacted, and overflow-navigation cases for future dock scrolling or paging work.

### Verification

- Full GitHub CI `Verify` passed for PRs #509, #510, #511, #512, and #513.
- Local worker verification included targeted tests for the reduced-motion pager policy, template catalog defaults, generated page plan application, dock overflow policy, and contextual settings reducer/UI routes.

### Known Limitations

- Reduced-motion pager polish has unit coverage but no device visual pass yet.
- Contextual behaviour remains opt-in and does not yet run platform observers, contextual signals, generated-page behaviour, or model/action execution.
- Template catalog, generated page application, and dock overflow policy are domain scaffolding; UI wiring and overflow navigation controls remain future work.

## Alpha 172

Release notes for the next alpha after Alpha 171.

### Changed

- Folded and narrow home docks now fit five app slots within the available width by reducing spacing first, then icon size, with clipped scrolling as a final guard.
- Launcher view-mode selection now respects the availability policy when restoring or switching layouts, falling back to the standard app drawer when experimental modes are unavailable.
- Notification grouping now has deterministic ordering for same-package notifications across personal, work, and private profiles.
- Contextual launcher behaviour settings are now persisted in launcher settings JSON and remain disabled by default for existing users.
- Template-based layouts gained domain support for planning and applying seed pages.
- Generated pages gained a domain content-planning layer for app-backed, profile-backed, and notification-backed page descriptors.

### Verification

- Full GitHub CI `Verify` passed for PRs #502, #503, #504, #505, #506, and #507.
- Local worker verification included targeted unit tests for view-mode availability, template seed application, notification grouping, settings JSON migration, generated page content planning, and dock fit metrics.

### Known Limitations

- Contextual behaviour is model/persistence only; no settings UI or platform signal observers are enabled yet.
- Template and generated-page work remains domain scaffolding; it is not wired into the launcher UI yet.
- The dock fix prevents visual overflow on narrow widths, but dock paging and richer overflow controls are still future work.
