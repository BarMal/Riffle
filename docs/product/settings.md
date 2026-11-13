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

The main page is a search field and exactly ten rows, in this order. Typing replaces the rows with matching pages and
settings (label, path or keyword); a result opens its page scrolled to that setting.

| Page | Section | Settings |
| --- | --- | --- |
| **Home** | Grid | Columns, rows (a stepper), horizontal margin, vertical margin (per device class) |
| | Icons and labels | Icon size, show labels, label background, label text size, label width, label sizing, label lines |
| | Behaviour | Returning to Home (restore, first page, start page) |
| | Wallpaper | Wallpaper (system, solid), wallpaper motion, change wallpaper |
| | Full screen | Hide status bar, hide navigation bar |
| | Set up | Set up Riffle again |
| **Dock** | Dock | Show dock, position, icon size, pinned icons, width, alignment, corner radius, background strength, spacing to the grid (per device class) |
| | Dynamic side | Live apps |
| | Floating dock | Opens the sub-page |
| Floating dock (under Dock) | | Floating dock on or off, apps, edge, handle thickness, size, position, opacity, expanded icon size, orientation, labels |
| **Library** | View | Show as (Categories, A-Z, Grid), grid columns (used by Grid) |
| | Search | Search results as icons or list |
| | Apps | Hidden apps (opens the sub-page), refresh installed apps |
| Hidden apps (under Library) | | The one list of hidden apps |
| **Stream** | each of Now, Notifications, Recents, Agenda, Tasks, Feeds | On or off, order (move up, move down), Show as (Stack, List: it applies to every page of the section); a row opening the section's sub-page where it has one |
| | the whole Stream (unfolded, tablet and desktop postures only) | **Show the Stream as**: Pages (swipe sideways, two side by side on a wide screen) or Stage (a rail of every page beside the one in front). Stored per posture in the Stream layout (`wideView`, default Pages); the same value as "Show as" in the Stream's own menu (long-press the spine) |
| Notifications (under Stream) | | The notifications source (status, allow access, on or off); Hidden items |
| Hidden items (under Notifications) | | The rules that hide items, per layout |
| Agenda (under Stream) | | The device calendar source (status, allow access, on or off); include calendar feeds; Calendar feeds |
| Calendar feeds (under Agenda) | | The calendar feeds source (on or off), refresh, the feed list, add a feed |
| Tasks (under Stream) | | Todoist: the connection status (Not connected, Connected, or rejected), a password-style API token field with Save and a "Where is my token?" link, and Remove token. See [stream-tasks.md](stream-tasks.md) |
| Feeds (under Stream) | | The feeds source (on or off), background refresh, only on Wi-Fi, only while charging, refresh now, the feed list, add a feed |
| **Appearance** | Colour | Colour mode, accent, custom accent |
| | Glass | Reduced transparency, spotlight flow |
| | Shape and type | Corners (standard, compact, rounded), typography (standard, system, monospace) |
| **Gestures** | One finger, Two fingers, Pinch, Three fingers | The 14 swipe and pinch bindings on Home. Actions: None, Open notifications, Open search, Open Library, Open Stream, Open settings, Edit Home, Page overview, Full-screen Home, Next page, Previous page, Launch app, Launch shortcut. Reset |
| **Motion and haptics** | Motion | Animation performance, reduced motion, feedback strength (all choice rows) |
| **Privacy and access** | Access | Default home app, notification access, usage access, calendar, floating dock access, each with its state and a way to change it |
| | Privacy | Hide launcher content in screenshots and recents; how much the Stream shows (content level) for each source that can show private content |
| **Backup** | | Export, import. Includes calendar feeds and which Stream sections are on. Never content, never any token |
| **About** | | Version and build |

Removed pages: Workspaces, Sources and its nine source detail pages, Saved lenses, Contextual, Cards appearance
(redirect), RSS feeds and Calendar feeds as top-level pages, Permissions and Privacy as two pages (merged), the
Developer section, Layout (now Home) and App drawer (now Library).

Where "Show as" lives twice it is one stored value. The Stream page writes `StreamLayoutAction`s through the same
`CachedStreamLayoutRepository` the Stream's header menus use (`A/stream/StreamLayoutStore.kt`); Library > Show as
dispatches `SelectLibraryView`, the action the Library's long-press dispatches. The Stream page and Library page are
per device class, so both show the device tabs.

## Naming

| Use | Instead of |
| --- | --- |
| Floating dock | "Overlay edge", "overlay dock" (the edge setting is just "Edge" on that page) |
| Hidden items | "Hidden items and rules", "Hide rules", exclusions |
| Hidden apps | (kept, as the Library's own list) |
| Glass | "Liquid glass", the GLASS preset |
| Corners | "Card corners" |
| Icon size (Dock) | "Dock height" |
| Colour | "Color" (British spelling throughout Settings; `SettingsPagesTest` fails on the common American spellings in the Settings strings) |
| Calendar feeds | "Calendar feeds (ICS)", "Calendar feeds source" |
| Library | "App drawer" |
| Stream | "Cards", "Sources", "stages" |
| Swipes and pinches on Home | "Home swipe actions" |

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
| Compact library pages | Row removed from Settings (legacy mode only). `GridSettings.compactLibraryPages` stays: `HomeScreenLibraryCompaction` reads it |
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
performance). Strings: every page title, summary, setting label and search keyword is a string resource, as is new
text; untouched hints and the Hidden items page's internals are still literals. The settings codec reads its
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
- **Home > Behaviour.** "New apps go to Home or Library only" is not built: no global setting exists (the per-workspace
  one went with workspaces), so it needs a new setting and a placement change of its own.
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
