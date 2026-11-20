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
| | Home layouts | A row opening the Home layouts sub-page, with how many are saved on the tab's device class |
| | Grid | Columns, rows (a stepper), horizontal margin, vertical margin (per device class) |
| | Icons and labels | Icon size, show labels, label background (with its strength while it is on), label text size, label width, label sizing, label lines |
| | Behaviour | Returning to Home (restore, first page, start page) |
| | Wallpaper | Wallpaper (system, solid), wallpaper motion, change wallpaper |
| | Full screen | Hide status bar, hide navigation bar |
| | Set up | Set up Riffle again; Show hints again (brings back the Dock's one-time tips; the row shows where the launcher runs the set-up) |
| Home layouts (under Home) | Save, Saved layouts | Per device class (the tabs): **Save current Home** (a name, default "Home layout N", twelve at most) and the saved layouts, each with its page and item count and a menu (Activate, Rename, Replace with current Home, Delete). Off in Simple mode. See "Home > Home layouts" below |
| **Dock** | Dock | Show the Dock, Dock position, icon size, pinned icons, Dock width, Dock alignment (along the top or bottom edge: left, middle, right; down a side: top, middle, bottom), Dock corner radius, Dock background strength, spacing to the grid (per device class) |
| | Live apps | Show live apps (a switch), Number of live apps |
| | Stream handle | Show Stream handle (a switch, on by default; off in Simple mode and without the Stream): the small chevron beside the Dock on Home that opens the Stream with a tap (touch and hold: Open Library, Hide this handle). A feature switch like Show live apps, stored in `FeatureSettings.dockStreamHandle` |
| | Floating dock | Opens the sub-page |
| Floating dock (under Dock) | | Floating dock on or off, apps, edge, handle thickness, size, position, opacity, expanded icon size, orientation, labels |
| **Layout** | Folded, Unfolded | Per posture (a tablet and a desktop window use Unfolded): Dock position (Bottom, Left, Right), App pages scroll (Sideways, Up and down; the pages of Home and of the paged Library, not the Stream, whose stacks change from the Dock), Page dots (Beside the Dock, Off), Drag the dots to scroll (a switch, disabled while the dots are off) and Show apps behind the cards (a switch). Defaults: folded Bottom, Sideways, apps behind the cards off; unfolded Right, Up and down, apps behind the cards on; dots beside the Dock and quick scroll on in both. The Dock position and Show apps behind the cards are the same rows, with the same wording, as on the Dock page and the Stream cards page, and one stored value each. Stored in `LauncherSettings.layout` (`LayoutSettings`), reduced by `SelectPageScroll`, `SelectPageIndicator`, `SetQuickScroll` and `SelectPostureDockEdge`. See [dock-modes-plan.md](dock-modes-plan.md), "U1" |
| **Library** | Features | Library (a switch; **on by default** again, since the Library holds every app, and off in Simple mode; a stored choice is kept) |
| | View | Show as (Categories, A-Z, Grid, Pages), grid columns (used by Grid, disabled otherwise) |
| | Search | Search results as a card stack (the default), icons or a list. The stack is the Dock's search; the Library's own search field draws the stack's choice as icons |
| | Search sources | A switch per source (see "Library > Search" below): Riffle settings (on), Calculator (on), System settings (on), Contacts (off), Files (off), Web search (on); the order of the groups (Move up, Move down per group) and Results per group (3, 5 or 8; 5). Apps and app shortcuts are always searched and have no switch |
| | Apps | Hidden apps (opens the sub-page), refresh installed apps |
| Hidden apps (under Library) | | The one list of hidden apps |
| **Stream** | Features | Stream (a switch; off in Simple mode); Show view button (a switch, on by default): the small button on the Stream that opens the page options a Dock tile's long-press opens (Show as, Move, Hide). Stored in `FeatureSettings.streamViewButton` |
| | Look | Opens the Stream cards sub-page |
| | Pairs (the Pairs section) | Show pairs (Automatic, Always show, Off): whether the Pairs stack (two apps that open side by side) and its Dock tile show. Automatic shows them where the system can split (SDK 37 or later, a window at least 600 dp wide, the launcher not in multi-window mode); Always show is for a device the rule gets wrong; Off hides them (the stored pairs are kept). Stored in `StreamSettings.showPairs`, reduced by `SelectPairsMode`; it replaces Settings > Dock > "App pairs", whose stored value (`dock.appPairs`) is read as it. The row is on the Stream page (not under Dock), in the Pairs section, beside the section's on and off and its order (the Pairs stack has no "Show as": it is a stack). See [dock-modes-plan.md](dock-modes-plan.md), "Pairs" |
| | each of Now, Notifications, Recents, Today, Feeds | On or off, order (move up, move down), Show as (Stack, List: it applies to every page of the section); a row opening each of the section's sub-pages where it has them (Today has two: Agenda and Tasks). The whole section is the search target of its setting |
| | Now: Show up next, Recent players, Clear recent players | Under Now, whose description reads "Now playing and your recent players". **Show up next** is a switch (on by default; stored with the Stream layout as `NowOptions.showUpNext`, per posture; a layout stored before it existed reads as on): each active player's queue is cards behind the player's card, at most ten, after the track playing; off leaves one card per player. **Recent players** is a switch (on by default; stored as `NowOptions.recentPlayers`, per posture): off, Now shows only the active sessions and its "Nothing playing" card. **Clear recent players** forgets the list Now keeps (package, profile and last-played time; nothing else is stored). All three are searchable ("show up next", "queue", "recent players", "now playing") and are one row each: Settings is the only place they live. See [dock-modes-plan.md](dock-modes-plan.md), "Media centre" and "One stack per player" |
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
| | Icons | A row opening the Icons sub-page, saying the shape, "themed" when themed icons are on and how many apps have a changed icon |
| Icons (under Appearance) | Preview | A strip of six of the user's apps drawn by the launcher's own icon loader, so it shows exactly the style every surface shows |
| | Shape | **Icon shape** (System default, Circle, Squircle, Rounded square, Square, Teardrop) and **Legacy icons** (Fit on a plate, Leave as is; disabled, and saying why, while the shape is System default) |
| | Themed icons | **Themed icons** (a switch, off by default; disabled with "Needs Android 13 or later" on API 32 and below) and **Theme all icons** (off by default; disabled until Themed icons is on) |
| | Icon pack | None and the installed icon packs, one radio row each with the pack's icon. Says so when none is installed, and when the chosen pack is not (icons use the default style until it is) |
| | Changes | **Reset icon changes**: clears every icon chosen for a single app, after a confirmation, with "Icon changes reset" and Undo (names are kept). Disabled, and saying so, when nothing was changed. See "Appearance > Icons" below |
| **Gestures** | One finger, Two fingers, Pinch, Three fingers | The 14 swipe and pinch bindings on Home. Actions: None, Open search, Open Library, Open Stream, Open settings, Edit Home, Page overview, Full-screen Home, Next page, Previous page, Launch app, Launch shortcut. Reset |
| **Motion and haptics** | Motion | Animation performance, reduced motion, feedback strength (all choice rows) |
| | Dock and Stream | Opening speed (also the grid fade under the stack, the Home entrance and a swiped card), Dock bounce, Card stack coasting, Card snapping, Hold time to choose, Light ticks |
| **Privacy and access** | Access | Default home app, notification access, usage access, calendar, floating dock access, each with its state and a way to change it |
| | Privacy | Hide launcher content in screenshots and recents; how much the Stream shows (content level) for each source that can show private content |
| **Backup** | | Export, import, and while a copy is kept, **Undo last import** (with the date) and **Dismiss**. Includes calendar feeds, which Stream sections are on and the saved Home layouts. Never content, never any token. See "Backup > Undo last import" below |
| **About** | | Version and build (tap the Version row seven times and a "Developer options" switch appears; default off, stored with the diagnostics settings). **Reset settings** (after a confirmation that lists what is kept; "Settings reset" with Undo) sits here too. **After-update starts** stays here, always (see below). While the switch is on, a **Developer options** sub-page holds the diagnostics: **What apps publish** (a read-only list of what each app exposes to a launcher: shortcuts, notification shapes, media session, widgets, with a redacted Copy report; never notification text or contact names), **App pair experiment**, **Pair recipe** and **Drag tuning**. Search leaves those pages out while the switch is off; what they store (the pair recipe, the drag tuning) keeps working while they are hidden |

Removed rows: "Show the Stream as" (Pages or Stage; removed with the split in dock modes U2), "Open notifications" as a Gestures action, "Grows out of the Dock" as an open card position. Removed pages: Workspaces, Sources and its nine source detail pages, Saved lenses, Contextual, Cards appearance
(redirect), RSS feeds and Calendar feeds as top-level pages, Permissions and Privacy as two pages (merged), the
Developer section, the old Layout (now Home) and App drawer (now Library). The Layout page of the table above is a different page: the layout per posture, with its page named `POSTURE_LAYOUT`.

Where "Show as" lives twice it is one stored value. The Stream page writes `StreamLayoutAction`s through the same
`CachedStreamLayoutRepository` the Stream's header menus use (`A/stream/StreamLayoutStore.kt`); Library > Show as
dispatches `SelectLibraryView`, the action the Library's long-press dispatches. The Stream page and Library page are
per device class, so both show the device tabs.

## Library > Search

Search finds apps, app shortcuts and Riffle's own settings, as it always did, and now optional **wider sources** behind one
small contract (`SearchSource` in `D/search/wider/`: an id, a suspend query that returns scored, typed results, a cap, a
minimum query length and a debounce). Each source is independently switchable in Settings > Library > Search (`SearchSettings`:
`launcherSettings`, `calculator`, `systemSettings`, `contacts`, `files`, `webHandoff`, `groupOrder`, `resultsPerGroup`; one
`SettingKey` each, all searchable, all in a backup, and a settings blob stored before them reads the defaults in the table
above). The same code draws them in the Dock's search (card stack, icons or list), the Library's search and the search
stack: `rememberWiderSearch` and `WiderSearchPanel` in `A/search/wider/`.

- **Apps stay instant.** App, shortcut and Riffle settings results are computed synchronously as before; the wider sources
  only add. Each runs in its own coroutine after its own debounce (calculator and system settings: none; contacts 150 ms,
  files 200 ms) and is cancelled when the query changes, so an old query's answer is never drawn for a newer one
  (`WiderSearchEngine`, tested with a virtual clock). A source that throws gives no rows and does not stop the others.
- **Order.** The calculator's answer first, then the apps (with Riffle's own settings ranked among them, so a Riffle setting
  always stands above a system one), then the groups in the person's order (System settings, Contacts, Files by default),
  each cut at Results per group with "Show N more" while it is cut. Group headers show only when more than one thing has
  results. Growing up from the Dock's field, the groups stand above the apps and the answer under them, nearest the field.
- **Calculator** (no permission). `+ - × * / ÷ ^ %`, parentheses, implicit multiplication by parentheses ("2(3+4)"), decimals
  in the locale's separator (the other of `.` and `,` is a grouping mark only in groups of three). Decimal arithmetic
  (0.1 + 0.2 is 0.3), whole numbers exact, others to ten places, never above 10^30. `%` after `+` or `-` is a share of the
  left side ("200 + 10%" is 220). Division by zero, overflow, a plain number, a phone number, a date, a version, an
  incomplete expression or any letter show nothing. Units: "12 km in miles", "70 f to c", "3.5 lb in kg" (length, mass,
  temperature, volume (US), speed, data sizes (1000 and 1024), time); fixed factors, no currencies, no network. Tap copies
  the answer ("Copied", system message on Android 13 and later); the long-press and the trailing "Copy sum" or "Copy both"
  button copy it with what was asked ("12 × 3 = 36"). Enter copies the answer.
- **System settings** (no permission). About twenty-five `Settings.ACTION_*` screens (Wi-Fi, Bluetooth, Display, Sound,
  Battery, Storage, Apps, Notifications, Location, Security, Accessibility, Date and time, Language, Developer options,
  About phone, Default apps, Data usage, NFC, Hotspot, Airplane mode and more) with labels and keywords in resources; only
  those that resolve on the device are offered, resolved once off the main thread and kept (`SystemSettingsCatalog`; each
  action is in the manifest's `<queries>`).
- **Contacts** (`READ_CONTACTS`, optional, off by default). The permission is requested only from the inline "Allow" row
  search draws while the source is on and the permission is missing ("Search contacts: allow access to find people by
  name."), never at launch and never from the switch. A contact matches when the name starts with the query or every query
  word starts a word of the name, ignoring case and accents; the row shows a bounded photo thumbnail when there is one
  (the Stream's `ItemImage` and `ExpressionImageLoader`, decoded to the size drawn, in memory only). Tap opens the contact;
  Call and Message appear only for a contact with a number and open the dialler (`ACTION_DIAL`) and the messaging app
  (`ACTION_SENDTO`, `smsto:`), so no call or SMS permission is involved and nothing is placed or sent. The provider is read
  off the main thread with a row limit of 40 plus one bounded query for the numbers. **Personal profile only in this batch**:
  work-profile and directory contacts are not read. Nothing from contacts (the query, a name, a number, a photo) is stored,
  logged or in a backup; `WiderSearchPrivacyTest` holds it.
- **Files** (optional, off by default). Photos, videos and audio by name through `MediaStore`, and downloads through
  `MediaStore.Downloads`, newest first, opened with `ACTION_VIEW` and the content URI with a read grant. What the launcher
  can see **without** a permission is nothing: since Android 10 `MediaStore` shows an app only the files it made itself.
  What it can see with permission is what that permission covers: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` and
  `READ_MEDIA_AUDIO` on Android 13 and later (asked together from one "Allow": "Search files: allow access to find photos,
  videos and audio by name."), `READ_EXTERNAL_STORAGE` (declared up to Android 12L) before. Documents (PDF, text, office
  files) are not reachable without broad storage access, which the launcher does not hold and will not ask for; the always
  last row **Open Files with this search** (the system Files app at Downloads, with the query passed along) is the way in.
  Whether that app starts a search from the query depends on the app, and is only confirmable on a device.
- **Manifest.** `READ_CONTACTS`, `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` and `READ_EXTERNAL_STORAGE`
  (`maxSdkVersion` 32) are declared as ordinary runtime permissions and no `uses-feature` is added, so none is a required
  feature and none is requested before the person taps "Allow". Each can be taken back in system settings; search reads
  its access again when the launcher is back in front.
- **Accessibility.** Rows are at least 56 dp and the buttons beside them 48 dp; the secondary actions (Copy sum, Call,
  Message, Show more) are buttons, not long-presses; a row says its group and place ("System settings, 2 of 5"), a group
  header is a heading, and the Allow row is a button with its sentence.

## Appearance > Icons

One pipeline (`AppIconPipeline`, `A/icons/`) draws every app icon: Home, the Dock, folders and their previews, the Library,
search, Stream and pair cards, the widget stack editor, the Floating dock and the Settings lists. A surface never decodes
an icon itself and never clips one to its own shape when the icon already comes cut (`AppIconLoader.drawsOwnShape`).
Stored in `LauncherSettings.icons` (`IconStyleSettings`, the `icons` section of the settings blob and so of a backup); a
blob without it reads as the defaults.

- **Defaults draw the platform's icons exactly as before**: System default, themed icons off, no icon pack, no changes.
  `IconStyleSettings.isDefault`; the existing goldens do not change at the defaults.
- **Order of precedence for one app**: the icon chosen for it > the icon pack > the themed icon > the shape
  (`IconPrecedence`, pure). The first recipe that works is drawn; a drawable missing from the pack falls through to the
  next. A choice whose pack is not installed is skipped and **kept**, so installing the pack again restores it.
- **Shapes** (`IconShapeGeometry`, pure, `:core:domain`): mask outlines in a 100 by 100 box (Circle, the n = 4 superellipse
  for Squircle, Rounded square at 26% radius, Square at 4%, Teardrop with one sharp corner) and the scales that keep an
  icon from being cropped. An adaptive icon's layers are drawn 108/72 times the icon, centred, and the mask cuts the icon
  out of the middle. A legacy icon (no adaptive layers) goes on a neutral plate (the same in light and dark) scaled to the
  shape's inscribed square, at most 86% of the plate; "Leave as is" draws it unmasked. System default is the platform's
  own mask, untouched.
- **Themed icons** (API 33 and later): where an app ships a monochrome layer it is drawn in the theme's on-primary-container
  colour on its primary container, so it follows the accent, Material You and light and dark. **Theme all icons** gives an
  app without a monochrome layer a generated one: the icon is desaturated (Rec. 709 luma) and thresholded at 50% with a soft
  edge, the smaller of the light and dark classes of its opaque pixels is the symbol, and an icon with one class keeps its
  silhouette (`MonochromeSynthesis`, pure). Both are off by default.
- **Icon packs**: found by the launcher-theme intent filters (`org.adw.launcher.THEMES`,
  `org.adw.launcher.icons.ACTION_PICK_ICON`, `com.novalauncher.THEME`, `com.gau.go.launcherex.theme`,
  `com.anddoes.launcher.THEME`, all in `<queries>`). `appfilter.xml` (res/xml or assets) maps `ComponentInfo{package/activity}`
  to a drawable name; `iconback`, `iconmask`, `iconupon` and `scale` dress apps the pack has no drawable for. The parser is
  defensive (`IconPackAppFilter`: malformed XML yields what could be read, 8 million characters and 30,000 entries at most,
  names of letters, digits, dots and underscores only). A pack is read once per package version, off the main thread, and
  held in memory only (no disk cache, never in a backup); a removed or updated pack falls back at once and is read again on
  the package change broadcast the launcher already receives. Calendar and dynamic-clock extras are not supported.
- **Change icon** (the long-press menu of Home, the Dock, folders, the Library and search): one sheet with a name field
  (Rename, Reset name), the icons of the current or another installed pack as a searchable grid (a page of 90 at a time,
  each drawable decoded only when its cell is composed) and Reset to default. Stored per app identity (package, activity
  and profile) as the pack's package and the drawable's name, never a bitmap. A custom name is applied to what is drawn
  (`withAppLabelOverrides`: installed apps, Home, folders, the Dock) and not to the stored layout; a shortcut of an app
  keeps its own label. Reset settings keeps these per-app changes; Reset icon changes clears the icons.
- **Cache**: the loader's bitmap cache is bounded by bytes (24 MiB, the old ceiling of 48 icons of 320 px) and keyed by app,
  size and style version, so a change of style makes the old entries unreachable and they age out; an icon on screen stays
  until its replacement has loaded. The version holds every global choice, the chosen pack's version, the theme colours
  (only while themed) and, per app, its own choice. Resolution is blocking and runs off the main thread; a main-thread call
  is reported (`MainThreadGuard`) and the tests assert there is none.

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
| Icon shape, Themed icons, Icon pack, Change icon | "Icon style", "Theme icons", "Icon theme", "Edit icon" |

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
| Bind a widget that could not be bound again, in place | Set up widget (its placeholder says "Not set up. Tap to set up.") | "Re-add", "Retry" |

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

## Home > Home layouts

Settings > Home > Home layouts (the page `HOME_LAYOUTS`, under Home; ADR 0003, Amendment 17). Shows the device tabs; a
saved layout belongs to the device class it was saved on.

- **What is saved:** the pages of Home (grids, apps, folders, widgets and widget stacks). Not the Dock, settings or Stream.
  It is a snapshot: "Changes to Home don't change a saved layout." Widgets are saved as unbound descriptions (provider,
  span constraints, stack order, glance kind); a layout never holds a widget host id.
- **Save current Home:** asks for a name (default "Home layout N", the next free number; at most 40 characters). Twelve
  per device class; the row says so when full. The one automatic layout does not count.
- **Row menu** (a button on the row, nothing needs a long press): **Activate**, **Rename**, **Replace with current Home**
  (confirmed), **Delete** (confirmed, with Undo). Renaming the automatic copy keeps it as a saved layout (needs room).
- **Activate** asks ("Home will be replaced... your current Home is kept as a saved layout named "Before <name>""),
  keeps the current Home as that **automatic** layout (one per device class, replaced by the next activation), replaces
  Home and offers Undo, which puts the previous Home back exactly. The rule that makes a saved layout valid today
  (`SavedHomeReconciler`, `:core:domain`): apps no longer installed, hidden apps and apps now in the Dock are dropped
  (a folder keeps what is left, as after an uninstall, and an emptied folder is removed); apps installed since are not
  added (Add all apps does that; Add new apps to Home applies later as always); items are fitted to today's grid, never
  overlap, and the misfits go to the first free cell, else a new page; pages the rule emptied and trailing empty pages
  go, one page stays (a pinned page stays too). Every item gets a new id.
- **Widgets.** A live widget with the same provider at the same page, cell and span keeps its host id. The others are
  bound again, with a new host id, only when Android needs no permission prompt and the provider needs no
  configuration screen; anything else is a placeholder that keeps the saved provider and says "Not set up. Tap to set
  up." (see "Setting up a placeholder" below). The ids of widgets that leave Home are kept until the Undo window (the
  snackbar) closes.
- **Setting up a placeholder.** A widget that could not be bound (after activating a layout, a late Undo of an import,
  a backup restore) is a placeholder that keeps its provider (`WidgetItem.provider`, `StackedWidget.provider`; the pool
  already stored it, and the layout JSON now writes an optional `provider`, read as none from older data). **Tap it**, or
  **Set up widget** in its menu, or the accessibility action "Set up <name>", or the row's button in Edit stack: it runs
  the widget picker's own bind, permission prompt and configuration transaction (`WidgetAddTarget.HOME_PLACEHOLDER`, the
  same pending-add record, persisted with the item and member so it survives the process dying during the system's
  screen) and, on success, the placeholder becomes the live widget in place: same cell, span, stack membership, position
  and shown widget (`WidgetStackEngine.bindPlaceholder`). Cancelled or failed: it stays a placeholder with "Widget setup
  was cancelled. Try again." A placeholder with no known provider keeps "Not set up. Add it again from Widgets." and
  offers only Remove; one whose app is not installed says "Its app isn't installed. You can remove it." and offers only
  Remove, with "<name> can't be set up: its app isn't installed" when asked.
- **Storage:** its own DataStore file `riffle_saved_home_layouts`, one versioned JSON document (version 1; each layout's
  pages and items are a pool value, so the pool's safe decoding applies; malformed entries are skipped; a newer version
  is read as far as it is understood and kept once). Included in backup export and import (`savedHomeLayouts`), and in
  Android's backup the same way the Home layout is.

## Backup > Undo last import

Before an import replaces anything, the current set-up is written, durably, as the export document to
`files/before_import_backup.json` (one copy; excluded from Android's backup and device transfer); if that cannot be
written the import does not happen. After an import, Settings shows "Backup imported" with Undo, and the Backup page
keeps **Undo last import** (with the date) and **Dismiss** until the next import or Dismiss. Undo re-imports the copy
through the same validated path and deletes it. Widget host ids Home let go of are kept only while the snackbar is
shown: Undo from the row later brings the widgets back as placeholders. A failed import says why: not a Riffle backup,
made by a newer version, or damaged; a file that cannot be read keeps the generic message.

## About > Reset settings

`SettingsReset` (`:core:domain`): every `LauncherSettings` value returns to its default, including Developer options
(hidden again), Simple mode and the hints seen. **The Stream's sections go back too**: they live in their own store
(`CachedStreamLayoutRepository`, apart from the settings), so the Reset row resets it in the same step: every posture's
layout returns to `StreamLayout.DEFAULT` (which sections are on, their order, how each is shown, and the per-section
options such as Now > Show up next). Kept: the feed list and the Floating dock's apps (inside `LauncherSettings`), and
everything else stored elsewhere: Home, the Dock, saved Home layouts, hidden apps and exclusions, calendar feeds and the
Tasks token. The confirmation lists these exactly; "Settings reset" offers Undo, which puts the previous settings back
unchanged and, with them, the Stream's layouts exactly as they were (`restoreAll` of what `resetAll` returned).
