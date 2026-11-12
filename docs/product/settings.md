# Settings: organised by Place

Status: **target design**, part of [ADR 0003](../architecture/adr/0003-places-and-views.md). It replaces
`workspaces-settings.md` and section 2 of `workspaces-configuration.md`. Built by slice P9 of
[`places-views-plan.md`](places-views-plan.md); slices that remove a feature remove its settings with it.

Paths: `A/` is `app/src/main/java/com/riffle/app/launcher/`, `D/` is
`core/domain/src/main/kotlin/com/riffle/core/domain/launcher/`.

## Audit of the settings as built (2026-11-12)

About 30 `SettingsPage` entries (`A/SettingsPages.kt`), about 140 controls, about 11,400 lines of settings UI.

### Duplicates

| Setting | Appears in | Keep it in |
| --- | --- | --- |
| RSS feeds and refresh | RSS feeds page (`A/SettingsPageContent.kt:93`) and the RSS source page (`A/SettingsSourceSections.kt:61`) | Stream > Feeds |
| Content level per source | Privacy and each source detail page | Privacy and access |
| Hidden apps | Hidden apps page and app exclusion rules (`A/SettingsSourceSections.kt:56`) | Library > Hidden apps (one list) |
| Default home app | Main page and Permissions | Privacy and access |
| Dock position, visibility, icon size | Dock page and the per-workspace Dock dialog | Dock |
| Dock notifications | Dock page (`A/DockSetting.kt:497`) and the workspace `dynamicSection` | Dock > Dynamic side |
| Notification hiding | `notificationHiding` in `LauncherSettings` and exclusions | Stream > Notifications > Hidden items |

### Dead or no-op (delete)

| Setting or field | Why it is dead |
| --- | --- |
| `afterLeavingLibrary` | No UI; `returnsHome` is never called |
| `unfoldedShowAllNotifications` | Never read (`D/settings/LauncherSettings.kt:116`) |
| Expandable dock, Open the shelf with, Dock panel | `DockShelfExpansion.enabled = false` is hard-coded, so they never render |
| Compact library pages | Shown only in the legacy `HOME_SCREEN_LIBRARY` mode |
| "Following the active Cards template" text | Refers to the legacy Cards mode |
| Developer > "Home from placed items" | The pool home becomes the only home |
| Cards appearance page entry | Only redirects to the tuning overlay |
| Contextual > Contextual behaviour | Only affects legacy generated pages |
| `workspaceMenuEnabled`, `needsWorkspaceMenu` | Hard-coded true; the menu itself is removed |
| `Workspace.gestureBindings` | Not editable anywhere |
| `CUSTOM` theme preset | Identical to `MATERIAL` minus dynamic colour |
| `fullscreenHome` with no UI | Folded into Home > Full screen (see below) |

### Naming (one word per concept)

| Use | Instead of |
| --- | --- |
| Floating dock | "Overlay edge", "overlay dock" |
| Hidden items | "Hidden items and rules", "Hide rules", exclusions |
| Hidden apps | (kept, as the Library's own list) |
| Glass | "Liquid glass", the GLASS preset |
| Corners | "Card corners" (it applies to panels, the dock and rows too) |
| Icon size (Dock) | "Dock height" (it is backed by `iconSizeDp`) |
| Colour | "Color" (British spelling throughout) |
| Calendar feeds | "Calendar feeds (ICS)", "Calendar feeds source" |
| Library | "App drawer" |
| Stream | "Cards", "Sources", "stages" |
| Swipes and pinches on Home | "Home swipe actions" |

### Other faults

- Back from Hidden apps goes to the main page, not its parent (`A/SourceDetailText.kt:35`).
- Search matches page titles only, not individual settings, and does not index the workspace, source,
  hidden-item or calendar-feed pages. The alias "double tap" matches no gesture
  (`A/SettingsMainPageEntries.kt:129`).
- Several pages use bespoke controls where shared rows exist: TextButton choice lists (`A/DockSetting.kt`),
  plus and minus buttons (`A/HomeGridSetting.kt:122`), hand-rolled radio rows
  (`A/SettingsReturnBehaviorSection.kt:39`, `A/SettingsPrivacyPage.kt:178`), raw FilterChips, tap-to-cycle
  rows (`A/SettingsMotionPageContent.kt`), and a separate component family for card appearance.
- All text is hard-coded; `strings.xml` holds only `app_name`.
- The settings codec writes `"version": 10` and never reads it (`A/LauncherSettingsJsonCodec.kt`).
- Backup omits calendar feeds and disabled sources.

## Target page tree

The main page is a search field and ten rows. Search indexes every individual setting and opens its page
scrolled to it.

| Page | Section | Settings |
| --- | --- | --- |
| **Home** | Grid | Columns, rows, horizontal margin, vertical margin (per device class) |
| | Icons and labels | Icon size, show labels, label background, label text size, label width, label sizing, label lines |
| | Behaviour | Returning to Home (restore, first page, start page); new apps go to Home or Library only |
| | Wallpaper | Wallpaper (system, solid colour), wallpaper motion, change wallpaper |
| | Full screen | Hide status bar, hide navigation bar |
| | Set-up | Set up your home again |
| **Dock** | Dock | Show dock, position, icon size, pinned icons, width, alignment, corner radius, background strength, spacing to the grid |
| | Dynamic side | Live app slots, show now playing |
| | Floating dock (sub-page) | Enabled, apps, edge, handle thickness, size, position and opacity, expanded icon size, orientation, labels |
| **Library** | View | Show as (Categories, A–Z, Grid), grid columns, personal and work split |
| | Search | Search results as icons or list |
| | Hidden apps (sub-page) | The one list of hidden apps |
| **Stream** | Sections | For each of Now, Notifications, Recents, Agenda, Tasks, Feeds: on or off, order, Show as (Stack, List) |
| | Notifications (sub-page) | Group by message or thread, order, hidden items |
| | Agenda (sub-page) | Calendars shown, calendar feeds (add, remove, refresh) |
| | Tasks (sub-page) | Todoist account (token), which project or filter |
| | Feeds (sub-page) | Feed list, background refresh, only on Wi-Fi, only while charging |
| | Stack feel (sub-page, advanced) | The card-stack geometry and fan controls that survive the material slice |
| **Appearance** | Colour | Colour mode, accent (including a custom accent) |
| | Glass | Frost strength, edge refraction, spotlight flow, reduced transparency |
| | Shape and type | Corners (standard, compact, rounded), typography (system, monospace) |
| **Gestures** | Swipes and pinches on Home | The 14 bindings. Actions: None, Open notifications, Open search, Open Library, Open Stream, Open settings, Edit Home, Page overview, Full-screen Home, Next page, Previous page, Launch app, Launch shortcut |
| **Motion and haptics** | | Animation performance, reduced motion, feedback strength (as choice rows, not tap-to-cycle) |
| **Privacy and access** | Access | Default home app, notification access, usage access, calendar, floating dock access, each with its state |
| | Privacy | Screen protection; content level for Notifications, Agenda and Tasks (show, hide body, hide text) |
| **Backup** | | Export, import. Includes calendar feeds and which Stream sections are on. Never includes the Todoist token or content |
| **About** | | Version and build |

Removed pages: Workspaces, Sources and the nine source detail pages, Saved lenses, Contextual, Cards
appearance (redirect), RSS feeds (top level), Calendar feeds (top level), Permissions and Privacy (merged),
the Developer section.

Removed settings beyond the dead ones above: Theme preset; the custom colour targets other than the accent;
the Liquid glass on/off switch; Dock effect (flat, elevated, outlined); Dock notifications and Notification
space (replaced by Dynamic side); every per-workspace action (switch, rename, duplicate, make default,
reorder, dock override, appearance, reset to preset, delete, install preset, copy from other layout); the
`OPEN_WORKSPACE_MENU` and `SELECT_NEXT/PREVIOUS_APP_STAGE` gesture actions.

## Rules for the implementation

1. **Shared rows only.** Every page is built from `SettingsSection`, `SettingsClickableRow`,
   `SettingsSwitchRow`, `SettingsListRow`, `SettingsChoiceRow` and `DiscreteSettingSlider`
   (`A/SettingsRows.kt`). Add a shared stepper row for columns and rows if one is needed; do not hand-roll.
2. **Every "Show as" in Settings is the same choice as the in-place long-press.** One stored value, two ways
   to change it.
3. **Stored data.** Removed enum values and keys decode without crashing: an unknown gesture action becomes
   None, an unknown theme preset becomes glass, removed keys are ignored. The codec reads its `version`.
   Add codec tests with a settings blob written by the current build.
4. **Strings.** New and moved text goes into string resources. Migrating all existing text is not part of
   this slice.
5. **Back** always goes to the parent page in the tree above.
6. **Accessibility.** Every row has a 48 dp target and a spoken state; choice rows are radio groups.
