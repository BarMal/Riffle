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
- `DockShelfGesture.kt` migrates to `AnchoredDraggable`; `DockSwipeUpGesture.kt` migrates to
  `detectVerticalDragGestures`.
- `HomeGestureInput.kt`'s 2-finger pinch path is evaluated against `detectTransformGestures`;
  its N-finger swipe path is expected to remain custom absent a Foundation equivalent, and
  that determination is recorded when the follow-up work lands rather than assumed here.
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

## References

- PRs #1113, #1114, #1115, #1117 (the four sequential `CardStack` fling fixes this ADR
  responds to).
- `BarMal/Calm` (`app/src/main/java/dev/barna/calm/CardStackController.kt`,
  `CalmLauncherRunner.kt`, `ViewPager2Tools.kt`, `ChapterPagerAdapter.kt`) -- the reference
  implementation this decision follows.
