# Dock modes: build plan

Builds ADR 0003 Amendments 1 to 3 on a folded phone. Each slice is one pull request that leaves the app
working. The unfolded layout (Amendment 2) follows once folded is on a device.

The reference for behaviour and feel is the set of working prototypes on the UX canvas (boards M1 to M3).
Their spring, magnet and timing constants are the starting values; the settings in slice 6 expose them.

| Slice | What it delivers | Depends on |
| --- | --- | --- |
| 1. Dock modes | The Dock's Home and contextual modes: contextual tiles appended after the live apps, the row scrolls in the contextual mode, the open icon is ringed. Press-down-and-release opens and closes. The Stream spine, page header and Stream button go. | none |
| 2. Trampoline and fan | The dip under the finger, the bounce on release, cards fanning out in sequence, Home fading, and closing as the opening reversed. Reduced-motion fallback. **Built**, see "As built". | 1 |
| 3. Hold to choose | Press and hold or slide while pressed: early extend, selection ring, name label, edge scrolling, haptic tick per icon. Starting a press on an icon opens that icon's stack. **Built**, see "As built". | 1, 2 |
| 4. Stacks | The bottom-anchored card stack with clipped peeking cards, drag, fling and magnet (**4a, built**, see "As built"); swiping left and right between stacks in Dock order (built in slice 1); shortcuts stacks for apps with nothing live and the Today page (agenda and tasks) (**4b**, not yet). | 1 |
| 5. Cards | App-tinted cards, images in browse and open cards, a card growing in place when opened, and its actions in the Dock. | 4 |
| 6. Settings | Stream settings (open card position, actions position, tint, background, pinned apps), motion settings, the accessibility action and the first-run hint. | 2 to 5 |
| 7. Home and Library | The combined search-and-page handle with quick-drag, search opening from the Dock, the paged Library, Home mode (Placed items or App library) and the feature switches. | none |

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
