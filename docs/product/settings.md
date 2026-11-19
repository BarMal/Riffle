# Settings: organised by Place

Status: **as built** (slice P9 of [`places-views-plan.md`](places-views-plan.md)), part of
[ADR 0003](../architecture/adr/0003-places-and-views.md). It replaces `workspaces-settings.md` and section 2 of
`workspaces-configuration.md`. Where the original design and the build differ, this document says what was built and
why ("Differences from the original design" below).

Paths: `A/` is `app/src/main/java/com/riffle/app/launcher/`, `D/` is
`core/domain/src/main/kotlin/com/riffle/core/domain/launcher/`.

## The page tree

The page list is the `SettingsPage` enum (`A/SettingsPages.kt`): every page names its `parent`, and Back always goes
there (`settingsBackTarget`). Every individual setting is a `SettingKey` (`A/SettingKey.kt`) that names its page, its
label and its search keywords as string resources. `SettingsPagesTest` checks that every page has a parent and is
reachable from the main page, that every setting is on a reachable page, indexed and anchored on its row, and that
Back goes to the parent.

The main page is a search field and exactly eleven rows, in this order. Typing replaces the rows with matching pages and
settings (label, path or keyword); a result opens its page scrolled to that setting.

| Page | Section | Settings |
| --- | --- | --- |
| **Home** | Home screen | Simple mode (the locked-down Home: every app A to Z, not arrangeable; it overrides the Stream, live apps and the Library) and Add new apps to Home (a switch, on by default: a new app is placed after the last item, off keeps it in the Library only). There is no "Home shows" choice any more (ADR 0003, Amendment 8) |
| | Fill Home | Add all apps to Home (a button; its subtitle says what it will do now: "Adds 23 apps that aren't on Home yet", or "Every app is already on Home" and disabled), Arrange Home A to Z (a button, after a confirmation) and Compact: fill each page completely (a switch, on by default). Both buttons act on the layout of the posture tab Settings shows and are off in Simple mode. See "Fill Home" below |
| | Grid | Columns, rows (a stepper), horizontal margin, vertical margin (per device class) |
| | Icons and labels | Icon size, show labels, label background (with its strength while it is on), label text size, label width, label sizing, label lines |
| | Behaviour | Returning to Home (restore, first page, start page) |
| | Wallpaper | Wallpaper (system, solid), wallpaper motion, change wallpaper |
| | Full screen | Hide status bar, hide navigation bar |
| | Set up | Set up Riffle again; Show hints again (brings back the Dock's one-time tips; the row shows where the launcher runs the set-up) |
| **Dock** | Dock | Show the Dock, Dock position, icon size, pinned icons, Dock width, Dock alignment (along the top or bottom edge: left, middle, right; down a side: top, middle, bottom), Dock corner radius, Dock background strength, spacing to the grid (per device class) |
| | Live apps | Show live apps (a switch), Number of live apps |
| | Stream handle | Show Stream handle (a switch, on by default; off in Simple mode and without the Stream): the small chevron beside the Dock on Home that opens the Stream with a tap (touch and hold: Open Library, Hide this handle). A feature switch like Show live apps, stored in `FeatureSettings.dockStreamHandle` |
| | Floating dock | Opens the sub-page |
| Floating dock (under Dock) | | Floating dock on or off, apps, edge, handle thickness, size, position, opacity, expanded icon size, orientation, labels |
| **Layout** | Folded, Unfolded | Per posture (a tablet and a desktop window use Unfolded): Dock position (Bottom, Left, Right), App pages scroll (Sideways, Up and down; the pages of Home and of the paged Library, not the Stream, whose stacks change from the Dock), Page dots (Beside the Dock, Off), Drag the dots to scroll (a switch, disabled while the dots are off) and Show apps behind the cards (a switch). Defaults: folded Bottom, Sideways, apps behind the cards off; unfolded Right, Up and down, apps behind the cards on; dots beside the Dock and quick scroll on in both. The Dock position and Show apps behind the cards are the same rows, with the same wording, as on the Dock page and the Stream cards page, and one stored value each. Stored in `LauncherSettings.layout` (`LayoutSettings`), reduced by `SelectPageScroll`, `SelectPageIndicator`, `SetQuickScroll` and `SelectPostureDockEdge`. See [dock-modes-plan.md](dock-modes-plan.md), "U1" |
| **Library** | Features | Library (a switch; **on by default** again, since the Library holds every app, and off in Simple mode; a stored choice is kept) |
| | View | Show as (Categories, A-Z, Grid, Pages), grid columns (used by Grid, disabled otherwise) |
| | Search | Search results as a card stack (the default), icons or a list. The stack is the Dock's search; the Library's own search field draws the stack's choice as icons |
| | Apps | Hidden apps (opens the sub-page), refresh installed apps |
| Hidden apps (under Library) | | The one list of hidden apps |
| **Stream** | Features | Stream (a switch; off in Simple mode); Show view button (a switch, on by default): the small button on the Stream that opens the page options a Dock tile's long-press opens (Show as, Move, Hide). Stored in `FeatureSettings.streamViewButton` |
| | Look | Opens the Stream cards sub-page |
| | Pairs (the Pairs section) | Show pairs (Automatic, Always show, Off): whether the Pairs stack (two apps that open side by side) and its Dock tile show. Automatic shows them where the system can split (SDK 37 or later, a window at least 600 dp wide, the launcher not in multi-window mode); Always show is for a device the rule gets wrong; Off hides them (the stored pairs are kept). Stored in `StreamSettings.showPairs`, reduced by `SelectPairsMode`; it replaces Settings > Dock > "App pairs", whose stored value (`dock.appPairs`) is read as it. The row is on the Stream page (not under Dock), in the Pairs section, beside the section's on and off and its order (the Pairs stack has no "Show as": it is a stack). See [dock-modes-plan.md](dock-modes-plan.md), "Pairs" |
| | each of Now, Notifications, Recents, Today, Feeds | On or off, order (move up, move down), Show as (Stack, List: it applies to every page of the section); a row opening each of the section's sub-pages where it has them (Today has two: Agenda and Tasks). The whole section is the search target of its setting |
| | Now: Recent players, Clear recent players | Under Now, whose description reads "Now playing and your recent players". **Recent players** is a switch (on by default; stored with the Stream layout as `NowOptions.recentPlayers`, per posture): off, Now shows only the active sessions and its "Nothing playing" card. **Clear recent players** forgets the list Now keeps (package, profile and last-played time; nothing else is stored). Both are searchable ("recent players", "now playing") and are one row each: Settings is the only place they live. See [dock-modes-plan.md](dock-modes-plan.md), "Media centre" |
| Stream cards (under Stream) | Preview | A sample card drawn with the same glass and tint a Stream card gets |
| | Colour | Card colour strength (0 to 40%, 22%), Glow on the top edge (off): the app colour as a strip along the top edge, drawn with the spotlight |
| | Folded, Unfolded | Show apps behind the cards, the same row as on the Layout page |
| | Open card | Where an open card sits (Centre, Above the Dock): folded only; the unfolded column always grows the card from the Dock, and the row says so. Where its actions sit (In the Dock, On the card): "In the Dock" only where the Dock holds a row, which is folded with the Dock along the bottom; with the folded Dock on a side, only "On the card" is offered, with the reason. "Grows out of the Dock" is not offered until it is built (a stored one reads as Above the Dock) |
| Notifications (under Stream) | | The notifications source (status, allow access, on or off); Hidden items |
| Hidden items (under Notifications) | | The rules that hide items, per layout |
| Agenda (under Stream; reached from Today) | | The device calendar source (status, allow access, on or off); include calendar feeds; Calendar feeds |
| Calendar feeds (under Agenda) | | The calendar feeds source (on or off), refresh, the feed list, add a feed |
| Tasks (under Stream) | | Todoist: the connection status (Not connected, Connected, or rejected), a password-style API token field with Save and a "Where is my token?" link, and Remove token. See [stream-tasks.md](stream-tasks.md) |
| Feeds (under Stream) | | The feeds source (on or off), background refresh, only on Wi-Fi, only while charging, refresh now, the feed list, add a feed |
| **Appearance** | Colour | Colour mode, accent, custom accent |
| | Glass | Reduced transparency, spotlight flow (reduced transparency is read by every glass surface) |
| | Shape and type | Corners (standard, compact, rounded), typography (standard, monospace; the old "System" choice was the same typeface as standard, and a stored one reads as standard) |
| **Gestures** | One finger, Two fingers, Pinch, Three fingers | The 14 swipe and pinch bindings on Home. Actions: None, Open search, Open Library, Open Stream, Open settings, Edit Home, Page overview, Full-screen Home, Next page, Previous page, Launch app, Launch shortcut. Reset |
| **Motion and haptics** | Motion | Animation performance, reduced motion, feedback strength (all choice rows) |
| | Dock and Stream | Opening speed (also the grid fade under the stack, the Home entrance and a swiped card), Dock bounce, Card stack coasting, Card snapping, Hold time to choose, Light ticks |
| **Privacy and access** | Access | Default home app, notification access, usage access, calendar, floating dock access, each with its state and a way to change it |
| | Privacy | Hide launcher content in screenshots and recents; how much the Stream shows (content level) for each source that can show private content |
| **Backup** | | Export, import. Includes calendar feeds and which Stream sections are on. Never content, never any token |
| **About** | | Version and build (tap the Version row seven times and a "Developer options" switch appears; default off, stored with the diagnostics settings). **After-update starts** stays here, always (see below). While the switch is on, a **Developer options** sub-page holds the diagnostics: **What apps publish** (a read-only list of what each app exposes to a launcher: shortcuts, notification shapes, media session, widgets, with a redacted Copy report; never notification text or contact names), **App pair experiment**, **Pair recipe** and **Drag tuning**. Search leaves those pages out while the switch is off; what they store (the pair recipe, the drag tuning) keeps working while they are hidden |

Removed rows: "Show the Stream as" (Pages or Stage; removed with the split in dock modes U2), "Open notifications" as a Gestures action, "Grows out of the Dock" as an open card position. Removed pages: Workspaces, Sources and its nine source detail pages, Saved lenses, Contextual, Cards appearance
(redirect), RSS feeds and Calendar feeds as top-level pages, Permissions and Privacy as two pages (merged), the
Developer section, the old Layout (now Home) and App drawer (now Library). The Layout page of the table above is a different page: the layout per posture, with its page named `POSTURE_LAYOUT`.

Where "Show as" lives twice it is one stored value. The Stream page writes `StreamLayoutAction`s through the same
`CachedStreamLayoutRepository` the Stream's header menus use (`A/stream/StreamLayoutStore.kt`); Library > Show as
dispatches `SelectLibraryView`, the action the Library's long-press dispatches. The Stream page and Library page are
per device class, so both show the device tabs.

## The settings audit (after dock modes U2 and the device fixes)

Every control on every page was walked from the control to where its value is consumed, in both postures and on both
Dock edges where that matters; the full inventory, what was wired, made truthful, removed until built or relabelled is
the "Settings audit" section of [dock-modes-plan.md](dock-modes-plan.md). The rules it leaves behind:

- A choice is never offered where it silently does nothing: it is wired, or the row says where it applies, or it is
  not offered (`cardActionsRowPlan`, the open card row's subtitle, "Grows out of the Dock").
- A setting reached from two pages is one stored value drawn by one composable, so the wording is identical and a change
  on either page is read off the other (`SettingsDockPositionRow`, `SettingsAppsBehindCardsRow`; `SettingsSharedRowsTest`).
- Every switch, option, slider, stepper and button on every page sits under a `SettingKey` anchor, or is one of the
  listed exemptions (the way back, the device tabs, rows that only open a page, the gestures reset, the read-only
  diagnostics page): `SettingsControlsHaveKeysTest`.
- A section that is one setting's place (each Stream section, the Tasks form, the label background pair, the apps behind
  the cards pair) is the anchor, so search scrolls to the whole of it.

## Naming

| Use | Instead of |
| --- | --- |
| Floating dock | "Overlay edge", "overlay dock" (the edge setting is just "Edge" on that page) |
| Hidden items | "Hidden items and rules", "Hide rules", exclusions |
| Hidden apps | (kept, as the Library's own list) |
| Glass | "Liquid glass", the GLASS preset, "Frost strength", "Edge refraction" (removed with the refraction shader: the flat glass has nothing to frost or refract; stored values still decode and are ignored) |
| Corners | "Card corners" |
| Icon size (Dock) | "Dock height" |
| Colour | "Color" (British spelling throughout Settings; `SettingsPagesTest` fails on the common American spellings in the Settings strings) |
| Calendar feeds | "Calendar feeds (ICS)", "Calendar feeds source" |
| Library | "App drawer" |
| Stream | "Cards", "Sources", "stages" |
| Stream cards | "Cards" (the Stream's sub-page) |
| Dock position | "Dock edge", "Overlay edge" (search still finds it by "edge") |
| Show apps behind the cards | "Peek through" |
| Live apps | "Dynamic side" |
| Swipes and pinches on Home | "Home swipe actions" |

## Wording

One name per action across every menu, button and setting. Sentence case: the first word is capitalised and the rest
are not, except the product's own nouns, which are always capitalised as the product writes them: **Home**, **Dock**,
**Library**, **Stream**, and **Floating dock** (the name of its page). Folders, pages and widgets are ordinary words.

| Action | Wording | Never |
| --- | --- | --- |
| Put an app on Home | Add to Home (once it is there: Added to Home) | "Add to home", "Pin to Home" |
| Put an app in the Dock | Add to Dock | "Pin to Dock", "Add to dock" |
| Take an item off the Dock | Remove from Dock | "Unpin from Dock", "Remove from dock" |
| Put an app in the Floating dock | Add to Floating dock, Remove from Floating dock | "Add to floating dock" |
| Take an item off Home | Remove from Home | "Remove from home" |
| The drop target that takes a lifted item off Home | Remove (its spoken name is Remove from Home) | "Delete", "Trash" |
| Edit page, several items | N selected, Select all on this page, Clear, Make a folder | "Select all", "Group" |
| Move an item between Home and the Dock | Move to Home, Move to Dock | "Move to home", "Move to dock" |
| Take an app out of a folder | Remove from folder | |
| Hide an app from the Library and Home | Hide app (undo from Hidden apps: Unhide) | a bare "Hide" |
| Hide a Stream item or section | Hide (the menu says what it hides) | |
| Open a page or sub-page | Open | |
| Put a widget in a widget stack | Add widget to stack | "Add to stack", "Stack with" |
| Change the widgets of a stack | Edit stack | "Edit widgets" |
| Take a widget out of a stack onto Home | Move out to Home | "Unstack", "Move out" |

Settings labels follow the same rule ("Show the Dock", "Dock position", "Floating dock", "Hidden apps"). A test
(`SettingsPagesTest`) holds the page and setting names; the menus' wording is held by `ShortcutContextMenuTest`,
`HomeDockContextMenuTest`, `HomeFolderContextMenuTest` and `PagedLibraryTest`.

## What the audit found, and what happened to it

The audit of 2026-11-12 listed about 30 `SettingsPage` entries, about 140 controls and about 11,400 lines of settings UI.

**Duplicates.** RSS feeds and refresh now exist only under Stream > Feeds. Content level per source exists only on
Privacy and access. Hidden apps exist once, under Library. Default home app exists only on Privacy and access. Dock
position, visibility and icon size exist only on Dock (the per-workspace dock dialog went with workspaces). Dock
notifications and Notification space were already replaced by Live apps under Dock > Dynamic side. Notification
hiding exists only as Stream > Notifications > Hidden items.

**Dead or no-op, removed.** Stored fields are removed where nothing reads them; every one of them still decodes (the
key is ignored) and is covered by a codec test over a blob written by the P8 build
(`LauncherSettingsJsonCodecTest`, `LauncherBackupDocumentTest`).

| Setting or field | Outcome |
| --- | --- |
| `AppDrawerSettings.afterLeavingLibrary`, `LibraryReturnTarget`, `returnsHome`, `SelectLibraryReturnTarget` | Deleted |
| `CardsSettings` (every field, including `unfoldedShowAllNotifications`, thread grouping and order, stage spine, page offset, pane arrangement and the adaptive stage appearance profiles), `AdaptiveStageAppearanceSettings` and its resolver, `LauncherSettings.cards`, the actions that set them, `LauncherShellState.appStageSnapshot` and `feedStageSnapshot`, and the adaptive stage haptic strength | Deleted. Nothing read them after P8: the Stream's Stack has fixed physics. Reduced transparency stored under the old `cards` motion block is still migrated |
| The stage code in `D/cards` (planners, templates, preferences) and `D/rss/FeedStage*` | Left in place; only the settings fields and shell-state projections that fed it were removed. Deleting it is a later cleanup |
| Expandable dock, Open the shelf with, Dock panel | Rows removed from Settings (they never rendered). The dock model's shelf fields stay: the shelf code that reads them is behind `DockShelfExpansion.enabled = false` and has device tests |
| Compact library pages | Row removed from Settings (legacy mode only). `GridSettings.compactLibraryPages` stays only so stored layouts decode; nothing reads it since the library view mode was retired (ADR 0003, Amendment 10) |
| "Following the active Cards template" text | Gone |
| Developer "Home from placed items" | Already gone (P8) |
| Cards appearance page entry | Already gone (P8) |
| Contextual > Contextual behaviour, `LauncherSettings.contextual`, `D/contextual/` | Deleted, with the automatic page selection that read it |
| `workspaceMenuEnabled`, `needsWorkspaceMenu` | Already gone (P8) |
| `Workspace.gestureBindings` | Stays: `DockPresentation` reads it for stored workspaces. Nothing edits it |
| `CUSTOM` theme preset | Already gone (P2) |
| `fullscreenHome` | Folded into Home > Full screen: the field and `HomeSystemBars.fullscreenHome` are gone. Saved settings with it (version 10 and earlier) hide both bars; the "Full-screen Home" gesture hides both |

**Other faults.** Back from Hidden apps goes to Library, and Back always goes to the parent. Search indexes every
page and every individual setting, including the ones on the sub-pages, and the alias "double tap", which matched no
gesture, is gone. The bespoke controls the audit listed are replaced by the shared rows: TextButton choice lists
(Dock position, width and alignment), plus and minus buttons (Home grid), hand-rolled radio rows (returning to Home,
content levels), raw FilterChips (Library, search results) and tap-to-cycle rows (reduced motion, animation
performance). Strings: every page title, summary, setting label and search keyword is a string resource, and so are the menus, the
Home page controls, the gesture names and the version rows. `HardCodedStringsGuardTest` stops a UI file from gaining a
literal; what is still literal is listed there (sample data, glyphs, a few helpers with pure tests), plus the Hidden items
page's internals, the text helpers of Privacy and Permissions, and the pages behind Developer options. The settings codec reads its
`version`. Backup now carries calendar feeds and the enabled Stream sections.

## Rules for the implementation (as followed)

1. **Shared rows only.** Pages use `SettingsSection`, `SettingsClickableRow`, `SettingsSwitchRow`, `SettingsListRow`,
   `SettingsChoiceRow`, `DiscreteSettingSlider` and the one added row, `SettingsStepperRow` (`A/SettingsRows.kt`).
   `SettingsChoiceRow` is a radio group (`selectableGroup`, each option `Role.RadioButton`) whose options wrap rather
   than scroll. The Hidden items page's own tabs and dialogs, the colour wheel and the feed and calendar-link forms
   are unchanged.
2. **Every "Show as" in Settings is the same choice as the in-place long-press.** See above.
3. **Stored data.** Removed enum values and keys decode without crashing: an unknown gesture action is None, an
   unknown theme preset is glass, removed keys are ignored. The codec writes `version` 11 and reads it: older blobs
   decode (version below 11 folds `fullscreenHome`), a newer blob decodes what this build knows with a changed or
   unknown section reading as its default, and before this build first overwrites a newer blob it keeps a copy under
   its own key (`isNewerThanThisBuild`).
4. **Strings.** See above.
5. **Back** goes to the parent page in the tree.
6. **Accessibility.** Rows are 48 dp or taller; a switch row speaks its state, a choice row speaks each option as a
   radio button with its selected state, a stepper speaks its value and its two buttons say what they change, a
   permission row says its state in words, and the page title is a heading. Nothing new animates, so reduced motion
   has nothing to change.

## Backup

`riffleLauncherBackup` stays version 1; older documents import. New in this slice: a `calendarFeeds` section (the feed
list: id, name, link, on or off) written only when there is at least one feed, restored after the home and settings
(`restoreCalendarFeeds`; nothing is fetched on restore). The Stream layout section, which already carried each
section's on or off, order, view and options, is unchanged. Content is never in a backup: no events, notifications,
articles or cached anything, and there is no token to include yet.

Decision to confirm: a calendar feed link can carry a private key, which is why the feed store was kept out of every
backup until now. The in-app export is a file the user writes by hand, like the RSS feed list that is already in it,
so the links are included and the Backup page and the feed page say to keep the file private. Android's own backup
(`data_extraction_rules.xml`, `backup_rules.xml`) still excludes the store.

## Differences from the original design

- **No "Stack feel" page.** The design kept "the card-stack geometry and fan controls that survive the material
  slice". None survive: after P8 nothing reads the stored card-stack settings, and the Stream's Stack uses fixed
  physics (`StreamStackPolicy`). The page would have been empty, so it, its controls and the stored fields are gone.
  A page can return when the Stack gets a control that does something.
- **Notifications sub-page.** "Group by message or thread" and "order" are not built: the Stream draws one card per
  notification with its thread inside, and the stored settings of that name were read by nothing. The page holds the
  notifications source and Hidden items.
- **Agenda sub-page.** "Calendars shown" is the device calendar source (status, allow access, on or off) plus the
  existing Agenda option "include calendar feeds"; there is no per-calendar list.
- **Home > Behaviour.** "New apps go to Home or Library only" was built later as Home > "Add new apps to Home" (ADR 0003,
  Amendment 8).
- **Dock > Dynamic side.** "Show now playing" is not built: now playing always shows while media is active. Only Live
  apps is a setting.
- **Library > View.** "Personal and work split" is the profile filter in the Library itself, which is not stored, so it
  is not a setting.
- **Set up.** The row keeps the wording "Set up Riffle again" (it re-runs all four steps, not only Home).
- **Privacy and access > Privacy.** The content-level rows are whatever the sources report as private (Notifications,
  Media, Calendar, Calendar feeds); Tasks (Todoist) has a row too. Usage access is read from the Recents
  source and opens Android's usage access settings.
- **Gestures.** The Gestures page keeps the four groups of the old panel (one finger, two, pinch, three), each row a
  menu, plus a Reset.
- **Backup of calendar feeds.** See Backup above.

## Planned and not built

Settings that earlier plans or this document's first design named and that do not exist; none is offered, and none is
silently stored.

| Setting | Why it is not there |
| --- | --- |
| Show now playing (Dock > Dynamic side) | Now playing always shows while media is active |
| Calendars shown, a per-calendar list (Agenda) | The device calendar is one source with an on or off |
| Group notifications by message or thread, notification order | The Stream draws one card per notification with its thread inside |
| Stack feel page (card-stack geometry and fan controls) | The Stream's Stack has fixed physics |
| Personal and work split (Library > View) | It is the profile filter in the Library itself, not stored |
| Behind the cards: Home blurred | Glass over the wallpaper cannot blur it; see `dock-modes-plan.md` |
| Grows out of the Dock (open card position) | Not built; a stored one reads as Above the Dock |

## Visible ways in (ADR 0003, Amendment 12)

- **Settings without a gesture.** The Library's header has a gear ("Riffle settings"), the Library's "Show as" menu ends
  with the same item (the paged Library, which has no header, has it in that menu and as a custom action), and
  "Riffle settings" is a search result that leads the settings that match as well as it does: it opens the main page of
  Settings. The Home long-press menu's "Settings" and the two-finger swipe stay.
- **Hints.** One-time tips for the Dock's gestures; Show hints again (Home > Set up) resets them. They are stored with the
  onboarding state, not in Settings or the backup.

## Home > Fill Home

Two buttons and one switch (`SettingsHomeFillSection`; the rules are `HomeArrangement` in `:core:domain`).

- **Add all apps to Home.** Places every visible (not hidden), launchable app that Home does not show yet, after the last
  occupied cell in the Library's A to Z order, adding pages as needed. "Shows" counts a page, a folder on Home and the
  Dock; work and private profile apps follow whatever the Library shows. Nothing already placed moves. Afterwards the
  Settings snackbar says "Added 23 apps" with Undo, which puts the previous layout back exactly.
- **Arrange Home A to Z.** Re-lays out the apps and folders of Home's pages alphabetically (apps and folders sorted
  together by label; a folder sorts by its name; the Library's order), page after page. It asks first ("Arrange Home A to
  Z? Your current arrangement will be replaced." with Arrange and Cancel), then says "Home arranged" with Undo. Widgets
  never move: they keep their page and cells, icons flow around them, and a page with a widget is never dropped. The Dock
  is not touched, trailing empty pages are removed (one page stays), and running it twice changes nothing.
- **Compact: fill each page completely** (`HomeBehaviourSettings.compactArrange`, default on). On, each page is filled
  before the next starts. Off ("leave room"), each page is filled to `floor(0.75 * cells)` cells (at least 1; the cells of
  a widget count) before the next starts. It decides what Arrange does and what the first-run seed does.
- Also stored there: `emptyHomeHintDismissed`, for the empty-Home hint (see `places-views-plan.md`, "First run").

## About > After-update starts (always available)

What the first start after each update looked like, for the last ten updates, with a Copy button: time, old and new
version code, the intent the activity was started with (action, categories, flags, component), whether it was the task
root, whether Riffle held the Home role, which component resolved `ACTION_MAIN` + `CATEGORY_HOME`, the time from process
start to the first frame, and the time of each new intent that carried `CATEGORY_HOME` in the first five minutes. It is
device-local (`riffle_after_update`, outside the launcher settings, the backup document and Auto Backup). See
`places-views-plan.md`, "After-update starts".

## About > Developer options > Drag tuning

Sliders for the numbers a Home drag reads at runtime, with Reset and the current values as text to copy: push ring radius
(0.5 to 1.2 cell, default 0.75), dead-zone radius (0.15 to 0.45, default 0.30), folder hold (100 to 600 ms, default 250),
edge hold (200 to 1000 ms, default 500), page repeat (300 to 1500 ms, default 700) and the Library drag distance (6 to
40 dp, default 12: how far a held Library app moves before its menu becomes a Home drag; data stored before it existed
reads as the default). Stored in `LauncherSettings.home`
(`HomeBehaviourSettings.dragTuning`, held to its ranges when decoded and when saved).
