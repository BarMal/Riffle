# The dock

> **Dock modes, slice 1 (2026-11-13, [ADR 0003 Amendment 3](../architecture/adr/0003-places-and-views.md); plan:
> [dock-modes-plan.md](dock-modes-plan.md)).** The Dock has two modes and one order of icons: the **Home mode** (pinned
> apps, a divider, live apps, now playing) and the **contextual mode** while the Stream shows (the same icons, then a
> divider and a tile for each Stream page that is not an app, in one row that scrolls). **Pressing the Dock down and
> releasing** toggles between them; there is **no Stream button** and the Stream has no spine or page header, so the Dock
> is the only page indicator. See [The two modes](#the-two-modes-dock-modes-slice-1). The pull away from the edge now
> opens the Library from anywhere on the Dock. Where the text below still describes the Stream button, the Stream pull,
> a swipe up on a live app or the two sides, it is superseded by that section and kept as history. **Slice 2** draws the
> press: the Dock dips under the finger, bounces on release, the Stream's cards fan out of it and closing plays the
> opening backwards ("The trampoline and the fan" in that section).

> **P5 (2026-11-12, [ADR 0003](../architecture/adr/0003-places-and-views.md)).** The Dock is the handle of the
> Stream and the Library. Its two sides open their own worlds: the pinned side the static one (the Library), the dynamic
> side the live one (the Stream). The dynamic side is no longer a notification strip or a Cards stage selector: it holds
> live apps, now playing and the Stream button (see [The dynamic side](#the-dynamic-side-p5)). The pull no longer opens
> the workspace menu or switches Home and Library; see [The dock is the handle](#the-dock-is-the-handle). The S9 and
> Home/Library text below is kept as history where it is not restated.

> **P8 and the dock fix (2026-11-12).** The Cards home mode, the workspace menu and workspace switching are deleted, so
> every mention of Cards (its stage selector, side rail, interpreter, merged All view) and of the workspace menu below
> describes code that no longer exists, and the Home/Library mode pair is no longer a user concept. The one dock is the
> pool's dock (`PoolDock`). Per-workspace dock overrides are no longer applied; the only trace is the one-time restore of
> size and edge (see "Restored size").

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

### The two modes (dock modes, slice 1)

One Dock, two modes (ADR 0003, Amendment 3). Nothing swaps: icons shared by both modes keep their order, and the contextual
mode only adds tiles after them and scrolls the row along.

| Mode | The Dock holds | Scrolls |
| --- | --- | --- |
| Home (Home, or the Library over it) | Pinned apps, a divider, live apps (apps with notifications) and now playing | As before: the pinned run and the dynamic side scroll on their own when they do not fit |
| Contextual (the Stream shows) | The same icons in the same order, then a divider and one tile for each Stream page that is not an app | Yes: one row, sideways (along the run on a side Dock), with the platform's fling |

- **Tiles.** One per built-in page: Now (unless now playing is shown, which stands for it), Recents, Agenda, Tasks and
  Feeds, in the Stream layout's order (Settings > Stream, or Move earlier / later on a tile). Notifications has a tile
  only while it holds just a state ("No notifications", "Allow access"), so that page and its action stay reachable.
  Agenda and Tasks are still two tiles. A page with several pages (one per feed) has one tile, which opens its first.
  Pinned apps with nothing live have no page yet and are neither ringed nor selectable as pages; a tap on them opens the
  app.
- **The open page's icon is ringed**, and the row scrolls to keep it in view (`DockRunScroll.toReveal`, with a margin so
  its neighbour peeks), again whenever the page changes by a tap or a swipe of the Stream.
- **Stream pages follow Dock order.** The Stream's pager order is the Dock's: the live apps newest first (the first
  "live app slots" of them), the pages of apps beyond the slots, now playing's Now page, then the built-in pages in layout
  order (`DockPageOrder`). Swiping the pager moves the ring when the swipe passes halfway and the Dock scrolls to keep it
  in view. The Stream opens on the page it was last left on, else on the first page it shows when it opens (Now, in the default layout: its data arrives after it opens, and the page in front stays in front while it does).
- **Tap.** Home mode: a pinned app, live app or now playing launches its app, as before. Contextual mode: a live app,
  now playing or a tile switches the Stream to that page (it never launches).
- **Long-press.** Home mode keeps the live app menu (Open in Stream, the app's shortcuts, App info, Hide app) and the pinned
  menu. Contextual mode: the page options that used to live on the page header, on the page's icon or tile: **Show as**
  the other view (Stack or List), **Move earlier / later** (tiles only; it steps over sections that draw no tile), **Hide
  section**, and a **Show <section>** for each hidden one; a live app's menu then continues with its shortcuts, App info
  and Hide app. The options are applied by the Stream, so each keeps its Undo.
- **Press the Dock down and release** (a drag toward the Dock's own edge, past `DOCK_PRESS_COMMIT_DP` = 28 dp, then lift;
  `DockPress`) toggles the modes with the trampoline and the fan (below). From Home it opens the
  Stream on the page of the icon the press started on when that is a live app or now playing, else the page it was last
  left on; from the contextual mode it returns to Home. A press that does not pass the threshold, or is drawn back before
  the lift, bounces back and does nothing else. Only with a Stream; not while the layout is being edited or the Library
  shows. The press reports its start, its travel and its release (`DockPressState.onBegan`, `onMoved`, `onReleased`) to
  the Dock's motion (`DockMotionState`), which draws the dip and Home's first fade.
- **Accessibility.** The Dock (and the surface root) offers the custom actions **"Show live pages"** (or **"Back to
  Home"** while the Stream shows) and "Open Library"; **Ctrl + the arrow of the pull direction** does the first, with Shift
  the second. In the Home mode a live app or now playing keeps "Show <app> in Stream" (the press that starts on the icon)
  and Shift + Enter. Every icon and tile is a focusable 48 dp target with a spoken label and a click action ("Show Agenda");
  the ringed one is selected.

### The Dock's gestures

| Action on the dock | Result |
| --- | --- |
| Tap a pinned app | Launches it |
| Pull away from the edge, anywhere on the Dock | Library, tracking the finger |
| Press the Dock down and release (Home mode) | Stream, on the last page (on that icon's page when the press started on a live app) |
| Press the Dock down and release (contextual mode) | Home |
| Tap a live app (Home mode) | Launches the app (the same launch as a pinned icon, in its own profile) |
| Tap a live app or a tile (contextual mode) | Switches the Stream to its page |
| Drag along the run (contextual mode) | Scrolls the row, with fling |
| Long-press a live app | Home: Open in Stream, the app's shortcuts, App info, Hide app. Contextual: the page options, then the app's |
| Tap now playing | Launches the player app (Home mode) |

- **Natural pull direction**: away from the dock's edge, toward the screen interior -- a bottom dock pulls up, a top dock
  down, a left dock right, a right dock left. It works the same on every edge, and a dock with a moved edge (a workspace
  override) pulls away from that edge.
- **Where it starts.** The pull and the press start on the dock body, never in the system-gesture inset. A drag is claimed
  past touch slop and before the long-press timeout (so a held press stays a long-press): mostly along the pull direction
  it is a pull (`dockPullClaimFor`), mostly the opposite way a press (`DockPress.claimFor`); drags along the run still
  scroll the Dock and long-press + drag still reorders or drags out. A press also consumes its touches on the Initial
  pass so a tile it started on cannot take the release for a tap. Both live in the one pointer loop of `DockPull.kt`, which
  ADR 0002 allows because no platform drag detector claims by direction in both directions; the row's scroll and fling are
  the platform's.
- **The pull** (always the Library now) tracks the finger 1:1 over the window's extent along the pull and commits on
  release by travel (30% of the travel) or velocity (600 dp/s toward the interior, once it has moved 8 dp), otherwise
  springs back; both live in `GestureThresholds` (`DOCK_PULL_*`) and `DockPullThresholds`. A finger that lands on a settling
  pull catches it. Without a Stream the press is off, and the pull is the only gesture.
- **The trampoline and the fan** (dock modes slice 2; ADR 0003, Amendment 3, Motion). Pressing dips the Dock under the
  finger by `24 dp * (1 - exp(-travel / 60 dp))` (rising resistance) while Home begins to fade (to at most 30%); pressing
  in the contextual mode only dips the Dock, the cards stay. Released past the threshold the Dock springs back past rest
  and settles (under-damped), the progress spring (critically damped, with a slight magnet near its end) carries the
  cards: they come out of the Dock one after the other, front card first, each rising, fading in and growing from 82% to
  full size, and Home fades out with a slight scale-down. A page that is not a stack of cards (a List view, an empty or
  state page) rises as one piece with the front card's timing; the first three cards of a stack fan, the rest use the
  third's timing. **Closing is the opening played backwards in time**: the opening is simulated and its samples played
  last to first, so the cards gather speed into the Dock, the card that came out last goes in first and the Dock's bounce
  runs in reverse and ends in the dip; then the Dock rises to rest while Home fades back in (the shell is told to go
  Home once Home is back). A press that stops short bounces back. The press, the accessibility actions, the shortcut and
  a tap on a live app all run it (the actions from rest with a small dip and kick instead of a finger). It is
  interruptible: a finger that claims the Dock while it runs catches it where it is (nothing snaps), and whatever
  follows, a deep release, a shallow press or an action, runs on from there; what the shell has been told is always
  consistent (opening: told at the release; closing: told when it has finished). Under reduced motion all of this is one
  80 ms cross-fade: no dip, no pre-fade, no bounce, no fan. Physics, curves and constants (speed 200 ms, trampoline 50%,
  magnet 40%, to be exposed by the settings slice) are `core/domain/.../dock/DockMotion.kt` and `DockClosing.kt`; the UI
  is `DockMotion.kt`, `DockFanSource.kt` and the Stream's `dockFan` modifier. Expanded postures keep the split's settle
  (below) until the unfolded layout is built.
- **Expanded postures (P6).** On an unfolded foldable, a tablet or a desktop window Home and the Stream can share the
  screen (Home only, a split, the Stream only: `SplitMode`). The press (and the accessibility action) opens the last-used
  shown state and returns to Home only; the divider's own actions choose among the three. The pull no longer settles among
  the three states, because it only opens the Library (the split's pull settle is no longer started by a gesture). See
  [The split](#the-split-p6).
- **The Library pull (P7)** raises the Library's sheet from the dock's edge, 1:1 with the finger, while Home or the Stream
  fades out under it; it commits by the same thresholds, can be caught mid-settle, and under reduced motion the sheet
  fades in. The Dock stays drawn and tappable under the sheet. See [The Library](gestures.md#the-library-p7).
- The dock never moves, re-orients or fades with a pull; it dips and bounces with a press (above).
- Home gestures can be bound to "Stream" (`OPEN_STREAM`) and "Library" (`OPEN_LIBRARY`); neither is bound by default.
  "Search" (`OPEN_SEARCH`, bound to swipe down by default) opens the Library with its field focused.
- The workspace menu is no longer reached by the pull, and since P8 it and its "Workspace menu" accessibility action no
  longer exist.
- A workspace may hide its dock only while a gesture opens the Stream or the Library (the stranding rule, restated for the
  new handle).
- A Dock with no pinned apps and nothing live still has a body to press: it keeps one icon's length.

### The split (P6)

On expanded postures (an unfolded foldable, a tablet, a desktop window) Home and the Stream can share the screen with a
movable divider (ADR 0003, Folded and unfolded). Three states: **Home only**, **split**, **Stream only**. The shell still
knows only "Stream shown or not"; whether a shown Stream is a split or the Stream alone is the user's last choice on that
posture (`StreamSplit.lastShown`), stored with the split's fraction in `StreamLayout` (per posture, with the rest of the
Stream's layout and its backup; layouts stored before P6 read as a 40% split). The pure logic lives in
`core/domain/.../launcher/split/`; the UI is `launcher/split/SplitSurface.kt`.

- **Which side the Stream takes.** For a bottom or top dock, the side nearer the dock's dynamic side. For a side dock the Stream takes the side **away from** the dock,
  so Home keeps the dock's edge. In a stacked (tabletop) split the Stream takes the pane nearer a bottom or top dock, and
  the lower pane for a side dock.
- **Room.** The divider moves in the window minus the system insets and the dock's room on the split axis. Each pane keeps
  at least 280 dp and the Stream between 25% and 75% of the room; narrower than two panes and a gap, there is no split and
  the press goes between Home only and Stream only. The Stream's surface stops at the dock's room (Home
  pads for it itself), so what it draws is the same from a split to the Stream only.
- **The hinge.** A vertical hinge splits side by side, a horizontal one (tabletop) stacks the panes. The divider snaps to
  the hinge when within 32 dp of it, and a pane that would cover the hinge is cut back to its larger side, so content
  never crosses it. Home only and the Stream only fill the window as they do on a flat screen.
- **The divider.** A 48 dp target with a thin glass handle, moved with the platform `draggable` (ADR 0002). On release it
  settles to the nearest of the three states by position and velocity; past the split's limits it goes to Home only
  (which asks the shell for Home) or the Stream only. It is focusable with the spoken label "Divider between Home and
  Stream" and a value ("Split, Stream 40 percent"); the arrow keys along the axis move it 5% (and past the end step to
  the next state); the accessibility actions "Home only", "Split" (the default fraction, or the hinge) and "Stream only"
  do the same without a gesture; it also exposes a progress value. Reduced motion snaps instead of animating.
- **Equivalents.** The dock's "Show live pages" / "Back to Home" actions and Ctrl + the pull arrow open the last-used
  shown state or return to Home only.

### The dynamic side (P5)

The dock's second side fills itself, from what the Stream already shows -- so Hide rules, content levels and source
on/off apply and there is no second notification pipeline (`DockDynamicSide` in `core/domain`):

1. **Live apps**: the apps with something new, which are the groups of the Stream's Notifications section, newest first
   (ties by group key), at most **live app slots** of them (zero to five, default three). A pinned app can appear here
   too: either icon launches it when tapped (Home mode); a press that starts on the live entry opens the Stream on its
   stack. The entry carries the count as a badge.
2. **Now playing**: one entry while a media session is active (the Now section's media). It is not counted in the slots,
   and it stands for the Now page.
3. **Contextual tiles** (the contextual mode only): see [The two modes](#the-two-modes-dock-modes-slice-1). There is no
   Stream button any more.

Events, to-dos and feeds get no entry in the Home mode, because they are always present in the Stream. Each entry is a
compact 48 dp tile (the touch target, whatever size the pinned icons are) and a spotlight source like the pinned icons; the
side takes only the width its entries need, up to the slot limit, and scrolls on its own when it has more entries than room.
In the contextual mode the whole Dock is one row (`DockContextualRow`): the pinned icons at full size and spacing, a
divider, the live entries, a divider, the tiles; it is as long as that, up to the run the Dock may have, and scrolls the
rest (`DockRunGeometry` says where each item lies).

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
settings, shared by every mode, and the dock's extent follows from them rather than being fixed.

- **Pinned icon size.** A pinned icon is drawn at exactly the **Icon size** setting (32 to 56 dp), and the phone
  template's default (44 dp) is the Home grid's default icon size, so pins and Home icons match until you change either.
  Nothing ever shrinks a pinned icon: not a long run, not the dynamic side beside it.
- **Bar height.** The bar is one icon plus its chrome (32 dp) across its edge: 76 dp at 44, 88 dp at 56. It follows the
  setting, not the room along the run.
- **Gap.** When the pins do not quite fit, the gap between them gives first, down to 8 dp (or your own spacing setting
  when that is smaller); beyond that the run keeps its size and scrolls, so icons never touch.
- **Pinned run.** A run longer than its room **scrolls**, at full icon size, fading through the glass at the edge that
  has more (no fade on an edge that has nothing beyond it). Five pins fit on a phone at the template size; eight scroll.
- **Dynamic side width.** It takes only what its entries need (live apps and now playing, each a 48 dp
  tile with the pinned run's gap) up to the live-app-slot limit, plus the 17 dp rule. When room is short it never takes
  the pinned run's last three icons (fewer if the dock pins fewer) and the dock's padding; the pins scroll in the rest
  and the dynamic side scrolls in what it has.
- **Restored size.** Until the Dock rework the icon size of a workspace's dock override applied over the shared dock;
  the cut-over to one arrangement dropped it. At start the stored workspaces are read once and, where the shared dock
  still holds a default size (and no edge), the override's size (and edge) is carried into the shared dock; a size set
  since is never overwritten. A hidden override is not carried over.

### Static and dynamic sections

The **static** section is what the user pinned. It always shows. **A tap opens the app, in every mode** -- muscle memory
does not change with the mode (#1212). A long-press opens the item's menu; the active mode may add items to it. In Cards
those are **Show stage** (when the app has a stage) and **Pin stage** (when its stage is not pinned yet), ahead of the
app's own shortcuts.

The **dynamic** section is the same in every mode: live apps and now playing (and the contextual mode's tiles), see
[The dynamic side](#the-dynamic-side-p5).

The two sections are sized from two independent, per-layout settings (both per device class, shared by every mode):
**capacity** caps how many pinned icons show before the static side scrolls, and **live app slots** (plus now playing)
caps how many dynamic entries show before that section scrolls (the contextual mode's row is longer and scrolls as one). The run itself is one shared strip,
and the dynamic side goes first when it is tight: it always draws in full up to its slot count, and the static side is
sized from whatever room that leaves, scrolling the rest rather than the other way around -- except that the static side
always keeps room for at least three pinned icons (fewer when fewer are pinned), so a busy dynamic side can scroll the
rest of the pinned icons but never squeeze them or make them disappear. A settings-screen summary states the result in one line ("Shows up to 7 pinned
icons, then 3 live apps and now playing").

Each section scrolls on its own. Fewer entries than the slot count shrinks the section instead of padding it out to a
fixed width -- a pinned icon is always exactly the icon-size setting, and a dynamic entry always a 48 dp tile, never bigger
or smaller to fit the space.

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
be revisited. Were it switched on, a shelf that can open claims the drag before the pull (the pull yields to
a consumed drag; see `gestures.md`). The shelf's content includes the notification section whenever the dock
has live app slots (the default) -- a permission prompt until notification access is granted.

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
| Dynamic side exists in every layout | **Done (P5)** — live apps and now playing (the Stream button went in dock modes slice 1); a dock with no Stream host has none |
| Dynamic side means "something new and now playing" | **Done (P5)** — derived from the Stream's Notifications and Now sections (`DockDynamicSide`), so Hide rules and privacy already apply; not de-duplicated against pinned apps, because a live entry has a page and a pinned icon has none yet |
| Two modes, one order of icons; contextual tiles; press to toggle | **Done (dock modes slice 1)** — `DockMode`, `DockPageOrder`, `DockRing`, `DockPress`, `DockContextualRow`; no spine, page header or Stream button |
| The trampoline and the fan: dip, bounce, cards out of the Dock in sequence, closing reversed | **Done (dock modes slice 2)** — `DockMotion.kt` and `DockClosing.kt` (domain), `DockMotionState`, `DockFanSource`, the Stream's `dockFan`; see [The trampoline and the fan](#the-two-modes-dock-modes-slice-1) |
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
| Workspace menu from the dock | **Retired (P5), deleted (P8)** — the pull and the Ctrl+arrow shortcut stopped opening it in P5; the menu and its "Workspace menu" accessibility action went in P8 |
| Per-workspace dock overrides on the home | **Removed (P8)** — nothing applies a workspace's dock override; a one-time restore carries the stored size and edge into the shared dock |
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
direction on every edge; the press still toggles the modes on every edge; the contextual mode's icons are still the Home
mode's in the same order; and a layout with no live app slots still has now playing.

### Live app entries: tap opens the app, a press that starts on them opens their stack

Owner decisions, 2026-11-13: a live app entry behaves like the app, with the Stream a press away (replacing "swipe up",
which the dock-modes amendment dropped: opposite directions for "all cards" and "one app" felt wrong).

- **Tap** (Home mode) launches the app, by the same action a pinned icon uses (`LaunchApp` with the entry's own profile). An
  app the launcher cannot resolve has nothing to launch, so its tap still opens its page in the Stream. In the contextual
  mode a tap switches to the app's page.
- **Press the Dock down and release, starting on the icon,** opens the Stream landed on that app's stack. The entry under
  the finger is read from where it landed (`dockPullEntryAt`; each entry reports its bounds). The landing is asked for when
  the press commits and consumed once the Stream has scrolled there.
- **Now playing**: tap opens the player app; a press that starts on it opens the Stream on Now.
- **Long-press** on a live app or now playing: "Open in Stream", then the app's shortcuts, App info and Hide app (no
  Uninstall or dock edits: the entry is not pinned). In the contextual mode the page options come first.
- **Non-gesture routes**: the click action "Open <app>", the custom action "Show <app> in Stream", the menu's "Open in
  Stream" and Shift + Enter.
