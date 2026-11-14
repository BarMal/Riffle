# Dock modes: build plan

Builds ADR 0003 Amendments 1 to 3 on a folded phone. Each slice is one pull request that leaves the app
working. The unfolded layout (Amendment 2) follows in the parts under "Unfolded" below.

The reference for behaviour and feel is the set of working prototypes on the UX canvas (boards M1 to M3).
Their spring, magnet and timing constants are the starting values; the settings in slice 6 expose them.

| Slice | What it delivers | Depends on |
| --- | --- | --- |
| 1. Dock modes | The Dock's Home and contextual modes: contextual tiles appended after the live apps, the row scrolls in the contextual mode, the open icon is ringed. Press-down-and-release opens and closes. The Stream spine, page header and Stream button go. | none |
| 2. Trampoline and fan | The dip under the finger, the bounce on release, cards fanning out in sequence, Home fading, and closing as the opening reversed. Reduced-motion fallback. **Built**, see "As built". | 1 |
| 3. Hold to choose | Press and hold or slide while pressed: early extend, selection ring, name label, edge scrolling, haptic tick per icon. Starting a press on an icon opens that icon's stack. **Built**, see "As built". | 1, 2 |
| 4. Stacks | The bottom-anchored card stack with clipped peeking cards, drag, fling and magnet (**4a, built**, see "As built"); swiping left and right between stacks in Dock order (built in slice 1); shortcuts stacks for apps with nothing live, the Today page (agenda and tasks) and Recents as a stack (**4b, built**, see "As built"). | 1 |
| 5. Cards | App-tinted cards, images in browse and open cards, a card growing in place when opened, and its actions in the Dock. **Built**, see "As built". | 4 |
| 6. Settings | Stream settings (open card position, actions position, tint, background, pinned apps), motion settings, the accessibility action and the first-run hint. **Built**, see "As built" (background and pinned apps deferred). | 2 to 5 |
| 7. Home and Library | The combined search-and-page handle with quick-drag, search opening from the Dock (**7a, built**, see "As built"), the paged Library, Home mode (Placed items or App library) and the feature switches with Simple mode (**7b, built**, see "As built"). | none |

## Unfolded

Builds ADR 0003 Amendment 2 in two parts. Each part is one pull request that leaves the app working.

| Part | What it delivers | Depends on |
| --- | --- | --- |
| U1. Layout per posture | The layout as a setting per posture (Dock edge, which way the app pages scroll, the page indicator and quick scroll), Home, the paged Library and the App library Home following it, with the handle in front of a side Dock's middle, and the Layout page in Settings. **Built**, see "As built". | 7a, 7b |
| U2. Stack attached to the Dock | The upright card stack attached to a side Dock, the third of the screen for cards and two thirds for the grid, the grid flowing and fading continuously beneath the cards from the screen edge, and app peek-through. The Home and Stream split and the Stage view are replaced. **Built**, see "As built". | U1 |

## Rules for every slice

- No behaviour is removed without its replacement in the same slice.
- New gestures follow ADR 0002 (platform gesture primitives).
- Every new gesture has a non-gesture route (an accessibility action or a menu entry).
- JVM tests for pure logic; device tests for the gesture's outcome, not its pixels.
- The slice's row in this table is updated with what was built and what differed.

## As built

Filled in as slices land.

### Slice 1: Dock modes (built)

**Built.**

- **Two modes, one order** (`core/domain/.../dock/`). `DockMode` (Home, or contextual while the Stream shows).
  `DockDynamicSide.entries` gives the live apps and now playing and, for the contextual mode, one `ContextTile` per
  Stream page that is not an app (`contextSections`, from the pages the Stream publishes). The Stream button is gone from
  the domain, the UI and the strings. In the UI the contextual mode is `DockContextualRow`: the pinned icons at full size
  and spacing, a divider, the live entries, a divider and the tiles, in one row scrolled with the platform's
  `horizontalScroll` / `verticalScroll` (fling included), on every edge. `DockRunGeometry` says where each item lies and
  `DockRunScroll.toReveal` scrolls the ringed icon into view, with a margin so its neighbour peeks, whenever the ring
  moves; `DockRing` says which icon is ringed.
- **Pages follow Dock order.** `DockPageOrder` reorders the planner's pages: live apps in the Dock's order, the pages of
  apps beyond the Dock's slots, now playing's Now page, then the built-in pages in layout order. `StreamSurface` applies it
  and publishes the pages and the open one (the pager's `currentPage`, so the ring moves when a swipe passes halfway)
  through `StreamDockLink`; the Dock reads them. A tap on a tile or a live app in the contextual mode asks the Stream to
  land on the page through `StreamFocusRequests`, as before.
- **Spine, page header and Stream button removed.** The page options (Show as, Move, Hide) and "Show <hidden section>" are
  on a long-press of the tile (or of a live app's / now playing's icon) in the contextual mode, applied by the Stream
  through `StreamDockLink` so each keeps its Undo; Move steps over sections that draw no tile (`DockTileMoves`). The
  Stage's own bar and menu (unfolded) stay.
- **The press** (`DockPress`, `DockPressState`, one pointer loop in `DockPull.kt`). A drag toward the Dock's edge past
  touch slop is claimed as a press, and released past 28 dp (`GestureThresholds.DOCK_PRESS_COMMIT_DP`) it toggles the modes
  by running the existing Home and Stream transition from rest. A press that starts on a live app's or now playing's icon
  opens that page. `DockPressState` exposes `progress` (0 to 1) and `onReleased` for slice 2. The pull away from the edge
  now always opens the Library.
- **Accessibility.** The Dock's custom actions are "Show live pages" / "Back to Home" and "Open Library"; in the Home mode a
  live app keeps "Show <app> in Stream"; every tile is a focusable 48 dp target with a content description, a click action
  ("Show Agenda") and a long-click action.
- **Tests.** Pure logic in `DockContextualTest`, `DockPressTest`, `DockDynamicSideTest`, `DockPullEntryTest` and
  `DockDynamicSectionMetricsTest`; behaviour on the JVM in `DockContextualModeTest`, `DockLiveAppEntriesTest`,
  `DockPullScreenshotTest`, `StreamHomeDestinationTest`, `StreamOpenTransitionTest`, `StreamBehaviorTest`,
  `SplitScreenshotTest`; on a device in `DockContextualRowTest`, `DockPressInteractionTest` and the updated
  `StreamPagesInteractionTest`.

**What differed from the design.**

- **Now playing keeps its entry.** The amendment's Home mode lists only apps with notifications; the now playing entry stays
  (a tap still opens the player) and, while shown, stands for the Now page, so the Now tile is not drawn then and the order
  is unchanged either way.
- **Tiles follow the layout's order, not the prototype's.** The default layout order is Now, Notifications, Recents, Agenda,
  Tasks, Feeds, so the tiles read Now, Recents, Agenda, Tasks, Feeds, where the prototype has Now, Today, Recents, Feeds.
  Agenda and Tasks are two tiles, as asked. "Move earlier / later" on a tile is the layout's Move.
- **Notifications has a tile while it holds only a state** ("No notifications", "Allow access"), so the page and its action
  stay reachable; with live apps it has none.
- **The Stream opens on the page it was last left on, else its first page** (Now, in the default layout), not on a live
  app: the Stream's data arrives after it opens, and the page in front stays in front while it does.
- **Pull is always the Library**, from any part of the Dock. The Stream's pull (tracking the finger) and the split's
  three-state settle by pull are no longer started by a gesture (the press, the divider and their actions reach the three
  states); `DockPullState`'s split path remains for the press's settle.
- **Pinned apps in the contextual mode** launch on a tap (they have no page yet).
- **A Dock with no pins and nothing live** keeps a body one icon long, so it can still be pressed (the Stream button used
  to give it one).
- **Stream-level menu.** The pager's spine held the Stream's own menu (Show as Pages or Stage on a wide posture, Show for
  each hidden section). Hidden sections are offered by the tiles' long-press; Pages or Stage is Settings > Stream (and the
  Stage's own bar).

**Left for later.** Everything the slice list assigns to slices 2 to 7: the dip, bounce and card fan (the press only exposes
its progress and release), hold to choose, shortcuts stacks for pinned apps, bottom-anchored stacks, card tinting,
settings and the first-run hint (the onboarding text now describes the press), the Library and Home handle, and the unfolded
layout. Not done in this slice either: the owner's Today page (Agenda and Tasks stay two pages), and the dead split-settle
path in `DockPullState` can go when slice 2 rewrites the transition.

### Slice 2: Trampoline and fan (built)

**Built.**

- **Pure physics in `:core:domain`** (`launcher/dock/DockMotion.kt`, `DockClosing.kt`). `DockMotionConstants` holds every
  number (grouped: press, springs, release impulses, fan, closing, reduced motion, catching) and `DockMotionTuning` the
  three the settings slice exposes (speed 200 ms, trampoline 0.5, magnet 0.4). `DockPressCurve` is the dip
  (`24 dp * (1 - exp(-travel / 60))`) and Home's pre-fade (up to 0.3); `DockSpring` steps the two springs at a fixed
  1/240 s (progress: critically damped with a magnet, never overshoots; dip: under-damped, damping ratio 0.675, so it
  overshoots and settles); `DockRelease` gives the starting kicks (deep, shallow, from an action); `DockFan` gives each
  card's frame (`t = clamp(q * 1.3 - i * 0.11)` with `q = (p - 0.3) / 0.7`: it rises from the Dock, fades in, scales
  0.82 to 1) and Home's (alpha `1 - p`, scale down 4%); `DockClosingPlan` simulates the opening once and plays its
  samples last to first, then the rise, and `joinAt` finds where to join it for a Stream caught part-way;
  `DockReducedTimeline` is the reduced-motion cross-fade (the design language's 80 ms token).
- **UI** (`app/.../launcher/`). `DockMotionState` (`DockMotion.kt`) runs the press (REST, PRESSING, RUNNING), the release,
  the closing and the reduced fade off the frame clock and exposes `progress` and `dip`; `HomeDockPullBinding` wires the
  press's callbacks (`DockPressState.onBegan` / `onMoved` / `onReleased`) to it, draws Home from it (alpha and a slight
  scale), and offers the fan to the Stream through a composition local (`DockFanSource`, `LocalDockFan`, the
  `Modifier.dockFan(source, index)` graphics layer); `HomeDockHost` translates the Dock body by the dip, after the
  gesture modifier so the press is still measured in resting coordinates. In the Stream, the focused card of a stack and
  the two behind it fan by rank (`StreamStack`), a lone or expanded card and every other page (`PageScroll`, the
  all-hidden state) rise as one piece with the front card's timing. The accessibility actions, Ctrl + the arrow, a tap
  on a live app and Glance's open all go through `motion.run`, the same transition without a finger.
- **The shell is told at the moments that keep it consistent:** `OpenStream` when an opening starts (the Stream is then
  the shell's, and the contextual tiles appear with the fan), `OpenHome` when a closing has finished. A finger that lands
  mid-way is caught where things are (the on-screen frame hands over to the press over 100 ms), so the next release,
  shallow press or action runs on from there; the closing joins the reversed playback at the progress it was caught at.
  A Library pull, or the shell showing something else, ends a running motion at once (`finishNow`, `syncShell`).
- **Reduced motion** uses the repo's existing resolution (`launcherSettings.motion.reducedMotion`: the setting, which by
  default follows the platform animator scale): no dip, no pre-fade, no bounce, no fan; the press changes nothing on
  screen and the release is one 80 ms cross-fade, both ways. The old reduced-motion cut (no frame between) is replaced
  by this fade, as the amendment says.
- **Tests.** JVM, `core:domain`: `DockMotionTest` (dip curve, press frames, spring: no overshoot of progress at any
  tuning, overshoot of the dip, trampoline and speed settings, shallow press back to rest, fan order and landing,
  reduced frames and timeline) and `DockClosingTest` (the closing is the mirror of the opening, gathers speed, ends in the
  dip then rises, last card out is first back in, joining mid-way, carried dip). On Robolectric: `StreamOpenTransitionTest`
  (rewritten, see below; frame by frame: dip and pre-fade, open, close, shallow, reduced motion, accessibility actions,
  four interruptions) and `StreamFanTest` (which cards fan by rank and which pages rise as one piece). The existing press
  and accessibility device tests (`DockPressInteractionTest` and friends) assert end states and were left as they are.

**What differed from the design.**

- **The fan's overshoot cap.** The prototype lets a card's local progress reach 1.06, which leaves every card 6% of its
  rise above its resting place when the fan is complete. Here it is clamped to 1, so a finished fan is exactly the stack.
- **The shell is told at the release** (opening) so the Dock's contextual tiles are there while the cards fan, rather than
  at the end; closing tells it at the end, so the tiles stay until Home is back.
- **The fan applies to the focused card and the two behind it** in a stack by rank (cards past the third are not on screen
  in a fresh stack); a card caught mid-fan when the stack is dragged is not special-cased (the stack and the fan are
  separate layers).
- **Expanded postures** (split) keep the pull's own settle for the press; the dip and fan are folded-phone only until the
  unfolded layout slice. That is why `DockPullState`'s split path (`settleSplit`, `onSplitSettled`, `SplitPullSetup`) and
  its stream slide (`streamPullFrame`) stay: the press, the accessibility actions and the divider still run through them
  there, and its tests cover behaviour that still exists. On a phone nothing starts a Stream pull any more; it is left,
  not removed, for that reason.
- **The golden of the pressed Dock** (`DockPullScreenshotTest.theDockPressedDownBeforeTheRelease.png`) changes, because the
  Dock now dips and Home starts to fade there. The recorded image is not committed.

**Left for later.** Haptics (slices 3 and 6), hold to choose, settings UI for speed, trampoline and magnet (the tuning
object is ready for slice 6), bottom-anchored stack behaviour, the unfolded layout's fan.

### Slice 3: Hold to choose (built)

**Built.**

- **Pure decisions in `:core:domain`** (`launcher/dock/DockChoice.kt`, constants in `DockMotionConstants`, the hold time in
  `DockMotionTuning.holdMillis`). `DockChoiceStart` says when a press turns into choosing (past the 28 dp threshold and
  resting for the hold time, 220 ms, or sliding more than 14 dp along the run; never before the threshold; moving more than
  6 dp restarts the wait; drawing back above the threshold before it starts cancels it). `DockChoice.pageFor` is the one
  decision of what is selectable and what page it opens (live apps, now playing, tiles; pinned apps return null, which is
  the single line slice 4 changes), `nearestSelectable` snaps to the nearest selectable item by centre, skipping the rest,
  `edgeDirection` and `edgeScrollDp` scroll the row when the finger is in the outer 15% (300 dp/s), `isCancelling` is the
  cancel rule, `shouldTick` the haptic rule and `outcome` what a release does in every case (Home quick, Home choosing,
  contextual quick, contextual choosing, cancel, nothing selectable). `dockDynamicEntryTarget` now delegates to `pageFor`.
- **UI** (`app/.../launcher/`). `DockPull.kt`'s one pointer loop feeds every sample to `DockChoiceStart` and, while the
  finger rests, bounds its wait for the next event by the moment holding starts choosing (`withTimeoutOrNull`), so a still
  finger gets there; `DockPressState` gains `beginChoosing`, `choose`, `onChoosingBegan`, `onChoosingMoved` and
  `DockPressRelease.choosing`. `DockChoiceState` (`DockChoice.kt`) holds the selection, the cancel state and the row's
  scroll hook and applies the domain's rules; `DockMotionState.beginChoosing` / `endChoosing` freeze the dip and Home's
  pre-fade and dim Home to 30% (letting go over 120 ms when the choice ends). The binding turns the release into a
  `DockPressOutcome` and runs it: `OpenPage` and `Close` through the existing `onPress` (so the fan and the closing are
  untouched), `SwitchPage` through the tile's own delegate (the Stream lands on the page and stays out), `Revert` the
  existing bounce back.
- **Early extend.** While choosing from Home the dynamic side behaves as in the contextual mode (`rememberDockDynamicSide`
  `choosing`): the contextual row, the tiles, the row scrolled so the first live entry is at its start
  (`RevealForChoosing`; instant under reduced motion). The tiles are the Stream's pages, which only a composed Stream
  publishes, so `HomeDestination` composes the Stream unseen (alpha 0, no semantics) for the duration of a choice from Home.
- **Selection UI.** `Modifier.dockChoiceMark` rings the selected tile (3 dp, primary) and draws it 14% larger (snapped under
  reduced motion); `DockChoiceLabel` is a thin-glass chip just above the Dock on every edge. The label is hidden from
  accessibility (`clearAndSetSemantics`, a test tag and `DockChoiceLabelKey` only): it echoes the name the item already
  speaks, and a live region would announce it at every move. Entry bounds are now the tiles' whole extents
  (`positionInRoot` and size) rather than `boundsInRoot`, which a parent's clip shrinks to nothing off-screen.
- **Idle-friendly.** Frames run only while the finger holds in an end zone; otherwise the selection is looked at again when
  a pointer event arrives or an entry moves (`snapshotFlow`), so a resting finger keeps the app idle.
- **Haptics.** `LauncherHaptics.perform(DETENT)` (the repo's helper, which honours the haptic strength setting: off sounds
  nothing) when choosing begins and when the selection lands on another item. Not asserted by a fake in any test; the rule is
  `DockChoice.shouldTick`, tested on the JVM.
- **Tests.** `core:domain`: `DockChoiceTest` (selectable, nearest with skipping, edge scroll, cancel, tick, when choosing
  starts, every release outcome). JVM behaviour: `DockHoldToChooseTest` (hold, slide, cancel, from the contextual mode,
  quick presses unchanged, edge scrolling, reduced motion). Device: `DockHoldToChooseInteractionTest`.

**What differed from the design.**

- **The cancel rule** is the lift, with the finger more than one Dock thickness beyond the Dock's inner edge (above a bottom
  Dock); it is a state of where the finger is, so coming back over the Dock chooses again, and a system cancel never chooses.
- **Choosing in the contextual mode with nothing selectable changes nothing** (the "page a plain press opens" there would be
  Home, which is not a choice).
- **The hold needs the finger still** (within 6 dp of where it came to rest, measured from there, not from the last sample),
  so a press that keeps travelling is not a hold; the prototype only checked the time.
- **A finger resting at the lift moment counts**: the lift is a sample, so choosing begins at it if the hold time has run.
- **Folded phone only**, like the fan: an expanded posture's press stays the split's settle and never chooses.
- **Pinned apps are not selectable** and so are never ringed; a press that starts on one still opens the last page.

**Left for later.** Shortcut stacks for pinned apps (slice 4 only has to make `DockChoice.pageFor` answer for them), the
settings for hold time and haptics (slice 6; the tuning field is ready), the first-run hint, the unfolded layout.

### Fix: closing by press on a device

The owner, on a phone with gesture navigation: "I could only exit the context menu by using the home button, the dock
gesture didn't work."

**Cause.** Slice 3 turned a press that rests past the threshold into choosing after 220 ms, in both modes, and assumed that
nothing selectable in the contextual mode changes nothing. A real press (a finger lands on a tile or an app icon, which fill
the row edge to edge, drags 30 to 45 dp and lifts) lasts about half a second, so it almost always rested for 220 ms: it became
a selection, the finger was over a tile, and the release switched to that page (the one already showing) instead of
closing. Every test before this injected a press from the Dock's centre lasting 160 ms with no rest, so none of them could
see it. Opening from Home was unaffected: there the same selection opens the Stream.

**Ruled out, from the code.** The row's `horizontalScroll` and the tiles' `combinedClickable` do not take a mostly vertical
drag (the scroll claims only the horizontal one; the click detectors consume the lift, and the press consumes it first on the
Initial pass); the Dock's loop sits above both, observes on the Main pass after them, and a quick flick from a tile closes. The
dip translates the Dock inside the gesture, so the finger is measured in resting coordinates. The room is there: a finger on a tile
has about 54 dp below it in the fixture (more on a device, where the navigation bar's inset sits below the Dock), against the
28 dp threshold, and the system's gesture area is only entered by touches that start in it. A system cancel is a cancel and
was not seen to happen. Whether a particular phone's gesture navigation behaves differently cannot be settled without one.

**Fix.** `DockChoiceStart.next(holdStarts)`: with the Stream open only a slide of more than 14 dp along the run chooses;
holding never does. And `DockChoice.outcome(startedNearKey)`: a selection at the lift that is the item nearest where the
finger landed closes like a plain press (a thumb drifts, and a press that began on a pinned app has no page of its own), so
only a move onto another item switches pages. Holding still to choose from Home is unchanged. The UI feeds both
(`DockPullGestureHost.holdChooses`, `pressNearestSelectableAt`). System Back while the Stream shows already returned Home
(`LauncherShellBackHandling`, enabled for every destination but Home); a test now pins it.

**Tests.** `DockChoiceTest` (hold never chooses with the Stream open, the slide still does, the near-start selection closes);
Robolectric `DockRealFingerPressTest` (a press of about half a second landing on a tile, on a pinned app, held long, drifting
along the row, quick, and from Home, through the real `HomeDestination`) and `StreamBackReturnsHomeTest`; device
`DockRealFingerPressInteractionTest` and `StreamBackInteractionTest`. Rewritten on purpose because the behaviour changed:
`DockHoldToChooseTest.choosingWhileTheStreamIsOutSwitchesPagesAndDoesNotClose` and the device
`DockHoldToChooseInteractionTest.choosingWhileTheStreamIsOutSwitchesThePageAndKeepsTheStream` chose the open Stream's page by
holding still; they now slide (and the JVM one asserts that holding does not choose).

**Follow-up: the press is decided ahead of the row's scroll.** CI's emulator run failed two tests that move sideways
(a thumb drifting 18 dp along the row while pressing; a press that slides to choose). Two causes, one in each:

- The slide test (`choosingWhileTheStreamIsOutSwitchesThePageAndKeepsTheStream`) was my rewrite's fault: it moved to the
  Dock's mid-height as well as along the run, which lifts the finger back above the 28 dp threshold, and choosing never
  starts short of it. It now slides at the depth the press reached (the Robolectric twin already did).
- The drift test is the real gap. The press was claimed on the Main pass, after the children, so the row's
  `horizontalScroll` (which claims the first event where the sideways distance alone passes slop) and a tile's handlers saw a
  diagonal thumb's events first; when the sideways part passed slop in the same event as the whole did, the scroll took and
  consumed it and the Dock's loop abandoned the touch. Robolectric's fine-grained injected events rarely cross both in one
  event; a device's do. Not confirmed on a device here; the code path is the evidence.

The Dock now decides a press on the Initial pass (`DockPull.kt`, `awaitClaim`): the first event past slop that leads toward
the Dock's edge and more along that than across (`DockPress.claimFor`, the dominant axis at that moment, so 35 dp down with
18 sideways is a press) is consumed there, before the row or a tile sees it, and from then on `trackPress` consumes every
event on Initial. Mostly horizontal drags are not claimed and the row scrolls and flings as the platform does. The pull
(Library) is still claimed on the Main pass, after the children, so a child's own drag wins over it. ADR 0002: no platform
detector claims "the perpendicular axis ahead of a child scrollable"; the Initial pass of the one custom pointer loop is the
primitive that does. Tests: `DockPressTest` (diagonals), `DockRealFingerPressTest` and the device
`DockRealFingerPressInteractionTest` (a mostly horizontal drag scrolls the row and sends nothing).

**Second follow-up: a thumb's drift never chooses in the open Stream.** CI still failed
`DockRealFingerPressInteractionTest.aThumbThatDriftsAlongTheRowButStaysOnItsTileStillReturnsHome` after the Initial-pass claim
(the slide-to-choose and the horizontal-scroll tests passed, so the press was being claimed). The press was therefore ending
in the choice rules: 18 dp of drift is past the 14 dp slide that starts choosing, and the outcome then depends on the selection
at the lift matching the item nearest the landing. I could not find from the code which part differed on the emulator (a
neighbour's midpoint, the row moving under the finger, or the selection being null); no Robolectric run reproduces it. So the
rule no longer leans on it:

- With the Stream open choosing starts only on a deliberate slide, `DockMotionConstants.CHOOSE_SLIDE_FROM_OPEN_DP` = 36 dp
  (Home keeps 14 dp, `CHOOSE_SLIDE_DP`); a thumb that drifts 18 dp or more is a plain press and closes. Because choosing
  is what scrolls the row at its ends, a finger that has not slid that far never scrolls the row either.
- A selection at the lift that is the item nearest the landing still closes, and now so does a committed press that has
  nothing selectable (it was `Revert`; the test that asserted that was changed on purpose).
- The Dock records what it decided (`DockPressDiagnostics`, a test-only semantics property `DockPressDiagnosticsKey` on the
  Dock body, like `DockDipKey`): presses claimed, whether it began in the open Stream, the entry it started on, the selectable
  item nearest the landing and where that item lay, what started choosing (slide or hold) and the travel and slide then, at
  the lift the selection, whether it was cancelling, committed, the travel and where the landing item lay, and the outcome.
  The real-finger device tests put it, the landing point, the screen size and density and the row's position before and after
  in every failure message.

### Slice 4a: The stack (built)

**Built.**

- **Pure physics in `:core:domain`** (`launcher/stream/StackMotion.kt`; constants in `DockMotionConstants`, the inertia next to
  speed and magnet in `DockMotionTuning`; geometry tokens in `StreamCardStackSpec`). One continuous position (a float card
  index); `StackGeometry.frame(r)` with `r = index - position` is the layout (r = 0 front card, -1 sunk into the Dock and
  gone, 1 to 3 peeking strips, 4 hidden; keyframes from the prototype, with a card's visible part clipped at the top of the
  card in front plus a 4 dp gap so fills never overlap); `StackDrag` (90 dp per card, rubber band past both ends at 0.35,
  both ways); `StackFling.target` (`pos + v * 0.18 s * inertia`, rounded, held to the stack); `StackSpring` (critically
  damped, `STACK_SETTLE_TIME_FACTOR` 2.2, magnet within a quarter of a card, velocity held so it never overshoots);
  `StackSettle` (rest on a whole card, tick only on a different card, the reduced-motion jump and fade, Next and Previous).
- **UI** (`app/.../launcher/stream/`). `StreamStack.kt` (`FocusedStack`, `StackSlot`), `StreamStackLayout.kt` (a `Layout` that
  reads the position while measuring, so a drag relays out and recomposes nothing), `StreamStackState.kt` (the position,
  the spring loop and the reduced-motion jump). Bottom-anchored: the front card sits just above the Dock (a 16 dp inset),
  older cards peek above it. A peeking card is the real card clipped by its slot (`clipToBounds` at the measured size), so
  strip and card are the same pixels at every position. Drag and axis decision are the platform `scrollable`
  (vertical) with a custom `FlingBehavior` (ADR 0002); a move without a finger runs inside the same scroll so a touch
  catches it. `LocalLauncherHaptics` (new, provided by the Home destination) carries the haptic helper and its strength
  setting to the stack: `DETENT` when it settles on another card.
- **Fan**: ranks 0 to 2 from the front card fan as before (`dockFan` per slot, rank by distance from the front card).
- **Accessibility.** "Next card" (older) and "Previous card" are custom actions of the stack itself, "Open card" of the front
  card, whose state description is "Card 2 of 3"; every card is a node in order (front first); a peeking card is one node
  with a content description that includes its hidden body text and the click action "Bring to front"; arrow keys and Page
  Up/Down step. Reduced motion: dragging follows the finger; release jumps to the nearest card and fades from 40% over the
  80 ms fade, no coast, spring or magnet.
- **Tests.** `core:domain`: `StackMotionTest` (layout at whole and fractional positions: monotonic, no overlap, the clip
  opens toward the front; drag; rubber band; fling target for slow, fast and no velocity; magnet; settle on a whole card;
  reduced path; haptic rule). Robolectric: `StreamStackBehaviorTest` (which card is in front after a drag, a fling, a tap on
  a peeking card, the actions; catching a coast; ticks; reduced motion). Device: `StreamPagesInteractionTest` (same outcomes).

**What differed from the design.**

- **The "Card N of M" stepper pill is gone** (the owner asked, from a device screenshot): the drag, the fling, a tap on a
  peeking card and the stack's accessibility actions replace it.
- **Placement.** The Stream has no placement settings yet, so the rule kept is "the stack fills the page": the front card is
  now at its bottom instead of near its top; a lone card sits at the same place. An expanded card (the thread, the queue) is
  not redesigned and still scrolls from the top (slice 5).
- **The card before the front one is not a strip.** It has sunk into the Dock, so it is reached by dragging up, Previous
  card or the arrow keys, not by a tap.
- **Rubber band replaces the hand-off.** Past the first or last card the stack used to hand the drag on to the Home gesture
  layer; nothing in the Stream consumed it, and the brief asks for a rubber band at both ends.
- **Stage and split.** They draw pages with the same `StreamPageView`, so they get the same bottom-anchored stack; no
  unfolded-layout work was done. The Stage rail's miniatures keep their own (decorative) clip.
- **Peek scale** is 4% per card about the top centre (the prototype narrows the card by 14 dp per step) so the real card is
  never reflowed during a drag.
- Removed: the old stack's `CardStackLayoutPolicy` use, `peek` rows, the `hollow` card variant and `StreamCardStackSpec`'s
  depth, offset and alpha tokens. `CardStack.kt` stays (the Notification overview prototype uses it).

**Left for 4b and later.** Shortcut stacks for pinned apps (`DockChoice.pageFor` still answers null for them), the combined
Today page, settings for inertia and magnet (slice 6; `DockMotionTuning.inertia` and `magnet` are ready), card tint and
images, the opened-card redesign (slice 5).

### Slice 4b: Shortcut stacks, Today and Recents as a stack (built)

**Built.**

- **Shortcut stacks for pinned apps.** `StreamSection.SHORTCUTS` is a derived section (never in a layout, never stored):
  `StreamShortcutPages.plan` (pure, `core:domain`) gives every pinned app with no live page one page, key
  `shortcuts/<package:profile>`, of `AppShortcutContent` cards (the new typed content: the shortcut's icon, short label,
  long label when it differs, a launch action; the "Open <app>" card is the same type with no shortcut id). They are the
  shortcuts the long-press menu offers, in the platform's rank order (`AppShortcut.rank`, sorted in
  `AndroidAppShortcutRepository`). Without the launcher role the page is the Open card and a gap, drawn as a notice card
  behind it ("Shortcuts need Riffle to be your Home app"). A pinned app that is live keeps its notifications page and has no
  second one. The pages sit in Dock order (`DockPageOrder.order(..., pinnedGroupKeys)`: pinned apps first, as the pinned
  icons are, then live apps, Now, the rest), so a swipe passes through them and the Dock rings the pinned icon
  (`DockRing`). The Stream opens on the first page that is not a shortcuts page, so it still opens on Now.
- **Pinned apps in hold-to-choose.** `DockChoice.pageFor` answers for `pin:<package>:<profile>` with
  `StreamFocusTarget.AppShortcuts`, which `StreamFocus.resolvePage` lands on the app's live page when it has one, else its
  shortcuts page. The pinned icons report their bounds like the dynamic entries (`LocalDockDynamicEntryBoundsSink`), so a
  press that starts on one, a selection that ends on one and the nearest-item rule all see them; `DockChoice.outcome` is
  unchanged (a selection on the item nearest where the finger landed still closes the open Stream). In the contextual mode a
  tap on a pinned icon switches to its page (`DockPinnedStream`, `LocalDockPinnedStream`) and the ringed one is
  `selected`; in the Home mode a tap still launches the app.
- **Loading and caching.** Shortcuts are the ones the launcher already reads into `LauncherShellState.appShortcutsByApp`
  when it refreshes the installed apps; the platform's `onShortcutsChanged` already triggers that refresh
  (`AndroidPackageChangeObserver`). The pages are built from that map only when the pinned apps, what is live, the map or
  the role change (`remember`), so nothing is loaded for a recomposition and entering the contextual mode reads no more
  than the long-press menu does. The shortcut's own icon is resolved lazily by the image loader (`shortcut-icon:` handle,
  `AndroidShortcutIcons`), falling back to the app's icon.
- **The Today page.** `StreamSection.TODAY` replaces Agenda and Tasks (one tile, Agenda's place in the order). It reads the
  calendar sources and the tasks; `TodayPlanner` (pure) composes the cards front to back: the next event (a live
  countdown from `TodayCountdown`, "Open in calendar", Hide), "To do today" (the first three tasks, "3 left · 1 overdue",
  complete, the add field, refresh), later events, a Tomorrow card (a tap opens its first event or task) and notices for the
  inputs that gave nothing. The evaluator reports those through `StreamSectionResult.Ready.gaps` (permission, no task
  account, tasks unavailable, a connected list with nothing on it), so Today draws what there is beside what there is not.
  The List view is the old Agenda list followed by the old Tasks list under headings. The countdown has its own ticker
  (`TodayCountdownText`) that wakes only when its words change.
- **Stored layouts.** Agenda and Tasks were `StreamSection` entries; they are now `LegacyStreamSections` ids that
  `StreamLayoutCodec` maps on decode: a layout that already names `today` keeps it and drops them; otherwise Today takes
  Agenda's place (Tasks' when there is no Agenda), is hidden only when every retired entry present was hidden, and takes
  Agenda's view (Tasks' when there is none). The options for both (`agenda`, `tasks`) still decode under their own keys and
  both Settings sub-pages are still there, under the one Today row. Encoding writes `today` only.
- **Recents as a stack.** `RecentAppCard` (icon, name, last used) replaces `RecentAppTile` and the `RecentAppsGrid`; the List
  view is unchanged. "Often used" is kept: the apps of the second recents group follow the recent ones in the pile with an
  "Often used" caption (and under its heading in the List); `RecentsOptions.frequentLimit` and the group are as before.
- **Stack entries.** `StreamStack` draws `StackEntry`s (`ContentEntry` for typed content, plus the checklist, the Tomorrow
  card and notices), so the cards composed around content are not content kinds.
- **Tests.** `core:domain`: `TodayPlanTest` (every data combination, overdue, the countdown at its boundaries and its
  ticker), `StreamShortcutPagesTest`, `StreamLayoutCodecTest` (the old layouts), `StreamSectionEvaluatorTest` (the gaps),
  `DockChoiceTest`, `DockContextualTest`. Robolectric: `DockPinnedStackTest`, `StreamBehaviorTest` and
  `StreamScreenshotTest` (Today, shortcuts). Device: `StreamShortcutsTodayInteractionTest` (swipe from a live page to a
  pinned app's shortcuts, a card launching through a fake launcher, a task ticked, Recents as cards) and
  `DockContextualRowTest` (the pinned tap and ring).

**What differed from the design.**

- **Existing layouts show Today as a List.** Agenda's stored view is taken as the brief says, and Agenda was a List by
  default (it is stored explicitly), so most existing users get Today as a List until they choose Stack. New layouts
  default to Stack for every section.
- **Tomorrow counts events only, in practice.** The Todoist source fetches today and overdue; tasks due tomorrow are counted
  when a source returns them (the card supports it) and are not fetched yet (deferred: a wider filter in `TodoistApiMapping`).
  Events after tomorrow are in the List only.
- **The Next event glance widget now observes the whole Today section**, since the Agenda section is gone, so it keeps the
  Todoist source refreshing while a Next event widget is on Home.
- **No separate load when entering the contextual mode.** The shell's map of shortcuts (loaded at app refresh, refreshed on
  `onShortcutsChanged`) is the cache; see above.
- **Shortcuts pages have no List view and no tile.** They are not a layout section, so "Show as", Move and Hide are not
  offered for them.
- **A "Retry" button on the stale note became the card's refresh button.** The To do card has no room for a second control;
  the refresh icon next to the add field does what Retry did.

**Left for later.** Settings (slice 6), card tint and images and the opened-card redesign (slice 5), Tomorrow's tasks, the
unfolded layout.

### Slice 5: Cards (built)

**Built.**

- **App-tinted cards** (`core:domain` `designsystem/CardTint.kt`; `GlassSurface`'s `cardTint`). A card's fill is its app's main
  icon colour (`rememberAppIconColor`, the same dominant colour the Dock's spotlight uses) at the tint strength, 22% by
  default, mixed over the card's base colour: one solid colour, no gradient, blur or offscreen layer. The edge stays the
  neutral glass edge. The text colour is computed from the resulting fill (white or black, whichever reads better) and
  recolours the theme's `onSurface`, `onSurfaceVariant` and `primary` inside the card. `CardTintTest` asserts at least
  4.5:1 over a black, a mid-grey and a white backdrop for very dark, very light, saturated and grey colours, at the default
  and the maximum strength, in both themes.
- **Open cards.** Tapping the front card of a conversation, the player, an event or a task opens it in place
  (`StreamStack`, `OpenCardFrame`): it grows from its browse size to its full content with the critically damped
  `OpenCardSpring` (constants in `DockMotionConstants`, frequency `DockMotionTuning.openCardOmega`), the cards behind stay as
  peeking strips above it (the stack translates so its front slot sits at the open card's top), and the stack and the pager
  take no drag while it is open. Closing: a tap on the card or beside it, a press of the Dock, Back, a change of page, the
  card going away (`OpenCardReducer`, pure and tested). Reduced motion: no growth, an 80 ms fade.
- **Actions in the Dock** (`DockActionsRow`, `CardActionControls`, `StreamCardActions`). While a card is open the Dock gains a
  Thick-glass row above its icons (below them on a top Dock): a reply field with send, Dismiss and Snooze for a
  conversation; previous, play or pause, next and the queue control for the player; "Open in calendar" for an event;
  Complete for a task. The row is part of the Dock's area, so the Dock grows upward and the room the Stream sees shrinks with
  it; it follows the window insets, so it and the open card stay above the keyboard. The same controls are the open card's
  accessibility actions. "On the card" puts them under the content (also the fallback where the Dock cannot hold the row).
  Pressing the Dock with a card open closes the card (the Dock dips and springs back, the Stream stays); Back and the Dock's
  accessibility action ("Close card") do the same.
- **Images.** The notification mapper now also captures a person icon (the newest message's sender photo) and the
  big-picture of a notification, into `NotificationImageStore`: memory only, never written anywhere (the persisted
  notification snapshot gains only two booleans, `hasPersonIcon` and `hasPicture`). They are bounded three ways: each is sized
  down when captured (a picture to 512 px on its long side, a contact photo to 128), the whole set is held to 8 MB and 8
  pictures by `NotificationImageBudget` (contact photos first, then the newest pictures), and the set is replaced at every
  listener change and cleared when it disconnects, so nothing outlives its notification. A card then loads an image sized
  to what it draws (`AndroidStreamImageLoader` never upscales) and holds it only while it is composed. Browse cards show the
  avatar (photo, else the large icon, else the sender's initials, else the app's icon) with the app's icon badged at its
  corner and one small thumbnail at the trailing edge; open cards show the picture in full under the thread and the artwork
  large. Avatars are decorative; a picture is described "Picture from <sender>" and never by what it shows.
- **Settings model** (`StreamSettings`, stored in `LauncherSettings.stream`; the settings page is slice 6): the tint strength
  (22%), a coloured glow on the top edge (off), where an open card sits (centre, just above the Dock, grows out of the Dock) and
  where its actions sit (in the Dock, on the card), with the reducer actions and the codec (`stream` section; old blobs
  decode to the defaults, an unknown placement reads as the default, the tint is held to 0 to 40).

**What differed from the design.**

- **The alpha is not the weight's.** A card cannot meet 4.5:1 over a white or a black backdrop at Regular's 16%, so a card
  takes Thick's colours (the dark theme's base a little deeper) and the lowest alpha at which its text passes, 60 to 80% in
  the dark theme and 48 to 62% in the light one at the default strength (up to 97% for a white-ish app colour at 40%).
  The tint is still the app colour at the strength mixed over that fill's colour. Cards, and so Glance widgets' cards,
  Today's cards and the Stage's thumbnails, are heavier than before; Thin and Regular are untouched elsewhere.
- **Cards no longer run the spotlight's edge glow.** It read as a coloured outline. The `edgeTint` strips and the spotlight
  are unchanged for the Dock, folders, Library tiles and search; a card's content still reports its colour to a spotlit
  surface it sits in. The optional top glow is the edge's tint strip along the top edge only.
- **Browse cards lost their buttons.** A notification's Reply, Dismiss and Snooze, and its thread toggle, are on the open card
  (the Dock's row, or the card); the player keeps its transport and scrubber on the browse card and gains the queue on the
  open one. Tests that clicked those buttons now open the card first (see the report of this slice).
- **Not every kind opens.** A recent app and a launcher shortcut have nothing more to show (a tap launches them) and an
  article card already shows what it has (a tap opens the article); Today's next event keeps its inline actions. A
  notification's tap used to launch the app; the open card has an "Open" button for that.
- **Mark read** is not offered for a notification (the platform has no such action); it stays on articles in the List view.

**Left for later.**

- **"Grows out of the Dock"** is stored and drawn as just above the Dock.
- **The media session's custom actions** ("where published") are not plumbed through the session snapshot, the item and the
  action gateway, so the player's row has the transport and the queue only.
- **A side Dock** (left or right) holds no row, and neither does the unfolded split: the actions are on the card there.
  A row along a side Dock's inner edge needs its own layout.
- **A person icon per message** (a thread with several senders' photos) and the conversation's own icon: the newest
  message's sender photo is the avatar.
- **The settings page** (slice 6): the tint, glow, open-card and actions controls.

### Slice 6: Settings (built)

**Built.**

- **Settings > Stream > Cards** (`SettingsPage.STREAM_CARDS`, `SettingsStreamCardsPage.kt`; a "Cards" row at the top of the
  Stream page). Card colour strength (0 to 40 percent, default 22) with a live preview card drawn by `GlassSurface` with the
  same `GlassCardTint` a Stream card gets, the top glow switch (off), where an open card sits (Centre, Above the Dock, Grows
  out of the Dock; Centre) and where its actions sit (In the Dock, On the card; In the Dock). They send the actions slice 5
  already had; "Grows out of the Dock" is offered, stored and drawn as above the Dock, and the page says so.
- **Settings > Motion > Dock and Stream** (`DockMotionSettings` in `core:domain`, `MotionSettings.dock`; defaults equal the
  old constants, so nothing changes until one moves): opening speed 100 to 400 ms in 10 ms steps (200), dock bounce 0 to 100
  percent in tens (50), card stack coasting 0 to 250 percent in tens (100), card snapping 0 to 100 percent in tens (40),
  hold time to choose 100 to 600 ms in 20 ms steps (220) and light ticks on or off (on). `DockMotionSettings.tuning()` is
  the `DockMotionTuning` the springs read: `LauncherHomeDestination` hands it to `DockMotionState` (the press, the springs,
  the closing plan, the hold time) and, as `LocalDockMotionTuning`, to the card stack (drag, fling, magnet) and the open
  card's growth. Reduced motion is resolved as before and still wins. The ticks switch wraps the shell's haptics
  (`withTicks`: detents and boundaries are dropped, everything else follows the feedback strength) for the stack and for
  choosing from the Dock. Stored as a `dock` object in the `motion` section: an old blob has no such key and decodes to the
  defaults, each value is clamped to its range, a mistyped section reads as the defaults.
- **First-run hint** (`DockPressHint.kt`, `HintSettings.dockPressSeen`, action `DismissDockPressHint`). One Thick glass pill
  beside the Dock, on the side the Dock opens toward: "Press the Dock down to see what's live" and a "Got it" button
  (a 48 dp text button; announced as a polite live region; no touch outside itself). It shows on Home when there is a
  Stream and the shell has asked for it (`onDockPressHintSeen` is non-null, which it is except while the setup card shows),
  and not while the Stream, the Library, a motion or a choice is up. The button, or the Stream opening by any route, stores
  it as seen and it is never shown again. There is no Simple mode or other reduced-interruption mode yet; it joins
  `shouldShowDockPressHint` when slice 7 adds one.
- **Accessibility audit.** Every gesture of slices 1 to 5 already had a non-gesture route except one: the press that starts
  on a pinned icon. Found and kept: Dock press (custom actions "Show live pages" / "Back to Home", "Close card", Ctrl +
  arrow), press on a live app's icon ("Show <app> in Stream" and Shift + Enter), hold or slide to choose (tap the tile or icon
  in the contextual mode, which is also what the actions open), card stack drag, fling and magnet ("Previous card" and
  "Next card" on the stack, a click on a peeking card, Enter on the front one), open card ("Open card", a tap or Enter, "Close
  card" on the card and the Dock), swiping between stacks (the pager's previous and next page actions and the tiles), the
  Dock row's scrolling (the platform's scroll actions) and the page options (their custom actions). Added: "Show <app> in
  Stream" as a custom action on a pinned icon in the Home mode (`DockShortcut`); in the contextual mode a tap does it.
- **Tests.** JVM, `core:domain`: `DockMotionSettingsTest` (defaults are today's tuning, clamping, every value reaches the
  tuning, speed, trampoline, inertia, magnet and hold time change what the motion functions do). JVM, `app`: codec
  (round trip, an old blob decodes to the defaults, out-of-range values clamped, hints), reducer (`SetDockMotion`,
  `DismissDockPressHint`), `DockPressHintTest` (when it shows, where it sits), `LauncherHapticsTicksTest`, and the action
  and page tests. On Robolectric, `StreamAndMotionSettingsTest` (a change on either page reaches the saved settings through
  the real reducer; screenshots dark, light and large type), `DockPressHintBehaviorTest`, and the Cards and Motion pages in
  `SettingsPagesScreenshotTest`.

**What differed from the design.**

- **Behind the cards and pinned apps in the Stream are not built.** Amendment 3 superseded the second Dock row (the
  contextual mode always shows the pinned icons), so "Pinned apps while in Stream" has nothing to switch; "Home blurred and
  dimmed" needs a blur, which new drawing may not use, and there is no stored concept of either. Left out rather than
  invented.
- **The Cards page is a sub-page** of Stream (`SettingsPagesTest` lists it among the Stream's children on purpose).
- **Haptics.** The feedback strength setting (Off, Light, Medium, Strong) stays; the new switch only silences the ticks.
- **The hint is not drawn in a host that does not ask for it**, so existing Home tests and goldens are unchanged.

**Left for later.** Behind the cards, "Grows out of the Dock" as a real growth, a Simple mode exclusion for the hint, and a
device test of the hint (the behaviour is covered on Robolectric).

### Slice 7a: Home handle and search from the Dock (built)

**Built.**

- **One handle pill** (`HomeHandlePill.kt`; pure part in `:core:domain` `launcher/home/HomeHandle.kt`). The Home search pill and
  the page indicator are one small Thin glass pill (36 dp drawn, a 48 dp touch target) in the row just above the Dock
  (`HomeBottomSearchArea`): a search glyph, then the page dots when there is more than one page. A tap anywhere on it opens
  search (the platform's `detectTapGestures`). Dragging along the dots is the old indicator's quick-drag unchanged
  (`PageIndicator`: the roundel follows the finger, each page crossed is shown at once, release commits); the position to page
  mapping now lives in `HomeHandle.pageAt` / `offsetOf` and `pageIndicatorDragTargetIndex` delegates to it. The indicator is
  no longer shown only while a page gesture runs (its idle-hide timer is gone): the dots are always there. The Dock's own handle pill stays removed. Other Dock edges keep the row where it was; a first-run hint
  above a bottom Dock now leaves the handle's row clear (`dockPressHintOffset(clearance)`).
- **Search from the Dock** (`search/DockSearchSurface.kt`, `DockSearchMotion` in `:core:domain`). The shell's `SEARCH`
  destination (the handle, the Dock's "Search" action, swipe down on Home and `OPEN_SEARCH` all reach it) is drawn by
  `LibraryHomeSurface` as `DockSearchSurface` instead of the Library sheet: the field on its own Thick glass panel on the Dock's
  edge, the results above it on a second Thick panel that is as tall as its content up to the room there is, the best match
  nearest the field (`SearchResultList(growsUp = true)`: a reversed lazy list or grid), "Search web" between them. The surface
  is laid out above the Dock, which stands above the keyboard, so the field and the results stay clear of it. It rises from the
  Dock's edge on a critically damped spring at `DockMotionTuning.omega` (the opening speed setting applies), alpha first, a
  36 dp rise and a 0.94 scale about the Dock's edge; closing (a tap on the room around it, Back with an empty field, the "Close
  search" action) runs the same spring back and only then tells the shell. Reduced motion: the 80 ms fade
  (`DockReducedTimeline`). Back with text clears the text first. Launching a result closes at once, as the Library does.
- **Non-gesture routes.** Tap on the handle; the handle's "Search" button node (48 dp); "Search" as a custom action of the Dock
  (last in its list, after "Open Library" and the Stream action); the page selector keeps its set-progress action and gains
  "Previous page" and "Next page" (only where there is such a page).
- **Tests.** `:core:domain`: `HomeHandleTest` (the track to page mapping and its inverse, left to right and right to left,
  clamping, steps, sizes), `DockSearchMotionTest` (the spring never overshoots, closing mirrors opening, a caught opening, speed,
  the frame, reduced motion). Robolectric: `HomeHandleBehaviorTest` (the pill, no old search pill, 48 dp, tap, the accessibility
  routes, quick-drag, the Dock press still opens the Stream with the handle there, search from the handle, the field on the Dock's
  edge, best match nearest, closing by action, tap and Back, over the Stream, the motion) and `HomeHandleScreenshotTest` (Home
  with the handle, one page, a side Dock, beside the first-run hint; search empty, as a list, as icons, nothing found, large
  type; dark and light). Device: `HomeHandleInteractionTest` (tap opens search, quick-drag, the search field).

**What differed from the design.**

- **Search is not inside the Dock.** The ADR says search "opens inside the Dock" (Amendment 1) and, unfolded, as a panel attached
  to it. Folded it is a field panel and a results panel standing on the Dock's edge, rising from it; the Dock stays drawn and
  tappable under it. "Inside" the Dock's own glass would mean growing the Dock past its size and its press gesture.
- **Swipe down and `OPEN_SEARCH` now open the Dock's search**, not the Library sheet with its field focused (the ADR's first
  table says Library; Amendment 1 says search from the Dock). The Library keeps its own search field when it is open.
- **Home fades under search**, as it does under the Library, so the handle does not show through the glass.
- **The Library's filters are not on the Dock's search** (the Library has none while searching either).

**Assumptions.** The handle is also shown on a single page, as the glyph alone (nothing to scrub). A tap on the dots searches
too. The pill sits where the old pill was, in the Home column above the Dock's reserved room, on every edge; "in front of the
Dock's middle" on a side Dock is Amendment 2 and left for the unfolded slice (nothing here stops it: the pill is its own
composable and takes no position from the Dock).

**Left for later (7b).** The paged Library, Home mode, the feature switches and Simple mode, and the hint's Simple mode exclusion.
Keyboard behaviour is covered by layout (the surface is padded by the same insets as the Dock) and was not run on a device.

### Slice 7b: Paged Library, Home mode and feature switches (built)

**Built.**

- **The paged Library View** (`library/LibraryPagedView.kt`; pure part `LibraryPaging` in `:core:domain`). `LibraryView.Paged`
  (stored id `paged`, "Pages" in "Show as") draws every app as an icon on horizontal pages of the platform's `HorizontalPager`,
  straight over the wallpaper with no sheet, header or search field behind it. Each page is Home's grid: the same columns and
  rows (`homeLayout.selectedPage.grid`), margins, square cell size (`HomeGridLayoutMetrics`), icon size, notification badge and
  label (`WallpaperReadableLabel`), so it reads as Home's sibling. Below the pages stands the one handle component, unchanged:
  `HomeHandlePill` (a tap searches, dragging along the dots quick-drags between pages, "Previous page" and "Next page"
  actions). Tapping an app launches it (and closes the Library, as before); long-press opens the Library's app menu. A
  long-press on the empty space opens "Show as" with Undo (the sheet's own snackbar and `onShowAs`), and the same routes are
  custom actions of the pages ("Close Library", "Show as <view>"). The Library keeps its pull away from the Dock's edge and
  its motion (the same translating layer in `LibrarySheet`); a pull toward the Dock's edge on the pages (or, on a side Dock,
  on the handle's row, so the pager keeps horizontal drags) closes it by the same thresholds as the header's. Search stays the
  Dock's search: the handle opens it. The other Views are as they were and list "Pages" in "Show as" and Settings > Library.
- **Home mode** (`HomeMode`, `HomeBehaviourSettings.mode`; `AppLibraryHome` in `LauncherHomeDestination.kt`). Placed items
  (the default) or App library. In App library mode the Home surface is the paged View, laid out as Home is (inside the Dock's
  room, under the Dock, inside the insets); the placed layout is not touched and is back when the mode is. Home's gestures
  still work except the ones that act on the placed pages (`HomeGestureSettings.forAppLibraryHome`). Long-pressing the empty
  space offers "Show placed items" and "Settings" (custom actions too). Settings > Home > "Home shows". Stored as
  `home.mode`: an old blob has no such key and decodes to Placed items, as does any unknown value.
- **Feature switches and Simple mode** (`FeatureSettings`, `EffectiveFeatures`; `settings/FeatureSettings.kt`). The ADR
  lists three switches and Simple mode, and these are exactly what was built: **Stream** (Settings > Stream), **Show live
  apps** on the Dock (Settings > Dock) and **Library** (Settings > Library), each on by default, and **Simple mode**
  (Settings > Home), off by default. Simple mode is an override, not a copy: while it is on the effective features are Stream
  off, live apps off, Library off and Home mode App library, and turning it off gives every stored switch and the Home mode
  back untouched. Stored as a `features` section (an old blob decodes to the defaults).
  - Off removes the entry points: the Stream is not handed to `HomeDestination` (no press, live pages, tiles, "Show live
    pages" action, "Show <app> in Stream" action or hint); live apps off leaves the contextual tiles and the press but draws no
    live app or now playing entry (`rememberDockDynamicSide(showLiveApps)`); Library off removes the pull away from the Dock
    (`DockPullGestureHost.canOpenLibrary`; the single pointer loop is not otherwise changed), the Dock's "Open Library"
    action and Ctrl + Shift + the arrow; the navigation reducer refuses `OpenStream` and `OpenAppDrawer` while off (so a
    bound gesture, Glance or any other route changes nothing), and a Home gesture bound to "Open Stream" or "Open Library" is
    not offered while its feature is off (`HomeGestureSettings.without`). Search is not the Library sheet and stays.
  - The settings rows read the switch as off and disabled while Simple mode is on, and say so.
  - The slice 6 first-run hint does not show in Simple mode (`shouldShowDockPressHint(simpleMode)`).
- **Settings rows.** A switch row is now one toggle for the whole row (`SettingsRows.kt`), so a screen reader hears its state;
  it looked the same before.
- **Tests.** JVM, `core:domain`: `LibraryPagingTest`, `FeatureSettingsTest` (defaults, Simple mode overriding and giving
  back, gesture unbinding, stored ids), the reducer's navigation refusals, `LibraryViewTest`. JVM, `app`: codec (home mode and
  switches round trip, old blobs decode to today's behaviour, the paged view round trips), reducer, action domain and hint.
  Robolectric through the real `HomeDestination` and `SettingsSurface`: `PagedLibraryTest` (no sheet or field, Home's icon size,
  the handle, swipe, quick-drag, launching from either page, the app menu, "Show as" with Undo, the custom actions, the
  dismiss pull; screenshots dark, light and the second page), `HomeModeTest` (the app library on Home, switching back
  restores the untouched layout, Simple mode; screenshots dark and light), `FeatureSwitchesTest` (each switch's entry points
  gone and the others kept, the hint, Simple mode and back), `FeatureSettingsPagesTest` (the rows and their actions; screenshots).
  Device: `PagedLibraryInteractionTest` (compiled, not run).

**What differed from the design.**

- **Paged is not the new default.** Amendment 1 says the Library is pages of every app and the categories sheet is dropped,
  but decision 8 made Categories the default, stored layouts keep their View, and nothing tells a new install from an old one
  with no stored view. Paged is a View in "Show as" and the Settings > Library list; the default stays Categories and the
  categories sheet is kept. Making Paged the new-install default needs a stored marker for "new install" and the Library
  goldens and tests that rely on the default re-recorded.
- **Simple mode is where the Home mode is** (Settings > Home, first), not a page of its own: settings are organised by Place and
  the ADR names no place for it.
- **The Stream switch takes the live apps with it** (they come from the Stream's sources), so "Show live apps" is only
  meaningful while the Stream is on.

**Assumptions.** Simple mode turns the Library sheet off, since the app library is Home there; search (the handle) stays, and
Settings stays reachable by the Home gestures and the long-press menu. The paged View's grid follows the selected Home page's
grid, as the Library's grid search results already do. The pull-away claim is dropped, not redirected, when the Library is off.

**Left for later.** Making Paged the default for new installs; Simple mode's own first-run explanation; the "More apps" overflow
button of a generated Home page still sends `OpenAppDrawer` (it does nothing while the Library is off); the unfolded layout
(Amendment 2). The paged View was not run on a device: the handle sits over the wallpaper with no glass behind the grid, so
label legibility on a bright wallpaper rests on the labels' own shadow (`WallpaperReadableLabel`).

### U1: Layout per posture (built)

**Built.**

- **The settings** (`core/domain/.../launcher/settings/LayoutSettings.kt`, `LauncherSettings.layout`). `LayoutPosture` is folded or unfolded: the
  phone (portrait and landscape) is folded; a foldable opened flat, a tablet and a desktop window are unfolded (a tablet is the
  same kind of wide, flat screen). `PostureLayoutSettings` holds, per posture, the page scroll (`PageScrollDirection`:
  sideways or up and down), the indicator (`PageIndicatorPlacement`: beside the Dock, or off) and quick scroll (on or off);
  `LayoutSettings` holds both postures and a device class reads its posture's (`forDeviceClass`).

  | Setting | Folded default | Unfolded default |
  | --- | --- | --- |
  | Dock edge | Bottom | Right (a tablet and a desktop window keep Left) |
  | App pages scroll | Sideways | Up and down |
  | Page indicator | Beside the Dock (above its middle) | Beside the Dock (in front of its middle) |
  | Drag the dots to scroll quickly | On | On |

- **The Dock edge is not copied.** It is still the Dock's own `DockModel.position`, stored per device class with the home layout,
  and null means "no choice yet", which now resolves to the posture's default (`defaultDockEdge`; the unfolded foldable's was
  Left and is Right). The Dock page and the Layout page write the same value: the Layout page runs the Dock page's own edit
  (`SelectPostureDockEdge`, in `LauncherDockEditReducer`) against the posture's device class (`dockDeviceClass`: the class being held when it
  is in the posture, else the phone for folded and the foldable for unfolded) and puts the Dock page's own tab back.
- **Stored** as `layout.folded` and `layout.unfolded` in the settings blob (`pageScroll`, `indicator`, `quickScroll`; ids are
  stable). An old blob has no `layout` and decodes to the defaults; a missing posture, an unknown value or a value of the wrong
  type reads as that field's default. A stored Dock edge keeps winning over the new default.
- **The handle** (`HomeHandlePill`, the same component, with an `axis`). It runs the way the pages scroll (Amendment 2: the
  indicator always runs the way the pages scroll), so a quick-scroll drag never moves the pages at right angles to it: a row
  for sideways pages (the glyph, then the dots), a column for pages that scroll up and down (the glyph on top, the dots
  stacked, page 0 at the top). The dots' drag axis is the pages' axis (`PageIndicator(axis)`; `detectVerticalDragGestures` for a
  column). Its size is fixed by `HomeHandleLayout.sizeDp`, so the glyph and the dots keep the same room at every page count.
- **Where it stands** (`HomeHandleLayout.stripFor(edge, axis)`). Above a bottom Dock it is the row it always was. A column
  of dots beside a left or right Dock stands in a column of its own between the Dock and the pages (`HomeHandleStrip` in
  `StandardHome.kt`), as wide as the handle and centred vertically; the Dock is centred on its edge, so the handle is in
  front of the Dock's middle, on Home's side of it, as the folded handle is above a bottom Dock's middle. A row beside a
  side Dock (sideways pages with a side Dock) runs across the Dock's edge, so it keeps the row below the pages. The pages
  give the room up, so the handle never covers an icon. While pages are edited there is no handle, as before.
- **Vertical pages** are the platform's `VerticalPager` with the same page content (`ImmediateWorkspacePager(vertical)`); a drag
  of an icon held toward the page's far ends turns the page along the same axis.
- **Quick scroll off** keeps the dots as an indicator only: no drag, no set-page action on the page selector (no previous/next
  page actions either), and a drag that lands on the dots is taken by the indicator (a no-op `draggable`), so it does not turn
  into one of Home's swipes. A tap on the dots still opens search. **Indicator off** draws no dots; the pill is its glyph alone
  and still opens search.
- **The paged Library and the App library Home** reuse the same pager rules and the same handle placement
  (`LibraryPagedView(dockEdge)`, `LibraryState.pagedLayout`). The Library's pull toward the Dock's edge takes the whole View
  unless the pages scroll along that very axis, and then the handle's room only (as it did for a side Dock before).
- **Search** keeps rising from the Dock's edge, as on a bottom Dock: the field and the results are laid out in the room beside a
  side Dock, the field at the bottom, the results above, sliding in from the Dock's side. That stays readable and works on every
  edge; the amendment's search panel attached to the Dock, with the field nearest the keyboard, goes with the card stack in U2.
- **The Dock's gestures are untouched** (the single pointer loop in `DockPull.kt` is not changed): press toward the edge, pull away
  to the Library and the contextual row's scrolling along the Dock work on every edge in both postures. Hold to choose stays
  the folded phone's, as it was (an expanded posture's press stays the split's settle).
- **Settings > Layout** (`SettingsPage.POSTURE_LAYOUT`, `SettingsLayoutPage.kt`; the enum is not `LAYOUT`, a name an earlier removed
  page used and a test pins as gone). A Folded section and an Unfolded section, each with the Dock position (Bottom, Left,
  Right), App pages scroll (Sideways, Up and down), Page dots (Beside the Dock, Off) and a Drag the dots to scroll switch
  (off and disabled while the dots are off). Every row is a choice or a switch, so each has a route that needs no gesture, and
  each is a `SettingKey` for search. It is the eleventh row of the main page, after the Dock.
- **Two small fixes.** (1) In the handle pill the search glyph crowded the selected page dot with two or three pages (the paged
  Library's golden shows three of its pages): the roundel was a 44 dp box that the indicator's tight constraints squeezed to the
  length of a short track and then centred, which moved it left of its dot, onto the magnifier. It is now exactly as big as it
  draws and rides the dots' centres, at every page count. (2) The "More apps" button of a generated Home page sent
  `OpenAppDrawer`, which does nothing while the Library is off: it now opens search then (the Library is off in Simple mode and
  with its switch; search stays on), which finds the rest of the apps.
- **Tests.** `:core:domain`: `LayoutSettingsTest` (postures, defaults, independence, what the indicator and quick scroll do, the
  device class edited for a Dock edge, stored ids), `HomeHandlePlacementTest` (the strip per Dock edge, the size and reserved
  room per axis and page count, the drag-to-page mapping on a column). `app` JVM: `LayoutSettingsCodecTest` (round trip, old
  blobs, bad values, a stored Dock edge winning over the new default, the Layout page's edges), the reducers, the action table,
  the settings pages tests. Robolectric through the real `HomeDestination` and `SettingsSurface`: `UnfoldedLayoutTest` (each Dock
  edge places the Dock and the handle, vertical paging by a swipe and by the handle, quick scroll off, indicator off, the Dock's
  press, pull and hold on a right edge, the paged Library and the App library Home following the settings; screenshots of Home
  with the right-edge Dock and of the Layout page, dark and light), `HomeHandleSpacingTest`, `GeneratedPageOverflowTest`. Device:
  `UnfoldedLayoutInteractionTest` (compiled, not run).

**What differed from the design.**

- **"In the Dock" is not an indicator placement.** The amendment lists Beside the Dock, In the Dock and Off; Amendment 3 has one Dock
  with no handle or marker row of its own, so there is nothing to put the dots into. The options are Beside the Dock and Off.
- **Off keeps the search glyph.** The handle is the way into search as well as the dots; with the dots off the pill is its glyph.
- **The indicator runs the way the pages scroll**, as the amendment says, so a column can stand above a bottom Dock and a row
  beside a side Dock. A row beside a side Dock is not given a strip of its own: a strip as wide as the row (up to about 100 dp
  with a few pages) squeezed the pages beside the Dock and clipped their labels in a screenshot, so it keeps the row below the
  pages, where it was. A column above a bottom Dock stands in the row above it, which is as tall as the column is long (92 dp
  with three pages).
- **A tablet and a desktop window take the Unfolded settings** (and keep their left Dock); the amendment speaks of an unfolded
  foldable only.
- **Vertical one-finger swipes on Home scroll the pages** while the pages scroll up and down and there is more than one, as a
  sideways swipe does for sideways pages (the pager takes the touch first). Search stays on the handle, the Dock's action and
  the gesture settings.

**Assumptions.** The Dock edge a posture's section edits is stored per device class, so on a landscape phone, a tablet or a
desktop window the section edits the class being held (the phone's landscape layout has its own edge). The Library's Dock edge
follows the Home edge when it is chosen (as it did). The first-run hint clears a vertical handle's length above a bottom Dock.

**Left for U2** (all built, see below). The card stack attached to the Dock, the grid and cards ratio, the continuously flowing and
fading grid, app peek-through, and search as a panel attached to the Dock; the Stage view and the split are gone.

### U2: The stack attached to the Dock (built)

**Built.**

- **One Stream, one motion.** Unfolded (a foldable opened flat, a tablet, a desktop window: `LayoutPosture.UNFOLDED`) the Stream is
  the same contextual mode as folded: pressing the Dock toward its edge, a tap on a live icon or a tile, the accessibility
  action and the keyboard all run the Dock's trampoline and the fan (`DockMotionState`, slice 6's tuning and reduced motion
  apply), and closing is the opening reversed. What changed is where the cards stand and that **Home stays**: it is composed
  and drawn at full opacity under and beside the Stream (`HomeDockPullBinding`, `StreamPullInputs.homeStays`; no fade or scale
  with the trampoline, it still fades under the Library and under search as it does folded).
- **The column** (`:core:domain` `launcher/stream/StreamColumn.kt`, pure; `StreamColumnHost.kt`). The reused `StreamSurface` (the
  same pager of upright stacks, `StreamStack`, drag, fling, magnet, tints, images, the Dock's ringed icon, the open card) in a column
  attached to the Dock's inner side: a third of the screen's width at rest (320 dp at least), up to half (440 dp at least) with a card
  open; vertically its bottom is at the Dock's lower end (the live pages are there), 448 dp tall at rest (the tallest front card, the
  cards peeking above it and the gap under it) and up to 480 dp with a card open. Beside a left or right Dock the column's inner edge
  is the Dock body's inner edge (`LocalDockBodyBoundsSink`, `HomeDockHostState.bodyBounds`, captured at rest so a press's dip does
  not move it; a 4 dp sliver and the Stream page's own 16 dp side padding are accounted for, so the cards stand against the glass);
  above a bottom Dock it is centred on the Dock and stands on it. The column's rectangle is read in layout (`dpBounds`), so it
  widening as a card opens relays out and recomposes nothing. It never crosses a vertical hinge (the open width is cut back).
  It takes the touches of its own rectangle, under the Dock in the draw order.
- **Opening a card.** The column eases (smooth spring, a snap under reduced motion) to its open size, the Dock side and the bottom
  staying put, and the open card grows from where its browse card stood toward the middle of the screen (`OpenCardPlacement`
  is read as "above the Dock" in the column whatever the setting says: it grows, it does not travel to the column's centre). A side Dock
  holds no actions row, and a bottom Dock's would stand under the column, so the actions are on the card.
- **The grid fades, it is never cut** (`StreamGridFade`, pure, tested; `GridFadeField`, `Modifier.gridFade`). Home's grid keeps its
  full width. Each cell (the placed grid's and the paged Library's) reads its own opacity from where it stands: untouched beyond 144 dp
  of the column, easing (a smoothstep, so no step) to 22% under it with peek-through, or to nothing without. Drawn as a per-cell
  `graphicsLayer` alpha read in the draw phase: no blur, gradient, offscreen layer or blend mode. The fade comes in with the Stream, and the
  22% goes out as a card opens (dense text over faint icons was unreadable). A cell exposes the opacity it is drawn at as a semantics
  property (`GridFadeAlphaKey`), for tests.
- **App peek-through** (`PostureLayoutSettings.appPeekThrough`, stored as `layout.<posture>.appPeekThrough`, **on by default**; Settings >
  Layout > Unfolded > "Apps behind the cards"; `SettingKey.LAYOUT_UNFOLDED_APP_PEEK_THROUGH` for search). An old blob has no key and
  decodes to on. Only the unfolded posture reads it.
- **The handle** stays in front of the Dock's middle while the Stream is closed. While the column stands (Stream out) the pill is not
  drawn and its room stays, so nothing moves: the column stands where the handle does, and folded the handle goes with Home when the
  Stream is out. Pages still swipe with the Stream out; the handle's quick scroll and its "Page selector" actions are back with Home.
- **Search** (`DockSearchSurface(attach)`) unfolded is the column's width, against the Dock's inner side, field at the bottom, results
  above, rising from the Dock as folded (`StreamColumn.restWidthDp`).
- **Replaced and removed.** The split pane (`SplitSurface`, `SplitPaneState`, the divider, `SplitMode`, `SplitGeometry`, `SplitSettle`,
  `SplitReveal`, `StreamSplit` and `StreamLayoutAction.SetSplit`), the Stage view (`StreamStageView`, `StreamStage`, the rail, the
  slim bar and the Stream's own menu `StreamMenu`) and the two-pages-side-by-side Pages view (`StreamPagesPerView`,
  `StreamWideView`, `StreamLayoutAction.SetWideView`, the "Show the Stream as" row in Settings > Stream, the strings), and the Dock pull's
  split settle (`SplitPullSetup`, `DockPullState.splitting`, `reveal`, `onSplitSettled`). Deleted, not left compiling.
- **Stored data.** `StreamLayoutCodec` no longer reads or writes a posture's `split` and `wideView`: a layout stored with either decodes
  with its sections and options intact (a Stage or Pages choice, a split fraction or a bad value of either is ignored) and is written
  back without them. `StreamLayoutCodecTest.aLayoutStoredWithTheSplitAndTheStageViewStillDecodesAndTheyAreIgnored` pins it.
- **Hold to choose and the press** are now on every posture, as the folded phone's: the unfolded press was the split's settle and is
  the trampoline now.
- **Tests.** `:core:domain`: `StreamColumnTest` (the width per ratio and floor, the column against a right, a left and a bottom Dock,
  the measured Dock's bottom and the dip not moving the edge, opening, a short window, a hinge), `StreamGridFadeTest` (no column, under
  it, clear of it, no step, above and below, peek-through against off, presence, an open card), `LayoutSettingsTest` (the setting),
  `StreamLayoutCodecTest` (old data). `app` JVM: `LayoutSettingsCodecTest` (round trip, an old blob, a bad value). Robolectric through the
  real shell content, `UnfoldedStreamColumnTest` (a press on a right Dock opens the stack attached to it, Home stays at full opacity and
  its grid spans the width, the fade under the column and its absence clear of it, peek-through off, paging with the Stream out, the handle,
  opening a card (wider, taller, actions on the card), closing by press, Back and the accessibility actions, a left Dock mirrored, a
  bottom Dock, search in the column's width; screenshots dark and light of the stack with peek-through on and off, an open card, a left
  Dock, a bottom Dock and search). Device: `UnfoldedStreamColumnInteractionTest` (compiled, not run).

**What differed from the design.**

- **The ratio is a constant, not a setting.** The owner wanted to experiment with ratios; the ADR lists no setting for it, so it is
  `StreamColumn.REST_FRACTION` (a third) and `OPEN_FRACTION` (a half), with floors in dp so a card stays readable on a 840 dp window.
- **The column holds a bounded height, not the whole edge.** A full-height column would have covered the handle and hidden the grid
  where there are no cards; the stack's rest height is fixed instead, which also lets the fade stay under the cards.
- **The handle is hidden while the Stream is out** (see above) instead of standing "below the dots": the amendment's picture has the
  stack below the dots and an open card covering them, and a stack of one card plus three strips is taller than the Dock's lower half on
  an 840 dp window. Hiding is deterministic and closest to folded.
- **The press on a bottom Dock unfolded** has its card actions on the card, not in a Dock row (see above).
- **Stage, two pages and the split are gone with no stored equivalent**: there is no unfolded view choice left.

**Assumptions.** The column is anchored to the Dock's lower end rather than to its live icons exactly. A tablet or desktop window with a
left Dock gets the same column. The grid cell of a wide widget fades by where its first cell is. Hinges are read from the window layout the
activity already tracks (`adaptiveStageWindowLayout`); a horizontal (tabletop) hinge is not avoided.

### Glass and Dock sizing (after slice 4a)

A visual slice between the dock-modes slices; it changes no gesture.

**Built.**

- **The flat glass, lighter.** One flat translucent fill, lit only at the edge (a 1 dp border brightest at the top, an
  inner top hairline, strips of an app's colour along the top and bottom): the restyle of `551f194`, re-applied on top of
  the current code and made lighter and more see-through with the owner's signed-off values (Thin 12%, Regular 16% of a
  light blue-white, Thick a darker 56%; the details are in `design-language.md`). The Dock's "Dock background" setting
  maps its default (72%) to the Thin fill. The shader, backdrop capture, sheen and the Frost and Refraction settings are
  gone (stored values decode and are ignored); reduced transparency stays and now uses the Thick fill.
- **Defensive drawing.** The edge lights draw nothing under 1 px or for a non-finite size, clamp the outline's radii,
  fall back to a rectangle for a malformed outline and keep gradient stops strictly increasing; `GlassEdgeOutlineTest`
  and `GlassDegenerateSizeTest` cover 0 x 0, 1 x 1, hairline-thin and oversized-radius surfaces.
- **Contextual Dock sizing.** One row of equal 48 dp items (pinned, live and tiles), the gap closed up until six whole
  items fit a 360 dp phone, a programmatic scroll that stops on a whole item, and trailing padding so the row's end does
  too (`DockRunGeometry`, `DockRunScroll.toRevealWhole`). The Home mode's size and icon size are unchanged.

**What differed.**

- `551f194` made CI's device job die at about test 38 of 218 (`DockDynamicSectionTest`); the cause was never found. Part
  of this slice is a read of that drawing code for degenerate-size and software-GPU hazards; see the change's notes. The
  restyle should be watched on the next device run.
- The Dock's thickness still follows its icon size setting in the contextual mode, so a Dock set to 56 dp holds 48 dp
  items in a 56 dp-deep strip.


**The emulator and the edge lights (found on CI's device job).** With the flat glass, the device job's emulator
went away during the first test that drew a Dock holding icons: no app exception, signal, ANR or low-memory kill
was logged; the emulator stopped logging and then was gone from adb. A temporary device probe that drew the
glass one piece at a time, in a fixed order and first in the run, showed:

- Glass surfaces alone (with an edge tint, with the spotlight), the Dock's background alone and an empty Dock
  draw without harm.
- A Dock with pinned apps is harmless with the edge lights off (with or without the spotlight), and with only
  the hairline, only a solid border or only the gradient border.
- The same Dock with the hairline and the gradient border together took the emulator down, on its first frame
  in five runs out of six.

So no single call is at fault; more edge light on a Dock with icons makes the emulator's default software
renderer (`swiftshader_indirect`) more likely to die. Two changes came of it:

- The border's ramp is drawn as a few solid strokes clipped to bands (`borderBands`) rather than one stroke
  with a gradient brush. This made the death rarer but did not remove it.
- The device job runs the emulator on its ANGLE-based software renderer (`-gpu swangle_indirect`, in
  `.github/workflows/ci.yml`). On it the same frames draw and every device test runs.

Nothing here has been seen on a phone's GPU. If the default renderer is wanted back, the first thing to try is
the Dock with a plainer edge (a border and no hairline), which every probe survived.

**Search on Thick.** With the run completing, `LauncherSearchSurfaceTest` showed the search field and results
(Regular glass) no longer contrast with a black wallpaper in the light theme. Both are dense text and are now
Thick.

## Device fixes, round 2

Five things the owner found on a phone, each one commit.

- **A pinned app that is also live is drawn once.** The Dock drew its pinned icon and a live entry for it. The live entry is
  left out (`DockDynamicSide.entries(pinnedGroupKeys)`, so the pinned app does not use a live slot either; both Dock modes);
  the pinned icon already carried the badge, the ring (`DockRing` falls back to it for a live page), the tap in the
  contextual mode, the press start and hold-to-choose, and all of them land on the app's one page. Identity is
  `package:profile`, as the Stream's pages, so a work-profile clone is a different app. That one page leads with the app's
  notifications (newest in front, each opening in place with reply, dismiss and snooze) followed by its shortcut cards
  (`StreamShortcutPages.merge`); an app with nothing live keeps its shortcuts page. `DockPageOrder` puts the page at the
  pinned icon's place in the run. A request to open a pinned app's page waits while the Stream's notifications are still
  loading rather than landing on the shortcuts page that the live page replaces. Now playing keeps its own entry: it stands
  for the Now page, which is not the app's notifications page.
- **The Dock no longer shifts when the open page changes.** The row's reveal of the ringed icon used computed geometry and
  stopped on whole item starts, so an icon already in view could still move the row, a clipped one moved by more than
  needed, and a ringed pinned icon was never revealed at all. `DockRunScroll.toReveal` (pure, tested for visible, clipped
  at either end, off screen either side, first and last item) leaves the row alone for a wholly visible icon, else moves
  by the least that shows it whole with a 24 dp peek of its neighbour on the side it came from. The row measures the icons
  and its viewport (the ring is a border drawn over the icon, the selection scale a graphics layer, so no layout depends on
  the selection) and reveals only when the ringed icon changes, on every edge. The old `toRevealWhole` and its stops are
  gone.
- **Cards have their spotlight back.** The harsh thing in the design canvas was a uniform app-coloured border, not the
  spotlight. A card's icon or avatar now lights the nearby part of its edge, softly, falling off with distance
  (`GlassSurface(spotlightLocal)`, `glassSpotlightLocalProfile`: a bell 72 dp either side of the source, nothing beyond),
  over the tinted fill and with its text contrast unchanged; the rest of the edge is the neutral glass edge. The same baked
  glow image as the Dock (no blur, offscreen layer or gradient stroke), halved under reduced transparency. The top-glow
  setting is the edge tint strip along the top edge; it is drawn too, under the spotlight, so with both on the top edge has
  the app's strip and the local light on it. A card with no app (Today's) has no source and so no light. The player's
  card, whose artwork covers the glass, lights nothing a viewer can see.
- **The page handle beside a side Dock is always a column in front of it.** With sideways pages the handle stayed a row at
  the bottom of the page. `HomeHandleLayout.stripFor(edge)` is now the column for a left or right Dock, `axisFor` runs the
  pill down it whichever way the pages scroll (a drag along it scrubs in page order; "Page dots: Off" leaves the glyph), and
  its room comes out of the pages' margin on the Dock's side first (`remainingMarginDp`), so the grid gives up only what the
  margin could not hold. The paged Library and the App library Home share it. Stored layouts decode to up and down pages
  unfolded (`LayoutSettings.UNFOLDED_DEFAULT`; old blobs and unknown values read as the default): sideways pages unfolded
  come only from a stored `layout.unfolded.pageScroll` of `horizontal`, that is the Layout setting, so nothing was changed.
- **The handle is drawn as the Dock's material.** The pill uses the Dock's own `dockSurfaceAppearance` (its "Dock
  background" fill and colour, clear at 0%, reduced transparency, the same edge lights) with the Dock's corner capped at the
  pill's half height (a 32 dp Dock corner keeps it fully rounded).

## Device fixes, round 3

Six things the owner found on a foldable (unfolded, Dock on the right edge), each one commit.

- **Swipe down no longer reaches the handle's drags, and nothing opens the old notification overview.** A drag that
  starts on the page handle (along it, across it, on the search glyph, on a single page's bare pill) is consumed by the
  handle (`HomeHandlePill`: a drag detector beside the tap detector; the scrub along the dots still takes it first), so
  Home's one-finger swipes can no longer fire from it (a drag down a column handle was "swipe down", which opens
  search). The gesture binding `OPEN_NOTIFICATIONS` is removed (`LauncherGestureAction`, the Gestures page, the mapper); a
  stored name decodes to no action like any removed one (`LauncherGestureSettingsJsonCodecTest`), and the old default of
  two fingers up goes with it (now unbound). Swipe down was never bound to notifications by default: it opens search
  (`OPEN_SEARCH`), which stays the default, and over vertically scrolling pages the pager already owns vertical drags
  (the arbiter's child axes), so nothing fires there from the page either. The overview surface itself is untouched.
- **Unfolded search stands in the Stream column's rectangle.** `DockSearchSurface` is placed by `StreamColumn.rect` at
  rest (`StreamColumnGeometry.restBounds`, from the same window, Dock bounds and insets as the column, so the keyboard
  lifts it): the same width, the same side against the Dock's inner edge, its bottom at the Dock's lower end as it is in
  the current mode (the Dock is shorter in the Home mode than while the Stream shows its tiles), growing up with the
  results; the field and results keep the page's 16 dp inset from the rectangle's edges, as the cards do. The press
  catcher covers the window. The field takes focus without waiting on layout. The surface exposes its drawn alpha
  (`DockSearchAlphaKey`) and the tests assert it is 1 once the spring has ended, with the field focused, the panel
  against a right and a left Dock, and above a simulated keyboard. The cause of the dim, detached panel on the device
  was not reproduced: the geometry was the Home-sized reservation, not the column's, and the Thick glass over a Home
  that fades out to the bare wallpaper is a 56% fill (reduced transparency makes it nearly opaque); nothing kept the
  spring from arriving in any run here. Folded search is unchanged.
- **Home takes no input while it is hidden.** The Home surface is wrapped in a gate (`HomeInputGate`) that, while search
  or the Library covers Home or Home is drawn at or below 2% (`HomeDockPullBinding.homeInputBlocked`), lays a full-size
  node over it that takes every touch, so a tap, a long press, a drag, a page swipe or a Home swipe stops there. Search's
  own press catcher used to be padded by the system bars and the keyboard's room (the surface was laid out inside those
  insets), which left strips where Home still received touches; it now covers the whole window and only the panel
  keeps clear of the insets (see the search item below). Cells of the grid that the Stream's fade has taken below 5%
  (under the column with nothing peeking through, or behind an open card) take no input either
  (`GridFadeCell.blocksInput`, `GridFadeInputBlock`, in Home's grid and the paged Library): a cell-sized node over the
  cell that swallows taps and presses and consumes no movement, so a page's drag still reaches the pager.
- **Home ignores the keyboard.** Home laid out inside `WindowInsets.safeDrawing`, which includes the keyboard, so an
  open card's reply field raised the keyboard and shrank Home's grid to a column of small icons. Home's frame and the
  paged Library standing in for it now use `HomeInsetPolicy.contentInsets()` (system bars and cutout only). Only the
  Stream's column, an open card, search and the Dock move for the keyboard. Found while proving it: with a card open
  on a bottom Dock (folded) Home's grid also moved up, because the Dock's measured extent, which Home reserves, counted
  the open card's actions row growing out of the Dock; the Dock area now reports its extent without that row
  (`DockExtentReport`). `UnfoldedStreamColumnTest` and `FoldedAppsBehindCardsTest` assert every grid cell
  (`home-grid-cell`) has the same bounds with the Stream closed, open, a card open and a simulated keyboard inset.
- **"Show apps behind the cards", folded too.** The setting `layout.<posture>.appPeekThrough` now works in both postures
  (default folded off, unfolded on; Settings > Layout in each section, and Settings > Stream > Cards with one switch per
  posture, the same stored value). Folded with it on, Home stays on screen behind the stack (`homeBehind`: the handle
  stands down, no fade or scale with the trampoline) and its grid fades under the same rectangle `StreamColumn.rect`
  gives, cell by cell, no blur; off, Home fades out as before. The folded value is stored under its own key
  (`appsBehindCards`; unfolded keeps `appPeekThrough`) because earlier builds wrote a meaningless `true` for the folded
  posture, which would otherwise have turned it on for everyone. Card text stays readable over the faded grid through the
  card fill's contrast guarantee (screenshots, light and dark, of the stack and an open card).
