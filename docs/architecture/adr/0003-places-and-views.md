# ADR 0003: Places and Views replace sources, lenses and workspaces as the product model

- Status: Proposed
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
| F7 | The workspace system was never validated on a device before it became the home. | `workspaces-dogfood.md`: "Nothing about this has been run on a device yet" |
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
| **Stream** | The actions centre: now playing and its queue, notifications, recent apps, events, to-dos, feeds | No; only which sections, their order and their view | Pull up on the Dock's dynamic side, or tap its Stream button; tapping a live app lands on that app's stack |

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
| Tap a live app | Stream, landed on that app's stack |

- The dynamic side holds apps with something new, plus one entry for now playing while media is active.
  Events, to-dos and feeds get no entry, because they are always present.
- The Stream button is always the last entry on the dynamic side, so the handle exists when nothing is live.
  It is also the accessibility equivalent of the pull, alongside Dock custom actions "Open Stream" and
  "Open Library".
- There is no other trigger. Home's pager holds only Home pages; there is no sideways swipe into the Stream.
  This keeps the lesson of the 2026-09-25 revision: overlapping triggers made switching unpredictable.
- "Pull up" means the Dock edge's natural pull direction (away from its edge), as `gestures.md` defines it.
- Fallback, to be decided on a device: if the dynamic side is too narrow a target with one or two entries,
  the pull may start anywhere on the Dock and choose Library or Stream by the half it started on.

### Views

| Place | Views | Other options |
| --- | --- | --- |
| Home | Grid (only) | Grid size, icons and labels, return behaviour |
| Dock | Icon row (only) | Edge, size |
| Library | Categories (iOS-style, the default), A–Z list, Grid | Personal and work split |
| Stream | Stack or List, chosen per section | Which sections are on, and their order |

The Stream's sections are fixed: **Now** (media and its queue, next event, alarm), **Notifications**,
**Recents**, **Agenda**, **Tasks**, **Feeds**. Each draws as a Stack of cards or a List of rows.

Deliberately absent: a Home list view, user-defined sections, and filters beyond per-type options (which
apps, how far back). Each is a possible later View or option, added one at a time against rule 2.

### Model map

Source ids are those in `core/domain/.../launcher/workspace/`.

| Model today | Place | Shown as | Interaction | Echo elsewhere |
| --- | --- | --- | --- | --- |
| Placed app, folder, widget (the pool, `home.grid`, `apps.favourite`) | Home | Icon grid, in pages | Tap launches; long-press enters edit; drag moves or makes a folder | Pins in the Dock |
| Dock pins (`PoolDock`) | Dock, pinned side | Icon row | Tap launches; drag to reorder or out to Home | None |
| All apps (`apps.all`) | Library | Categories, A–Z or Grid | Tap launches; long-press for shortcuts, Add to Home, Pin to Dock, Hide; drag out to Home | None |
| Recent and frequent apps (`apps.recent`, `apps.frequent`) | Stream, Recents | Icon row, or rows with last-used time | Tap launches | None |
| App shortcuts (`shortcuts`) | No Place: the long-press menu of any app icon | Menu rows | Tap runs it; drag a row out to place it on Home | A placed shortcut is a Home item |
| Search (`search`) | Library | The search field; results replace the app list | Type; Enter launches the top hit | Swipe down on Home |
| Notifications (`notifications`) | Stream, Notifications | One stack per app, or grouped rows | Tap opens; swipe dismisses; expand to reply; long-press for Hide and rules | Dock dynamic side, icon badges |
| Media (`media`) | Stream, Now | One card | Play, pause, skip, scrub; expand for the queue; tap opens the player | Dock dynamic entry while playing; glance widget |
| Calendar and calendar feeds (`calendar`, `ics`) | Stream, Now (next event) and Agenda (by day) | Card or rows | Tap opens the event; expand for details | Glance widget |
| News feeds (`rss`) | Stream, Feeds | One stack per feed, or headlines | Tap opens the article; swipe marks read | Glance widget (top story) |
| Alarm (`LauncherCardKind.ALARM`, declared, unwired) | Stream, Now | A row beside the next event | Tap opens the clock app | None |
| To-do (`LauncherCardKind.TASK`, declared, unwired) | Stream, Tasks | Card of today's items, or rows | Tap ticks it off; tap the title to open; quick-add at the top | None |
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
- **No idle animation.** The spotlight keeps its existing behaviour: it requests frames only while its
  sources change, settles, and then stops. This satisfies "nothing animates without cause" in
  `design-language.md`.
- **Tint** comes from two places only: the wallpaper for chrome, and an app's own colour for that app's
  cards, stacks and spotlight.
- **Fallbacks are part of the material, not separate looks.** Reduced transparency is one top-level
  appearance setting honoured by every glass surface (opaque tint, no backdrop sampling, sheen kept faint).
  Reduced motion makes the spotlight snap. Below the API levels the shader needs, the existing blurred-tint
  and scrim fallbacks apply.
- **Removed:** the `MATERIAL`, `MINIMAL`, `RETRO`, `TERMINAL` and `CUSTOM` theme presets; the per-workspace
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

| Package or concept | Verdict | Becomes |
| --- | --- | --- |
| `workspace/sources`, shared source streams | Keep | Internal plumbing; each source emits its own type |
| `workspace/pool` | Keep | The Home and Dock model |
| `workspace/dock`, `dockpull` | Keep | The Dock; the pull opens the Library or the Stream by side |
| `cards` (stack physics, travel, magnet) | Keep | The Stream's Stack view |
| `expressions` | Keep | The Views, each bound to its Place |
| `workspace/exclusions`, privacy redaction | Keep | Per-type Hide rules |
| `designsystem` | Keep and extend | The glass material and tokens |
| `workspace/lens` | Demote | One fixed default per section, plus two or three options; never shown |
| `workspace/container` (page-set, widget grid) | Demote | Page-set becomes "one stack per app or feed"; widget grid becomes the glance widgets |
| `Item` | Replace | The six content types |
| `workspace/editor`, `workspace/wizard` | Cut | "Show as" in place, and Settings per Place |
| Saved lenses, lens library, advanced builder, per-lens search queries | Cut | Nothing |
| `workspace/preset` (iOS, Nova, TimeScape, Niagara, Kvaesitso) | Cut | One default arrangement |
| `workspace/menu`, multiple workspaces | Cut; stored workspaces stay decodable | One arrangement per posture |
| Skin override, non-glass theme presets | Cut | Glass |
| Onboarding walkthrough | Rework | Four steps, one per Place |

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
- **Per-type work replaces generic work.** Ten card and row renderers are hand-built. This is the intended
  trade: fewer combinations, each one designed.
- **One provider dependency.** Tasks need a Todoist personal token. It is entered in Settings, stored
  encrypted on the device, never backed up and never shipped.
- **Device validation is a gate.** No slice that changes a visual surface is done without rendered evidence
  (JVM screenshots), as Decision 8 of the earlier plan already requires. Motion and the Dock pull need a
  device pass by the owner.
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
`workspaces-ics-*.md`, `workspaces-sources-rss-search.md`, `rss-*.md`), `dock.md` and `gestures.md` (both
to be updated by the Dock slice), and `design-language.md` (to be updated by the material slice).

## References

- `docs/product/workspaces-sources-lenses.md`, `docs/product/modes-dock-handle-and-cards-plan.md`
- `docs/product/design-language.md`, `docs/product/dock.md`, `docs/product/gestures.md`
- `docs/development/workspaces-dogfood.md`
- Dock spotlight and sheen: #1442, #1443, #1444, #1445
- `BarMal/Calm` (`PageSlot`, `CategoryDisplayMode`), `BarMal/dotfiles` (`docs/shell-design-plan.md`)
- ADR 0002 (platform gesture primitives), which the Dock pull and the split divider must follow
