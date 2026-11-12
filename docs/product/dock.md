# The dock

> **P5 (2026-11-12, [ADR 0003](../architecture/adr/0003-places-and-views.md)).** The Dock is the handle of the
> Stream and the Library. Its two sides open their own worlds: the pinned side the static one (the Library), the dynamic
> side the live one (the Stream). The dynamic side is no longer a notification strip or a Cards stage selector: it holds
> live apps, now playing and the Stream button (see [The dynamic side](#the-dynamic-side-p5)). The pull no longer opens
> the workspace menu or switches Home and Library; see [The dock is the handle](#the-dock-is-the-handle). The S9 and
> Home/Library text below is kept as history where it is not restated.

The dock is Riffle's central shortcut and recents surface, and the handle that opens the workspace menu (before S9:
that moved between Home and Library). There is **one dock per device class: its content is shared by both modes, its edge
is chosen per mode**. This document is the target it is being built toward and the honest state of
it today; where the two differ, the target wins.

## What the dock is

A single strip, anchored to one edge of the screen, holding two sections:

- a **static** section of items the user pinned, which always show; and
- a **dynamic** section of items that appear because something arrived.

Pulling it away from its edge opens the Library or the Stream, by the side the pull started on (see
[The dock is the handle](#the-dock-is-the-handle)).

Swiping it open reveals a **panel**: a small home screen using standard conventions, for widgets
and shortcuts the user wants within reach without leaving where they are. Expansion is
**disabled for now** behind a feature flag (Decision 11 in `modes-dock-handle-and-cards-plan.md`),
so the pull is the only thing a drag away from the edge does.

That is the whole surface. Anything that looks like a second dock — a rail beside the cards, a
separate floating bar over other apps — is the same dock in a different posture, or it should not
exist.

## Target behaviour

### One dock per device class: shared content, per-mode edge

The dock is available in every view mode, and its content is the **same** in each: the same pinned
items, size, appearance and dynamic-section budgets (#1205). `HomeLayoutSet.docks` holds one
`DockModel` per device class. The one thing stored per mode is the dock's **edge**: Home and
Library each have their own (Decision 2), and they may be the same (#1242; Library draws its own stored
edge, but there is still one edge setting, which sets both). Pinning, reordering or
moving an item to or from home, and every dock setting, edits that one dock whatever mode it was
made in, so a pin made in Library shows in Cards and Standard at once.

It is per device class, not global, because an edge that suits a tablet wastes width on a phone in
portrait.

Anything that must differ by mode is derived from the active mode when the dock is drawn, never
stored. Today that is what the dynamic section lists and what its entries do, plus the extra items
on a pinned app's long-press menu (see below).

The dock itself stays mode-agnostic (Decision 2 in `modes-dock-handle-and-cards-plan.md`): it
renders neutral `DockDynamicEntry` values and reports neutral intents -- a pinned app tapped (always
opens it), a dynamic entry activated (`DockDynamicEntryIntent.Delegate` hands its key back), a
long-press menu item chosen (`DockItemMenuExtras`). It is drawn once, by `HomeDockHost` outside the
mode surface, and the active mode interprets it through the one host contract, `HomeDockInterpreter`:
which dynamic entries to show, what a delegated entry key means, what to add to a pinned app's menu,
how to route the actions the dock sends, and whether the expanded shelf keeps its notification row.
Grid modes use its defaults; Cards builds its own in `CardsDockInterpreter.kt`
(`rememberCardsDockInterpreter`), and nothing in the dock knows what a stage is.

Today the edge is shared, so the dock is offered only the edges every mode can draw it on — left,
right and bottom. Once the edge is per mode (#1242), each mode offers the edges its own surface can
draw, so the top edge returns for a Cards Home.

### The dock is the handle

Each side of the dock opens its own world (ADR 0003): the pinned side the Library, the dynamic side the Stream.

| Action on the dock | Result |
| --- | --- |
| Tap a pinned app | Launches it |
| Pull away from the edge, starting on the pinned side | Library (the app drawer until P7 reworks it) |
| Pull away from the edge, starting on the dynamic side | Stream, tracking the finger |
| The same pull on the dynamic side while the Stream shows | Home |
| Tap the Stream button | Switches to the Stream, and back to Home from it |
| Tap a live app | Stream, landed on that app's stack in the Notifications section |
| Tap now playing | Stream, landed on the Now section |

- **Natural pull direction**: away from the dock's edge, toward the screen interior -- a bottom dock pulls up, a top dock
  down, a left dock right, a right dock left. It works the same on every edge, and a dock with a moved edge (a workspace
  override) pulls away from that edge.
- **Which side.** The side is where the finger landed, against where the dynamic section is drawn (it reports its
  bounds); the divider counts as pinned, the padding at the dock's end as dynamic (`dockPullSideAt`). Without a Stream
  there is no dynamic side and every pull opens the Library.
- The pull starts on the dock body, never in the system-gesture inset, and is claimed past touch slop when it leads along
  the pull direction before the long-press timeout; drags along the run still scroll the sections and long-press + drag
  still reorders or drags out.
- **The Stream pull** tracks the finger 1:1 over the window's extent along the pull: the Stream slides in from the dock's
  edge while Home fades out under it. It commits on release by travel (30% of the travel) or velocity (600 dp/s toward the
  interior, once it has moved 8 dp), otherwise springs back; both live in `GestureThresholds`
  (`DOCK_PULL_*`) and `DockPullThresholds`. A finger that lands on a settling pull catches it and tracks from where it is.
  From the Stream the same pull slides it away toward the interior and returns Home. Under reduced motion nothing slides:
  Home and the Stream crossfade.
- **The Library pull** moves nothing with the finger; it opens the Library when its release commits by the same rules.
- The dock itself never moves, re-orients or fades with a pull: it is the handle you are holding.
- **Accessibility.** The dock (and the Home surface root, so they exist when a layout hides the dock) offers the custom
  actions "Open Stream" (or "Back to Home" while the Stream shows) and "Open Library"; **Ctrl + the arrow of the pull
  direction** opens the Stream (or returns Home), **Ctrl + Shift + that arrow** opens the Library. Every dynamic entry is
  a 48 dp target with a spoken label ("Messages, 3 new", "Now playing: <title>", "Stream"); the Stream button is selected
  while the Stream shows.
- Home gestures can be bound to "Stream" (`OPEN_STREAM`) and "Library" (`OPEN_LIBRARY`); neither is bound by default.
- The workspace menu is no longer reached by the pull. Its "Workspace menu" accessibility action stays on the dock and
  the Home root until the menu is removed (P8).
- A workspace may hide its dock only while a gesture opens the Stream or the Library (the stranding rule, restated for the
  new handle).
- Fallback, to be decided on a device: if the dynamic side is too narrow a target with one or two entries, the pull may
  start anywhere on the dock and choose the Library or the Stream by the half it started on.

### The dynamic side (P5)

The dock's second side fills itself, from what the Stream already shows -- so Hide rules, content levels and source
on/off apply and there is no second notification pipeline (`DockDynamicSide` in `core/domain`):

1. **Live apps**: the apps with something new, which are the groups of the Stream's Notifications section, newest first
   (ties by group key), at most **live app slots** of them (zero to five, default three). A pinned app can appear here
   too: tapping the pinned icon launches it, tapping the live entry opens the Stream on its stack. The entry carries the
   count as a badge.
2. **Now playing**: one entry while a media session is active (the Now section's media). It is not counted in the slots.
3. **The Stream button**: always last, so the handle exists when nothing is live.

Events, to-dos and feeds get no entry, because they are always present in the Stream. Each entry is a pinned icon's
size (never under a 48 dp target) and a spotlight source like the pinned icons; the side scrolls on its own when it has
more entries than room.

**Live app slots** (Settings > Dock) replaces "Dock notifications" and "Notification space", and the workspace dock
dynamic section. A dock stored before it decodes: notification cards on keeps the old slot count (clamped), off becomes
none, nothing stored takes the default.

**Cards.** The dock no longer lists Cards' stages; its stage chips (the spine) are always drawn, so every stage stays
reachable by touch.

#### Migrating per-mode docks

Layouts written before #1205 stored a dock per (mode × device class). On decode, each device class
takes the dock of the mode it was showing, so any conflicting setting takes that mode's value (a
top edge comes down to the bottom). Nothing pinned is dropped: an item only another mode's dock
(or dock panel) held is placed on that mode's own first home page with a free cell — or on the
showing mode's pages when it came from Cards, which draws no home grid — and whatever finds no free
cell is gathered into a **From dock** folder.

### Anchored, with space reserved for it

The dock anchors to any edge, and the space it takes is reserved rather than overlaid: a side dock
costs the workspace a column, a top or bottom dock costs it a row. Items are laid out along the
edge it sits on.

Until the user picks an edge, the dock takes its layout's default: the bottom on a phone, the left
edge — where the rail used to be — on a foldable, tablet or desktop, so a wide window opens with the
dock already standing in as its side rail. A chosen edge always wins over the default (#1165). The
edges are absolute (top/bottom/left/right), not layout-direction-relative: a dock the user places
stays where they put it rather than mirroring in a right-to-left locale.

### Sized by settings

Icon size, item spacing, corner radius, background alpha and sizing are per-device-class dock
settings, shared by every mode, and
the dock's extent follows from them rather than being fixed.

### Static and dynamic sections

The **static** section is what the user pinned. It always shows. **A tap opens the app, in every mode** -- muscle memory
does not change with the mode (#1212). A long-press opens the item's menu; the active mode may add items to it. In Cards
those are **Show stage** (when the app has a stage) and **Pin stage** (when its stage is not pinned yet), ahead of the
app's own shortcuts.

The **dynamic** section is the same in every mode: live apps, now playing and the Stream button, see
[The dynamic side](#the-dynamic-side-p5).

The two sections are sized from two independent, per-layout settings (both per device class, shared by every mode):
**capacity** caps how many pinned icons show before the static side scrolls, and **live app slots** (plus now playing and
the Stream button) caps how many dynamic entries show before that section scrolls. The run itself is one shared strip,
and the dynamic side goes first when it is tight: it always draws in full up to its slot count, and the static side is
sized from whatever room that leaves, scrolling the rest rather than the other way around -- except that the static side
always keeps room for at least one pinned icon, so a busy dynamic side can scroll the rest of the pinned icons away but
never make every pinned icon disappear. A settings-screen summary states the result in one line ("Shows up to 7 pinned
icons, then 3 live apps, now playing and the Stream button").

Each section scrolls on its own. Fewer entries than the slot count shrinks the section instead of padding it out to a
fixed width -- an entry is always exactly a pinned icon's size, never bigger or smaller to fit the space.

### The merged All-notifications view

Cards can show a single view merging every stage's notifications. Since #1212 it is always the first
entry of the Cards stage selector, on every posture. What remains a setting ("Swipe through All",
off by default) is whether swiping between stages on a compact window also passes through it. The
older per-posture "show on unfolded" switch is no longer read.

### The spine

The chip strip under the compact stack is optional and **off by default** (setting "Stage spine";
settings saved before it existed decode as off). When on, it leads with an "All" chip and scrolls
the selected chip into view. It is also drawn whatever the setting says whenever the dock cannot
host the selector -- the dock is switched off or hidden -- so no stage is ever unreachable by touch.

### Overflow and rows

The dock has a configurable number of items **visible before overflow**, and supports **multiple
rows**. Whatever does not fit is reached by scrolling, not by shrinking: an entry is always the size
of a pinned icon, because a dynamic entry a different size from the icon beside it is the seam the
single dock exists to remove.

### The panel

A gesture opens the dock into a panel — a single home screen using standard conventions, so
widgets and shortcuts are placed arbitrarily on a grid rather than into bespoke slots. It is
configurable for **rendered size**, **grid dimensions**, and **padding**. The expand gesture is
disabled behind a feature flag until it is revisited after the dock pull ships, because both claim
a drag away from the dock edge.

**Expansion is switched off for now** (plan Decision 11): `DockShelfExpansion.enabled`
defaults to false, so no dock opens its shelf -- by swipe or by button -- and the expansion settings
(expandable, "Open the shelf with", dock panel) are hidden. A swipe away from the dock edge does
nothing until the dock pull claims it as the mode switch. The shelf and panel code stays in place to
be revisited.

It is deliberately not where items past the visible-before-overflow count go; those scroll in the
dock's own strip. The panel is for things you consult or act on without leaving where you are.

In Cards the panel is the whole of the expanded shelf. The notification card row that used to sit
alongside it is gone (#1166): the stages already are the notifications, so the row showed them a
second time. What arrived still reaches the collapsed strip's dynamic section either way.

### Floating over other apps

The dock — the same dock, with the same two sections and the same panel — can float above other
apps. The separate overlay dock is superseded by this and is to be removed rather than maintained
alongside it.

### The Cards side rail

Superseded. The rail was a stage list beside the card stack on wide windows; the dock's dynamic
section does that job, so the rail is gone (#1159).

## Where the code is against this

| Target | State |
| --- | --- |
| One dock per device class, shared by every mode | Done (#1205): one `DockModel` per device class, per-mode docks migrated, and one `HomeDockHost` drawn outside the mode surface in `HomeDestination`, so a mode switch keeps the same dock instance in the same place. Each mode reads the dock through a `HomeDockInterpreter` (Cards: the stage selector and "Show stage"/"Pin stage" menu extras, #1212); the grid and Cards lay out in the room the host reserves. The dock's thickness and edge hold across a switch; its run follows what the mode puts on the dynamic side |
| Per-mode dock edge, content still shared | **In progress** (#1242) — model, persistence and migration done (#1246); `HomeDestination` draws the dock on Library's own edge in Library and reserves room on that edge (#1207). There is still one edge setting: choosing an edge sets it for both surfaces until the setting is split |
| Dock pull by side: Library from the pinned side, the Stream from the dynamic side | **Done (P5)** — `dockPullInput` on the dock body drives `DockPullState` (`DockPull.kt`), which holds a pure `DockPullDrag`; `dockPullSideAt` and `dockPullDestinationFor` (`core/domain/.../dockpull/`) decide the side and where it leads, `streamPullFrame` how the Stream and Home are drawn, `HomeDockPullBinding.kt` wires it. The Home ↔ Library re-orient pull (`DockPullTransitionController`, `ModePair.counterpart`) is deleted. See gestures.md |
| Re-orientation frost (dock itself, and the whole screen) | **Removed (P5)** — the dock no longer re-orients or moves with a pull, so there is nothing to frost |
| Alternative triggers deleted (drawer swipe, three-finger mode gestures, dock swipe-up) | Done (#1241) |
| Mode ring collapsed to a fixed Home ↔ Library pair per device class | Done (#1241) — `ModePair` (Home is Cards or Standard, Library the other side; `ModePair.counterpart` is where a pull leads), Settings > Layout > Modes > "Home screen: Cards / Standard"; stored `modeRings` and older `preferredModes`/`lastNonCardsModes` migrate on decode |
| Panel expansion disabled behind a flag | Done (#1241) — `DockShelfExpansion.enabled` |
| Anchors to any edge, space reserved | Done for grid modes (#1148–#1152, #1165) and for Cards — both resolve through `resolveDockPosition`, and `dockInteractionRegionExtentDp` reserves a width for a side edge, a height for top/bottom |
| Default edge per device class | Done for the standard dock (#1165) — phone bottom, wide left-edge rail, a chosen edge wins. Cards follows the same resolution now |
| Sized by settings | Done |
| Static section | Done |
| Dynamic side exists in every layout | **Done (P5)** — live apps, now playing and the Stream button; a dock with no Stream host has none |
| Dynamic side means "something new, now playing, and the way to the Stream" | **Done (P5)** — derived from the Stream's Notifications and Now sections (`DockDynamicSide`), so Hide rules and privacy already apply; not de-duplicated against pinned apps, because a live entry opens the Stream and a pinned icon launches the app |
| Static tap opens the app in every mode; Cards stage on long-press | Done (#1212) |
| Cards dynamic section is the stage selector | **Removed (P5)** — the dynamic side is the same in every mode; Cards' stage chips are always drawn |
| Static and dynamic sections are sized independently | Done — separate per-layout settings (capacity, live app slots); the dynamic side draws in full first, the static side is sized from what is left |
| Settings summarise the total and its split | Done — one line at the top of the dock settings section |
| Merged All-notifications view reachable | Done (#1212) — always the selector's first entry; optional in the compact swipe order |
| Visible-before-overflow, scroll for the rest | Done — each section scrolls independently within its own setting |
| Multiple rows | **Not started** — no notion of rows exists |
| Panel exists, standard conventions | Done — a real `LauncherPage` on the same grid machinery as a home page |
| Cards expanded shelf is panel-only | Done (#1166) — the notification card row is dropped there, the panel stays |
| Shelf expansion | **Switched off** (plan Decision 11) behind `DockShelfExpansion.enabled`; code kept |
| Workspace menu from the dock | **Retired (P5)** — the pull and the Ctrl+arrow shortcut no longer open it; its "Workspace menu" accessibility action (dock and home root) stays until P8 removes the menu |
| Per-workspace dock overrides on the home | **Done** (S9): `withWorkspaceHome` applies `EffectiveDock` to the dock the home draws (render only; Settings > Dock edits the shared dock) |
| Dock swipe-up gesture action | **Removed** (plan Decision 9) — the dock pull will be the only mode-transition trigger |
| Panel configurable: size, grid, padding | **Not started** |
| Panel editing: drag in from the picker | **Not started** — needs a non-fullscreen picker so the dock stays visible |
| Dock floats over other apps | **Not started** — a separate overlay dock subsystem exists and is to be replaced |
| Rail superseded | Done (#1159) |

## Unresolved

- **Resolved (#1212): a pinned app's tap no longer changes meaning in Cards.** It always opens the
  app; its stage is on the long-press menu and in the selector.
- **The rail's per-stage snippet.** The rail showed a line of the most recent card per stage. A dock
  entry is one icon wide and has nowhere to put it, so that is lost with no replacement.
- **A narrow dynamic side (P5).** With one or two entries the dynamic side is a small target for a pull. If it proves
  too narrow on a device, the pull may start anywhere on the dock and choose its destination by the half it started on.
- **No de-duplication.** A live app that is also pinned shows on both sides; the two taps differ (launch vs. the Stream
  on its stack). Revisit if it reads as the same app twice.

## Change checklist

When changing the dock, check that: the two sections still read as one strip; the dynamic section
still draws in full before the static side is sized; nothing shrinks below a pinned icon's size to
fit; the behaviour still holds on every edge and in RTL; the pull still works in its natural
direction on every edge; the Stream button is still last; and a layout with no live app slots still has its Stream button
and now playing.
