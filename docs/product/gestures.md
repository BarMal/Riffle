# Gesture arbitration

Which part of the launcher owns a touch, and what it does with it, in each home mode:
**Std** (Standard, app drawer), **Lib** (Home screen library) and **Cards** (Adaptive Stage).
Std and Lib share the `StandardHome` surface, so they arbitrate identically; Cards composes the
dock as a sibling of the Adaptive Stage surface rather than inside it.

This page describes the behaviour after #1210. The code it describes:

| Recognizer | File | Primitive |
| --- | --- | --- |
| Home gestures (1/2/3-finger swipe, pinch) | `HomeGestureInput.kt`, `HomeSwipeGesture.kt` | Custom pointer loop (ADR 0002: no N-finger Foundation equivalent) + `nestedScroll` hand-off |
| Card stack scroll/fling | `CardStack.kt` | `Modifier.scrollable` + custom `FlingBehavior` |
| Stage pager (Cards) | `AdaptiveStageAppStageSurface.kt` | `HorizontalPager` |
| Home page pager (Std/Lib) | `ImmediateHomePager.kt` | `HorizontalPager` |
| Dock shelf expand/collapse | `DockShelfGesture.kt` | Custom pointer loop (ADR 0002: direction-selective claim) |
| Dock swipe-up action | `DockSwipeUpGesture.kt` | `detectVerticalDragGestures` |
| Dock run / dock sections | `HomeDock.kt`, `DockDynamicSection.kt`, `DockNotificationCards.kt` | `horizontalScroll`/`verticalScroll` |
| Page indicator scrub | `HomePageControls.kt` | `detectHorizontalDragGestures` |
| Arbitration decisions | `core/domain/.../gestures/`, `.../cards/CardStackOverscroll.kt` | Pure Kotlin, unit tested |

## Thresholds

All thresholds are dp (`GestureThresholds`), resolved to pixels per display
(`GestureThresholdsPx.resolve(density, touchSlop)`). The dp values reproduce the old pixel
constants at the 2.625x reference density (420dpi) they were tuned on.

| Threshold | dp | Was | Notes |
| --- | --- | --- | --- |
| Home swipe commit | 30.5 | 80px | Dominant axis must lead the other by 1.2x; pinch at 18% scale change |
| Dock shelf toggle | 30.5 | 80px | Measured away from / toward the dock edge |
| Dock shelf claim | 9 (never below touch slop) | 24px | Must stay below the home swipe commit |
| Dock swipe-up | 30.5 past touch slop | 80px past slop | Slop handled by `detectVerticalDragGestures` |
| Card stack travel per card / fling | see `CardStackTravel` | 64px / 500px/s | Already dp since #1211 |
| Multi-finger claim | 3 pointers | – | See rule 2 |

## Arbitration table

Default bindings come from `defaultHomeGestureActions` (`LauncherSettings.kt`) and
`DockGestureSettings` (swipe-up = `EXIT_ADAPTIVE_STAGE`); every home binding is user configurable.
Cards filters home actions to stage navigation, exit, app drawer and search
(`adaptiveStageAppStageActionFilter`). "Home" means the home gesture layer with the user's binding.

| Region | Input | Std / Lib | Cards |
| --- | --- | --- | --- |
| Dock pill / handle | any | Reserved for #1207 | Reserved for #1207 |
| Dock sections (dynamic, notification row) | 1-finger along the dock run | Section scrolls (owns) | Section scrolls (owns) |
| Dock sections | 1-finger away from edge | Shelf gesture, if shelf affordance is GESTURE | same |
| Dock background / icons | 1-finger away from edge | Shelf expand (GESTURE affordance) → otherwise dock swipe-up action if it maps to one → otherwise Home (default: app drawer) | Shelf expand (GESTURE) → otherwise dock swipe-up (default: exit Cards) |
| Dock background / icons | 1-finger toward edge while expanded | Shelf collapse | Shelf collapse |
| Dock icons | long-press + drag | Reorder / drag out (owns) | same |
| Dock | 1-finger along run, no scroll room | Home (Std/Lib only; dock is inside the home layer) | Nothing (dock is outside the stage surface) |
| Card stack | 1-finger along stack axis | – | Stack scrolls; **past first/last card → handed to Home** (default up: app drawer) |
| Card stack with ≤1 card | 1-finger along stack axis | – | Home (stack does not claim) |
| Card stack | 1-finger across stack axis | – | Stage pager (horizontal) |
| Card stack | 2-finger | – | Stack (first finger's drag wins; see limitations) |
| Card stack | 3-finger | – | **Home claims** (default down: exit Cards; up: enter Cards is filtered out) |
| Grid / workspace | 1-finger horizontal | Page pager (owns) | – |
| Grid / workspace | 1-finger vertical | Home (up: app drawer, down: notifications) | – |
| Grid / workspace | long-press + drag | Item drag (owns) | – |
| Grid / workspace | 2-finger swipe | Home (up: search, down: settings) | – |
| Grid / workspace | pinch | Home (in: edit mode, out: app drawer) | – |
| Grid / workspace, pager | 3-finger | **Home claims** (up: enter Cards) | – |
| Stage area outside the stack | 1/2/3-finger | – | Home, filtered |
| Page indicator | 1-finger horizontal | Scrub pages (owns) | – |
| Page indicator | 1-finger vertical | Home | – |
| Screen edges | back / home system gestures | Platform (the dock excludes no edge area) | Platform |

## Resolution rules

1. **First consumer owns 1- and 2-finger touches.** Children run on the Main pass
   (descendant-first); the home layer reads the Final pass. Once a child consumes a pointer, the
   home layer yields for the rest of that touch (`HomeGestureArbiter`, `YIELDED`).
2. **Three fingers are always a mode gesture.** When a third finger lands, the home layer consumes
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
   carry on further past the end), which avoids a card change and an app drawer from one flick.
4. **A stack with nowhere to go does not claim.** With ≤1 card the stack's `scrollable` is disabled,
   so the swipe is an ordinary unconsumed home swipe.
5. **The dock shelf claims early and only in its own direction.** It consumes once the drag is 9dp
   away from (or toward, when expanded) the dock edge -- ahead of the 30.5dp home threshold -- and
   never consumes a wrong-direction drag, which then falls through to the dock swipe-up or home.
6. **The dock swipe-up yields to the shelf.** It is attached only when the shelf does not claim
   swipe-up (`claimsSwipeUp`) and no widget-picker drag is active, and only when its binding maps to
   an action in the current mode.
7. **Platform edges win.** The dock never requests system-gesture exclusion.

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
- **Side docks:** along-run vertical drags scroll the dock run when it has overflow, which takes
  precedence over the dock swipe-up. Revisit with the #1207 pill.
- **Primitive migration (ADR 0002):** `HomeGestureInput` and `DockShelfGesture` stay custom pointer
  loops for the reasons recorded in the ADR (N-finger swipes with one pinch/swipe decision;
  direction-selective claiming that `detectVerticalDragGestures`/`AnchoredDraggable` cannot
  express). This change moves their decision math into tested domain code and adds a platform
  nested-scroll hand-off; it does not rewrite the loops.
- **The dock swipe-up threshold is measured past the touch slop** (as before), so it commits at
  about 8dp more finger travel than the home swipe.
