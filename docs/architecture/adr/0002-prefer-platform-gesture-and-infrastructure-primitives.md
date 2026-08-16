# ADR 0002: Prefer platform/library primitives over hand-rolled gesture and infrastructure code

- Status: Accepted
- Date: 2026-08-15
- Supersedes: N/A
- Superseded by: N/A

## Context

Card-stack fling/scroll (`CardStack.kt` + `CardStackController.kt`) shipped as a hand-rolled
Compose `pointerInput` implementation: a raw `awaitFirstDown`/`awaitPointerEvent` loop, a
manually-fed `androidx.compose.ui.input.pointer.util.VelocityTracker`, a hand-written
touch-slop-based axis-latch heuristic, and a synchronous release-time formula deciding
whether a gesture committed a focus change. Over five PRs (#1113-#1117) this needed four
separate bugfixes: a diagonal-drift axis-latch bug, an uncapped fling distance, a
drag-or-velocity-alone threshold that missed gestures where neither signal alone crossed its
threshold but their sum should have, and a `VelocityTracker` that silently dropped its first
sample (starving velocity estimation specifically for quick, short flicks). Each fix was
correct in isolation but the pattern -- reimplementing scroll/fling physics from primitives
instead of reusing a mature implementation -- kept producing a new edge case.

Riffle's own predecessor prototype, "Calm" (a plain-View launcher, `BarMal/Calm`), solves the
identical card-stack problem with zero custom velocity/fling code: its card stack is a
platform `android.widget.ScrollView` wrapping a `LinearLayout` of cards. `ScrollView`'s drag
detection, `VelocityTracker`, `OverScroller`, and momentum physics are implemented and
maintained by the Android framework itself. Calm's own code only listens for scroll-position
changes to restyle each card's depth-based visual properties, and separately runs a
*debounced, post-settle* correction (`magnetize()`, on a `postDelayed` timer) that snaps to
the nearest card -- decoupled from the fling gesture itself, not a synchronous release-time
classification. Calm's top-level page-switching similarly reuses
`androidx.viewpager2.widget.ViewPager2` rather than a hand-rolled pager.

A follow-up audit (three parallel research passes: hand-rolled gesture code across Riffle,
a Riffle-vs-Calm cross-reference, and non-gesture infrastructure) found the same reinvention
pattern recurring elsewhere:

- `ImmediateHomePager.kt` and `AdaptiveStageStagePager.kt` are the *only* other two files in
  the app that touch `VelocityTracker`, and both carry the identical shape that took four
  fixes to harden in `CardStack.kt`: a hand-rolled pointer loop, a manual axis-intent
  threshold, and a manual distance-or-velocity settle formula.
- `DockShelfGesture.kt` (a bistable expand/collapse) and `DockSwipeUpGesture.kt` (a one-shot
  threshold trigger) hand-roll simpler gestures that Compose Foundation's
  `AnchoredDraggable`/`detectVerticalDragGestures` already cover, though without the
  velocity-tracking bug surface.
- `HomeGestureInput.kt`'s N-finger pinch/swipe interpreter has no exact 1:1 Foundation
  replacement; its 2-finger pinch case maps to `detectTransformGestures`, but 3-finger swipe
  support has no off-the-shelf equivalent.
- Outside gestures: three independent hand-rolled LRU caches
  (`PackageManagerAppIconLoader.kt`, `AndroidWidgetPreviewImageLoader.kt`,
  `AdaptiveStageCardSurface.kt`) duplicate `android.util.LruCache`; two independent WCAG
  contrast-ratio implementations (`LauncherTheme.kt`, `AdaptiveStageCardSurface.kt`) duplicate
  `androidx.core.graphics.ColorUtils.calculateContrast`; and a hand-rolled hue-bucket
  dominant-color extractor (`AppIconDominantColor.kt`) plus a cruder single-pixel sampler
  (`AdaptiveStageCardSurface.kt`) duplicate `androidx.palette.graphics.Palette`.

## Decision

Prefer a mature platform widget, AndroidX library, or Compose Foundation API over hand-rolled
implementations of gesture recognition, scroll/fling physics, bounded caching, and
perceptual/color math, whenever one exists that fits the product requirement. Specifically:

- `ImmediateHomePager.kt` and `AdaptiveStageStagePager.kt` migrate to Compose Foundation's
  `HorizontalPager`/`PagerState`, mirroring Calm's `ViewPager2` usage.
- `CardStack.kt`'s own gesture handling migrates to `Modifier.scrollable` +
  `FlingBehavior` for the underlying scroll/fling physics, while keeping its existing custom
  fan/depth rendering driven by the resulting continuous scroll offset -- the same split Calm
  uses: the platform/library owns "how far did it scroll and by what physics," Riffle's own
  code keeps owning "how does depth N render at scroll position X."
- `DockSwipeUpGesture.kt` migrates to `detectVerticalDragGestures`.
- `DockShelfGesture.kt` is evaluated against `AnchoredDraggable` and `detectVerticalDragGestures`;
  the evaluation's outcome (migrate or keep custom, and why) is recorded when that work lands
  rather than assumed here, mirroring how `HomeGestureInput.kt`'s N-finger case is handled below.
- `HomeGestureInput.kt`'s 2-finger pinch path was evaluated against `detectTransformGestures`
  and kept custom (see Consequences); its N-finger swipe path remains custom absent a
  Foundation equivalent, as expected.
- The three hand-rolled LRU caches consolidate onto `android.util.LruCache`.
- The two hand-rolled contrast-ratio implementations consolidate onto
  `androidx.core.graphics.ColorUtils.calculateContrast`.
- The hand-rolled dominant-color extractors migrate to `androidx.palette.graphics.Palette`.
  Unlike the other items in this decision, Palette's swatch selection differs enough from the
  current fixed hue-bucket approach that visible output will shift; this migration is called
  out explicitly in its own PR rather than folded silently into a mechanical refactor.

Each migration lands as its own pull request rather than one large change, so a regression in
one item does not block or entangle the others, and each can be reverted independently.

## Consequences

- Scroll/fling/paging code gets platform-calibrated physics (friction, deceleration,
  velocity estimation) for free instead of hand-tuned constants, removing an entire class of
  edge-case bugs (diagonal drift, uncapped distance, combined-signal thresholds, dropped
  velocity samples) rather than patching them one at a time as they're discovered.
- `PagerState`/`AnchoredDraggable`/`FlingBehavior` have their own API shapes and lifecycle
  (e.g. `currentPage` vs `settledPage` vs `targetPage`, `animateScrollToPage`) that differ
  from the current hand-rolled state objects' public surface
  (`AdaptiveStageStagePagerState.pagePosition`/`visualSelectedStageIndex`/
  `isStageGestureActive`, `onDragStarted`/`onTargetStageSettling`/`onDragStopped`). Each
  migration wraps the Foundation API behind the existing call sites' expectations rather than
  rewriting every caller, to keep blast radius contained.
- `Palette`-based dominant color is a visible product change (accent colors extracted from
  app icons/artwork will shift), not a pure refactor -- this is flagged in its own PR
  description rather than bundled with the mechanical cache/contrast consolidations.
- `HomeGestureInput.kt`'s N-finger swipe path may remain permanently custom; this ADR does not
  mandate migrating it, only evaluating the 2-finger pinch portion.
- `DockShelfGesture.kt` was evaluated against both `AnchoredDraggable` and
  `detectVerticalDragGestures` and kept hand-rolled; neither fits without changing behavior.
  `AnchoredDraggable` models a continuous drag that follows the finger and settles to
  an anchor on release/fling; this gesture has no visual offset-following at all -- it commits
  synchronously mid-drag, still pressed, the instant a threshold is crossed, closer to
  `DockSwipeUpGesture.kt`'s shape than to a bottom-sheet-style drag. `detectVerticalDragGestures`
  was traced against Compose Foundation's actual source
  (`DragGestureDetector.kt`): its `verticalDrag` loop calls `change.consume()`
  unconditionally after every `onVerticalDrag` invocation, so it cannot preserve this gesture's
  direction-selective claim (only consume when the drag is both past threshold *and* in the
  direction that state can move) -- adopting it verbatim would make the dock swallow
  wrong-direction drags that today fall through to `homeGestureInput`. Dropping to the lower-level
  `awaitVerticalTouchSlopOrCancellation` for selective consumption doesn't work either: per its
  own documented contract, an unconsumed touch-slop check resets and re-arms from a new
  reference point rather than continuing to track cumulative distance from the original touch
  down, so a claim that's initially rejected (wrong direction, or under the eager-claim
  threshold) permanently loses the slop-sized distance already travelled -- silently raising the
  effective 80px toggle threshold above the constant callers can see, and breaking the pixel-exact
  choreography `DockShelfGestureInteractionTest.kt` already asserts. `DockSwipeUpGesture.kt`
  avoided both problems because it has a single threshold, a single fixed direction, and no
  competing early-claim step to preserve alongside it.
- `HomeGestureInput.kt`'s 2-finger pinch path was evaluated against `detectTransformGestures`
  and kept custom, for a related but distinct reason from `DockShelfGesture.kt`.
  `HomeGestureInput.kt` is deliberately *one* recognizer covering 1/2/3-finger swipes in four
  directions plus 2-finger pinch, with pinch classified ahead of a 2-finger swipe whenever both
  conditions could apply (`HomeSwipeGestureInterpreterTest.interpretsPinchesBeforeTwoFingerSwipes`
  asserts exactly this priority). `detectTransformGestures` is a self-contained, single-purpose
  recognizer with its own `awaitEachGesture` loop that unconditionally consumes every position
  change once its own touch slop is crossed (read from Foundation's actual source,
  `TransformGestureDetector.kt`); running it *alongside* the N-finger swipe loop as a second,
  independent `pointerInput` block would mean two recognizers racing for the same 2-finger touch
  with no shared arbitration, reintroducing exactly the kind of race this ADR's Dock-gesture
  items depend on Main-pass consumption ordering to avoid -- except here there's no ordering that
  resolves it, since both recognizers would be watching the same pass. Its lower-level centroid
  utilities (`PointerEvent.calculateCentroid`, `calculateCentroidSize`) were also considered as
  drop-in replacements for `HomeGestureStart`'s hand-rolled centroid/distance math, but Foundation's
  versions only include pointers that were *also* pressed in the previous frame, while
  `HomeGestureStart` deliberately resets to a fresh baseline including a just-added finger on the
  same frame the pointer count changes -- swapping the math alone would shift centroid/scale
  results on every finger-count transition, not just simplify the implementation.
- Future gesture or infrastructure code in this app should default to a platform/library
  primitive first, and justify a custom implementation explicitly (in code comments or a new
  ADR) when no suitable primitive exists, rather than defaulting to hand-rolled code and
  discovering the gap through production bug reports.
- The `app` module's JVM unit tests (`testDebugUnitTest`) run against AGP's "mockable
  android.jar", which stubs every `android.*` method to throw unless Robolectric is present.
  The `LruCache` consolidation (#1119) is the first migration in this app module to construct
  an `android.*` framework class from those tests, and needed Robolectric added as a test
  dependency for exactly that reason. The `Palette` migration will hit the same constraint,
  since `Palette` operates on `android.graphics.Bitmap`.

## Alternatives Considered

- **Keep patching the hand-rolled implementations as bugs surface.** Rejected: this is the
  status quo that produced four sequential fixes to the same file, with the fourth fix
  reported by a user as still not fully solid before the actual root cause (a fundamentally
  fragile approach, not a specific formula error) was identified.
- **Migrate everything in one large PR.** Rejected: the migrations are independent in scope
  and risk (a mechanical `LruCache` swap carries essentially no behavioral risk; a `Pager`
  migration changes gesture lifecycle semantics that need their own focused review and CI
  run). Bundling them would make a regression in one item block or obscure the others.
- **Leave `HomeGestureInput.kt`'s N-finger swipe support unmigrated without ever revisiting
  it.** Rejected in favor of an explicit, scoped evaluation of just the 2-finger pinch case,
  so the "no Foundation equivalent" conclusion for N-finger swipe is a documented finding
  rather than an assumption.
- **Force `DockShelfGesture.kt` onto `AnchoredDraggable` or `detectVerticalDragGestures`
  regardless of fit.** Rejected after tracing both APIs' actual source and contract (see
  Consequences): both would either change what gets consumed (breaking ancestor-gesture
  preemption) or silently shift the effective toggle threshold (breaking the existing
  pixel-exact regression tests). A migration that trades a small, correct, tested
  implementation for a platform API that can't preserve its contract is not the improvement
  this ADR is arguing for.
- **Run `detectTransformGestures` as a second `pointerInput` block alongside
  `HomeGestureInput.kt`'s existing N-finger swipe loop, for the 2-finger pinch case only.**
  Rejected: `HomeSwipeGestureInterpreterTest.interpretsPinchesBeforeTwoFingerSwipes` requires
  pinch and 2-finger swipe to be mutually exclusive outcomes of a *single* decision, and two
  independently-consuming recognizers watching the same touch have no shared way to guarantee
  that -- splitting the recognizer would risk both firing, or firing inconsistently depending on
  event-processing order, for the exact case the existing test locks down.

## References

- PRs #1113, #1114, #1115, #1117 (the four sequential `CardStack` fling fixes this ADR
  responds to).
- `BarMal/Calm` (`app/src/main/java/dev/barna/calm/CardStackController.kt`,
  `CalmLauncherRunner.kt`, `ViewPager2Tools.kt`, `ChapterPagerAdapter.kt`) -- the reference
  implementation this decision follows.
