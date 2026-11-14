# Gesture arbitration

Which part of the launcher owns a touch, and what it does with it, in each home mode:
**Std** (Standard, app drawer), **Lib** (Home screen library) and **Cards** (Adaptive Stage).
Std and Lib share the `StandardHome` surface, so they arbitrate identically; Cards composes the
dock as a sibling of the Adaptive Stage surface rather than inside it.

This page describes the behaviour after #1210, revised by the dock-pull decisions (see
"Mode transitions" below). The code it describes:

> **Dock modes, slice 1 (2026-11-13, [ADR 0003 Amendment 3](../architecture/adr/0003-places-and-views.md)).** The Dock has
> two modes. **Pressing the Dock down (toward its edge) past a short threshold and releasing** toggles Home and the
> contextual mode (the Stream showing); a press that starts on a live app's icon opens that app's page; a press that is
> held or slid chooses an icon (slice 3, "Hold to choose" below). The pull away from
> the edge now opens the Library from anywhere on the Dock. The Stream button, the swipe up on a live app and the Stream
> spine and page header are gone: in the contextual mode the Dock's row (scrolled sideways with fling) is the page
> indicator, a tap on a tile switches page, and the page options are on a long-press of the tile. See ["The Dock press
> (dock modes slice 1)"](#the-dock-press-dock-modes-slice-1). The older text below that describes them is history.

> **P5 (2026-11-12, [ADR 0003](../architecture/adr/0003-places-and-views.md)).** The Dock is the handle of the Stream and
> the Library. The dock pull is claimed by the same rules as before, but it now opens the Library when it starts on the
> dock's pinned side and the Stream when it starts on its dynamic side; it no longer opens the workspace menu and no
> longer switches Home and Library. See ["The Dock pull (P5)"](#the-dock-pull-p5). The older sections below are kept as
> history where they are not restated.

> **P8 (2026-11-12).** The Cards home mode, its stage pager and the workspace menu are deleted. Everything below that
> names Cards, a stage pager, a side rail of stacks, the workspace menu or the Home/Library mode pair is history; the
> arbitration that is live is the Std/Lib column (the standard Home surface), the Dock pull, the Library and the Stream.

> **P13 and P14 (2026-11-12).** The Stream is a horizontal pager of pages, each one vertically scrolling stack or list,
> with a spine as the page indicator; unfolded postures can show it as Pages (two side by side) or as the Stage view. See
> ["The Stream (P4, entry points restated by P5)"](#the-stream-p4-entry-points-restated-by-p5) and the arbitration table.

> **P7 (2026-11-12).** The Library is its own surface: a Thick glass sheet that rises over Home or the Stream with the
> Dock still drawn under it, no longer a workspace Finder page or the full-screen app drawer. Its pull now tracks the
> finger. See ["The Library (P7)"](#the-library-p7).

> **Plan revision 2026-10-01 (WS10, as built at S9, 2026-10-24).** Workspaces are the launcher's home and the
> Home/Library mode pair is no longer a user concept (`workspaces-configuration.md` 3.1, 11.5;
> [`workspaces-default.md`](workspaces-default.md)). The dock pull opened the workspace menu (owner decision Q9) until P5.

> **Plan revision 2026-09-25.** The dock pull becomes the only mode-transition trigger (Decisions
> 3, 9, 11 in `modes-dock-handle-and-cards-plan.md`). The alternative triggers are deleted and
> dock shelf expansion is switched off (#1241, below). The pull itself is built (#1206, #1207): a
> one-finger drag starting on the dock body in its natural pull direction (away from the dock edge)
> is owned by it.

| Recognizer | File | Primitive |
| --- | --- | --- |
| Home gestures (1/2/3-finger swipe, pinch) | `HomeGestureInput.kt`, `HomeSwipeGesture.kt` | Custom pointer loop (ADR 0002: no N-finger Foundation equivalent) + `nestedScroll` hand-off |
| Card stack scroll/fling (the Notification overview prototype) | `CardStack.kt` | `Modifier.scrollable` + custom `FlingBehavior` |
| The Stream's card stack (dock modes slice 4a) | `StreamStack.kt`, `StreamStackState.kt`, `StreamStackLayout.kt` | `Modifier.scrollable` (vertical; drag, slop, velocity and the axis decision are the platform's) + a custom `FlingBehavior` that hands the release to the stack's own spring. Custom where the platform has no notion: the float position rubber-banded past both ends, the magnetic spring, the tap on a peeking card. Decisions in `core/domain/.../stream/StackMotion.kt` |
| Home page pager (Std/Lib) | `ImmediateHomePager.kt` | `HorizontalPager` |
| Dock pull (Library) and Dock press (Home and contextual mode) | `DockPull.kt`, `DockPress.kt`, `HomeDockPullBinding.kt` | Custom pointer loop (ADR 0002: direction-selective claim in both directions); the one loop the Dock needs. Decisions (claim, commit, frame, press threshold) in `core/domain/.../dockpull/` and `.../dock/DockPress.kt` |
| Contextual Dock row scroll | `DockContextualRow.kt` | `horizontalScroll`/`verticalScroll` with the platform fling (ADR 0002); the reveal of the ringed icon is `DockRunScroll` |
| Dock shelf expand/collapse (switched off, see below) | `DockShelfGesture.kt` | Custom pointer loop (ADR 0002: direction-selective claim) |
| Dock run / dock sections | `HomeDock.kt`, `DockDynamicSection.kt`, `DockNotificationCards.kt` | `horizontalScroll`/`verticalScroll` |
| Page indicator scrub | `HomePageControls.kt` | `detectHorizontalDragGestures` |
| Arbitration decisions | `core/domain/.../gestures/`, `.../cards/CardStackOverscroll.kt` | Pure Kotlin, unit tested |

## The Dock press (dock modes slice 1)

The Dock has two gestures along the axis across its run, in opposite directions (ADR 0003, Amendment 3):

| Gesture | Direction | Result |
| --- | --- | --- |
| **Pull** | Away from the edge (a bottom dock pulls up, a top dock down, a left dock right, a right dock left) | The Library, tracking the finger, from anywhere on the Dock and from Home or the Stream |
| **Press** | Toward the edge (a bottom dock is pressed down), then release | Toggles Home and the contextual mode (the Stream): opens the Stream on the last page, or on the page of the live app / now playing icon the press started on; from the Stream returns Home |

- **Claim.** One finger on the dock body, past touch slop, before the long-press timeout. The drag is a pull when it leads
  along the pull direction (`dockPullClaimFor`) and a press when it leads the opposite way and more along that than across
  it (`DockPress.claimFor`); a drag along the run is the Dock's scroll (the contextual row scrolls, with fling), a held
  press is a long-press. The press is only claimed with a Stream, while the layout is browsed and the Library is not
  showing. The press is claimed on the Initial pass (ahead of the row's scroll and the tiles' handlers; the dominant axis when
  the drag leaves slop decides, so a diagonal thumb is still a press); the pull is claimed on the Main pass. Once claimed, the press consumes its touches on the Initial pass, so a tile it started on never takes the
  release for a tap.
- **Commit.** The release commits when the Dock is pressed down `DockPress.COMMIT_DP` = 28 dp (`GestureThresholds
  .DOCK_PRESS_COMMIT_DP`) at the moment of the lift, so a press drawn back before the lift does nothing. There is no
  velocity rule. A commit runs the trampoline and the fan (`DockMotionState`, see [the Dock](dock.md)): the Dock dips
  under the finger with rising resistance while Home starts to fade, bounces past rest on release while the Stream's cards
  fan out of it in sequence, front card first, and Home fades away; closing is the same played backwards in time; a
  shallow press just bounces back; a finger landing mid-way catches it and the next release or press runs on from there.
  Under reduced motion it is one short cross-fade (no dip, bounce or fan). An expanded posture still opens the last-used
  shown state (split or Stream only) and returns to Home only, by the split's settle.
- **What the press exposes**: `DockPressState.onBegan`, `onMoved(travelDp)` and `onReleased` (committed or not, whether
  it was choosing, and the entry it started on), `onChoosingBegan` and `onChoosingMoved` (hold to choose, below), and
  `progress` (0 to 1, read it in a draw block). The Dock's motion draws from them.
- **Hold to choose (dock modes slice 3).** The third depth of the one gesture, inside the same pointer loop (no new
  detector; ADR 0002). Once claimed, every sample of the finger goes to the pure `DockChoiceStart`; while the finger rests
  past the 28 dp threshold the wait for the next event is bounded (`withTimeoutOrNull`) by the moment holding starts
  choosing, so a still finger gets there without events. It starts when the finger has rested for the hold time
  (`DockMotionTuning.holdMillis`, 220 ms; "rested" is within 6 dp, moving restarts it) or slides more than 14 dp along the
  run past the threshold; never before the threshold, and drawing the press back above it first cancels the wait. **Resting
  only chooses from Home** (`DockChoiceStart.next(holdStarts)`, fed by `DockPullGestureHost.holdChooses`): with the Stream
  open only the slide does, so a slow press that rests on a tile closes the Stream however long it is held (a real press
  lasts about half a second, longer than the hold time; this was why the Dock did not return Home on a phone). From
  then on the press is a selection: the finger's place is what is followed, the lift commits to the selected item (any
  travel), and the release outcome is the one pure function `DockChoice.outcome`:

  | Release | Home mode | Contextual mode |
  | --- | --- | --- |
  | Quick press past the threshold | Opens the page of the icon the press started on, else the last page | Closes |
  | Short of the threshold | Nothing | Nothing |
  | Choosing, an item selected | Opens that item's page, with the fan | Switches to that page; the Stream stays out, unless the item is the one nearest where the finger landed: then it closes |
  | Choosing, nothing selectable | The page a quick press opens | Nothing |
  | Choosing, finger more than one Dock thickness beyond the Dock's inner edge | Nothing (back to Home) | Nothing |
  | Gesture taken away (not a lift) | Nothing | Nothing |

  The nearest selectable item is chosen by centre along the run, skipping items with no page (`DockChoice.pageFor`: live
  apps, now playing, tiles and (slice 4b) pinned apps have one); a finger within 15% of the row's length of an end (or
  beyond it) scrolls the row at 300 dp/s; a light haptic tick (`LauncherHapticEvent.DETENT`, honouring the haptics
  setting) sounds when choosing begins and when the selection lands on another item. The Dock draws it: the contextual row
  early (from Home), the ring and 14% growth on the selected tile, the name label above the Dock. Folded phone only for now.
- **Starting on an icon.** The entry under the finger is read from where it landed (`dockPullEntryAt` against the bounds
  each dynamic entry reports, and each pinned icon: `pin:<package>:<profile>`); a live app, now playing, a pinned app (its
  live page, else its shortcuts page) has a page, so a press that starts on one opens it; a press that starts on a tile
  or the background opens the last page. The landing is asked for at the commit and dropped if nothing opens.
- **Taps.** Home mode: a pinned app or a live app launches the app, now playing the player. Contextual mode: a pinned
  app (slice 4b), a live app, now playing or a tile switches the Stream to that page. The ringed icon is the open page's; swiping the Stream's
  pager moves the ring (when the swipe passes halfway) and the Dock scrolls to keep it in view.
- **Long-press.** Home mode: the app's menu with "Open in Stream". Contextual mode: the page options (Show as the other
  view, Move earlier / later on a tile, Hide section, Show <hidden section>), then the app's own items for a live app.
- **Equivalents (a non-gesture route for each).** Press: the Dock's (and the surface root's) accessibility action **"Show
  live pages"** (or **"Back to Home"** while the Stream shows) and Ctrl + the arrow of the pull direction; press on an icon:
  the click action and the custom action "Show <app> in Stream", the menu's "Open in Stream", Shift + Enter. Pull: "Open
  Library" and Ctrl + Shift + the arrow. Switching page: the tiles are focusable 48 dp targets with a spoken name and a
  click action ("Show Today"), and the pager's custom actions "Previous page" and "Next page". Page options: the tile's
  long-press menu (its own long-click action for assistive technology).
- **Expanded postures (P6).** The split's divider and its actions are unchanged; the pull no longer settles among Home only,
  split and Stream only (it only opens the Library), the press and the divider reach them.
- **Library pull (P7).** Unchanged: the sheet rises from the Dock's edge as the finger moves, 1:1, while what is under it
  fades; it commits by `DockPullThresholds` (30% of the travel or 600 dp/s toward the interior), can be caught mid-settle,
  and under reduced motion fades in. While the Library shows neither the pull nor the press is claimed.

### History: the Dock pull by side (P5, superseded)

Until dock modes slice 1 a pull toward the interior opened the Library from the pinned side and the Stream from the dynamic
side (tracking the finger, with the Stream button and a swipe up on a live app as its relatives). That text is gone from
this page; `docs/product/dock.md` keeps the history of the sides.

## The Stream (P4, entry points restated by P5)

The Stream (ADR 0003) is a full surface that replaces Home with the Dock still drawn under it. It is reached by the
Dock's press (above), the home gesture action **Stream** (`OPEN_STREAM`; no gesture is bound to it by default) and
the Dock's accessibility actions.

- **Close.** Back and the Home button (`OpenDefaultHome`) return to Home, as do the Dock press and "Back to Home". **An open
  card closes first** (dock modes slice 5, below): Back, a press of the Dock and "Back to Home" (which then reads "Close card") each close
  the open card and leave the Stream where it is; the next one returns Home.
- **Inside the Stream (P13, P14, dock modes slice 4a).** The Stream is a horizontal pager of pages and each page is one
  vertically scrolling stack or list ([places-views-plan.md](places-views-plan.md), P13). The pager is the platform
  `HorizontalPager` (`Orientation.Horizontal`, ADR 0002); a page's stack is `StreamStack`, bottom-anchored: the front card
  sits just above the Dock and older cards peek above it as the real card clipped to its top strip. Its drag is the
  platform `scrollable` (`Orientation.Vertical`), a List page is `verticalScroll`. The two never fight because
  they take different axes and the platform decides from the first movement: a drag that leads sideways is the pager's,
  one that leads up or down is the page's. A stack with one card draws no stack (a lone card is drawn where the front card
  is, and owns no drag). Nothing in the Stream consumes a vertical drag the stack leaves over, so the stack takes all of
  it: past the first or last card it rubber-bands (a third of the finger's travel, back along the same path) instead of
  handing the drag on. The Dock pull is claimed on the dock body only, so it never competes with either.
  - **The drag follows the finger**: 90 dp of drag moves the stack one card; dragging down brings older cards toward the
    front while the front card sinks toward the Dock, fading; dragging up brings the sunk card back out.
  - **Release**: the stack coasts to the card it would reach (`position + velocity * 0.18 s * inertia`, held to the stack),
    then a critically damped spring settles it on that whole card with a magnet that strengthens the pull within a quarter
    of a card. A finger that lands mid-coast catches it where it is (a `scrollable` whose scroll is in progress starts
    dragging at once and cancels the coast), and a move without a finger (a tap on a peeking card, the actions below) runs
    inside the same scroll, so a touch catches that too. A light tick (`LauncherHapticEvent.DETENT`, through the haptic
    strength setting) when it settles on a different card than it started on.
  - **Reduced motion**: dragging still follows the finger; on release the stack jumps to the card nearest to where the
    finger left it (no coast, spring or magnet) and fades from 40% to full opacity over the 80 ms reduced-motion fade.
  - **Tap**: a peeking card is one 52 dp target that brings it to the front (the card's own buttons are out of reach until
    it is in front); a tap on the front card of a conversation, the player, an event or a task **opens it in place**
    (below), and on a recent app, a shortcut or an article it acts (launches, opens the article) as before. The card sunk
    before the front card is not a strip, so it is reached with Previous card, a drag or the arrow keys.
  - **Open** (dock modes slice 5). Tapping the front card grows it in place from its browse size into its full content
    (`OpenCardFrame`, driven by the critically damped `OpenCardSpring`; reduced motion: no growth, an 80 ms fade) and the
    cards behind stay as peeking strips above it. While a card is open the stack takes no drag and the pager no swipe. It
    closes by a tap on it or beside it, **a press of the Dock** (the Dock dips and springs back; the Stream stays: the press
    is the card's first, `OpenCardHolder.closeByPress`), Back, or by the Stream coming to another page (a tile, the
    pager's actions) or the card going away. The state and its transitions are `OpenCardReducer` (`OpenCard.kt`). The
    Dock's press is still claimed when it starts on the actions row's empty space (the row is inside the Dock's gesture,
    decided on the Initial pass as before), and the row's controls take their own taps and text.
  - **The actions row** (setting "Where its actions sit": in the Dock, the default, or on the card). While a card is open the
    Dock gains a row above its icons (below them on a top Dock) holding that card's actions, on Thick glass, rising with the
    card and leaving with it: a conversation's reply field and send button, Dismiss and Snooze (as the notification offers
    them); the player's previous, play or pause, next and the queue control; "Open in calendar" (an alarm: "Open clock");
    Complete for a task; nothing for a recent app or a shortcut (they do not open). Every control is a real focusable control
    with a 48 dp target (`CardActionControls`), and the open card carries the same actions as accessibility actions
    (`cardActionAccessibility`: Dismiss, Snooze, the transport, the queue, Open in calendar, Complete). The Dock grows
    upward (it is anchored at its edge) and the room it reserves grows with it, so the Stream makes room and the open card
    is not covered; the keyboard raises the Dock and the card with it (both follow the window insets). A Dock down a side
    holds no row: the actions are on the card then (`StreamCardActions.effectiveActionsPlacement`), and so wherever there
    is no Dock to hold them (the unfolded split).
  Every gesture has a non-gesture equivalent:

  | Gesture | Equivalent |
  | --- | --- |
  | Swipe to the next or previous page | Tap the page's tile or live app in the Dock (contextual mode); the pager's custom actions "Previous page" and "Next page" |
  | Tap the front card to open it, press the Dock to close it | The front card's custom action "Open card" and Enter or Space; the open card's "Close card" click action and Back; the Dock's action "Close card" (it reads "Back to Home" when no card is open) |
  | Scroll a stack to another card | The stack's own custom actions "Previous card" and "Next card" (Next is an older card, drawn above) and the front card's "Open card"; the arrow keys (up and Page Up: older; down and Page Down: newer); a tap on a peeking card. There is no on-screen stepper (the "Card 2 of 3" pill with arrows was removed in slice 4a); the front card's state description says "Card 2 of 3" |
  | Long-press a page's tile or icon in the Dock for Show as, Move and Hide, and Show for each hidden section | The long-click custom action of the tile (assistive technology), the same menu |
  | Long-press the Stage's bar for the Stream's own menu: Show as Pages or Stage | The options button at its end (Settings > Stream chooses Pages or Stage too) |
  | Tap a Stage rail thumbnail | It is a tab (a button named "Bring Chat to the front") |

  Each page is a labelled pane, the pager carries one custom-actions list, and no node carries two (the page actions are
  on the pager, Next and Previous card on the stack, Open card on the front card, Hide on each card). The cards are
  individually focusable in order, the front card first; a peeking card is one node whose description reads the card's
  whole text (what its strip hides) and whose click action is "Bring to front". The pages are in the Dock's order, and
  there is no spine or page header: the Dock is the page indicator.
- **The Stage (P14).** On an unfolded posture the Stage view has no swipes: the rail is a column of tabs that scrolls
  vertically (`verticalScroll`) and a tap brings a stage to the centre; the selected stage's stack is the same
  bottom-anchored `StreamStack` (the Stage and the split keep no stack of their own). Two pages side by side (Pages on a wide screen) are one pager showing two, so a swipe still moves one
  page.

## Mode transitions (history; the Home/Library pull was retired by P5)

**The dock pull is the only mode-transition trigger** (plan Decision 9). It moves between a device
class's fixed Home ↔ Library pair (`ModePair`, #1241): Home is Cards or Standard, chosen in
Settings > Layout > Modes > "Home screen", and Library is always the other side;
`ModePair.counterpart` gives the mode a pull leads to. There is no configurable ring and nothing to
order. Every alternative trigger was deleted rather than kept dormant:

- the dock swipe-up gesture, its setting ("Dock gestures" -> "Swipe up") and its stored
  `dockGestures.swipeUp` binding;
- the NEXT_MODE / PREVIOUS_MODE gesture actions (and the three-finger defaults that used them), and
  the shell actions behind them;
- the app drawer as a gesture action (OPEN_APP_DRAWER, "Apps" in the gesture settings). Library
  *is* the app drawer, so no home gesture opens it -- swipe up and pinch out, which did by default,
  are now unbound. The drawer itself stays: its on-screen entry points (the Apps button, search)
  still open it through the shell's `OpenAppDrawer` action.

Stored settings that name a removed action (NEXT_MODE, PREVIOUS_MODE, their older names
ENTER_ADAPTIVE_STAGE / EXIT_ADAPTIVE_STAGE, or OPEN_APP_DRAWER) still decode: any stored action name
that no longer exists decodes as "no action", and a stored `dockGestures` object is ignored. A
missing value still takes the default. So an existing install whose swipe up and pinch out still
opened the drawer comes back with both unbound; no notice is shown.

### The dock pull

A one-finger drag that starts on the dock body (`HomeDockHost`'s dock, wrapped by
`dockPullInput`) switches Home ↔ Library (#1206, #1207):

- **Claim.** On the Main pass, after the dock's children. Nothing is decided inside touch slop.
  Past it the drag is a pull only when its component along the edge's natural pull direction is
  positive and larger than the component across it (`dockPullClaimFor`), and only before the
  long-press timeout -- so a drag along the run still scrolls the sections, a held press is still a
  long-press, and a drag into the edge does nothing. It is off while the layout is being edited
  (long-press + drag moves dock items then) or the widget picker is open.
- **Tracking.** The dock, and the outgoing surface with it, follow the finger 1:1; the pull's
  travel is the window's extent along the pull axis. The incoming surface follows in from the side
  the dock came from, and the dock background fades with progress (`DockPullFrame`).
- **Release.** `DockPullThresholds`: 40% of the travel or a 600dp/s fling toward the interior
  commits (a fling back toward the edge cancels; under 8dp of movement a fling does not count). A
  commit moves the dock to the destination's edge and orientation, displaced in by as far as it was
  pulled, and settles it there with its background fading back in; the shell is asked for the
  switch (`SelectLauncherViewMode` with `dockPullCounterpartMode`) when the settle finishes (commit
  happens under cover of the settle, well before it visually resolves -- see the reorient below). A
  cancel springs back. A finger landing on a settling dock catches it and tracks from there.
- **Reorient (dock-reorient decisions, follow-up to #1278).** The dock's own re-orientation on
  COMMIT is drawn, not cut: a darken-and-blur frost (`Modifier.dockReorientFrost`, blur on API 31+,
  darken alone below it) ramps with the same background-fade progress and clears as the dock
  settles; the dock tilts a few degrees on its own `graphicsLayer` (`cameraDistance`,
  `rotationX`/`rotationY`) proportional to that same frost strength, easing back to flat on settle;
  the dock area's own cross-axis extent eases (`animateContentSize`) instead of snapping to the
  destination edge's size; and the freshly-committed dock content holds at low alpha for a short
  reveal beat (`DOCK_PULL_REORIENT_REVEAL_HOLD_MILLIS`, `DockPullState.revealAlpha`) before
  resolving, so it doesn't pop in under the frost.
- **Reduced motion.** Nothing slides; the two surfaces crossfade with progress and the settle is a
  short tween to the same end state.
- **Equivalents** (Decision 12): the dock's accessibility action "Switch to Library" / "Switch to
  Home", and **Ctrl + the arrow of the pull direction** (Ctrl+↑ for a bottom dock) whenever focus is
  inside Home. Both run the same commit as a settle from rest.

**Dock shelf expansion is switched off** (plan Decision 11) behind `DockShelfExpansion.enabled`, which
defaults to false. Off, no dock opens its shelf -- by swipe or by button -- and the expansion
settings are hidden; the swipe away from the dock edge that the shelf used to claim does nothing.
The shelf code and its tests stay (the tests switch the flag on around themselves).

**Workspace menu.** Since S9 the dock pull opened it; since P5 it does not, and the home gesture action
"Workspace menu" (`OPEN_WORKSPACE_MENU`) is removed along with "Next stage" and "Previous stage"
(`SELECT_NEXT_APP_STAGE`, `SELECT_PREVIOUS_APP_STAGE`): a stored settings or workspace binding that names one decodes as
no action.

## Thresholds

All thresholds are dp (`GestureThresholds`), resolved to pixels per display
(`GestureThresholdsPx.resolve(density, touchSlop)`). The dp values reproduce the old pixel
constants at the 2.625x reference density (420dpi) they were tuned on.

| Threshold | dp | Was | Notes |
| --- | --- | --- | --- |
| Home swipe commit | 30.5 | 80px | Dominant axis must lead the other by 1.2x; pinch at 18% scale change |
| Dock shelf toggle | 30.5 | 80px | Measured away from / toward the dock edge |
| Dock pull commit | 30% of the travel (the window's extent along the pull); fling 600 dp/s once moved 8 dp | 40% / 600 dp/s, then 30% | `GestureThresholds.DOCK_PULL_*`; the Library pull |
| Dock press commit | 28 | – | `GestureThresholds.DOCK_PRESS_COMMIT_DP`; measured toward the edge at the lift, no velocity rule |
| Dock choose: hold | – | 220 ms | `DockMotionTuning.holdMillis` (slice 6 exposes it); the finger rests within 6 dp past the commit threshold |
| Dock choose: slide | 14 | – | Along the run, past the commit threshold; starts choosing at once |
| Dock choose: edge zone / speed | 15% of the row / 300 dp/s | – | `DockMotionConstants.CHOOSE_EDGE_*` |
| Dock choose: cancel | 1 Dock thickness | – | Beyond the Dock's inner edge; lifting there changes nothing |
| Dock shelf claim | 9 (never below touch slop) | 24px (9dp = 23.6px at 2.625×) | Must stay below the home swipe commit |
| Card stack travel per card / fling | see `CardStackTravel` | 64px / 500px/s | Already dp since #1211 |
| Multi-finger claim | 3 pointers | – | See rule 2 |

## Arbitration table

Default bindings come from `defaultHomeGestureActions` (`LauncherSettings.kt`); every home binding
is user configurable. Cards filters home actions to stage navigation, search,
Settings, the Stream and the Library (`adaptiveStageAppStageActionFilter`; Settings since #1212, so a
gesture bound to it is never a dead end in Cards; the Stream and the Library since P5). "Home" means the home gesture layer with the user's binding; "unbound"
means no default binding, so nothing happens unless the user binds the gesture.

| Region | Input | Std / Lib | Cards |
| --- | --- | --- | --- |
| Dock body | 1-finger in the natural pull direction (away from the edge) | **Dock pull**: opens the Library, tracking the finger (claims past touch slop when the drag leads along the pull) | same |
| Dock body | 1-finger toward the edge, then release past 28 dp | **Dock press**: toggles Home and the Stream (needs a Stream); a press that starts on a live app / now playing icon opens that page | same |
| Dock body | the same press held 220 ms past 28 dp, or slid sideways more than 14 dp | **Hold to choose**: the selection follows the finger along the Dock; release opens (Home) or switches to (Stream out) the selected page; dragging more than a Dock thickness away cancels | same |
| Dock, live app / now playing | tap | Home mode: launches the app (the player for now playing). Contextual mode: switches to its page | same |
| Dock, contextual tile | tap | Switches the Stream to that page | same |
| Dock, contextual row | 1-finger along the run | The row scrolls with fling (owns) | same |
| Dock, live app / now playing | long-press | Home mode: Open in Stream, shortcuts, App info, Hide app. Contextual mode: page options first | same |
| Dock, contextual tile | long-press | Page options: Show as, Move earlier / later, Hide section, Show <hidden section> | same |
| Dock sections (dynamic, notification row) | 1-finger along the dock run | Section scrolls (owns) | Section scrolls (owns) |
| Dock sections | 1-finger away from edge | Nothing while shelf expansion is off (reserved for the dock pull) | same |
| Dock background / icons | 1-finger away from edge | Nothing while shelf expansion is off; falls through to Home in Std/Lib (unbound by default) (reserved for the dock pull) | Nothing (reserved for the dock pull) |
| Dock background / icons | 1-finger toward edge while expanded | Shelf collapse (only reachable with shelf expansion on) | same |
| Dock icons | long-press + drag | Reorder / drag out (owns) | same |
| Dock | 1-finger along run, no scroll room | Home (Std/Lib only; dock is inside the home layer) | Nothing (dock is outside the stage surface) |
| Card stack (Notification overview prototype only) | 1-finger along stack axis | – | Stack scrolls; **past first/last card → handed to Home** (unbound by default) |
| Card stack with ≤1 card | 1-finger along stack axis | – | Home (stack does not claim) |
| Card stack | 1-finger across stack axis | – | Stage pager (horizontal) |
| Stream page (stack, list, tiles) | 1-finger vertical | Page scrolls: the stack follows the finger (dragging down brings older cards forward), flings with light inertia and settles on a card with a magnet, rubber-banding past the first or last card; or the list scrolls | same |
| Stream page | 1-finger horizontal | Stream pager (owns): next or previous page, the Dock's ring follows | same |
| Stream Stage rail (unfolded) | tap a thumbnail / vertical drag | Brings that stage to the centre (shared-element transition) / scrolls the rail | same |
| Card stack | 2-finger | – | Stack (first finger's drag wins; see limitations) |
| Card stack | 3-finger | – | **Home claims** (unbound by default) |
| Grid / workspace | 1-finger horizontal | Page pager (owns) | – |
| Grid / workspace | 1-finger vertical | Home (up: unbound, down: Search, which opens the Library with its field focused) | – |
| Grid / workspace | long-press + drag | Item drag (owns) | – |
| Grid / workspace | 2-finger swipe | Home (up: search, down: settings) | – |
| Grid / workspace | pinch | Home (in: edit mode, out: unbound) | – |
| Grid / workspace, pager | 3-finger | **Home claims** (unbound by default) | – |
| Stage area outside the stack | 1/2/3-finger | – | Home, filtered |
| Page indicator | 1-finger horizontal | Scrub pages (owns) | – |
| Page indicator | 1-finger vertical | Home | – |
| Screen edges | back / home system gestures | Platform (the dock excludes no edge area) | Platform |

## Resolution rules

1. **First consumer owns 1- and 2-finger touches.** Children run on the Main pass
   (descendant-first); the home layer reads the Final pass. Once a child consumes a pointer, the
   home layer yields for the rest of that touch (`HomeGestureArbiter`, `YIELDED`).
2. **Three fingers always belong to the home layer.** When a third finger lands, the home layer consumes
   the pointers on the Initial pass (ancestor-first), before any child sees them. That cancels a
   card-stack or pager drag already in progress, and the home layer may fire even though it had
   yielded. Its baseline resets when the finger count changes.
3. **Scrollers hand back what they cannot use.** The card stack consumes only the part of a drag
   that moves it (`cardStackScrollStep`); the remainder travels up the nested-scroll chain. In
   Cards the home layer listens (`homeGestureInput(overscrollHandOff = true)`,
   `OverscrollHandOffTracker`) and treats leftover vertical travel past the home threshold as a
   one-finger swipe, once per drag. When the stack moved not at all, the touch slop that
   `Modifier.scrollable` withheld is credited back so the hand-off commits at the same finger
   travel as a swipe anywhere else. A drag that moved the stack gets no credit (the swipe has to
   carry on further past the end), which avoids a card change and a home action from one flick.
4. **A stack with nowhere to go does not claim.** With ≤1 card the stack's `scrollable` is disabled,
   so the swipe is an ordinary unconsumed home swipe.
5. **The dock shelf claims early and only in its own direction.** It consumes once the drag is 9dp
   away from (or toward, when expanded) the dock edge -- ahead of the 30.5dp home threshold -- and
   never consumes a wrong-direction drag, which then falls through to home. It only applies while
   shelf expansion is switched on.
   With it on, the shelf and the dock pull (both claim a drag away from the dock edge) are arbitrated by
   pass order: the shelf handler sits inside the dock body and sees a drag first (Main pass, descendants
   first), consumes it at 9dp, and the dock pull, which only claims drags no descendant consumed, yields.
   So a shelf that can open wins the drag; with expansion off (the default) the pull owns it.
6. **Platform edges win.** The dock never requests system-gesture exclusion. The dock pull starts on
   the dock body, which is already inset from the screen edge; exclusion over the dock is added
   only if device testing shows side-dock conflicts with Back (Decision 13).

7. **Containers declare their axes (WS4).** `HomeGestureArbiter` takes `ContainerGestureAxes` (the axes
   `AxisDeclarations.resolve` produced for the containers under the home layer) instead of per-surface flags.
   A declared vertical axis turns the nested-scroll hand-off on (rule 3, same as `overscrollHandOff`), and a
   one- or two-finger drag along a declared axis is never fired as a home swipe, even before the child has
   consumed a pointer (`mayFireOnDrag`). Three fingers still always belong to home. A container that cannot
   scroll right now (a stack with one card) passes no axes (rule 4). `homeGestureInput(childAxes = ...)` is
   the app-side input; nothing passes it yet, so existing surfaces behave as before until WS6-WS8 wire it.

Boundary feel: when a drag first pushes against the first or last card the stack ticks one haptic
(`CardStackInteraction.onBoundaryHaptic`, the Cards settle haptic strength). There is no rubber-band
stretch: drawing one would need the stack to move past its clamp, and the travel it absorbed would
no longer be available for the hand-off.

## Known limitations / remaining work

- **Hand-off is vertical only.** Leftover horizontal travel (e.g. past the last stage in the
  `HorizontalPager`, or a horizontal side-rail stack) is not handed to home gestures. Enabling it
  would turn a pager overscroll into a page/stage action; that needs a product decision first.
- **Two-finger gestures started over the stack or pager** still belong to the child once its first
  finger crosses touch slop. Raising the claim to two fingers would steal pinch/zoom from hosted
  widgets.
- **The dock pull's hit area is the dock's resting place.** A finger catching a settling dock has to
  land where the dock rests (on the edge it is settling onto), not where it is drawn mid-flight.
- **No dock, no pull.** With the dock switched off or hidden by a workspace, the "Open Stream" and "Open Library"
  accessibility actions on the home surface root, the keyboard shortcuts and a bound gesture reach the two places; a
  workspace may hide its dock only while a gesture does (the stranding rule).
- **Narrow dynamic side.** With one or two entries the dynamic side is a small pull target; if it proves too narrow on a
  device, the pull may start anywhere on the dock and choose by the half it started on (ADR 0003).
- **Side docks:** along-run vertical drags scroll the dock run when it has overflow. The dock pull
  (#1207) does not compete with that: a side dock's pull is horizontal, perpendicular to its run.
- **Primitive migration (ADR 0002):** `HomeGestureInput` and `DockShelfGesture` stay custom pointer
  loops for the reasons recorded in the ADR (N-finger swipes with one pinch/swipe decision;
  direction-selective claiming that `detectVerticalDragGestures`/`AnchoredDraggable` cannot
  express). This change moves their decision math into tested domain code and adds a platform
  nested-scroll hand-off; it does not rewrite the loops.
