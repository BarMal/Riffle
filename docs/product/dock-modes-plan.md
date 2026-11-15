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
- **A crowded side Dock never rests with an icon cut in half.** The pinned run in the Home mode, when it still does not
  fit after the gap has closed up, is now a whole number of icons long (`dockWholeIconsViewportMainAxisDp`; the Dock is
  as long as what it shows) and scrolls by whole icons (it settles on the nearest whole icon), on every edge, as the
  bottom Dock's crowded run does. The existing overflow fade at its edges stays as the cue. In the contextual row, which
  scrolls by design and whose reveal leaves a 24 dp peek of the neighbour, an icon the viewport cuts is faded by how much
  of it is in view (`DockEdgeFade`: the visible fraction squared; per-item opacity read in the draw layer, no mask,
  gradient or offscreen layer), so the clipped end reads as "more this way" and not as a half icon.

## Device fixes, round 4 (cards)

Twelve things the owner found on a foldable (mostly unfolded, Dock on the right edge), most of them in how a card opens
and closes and what an open card shows. Fixes 2, 3 and 4 share one structural change and are one commit; 7 and 8 share one; 13 is
only partly done; 10 and 11 are **not done**: see the end.

### Opening and closing a card

- **"Open" opens the notification's app (fix 1).** The open card's "Open" sent the notification's content intent with
  a plain `PendingIntent.send()`. From Android 14 a pending intent that starts an activity is a background activity
  start unless the sender opts in, and a notification listener's launcher has no standing of its own, so the system
  dropped it. Every send from a notification (its content intent, and the provider actions the Stream uses for Reply)
  now goes through `PendingIntentSender`: on API 34+ it carries `ActivityOptions.setPendingIntentBackgroundActivityStartMode(
  MODE_BACKGROUND_ACTIVITY_START_ALLOWED)`; a cancelled intent fails once (the Stream's gateway then launches the app,
  as it already did when there was no intent or the handle was gone); a refused opt-in is retried plain. The API 36
  creator-side modes are not compiled against (compile SDK is 35) and are not needed for the launcher as sender. The
  decision (options, cancellation, retry) is `PendingIntentSender.send`, pure over a dispatch function, and tested with
  the gateway's own fallback (`AndroidStreamActionGatewayTest`). A successful Open leaves the Stream and the card as they
  were, as every other Stream action does (the app comes to the front over the launcher).
- **No hard cut-off while a card opens or closes (fix 2).** The cause was the open card's frame: it was laid out at its
  full content height and *drawn clipped* to the part that had grown, so its bottom edge (and its rounded corners and
  border) was sliced by a straight line at the column's bottom until the spring ended. On the unfolded column the column
  itself also grew on a second spring, so the card's room lagged the card. Now the card is a whole card at every frame
  (`OpenCardFrame`: it is measured at the height it has now, its glass drawn at that size, its content keeping its top
  and scrolling, as it does once open) and it is drawn by the surface as an overlay (`OpenCardLayer`,
  `OpenCardLayerHost`) over the whole of its area, outside the pager. The unfolded host (`StreamColumnSurface`) is
  as wide and as tall as an open card may be and **never changes size**; the pages stand in the column's own rectangle
  inside it (`pageBounds`, tagged `stream-pages`, the old `stream-column`). Nothing between the card and the screen
  clips it. Test: `UnfoldedStreamColumnTest` and `FoldedAppsBehindCardsTest` step the test clock through an opening and
  a closing and, at every frame, compare the card's laid-out bounds with its drawn bounds (a node's `boundsInRoot` is
  clipped by its parents, `positionInRoot` and size are not).
- **One motion, no recomposition per frame (fix 3).** Before: `StreamStack` read the card's growth in composition (to
  decide whether to keep drawing a closing card), so the whole stack and every card of it recomposed on every frame of
  the spring; the column's width and height ran on a second spring and were read in layout, so the pager, the stack and
  all its cards relaid out each frame; Home's cells read the fade in their draw layer already, but the fade's inputs were
  re-assigned (new lambdas) on every recomposition of the Home destination. Now there is one progress, the open card's
  (`OpenCardHolder.motion`, run by the surface), read only in layout and draw: the card's frame, the strips' translation
  (a graphics layer), the grid's fade (`GridFadeField.column`, read in each cell's `graphicsLayer`). The stack reads
  whether the card is still drawn through a `derivedStateOf`, so it recomposes when the card appears and when it is
  gone, not between. The fan-out on opening the Stream and the Dock's dip were already draw-phase (`dockFan`,
  `dockDipDp`) and are unchanged. Test: `RecompositionProbe` (a test hook: a `SideEffect` in a Home grid cell and in each
  stack slot) counts recompositions while the card is mid-way open and mid-way closing; the count must not move.
- **A tap outside an open card closes it, anywhere (fix 4).** While a card is open a scrim covers the window over Home
  and under the Stream and the Dock (`OPEN_CARD_SCRIM_TEST_TAG`): a tap on an app, the room beside the column or the
  wallpaper closes the card and does nothing else; the Dock keeps its own rule (a press closes the card first) and
  "Close card" stays. With only the stack showing: unfolded, a tap on a visible app launches it (Home is live beside the
  stack), a tap on Home's empty room (`detectUnconsumedTap`, which watches the lift after every child has had its turn
  and consumes nothing) or on the column's own empty room closes the Stream; folded, a tap on the stack's empty room
  closes the Stream.

### What an open card shows

- **The strips behind an open card (fix 5).** The blank slabs were not reproduced: in the Robolectric stack of four
  notifications the strips behind an open card show their avatar and title both before and after the change
  (`theCardsBehindAnOpenCardStayAsStripsWithTheirOwnTitles`, with a screenshot). The open card is now drawn outside the
  stack, so nothing of it can sit over the strips. Not confirmed on a device.
- **The same line twice (fix 6).** An app that lists a message in both its messaging style's lists (or twice) produced
  two consecutive identical thread lines. `NotificationThreadSummary.withoutEchoes` drops a message that is the same
  sender, text *and time* as the one before it, in the platform mapper and again in the domain's thread (so a stored
  snapshot with the echo is cleaned too). The same words at different times are a conversation and stay.
- **Avatar and badge (fix 7).** `NotificationContent.conversation` (a messaging style, or the message category) and
  `hasPerson` decide whether there is a person: only then a round photo or initials, with the app's icon badged at the
  corner; any other notification shows the app's icon, no initials, no badge. The badge is never drawn on the app's icon,
  nor on a photo that is the app's icon (`IconLookalike`, a 8x8 mean colour difference, for a large icon an app sets to
  its own logo). The rule is `AvatarPlanner` (pure, tested).
- **The picture (fix 8).** `StreamPicture` spans the card's content width at its own proportions (`PictureLayout`, capped
  at 280 dp, cropped about its centre) with all four corners at the card's inner radius (24 dp less the 16 dp of padding);
  the open card's height follows its content (it no longer fills the room it is given) within the column's limits.
- **Keyboard (fix 9).** The open card is a header (pinned), a body that scrolls and the actions (pinned): when the room
  shrinks the thread gives way, not the header or the reply row. The strips behind it peek out only while three of them
  and a card of at least 240 dp fit (`OpenCardLayout.stripsFit`); otherwise they are not drawn, never overlapped.

### Look

- **Faded apps (fix 12).** The fade was a smoothstep over the 144 dp *outside* the column and a step to 22% at its edge,
  so one column of apps at ~96% sat next to one at 22%. It is now one smooth curve of the signed distance from the
  column's edge: untouched 240 dp outside it, falling through the edge (about 38%) to a floor of 30% at 64 dp inside it
  (`StreamGridFade`: `FEATHER_DP` 240, `INNER_DP` 64, `PEEK_ALPHA` 0.30). Still per-cell opacity in the draw phase; no blur,
  gradient or offscreen layer.

### Also in this round

- **A tap on a strip behind an open card opens that card (fix 13, partly).** The overlay's scrim reads where a tap lands:
  on a strip it brings that card to the front and opens it in place (the card's content swaps, the motion stays open); else
  it closes the card. Not built: dragging the stack while a card is open, the nested-scroll hand-off with the thread, and
  Next/Previous opening the neighbour.
- **The ringed Dock icon opens its app (fix 14).** In the contextual mode a tap on a live app or a pinned app whose page is
  showing launches the app (`LaunchApp`); any other tap shows its page, tiles and now playing are unchanged, and the
  ringed icon's click label reads "Open <app>" (a pinned icon's now says "Show/Open <app>" too).

### Not done

- **Fix 10, swipe a card sideways to dismiss it,** and **fix 11, the notification's own action buttons.** Neither was
  started: a half-built gesture or a half-plumbed pending-intent path is worse than none.
- Fix 13's drag, nested scroll and accessibility actions.

## Device fixes, round 5 (cards: gestures and actions)

The four things round 4 left: strips that look like blank slabs, dragging from one open card to the next, swiping a
notification away, and the notification's own buttons. Built in that order, one commit each; the tests are in the same
commits. Nothing in this round is confirmed on a device.

### Strips that look like blank slabs (item 4)

- **Cause.** The owner's page is not four notifications: after the pinned and live merge an app's page is its notification
  cards followed by its **shortcut cards**. A shortcut card, a recent app and the notice cards centred their icon and name in
  the card (a 104 dp card, the icon at 32 to 72 dp), and a strip shows only the top 52 dp of its card (the pitch less the gap,
  `StreamCardStackSpec.STRIP_HEIGHT_DP`), so their strips were empty glass. Now-playing kept its title in a deck at the
  bottom; an event led with a time line, an article with its picture; the checklist, tomorrow and the notices had no icon.
- **Rule.** Every card starts with a header (`StreamCardHeader`: an icon or avatar and a one-line title, 4 dp below the
  card's top, at most 48 dp tall) that lies wholly inside the strip, whatever the kind. Notification: avatar and sender
  (40 dp); event, next event, Today's checklist and tomorrow: a calendar or check glyph and the title; article: the feed glyph
  and the headline above its picture; task: its checkbox and title; recent app and shortcut: the icon at the top, the
  detail under it; now-playing: the app's icon and the track in a glass header at the top, the scrubber and transport in the
  deck at the bottom; notices have a title of their own ("Shortcuts unavailable", "Allow access", "Tasks not connected",
  "Tasks unavailable") over their sentence.
- **Tests.** `StreamStripsTest`: one test per kind (and per notice) draws the real card clipped to a strip and asserts the
  header's icon and title nodes (`stream-card-icon`, `stream-card-title`, in the unmerged tree) lie inside 52 dp; a merged
  page (one notification and three shortcuts) through the real stack at rest and with the notification open, with a screenshot.
  `StackMotionTest` pins the strip height against the geometry.

### Dragging to the next card with a card open (item 1)

- **The gesture.** While a card is open, a vertical drag that starts on a strip, on the open card's pinned header or actions
  or on the room beside the card moves the stack by one card with the stack's own drag, fling and magnet (`StackMover`, one
  platform `scrollable` shared by the resting stack and the open card's layer), and the card that arrives is open in place.
  A drag that starts in the card's scrolling body scrolls it (platform nested scrolling): the body consumes first, and what it
  cannot take at its top or bottom is accumulated by `OpenCardHandoff` (pure, tested); only past **56 dp** does the stack begin
  to move, one to one by what exceeds the threshold, and a finger that comes back takes the stack back before the body scrolls.
  Releasing before the threshold changes nothing. A body with nothing to scroll hands the drag over at once. A fling in the
  body never turns a card: the stack's fling behaviour ignores a release with no drag of its own before it.
- **The motion.** The open card's layer (`OpenCardFrames`) draws the open cards of the two cards the stack's position lies
  between and blends each between its slot in the stack and its open frame by how near the front it is
  (`OpenCardBlend.openness`): the card that arrives grows as the one that leaves shrinks to its strip (moving to the newer
  neighbour) or sinks into the Dock (to the older one), one continuous motion read in layout and draw. The stack stays "open"
  throughout; when it comes to rest on another card `OpenCardEvent.Settled` opens that card in place. Under reduced motion one
  card is drawn at a time and the jump cross-fades.
- **Other routes.** "Previous card" and "Next card" are custom actions of the open card; a tap outside still closes it; a tap
  on a strip now glides to that card (and opens it); the Dock's press closes first.
- **Cards that do not open in place** (a shortcut, a recent app, an article): if the neighbour is one of those the stack moves
  to it and returns to the resting stack with nothing open.
- **Tests.** `OpenCardDragScenarios` (folded and unfolded) through the real shell content: header and strip drags, a drag in
  a long text that scrolls it, a short and a long overscroll at both edges, a fling, the accessibility actions, a tap outside
  after a drag, continuity (two whole cards while moving), reduced motion, and no recomposition while dragging (the
  `RecompositionProbe` now also counts the open cards); `OpenCardNeighbourWithNothingToOpenTest`.

### Swiping a notification away (item 2)

- **The gesture.** A horizontal swipe on the front card of a notification (the browse stack, a lone card, the open card's
  header) dismisses it and the next card comes forward. The platform's `draggable` gives the drag and the velocity; the rules
  are `SwipeDismiss` (pure, tested): past **40%** of the card's width, or a fling of **700 dp/s** after 24 dp of travel,
  dismisses (either direction); anything else, and a flick back, settles back on a spring. The card follows the finger with a
  slight fade (to 50% at a full width), flies off past the threshold and fades out in place under reduced motion. One light
  tick on dismissal through the Stream's tick wrapper (the "light ticks" setting applies). A dismissal that does not take
  brings the card back after two seconds.
- **Which gesture wins** (the rule, `SwipeDismiss.claimsDrag`): a horizontal drag is the card's only where it starts **on the
  front card of a notification that `StreamActionPolicy` offers Dismiss for**. On the strips, the empty room, a notification
  that cannot be dismissed (ongoing or not clearable: no Dismiss action) and every other kind of card it is the pager's, so a
  page whose front card is dismissible is still left by swiping on its strips or its room. The Dock's row, the tiles and
  hold-to-choose are untouched.
- **A decision to check.** The brief says a non-clearable notification "resists (rubber-band) and settles back" and also that
  non-dismissible cards page. The second won: such a card has no swipe at all, so a drag on it pages like a drag anywhere else
  (a card that resisted would be a dead zone for paging) and it is never dismissed.
- **Non-gesture routes.** "Dismiss" stays on the open card (the Dock's button and a custom action) and is a custom action of
  the browse card. Tasks, events, media, recents, shortcuts and articles have no swipe.
- **Tests.** `SwipeDismissTest` (thresholds, fade, fly-off, the rule); `StreamSwipeDismissTest` through the real shell with a
  gateway that removes what it dismisses (past the threshold either way, short and slow, a fast fling, motion on, a dismissal
  that does not take, a non-clearable notification pages, strips and empty room page, the accessibility action, the open
  card's header); `StreamSwipeDismissTickTest`.

### The notification's own action buttons (item 3)

- **What shows.** After Reply, Dismiss and Snooze the open card shows the notification's own actions (at most three, in its
  order) as text buttons, each with a 48 dp target and its title (a long one ellipsizes, short ones keep their width), on a row
  under the controls: in the Dock's row or on the card as the "actions position" setting says. Each is also a custom
  accessibility action of the open card.
- **What is captured** (memory only; the stored snapshot has no field for it, a test pins its keys): the listener's registry of
  live notification handles notes each action's id and title when it registers the notification; `NotificationOwnActions`
  (pure) leaves out the inline reply and anything that needs input (a remote input), a second "reply" and a "delete" (the
  card's own controls), and an action with no title; "mark as read", "archive", "thumbs", "mute" and "call" stay.
- **Typed.** `NotificationContent.ownActions` (ids and titles, via the item ext like the thread, empty when redacted),
  `CardAction.Own`, `StreamAction.OwnAction`; the gateway sends it by id through `PendingIntentSender` (the Android 14
  opt-in; a cancelled intent is a failure, a notification that has gone is unavailable: the usual failure message, no crash).
  When the app removes the notification ("mark as read", "archive") the open card closes cleanly and the next card comes
  forward.
- **Tests.** `NotificationOwnActionsTest`, the mapper, `StreamCardActionsTest` and the policy (domain);
  `NotificationOwnActionsCaptureTest` (real platform actions: reply skipped, cap, order; sent by id; cancelled; gone),
  `AndroidStreamActionGatewayTest`, the codec; `OpenCardOwnActionsTest` at 320 dp (sizes, fit with Reply's field in the Dock's
  row and on the card, tap, failure, closing when the notification goes, accessibility actions, no row without buttons).

### Tests rewritten on purpose

- `StreamStackBehaviorTest.theFrontCardAlsoOffersOpenAndEveryCardIsOneNodeInOrder` (now `...OpenAndDismiss...`): a
  dismissible notification's front card now also offers "Dismiss" as a custom action.

### Not confirmed without a device

Every gesture here was driven with the Robolectric touch injector and the test clock, never a finger: the feel of the
hand-off threshold, the blend between two open cards, the swipe's fly-off and the fit of three buttons on a real 320 dp
screen. Device tests for the drag and the swipe were not added.

## Settings audit

The owner asked for the settings to be walked: well grouped and labelled, and everything wired, "some of the card and
dock options don't seem wired up yet". Every control on every page was followed from the control to where its value is
consumed (the stored value, then the composable or logic that reads it, folded and unfolded, bottom and side Dock where
that matters), and each one given a verdict. The rule for a setting that is not wired: wire it with a test that fails
without the wiring; else make the row truthful (it says where it applies, or the choice is not offered where it would
do nothing); else, for a design promise that is large, remove the option until it is built and let the stored value
read as the nearest working one.

"Wired before" is the state at the start of the audit: **yes** (the stored value reaches the feature in every posture
and edge it names), **partly** (it did in some), **no**. Paths are under `app/src/main/java/com/riffle/app/launcher/`
(`A/`). Tests are under `app/src/test/java/com/riffle/app/screenshots/` unless a package is named.

### Inventory

| Page | Setting (label) | Stored key | What it does | Wired before | Done |
| --- | --- | --- | --- | --- | --- |
| Home | Simple mode | `features.simpleMode` | Overrides Home shows, the Stream, live apps and the Library | yes (`effectiveFeatures`, `FeatureSwitchesTest`) | Grouped with Home shows in one "Home screen" section (two single-row sections before) |
| Home | Home shows | `home.mode` | Placed items or the App library on Home | yes (`HomeModeTest`) | Grouping, above |
| Home | Columns, Rows | per device class `grid.columns/rows` | The Home grid | yes (`HomeGridLayoutMetrics`) | nothing |
| Home | Horizontal margin, Vertical margin | per device class `grid.margin` | Margins round the grid | yes | nothing |
| Home | Icon size | per device class `labels.iconSizeDp` | Home icon size (also the paged Library) | yes | nothing |
| Home | Show labels, Label background (and its strength), Label text size, Label width, Label sizing, Label lines | per device class `labels.*` | Label drawing (`WallpaperReadableLabel`, `HomeGridLayoutMetrics`) | yes | The strength slider was an unlabelled orphan of the switch: it is now "Label background strength" and under the same anchor; lines read "2 lines" |
| Home | Returning to Home | `home.returnBehavior` | Restore, first page or start page (`HomeReturnEffect`) | yes | nothing |
| Home | Wallpaper, Wallpaper motion, Change wallpaper | `appearance.wallpaper.*` | Wallpaper source and scrolling (`LauncherWallpaperSync`) | yes | nothing |
| Home | Hide status bar, Hide navigation bar | `appearance` system bars | Full screen Home (`homeInsetPolicy`) | yes | nothing |
| Home | Set up Riffle again | none (action) | Re-runs the walkthrough | yes | nothing |
| Dock | Show the Dock | per device class `dock.isEnabled` | Draws the Dock (`shouldShowDock`) | yes | relabelled ("Show dock") |
| Dock | Dock position | per device class `dock.position` | The Dock's edge | yes | The same stored value as Layout's: one shared row (`SettingsDockPositionRow`), same title, subtitle, options and order; `SettingsSharedRowsTest` changes one and reads the other |
| Dock | Icon size, Pinned icons | `dock.iconSizeDp`, `dock.capacity` | Dock icon size and how many pins show before it scrolls | yes | nothing |
| Dock | Dock width | `dock.backgroundSizing` | Fit content or full width | yes | nothing |
| Dock | Dock alignment | `dock.alignment` | Where a Dock that does not fill its edge stands along it | **partly**: along the top or bottom edge only; down a side (the unfolded default) the area always centred it | **wired**: start, centre, end are top, middle, bottom of a side edge; `DockAlignmentTest` (each edge, each alignment); subtitle reworded |
| Dock | Dock corner radius | `dock.cornerRadiusDp` | The Dock body, the actions row and the handle | yes | nothing |
| Dock | Dock background strength | `dock.backgroundAlphaPercent` | The Dock's fill (`dockFillColor`) | yes | relabelled ("Background strength") |
| Dock | Spacing to the grid | `dock.homeControlsSpacingDp` | The gap between the pages and the controls under them (Home and the paged Library) | yes | nothing |
| Dock | Show live apps | `features.dockLiveApps` | The Dock's live entries and now playing | yes (`FeatureSwitchesTest`) | Section "Dynamic side" is "Live apps" |
| Dock | Number of live apps | `dock.liveAppSlots` | How many live apps the Dock shows | yes | relabelled ("Live apps", which read as the switch's twin) |
| Floating dock | Floating dock, Floating dock apps, Edge, Handle thickness, Handle size, Handle position, Handle opacity, Expanded icon size, Expanded orientation, Expanded labels | `overlayDock.*` | The overlay service's handle and tray (`overlay/OverlayDockViewFactory`, `...Layout`, `...Drawing`, `OverlayDockService`) | yes: every field has a reader | nothing (rendering is the overlay service's, which needs a device) |
| Layout | Dock position, folded and unfolded | per device class `dock.position` | The posture's Dock edge | yes (`LauncherDockEditReducerTest`) | Shared row with the Dock page, above |
| Layout | App pages scroll | `layout.<posture>.pageScroll` | The pager axis of Home, the paged Library and the App library Home | yes, both postures (`UnfoldedLayoutTest`) | nothing |
| Layout | Page dots | `layout.<posture>.indicator` | The handle's dots or its glyph alone | yes | nothing |
| Layout | Drag the dots to scroll | `layout.<posture>.quickScroll` | Quick scroll along the dots (disabled while the dots are off) | yes | nothing |
| Layout | Show apps behind the cards, folded and unfolded | `layout.folded.appsBehindCards`, `layout.unfolded.appPeekThrough` | Home stays behind the stack and its grid fades cell by cell under it | yes, both postures (`FoldedAppsBehindCardsTest`, `UnfoldedStreamColumnTest`) | Shared row with the Stream cards page (`SettingsAppsBehindCardsRow`): identical title and subtitle on both, `SettingsSharedRowsTest` |
| Library | Library | `features.library` | The Library sheet and its pull | yes (`FeatureSwitchesTest`) | nothing |
| Library | Show as (Categories, A-Z, Grid, Pages) | per device class `appDrawer.libraryViews` | The Library's view, the same choice as its long-press | yes (`PagedLibraryTest`) | nothing |
| Library | Grid columns | `appDrawer.iconGridColumns` | The Grid view's columns (disabled for other views, and says so) | yes | nothing |
| Library | Search results | `search.resultPresentation` | Card stack (the default), icons or list, in the Dock's search; the Library's own search field draws the stack's choice as icons | yes (`LibraryState`, `DockSearchSurface`, `SearchStackBehaviorTest`) | nothing |
| Library | Hidden apps, Refresh installed apps | app visibility store; action | The hidden list; a rescan | yes | nothing |
| Stream | Stream | `features.stream` | The Stream, its press, live pages and shortcuts | yes (`FeatureSwitchesTest`) | nothing |
| Stream | Now, Notifications, Recents, Today, Feeds: on or off, Order, Show as | per device class Stream layout | Which pages exist, their order and Stack or List | yes (`CachedStreamLayoutRepository`, the Stream surface) | Each section (all three rows) is now the setting's search anchor, not only the switch |
| Stream | Look (row to Stream cards) | none | Opens the sub-page | n/a | the section was called "Stream cards" over a row of the same name; now "Look" |
| Stream cards | Card colour strength | `stream.cardTintPercent` | The card fill's app colour (`CardTint`, `GlassSurface`), both postures | yes | `StreamCardLookSettingsTest`: a real card's fill moves with it (`GlassCardLookKey`) |
| Stream cards | Glow on the top edge | `stream.cardTopGlow` | The app colour as a strip on the top edge, drawn with the restored local spotlight | yes | `StreamCardLookSettingsTest`: lit only with the switch, fill unchanged |
| Stream cards | Show apps behind the cards (two rows) | see Layout | see Layout | yes | Two single-row sections "Folded" and "Unfolded" under one anchor |
| Stream cards | Where an open card sits: Centre, Above the Dock | `stream.openCardPlacement` | Where the grown card stands (`OpenCardLayout.frame`) | **partly**: folded it differs (a card shorter than the room), unfolded the column forced Above the Dock | **made truthful**: the row says "Folded. On the unfolded stack an open card always grows from the Dock."; `OpenCardMotionTest` that the two differ |
| Stream cards | Where an open card sits: Grows out of the Dock | `stream.openCardPlacement` | Was drawn as Above the Dock | **no** (stored, drawn as another option) | **removed until built**: not offered, enum value deleted, a stored one decodes as Above the Dock (`LauncherSettingsJsonCodecTest.aStoredGrowsOutOfTheDockPlacementReadsAsJustAboveTheDock`) |
| Stream cards | Where its actions sit | `stream.cardActionsPlacement` | In the Dock's row or on the card | **partly**: In the Dock only folded with a Dock along the bottom; a side Dock and the unfolded column always use the card | **made truthful**: the subtitle says so, and with the folded Dock on a side only "On the card" is offered with the reason (`cardActionsRowPlan`, `CardActionsRowPlanTest`, `StreamAndMotionSettingsTest`) |
| Notifications | Use notifications, Hidden items | source registry; exclusion rules | The source and what it hides | yes | nothing (needs the sources host: not drawn in the audit's page test) |
| Agenda, Calendar feeds, Tasks, Feeds | Device calendar, Include calendar feeds, feeds (add, refresh, list), Task provider token, Feeds source, background refresh, Wi-Fi only, charging only | source registry; Stream layout options; feed stores; Todoist token store | The sources behind Today, Tasks and Feeds | yes | Tasks section is the Task provider's anchor (Save and "Where is my token?" were unkeyed) |
| Appearance | Colour mode, Accent, Custom accent, Corners, Typography | `appearance.theme*` | The theme | yes | nothing |
| Appearance | Reduced transparency | `appearance.reducedTransparency` | Every glass surface takes the Thick fill (`GlassSurface`) | yes | nothing |
| Appearance | Spotlight flow | `liquidGlass.spotlightFlowMillis` | The pace of the Dock's spotlight | yes | nothing |
| Gestures | The 14 swipe and pinch bindings, Reset | `gestures.homeGestures` | Home's gestures; "Open notifications" is gone | yes (the action list no longer has it; a stored name decodes to none) | nothing |
| Motion | Animation performance | `motion.performanceTargetFps` | The preferred frame rate while the Dock animates | yes | nothing |
| Motion | Reduced motion | `motion.reducedMotionPreference` | A short fade instead of every motion, resolved once (`reducedMotion()`) | yes (the open card, drag between cards, swipe-dismiss, the column, search and now the entrance all read it) | nothing |
| Motion | Feedback strength | `haptics.feedbackStrength` | The haptic strength | yes | nothing |
| Motion | Opening speed | `motion.dock.speedMillis` | The Dock's springs, the fan, the stack, search and the open card | **partly**: the grid fade under the stack ("apps behind the cards") and a swiped card's fly-off ran on fixed times | **wired**: both scale by it (`DockMotionTuning.stiffnessFor`, `millisFor`); `DockMotionSpeedReachTest`; the Home entrance uses it too |
| Motion | Dock bounce | `motion.dock.trampolinePercent` | The dip spring's damping | yes (`DockMotionSettingsTest`) | nothing |
| Motion | Card stack coasting, Card snapping | `motion.dock.inertiaPercent`, `magnetPercent` | The stack's fling and magnet, also the drag between open cards (one mover) | yes | nothing |
| Motion | Hold time to choose | `motion.dock.holdMillis` | The press's hold in every posture (`HomeDockPullBinding`) | yes | nothing |
| Motion | Light ticks | `motion.dock.ticks` | Wraps the haptics for the stack, choosing and swipe-dismiss | yes (`LauncherHapticsTicksTest`, `StreamSwipeDismissTickTest`) | nothing |
| Privacy and access | Default home app, Notification access, Usage access, Calendar, Floating dock access | platform state | Each shows its state and opens the way to change it | yes | nothing |
| Privacy and access | Hide launcher content in screenshots and recents | `privacy.protectScreenContent` | `FLAG_SECURE` (`LauncherScreenProtection`) | yes | nothing |
| Privacy and access | How much the Stream shows (per source) | source content level store | The detail a source may show | yes | nothing |
| Backup | Export backup, Import backup | the backup document | Settings, layouts and the enabled Stream sections; the new settings are in the settings blob, which the codec round-trips (`LauncherSettingsJsonCodecTest`, `LauncherBackupDocumentTest`) | yes | nothing |
| About | Version, What apps publish | none | Read-only | n/a | exempt from the key rule (diagnostics) |

Verified by reading the consumer rather than by a new behaviour test: the older Home, Dock (apart from alignment),
Library, Appearance, Privacy, Floating dock and source rows above have existing reducer, codec and consumer tests and
were not given new ones. Not looked at in depth: the feed, calendar and Todoist source controllers and the Hidden items
rules (each routes to its own controller), and the overlay service's drawing, which needs a device.

### Grouping and labels

- Home: Simple mode and Home shows are one "Home screen" section. The label background strength sits under its switch.
- Dock: "Show the Dock", "Dock background strength", "Number of live apps" (and the section "Live apps", not "Dynamic
  side"), a Dock position with the same options in the same order as Layout.
- Stream: the sub-page is "Stream cards", not "Cards"; its row sits in a section called "Look". Each section of the Stream
  page is the search anchor of its setting.
- Page summaries name the newer rows (Home: Simple mode; Stream: how cards look; Motion: opening speed; Cards: apps behind
  the cards).
- The main page keeps its eleven places in the order Home, Dock, Layout, Library, Stream, Appearance, Gestures, Motion
  and haptics, Privacy and access, Backup, About: Layout follows the Dock it positions and nothing found a better order.
- Reduced transparency stays under Appearance > Glass (it is a look of the material) and reduced motion under Motion.

### The Home entrance (the Dock dropping from the top)

The owner saw the Dock drop from the top when Home loads. The Dock itself has no entrance transition. The only things
that animate the Dock's position or size are the Dock press (a dip toward its edge, zero at rest), the fan, and two
stacked `animateContentSize` modifiers on the Dock area (`StandardHomeDockArea`: its own spring, and `dockShelfMotion`),
which ease **any** size change from the first frame. Home's first frames are not final: the posture settles after the
first frame (`WindowInfoTracker`), the insets and the apps arrive, and the live apps load, so the area's size or edge
changes while it is already measured, and an easing size animation of a bottom-anchored or edge-centred area carries the
Dock across the screen. This could not be reproduced in Robolectric (the first-frame bounds there are stable, and a
posture change snaps in the test harness), so this is the cause found by reading, not one seen on a device.

Fixed in two parts. (1) The area's size easing (both modifiers) applies only once the entrance has arrived and never on a
change of edge (`rememberDockSizeEasing`), so on the first frames the Dock is laid out in its final place; a later change
(the actions row, the icon size, a shelf) still eases. (2) Home plays an entrance on appearing (`HomeEntrance`,
`HomeEntranceState`): the Dock and the page handle fade in and rise 14 dp from the Dock's own edge on the calm spring at
the opening speed, a plain 80 ms fade under reduced motion, drawn by one progress read in a `graphicsLayer` (alpha and
translation: no relayout, no recomposition per frame). It plays on a cold start and when the activity stops and starts
again (another app, the lock screen); a configuration change does not replay it (the flag is saved and a stop caused by one
does not reset it), nor does a sheet, search or the Stream over Home. `DockEntranceTest` steps the real Home frame by
frame: the Dock's layout bounds are its final ones at every frame including the first (bottom, right and left), the drawn
strip differs only by the translation toward the edge, the handle comes in with it, and the size easing is off on the first
frames and on afterwards.

### Tests rewritten on purpose

- `LauncherSettingsJsonCodecTest.theDeferredGrowsOutOfTheDockPlacementIsStored` is now
  `aStoredGrowsOutOfTheDockPlacementReadsAsJustAboveTheDock` (the option is removed; old data decodes as Above the Dock).
- `StreamAndMotionSettingsTest.theDeferredGrowsOutOfTheDockChoiceIsOffered...` is now
  `growsOutOfTheDockIsNotOfferedUntilItIsBuilt`; the placement test scrolls to its chips (the rows got a subtitle).
- `OpenCardMotionTest` and `StreamCardActionsTest` no longer treat Grows out of the Dock as an input; the first now
  asserts Centre and Above the Dock place a short card differently.
- Search screenshots (`HomeHandleScreenshotTest.searchOpenFromTheDock*`, `searchResultsAsIcons*`) showed only the caret
  blinking differently and were not re-recorded.

### Not confirmed without a device

The Home entrance and the Dock's drop (the cause above is read from the code, not seen), the feel of the opening-speed
change on the grid fade and the swipe, a side Dock's alignment at the top and bottom of a real unfolded screen, and the
overlay (Floating dock) rendering. Device tests were compiled, not run.

## Device fixes, round 6 (cards: content and the stack's look)

Six things from the owner's device about what a notification's card shows and how the stack looks. One commit per item,
with its tests. Nothing in this round is confirmed on a device.

### "Hidden content" and the title (item 1)

- **Cause of "Hidden content".** The card's title was `title ?: "Hidden content"`: any notification without a title was
  drawn as hidden. A navigation notification that draws itself with custom views fills in no title and no text, so it was
  a card that said "Hidden content" and nothing else. Nothing reads the notification's lock-screen `visibility`, before or
  now. The hidden state is now `NotificationContent.hidden`: the Privacy content level of the source hides text, or the
  profile is quiet. A notification that is not hidden always has a title line (`headline`): its own title, else what was
  read out of its layout (item 2), else its app's name; the app's name is the second line unless it already is the title.
- **The title.** The listener's mapper read only `EXTRA_TITLE`. It now resolves the title with `NotificationTitleRule`
  (pure): a messaging style takes the conversation's title (a group's name), else the title the platform wrote (the
  sender), else the newest sender; anything else takes the title, else `EXTRA_TITLE_BIG`. A candidate that is only the
  app's own name is passed over for the next one, and when there is no other the first short line of the text is the
  title. So an app that posts as itself (a title or a messaging "person" that is the app's name) is titled with what it
  says, and a real conversation keeps its person or group, whose name is not the app's. The exact shape of the owner's
  notification is not known (it was not in the diagnostics), so both shapes that give that result are covered by tests.
- **Group summaries.** Nothing left a group's summary out, so an app that posts a summary and children had a card for
  the summary too. `NotificationGroupSummaries` (pure) drops a summary when a child of its group is posted beside it;
  a summary on its own stays. Each child was and is one card.

### Custom layouts (item 2)

Three tiers, best first: the standard fields; **extraction**; **embedding**.

- **Extraction.** For a notification with a `contentView` or `bigContentView` and no title or text,
  `NotificationCustomContentStore` (memory only, beside the listener, like `NotificationImageStore`) has the layout read on
  the main thread: `RemoteViewsReader` inflates the `RemoteViews` off-screen into a bounded box (never attached to a
  window, every step in a try, at most 200 views), walks the views in drawing order and collects the visible text views'
  texts with their size and weight, the largest picture above 32 dp (sized down to 128 px) and which views take a tap.
  `CustomNotificationMapping` (pure, `:core:domain`) makes the title of the largest text (bold wins a tie, then the first)
  and lines of the others (no repeats, no buttons' texts; three for the browse card from the compact layout, five for the
  open card from the expanded one). The listener only hands the notification over; a notification posted again is read
  again, at most twice a second, and one that is removed (or the listener disconnecting) drops its entry and any read
  that was waiting. The card draws the result in its own style: the header, the lines, the thumbnail.
- **Embedding.** When the layouts inflate but have no text, the card embeds the app's own layout
  (`EmbeddedNotificationLayout`, an `AndroidView`): applied in a try, applied again in place (`reapply`) when the app posts
  again, clipped to the card's inner corner, at most 64 dp tall on the browse card and 256 dp on the open card, described
  as "Layout from <app>". On the browse card it takes no touch (the card's tap, the stack's drag and the pager are as on any
  card); on the open card its own buttons work.
- **Typed, not stored.** `NotificationContent.custom` (`CustomNotificationContent`) and `NotificationContent.body` (text,
  thread, custom or none). It reaches the content through the item's ext, like the thread, so the content level applies:
  hide text extracts and embeds nothing (the card is hidden), hide body keeps the title only. The stored snapshot has no
  field for any of it (a codec test pins its keys).
- **Not built.** A setting (none is needed); recipes per app (item 6 is for that).

### Live notifications' buttons on the browse card (item 3)

`LiveNotificationRule` (pure): ongoing, and custom views or a progress bar or the navigation, transport or progress
category. The listener's registry reads the facts and answers per notification; nothing is stored. The front browse card of
a live notification shows its own buttons (at most three) under its lines, each a 48 dp text button and a custom
accessibility action of the card. A button's tap is its own; the card's tap still opens it; a vertical drag that starts on
a button moves the stack; an ongoing notification has no swipe-dismiss, so nothing competes sideways. The buttons stay on
the open card too.

### The front card's content and height (item 4)

- **What it shows.** Up to four lines: a conversation's last three messages as "Sender: text" (one line each), else the
  text (the big text, or an inbox style's lines, which the mapper now reads) up to four lines, else the extracted lines;
  the picture thumbnail stays at the trailing edge.
- **Height.** `BrowseCardHeight` (pure): the header (52 dp), the body as measured, the thumbnail, the buttons' row and the
  paddings, between 88 dp (the header and one line) and 168 dp (208 dp with buttons). The body is measured in composition
  with a text measurer at the width it is written at (`rememberBrowseHeights`), so nothing waits for a layout pass.
- **The stack.** Every card now has its own height (`StackGeometry(heightsDp)`); before, every card of a stack was as tall
  as the tallest. The stack stays anchored at its bottom: the front card's top is its own height up, the strips sit on it
  (lower behind a short card) a pitch apart, and between two cards the top moves from the one height to the other
  (`frontHeightAt`). The drag, fling and magnet are unchanged (they work on the position); the open card grows from the
  height the front card has, and the stack under it is moved from that height.
- **Other kinds.** A shortcut and a recent app (104 dp: a header and a line or two) and the events have no large empty
  area. A single task as its own card was drawn at the checklist's 232 dp with one line: it is 72 dp now. Today's next
  event keeps its own layout (its countdown and action sit at the bottom of a 200 dp card).
- **Also found.** The Dock's actions row left the composition when it had gone and so never reported a height of 0: the
  room kept for the Dock was short by the row and the stack slid under the Dock after an open card closed. A tall card
  hid this; a short one did not. The row is measured on a box that stays.

### Strips that tuck under the card in front (item 5)

A strip was the card clipped to its slot (a straight line) 4 dp above the next card. It is now drawn down to and around
the rounded top of the card in front and clipped to its own outline less that card's outline (`Modifier.stripCut`:
`Path.op(Difference)` used with `clipPath` in `drawWithContent`). No gap (`GAP_DP` is 0), a rounded cut, and the fills still
never overlap, since it is one clip: no offscreen layer, blur or blend mode. The cut reads the stack's position while
drawing, so it follows the neighbour during a drag or a coast with no recomposition (the `RecompositionProbe` tests pass
unchanged). The front card at rest is not clipped. The slot (the tap target and what a screen reader finds) is still the
strip's rectangle, a pitch tall. With a card open the stack is moved in whole pixels so the strip ends exactly on the open
card's top.

### Diagnostics: a layout's outline (item 6)

"What apps publish" lists, for a notification with custom views, an outline of each layout (content, big): a line per view
in drawing order, indented by depth, with its class's simple name, the entry name of its id, whether it takes a tap, its
visibility, a text view's text length and size, an image view's picture size. The text goes into the input and never comes
out of the mapper; the page's promise is tested with secrets in the layout, on the screen's lines and in the copied report.

### Tests rewritten on purpose

- `StreamBehaviorTest.aNotificationOpensToItsThreadAndSendsAReply` and `aControlOnEverySectionsPageReachesTheHost`,
  `StreamScreenshotTest.notificationExpandedToItsThreadWithReply`: the browse card of a conversation shows its last
  messages now, so they tap "Sam: Are we still on for lunch tomorrow?", and the first asserts the reply field is absent
  before opening (it asserted the thread was).
- `StreamStackBehaviorTest.aPeekingCardIsTheRealCardClippedToItsStripAndTheStripsNeverOverlap`: a strip is shorter than
  the front card, no longer under half of it (the front card is as tall as its one line).
- `StreamBehaviorTest.theNextCardsPeekOutAboveTheFocusedOneNotBesideIt`: reads the peeking card's drawn bounds (it ends
  on the focused card's top, with no gap).

### Not confirmed without a device

- Reading and embedding a real app's `RemoteViews` from another package (the tests use this app's own layouts): package
  contexts, an app's custom fonts and its click intents on the open card.
- Whether the owner's two notifications are the shapes the tests cover.
- The look of the cut over a real wallpaper with blur, and the seam between a strip and the card in front (two
  anti-aliased edges meet there).
- Unfolded, an open card is wider than the stack's column: the strip behind it is cut along the outline of the front
  card's slot, which is narrower than the open card, so the cut's corners sit inside the open card's width.
- At the largest font sizes a strip (a fixed pitch) cuts the header's second line, as before this round.

## Long-press coverage

The owner's report: "everywhere should be long pressable, but currently there's large dead zones." The coverage was mapped
before anything was changed, by long-pressing a grid of points across the whole window (every 48 dp, plus a point 2 dp
inside each edge, skipping the points that land on an item, a Dock icon or the handle) with a 24 dp status bar and a 48 dp
navigation bar simulated, and counting the points that played the menu's haptic tick. Folded is 411 by 914 dp, unfolded
840 by 900 dp. The probe is `LongPressCoverageBase` and `LongPressProbe.kt` (Robolectric, through the real
`HomeDestination`); it is kept, and now asserts there is no dead point.

### The map before the change

Dead points of the grid, and why:

| Surface | Dead before | Cause |
| --- | --- | --- |
| Home, folded, bottom Dock | 104 of 210 | The menu was one `HomeBackgroundContextMenu` per grid cell, plus one on the handle's row. Everything outside a cell was dead: the page's four margins (the pager is padded), the controls gap between the grid and the handle row, the room under the status bar and the navigation bar (the frame is padded by the insets and the handler was inside it), the Dock's room beside and around its glass, the room between the Dock and the window's edge, and the handle itself (tap only). |
| Home, folded, side Dock | 144 of 210 | The same, and the column the handle stands in held a handler only as wide as the handle's own strip. |
| Home, unfolded, right Dock | 154 of 399 | The same; and the grid is square cells centred in a page that is much wider than it is tall, so the hundreds of dp on each side of the grid were dead. |
| Home, unfolded, bottom Dock | 225 of 399 | The same. |
| An empty page | 104 of 210 | Cell handlers only: the page's own room around the (empty) grid was dead. |
| A generated page's "More apps" | 96 of 210 | The page's room beside and below the grid, and the button itself was as wide as the page, so no room was left beside it. |
| The Dock's body between its icons | 3 of 13 probes | The icons' run is a scroll (`horizontalScroll` / `verticalScroll`), which takes the touches of its whole run: a handler under it is never reached, so the gaps between the icons were dead. The body's ends and its padding were live (a handler on the Dock's own box). The Dock's margin strip around the body was dead (its pull and press gesture hit-tests there, and nothing answers a still press). |
| App library Home, folded and unfolded | 88 of 210, 170 of 399 | The pages had a handler, but the handle's row, the margins, the room under the bars and the Dock's reserve were outside it (the view is padded by the insets and the Dock's room, and the Home gestures wrapped it). |
| Paged Library over Home | 88 of 210, 169 of 399 | The same, and the Library's scrim (the room around the sheet) was a plain `clickable`: a long-press and release was a click and closed the Library. |
| Library sheet (Categories, A-Z, Grid) | 158, 102 and 92 of 210; 346 of 399 unfolded | The only menu was on the header: the sheet's glass between and under the rows and tiles, and the scrim around it, did nothing (and the scrim closed the Library on release: 50 `OpenHome` actions in the probe). |
| Home with the Stream open, unfolded | 209 of 399 | Home's cells under the column are faded and block touches, the column's empty room only had a tap (close), and a long-press and release there closed the Stream (60 `OpenHome`). |
| The Stream, folded | 195 of 210 | The same: the whole window was the Stream's empty room, which only had a tap, so a long-press closed it (150 `OpenHome`). |

Two of those were worse than dead: a long-press that ends is a tap for a `clickable` or `detectTapGestures { }` that
has no long-press handler, so holding on the Library's margin or the Stream's empty room closed it on release.

### The rule now, per surface

One empty-space handler per surface, under everything, as big as the surface's whole window (under the system bars and
the Dock's reserve too); what is on top of it (an item, the Dock, the handle) takes its own presses first, and a press on
nothing reaches it. It is `Modifier.pressMenu` (`PressMenuState.kt`): the platform's `detectTapGestures` (a tap, and a
long-press on the platform's timeout), the light haptic through `LauncherHaptics.longPress`, and a long-click action for
assistive technology. The menu hangs from an empty node at the finger (`PressMenuAnchor`), so it opens at the press and
the platform's popup keeps it on screen. A finger that has left touch slop before the timeout is not a long-press (a
small movement within slop is), and a drag, a swipe or a scroll that something consumes never reaches it.

- **Home (Placed items).** One handler over the whole Home window (`StandardHomeFrame`'s background, under the inset
  padding), and one per page behind its cells (the pager takes the touches of its whole page, so a page holds its own).
  The cells no longer hold one each. Opens the Home menu (Create folder, Widgets, Settings, Edit page, Manage pages) at the
  finger, so the margins, the strips the Dock and the handle reserve, the room under the bars, an empty page and the room
  beside "More apps" (the button is now as wide as its label) all open it. A tap on it still dismisses the Dock's shelf
  and, with the Stream out beside Home, closes the Stream (`LocalHomeEmptyTap`).
- **Items** keep their own long-press (app, folder, widget menus, and the drag after the long-press); a long-press on an
  item never opens Home's menu, and a long-press that becomes a drag still drags.
- **The handle.** A tap is search and a drag is quick-scroll, as before. A still long-press on it is Home's menu (it had
  none); on the paged Library and the App library Home it is that surface's menu. It never searches.
- **The Dock.** See the rule below.
- **App library Home and the paged Library.** One handler over the whole window (`LibraryPagedView`: under the bars and the
  Dock's reserve, which the view is now padded by), one per page: the surface's own menu at the finger ("Show placed items"
  and "Settings" on App library Home, "Show as" on the Library). On the Library the room around the pages still closes it
  by a tap.
- **The Library sheet (Categories, A-Z, Grid).** The sheet's glass is the handler (a parent: the rows, tiles, header and
  search field take their own presses first), and the scrim around it, now window-wide, is another sharing the same menu:
  "Show as" with Undo, at the finger. A tap on the scrim still closes the Library; a long-press never does. The header's
  long-press and its options button open the same menu.
- **Home with the Stream open.** Unfolded, Home is live beside the column and Home's rule above holds there (the faded
  cells under the column take no input, as before); folded, "apps behind the cards" is the Stream's window, below.
- **The Stream's empty room.** A tap closes the Stream, as before. A long-press opens the options of the page in front (the
  menu the long-press of its Dock tile offers: Show as, Move earlier and later, Hide, and a Show for each hidden section) at
  the finger, with the Undo the Stream already offers; a card, a control or the stack takes its own press first. Folded the
  room is the whole window, under the bars and the Dock's reserve; unfolded it is the column's rectangle (beside it is
  Home's). A pinned app's shortcuts page has no options, so there the long-press does nothing new and a tap still closes.
- **Home hidden** (search or the Library over it, or drawn at nothing behind the Stream): nothing on Home responds. The
  round 3 gate is unchanged and is above the handlers.

### The Dock's rule

A long-press on the Dock's room that is not an icon opens **Home's menu** (the Dock has no menu of its own, and none was
invented): the body between its icons, its ends and padding, the margin strip around it, the shelf's gaps. The Dock area
holds the handler as a parent, so the icons' run scroll, the icons' own long-press (their menus and the drag to reorder)
and the Dock's pull and press take their presses first.

It does not conflict with the press and hold-to-choose loop in `DockPull.kt`: that loop claims a press only when the
finger leaves touch slop toward the Dock's edge before the platform's long-press timeout, on the Initial pass, and
hold-to-choose is a timer that only runs after such a claim. A finger that has not left slop when the timeout expires is
not claimed (`awaitClaim` ends with no claim) and is the menu; one that left slop before it is a press, a pull or a scroll
and never reaches the menu (its changes are consumed, which cancels the long-press). Both are tested
(`LongPressBehaviorTest.aStillLongPressOnTheDocksBodyIsTheMenuAndAPressTowardItsEdgeIsNot`). The two inner handlers the Dock
had (one on the strip, one on the shelf) are gone for this one.

### Accessibility

Every handler is also a long-click action on its node (label: "Show home actions", "Show options for the Library", "Show
options for this page"), opening the same menu, so the menus reach a screen reader from the regions that used to be dead
(the Library's sheet and scrim, the Stream's room, the Dock). The existing custom actions stay: the Library's "Close
Library" and "Show as <view>", the App library Home's actions, the Dock's.

### Tests

- Behaviour and coverage, Robolectric: `HomeLongPressCoverageTest`, `LibraryLongPressCoverageTest` and
  `StreamLongPressCoverageTest` (17 probes, each fails with the map of its dead points), `LongPressBehaviorTest` (every kind
  of press on Home: the margins, under the bars, beside the handle and the Dock, the Dock's gaps, the handle, an empty
  page, "More apps", an app's own menu and never Home's, the menu hanging from the finger, a wobble within slop, a drag
  that is not a long-press, the Dock's press against the still press, a hidden Home), `LibraryLongPressBehaviorTest` (the
  Library's, App library Home's and the paged Library's own menus, and a tap that still closes the Library) and
  `StreamEmptyRoomLongPressTest`.
- Device (compiled, not run): `HomeMarginLongPressTest` long-presses Home's bottom margin and its side margin and expects
  the Home menu.

### Tests rewritten on purpose

None. Every existing test passes unchanged; the cell handlers, the Dock's two inner handlers and the Library's `clickable`
scrim were replaced, and nothing asserted them.

### Not confirmed without a device

- That the menu stays fully on screen near an edge: it is the platform's popup (it flips and slides to stay in the window);
  Robolectric has no real window geometry, so the tests assert only where it hangs from (`PRESS_MENU_ANCHOR_TEST_TAG`).
- The room the system keeps for itself: the back-gesture strips at the left and right edges and the gesture bar under the
  navigation bar may take a touch before the launcher sees it, whatever Home draws there.
- A long-press that lands exactly on the 1 px where the Dock's room meets the status bar's inset is dead in the probe; the
  probe's grid is offset by 1 dp to stay off that line, and it was not found at any other point. It is likely a rounding
  edge of the test's simulated inset and was not reproduced with a finger.
- The feel of the slop rule and the haptic on a real panel.

## Batch 7

Three things the owner asked for after a device pass: dismissing a card must feel clearly different from changing
stack, the unfolded stack is too small, and the Dock's resize animation is jarring. One commit each, tests in the same
commit.

### Dismissing a card against changing stack (item 1)

The owner: "dismissing a card vs scrolling between stacks doesn't have that much difference". After round 5 a sideways
drag that began on the front card of a dismissible notification dismissed it and one that began anywhere else paged, so
which of the two you got depended on a few dp of where the finger landed, and both moved a card sideways.

- **The rule** (`:core:domain` `SwipeDismiss.ownerOf`, pure, `SwipeDismissTest`): a sideways drag that starts on a card is
  always that card's. The front card of a dismissible notification dismisses it (as before); every other card (a strip, a
  notification that cannot be dismissed, a card of any other kind, a lone card) resists: it follows the finger through a
  rubber band (`SwipeDismiss.resist`, at most 12 dp, no fade) and settles back, and the pager does not get the drag. Only a
  drag that starts in the page's empty room, outside every card, changes the stack. Every card slot, strip included, and a
  lone card now carry the `draggable` (`SwipeDismissState` takes a `dismissible` rule; it is non-null for every slot).
- **Leaving a page without the Dock.** The empty room is the room above the stack (large on a folded phone) and the page's
  margins and, unfolded, the column's room beside and above the stack. The Dock stays the primary route (a tile, an icon,
  hold to choose, the contextual row) and "Previous page" and "Next page" are still the pager's accessibility actions
  (tested).
- **They look different.** A dismissing card alone moves; it lifts to 1.02 as it is taken, fades as it travels (to 50% at a
  full width, as before) and past 40% of its width it is armed: the word "Dismiss" comes in, in the room the card leaves, on
  the side it came from (`swipeCue`: a `drawBehind` ahead of the card's own graphics layer, text measured once, drawn in the
  draw phase; no recomposition while dragging). A page change moves the whole stack with the pager as one sheet, with the Dock's
  ring following (it moves when the page passes halfway, as before), and nothing fades. The dismissal's light tick stays; a
  page change has none (there was none).
- **A side effect that needed fixing.** The stack clipped itself to its own rectangle, which is 16 dp narrower than the
  surface on each side, so a card that gave a few dp toward an edge would have had its rounded corner cut. The clip is now
  the stack's rectangle widened by 16 dp at the sides (a `graphicsLayer` clip shape, no offscreen layer); the card is cut
  only by the pager at the edge of the surface. A dismissed card also flies a little farther before it is cut.
- **Hooks for tests** (semantics, read when the tree is read): `DismissArmedKey`, `DismissCueAlphaKey` and `SwipeOffsetKey`
  on a card's node.
- **Tests.** `:core:domain` `SwipeDismissTest` (the owner, the rubber band and its inverse, the cue and the lift).
  Robolectric `StreamSwipeDismissTest`: a non-dismissible front card resists a few dp, settles back and does not page (at rest
  and with the motion on, and with a fast flick), a strip likewise and is never dismissed, the empty room pages, the cue
  appears only past the threshold and releasing there dismisses, a resisting card never shows it, the pager's accessibility
  actions page. Device `StreamPagesInteractionTest` gains a swipe from a card that does not page.
- **Tests rewritten on purpose** (behaviour changed by the brief): `StreamSwipeDismissTest.aNotificationThatCannotBeDismissed...
  PagesInstead` and `aSwipeOnAStripOfAPageWhoseFrontCardIsDismissibleChangesThePage` asserted that a drag on such a card
  pages; they now assert that it resists and settles. Device `StreamPagesInteractionTest.aSidewaysSwipeChangesThePageAndTheDockFollows`
  and `StreamShortcutsTodayInteractionTest` (the swipe right to the pinned app's page) swiped at the pager's centre, which
  can land on a card; they now swipe in the empty room above the stack.
- **Not confirmed without a device:** the feel of the rubber band (12 dp), the lift and the cue's contrast over a busy
  wallpaper, and that the empty room above the stack is easy to hit on the smallest phones with three strips.

### Unfolded: the stack as tall as the Dock, with bigger cards (item 2)

The owner: "the card stacks when unfolded are small/short, I wonder if we should make the visible area the height of the
dock, and increase their size." Folded sizes did not change (the screenshots and the folded tests are as they were).

- **The column spans the Dock** (`StreamColumn.rect`, pure, `StreamColumnTest`). Beside a left or right Dock the column's top
  is the Dock body's top and its bottom the body's bottom: the Dock in its contextual mode (`StreamColumnWindow.dockBodyLengthDp`
  about the middle of the measured body, else the measured body), held inside the safe area. A Dock shorter than the column's
  least height (`StreamColumn.restHeightDp(unfolded = true)`: the tallest front card, three strips at the larger pitch and the
  gap, 512 dp, never less than the earlier 448 dp) is centred on instead. The stack stays bottom-anchored inside it. Above a
  bottom Dock the column keeps its centre on the Dock and takes the room between the safe top and the Dock, at most 560 dp
  (`BOTTOM_DOCK_MAX_HEIGHT_DP`). An open card keeps the bottom, and its top is the rest column's when that is already taller
  than the earlier 480 dp, so a tall Dock's open card may use all of its height. The grid's fade follows the same
  rectangle (nothing else was needed: it reads `StreamColumn.rect`), and so does unfolded search (same width and side; its
  bottom is the column's rectangle for the Dock as it is now, which is the Home mode's short Dock, centred, while search is
  open; its height still follows its results).
- **Wider.** The unfolded column is 0.38 of the window (360 dp at least), against a third (320 dp) before and still for the
  folded phone's "apps behind the cards" column (`restWidthDp(width, unfolded)`); an open card is up to half as before.
  The grid keeps more than half the window on a 1200 dp one. The hinge rule and the narrow-window clamp are unchanged.
- **Bigger cards** (`StreamCardSizes`, pure, `StreamCardSizesTest`; provided by `StreamColumnSurface` through
  `ProvideStreamCardSizes`, which also scales the theme's typography by 1.15 for the column's cards). A card of text (a
  notification, an event, an article with no image) is up to 1.35 times the folded maximum (227 dp, 292 dp with buttons); a
  card of fixed content (the player, an article with a picture, the checklist) 1.15 times its height so the larger text
  fits; a notification shows up to six lines of body (four before) and four messages of a conversation (three); the strip
  is 64 dp (56) so its header reads comfortably, and each strip is 2.5% narrower than the one in front (4% folded) because
  there can be six. `BrowseCardHeight.notification` and `NotificationBrowseText.blocks` take the sizes.
- **More strips.** The number of strips is what fits above the tallest card (`StreamCardSizes.peeksFor`), at most six and at
  least three (the folded phone keeps its three, whatever the room), and also what leaves an open card its 240 dp (below
  that the open card hides them). `StackGeometry` and the open card's layer take the peeks and the pitch.
- **Tests.** Domain: `StreamColumnTest` (a tall Dock, a short Dock, a Dock longer than the safe area, a left Dock, a narrow
  window, a hinge, a bottom Dock, an open card, the folded geometry unchanged), `StreamCardSizesTest`. Robolectric
  `UnfoldedStreamColumnTest`: the column's top and bottom equal the Dock body's within 4 dp on a tall Dock (eight pinned
  apps) on both sides, more than three strips at a 64 dp pitch, a front card taller than folded's maximum, a short Dock
  centred on with the least height, the grid's fade under the tall column, a bottom Dock's column up to 560 dp; new
  screenshots, dark and light, of a tall stack on a right Dock and light on a bottom Dock.
- **Tests rewritten on purpose** (the brief changes the geometry): `UnfoldedStreamColumnTest` asserted a third of the width
  (`about a third of the width`, twice), `restWidthDp(width)` and the search panel's width against the folded fraction, and
  that search's bottom is the Home-mode Dock's lower end; they now assert the unfolded column's width and rectangle. The
  fourteen unfolded goldens of that class were re-recorded (a taller, wider column with larger cards); no other golden changed.
- **Not confirmed without a device:** how the larger text and cards look with a real wallpaper and a hinge, the column with
  the Dock's measured body on every foldable, and unfolded search at the column's centred rectangle (its field now stands
  below the Dock's lower end while the Home mode's Dock is shorter than the column's least height).

### The Dock's resize animation (item 3)

The owner: "the dock resize animation is a little jarring." What the code did, read and then measured frame by frame with the
test clock (the Dock body's bounds and every icon's position at each 16 ms frame, bottom Dock on a 411 dp phone, right Dock on
an unfolded window; px at 2.625):

- **Opening, Home mode to contextual (before).** The body's length was a plain `width()` / `height()` of the strip
  (`DockSurfaceStrip`), so it was not animated at all: on the frame after the release (the shell is told then, and the
  contextual tiles appear) the bottom body went from 599 to 1014 px in one frame, the Home strip was swapped for the contextual
  row in the same frame (the first pinned icon jumped from x 277 to x 70, 79 dp; the icons grew 116 to 126 px) and the five
  tiles stood at full strength at once, two of them past the body's clip. A right Dock grew from 500 to 1094 px about its middle,
  so each pinned icon jumped 297 px (113 dp) along the edge in that one frame, and the tiles popped in whole. The Stream's
  cards fanned out over the next 200 ms against a Dock that had already finished. The two `animateContentSize` modifiers on the
  Dock's area (`StandardHomeDockArea`'s own `RiffleMotion.smooth()` and `dockShelfMotion`) did not run for this: the area is
  full width (or full height) along the Dock's edge and its thickness does not change, so there was nothing for them to ease.
- **Live apps arriving or leaving in Home mode (before).** The same snap, by reading: `dockSurfaceStripMainAxisDp` changes with
  the entries and the body's `width()` with it (not traced separately).
- **The actions row (before)**, the only place the two stacked modifiers did run, and measured: the row grew 0 to 157 px over
  about 20 frames in its own `AnimatedVisibility` spring, the area's height followed it 268 to 439 px through the two
  `animateContentSize` springs in series (about 45 frames to settle: three springs, one behind the next), and since
  `animateContentSize` keeps its content at the top and the area stands on the bottom edge, the Dock body, which is the
  content's bottom, was clipped to a shrinking window and pushed down: its bounds went 200 to 110 px tall and its top
  from 2157 to 2289 px (132 px past where it rests, below the area's clip) at frame 12 and rose back to 2159 by frame 44.
  The Dock visibly sank and bobbed back as the row opened; the container lagged the row, so what Home was told to reserve
  (the area less the row) lagged too.
- **Not found:** an overshoot or bounce on any of them (all the springs are critically damped), the Dock re-centring mid-way on
  the first frames (the first-frame easing was already off, and still is), or a relayout storm in Home's grid (it is not
  measured by any of it).

**What it is now: one motion.**

- **The body's length** (`DockBodyLength.kt`: `DockBodyLength`, `rememberDockBodyLength`, `dockBodyLength`, `DockBodyFrame`)
  eases from the length it has to the one it is to have, whatever the cause (the tiles appended by the Stream, a live app
  arriving or leaving): one `Animatable` on `RiffleMotion.smooth(tuning)`, the Dock's own spring at the opening speed
  (`DockMotionTuning`, the setting that moves the trampoline and the fan), critically damped, so no overshoot. It starts the
  moment the target changes: the contextual mode is shown at the release, as the cards start to fan, so the two start
  together. It is read in layout only: nothing recomposes per frame, and the Dock's cross size, and so the room Home reserves
  and Home's grid, are not touched (the grid's cells keep their size at every frame; unfolded they keep their bounds).
- **Closing is the opening reversed.** The row stays drawn while the cards go back in, but the body's target is already the
  Home mode's length (`HomeDockInterpreter.retracting`, from `DockMotionState.isClosing`; `Dock` works the Home length out with
  the same metrics), so it retracts with the cards, not after them. A closing that is taken back mid-way extends it again.
- **The content is laid out once, at its final size, and the body clips it** (`DockBodyFrame`): the row is not measured
  per frame and is never squeezed. The frame is the viewport the icons fade in (`LocalDockRunViewport`, the per-item
  opacity by visible fraction of round 3, `DockEdgeFade`): a tile fades in as the body reaches it and one it has not
  reached is not drawn, so no half-drawn tile and no tile outside the body. A few pixels of slack stop the rounding of an
  icon's size and gap from reading as a cut at rest. The Home strip's icons, which had no such fade, now have it through the
  same frame (a no-op at rest).
- **Where the pinned icons are.** The pinned run does not move relative to the body. The Dock is centred on its edge, and
  its contextual length is not its Home length, so the Home and the contextual Dock cannot both be centred with the pinned run
  in the same place: anchoring the run would put the contextual Dock off-centre (and, on a bottom Dock, off the screen's
  edge). I kept the Dock centred and made the body's growth the one thing that moves: the body grows about its middle,
  the pinned run rides the body's start edge (its offset from it is the same at every frame, a test), and the new tiles
  appear in the room beyond. So the pinned icons do move on screen, but with the body, smoothly, in the same single motion
  (a right Dock's icons slide 113 dp over about 15 frames instead of jumping that far in one), never as a second motion
  against the body. The ring and the selection do not scroll the row meanwhile: the row is laid out at its final
  size, so its scroll range does not change with the motion (also covered by the pinned offset being constant).
- **One thing animates a Dock's size, nothing stacks.** The area's two `animateContentSize` modifiers are removed (and
  `dockShelfMotion`, `easeSizeWhen`): the actions row grows in its own `AnimatedVisibility`, now on the same spring at the
  same speed (`DockActionsRow`), and the area is simply what it holds at every frame, so the Dock body does not move while the row
  opens and goes (the measured sinking is gone) and what Home reserves is exact. A change of the Dock's thickness (its icon
  size in Settings) and the shelf (off for everyone today) therefore land at once; they were eased by the modifiers.
- **Reduced motion:** the length is the target in the very frame it changes (not a frame late) and the row's content
  cross-fades in over the short 80 ms fade (`rememberDockContentFade`).
- **First layout and a change of edge:** unchanged (`rememberDockSizeEasing`): the body is laid out at its length from the
  first frame and never eased across a change of edge.
- **The Stream's column** no longer reads the Dock's body frame by frame: the body reports the rectangle it will have once it
  has arrived, and how long it is in the contextual mode (`HomeDockHostState.contextualLengthDp`), which the unfolded
  column spans, so the column does not relayout while the Dock moves.

Tests (`DockResizeMotionTest`, `DockLiveAppsResizeTest`, `DockActionsRowMotionTest`, Robolectric, the test clock stepped by hand,
through the real `HomeDestination`): opening on a bottom and a right Dock (the body's length is monotonic, never above its
final length, eased over several frames, starts within a few frames of the release, centred throughout; the first pinned
icon's offset from the body's start edge is constant and it never jumps relative to it; no tile is drawn outside the body and
tiles fade in part-way; Home's cells keep their size, unfolded their bounds), closing on both (monotonic, starts with the
cards, never undershoots), reduced motion (nothing between the two lengths is drawn), the first frames, a live app arriving and
leaving, and the actions row opening and closing (the row's height monotonic, the Dock body's bounds constant at every frame,
the row inside the area and above the body, Home's grid unchanged).

**Tests rewritten on purpose:** `DockEntranceTest.theDockAreaDoesNotEaseItsSizeOnTheFirstFramesButDoesEaseALaterChange` asserted
that the Dock area eases a later change of its thickness (the two `animateContentSize` modifiers); it is now
`theDockAreaHasNoSizeAnimationOfItsOwnSoAChangeOfItsThicknessLandsAtOnce`. `DockShelfMotionAndFrameRateTest` still tests the shelf
motion policy; nothing draws with it now.

**Not confirmed without a device:** how the sideways slide of the pinned icons reads on a real bottom Dock (it is the whole
width, 79 dp), the match of the length's spring against the cards' fan (they share a tuning but are two springs; the frame
tests show them starting together), and a Dock with a very long run of live apps.

### Bugs from the owner's phone: the front card blinking out, and the stack at the top of the screen (A and B)

Both came from the open card's overlay, and one commit fixes both.

- **Cause (B, and half of A).** Every stack on screen publishes the open card's layer (`OpenCardHolder.publishLayer`) and the
  Dock's actions (`publish`) in a `SideEffect` on every composition, with null when it has no card open. Two stacks are composed
  while pages are swiped or one is going out, and the one with nothing open erased the layer of the one that had a card
  (last writer wins). The stack still believed a card was open (`layerActive`), so its front slot stayed hidden (the hole above the
  Dock: the front card "blinking out", strips left standing) and the stack was shifted to the open card's frame by
  `frameGeometry.topPx`, which had not been laid out for lack of a layer (0): the stack was drawn with its front card at the top of
  the screen under the status bar. Fix: a stack takes back only what it published (`PublishOwner`, tested); the slots give way to
  the open card, and the stack shifts, only once the layer exists (`drawnByLayer`), which also removes a one-frame hole when a
  card opens (a test fails on it); and the stack's position is held to its cards in the very frame they change.
- **Cause (A, the other half).** A swiped card flew off and stayed off screen for two seconds waiting for the app to remove the
  notification; an app that re-posts it brought it back, and each swipe left a hole. A swiped card is now out of the stack at once
  (the next card comes forward) and, if the notification is still there after two seconds, comes back once. The Dismiss button and
  the accessibility action are unchanged (the card stays until the app answers).
- **Candidates checked and not the cause:** round 6's per-card heights (a re-post re-measures a card, never re-keys it: the id is the
  notification's key), the keyboard's inset (the strips are simply hidden and returned), the position past the end after a
  dismissal (already clamped in an effect, now in the same frame too).
- **Tests.** `StreamFrontCardStaysTest` (every frame: the front card or the open card is drawn and stands on the Dock) re-posting at
  rest, while open, while closing, after an ignored swipe, with the keyboard coming and going, dismissing down to the last card and
  re-posting it; `PublishOwnerTest`. The ignored swipe and the opening frame fail without the fix. The test of the two-stacks
  clobber itself is the unit test of `PublishOwner`: two composed stacks were not reproduced through the pager here.
- **Not confirmed without a device:** that the owner's recording is this cause and not another.

### Search from the Dock: legibility and the floating button (C)

- **Why the field looked washed out.** The panel is Thick glass lit by `launcherPanelContentColor`, but the field inside it was an
  `OutlinedTextField` taking the theme's `outline` and `onSurfaceVariant` for its box, its floating label and its hint: colours tuned
  for a plain surface that read pale on the glass, and a second box inside a box. The field is now `PanelSearchField` (an unlined
  `TextField`: a transparent container, no indicator, the text and the caret in the panel's own content colour, the hint in it at 74%),
  so the glass is the field's container. The other search fields (Library, Settings, widgets) are unchanged.
- **One results surface.** The empty answer and "Search web" are one glass surface directly above the field (a small seam of
  4 dp), and with results "Search web" is the results' own footer, not a pill floating between them and the field.
- **Tests and screenshots.** `HomeHandleScreenshotTest`: the empty-results state and the typed state, dark and light
  (`searchWithNoResults*`, `searchWithNothingFoundDark`, the earlier search goldens re-recorded); `DockSearchPanelTest` (the action
  stands within 20 dp of the field in both states; it does not by itself tell the old layout from the new, the screenshots do).
  Search as a card stack is a later piece of work and was not started.

### App pair experiment (diagnostics only)

- **What it is.** Settings > About > Diagnostics > "App pair experiment": pick two apps, pick a method, press Launch, and read the
  in-memory result log. It exists so the owner can find out whether launching two apps as a split-screen pair works on their foldable
  before any feature is designed. It adds nothing anywhere else and stores nothing.
- **Methods.** Sequential (first app with `NEW_TASK`, a 0-2000 ms wait, second with `LAUNCH_ADJACENT | NEW_TASK | MULTIPLE_TASK`);
  Trampoline, adjacent second / adjacent first (a transparent no-history activity in its own task starts both); Both adjacent (two
  starts back to back); One `startActivities` call (second intent carries the adjacent flag). Public APIs only.
- **How to run it.** On the foldable, folded and then unfolded: choose the two apps, try each method, and for each note whether both
  apps appeared side by side, which app took which side, and the split ratio. Then "Copy log" and paste it back. Work and private
  profile apps are not listed (the launcher gateway cannot add intent flags).
- **What to report back.** The pasted log (it holds package names and device facts only), plus for each method what you saw. The log's
  "ok" only means the call returned without an exception; a start the system refuses (background activity launch) is silent, so the
  screen is the verdict. If only the first app opened, the second start was probably blocked.
- **Known limits.** No public API chooses the ratio or the side; `ActivityOptions.setLaunchBounds` only applies in freeform windowing;
  `ActivityInfo.resizeMode` is not public, so resizability cannot be logged.

## Quick actions, and apps behind the cards

Two things the owner decided on the design canvas (boards QA, QA2, QA3 and QA-U1), one commit each.

### Quick actions: an app's shortcuts leave the stack

The owner: "once a pinned card is dismissed, the remaining items are the standard context actions; it'd be better to have
these available as quick actions".

- **The model** (`:core:domain` `StreamShortcutPages`, `QuickAction`, `StreamPage.quickActions`, tested in
  `StreamShortcutPagesTest`). An app's page holds only its live content. Its launcher shortcuts are the page's
  `quickActions`, a typed list over the existing `AppShortcutContent` (so the one action gateway launches them, profiles
  included), in the platform's rank order, **at most eight** (`MAX_QUICK_ACTIONS`), **disabled shortcuts left out** (a chip
  that does nothing is noise). Round 2's merge no longer interleaves cards: `merge` leaves a live page's cards as they are and
  adds the actions. A pinned app with nothing live still has a page: **one quiet card** (its icon, its name, "Nothing new";
  a tap opens the app) and its quick actions. An app with no shortcuts and nothing live is that card alone, as the old "Open
  <app>" card was. Without the launcher role there are no quick actions and the existing "Shortcuts need Riffle to be your
  Home app" notice stays behind the card (on a live page too), so the page says why there is no row. Recents, Today, Now and
  Feeds have none.
- **Folded, a Dock along the top or bottom edge** (`DockActionsRow.kt`, `QuickActionsRow.kt`). The chips are in the Dock's one
  body above the icons with a hairline between, the same mechanism as the open card's actions row: the body's outline grows
  with the row; the pinned run stays put. A chip is the shortcut's small icon and label, a 48 dp target; the row scrolls
  sideways and a chip at a clipped end fades by how much shows (`dockEdgeFade`). A tap launches and the Stream closes as for any
  launch from it. With a card open on the page the card's actions take the **same** row (a cross-fade; never two rows) and the
  chips return when it closes; a change of page cross-fades the chips; a page without quick actions has no row. The row's
  height now eases on the Dock's one spring to the height of what it holds (`DockHeaderState` holds either), so a swap of
  content is one motion. With "Where its actions sit" on the card, the card's actions stay on it and the row still lives in
  the Dock. Unfolded with a bottom Dock the row is in the Dock as folded.
- **A side Dock, either posture** (`UnderStackQuickActions` in `StreamPageView`). The chips are a Thin glass strip under the
  stack, as wide as the cards, at the bottom of the page (the column's lower end); the stack is bottom-anchored just above it,
  so it gives up the row's height and the column's rectangle is unchanged. **While a card is open the strip is hidden** (it
  fades with the open card's motion and leaves the tree): the open card grows over the room it keeps.
- **Accessibility.** The row is a labelled group ("Quick actions for <app>"), each chip a button named by its shortcut; the
  same actions are custom accessibility actions of the front card (and of the quiet card, a lone card).
- **Where things moved.** `PageQuickActions` (the page's actions as a row needs them) is published by `StreamSurface` to
  `StreamDockLink.quickActions` for the Dock; `LocalQuickActionsInDock` says which place a surface uses.
- **Tests.** `StreamShortcutPagesTest` (rewritten: live cards only, the list, the cap and order, the quiet card, no role),
  `QuickActionsTest` (Robolectric, through the real shell: folded the row inside the Dock's body above the icons, chips launch
  through the gateway, a card replaces and then restores them, a change of page swaps them, the quiet card, accessibility;
  unfolded the strip under the stack inside the column and hidden while a card is open; screenshots dark and light).
- **Tests rewritten on purpose:** `StreamStripsTest` (a merged page of a notification and three shortcut cards; now a page of a
  notification and cards of another kind, and a live page has no shortcut card), `OpenCardNeighbourWithNothingToOpenTest` (a
  shortcut card was the neighbour with nothing to open; it is now a recent-app card), `StreamFrontCardStaysTest` (the
  last card is now a lone card, which carries its stack-card tag so the helper finds it), the shortcuts goldens of
  `StreamScreenshotTest`. Device: `StreamShortcutsTodayInteractionTest` taps shortcut cards and counts cards; see its notes.

### Apps behind the cards: a choice of three per posture

The owner: "make it configurable please, I like the choice". Per posture, in Settings > Layout and Settings > Stream > Cards
(one shared row, `SettingsAppsBehindCardsRow`, one stored value; its subtitle states the choice):

- **Hidden**: Home fades out when the Stream shows (unfolded, where Home always stays beside the column, nothing shows under
  the column: as the old "off").
- **Around the cards**: Home stays; any app that meets a card of the stack (front card, strips, an open card) or the quick
  actions row is fully hidden, with a 24 dp feather (`StreamGridFade.aroundFactor`, pure) so an icon never pops; apps beside
  and above the cards keep the distance fade at its floor.
- **Behind and around**: the old "on": apps under the cards show at the floor.

Stored data: `PostureLayoutSettings.appsBehind` (`AppsBehindCards`), stored by id under the same keys; an old boolean reads
`true` as Behind and around and `false` as Hidden; unset: folded Hidden, unfolded Behind and around (`LayoutSettingsCodecTest`).
The hiding follows the cards' real rectangles: every card reports its coordinates (`CardRects`, `reportsCardRect`) and a cell
works its factor out **in the draw phase** (`GridFadeCell`, `GridFadeField.alphaAtPx`), so a moving stack redraws cells and
recomposes nothing (the recomposition probe tests pass). A cell that is faded out takes no input by the existing alpha rule.
Tests: `StreamGridFadeTest`, `LayoutSettingsTest`, the codec and reducer tests, `FoldedAppsBehindCardsTest` and
`UnfoldedStreamColumnTest` (alphas under, beside and clear of the cards, per posture), `SettingsSharedRowsTest` (both entry
points show and write the same value); screenshots dark and light of Around the cards, folded and unfolded.

## The Dock's open and close: one size, one place, no blink

The owner, from a recording of the unfolded Dock on the right edge: "the dock open/close still has jitter/icon size changes".
A blank frame, the icons smaller in the contextual mode, and the pinned run sliding (and the first icon clipped at the top).

- **Measured before** (the test clock, a frame at a time; px at 2.625): bottom Dock on a phone, the first pinned icon was
  116 px in the Home mode and 126 px from frame 3 of the opening (the contextual row drew every item as a 48 dp target and
  closed the gap up until six fitted), its x going 277, 277, 277, 277 to 241 within the body's own growth about its middle
  (body 240..839 to 33..1046); right Dock on a foldable, the body went 660..1160 to 363..1457 about its middle and the first
  pinned icon went from y 688 to y 391 (297 px, 113 dp) in about 15 frames, ending near the Dock's top end.
- **After** (same frames): the pinned icon is 116 px (bottom) and 96 px (right) on every frame of opening and closing, the gap
  between neighbours constant; on the right Dock the body is 660..1160 growing to 660..1754 and the first pinned icon stays at
  y 688 on every frame; on the bottom Dock the body grows from 240..839 to the right and the first icon stays at x 277 until
  the body's end reaches the edge's room (frame 8, x 1046), after which it also grows back to the left (277 to 70 at rest).
- **The blank frame.** The Dock's content was drawn at opacity 0 for the frame in which the mode changed, in every motion
  setting: `rememberDockContentFade` set the opacity to 0 the moment the mode changed and returned it to 1 in an effect that
  runs after that frame (it was meant for reduced motion's cross-fade). Now only reduced motion dips and fades back; with the
  motion on the content is drawn on every frame. **I could not reproduce the blank frame with the test clock** (the effect
  runs before the frame is sampled), so this is the cause found by reading the code, not one seen failing.
- **One size per Dock** (`dockContextualSlotMetrics`, `DockSurface.kt`). The pinned icons keep the Home mode's size and gap
  (what its pinned run needs, whatever the Dock's setting and room) in the contextual mode. Only the appended live apps and tiles
  are the 48 dp target, with the same gap. The row gains its items by growing the body and then by scrolling, never by shrinking
  an icon or closing the gap up (the "six whole items" rule is gone).
- **The pinned run stays where it is** (`dockAnchoredShiftPx`, `DockBodyAnchor`). Extended, the body keeps its start edge where
  the Home mode's body had it and grows away from the pinned run (a side Dock: downward; a bottom Dock: toward the end of the
  row), and only when its end reaches the edge's room does it also grow back the other way. So the Dock may be off-centre on its
  edge while it is extended; the Home mode's body is centred as ever and the change is one continuous motion on the same spring.
  The body reports its arrived rectangle (`anchoredArrival`) and **the Stream's column follows that rectangle**: it spans the
  body's actual top and bottom (side Dock) or is centred on the body (bottom Dock), wherever the body stands, not the edge's middle.
  Left to right text direction is assumed for a bottom Dock's "end".
- **The page handle** (`FadingHandle`, `StandardHome.kt`): unfolded, where it is hidden while the column stands, it fades on the
  Dock's own spring (a snap under reduced motion) instead of popping out and in in the frame the mode changes.
- **Tests** (`DockResizeMotionTest`, Robolectric, the test clock stepped by hand through the real `HomeDestination`, a right Dock
  unfolded and a bottom Dock folded, opening and closing): on every frame every pinned icon is placed, has a size, is drawn and the
  content is not blanked; each pinned icon's size and the gap are equal on every frame; the start edge stays until the end has
  reached the room. `DockDynamicSectionMetricsTest` (the pinned run is the Home mode's on every width) and
  `DockContextualModeTest`.
- **Tests rewritten on purpose:** `DockResizeMotionTest.assertTheBodyExtends` asserted the body stays centred on its edge (now: it
  grows away from the pinned run); `DockContextualModeTest.aPhoneWideRowShowsSixWholeItemsOfOneSizeAndTheRestScrollsAway` asserted
  the 48 dp item and six whole items (now the pinned size and gap are the Dock's); `DockDynamicSectionMetricsTest` (the run's
  length and the six-whole-items test); `UnfoldedStreamColumnTest` (two tests asserted a bottom Dock's column centred on the Dock's
  strip; now on the body). **Not confirmed without a device:** that the owner's blank frame was this cause, the look of an
  off-centre Dock on a real foldable, a Dock with a very long run of live apps, and a right-to-left layout.

## Search stack

The owner found search "bumpy": results of three kinds in two layouts, a list that grows and shrinks under the thumb. The
decision: **the Dock's search shows its results as a card stack**, the Stream's own (ADR 0003 and its amendments: the stack
anchored to the Dock, strips that tuck under the front card, the per-card spotlight, quick actions). No new Place, no new
kind of content, no new visual language.

- **The model** (`:core:domain` `SearchStack`, `SearchStackCard`, `SearchStackTap`, `SearchStackLayout`,
  `SearchStackNotifications`, tested in `SearchStackTest`). The hits (apps, then shortcuts, then settings, each in the
  search's rank order) become cards: the **best match is the front card**, the next matches are **strips behind it in rank
  order**, and **"Search web" is the last card** when the query is not blank (the existing offer), and the **only card** when
  nothing matches (it then carries the existing "No results found" wording and the existing "Search web" pill). An app card
  carries the app's **quick actions** exactly as its Stream page plans them (`StreamShortcutPages`: enabled, platform rank, at
  most eight, the one `AppShortcutContent` so the one action gateway launches them, profile included) and, when the app has
  notifications, a **short line for the newest**. A shortcut hit that already is one of its app card's quick actions is not
  a strip of its own.
- **The tap rule** (`SearchStackTap.onStrip`). A strip **with quick actions or a notification** comes to the front and shows
  them; it does not open the app. A strip with **nothing more than a name** (an app without shortcuts or notifications, a
  shortcut, a setting, "Search web") opens straight away. A tap on the **front card** opens it, and so do **Enter and the IME's
  search action**: they open the front card, which is the best match until a strip has been brought forward, and "Search web"
  when nothing matched (earlier, Enter on no results did nothing). The strips that have more carry the Stream's own "more"
  glyph (the vertical dots its cards use for their menus), dimmed, at the trailing end of the header; it is decorative, the
  strip's state description says it.
- **Fixed geometry** (`SearchStackLayout`). The front card has one height (152 dp folded, times the unfolded fixed factor) and
  the stack one height for a posture (its strips, the front card and the Stream's own insets), in one rectangle at the bottom of
  the room search is given, just above the field. Nothing in it depends on the results: a keystroke changes what the cards say,
  never where they stand or how big the front card is (`SearchStackBehaviorTest`: bounds equal across typed queries, across an
  app with quick actions, with a notification and with nothing more). Only the room (the keyboard) can shorten the stack, and
  then it shows fewer strips (down to none), never a smaller front card. The strips shown are the posture's: three of 56 dp
  folded, as many as fit up to six of 64 dp unfolded. The rest are reached as in the Stream: dragging the stack, "Next card"
  and "Previous card" as accessibility actions. A new query puts the best match in front again.
- **Where it stands.** Folded (a bottom or top Dock) it is the old results' place: the panel's width, centred, above the field,
  which stands above the Dock and the keyboard. Unfolded (a side Dock) it is the Stream column's rectangle
  (`StreamColumn.rect`), the field at its bottom, the stack above it, in the unfolded sizes (`ProvideStreamCardSizes`). At 320 dp
  with the keyboard up the stack stays on the screen, above the field and inside the width (`SearchStackNarrowTest`).
- **What is reused.** `StackLayout`, `StackGeometry`, `StreamStackState` and `StackMover` (the position and its drag, release
  and steps), `stripCut`, `StreamCardSurface` (the glass, the per-card spotlight and tint), `StreamCardHeader`, `QuickActionsRow`
  and its chips, `SearchWebPanel`. The Stream's open card, swipe-to-dismiss and Dock fan are not used. The position is read in
  layout and draw, so a drag recomposes no card (a recomposition-probe test).
- **Notifications.** The count is the launcher's own (what the app's badge counts). The newest notification's line comes from
  the Stream's notifications section when the Stream is on: that reading is already redacted by the Privacy content level, the
  hide rules and the quiet profiles, so the card says nothing the Stream would not ("Hidden content" when text is withheld). It
  is subscribed only while the stack is shown. With the Stream off the card says only "N notifications", and a quick action is
  launched through the shell's own shortcut launch instead of the Stream's gateway.
- **The setting.** "Search results" (Settings > Library; its one place) gains **Card stack**, the default for new installs and
  for settings saved before the choice existed; a stored Icons or List keeps its value (`LauncherSettingsJsonCodecTest`,
  `SearchResultsSettingTest`). The Library's own search field has no stack: it reads Card stack as Icons.
- **Accessibility.** The front card is one node, a button named for the app ("Maps", with "Work - " for a profile as the lists
  say it) whose click opens it and whose quick actions are custom actions (as the Stream's front card); the quick action chips
  stay buttons for touch. A strip is one button named for its result with the state "Shows quick actions or notifications"
  when it has more; its click follows the same tap rule. The stack has "Next card" and "Previous card" and says "Card n of m".
- **Tests.** `SearchStackTest` (the ranking, the cap and order of quick actions, which cards have more, the web card last or
  only, duplicates, the tap rule, the geometry), `SearchStackBehaviorTest` and `SearchStackNarrowTest` (Robolectric, through the
  real shell content: both branches of the tap rule, Enter on the front, the quick action through the one gateway, the
  notification line, fixed geometry while typing, the cap and stepping, the stored Icons and List choices, accessibility, a
  drag recomposes no card), `SearchResultsSettingTest`, `SettingsPagesTest` (the option is found by "card stack"),
  `SearchStackScreenshotTest` and `SearchStackUnfoldedScreenshotTest` (folded and unfolded, dark and light, with results, with
  a notification and with none; large text).
- **Tests rewritten on purpose:** `LauncherSettingsTest` (the default is the stack, not icons) and
  `LauncherSettingsJsonCodecTest` (a missing or unknown value reads the stack; every stored value round-trips; a stored Icons or
  List is kept).
- **Not confirmed without a device:** the feel of the stack under a real thumb with the keyboard up, the IME's search key, a
  launch of a quick action from the stack through the real gateway and a work profile, the notification line against a real
  listener, and TalkBack's reading of the front card and the strips (the semantics are asserted, the speech is not).

## Open card polish

Device report from the owner's unfolded phone (724 x 784 dp, the Dock on the right edge, a page with three notification cards
and the quick actions strip under the stack): "expanded cards need to be bigger", "if we're allowing expanded cards to scroll to
the next card then there needs to be a visual indicator", and "work on the animations to transition/dock/undock quick actions".
The recording showed four things; each had a cause in the code, which the tests below pin.

### What the recording showed, and why

1. **The open card appeared somewhere else and was small.** The open card's layer measured from the bottom of the Stream's whole
   area (the Dock's gap), but the collapsed stack stands higher on a side Dock's page: the page keeps the quick actions strip's
   room under the stack (gap 8 + strip 52 + padding 8 dp). So at progress 0 the card was 68 dp lower than the front card it was
   meant to grow from: the first frame was a jump, the card covered the strip, and on close the card landed 68 dp low and the
   stack then snapped up. The "strip vanishes in one frame" was that cover. The card's height rule (`room` minus the strips of
   the cards behind it) was right but the room was cut at the top by the strips only; its content measure gave a short card the
   height of its text, which could be less than the front card it came from.
2. **Handoff mess.** The card leaving and the card arriving were each drawn whole at the height the blend gave them, with the
   content measured at that height: a body, its reply row and its actions overflowed a card that was a strip tall, and the two
   bodies were drawn through each other. Nothing said a neighbour was reachable until the handoff had happened.
3. **Strips only after the handoff.** The cards behind the open card were strips of the *stack*; the cards in front of it had no
   strip at all, so the look of row 3 existed only for the older cards, and a card opened by the handoff had nothing below it.
4. **Closing landed, then moved.** Same cause as 1: the layer's rest place was not the stack's. The strip came back in a single
   frame because it was out of the tree while the card was open and its room was reserved only by being there.

### What was built

- **One rectangle, one spring (A).** `OpenCardLayout.frame` takes the collapsed front card's bottom edge (`restBottomPx`) and
  the room the cards in front take under the open card (`belowPx`). At progress 0 the card is exactly the front card; top,
  bottom and height are each linear in the progress, so they move one way only. A card is never shorter than its browse card
  (`fullHeight` is raised to it); a card with more to say takes all the room: the column's height less the strips of its
  neighbours and the Dock's gap. The page reserves the strip's room always (`StackFooter`, `LocalStackFooter`): the strip has
  one fixed height (`QuickActionsStripHeight`, 52 dp), the room is there on every page of a side Dock whether or not the page
  has quick actions and whether or not a card is open, and the layer reads it, so the rest place of the stack is a constant.
  Edges are rounded to whole pixels, the height is the difference: no pixel goes back.
- **Folded.** The layer's area is the Stream's, inside the system bars, down to the Dock's room, which grows with the row of the
  card's actions: the card's top strip stands 8 dp from the top of the area and its foot 26 dp above the Dock. Both edges move
  one way (the Dock rises under it, or falls), checked by `OpenCardPolishFoldedTest`.
- **Neighbours from the first frame (B).** The cards behind stay as the stack's strips above the card (the stack moves to the
  card's top). The cards in front of it are strips below it (`OpenLayerStrip`, the real card cut off at its header, one node for
  assistive technology: "bring to front"), as many as leave the card its least room (`OPEN_CARD_MIN_DP`), tapping one opens
  that card. Their slots do not move while the card changes: the card that leaves or arrives takes the slot of the one that
  arrives or leaves, so each strip stands still and one card moves between them.
- **The handoff.** The card's body (thread and actions) has an opacity of its own (`bodyAlphaAt`): nothing until the card is half
  open, full from there, so the body of the card that leaves has gone before the body of the card that arrives shows: no
  moment has both (a pure test over the whole range). The card is clipped to its own bounds, the header scales from 0.75 to 1
  with it (the headers around it are about that size), the actions row stays at the foot of a card held at its browse height.
  Everything moves on the position of the stack: one spring.
- **The cue.** Past the edge of a scrolling body the stack does not move for 56 dp (`OpenCardHandoff.THRESHOLD_DP`). The finger's
  travel past the edge now fills a cue: a chevron and the neighbour's title in a small Regular-glass pill at the top (older
  card, finger down) or the bottom (newer card, finger up) of the body, its type the Dismiss cue's (`labelLarge`), opacity
  rising with the first two thirds of the drag, a line along its foot filling to the threshold, the body dimming by up to 55 %
  so the cue reads over it. It is drawn only toward a neighbour that exists, is no part of the tree for assistive technology,
  and is read in draw (a recomposition probe, `handoff-cue` and `open-strip:`). The card's own "Next card" and "Previous card"
  custom actions were already there (stack of more than one card); a test pins them.
- **Quick actions (C).** Unfolded, the strip leaves the way the card arrives: it fades and slides down by its own height on the
  card's growth (`openCards.motion.progress`, read in draw) and returns the same way; its room is reserved throughout, so nothing
  around it moves. **Decision:** a page with no quick actions keeps the room empty and the stack keeps its place on every
  page; the stack does not re-anchor (re-anchoring would need its own spring and would move the stack under the finger as
  pages swipe; an empty band under it is the cheaper cost). The strip belongs to its page, so on a page change it goes with the
  page on the pager's own motion (a slide, never an appearance or a disappearance); chips do not cross-fade inside one strip
  because no strip is shared by two pages. Chips: `QuickChipFit` fits them whole when their labels can give up their ends
  (every chip wider than a common cap is ellipsized, none narrower than 88 dp); only when that cannot be, the row scrolls and a
  chip less than half in view is not drawn at all (`DockEdgeFade.alpha(vanishBelow = 0.5)`), one more in view is quiet.
  Folded, the Dock's row swaps the chips for the card's actions: the row is one node holding one of the two, its content fades out,
  swaps while nothing of it is drawn, and fades in while its height eases on the Dock's spring. Nothing was found to pop there;
  the per-frame test (`OpenCardPolishFoldedTest`) pins it (`DockHeaderAlphaKey`, a test hook like `DockEdgeFadeAlphaKey`).

### Per-frame numbers (owner's window, xhdpi so px = 2 x dp; the stack's front card and the open card)

Collapsed front card before opening: left 300, top 482, right 628, bottom 688 dp (328 x 206 dp). After closing: the same
rectangle, to the pixel (before the change too, but only after a jump, see below).

| Frame after the tap | Before: open card top / bottom (dp) | After: open card top / bottom (dp) |
| --- | --- | --- |
| 1 | not drawn yet | not drawn yet |
| 2 | 550.5 / 756.5 (68.5 dp lower than the front card: the jump) | 482.5 / 688.5 (the front card) |
| 3 | 547.5 / 756.5 | 481 / 690 |
| 5 | 533.5 / 756.5 | 473 / 696 |
| 10 | 484.5 / 756.5 | 445.5 / 717.5 |
| 20 | 426 / 756.5 | 411 / 745 |
| 40 | 400.5 / 756.5 | 398 / 756 |
| settled | 396.5 / 756.5 (360 dp tall) | 396.5 / 756.5 (360 dp tall) |

The settled card is the same size in this window because its room (the column, 512 dp here, less two 64 dp strips and the gap)
was always the limit; what changed is where it starts from and that it starts there. A lone card, with no strips, takes the
column less its gap. On closing, before: the last card frame stood 68.5 dp low and the stack was drawn 68.5 dp higher the frame
after (a jump of 137 px); after: the last card frame is the front card (482.5 / 688.5) and the stack is where it landed. The
test `OpenCardPolishUnfoldedTest` samples every frame of a 80 frame opening and closing: the stack's rectangle is equal before
and after (0.5 px), the card's first drawn frame is the front card's rectangle (1.5 px of rounding), top never goes back down,
bottom never goes back up, the left edge never goes back in, and the strip's reserved room never moves.

### Fit content, up to the Dock's height (owner's decision)

The rule: an open card is as tall as its content needs (header, the whole body, the reply row, its own actions), never shorter
than the collapsed front card. Its ceiling unfolded is the Stream column, which spans the Dock body (not the window), less the
room its neighbours take: 64 dp for each older card above it (28 dp since "Slim neighbour strips", below), the strips of the newer
cards and their gap under it, the page's 8 dp above and 16 dp under the stack. Folded it is the room between the status bar's inset and the Dock. The body scrolls only
once the card has reached the ceiling; a card under it has no scroll range and no empty room inside; a short card stays short.
The code already did this (`OpenCardLayout.frame`: `full = max(min(content, room), browse)`, the content measured at its
intrinsic height, never a fixed maximum), and the column is the Dock's contextual length centred on its body, so no cause was
found in the rectangle (`OPEN_HEIGHT_DP`, `REST_HEIGHT_DP` and `openTop` only matter when the Dock body is shorter than 480 dp)
or in the strips' reservation. `OpenCardFitUnfoldedTest` and `OpenCardFitFoldedTest` assert the rule at layout level through the
real shell and pass on the code as it was; they replace "rests on arithmetic" for the lone card too.

Numbers in the owner's window (Dock body 272 to 761 dp = 489 dp, column 260.5 to 772.5 = 512 dp, xhdpi), card height / body
scroll range, before and after (the same: nothing changed in the code):

| Page and front card | Card | Scroll range | Reading |
| --- | --- | --- | --- |
| three chats, 12 sentences (long) | 360 dp | 376 px | at the ceiling: 512 - 24 - 2 x 64 = 360; strips 268.5 to 396.5, card to 756.5: the stack spans 488 dp of the Dock's 489 |
| three chats, 8 sentences | 360 dp | 152 px | content is 436 dp, ceiling 360 dp: it scrolls |
| two chats, 6 sentences | 380 dp | 0 | content, under the ceiling of 424 dp (one strip) |
| three chats, "Hi." | 259 dp | 0 | the collapsed card's height |
| one chat, 12 sentences | 488 dp | 240 px | no strips: the whole column less 8 and 16 dp |

What the owner saw is this ceiling: with two older cards the strips take 128 dp of the Dock's ~470 dp, and the chrome of a
conversation card (header 56, reply row 60, own actions 48, gaps) takes about 212 dp of the rest, so a body of eight messages
shows about 148 dp of itself (four lines) in a 360 dp card. A taller card needs fewer strips or a different rule for them,
which is a product decision, not a fit bug; see the report with this change.

**A card whose content changes while it is open** (a message arrives): the card is measured at its content's height on each
layout pass once settled, so its height changes in that frame; it does not ease on the spring. Not tested (the harness posts its
notifications once); found by reading `OpenCardFrames`. Easing would need the natural height as animated state read in layout,
which the one-measure-per-child rule of that layout makes more than a small change, so it is left.

### Slim neighbour strips (owner's decision)

"While a card is open, its neighbours shrink to thin title-only tabs so the open card gets more reading room, and the
next-card indicator stays." Measured before the change, unfolded at 724 x 784 dp with the Dock on the right and three chats:
the column is 512 dp and each neighbour strip took a 64 dp pitch, so with two neighbours the open card's ceiling was 360 dp and
its body's viewport 148 dp (about four lines).

- **The rule.** With a card open, each neighbour (older above, newer below) is a slim tab: one line, a leading icon or avatar
  of 18 dp (`SlimTabs.ICON_DP`; the person's avatar on a conversation, the app's icon, else a glyph of the kind) and the title
  in `labelMedium`; no subtitle, no time, no body. **Pitch: 28 dp** (`SlimTabs.PITCH_DP`): the line of a small label is about 16 dp,
  which leaves 5 dp of air above and below the icon so a tab reads whole, and it is the figure that takes the ceiling to the
  asked-for 432 dp (512 - 24 - 2 x 28). Smaller would clip the label at large font sizes for no more room worth having. A tab
  is the card's own glass drawn only as far as its header (`StreamCardSurface`), tucked under the open card by the same cut as a
  strip ([`stripCut`](../../app/src/main/java/com/riffle/app/launcher/stream/StreamStripCut.kt)), 4 dp apart below it as before.
- **The ceiling.** The room the open card has is the column less the page's 24 dp less 28 dp per neighbour. The fit rule is
  unchanged: content height up to the ceiling, never shorter than the collapsed front card, the body scrolls only at the ceiling.
  How many neighbours show is unchanged too: the same count as before (`peeks`, and the room the open card must keep, are asked
  at the full pitch), so a slimmer tab adds none; with more room the cards still fit and are not made more.
- **Touch targets.** A tab is 28 dp to look at and 48 dp to hit (`SlimTabs.TARGET_DP`), the whole width of the card's column
  and a little beyond it. The layer's scrim finds the tap (`SlimTabs.hit`): the tab whose centre is nearest within 24 dp of the
  tap, so a tab's target reaches past its edges into the room its neighbours' targets share (the nearer centre decides) and,
  for the outermost tab, into the room beyond it. It never reaches the open card: a tap on the card is the card's (its header
  closes it) and a tap beside the card's edge toward the tabs only counts on the tabs' own side of it. Where two tabs stand 28 dp
  apart their two 48 dp targets cannot both be whole: each tab's target there is its own 28 dp band split at the middle line,
  and only the outermost tab has the full 48 dp. That is the geometry, not a choice; Previous card and Next card (custom actions
  of the open card, and Previous and Next in a drag from the header) are the equal route to any card.
- **Accessibility.** Each tab is one node, a button labelled "<title>, open" (`stream_open_tab`; it was the card's whole text
  and "Bring to front" while the cards were strips, and still is when none is open), whose action opens that card. The open
  card's "Next card" and "Previous card" actions and its cue are as before.
- **Motion.** On the open card's own progress (so on its spring: `OpenCardMotionState`), the strips' pitch eases from the stack's
  (64 dp unfolded, 56 dp folded) to 28 dp, and the cards above swap their faces for their tabs' (the card fades out as the tab
  fades in on the same number, both in draw). Closing is the same in reverse; the collapsed stack's rest rectangle is exactly what
  it was. The open card's frame is made against the room the tabs leave in the end, so its top, bottom and height stay linear in
  the progress and move one way only; the strips above stand on the open card's top, so they stay in the area. During a hand-off
  to a neighbour the progress stays at 1 and the tabs move to their new slots on the stack's one spring. Nothing recomposes
  per frame: the pitch and the faces are read in layout and draw (`StackLayout`, `stripCut`, the open card's layer), and a
  probe test pins it.
- **Applies** folded and unfolded.

Numbers, owner's window (xhdpi), before / after:

| | Before (two strips of 64 dp) | After (two tabs of 28 dp) |
| --- | --- | --- |
| Ceiling of the open card (three chats, a long card) | 360 dp | 432 dp |
| Body viewport (the card less its header, reply row and their spacing, 212 dp) | 148 dp, about four lines | 220 dp, about six |
| Scroll range of a 12-sentence card | 188 dp | 116 dp |
| Six sentences, three chats | 360 dp card, scrolls 20 dp | 380 dp card, no scroll range |
| Two chats (one neighbour), the ceiling | 424 dp | 460 dp |
| Folded, two neighbours: room they take | 112 dp | 56 dp |

**Rests on code reading, not a test:** TalkBack's speech for the new label (the semantics are asserted, not the speech), the tab
over a bright wallpaper on a device panel (the screenshots show it on the fixture's backdrop), and the tabs at large font
scales (the line is clipped to the tab, not measured at it).

### Tests

`OpenCardFitUnfoldedTest` and `OpenCardFitFoldedTest` (the fit rule above), `OpenCardMotionTest` (the frame from the rest rectangle, the room below, the minimum height, one-way edges, both placements),
`QuickChipFitTest` (the fit, the scroll fallback, the vanishing edge chip), `OpenCardHandoffCueTest` (the cue's fill, its
direction, no cue toward nothing, lifting ends it, the bodies are never both drawn, the header scale),
`OpenCardPolishUnfoldedTest` and `OpenCardPolishFoldedTest` (every frame, above), `OpenCardPolishUnfoldedShots` and
`OpenCardPolishFoldedShots` (a long card with neighbours above and below, light and dark, and the cue at the top and at the
bottom of the body mid-drag; unfolded also the long front card whose strips fill the Dock's height).

- **Tests rewritten on purpose:** the fit tests' ceilings and the folded test's strips above the card, for the slim tabs (listed with
  the slim-strips change: 64 dp became 28 dp, 56 dp became 28 dp). The rest of the open card's tests pass unchanged.
- **Not confirmed without a device:** the feel of the spring against a finger, the cue's legibility over real text on a real
  panel, the strip's slide under a real pager, TalkBack's reading of the strips below the card (the semantics are asserted, the
  speech is not), and the owner's own column height (the fixture's Dock body is 489 dp).
