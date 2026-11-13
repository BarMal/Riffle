# ADR 0003: Places and Views replace sources, lenses and workspaces as the product model

- Status: Accepted (2026-11-12; built in PRs #1446 to #1452, see [As built](#as-built-differences-from-the-original-decision))
- Date: 2026-11-12
- Supersedes: the product model in `docs/product/workspaces-sources-lenses.md` and the documents that build on
  it (see [Superseded documents](#superseded-documents)). ADR 0001 and ADR 0002 are unchanged.
- Superseded by: N/A

Companion documents: the settings reorganisation in [`docs/product/settings.md`](../../product/settings.md) and
the implementation slices in [`docs/product/places-views-plan.md`](../../product/places-views-plan.md).

## Context

### What the owner wants

A well-rounded Android launcher (Nova or Apex class) with a glass-leaning look, a dock plus an iOS-style app
library, and both cards (Stage Manager, TimeScape) and lists (Niagara) for dynamic content, all in one glass
style. Not a clone of any one launcher. Elements should be mixable per component and swappable on a whim.

### How the model got here

Each step answered "I cannot decide" with a more general model:

1. Three modes: Standard, Library, Cards.
2. A user-configured ring of two or three modes (#1225, #1239).
3. A fixed Home and Library pair with the dock as the handle (`modes-dock-handle-and-cards-plan.md`,
   revision 2026-09-25).
4. Workspaces: Source, Lens, Expression, Container, Workspace (`workspaces-sources-lenses.md`), with five
   presets named iOS-style, Nova-style, TimeScape, Niagara and Kvaesitso-style, and six theme presets.

At the time of writing the repository holds about 220,000 lines of Kotlin, about 20,000 of them in
`core/domain/.../launcher/workspace/`, and about 8,500 lines of workspace product documents.

### Findings: why sources and lenses feel wrong

| # | Finding | Evidence |
| --- | --- | --- |
| F1 | The model abstracts the axis nobody is indecisive about. The indecision is about presentation and feel; a lens is a query (filter, group, sort, limit). There are twelve sources and each has one to three sensible queries. | `workspace/lens/`, `ContentGoalCatalog` |
| F2 | The UI has to hide the model. | `workspaces-wizard-and-onboarding.md`: "Goals, not sources. Sources stay an implementation detail." |
| F3 | The core of a launcher is not a query. Hand-placed icons, the dock and widgets are authored arrangements, so the pool, the `home.grid` pseudo-source and fork-on-first-edit were added beside the model. | `workspaces-pool.md` |
| F4 | One universal `Item` flattens content. Media wants transport controls and a queue, notifications want reply, events want a time span. | `workspace/Item.kt` (`title`, `subtitle`, `body`, `ext`) |
| F5 | Swapping landed at the wrong level. Workspaces swap the whole launcher; the wish is to swap one component. The presets are the clones the owner does not want. | `workspaces-presets.md` |
| F6 | Indecision became architecture, in look as well as structure: six theme presets, and `AGENTS.md` still says Material while the goal is glass. | `LauncherThemePreset`, `AGENTS.md` |
| F7 | The workspace system was never validated on a device before it became the home. | The old `workspaces-dogfood.md` (replaced by [`places-views-device-checklist.md`](../../development/places-views-device-checklist.md); see git history): "Nothing about this has been run on a device yet" |
| F8 | Glass is not one material. About 55 literal alpha values (about 31 distinct) across 24 files; five or more separate surface treatments; the dock sheen and spotlight are welded to the dock; reduced transparency reaches only adaptive cards and the GLASS preset's overlay alpha. | `GlassSurface.kt`, `DockSpotlight.kt`, `DockGlassSheenStrength.kt`, `LauncherTheme.kt` |
| F9 | Settings mirror the layers, not the product: about 30 pages and about 140 controls, with duplicates, dead settings and four names for one concept. | [`settings.md`](../../product/settings.md) |

### What the owner's other projects already do

- The desktop shell (`BarMal/dotfiles`, "Serein"): one token layer, a few glass primitives, one spatial
  metaphor (the Shade), and swapping by hot-loading one of two bars into a fixed slot.
- Calm (`BarMal/Calm`): a fixed set of `PageSlot`s the user reorders, and a per-slot display mode
  (categories as one card stack or as dynamic pages).

Both are fixed places with a swappable view per place and one material. Neither has a query language.

## Decision

Riffle has **four fixed Places**, each with a short closed list of **Views** swapped in place, **six typed
kinds of content**, and **one glass material**. Sources stay as internal plumbing. Lenses, containers and
workspaces stop being user-facing.

### Rules

1. **Every model has exactly one home.** It may be echoed elsewhere (a badge, a glance widget, a Dock entry),
   but it lives and is configured in one Place.
2. **A new idea must be a new View for an existing Place, or a new content type for the Stream.** If it needs
   a new Place or a new layer, the answer is no.
3. **Swapping is one gesture, in place.** Long-press a Place's header or empty background, choose "Show as",
   and the Place redraws at once, with Undo. No editor, no wizard, no query.

### Places

| Place | Holds | Arranged by the user? | Reached by |
| --- | --- | --- | --- |
| **Home** | Apps, folders and widgets the user placed; the standard Android launcher model | Yes, by hand | It is the start page; swipe between its pages |
| **Dock** | Pinned favourites on one side; on the other, live apps and the Stream button | Pins by hand; the dynamic side fills itself | Always visible, on every surface |
| **Library** | Every installed app, and search | No, it sorts itself | Pull up on the Dock's pinned side; swipe down on Home opens it with search focused |
| **Stream** | The actions centre: now playing and its queue, notifications, recent apps, events, to-dos, feeds | No; only which sections, their order and their view | Pull up on the Dock's dynamic side, or tap its Stream button; swiping up on a live app lands on that app's stack |

Home press returns to the start page. Back closes the Library or collapses a focused card.

**Folded and unfolded.** Folded, Home and the Stream swap completely. Unfolded (and tablet), they can share
the screen as a split with a movable divider, or either can take the whole screen. The Dock sits under both.

### The Dock is the handle

Each side of the Dock opens its own world: the pinned side the static one, the dynamic side the live one.

| Action on the Dock | Result |
| --- | --- |
| Tap a pinned app | Launches it |
| Pull up on the pinned side | Library |
| Pull up on the dynamic side | Stream, tracking the finger; unfolded, stopping partway leaves a split |
| Tap the Stream button | Switches to the Stream, and back to Home from it |
| Tap a live app | Launches the app |
| Swipe up on a live app | Stream, landed on that app's stack, tracking the finger |
| Tap now playing / swipe up on it | Launches the player / Stream, landed on Now |

- The dynamic side holds apps with something new, plus one entry for now playing while media is active.
  Events, to-dos and feeds get no entry, because they are always present.
- The Stream button is always the last entry on the dynamic side, so the handle exists when nothing is live.
  It is also the accessibility equivalent of the pull, alongside Dock custom actions "Open Stream" and
  "Open Library".
- There is no other trigger. Home's pager holds only Home pages; there is no sideways swipe into the Stream.
  This keeps the lesson of the 2026-09-25 revision: overlapping triggers made switching unpredictable.
- "Pull up" means the Dock edge's natural pull direction (away from its edge), as `gestures.md` defines it.
- Fallback, still undecided because it needs a device: if the dynamic side is too narrow a target with one or
  two entries, the pull may start anywhere on the Dock and choose Library or Stream by the half it started on.
  As built the dynamic side is compact (see below), which makes this question sharper, not smaller.
- **Size.** Pinned icons are drawn at exactly the Icon size setting (default 44 dp, the Home grid's default) and
  never shrink. The bar is one icon plus its chrome tall. When the pins do not fit, the gap between them gives
  first, down to 8 dp (or the user's own smaller spacing), and then the pinned run scrolls at full size, fading
  through the glass at the edge that has more. The dynamic side takes only what its entries need (compact 48 dp
  tiles, up to the live-app-slot limit, plus now playing and the Stream button) and leaves the pinned run at least
  three icons; it scrolls on its own when it has more entries than room.

### Views

| Place | Views | Other options |
| --- | --- | --- |
| Home | Grid (only) | Grid size, icons and labels, return behaviour |
| Dock | Icon row (only) | Edge, size |
| Library | Categories (iOS-style, the default), A–Z list, Grid | Personal and work split |
| Stream | Stack or List, chosen per section; on an unfolded posture, Pages or Stage for the whole Stream | Which sections are on, and their order |

The Stream's sections are fixed: **Now** (media and its queue, next event, alarm), **Notifications**,
**Recents**, **Agenda**, **Tasks**, **Feeds**. Each draws as a Stack of cards or a List of rows.

**The Stream is pages of vertical stacks** (decision 12). The Stream is a horizontal pager of pages, and each page is
one vertically scrolling stack of cards or list of rows, the way Calm lays out its chapters. A page is one section's
content, except that Notifications is **one page per app** that has notifications (newest app first) and Feeds is one
page per feed with unread items; Now, Recents, Agenda and Tasks are one page each, and a section that has nothing to show
or cannot show it (empty, no access, not connected, unavailable, off) is one page holding that state and its action.
Pages follow the user's section order and carry stable keys, so a new notification never jumps the pager and a vanished
page hands over to its nearest neighbour. Each page has a header (the app's icon or the section's glyph, its name and
count), and a Thin-glass **spine** above it, a strip of page markers that scrolls with the pager and jumps on a tap, is
the page indicator. A Stack is a vertical pile of overlapping cards in Calm's proportions (168 dp cards, 56 dp apart,
the focused one in front) moved by the existing card-stack physics, so a sideways swipe changes page and a vertical
one moves the stack. Recents is the exception: its Stack is a wrapping grid of app tiles, and Tasks is one card
under the quick-add row. "Show as" applies to a page's whole section. Rationale: the first Stream laid every section on one
scrolling page with horizontal card stacks, which is the opposite of the model Calm proves (pages you swipe, piles you
scroll) and of the vertical stack physics the project already had.

**Unfolded: Pages or Stage** (decision 13). On an unfolded foldable, tablet or desktop the whole Stream has two views,
chosen with "Show as" on the Stream and in Settings > Stream, stored per posture (default Pages). **Pages** is the pager
above, two pages side by side when the Stream has the whole wide screen. **Stage**, after Stage Manager on macOS, keeps a
rail of live thumbnails along the leading edge, one per page except the selected one, with the selected page's stack
large beside it; a tap on a rail stage brings it to the centre and sends the previous one back to the rail with a
shared-element transition, and the rail collapses to icons in a narrow pane such as a split's Stream pane. Both work in
the split and with the Stream alone.

Deliberately absent: a Home list view, user-defined sections, and filters beyond per-type options (which
apps, how far back). Each is a possible later View or option, added one at a time against rule 2.

### Model map

Source ids are those in `core/domain/.../launcher/workspace/`.

| Model today | Place | Shown as | Interaction | Echo elsewhere |
| --- | --- | --- | --- | --- |
| Placed app, folder, widget (the pool, `home.grid`, `apps.favourite`) | Home | Icon grid, in pages | Tap launches; long-press enters edit; drag moves or makes a folder | Pins in the Dock |
| Dock pins (`PoolDock`) | Dock, pinned side | Icon row | Tap launches; drag to reorder or out to Home | None |
| All apps (`apps.all`) | Library | Categories, A–Z or Grid | Tap launches; long-press for shortcuts, Add to Home, Pin to Dock, Hide (no drag out to Home) | None |
| Recent and frequent apps (`apps.recent`, `apps.frequent`) | Stream, Recents | Icon row, or rows with last-used time | Tap launches | None |
| App shortcuts (`shortcuts`) | No Place: the long-press menu of any app icon | Menu rows | Tap runs it; drag a row out to place it on Home | A placed shortcut is a Home item |
| Search (`search`) | Library | The search field; results replace the app list | Type; Enter launches the top hit | Swipe down on Home |
| Notifications (`notifications`) | Stream, Notifications | One page per app, each a vertical stack or rows | Tap opens; Dismiss and Snooze buttons; expand to reply; Hide in the card's menu | Dock dynamic side, icon badges |
| Media (`media`) | Stream, Now | One card | Play, pause, skip, scrub; expand for the queue; tap opens the player | Dock dynamic entry while playing; glance widget |
| Calendar and calendar feeds (`calendar`, `ics`) | Stream, Now (next event) and Agenda (by day) | Card or rows | Tap opens the event; expand for details | Glance widget |
| News feeds (`rss`) | Stream, Feeds | One page per feed, each a vertical stack or headlines | Tap opens the article; Mark read action | Glance widget (top story) |
| Alarm (`AlarmManager.getNextAlarmClock()`, wired in P3b) | Stream, Now | A row beside the next event | Tap opens the clock app | None |
| To-do (Todoist, P10) | Stream, Tasks | Card of today's items, or rows | Tap ticks it off; tap the title to open; quick-add at the top | None |
| Widgets as cards (`WIDGET_PROVIDER`, #1219) | Parked: widgets live on Home only | | | |

Favourites are not a Stream section: the owner's favourites are what Home and the Dock's pinned side are.

The eight expressions map onto this without loss: `ICON_GRID` is Home and the Library grid; `ICON_ROW` is
the Dock and the Recents row; `ALPHA_LIST` and `CATEGORIES` are Library views; `CARD_STACK` is the Stream's
Stack; `LIST` and `INDEX` merge into the Stream's List; `CARD` is the Now card and the glance widgets.

**Glance widgets** are how dynamic content appears on Home: a fixed catalogue of Riffle's own widgets (Now
playing, Next event, Top story), placed and sized like any other widget.

### Typed content

Six content types replace the universal `Item` for everything the Stream draws. Each gets a hand-designed
card and row, except apps, which stay icons.

| Type | Fed by | Card (Stack) | Row (List) | Actions |
| --- | --- | --- | --- | --- |
| App | `apps.*`, `shortcuts`, placed items | Not used | Icon and label | Launch, shortcuts, place, pin, hide |
| Notification | `notifications` | App tint, sender, message, inline image; expands to the thread | App icon, sender, one line, time | Open, dismiss, reply, snooze, hide |
| Media | `media` | Artwork as the card surface, title, artist, transport, scrubber, queue | Small artwork, title, play or pause | Play, pause, skip, scrub, open player |
| Event | `calendar`, `ics`, alarm | Time span, title, place, countdown to start | Time, title, calendar colour | Open, expand |
| Article | `rss` | Lead image, headline, feed name, age | Headline, feed name, age | Open, mark read |
| To-do | Todoist first | Today's items with checkboxes, due times, quick-add | Checkbox, title, due time | Complete, open, add |

Sources keep their contracts: one shared subscription each, work off the main thread, and privacy redaction
before anything is drawn. Item content is still never written to layout, settings, backup or diagnostics.
A further type joins the Stream the same way: add the type, its card, its row and its section.

### Material: one glass, with the spotlight everywhere

Glass is the only look. Every surface in every Place is drawn by one primitive at one of three weights.

| Weight | Used for | Treatment |
| --- | --- | --- |
| Thin | Dock, chips, row backgrounds, page indicator | Light frost, hairline top highlight, no shadow |
| Regular | Cards, widgets' frames, folders, the search pill | Full frost, hairline, one soft shadow, tint |
| Thick | Library sheet, menus, pickers, the focused card, settings sections | Heavy frost with a contrast scrim behind text |

- **One primitive.** `GlassSurface(weight, shape, tint)` is the only way a surface gets a translucent
  background. Feature code contains no literal surface alpha or blur radius; the three weights own them.
- **Sheen everywhere.** The dock's glass sheen (`dockGlassSheen`: top specular, caustic spot, inner shadow,
  rim line, cool border) becomes part of the primitive for every weight, driven by the surface's own shape.
- **Spotlight wherever a surface holds coloured things.** The dock's icon-colour spotlight
  (`DockSpotlight*`) is generalised to take a `Shape` and sources on any axis, and is applied to: the Dock,
  open folders, Library category tiles, the search results panel, and Stream cards and stacks (lit by the
  owning app's colour). A surface with no coloured content gets the sheen only.
- **Glass over the system wallpaper can tint, not blur.** The wallpaper is outside the app's content, so a
  surface drawn over it has no backdrop to blur or refract; there it is the tint at the weight's alpha, plus the
  sheen and the spotlight, with the wallpaper showing through. Each weight has a floor tint alpha over the
  wallpaper chosen for 4.5:1 text contrast over a bright wallpaper (`wallpaperTintAlpha`: Thin 0.66, Regular
  0.66, Thick 0.72). The Dock uses its own "Dock background" setting instead, exactly (default 72%, 0% clear,
  100% opaque). The first build ignored this and drew these surfaces opaque near-black; the shader and its
  domain mirror (`GlassBlend`) were fixed in #1450. Frost and refraction apply only to app content behind a
  surface (the Stream, the Library sheet over Home).
- **Labels.** Home labels are plain text with a soft shadow, no pill, by default; the pill is the "Label
  background" switch in Settings > Home, and the stored opacity is kept for when it is turned on.
- **No idle animation.** The spotlight keeps its existing behaviour: it requests frames only while its
  sources change, settles, and then stops. This satisfies "nothing animates without cause" in
  `design-language.md`.
- **Tint** comes from two places only: the wallpaper for chrome, and an app's own colour for that app's
  cards, stacks and spotlight.
- **Fallbacks are part of the material, not separate looks.** Reduced transparency is one top-level
  appearance setting honoured by every glass surface (opaque tint, no backdrop sampling, sheen kept faint).
  Reduced motion makes the spotlight snap. Below the API levels the shader needs, the existing blurred-tint
  and scrim fallbacks apply.
- **Removed:** the custom background, dock, label and label-background colours (a custom accent remains); the `MATERIAL`, `MINIMAL`, `RETRO`, `TERMINAL` and `CUSTOM` theme presets; the per-workspace
  skin override; the separate "Liquid glass" on/off switch (glass is always on; reduced transparency is the
  off switch); the dock effect choice (FLAT, ELEVATED, OUTLINED); and the "Material design language" line in
  `AGENTS.md`. Stored values that name a removed preset decode to glass.

Tokens stay as `design-language.md` defines them: one spacing scale, one radius scale with continuous
corners, three springs.

### Settings

Settings are organised by Place, not by layer: Home, Dock, Library, Stream, Appearance, Gestures, Motion and
haptics, Privacy and access, Backup, About. The page tree, every correction and every removal are in
[`docs/product/settings.md`](../../product/settings.md).

### Keep, demote, cut

Packages are under `launcher/` in `core/domain` and `app`.

| Package or concept | Verdict | Becomes | As built |
| --- | --- | --- | --- |
| `workspace/sources`, shared source streams | Keep | Internal plumbing; each source emits its own type | Kept; the Stream's typed sources and mappers read through them |
| `workspace/pool` | Keep | The Home and Dock model | Kept; the only Home and Dock model |
| `workspace/dock` (presentation), `dockpull` | Keep | The Dock; the pull opens the Library or the Stream by side | `dockpull` kept; the workspace dock presentation was deleted, the Dock is `PoolDock` |
| `cards` (stack physics, travel, magnet) | Keep | The Stream's Stack view | Physics kept and used; the adaptive stage planners, templates and `LauncherSettings.cards` are left in place, read by no surface, not deleted |
| `expressions` | Keep | The Views, each bound to its Place | Kept (Library views, Stream rendering) |
| `workspace/exclusions`, privacy redaction | Keep | Per-type Hide rules | Kept |
| `designsystem` | Keep and extend | The glass material and tokens | Done |
| `workspace/lens` | Demote | One fixed default per section; never shown | Kept as storage-only and test vehicle: the lens model and codecs decode stored workspaces, and the evaluator carries the exclusion and content-level code and tests |
| `workspace/container` (page-set, widget grid) | Demote | One stack per app or feed; glance widgets | Mostly deleted; what remains is shared-source plumbing |
| `Item` | Replace | The six content types | Replaced for drawing; `Item` still exists as the source-side type the typed mappers read |
| `workspace/editor`, `workspace/wizard` | Cut | "Show as" in place, and Settings per Place | Deleted (P8) |
| Saved lenses, lens library, advanced builder, per-lens search queries | Cut | Nothing | Deleted from the UI (P8); the stored library still decodes and is exported |
| `workspace/preset` | Cut | One default arrangement | Deleted (P8) |
| `workspace/menu`, multiple workspaces | Cut; stored workspaces stay decodable | One arrangement per posture | Deleted (P8); stored workspaces stay in storage and in backups; `PageRole.FINDER` stays decodable and is never drawn |
| Skin override, non-glass theme presets | Cut | Glass | Deleted (P2); stored values are ignored |
| Onboarding walkthrough | Rework | Four steps, one per Place | Done (P8) |

### Decision log (owner, 2026-11-12)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Start page | Home, the standard Android grid |
| 2 | The switch between Home and the Stream | The Dock's dynamic side, by pull or by its Stream button; no sideways swipe |
| 3 | Multiple workspaces | One arrangement per posture now; stored workspaces stay decodable so switching can be revisited |
| 4 | Stream view | Stack or List chosen per section |
| 5 | Dock dynamic side | Apps with something new, now playing, and the Stream button |
| 6 | Non-glass skins, including Terminal | Delete all; glass only. A phosphor look could return later as a mode |
| 7 | To-dos and widgets-as-cards | Todoist first; widgets-as-cards stay parked |
| 8 | Library default view | iOS-style Categories |
| 9 | Favourites in the Stream | No: favourites are Home and the Dock's pinned side |
| 10 | Spotlight | Apply the dock's spotlight wherever possible; at minimum unify transparency for all surfaces |
| 11 | Settings | Reorganise, correct and improve, per `settings.md` |
| 12 | Stream layout | Horizontally scrollable pages of vertically scrolling card stacks, not one scrolling page of horizontal stacks: one page per app for notifications, a spine as the page indicator (look to Calm, on TimeScape) |
| 13 | Stream on unfolded | Two views of the whole Stream, Pages and Stage (after Stage Manager on macOS), chosen per posture; Stage keeps a rail of live stage thumbnails beside the selected stack |

Later outcomes, driven by what the owner saw on the phone (all 2026-11-12):

| # | Question | Outcome |
| --- | --- | --- |
| 14 | Glass looked opaque near-black over the wallpaper | Fixed (#1450): the shader keeps the backdrop's alpha, each weight has a wallpaper tint floor, and the Dock follows its own "Dock background" setting exactly |
| 15 | The Dock after the Dock rework was short and its icons small | Restored to the reference look (#1452): pinned icons always at the Icon size setting, a scrolling pinned run with edge fades and an 8 dp minimum gap, a compact dynamic side that leaves at least three pins, the bar one icon plus chrome tall. Custom dock colours were not restored |
| 16 | Label pills on Home | Off by default (plain text with a soft shadow); "Label background" in Settings > Home turns the pill back on |

Owner decisions after the first device run (2026-11-13):

| # | Question | Decision |
| --- | --- | --- |
| 17 | What a tap on a live app on the Dock's dynamic side does | Live app tap opens the app; swipe up opens its stack. Now playing the same (tap opens the player, swipe up opens Now); the Stream button is unchanged |

## Consequences

- **Less to build and test.** The combination space drops from sources × lenses × expressions × containers ×
  workspaces × postures × skins to four Places with at most three Views each, six Stream sections with two
  Views each, and one material. The validity matrix, the fallback notices and most of the screenshot matrix
  go away.
- **Data safety is the main risk.** Stored workspaces, lenses, skins, theme presets and removed settings
  must still decode. Nothing the user placed may be lost: the pool (items, arrangements, dock) is the source
  of truth for Home and the Dock and is carried over unchanged. Backups written by earlier builds must
  still import. Every slice that removes a stored concept needs codec tests for old data.
- **The Stream has to earn its place.** The 2026-09-25 plan recorded that Cards was avoided because it was
  clunky, and this model is close to that Home and Cards pair. The model did not fail then; the feel did.
  The Stream's feel on the owner's phone is the first thing to validate.
- **Large deletions.** The editor, wizard, presets, workspace menu, lens library and saved lenses are
  removed, with their tests and screenshots. Deletion comes last, after the replacements work.
- **Per-type work replaces generic work.** Card and row renderers are hand-built per content type. This is the intended
  trade: fewer combinations, each one designed.
- **One provider dependency.** Tasks need a Todoist personal token. It is entered in Settings, stored
  encrypted on the device, never backed up and never shipped.
- **Device validation is a gate.** No slice that changes a visual surface is done without rendered evidence
  (JVM screenshots), as Decision 8 of the earlier plan already requires. Motion and the Dock pull need a
  device pass by the owner. As of this acceptance nothing in this refactor has been run on a physical device
  except what the owner reported (glass, Dock size, labels); the rest is checked only by JVM, screenshot and
  emulator tests. [`places-views-device-checklist.md`](../../development/places-views-device-checklist.md) is the
  list of what must be checked by hand.
- **Limitations that remain.** The dynamic side may be too narrow a pull target (undecided, needs a device). The
  Todoist API mapping is only partly verified against the reference. The stored workspaces, lenses, `Item` and
  the card-stage code are carried as storage-only code, so the codebase is smaller than the model but not as
  small as it could be; deleting it is a later, separate change.
- **Documents.** The superseded documents below get a banner pointing here; `AGENTS.md` and
  `design-language.md` are updated to say glass, not Material.

## Alternatives Considered

1. **Keep Source, Lens, Expression, Container, Workspace.** Rejected for findings F1 to F7. The flexibility
   sits on the wrong axis and the UI already hides the model behind goals.
2. **Keep the model and only simplify its UI.** Rejected: the pool, the wizard and the validity matrix exist
   because of the model, so the cost stays.
3. **Stream as the page left of Home, reached by a sideways swipe.** Rejected by the owner in favour of the
   Dock as the only trigger. One trigger family is more predictable, and Home's pager stays purely Home.
4. **A Stream button without the pull, or the pull without the button.** Rejected: the pull alone has no
   handle when nothing is live, and the button alone gives up finger tracking and the partial (split) stop.
5. **Keep multiple workspaces for Work and Personal set-ups.** Deferred, not rejected: storage stays
   decodable so switching can return if it is missed.
6. **Keep Terminal as a second skin.** Rejected: every surface would need checking in two looks. A phosphor
   mode may return later the way the desktop's sentry bar does, as a mode on the same tokens.
7. **Widgets as Stream cards.** Parked: not needed to prove the model.

## As built: differences from the original decision

Places, Views, typed content and the one material are as decided. These are the deviations, each checked against
the code at acceptance.

- **No Stream feel settings page.** Nothing reads the stored card-stack settings after P8, and the Stream's Stack
  has fixed physics, so the planned "Stack feel" page does not exist (`settings.md`, "Differences").
- **The Stream is pages, not one surface of sections** (decisions 12 and 13), with Pages and Stage views on
  unfolded postures. This replaced the first Stream built in P4.
- **Media and notification data came in P3b**, not P3: the typed content first carried only what `Item` held.
- **Tasks without a due date are not fetched**; only today's and overdue tasks are. The Todoist API paths, field
  names, task links and due-date formats are only partly verified; `TodoistApiMapping.kt` is where a mismatch is
  fixed.
- **Custom dock, label and background colours were removed and not restored**; only a custom accent remains.
- **Per-workspace dock and gesture overrides are no longer applied.** The only trace is the one-time restore of the
  Dock's icon size and edge from the stored workspaces, never over a size set since.
- **No drag from the Library onto Home**; Add to Home and Pin to Dock in the long-press menu are the equivalents.
- **No swipe to dismiss or mark read** on Stream cards; those are buttons and menu actions.
- **"Show as" is on headers and their option buttons** (and in Settings), not on a Place's empty background.
- **Settings not built:** "Show now playing" (it always shows while media is active), "New apps go to Home or
  Library only", a per-calendar list for Agenda, and thread grouping for Notifications (`settings.md`).
- **Stored code kept, not deleted:** see the "As built" column of Keep, demote, cut.
- **Not built in P13 and P14:** dragging a stage in the Stage rail to reorder, a thumbnail that is a scaled copy of
  the live card, an animation of the spine's markers between sections, a per-page stack-depth setting.
- **Glass over the wallpaper cannot blur it**, which the original Material section did not anticipate.

## Amendment 1 (owner, 2026-11-13): the Dock is the origin (folded)

Decided on the UX canvas after device feedback on the first build. Not built yet. It changes how the four
Places are reached and drawn on a folded device; the Places, typed content and the single material stand. The
unfolded layout is still open and is not covered here.

**Why.** The first build put the Stream spine and page header at the top of the screen, far from the thumb, and
the Dock's dynamic section repeated what the spine showed. Controls now cluster at the Dock and content grows
from it.

### The Dock has two faces

| Face | Dock shows | Behind it |
| --- | --- | --- |
| Apps | Pinned apps, a divider, live apps | Home |
| Live | The live pages as markers; the focused marker widens to show its name and count | Stream |

- **Swipe down on the Dock** flips between the faces. There is no Stream button.
- **Slide along the live face** to change page. The live apps are the page markers, so there is no separate
  spine and no page header.
- On the apps face a live app keeps its gestures: tap opens the app, swipe up opens its page.
- Whether pinned apps stay visible as a second row on the live face is a setting; hidden is the default.

### Stream: browse, then open

- **Browse.** A page is a stack of cards, anchored towards the Dock: the front card is nearest the thumb and
  older cards peek above it. This reverses TimeScape's direction on purpose, because the Dock is the origin.
  A peeking card is clipped to the part the card in front does not cover, so fills never add up.
- **Open.** Tapping a card grows it in place into its full content (a conversation, the player, an event).
  The Dock gains a row with that card's actions (reply field, Dismiss, Snooze, transport), so reading sits
  high and acting sits under the thumb.
- **Today.** Agenda and Tasks are one page: the next event in front with a countdown and actions, a
  "To do today" checklist card, then later events and tomorrow.
- **Images.** Browse and open cards show the contact photo with the app icon as a badge. Browse cards show one
  small thumbnail; open cards show image content in full. All of it comes from the notification, so it
  appears only when the sending app provides it.
- **Page options** (show as stack or list, move, hide) move off the page header to a long-press on its marker.

### Card colour

A card's fill is tinted with its app's main icon colour. The edge stays the neutral glass edge and the
spotlight lights the content only. A coloured border was tried and rejected as too harsh.

### Home, Library and search

- **Home handle.** Page dots and search are one pill above the Dock: tap searches, and dragging along the
  dots still scrubs quickly between pages.
- **Search** opens inside the Dock, above the keyboard. Best matches sit nearest the thumb; further results
  stack above them.
- **Library** is pages of every app in a grid, the same as the App library Home, with no sheet behind it.
  The earlier categories sheet was a misreading of the owner's decision and is dropped.
- **Home mode** (Placed items or App library) and the feature switches (Stream, Dock live apps, Library),
  including a Simple mode that leaves only the App library and the Dock, stand as agreed on 2026-11-12.

### Settings this adds (Stream)

| Setting | Options (default first) |
| --- | --- |
| Where an open card sits | Centre of the screen; just above the Dock; grows out of the Dock |
| Where its actions sit | In the Dock; on the card |
| Card colour | App tint strength (22%); coloured glow on the top edge (off) |
| Behind the cards | Wallpaper only; Home blurred and dimmed |
| Pinned apps while in Stream | Hidden until you swipe down; kept as a second Dock row |

Over blurred Home the tint is raised so text stays readable.

### Precedents

Read high and act low is the split Samsung's One UI is built on; a bar that becomes live controls resembles
Samsung's Now Bar and Apple's Dynamic Island; per-app card stacks come from TimeScape and webOS. The
combination, with the launcher's Dock as the single surface that flips between apps and live content, is
Riffle's own.

## Amendment 2 (owner, 2026-11-13): unfolded, and layout per posture

Decided on the UX canvas. Not built yet. It replaces the unfolded "Pages or Stage" views and the Home and
Stream split of the original decision (decision 13 and the Folded and unfolded rule). Motion is still open.

### Layout is a setting per posture

Folded and unfolded each keep their own layout. The owner leans towards configurability over one fixed answer.

| Setting | Options | Folded default | Unfolded default |
| --- | --- | --- | --- |
| Dock edge | Bottom, right, left | Bottom | Right |
| App pages scroll | Sideways, up and down | Sideways | Up and down |
| Page indicator | Beside the Dock, in the Dock, off | Beside (above the Dock) | In the Dock, at its middle |
| Drag the indicator to scroll quickly | On, off | On | On |

The indicator always runs the way the pages scroll, so a quick-scroll drag never moves pages at right angles
to it. Cards and live content stack upright in both postures.

### Unfolded

- **The Dock is centred on its edge** and shows both faces at once: search at its top end, pinned apps, the
  Home page dots at its middle, then the live pages. There is nothing to flip. The dots sit in the Dock
  because the card stack occupies the space beside its middle.
- **Cards are the same upright stack as folded**: older cards as tabs above the front card. The stack touches
  the Dock level with its page marker, so it reads as coming out of the Dock. A stack that floats as a free
  column was drawn and felt like a separate component.
- **Opening a card** makes it wider and taller from the same spot, with its actions inside it. At rest the
  grid has about two thirds of the screen and the cards a third; an open card may take up to half.
- **Home is one continuous grid**, not two pages side by side. It fades out softly at every edge and towards
  the cards and the Dock; it is never cut off at the Dock.
- **App peek-through** is a setting: apps show faintly behind the cards, or nothing shows behind them.
- **Search** opens as a panel attached to the Dock, the way a card does, with the field at the bottom of the
  panel nearest the keyboard. The Dock shortens while the keyboard is up.

### Tried and dropped

A bottom Dock with a cluster under each thumb; the folded layout centred over blurred Home; two Home pages
side by side; a sideways stack as a full-height panel (too much dead space) and as a compact card with
photo-only strips (older cards unreadable); a flat page-dots pill beside the Dock (the centre of the screen
is the least reachable part when unfolded).

### Precedent

Apple's iPhone Duo (2026) moves its Dock to the side of the unfolded screen for thumb reach, with a
left-handed option. Riffle's Dock differs in also carrying live pages and being the origin of cards.

## Superseded documents

These describe the workspace model or its UI and are superseded by this ADR. They stay in the repository as
history, each with a banner pointing here.

- `docs/product/workspaces-sources-lenses.md` (the model)
- `docs/product/workspaces-configuration.md`, `workspaces-settings.md`, `workspaces-editor.md`,
  `workspaces-wizard-and-onboarding.md`, `workspaces-presets.md`, `workspaces-lens-library.md`,
  `workspaces-lens-queries.md`, `workspaces-saved-lenses-page.md`, `workspaces-source-pages.md`,
  `workspaces-start-page.md`, `workspaces-s7.md`, `workspaces-default.md`, `workspaces-dock.md`,
  `workspaces-screenshot-matrix.md`
- In `modes-dock-handle-and-cards-plan.md`: Decisions 1, 3, 5, 6, 9 and 11 (the rest stand)

Still current: `workspaces-pool*.md` (the Home and Dock model), `workspaces-exclusions.md`,
`workspaces-privacy.md`, `workspaces-backup.md`, the source documents (`workspaces-external-sources.md`,
`workspaces-ics-*.md`, `workspaces-sources-rss-search.md`, `rss-*.md`), `dock.md` and `gestures.md`
(updated by the Dock slice, with a note where Cards and workspace-menu text remains as history), and
`design-language.md` (updated by the material slice). The pool, privacy, exclusions and backup documents carry a
short note that the preview and editor they mention are gone.

## References

- `docs/product/workspaces-sources-lenses.md`, `docs/product/modes-dock-handle-and-cards-plan.md`
- `docs/product/design-language.md`, `docs/product/dock.md`, `docs/product/gestures.md`
- `docs/development/places-views-device-checklist.md` (replaces `workspaces-dogfood.md`)
- Dock spotlight and sheen: #1442, #1443, #1444, #1445
- `BarMal/Calm` (`PageSlot`, `CategoryDisplayMode`), `BarMal/dotfiles` (`docs/shell-design-plan.md`)
- ADR 0002 (platform gesture primitives), which the Dock pull and the split divider must follow
