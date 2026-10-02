# Sources page: detail pages, Used by, Add source (as built)

Slice of #1363, issue #1419 (follows the Sources page of #1384; design section 2.4 of
[`workspaces-configuration.md`](workspaces-configuration.md)). Preview-only: every page here sits behind Settings >
Developer > Workspaces (preview), is never in the main page, Settings search or launcher search, and with the preview
off nothing in this slice exists or runs. No new permission, prompt, background work or dependency.

`./gradlew verify` was **not** run locally (the sandbox cannot resolve the Android Gradle plugin); CI is its first run.
What was run here: the new domain code and its tests (JVM, Maven Central only: tests, ktlint, detekt with the repo
config). The app-side Compose code was only parsed (ktlint and detekt without type resolution) and reviewed by eye; it
has **never been compiled or screenshotted**.

## What the Sources page does now

- Every row opens its **detail page** (tap the text part). The switch is its own 48 dp control with its own spoken label
  ("Use this source: Apps"); before this slice the whole row was the switch. TalkBack reads two elements per row: the
  row (name, status, "Used by N places", what it shows, "Double tap for details") and the switch.
- Every row has a **"Used by N places"** line (hidden until the workspaces have loaded; "Not used by any page yet" for 0).
- An **Add a source** section: **Add RSS feed** (opens the RSS source page, where the existing add-feed form lives) and
  **Add calendar feed** (opens Calendar feeds, the existing ICS add flow; shown only where calendar feeds exist). No flow
  was duplicated: both buttons only navigate.

## Detail pages

One new `SettingsPage` entry per source (`SOURCE_APPS`, `_RECENTS`, `_SHORTCUTS`, `_NOTIFICATIONS`, `_MEDIA`,
`_CALENDAR`, `_RSS`, `_SEARCH`, `_ICS`), mapped by `SourceDetailPages` (a pure table). Back from a source page, Hidden
items and Calendar feeds returns to Sources (`settingsBackTarget`); every other page still returns to the main page.

Every page has: the source's description, a status chip (live region), the existing permission affordance when the
source needs access (the same `SourceAccessLaunchers` flows, explained before the button, never automatic), a
"Use this source" switch, the source's own sections, and the **Used by** list.

| Page | Own sections |
| --- | --- |
| Notifications | A "Content level" note (see decisions) and the notification exclusion rules (the Hidden items page's list, filtered to this source, with its layout tabs, switches, delete with Undo and Add text rule), plus a row to open all rules. |
| Apps | The Hidden apps view: the exclusion rules of the app sources (all apps, recent apps, quick actions), same list as above without Add, plus the row to open all rules. |
| RSS feeds | The existing RSS feeds settings, embedded unchanged (privacy text, background refresh and its status, Refresh now through the existing coordinator, per-feed status, feed list, Add feed). |
| Search | The per-lens query model, and whether the one shared query is set (never its text). |
| Calendar feeds (ICS) | The existing feed list with per-feed status, toggles, Remove and Refresh, and a link to the page that adds feeds. |
| Calendar, Media, Recent apps, Quick actions | Status, permission affordance, a one-line note (calendar privacy, usage access, media uses notification access) and Used by. |

Unfolded (600 dp and up) the page is two columns: the source's own sections on the left, Used by on the right.

## Used by

`SourceUsagePlanner.plan(layouts)` (pure, `core/domain`, `workspace/settings/SourceUsage.kt`) walks every binding of every
workspace (pages, page-sets, widgets, the dock section) and records a `SourcePlace` per binding per source: layout,
workspace name, page number, widget number, kind and the saved lens name when the binding reads through a saved lens.

- A binding with a saved-lens reference that still resolves reads **what the saved lens reads** (the library is the truth,
  the stored snapshot may be stale); a dangling reference or an inline lens reads its own snapshot.
- A lens naming a source twice counts once; a lens over two sources counts for each.
- It counts **every layout** (a layout with nothing stored reads as its default workspace, as the rest of Settings does),
  because sources are enabled globally. Entries carry the layout name when more than one layout reads the source.
- The page shows each place as `Workspace > Page 2 > Widget 3` (spoken with commas). A place on the layout this device is
  showing is tappable and opens that workspace in the editor (the same `onEdit` the Workspaces page uses); a place on
  another layout is not tappable and says "Switch Settings to this device's layout to edit it."
- `WorkspacesSettingsController.sourceUsage(layouts)` feeds it from the repository and changes nothing.

## Decisions

- **Content level is a "coming later" note.** Design section 6.2 proposes `SHOW` / `HIDE_BODY` / `HIDE_TEXT` per source,
  but nothing like it exists in the app (searched: no content-level type, setting or projection). The page therefore says
  it is coming later and points at hide rules; no privacy control was invented.
- **Search**: no new provider concept. The page explains that a lens can carry its own query and that lenses without one
  read the shared `SearchQueryHolder`. The runtime now creates that holder explicitly and passes it both to the Search
  source and to Settings, so the page can say whether a shared query is set. **Nothing in the app writes the holder
  today**, so in practice it reads "No shared query is set". Only the fact is shown, never the text.
- **RSS** reuses `SettingsRssPageContent` rather than splitting it, so the old Settings > RSS feeds page and the source
  page are the same UI. "Add RSS feed" therefore lands on the top of that page (the add form is at its bottom); it does
  not scroll to it.
- **Hidden apps / notification rules** reuse `ExclusionsSettingsContent` through a new optional `sources` filter and the
  pure `ExclusionsSettingsModel.forSources`; the controller, announcements and Undo are the Hidden items page's.
- **ICS** has its own row (the `ics` source id was already registered) in addition to the existing Calendar feeds section
  at the bottom of the Sources page, which is unchanged.
- No new `LauncherShellAction`, so `LauncherActionDomainTest.routeOwnershipTable` is untouched.

## Not done

- A scroll-to-the-form for **Add RSS feed** / **Add calendar feed**; both land on the top of the existing page.
- The **content level** control itself (needs the section 6.2 projection ceiling).
- A saved-lens row in Used by for library lenses that no page uses yet (only placed bindings count).
- Screenshots of the RSS page body and the add forms (they contain text fields: a focused field's cursor blink hangs
  the Roborazzi run). The RSS page embeds the existing UI; the add forms are the existing ones.
- Per-source "Reset"/"Clear cache" actions, per-source refresh for Calendar (it has no refresh), search provider settings.
- Compile and run on CI and a device (see top).

## Tests

Domain: `SourceUsageTest` (pages, widgets, page sets, dock, duplicates, multi-source, saved-lens reference and dangling
reference, layout order, workspace order) and `ExclusionsForSourcesTest`. App JVM: `SourceDetailTextTest` (the page
table, Back targets, wording, spoken summaries), `WorkspacesSettingsControllerTest` (usage), `SettingsDeveloperPagesTest`
(the new pages are Developer pages: not in the main page or search). Screenshots (fakes only, Roborazzi):
`SourcesSettingsScreenshotTest` (compact, dark, large font, unfolded, open-row vs switch, Used by lines, Add source) and
`SourceDetailScreenshotTest` (Notifications at compact, dark, large font and unfolded; Hidden apps, Search with and
without a shared query, Calendar feeds, a permission-gated source, the Used by rows and their tap targets).

## Checklist for the owner (on the device)

- [ ] Preview off: no Sources row, no source page, nothing in search. Preview on: Settings > Developer > Sources.
- [ ] Each Sources row shows its status and "Used by N places"; tapping the text opens its page; the switch alone turns
      the source off and on (status changes at once); TalkBack reads the row and the switch separately.
- [ ] Notifications page: the content level note says coming later; the rules list is only notification rules; turn a rule
      off and on, delete one with Undo; Add text rule works; "Open all hidden items and rules" opens the full page and Back
      returns to Sources.
- [ ] Apps page: your hidden apps appear as rules (From earlier settings); turn one off and the app returns to the drawer
      and pages; no add button.
- [ ] RSS page: the same feeds, refresh interval and Refresh now as Settings > RSS feeds; add a feed; per-feed status.
      Sources > Add RSS feed opens this page.
- [ ] Calendar feeds source page: feeds, Refresh, the link to the add flow. Sources > Add calendar feed opens that page.
- [ ] Search page: explains per-lens queries; reads "No shared query is set"; never shows a query.
- [ ] Calendar, Media, Recent apps: Allow opens the same dialogs or settings screens as before and never before a tap.
- [ ] Used by: counts match what you see in the workspace editor, including a page that uses a saved lens and the dock
      section; tapping a place on this device's layout opens the editor on that workspace; another layout's places say to
      switch layouts.
- [ ] Unfolded: two columns on the Sources page and on a source page. Large font: nothing cut off. TalkBack: status is
      announced politely when it changes.
- [ ] Turn the preview off: all of it is gone and Settings behaves as before.
