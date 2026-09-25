# The dock

The dock is Riffle's central shortcut and recents surface, and the handle that moves between Home
and Library. There is **one dock per device class: its content is shared by both modes, its edge
is chosen per mode**. This document is the target it is being built toward and the honest state of
it today; where the two differ, the target wins.

## What the dock is

A single strip, anchored to one edge of the screen, holding two sections:

- a **static** section of items the user pinned, which always show; and
- a **dynamic** section of items that appear because something arrived.

Pulling it away from its edge switches between Home and Library (see
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

The dock pull is the only way to move between Home and Library (Decisions 3, 4, 9 in
`modes-dock-handle-and-cards-plan.md`; #1207):

- **Natural pull direction**: away from the dock's edge, toward the screen interior — a bottom
  dock pulls up, a top dock down, a left dock right, a right dock left.
- The pull starts on the dock body, never in the system-gesture inset; it tracks 1:1 and commits
  past a dp distance or velocity threshold, otherwise springs back. It is interruptible.
- During the pull the dock background fades with progress and its icons re-orient to the target
  mode's orientation; on settle the dock sits at the target mode's edge and its background fades
  back in. Reduced motion: a short crossfade.
- Drags along the run still scroll the sections; long-press + drag still reorders or drags out.
- Accessibility: a dock custom action ("Switch to Library" / "Switch to Home") and a keyboard
  shortcut (Ctrl + the arrow of the pull direction) perform the same switch.

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

The **static** section is what the user pinned. It always shows. **A tap opens the app, in every
mode** -- muscle memory does not change with the mode (#1212). A long-press opens the item's menu;
the active mode may add items to it. In Cards those are **Show stage** (when the app has a stage)
and **Pin stage** (when its stage is not pinned yet), ahead of the app's own shortcuts.

The **dynamic** section's meaning depends on the mode:

- in grid modes it shows an entry when **a notification has arrived** for an app the dock is not
  already showing, and a tap opens that app -- there is nowhere on the launcher for its content. It
  is opt-in, per device class;
- in Cards it is **the stage selector** (Decision 5): "All" is always the first entry, then every
  stage in the planner's order -- pinned stages in pin order, then the rest newest first, including
  a pinned stage with nothing in it. A tap brings that stage (or the merged view) forward. The entry
  showing carries a selection ring and `selected` semantics, and is scrolled into view whenever the
  selection changes. It shows whether or not the grid-mode notification switch is on, because in
  Cards it is navigation rather than a notification list. A "Now" entry (#1216) will go ahead of
  "All"; `CardsStageSelector.leadingEntries` is where it plugs in.

Selecting a stage from anywhere -- the selector, **Show stage**, a spine chip -- leaves the merged
view. Opening an app stays on the stage's header overflow as well as on the icon itself.

The two sections are sized from two independent, per-layout settings rather than negotiating a
shared run between them (both per device class, shared by every mode): **capacity** caps how many pinned icons show before the static side
scrolls, and **notification slot count** caps how many notification icons show before the dynamic
section scrolls. Neither setting shrinks the other -- a dock busy with pinned apps never squeezes
notifications out, and a dock with several notifications never shoves the pinned icons along. A
settings-screen summary states the resulting total and its split in one line (for example, "Shows
up to 10 icons: 7 pinned, 3 for notifications") so the two sliders read as one budget.

Each section scrolls on its own: the static side scrolls independently when pinned items exceed
capacity, and the dynamic section scrolls independently when notifications exceed the slot count.
Fewer notifications than the slot count shrinks the section instead of padding it out to a fixed
width -- an entry is always exactly a pinned icon's size, never bigger or smaller to fit the space.

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
| Dock pull switches Home ↔ Library, dock re-orients to the target edge | Done (#1206, #1207) — `dockPullInput` on the dock body drives `DockPullTransitionController`; the dock and outgoing surface track the finger, the incoming surface follows, the background fades and returns, and a commit moves the dock to the destination's edge and orientation before the shell switches mode. Reduced motion crossfades. Accessibility action and Ctrl+arrow shortcut are equivalents. See gestures.md |
| Alternative triggers deleted (drawer swipe, three-finger mode gestures, dock swipe-up) | Done (#1241, first slice) — collapsing the mode ring to Home ↔ Library still pending |
| Panel expansion disabled behind a flag | Done (#1241) — `DockShelfExpansion.enabled` |
| Anchors to any edge, space reserved | Done for grid modes (#1148–#1152, #1165) and for Cards — both resolve through `resolveDockPosition`, and `dockInteractionRegionExtentDp` reserves a width for a side edge, a height for top/bottom |
| Default edge per device class | Done for the standard dock (#1165) — phone bottom, wide left-edge rail, a chosen edge wins. Cards follows the same resolution now |
| Sized by settings | Done |
| Static section | Done |
| Dynamic section exists, opt-in per layout | Done (#1154), gated on the existing per-layout switch |
| Dynamic section means "a notification arrived" | Done (#1162) — de-duplicated against the static side in every mode |
| Static tap opens the app in every mode; Cards stage on long-press | Done (#1212) |
| Cards dynamic section is the stage selector ("All" first, every stage, selection ring, auto-scroll) | Done (#1212) behind the neutral `Delegate` intent; "Now" entry pending #1216 |
| Static and dynamic sections are sized independently | Done — separate per-layout settings (capacity, notification slot count), neither shrinks the other |
| Settings summarise the total and its split | Done — one line at the top of the dock settings section |
| Merged All-notifications view reachable | Done (#1212) — always the selector's first entry; optional in the compact swipe order |
| Visible-before-overflow, scroll for the rest | Done — each section scrolls independently within its own setting |
| Multiple rows | **Not started** — no notion of rows exists |
| Panel exists, standard conventions | Done — a real `LauncherPage` on the same grid machinery as a home page |
| Cards expanded shelf is panel-only | Done (#1166) — the notification card row is dropped there, the panel stays |
| Shelf expansion | **Switched off** (plan Decision 11) behind `DockShelfExpansion.enabled`; code kept |
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
- **The selector's width budget.** In Cards the dynamic section lists every stage but is still sized
  by the notification slot count, so most stages are reached by scrolling it. Whether Cards should
  get its own, larger budget wants hands-on time.

## Change checklist

When changing the dock, check that: the two sections still read as one strip; neither section's
setting shrinks the other's; nothing shrinks below a pinned icon's size to fit; the behaviour still
holds on every edge and in RTL; the pull still works in its natural direction on every edge; and a
layout that has the dynamic section switched off is unaffected.
