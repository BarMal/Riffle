# Gesture arbitration

Which part of the launcher owns a touch, and what it does with it, in each home mode:
**Std** (Standard, app drawer), **Lib** (Home screen library) and **Cards** (Adaptive Stage).
Std and Lib share the `StandardHome` surface, so they arbitrate identically; Cards composes the
dock as a sibling of the Adaptive Stage surface rather than inside it.

This page describes the behaviour after #1210, revised by the dock-pull decisions (see
"Mode transitions" below). The code it describes:

> **P5 (2026-11-12, [ADR 0003](../architecture/adr/0003-places-and-views.md)).** The Dock is the handle of the Stream and
> the Library. The dock pull is claimed by the same rules as before, but it now opens the Library when it starts on the
> dock's pinned side and the Stream when it starts on its dynamic side; it no longer opens the workspace menu and no
> longer switches Home and Library. See ["The Dock pull (P5)"](#the-dock-pull-p5). The older sections below are kept as
> history where they are not restated.

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
| Card stack scroll/fling | `CardStack.kt` | `Modifier.scrollable` + custom `FlingBehavior` |
| Stage pager (Cards) | `AdaptiveStageAppStageSurface.kt` | `HorizontalPager` |
| Home page pager (Std/Lib) | `ImmediateHomePager.kt` | `HorizontalPager` |
| Dock pull: Library (pinned side), Stream (dynamic side) | `DockPull.kt`, `HomeDockPullBinding.kt` | Custom pointer loop (ADR 0002: direction-selective claim); the one loop the pull needs. Decisions (claim, side, destination, commit, frame) in `core/domain/.../dockpull/` |
| Dock shelf expand/collapse (switched off, see below) | `DockShelfGesture.kt` | Custom pointer loop (ADR 0002: direction-selective claim) |
| Dock run / dock sections | `HomeDock.kt`, `DockDynamicSection.kt`, `DockNotificationCards.kt` | `horizontalScroll`/`verticalScroll` |
| Page indicator scrub | `HomePageControls.kt` | `detectHorizontalDragGestures` |
| Arbitration decisions | `core/domain/.../gestures/`, `.../cards/CardStackOverscroll.kt` | Pure Kotlin, unit tested |

## The Dock pull (P5)

The dock edge's natural pull direction is **away from the edge** (a bottom dock pulls up, a top dock down, a left dock
right, a right dock left). A one-finger pull along it, on the dock body, opens one of two places by **the side of the
dock it started on**:

| Starts on | Result |
| --- | --- |
| The pinned side (pinned apps, the divider, any padding before the dynamic section) | The Library (`OpenAppDrawer`), tracking the finger -- from Home and from the Stream |
| The dynamic side (live apps, now playing, the Stream button, and the dock padding beyond them) | The Stream, tracking the finger; from the Stream, Home |

- **Claim.** One finger on the dock body, past touch slop, leading along the natural pull direction (its component along
  it positive and larger than the one across it), before the long-press timeout (`dockPullClaimFor`); off while the layout
  is edited or the widget picker is open. A drag along the run still scrolls the sections, a held press is still a
  long-press, a drag into the edge does nothing. With a moved dock edge (a workspace's dock override) the direction
  follows the effective edge.
- **Side.** Where the finger landed, in root coordinates, against the dynamic section's bounds, which the section reports
  (`dockPullSideAt`, tested for a section at either end so a mirrored dock needs no special case). No Stream, no dynamic
  side: every pull opens the Library.
- **Tracking (Stream).** The pull's travel is the window's extent along the pull axis. The Stream slides in from the
  dock's edge as the finger moves, 1:1, while Home fades out under it; the dock itself does not move. From the Stream the
  same pull slides it away toward the interior and Home fades back in. Both surfaces are composed only while a pull or
  its settle runs. Reduced motion: nothing slides, the two crossfade.
- **Release.** `DockPullThresholds` (values in `GestureThresholds`): 30% of the travel or 600 dp/s toward the interior
  commits (a fling back toward the edge cancels; under 8 dp of movement a fling does not count). A commit settles to the
  end and asks the shell (`OpenStream` / `OpenHome`; a committed switch stands in for the shell's answer until it lands);
  a cancel springs back. A finger landing on a settling pull catches it and tracks from there (interruptible).
- **Expanded postures (P6).** On an unfolded foldable, tablet or desktop the same pull settles to the nearest of three
  states, Home only, a split (Home and the Stream side by side, or stacked over a tabletop's hinge) and the Stream only,
  by where it stopped and its velocity (`SplitSettle`; the release is projected 0.15 s ahead along its velocity). The
  Stream slides in over the first half of the travel while widening to its split share, then fills the screen over the
  second half; stopping partway leaves the split. A pull on the dynamic side while the Stream shows runs the scale
  downward: it can stop at a split from the Stream only, or return Home. The pull still never moves the dock, and the
  commit thresholds above apply only to the phone's full switch. The divider between the panes is a `draggable` (no new
  pointer loop), focusable, moved by the arrow keys along its axis and by the accessibility actions "Home only", "Split"
  and "Stream only"; see [The split](dock.md#the-split-p6). Folded and phone postures are unchanged.
- **Library pull (P7).** The Library's sheet rises from the Dock's edge as the finger moves, 1:1, while what is under it
  (Home or the Stream) fades out; the Dock does not move. The release commits by the same rules as the Stream pull
  (`DockPullThresholds`: 30% of the travel or 600 dp/s toward the interior), settles to open (a committed pull stands in
  for the shell's answer until it lands) or springs back, and a finger landing on a settling pull catches it
  (interruptible). Reduced motion: the sheet fades in instead of sliding. It never splits, whatever the posture. While
  the Library shows the Dock pull is not claimed.
- **Equivalents.** The dock's accessibility actions "Open Stream" (or "Back to Home" while the Stream shows) and "Open
  Library", also on the Home surface root so they exist when a layout hides the dock; **Ctrl + the arrow of the pull
  direction** (Ctrl+Up for a bottom dock) opens the Stream or returns Home, **Ctrl + Shift + that arrow** opens the Library;
  the Stream button; any home gesture bound to "Stream" (`OPEN_STREAM`) or "Library" (`OPEN_LIBRARY`), neither bound by
  default. The "Workspace menu" accessibility action stays on the dock and the root until P8 removes the menu; the pull
  and the shortcut no longer open it.
- **Taps on the dynamic side.** The Stream button toggles Home and the Stream; a live app opens the Stream on that app's
  stack (the Notifications section scrolls to it and the group comes into view); now playing opens it on the Now section.

## The Library (P7)

The Library (ADR 0003) is a Thick glass sheet over Home or the Stream, sized to what the Dock leaves free and inside the
system bar insets, with the Dock still drawn and tappable under it. It is `ShellDestination.APP_DRAWER` (the name kept for
the stored routing), or `SEARCH` when it opens with the search field focused and the keyboard up; the shell remembers
whether it opened over the Stream (`libraryOverStream`) so closing returns there.

- **Open.** The pinned-side Dock pull (above), the home gesture **Library** (`OPEN_LIBRARY`), the Dock's "Open Library"
  action, Ctrl + Shift + the pull arrow, and **Search** (`OPEN_SEARCH`), which opens the Library with its field focused
  and the keyboard up. **Swipe down** (`ONE_FINGER_DOWN`) is bound to Search by default now (it was Notifications); the
  two-finger swipe up took Notifications' place, so nothing else changes and every binding stays user-configurable. A
  stored value is kept as it was: only an unset binding takes the new default. The search pill on Home asks for the same
  thing.
- **Close.** Back (the open category or the typed query first), the Home button, a pull toward the Dock's edge on the
  sheet's header (the same commit thresholds as the pull; it tracks the finger), a tap on the margin around the sheet, the
  header's "Close Library" accessibility action, or launching an app, a shortcut or a web search from it. Back, the
  header close and the Home button return to what the Library opened over (Home or the Stream).
- **Header.** Long-press it, or use its options button, for **Show as** Categories, A-Z list or Grid (the same menu and
  wording as the Stream's section headers): it redraws at once and offers Undo. The view is stored per device class.
  The header node carries one custom-actions list ("Close Library" and each "Show as" view) for assistive technology.
- **Search.** The field at the top; typing replaces the view with results (apps first, then app shortcuts, then settings,
  each in the search source's own order, plus the web panel); Enter launches the top hit; clearing the field restores the
  view. Results are drawn inside a Regular glass panel with the spotlight.
- **Icons.** Tap launches; long-press opens the app menu (Add to Home, Pin to Dock, shortcuts, App info, Hide,
  Uninstall). There is no drag-out of the Library onto Home (the drawer never had one, and a new drag system is out of
  scope); Add to Home is its equivalent. The Personal and Work chips filter the view when more than one profile exists.
- **Recents and frequent apps** are not in the Library; they are the Stream's Recents section.
- **Arbitration.** The header's drag is a platform `draggable` (no new pointer loop); the lists and grids scroll vertically
  inside the sheet, so they never compete with it. The Dock pull is not claimed while the Library shows.

## The Stream (P4, entry points restated by P5)

The Stream (ADR 0003) is a full surface that replaces Home with the Dock still drawn under it. It is reached by the
Dock's dynamic side (above), the home gesture action **Stream** (`OPEN_STREAM`; no gesture is bound to it by default) and
the Dock's accessibility actions.

- **Close.** Back and the Home button (`OpenDefaultHome`) return to Home, as do the Stream button, the same dynamic-side
  pull and "Back to Home". A focused card that is expanded (a notification's thread, now playing's queue) collapses on
  Back first.
- **Inside the Stream (P13, P14).** The Stream is a horizontal pager of pages and each page is one vertically scrolling
  stack or list ([places-views-plan.md](places-views-plan.md), P13). The pager is the platform `HorizontalPager`
  (`Orientation.Horizontal`, ADR 0002); a page's stack is the existing `CardStack`, whose platform `scrollable` is
  `Orientation.Vertical` with the spline fling and magnet settle, and a List or Tasks page is `verticalScroll`. The two
  never fight because they take different axes: a drag that leads sideways is the pager's, one that leads up or down is the
  page's, and the stack hands a drag that runs past its first or last card on (it owns a drag only while it has cards to
  move: `cardStackOwnsDrag`). The Dock pull is claimed on the dock body only, so it never competes with either.
  Every gesture has a non-gesture equivalent:

  | Gesture | Equivalent |
  | --- | --- |
  | Swipe to the next or previous page | Tap a spine marker (a tab, spoken "Chat, page 2 of 8"); the pager's custom actions "Previous page" and "Next page" |
  | Scroll a stack to another card | The "Previous card" and "Next card" buttons under it with "Card 2 of 3"; the focused card's custom actions "Previous card", "Next card" and "Open card"; the arrow keys |
  | Long-press a page header for Show as, Move and Hide | The header's options button (one menu) |
  | Long-press the spine (or the Stage's bar) for the Stream's own menu: Show as Pages or Stage, and Show for each hidden section | The options button at its end |
  | Tap a Stage rail thumbnail | It is a tab (a button named "Bring Chat to the front") |

  Each page is a labelled pane, the pager carries one custom-actions list, and no node carries two (the page actions are
  on the pager, the card actions on the focused card, Hide on each card).
- **The Stage (P14).** On an unfolded posture the Stage view has no swipes: the rail is a column of tabs that scrolls
  vertically (`verticalScroll`) and a tap brings a stage to the centre; the selected stage's stack is the same vertical
  `CardStack`. Two pages side by side (Pages on a wide screen) are one pager showing two, so a swipe still moves one
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
| Dock pull commit | 30% of the travel (the window's extent along the pull); fling 600 dp/s once moved 8 dp | 40% / 600 dp/s, then 30% | `GestureThresholds.DOCK_PULL_*`; the Stream and the Library pull share it |
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
| Dock body, pinned side | 1-finger in the natural pull direction (away from the edge) | **Dock pull**: opens the Library, tracking the finger (claims past touch slop when the drag leads along the pull) | same |
| Dock body, dynamic side | 1-finger in the natural pull direction | **Dock pull**: opens the Stream, tracking the finger (from the Stream: returns Home) | same |
| Dock, Stream button / live app / now playing | tap | Stream button toggles Home and the Stream; a live app or now playing opens the Stream on its stack or on Now | same |
| Dock sections (dynamic, notification row) | 1-finger along the dock run | Section scrolls (owns) | Section scrolls (owns) |
| Dock sections | 1-finger away from edge | Nothing while shelf expansion is off (reserved for the dock pull) | same |
| Dock background / icons | 1-finger away from edge | Nothing while shelf expansion is off; falls through to Home in Std/Lib (unbound by default) (reserved for the dock pull) | Nothing (reserved for the dock pull) |
| Dock background / icons | 1-finger toward edge while expanded | Shelf collapse (only reachable with shelf expansion on) | same |
| Dock icons | long-press + drag | Reorder / drag out (owns) | same |
| Dock | 1-finger along run, no scroll room | Home (Std/Lib only; dock is inside the home layer) | Nothing (dock is outside the stage surface) |
| Card stack | 1-finger along stack axis | – | Stack scrolls; **past first/last card → handed to Home** (unbound by default) |
| Card stack with ≤1 card | 1-finger along stack axis | – | Home (stack does not claim) |
| Card stack | 1-finger across stack axis | – | Stage pager (horizontal) |
| Stream page (stack, list, tiles) | 1-finger vertical | Page scrolls: the stack moves a card (magnet settle) or the list scrolls; past the first or last card it hands on | same |
| Stream page | 1-finger horizontal | Stream pager (owns): next or previous page, the spine follows | same |
| Stream spine | tap a marker / long-press | Jumps to the page / opens the Stream's menu (Show as Pages or Stage, hidden sections) | same |
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
