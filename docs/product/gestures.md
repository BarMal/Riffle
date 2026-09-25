# Gesture arbitration

Which part of the launcher owns a touch, and what it does with it, in each home mode:
**Std** (Standard, app drawer), **Lib** (Home screen library) and **Cards** (Adaptive Stage).
Std and Lib share the `StandardHome` surface, so they arbitrate identically; Cards composes the
dock as a sibling of the Adaptive Stage surface rather than inside it.

This page describes the behaviour after #1210, revised by the dock-pull decisions (see
"Mode transitions" below). The code it describes:

> **Plan revision 2026-09-25.** The dock pull becomes the only mode-transition trigger (Decisions
> 3, 9, 11 in `modes-dock-handle-and-cards-plan.md`). The alternative triggers are deleted and
> dock shelf expansion is switched off (#1241, below). The pull itself (#1206, #1207) is not built
> yet: a one-finger drag starting on the dock body in its natural pull direction (away from the
> dock edge) will be owned by it.

| Recognizer | File | Primitive |
| --- | --- | --- |
| Home gestures (1/2/3-finger swipe, pinch) | `HomeGestureInput.kt`, `HomeSwipeGesture.kt` | Custom pointer loop (ADR 0002: no N-finger Foundation equivalent) + `nestedScroll` hand-off |
| Card stack scroll/fling | `CardStack.kt` | `Modifier.scrollable` + custom `FlingBehavior` |
| Stage pager (Cards) | `AdaptiveStageAppStageSurface.kt` | `HorizontalPager` |
| Home page pager (Std/Lib) | `ImmediateHomePager.kt` | `HorizontalPager` |
| Dock shelf expand/collapse (switched off, see below) | `DockShelfGesture.kt` | Custom pointer loop (ADR 0002: direction-selective claim) |
| Dock run / dock sections | `HomeDock.kt`, `DockDynamicSection.kt`, `DockNotificationCards.kt` | `horizontalScroll`/`verticalScroll` |
| Page indicator scrub | `HomePageControls.kt` | `detectHorizontalDragGestures` |
| Arbitration decisions | `core/domain/.../gestures/`, `.../cards/CardStackOverscroll.kt` | Pure Kotlin, unit tested |

## Mode transitions

**The dock pull is the only mode-transition trigger** (plan Decision 9; the pull itself is not
built yet). Every alternative was deleted rather than kept dormant:

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

Until the dock pull lands, Settings is the only way to change mode.

**Dock shelf expansion is switched off** (plan Decision 11) behind `DockShelfExpansion.enabled`, which
defaults to false. Off, no dock opens its shelf -- by swipe or by button -- and the expansion
settings are hidden; the swipe away from the dock edge that the shelf used to claim does nothing.
The shelf code and its tests stay (the tests switch the flag on around themselves).

## Thresholds

All thresholds are dp (`GestureThresholds`), resolved to pixels per display
(`GestureThresholdsPx.resolve(density, touchSlop)`). The dp values reproduce the old pixel
constants at the 2.625x reference density (420dpi) they were tuned on.

| Threshold | dp | Was | Notes |
| --- | --- | --- | --- |
| Home swipe commit | 30.5 | 80px | Dominant axis must lead the other by 1.2x; pinch at 18% scale change |
| Dock shelf toggle | 30.5 | 80px | Measured away from / toward the dock edge |
| Dock shelf claim | 9 (never below touch slop) | 24px (9dp = 23.6px at 2.625×) | Must stay below the home swipe commit |
| Card stack travel per card / fling | see `CardStackTravel` | 64px / 500px/s | Already dp since #1211 |
| Multi-finger claim | 3 pointers | – | See rule 2 |

## Arbitration table

Default bindings come from `defaultHomeGestureActions` (`LauncherSettings.kt`); every home binding
is user configurable. Cards filters home actions to stage navigation, search and
Settings (`adaptiveStageAppStageActionFilter`; Settings since #1212, so a gesture bound to it is
never a dead end in Cards). "Home" means the home gesture layer with the user's binding; "unbound"
means no default binding, so nothing happens unless the user binds the gesture.

| Region | Input | Std / Lib | Cards |
| --- | --- | --- | --- |
| Dock body | 1-finger in the natural pull direction (away from the edge) | Target: dock pull, Home ↔ Library (#1207; no pill) | same |
| Dock sections (dynamic, notification row) | 1-finger along the dock run | Section scrolls (owns) | Section scrolls (owns) |
| Dock sections | 1-finger away from edge | Nothing while shelf expansion is off (reserved for the dock pull) | same |
| Dock background / icons | 1-finger away from edge | Nothing while shelf expansion is off; falls through to Home in Std/Lib (unbound by default) (reserved for the dock pull) | Nothing (reserved for the dock pull) |
| Dock background / icons | 1-finger toward edge while expanded | Shelf collapse (only reachable with shelf expansion on) | same |
| Dock icons | long-press + drag | Reorder / drag out (owns) | same |
| Dock | 1-finger along run, no scroll room | Home (Std/Lib only; dock is inside the home layer) | Nothing (dock is outside the stage surface) |
| Card stack | 1-finger along stack axis | – | Stack scrolls; **past first/last card → handed to Home** (unbound by default) |
| Card stack with ≤1 card | 1-finger along stack axis | – | Home (stack does not claim) |
| Card stack | 1-finger across stack axis | – | Stage pager (horizontal) |
| Card stack | 2-finger | – | Stack (first finger's drag wins; see limitations) |
| Card stack | 3-finger | – | **Home claims** (unbound by default) |
| Grid / workspace | 1-finger horizontal | Page pager (owns) | – |
| Grid / workspace | 1-finger vertical | Home (up: unbound, down: notifications) | – |
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
6. **Platform edges win.** The dock never requests system-gesture exclusion. The dock pull starts on
   the dock body, which is already inset from the screen edge; exclusion over the dock is added
   only if device testing shows side-dock conflicts with Back (Decision 13).

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
- **No gesture changes mode until the dock pull lands.** Leaving Cards used to be the dock swipe-up
  or a three-finger swipe down; both were removed, so for now Settings is the only way to switch.
- **Side docks:** along-run vertical drags scroll the dock run when it has overflow. The dock pull
  (#1207) does not compete with that: a side dock's pull is horizontal, perpendicular to its run.
- **Primitive migration (ADR 0002):** `HomeGestureInput` and `DockShelfGesture` stay custom pointer
  loops for the reasons recorded in the ADR (N-finger swipes with one pinch/swipe decision;
  direction-selective claiming that `detectVerticalDragGestures`/`AnchoredDraggable` cannot
  express). This change moves their decision math into tested domain code and adds a platform
  nested-scroll hand-off; it does not rewrite the loops.
