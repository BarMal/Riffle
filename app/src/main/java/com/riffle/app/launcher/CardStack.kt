@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.cards.CardStackAnimationEasing
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackAnimationSpec
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import com.riffle.core.domain.launcher.cards.CardStackMagnet
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Which screen axis is this stack's own drag-to-navigate/fan axis. [VERTICAL] (the default,
 * matching every existing call site) keeps today's exact behavior: [CardStackLayoutEntry.offset]
 * maps to horizontal translation, [CardStackLayoutEntry.verticalOffset] maps to vertical
 * translation and drives settle, and a vertical drag is this stack's own gesture (horizontal
 * drags pass through unconsumed for an ancestor). [HORIZONTAL] rotates all of that 90 degrees --
 * used for a rail docked to a TOP/BOTTOM edge -- with no change to the layout math itself
 * (CardStackLayoutPolicy stays orientation-agnostic; only which screen axis each of its two
 * offset fields renders along, and which drag axis settles, changes here).
 */
internal enum class CardStackOrientation { VERTICAL, HORIZONTAL }

/**
 * Turns this stack's own drag/settle axis into a *continuously scrolling* position rather than a
 * drag that ends by picking a target card and animating to it.
 *
 * This is the reference "Calm" launcher's model. There, a real `android.widget.ScrollView` owns
 * the scroll position outright: the platform's `OverScroller` carries it through release with real
 * momentum, `style()` recomputes every card's pose from whatever that position currently is on
 * every frame (drag and fling alike), and `magnetize()` is a debounced correction that only nudges
 * an already-decelerated position onto the nearest card afterwards. There is never a moment where
 * one motion stops and a different one starts.
 *
 * Riffle's stack had no such position. A drag tracked the finger, and release *synchronously*
 * decided a target card and animated to it from a standing start -- the release velocity was
 * reported to [CardStackInteraction.onSettle] for the commit decision but never carried into the
 * motion itself, so however hard the finger was moving, the stack always stopped dead at release
 * and restarted. That discontinuity is the "catch" this replaces, and it predates the gesture
 * migration in #1127/#1128 (it goes back to the original snap-during-drag rendering in #1097).
 *
 * With this supplied, [CardStack] instead keeps a signed pixel scroll position that the finger
 * drives during the drag and Android's own spline fling curve (the same physics `OverScroller`
 * uses -- see `rememberSplineBasedDecay`) carries onward from the real release velocity. Only once
 * that decay has come to rest does the position magnetize onto the nearest card, and only then is
 * [CardStackInteraction.onSettle] called -- with the *magnetized* distance and zero velocity, an
 * exact multiple of [distancePerCardPx], so the existing
 * [com.riffle.core.domain.launcher.cards.CardStackController.settle] commit resolves to precisely
 * the card the scroll came to rest on. Settle is a post-hoc commit of where the scroll already
 * ended up, not a decision that starts a fresh animation.
 *
 * Leaving this `null` keeps the prior behavior: release reports the raw drag and velocity
 * immediately and the stack animates to whatever card the caller commits.
 *
 * @param cardCount total cards in the stack -- the scroll clamps at the first and last, so a fling
 *   cannot run on into empty space past either end.
 * @param activeCardIndex the durable focused index the scroll position is measured *from*; a
 *   position of zero renders exactly this card focused.
 * @param distancePerCardPx how much travel along this stack's own axis advances the scroll by one
 *   card. Pass the same value the caller gives
 *   [com.riffle.core.domain.launcher.cards.CardStackSettleRequest.distanceThresholdPx], so the
 *   magnetized distance reported to `onSettle` lands on exact card boundaries.
 * @param magnet how long the position is left standing where the decay stopped, and how firmly it
 *   is then pulled onto the nearest card. See [CardStackMagnet].
 */
internal data class CardStackScroll(
    val cardCount: Int,
    val activeCardIndex: Int,
    val distancePerCardPx: Float = DEFAULT_CARD_STACK_SCROLL_DISTANCE_PER_CARD_PX,
    val magnet: CardStackMagnet = CardStackMagnet(),
) {
    init {
        require(cardCount >= 0) { "Card count must not be negative." }
        require(distancePerCardPx > 0f) { "Scroll distance per card must be positive." }
    }

    /** [activeCardIndex] clamped into the stack, so a stale index cannot skew the scroll bounds. */
    val anchorIndex: Int
        get() = if (cardCount <= 0) 0 else activeCardIndex.coerceIn(0, cardCount - 1)
}

/**
 * The signed scroll positions that keep the rendered card index inside the stack. Negative scroll
 * moves forward (dragging up/left), matching [CardStackInteraction.onLiveDrag]'s sign convention,
 * so the reachable range runs from the last card to the first.
 */
internal fun cardStackScrollPxRange(scroll: CardStackScroll): ClosedFloatingPointRange<Float> {
    if (scroll.cardCount <= 1) return 0f..0f
    val anchor = scroll.anchorIndex
    val atFirstCardPx = anchor.toFloat() * scroll.distancePerCardPx
    val atLastCardPx = (anchor - (scroll.cardCount - 1)).toFloat() * scroll.distancePerCardPx
    return atLastCardPx..atFirstCardPx
}

/** Which card a scroll position of [scrollPx] is nearest to -- what magnetizing commits to. */
internal fun cardStackSettledCardIndex(
    scrollPx: Float,
    scroll: CardStackScroll,
): Int {
    if (scroll.cardCount <= 0) return 0
    return (scroll.anchorIndex - scrollPx / scroll.distancePerCardPx)
        .roundToInt()
        .coerceIn(0, scroll.cardCount - 1)
}

/**
 * [scrollPx] snapped onto its nearest card boundary -- an exact multiple of
 * [CardStackScroll.distancePerCardPx] away from the anchor, which is what lets the resulting
 * `onSettle` report resolve to that exact card rather than to a rounded-off approximation of it.
 */
internal fun cardStackMagnetizedScrollPx(
    scrollPx: Float,
    scroll: CardStackScroll,
): Float =
    (scroll.anchorIndex - cardStackSettledCardIndex(scrollPx, scroll)).toFloat() *
        scroll.distancePerCardPx

/**
 * The fractional card index a live scroll position renders at, for a caller that recomputes its
 * `entries` from [CardStackInteraction.onLiveDrag] (see that callback's own doc). Bounded by the
 * stack itself, matching both [cardStackScrollPxRange] and the clamp
 * [com.riffle.core.domain.launcher.cards.CardStackController.navigate] applies when committing.
 */
internal fun cardStackLiveActiveCardIndex(
    activeCardIndex: Int,
    cardCount: Int,
    liveDragPx: Float?,
    distancePerCardPx: Float = DEFAULT_CARD_STACK_SCROLL_DISTANCE_PER_CARD_PX,
): Float =
    liveDragPx?.let { dragPx ->
        (activeCardIndex - dragPx / distancePerCardPx).coerceIn(0f, (cardCount - 1).coerceAtLeast(0).toFloat())
    } ?: activeCardIndex.toFloat()

/** Callbacks supplied by a surface that owns durable card focus. */
internal data class CardStackInteraction(
    val focusedItemKey: Any?,
    val onFocusRequest: (CardStackLayoutEntry) -> Unit,
    /** Named for the [CardStackOrientation.VERTICAL] default; carries horizontal drag/velocity
     *  instead when [CardStackOrientation.HORIZONTAL] -- treat both params as "this stack's own
     *  primary-axis drag," not literally vertical. */
    val onSettle: (verticalDragPx: Float, verticalVelocityPxPerSecond: Float) -> Unit,
    /**
     * One haptic tick for the stack arriving on a card. Without [scroll] this fires once, at
     * release, as it always did. With [scroll] supplied it instead fires *every time the scroll
     * position crosses onto a different card* -- during the finger's own drag, through the momentum
     * fling, and on the magnetize that ends it -- so a fling that carries the stack past four cards
     * ticks four times rather than once at the end. That is how the reference "Calm" launcher's own
     * stack behaves (its `style()` compares a freshly derived `activeIndex` against a remembered
     * `lastHapticIndex` on every scroll callback, and its magnetize is just another scroll
     * callback), and it is what makes a long fling feel like it is travelling over cards instead of
     * teleporting to one. The final arrival is itself one of those crossings, so it is not ticked a
     * second time at commit.
     */
    val onSettleHaptic: () -> Unit = {},
    /** Alternate-input navigation commits one focused-card change without emulating a drag. */
    val onNavigate: ((CardStackNavigationDirection) -> Boolean)? = null,
    /** Opens the focused card's detail surface for keyboard, D-pad, rotary and switch users. */
    val onExpand: (() -> Unit)? = null,
    /** Increments only when a gesture settles to a new focused card. */
    val settleTransitionId: Int = 0,
    /**
     * How many cards the settle that produced the current [settleTransitionId] moved by. A
     * caller that lets a hard drag or fling skip several cards at once (see
     * [com.riffle.core.domain.launcher.cards.CardStackController.settle]'s doc) sets this to that
     * same step count so the settle animation itself travels for proportionally longer instead of
     * always taking the same brief, fixed duration however far it's skipping -- without this, a
     * multi-card skip committed near-instantly, reading as a pop rather than travel. Defaults to 1
     * (today's only prior behavior) so every existing caller that doesn't know about this field is
     * unaffected.
     */
    val settleStepCount: Int = 1,
    /** Requester owned by the currently focused rendered entry. */
    val keyboardFocusRequester: FocusRequester? = null,
    /**
     * Called continuously with this stack's live signed scroll position along its own drag/settle
     * axis, measured from wherever the position sat when the gesture began; called with `null`
     * once the stack is at rest on a card again. A caller that wants the stack to visibly track
     * the motion -- not just its outcome -- uses this to recompute a *fractional* activeIndex
     * every frame (see [cardStackLiveActiveCardIndex] and
     * [com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy]'s Float `entries()` overload),
     * the same way the reference "Calm" launcher's own card stack continuously recomputes every
     * card's pose from live scroll position rather than a discrete index -- not by uniformly
     * shifting the whole rendered stack, which is what an earlier, simpler version of this
     * mechanism did.
     *
     * With [scroll] supplied this keeps reporting through the *whole* motion -- finger, momentum
     * fling and magnetize alike -- so the caller's own recomputed pose is what carries the stack
     * through release. Without it, reporting stops at release, as it always did.
     */
    val onLiveDrag: ((dragPx: Float?) -> Unit)? = null,
    /**
     * Opts this stack into a continuously-scrolling position carried through release by real
     * momentum, instead of stopping dead at release and animating to a freshly-decided target.
     * See [CardStackScroll]. Requires [onLiveDrag] to be wired up as well -- that callback is how
     * the moving position reaches the caller's `entries`; without it the fling would advance a
     * position nothing renders from.
     */
    val scroll: CardStackScroll? = null,
)

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
internal fun CardStack(
    entries: List<CardStackLayoutEntry>,
    modifier: Modifier = Modifier,
    animationProfile: CardStackAnimationProfile = CardStackAnimationProfile.STACK_REFLOW,
    animationSpec: CardStackAnimationSpec = animationProfile.spec,
    reducedMotion: Boolean = false,
    itemKey: (CardStackLayoutEntry) -> Any = { entry -> entry.cardIndex },
    interaction: CardStackInteraction? = null,
    /** Multiplies every rendered entry's alpha, e.g. to dim the whole stack behind a detail overlay. */
    dimFactor: Float = 1f,
    orientation: CardStackOrientation = CardStackOrientation.VERTICAL,
    /**
     * Where along this stack's own settle axis the focused card's centre sits, as a fraction of the
     * stack's own area: 0.5 (the default) centres it, which is this composable's only prior
     * behavior. The reference "Calm" launcher calls this `stackPeakPosition` and defaults it to
     * 0.2 -- its focused card sits high in the viewport, leaving the room below it for the cards
     * still to come rather than splitting that room evenly with the ones already gone. Its own
     * `CardStackLayout.activeTopPadding` places the focused card's centre at exactly this fraction
     * of the viewport, which is what [androidx.compose.ui.BiasAlignment] expresses directly.
     */
    stackPeakFraction: Float = CENTERED_CARD_STACK_PEAK_FRACTION,
    content: @Composable (CardStackLayoutEntry, Modifier) -> Unit,
) {
    val motionMode = cardStackMotionMode(reducedMotion)
    val focusRequesters = remember { mutableMapOf<Any, FocusRequester>() }
    // Whether this stack's own scroll position is currently moving -- written live, every frame, by
    // the Modifier.scrollable attached below regardless of which entry the gesture visually started
    // over. With CardStackInteraction.scroll supplied this stays true for the whole continuous
    // motion (finger, momentum fling and magnetize), not just while a finger is down. Used only to
    // suspend every entry's animateFloatAsState (see AnimatedCardStackEntry), so a caller that
    // recomputes `entries` every frame from CardStackInteraction.onLiveDrag's report (a fractional
    // activeIndex -- see that callback's own doc) renders each new pose immediately instead of
    // chasing a constantly-moving animation target. Purely a rendering concern -- distinct from
    // onLiveDrag itself, which is what actually lets a caller recompute those entries in the first
    // place.
    val isScrolling = remember { mutableStateOf(false) }
    var restoreKeyboardFocus by remember { mutableStateOf(false) }
    var keyboardFocusOriginKey by remember { mutableStateOf<Any?>(null) }
    var consumedSettleTransitionId by remember { mutableStateOf(interaction?.settleTransitionId ?: 0) }
    val timing =
        if (
            interaction?.settleTransitionId != null &&
            interaction.settleTransitionId != consumedSettleTransitionId
        ) {
            CardStackAnimationTiming.SETTLE
        } else {
            CardStackAnimationTiming.REFLOW
        }
    LaunchedEffect(interaction?.settleTransitionId) {
        consumedSettleTransitionId = interaction?.settleTransitionId ?: 0
    }
    LaunchedEffect(interaction?.focusedItemKey, restoreKeyboardFocus) {
        val focusedItemKey = interaction?.focusedItemKey
        if (
            !restoreKeyboardFocus ||
            focusedItemKey == null ||
            focusedItemKey == keyboardFocusOriginKey
        ) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        focusRequesters[focusedItemKey]?.requestFocus()
        restoreKeyboardFocus = false
        keyboardFocusOriginKey = null
    }

    fun navigateFromKeyboard(direction: CardStackNavigationDirection): Boolean {
        val focusedItemKey = interaction?.focusedItemKey
        val moved = interaction?.onNavigate?.invoke(direction) ?: false
        if (moved) {
            // Keep the request pending until the owner publishes a different durable
            // focused key. This also supports owners that commit navigation asynchronously.
            keyboardFocusOriginKey = focusedItemKey
            restoreKeyboardFocus = true
        }
        return moved
    }

    val currentInteraction by rememberUpdatedState(interaction)
    val touchSlop = LocalViewConfiguration.current.touchSlop
    // This stack's live signed scroll position along its own axis, measured from the card that was
    // focused when the motion began. The finger drives it during the drag; with
    // CardStackInteraction.scroll supplied, real fling physics keep driving it after release (see
    // runCardStackScrollFling) until it magnetizes onto a card and the resulting position is
    // committed through onSettle. Without that, it is just the raw accumulated drag, reported to
    // onSettle at release exactly as before.
    val scrollPx = remember { mutableFloatStateOf(0f) }
    // Which card the position last ticked a haptic for -- see CardStackInteraction.onSettleHaptic.
    // Seeded from the anchor at the start of every motion, then advanced by every crossing the
    // finger, the fling and the magnetize make, so one continuous motion never re-ticks a card it
    // is already resting on. Mirrors the reference "Calm" launcher's own `lastHapticIndex`.
    val lastHapticIndex = remember { mutableIntStateOf(0) }
    val scrollableState =
        rememberScrollableState { delta ->
            var next = scrollPx.floatValue
            if (!isScrolling.value) {
                // Modifier.scrollable consumes touchSlop's worth of movement internally while
                // deciding this is a drag -- its own gesture utilities call this the "overSlop"
                // delta -- and never reports that portion through this callback. The old
                // hand-rolled cardStackPointerInput accumulated every pointer delta starting from
                // the very first touch-down, with no such exclusion, and CardStackController.
                // settle's distance/velocity thresholds were tuned against that full-distance
                // convention. Restoring the slop here keeps a fling/drag committing at the same
                // physical finger travel it always did, instead of silently requiring extra
                // travel -- past the already-crossed slop -- to reach those same thresholds.
                next += if (delta >= 0f) touchSlop else -touchSlop
                // A fresh motion starts resting on the anchor card, so that is the card the first
                // crossing is measured against. A finger landing mid-fling does not come through
                // here (isScrolling stays true across that hand-off), which is exactly right: that
                // motion is continuous, and its crossing history should carry straight on.
                currentInteraction?.scroll?.let { scroll -> lastHapticIndex.intValue = scroll.anchorIndex }
            }
            isScrolling.value = true
            // A finger landing mid-fling interrupts that fling's coroutine (Modifier.scrollable
            // cancels it to start this drag) and simply carries on from the position it had
            // reached -- the same catch-the-moving-content behavior a real ScrollView has -- since
            // nothing resets the position between the two.
            next += delta
            // Held to the first and last card, so the position a release starts flinging from is
            // already a reachable one. Rendering has always clamped the *index* it derives from
            // this (see cardStackLiveActiveCardIndex), so a drag past either end looked stopped
            // either way; clamping the position itself is what stops the fling that follows from
            // having to travel back through the slack first.
            val scroll = currentInteraction?.scroll
            publishCardStackScrollPosition(
                position = scroll?.let { next.coerceIn(cardStackScrollPxRange(it)) } ?: next,
                scroll = scroll,
                scrollPx = scrollPx,
                lastHapticIndex = lastHapticIndex,
                interaction = currentInteraction,
            )
            delta
        }
    // The same spline curve android.widget.OverScroller (and so every platform ScrollView) flings
    // with, which is the physics the reference "Calm" launcher's own card scroll inherits for free
    // by being a real ScrollView. See CardStackScroll.
    val flingDecay = rememberSplineBasedDecay<Float>()
    // Modifier.scrollable's own drag/velocity-tracking/touch-slop-axis-lock physics replace
    // cardStackPointerInput's hand-rolled awaitPointerEvent loop and VelocityTracker. This
    // FlingBehavior never scrolls the scrollable itself -- the pose the user sees comes from
    // entries(), which the caller recomputes from onLiveDrag -- so it always fully consumes the
    // release velocity and reports 0 remaining, driving this stack's own scroll position instead.
    val flingBehavior =
        remember(flingDecay) {
            object : FlingBehavior {
                override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                    val scroll = currentInteraction?.scroll
                    if (scroll == null) {
                        val dragPx = scrollPx.floatValue
                        scrollPx.floatValue = 0f
                        isScrolling.value = false
                        currentInteraction?.onLiveDrag?.invoke(null)
                        currentInteraction?.run {
                            onSettle(dragPx, initialVelocity)
                            onSettleHaptic()
                        }
                        return 0f
                    }
                    runCardStackScrollFling(
                        initialVelocity = initialVelocity,
                        scroll = scroll,
                        scrollPx = scrollPx,
                        lastHapticIndex = lastHapticIndex,
                        decaySpec = flingDecay,
                        interaction = { currentInteraction },
                    )
                    isScrolling.value = false
                    return 0f
                }
            }
        }

    Box(
        modifier =
            modifier
                // Fanned/rotated background entries can translate beyond the focused card's own
                // footprint by design (see AdaptiveStageAppearanceSettings' fan-stage-margin
                // doc) -- without this, that overflow bled into whatever sibling UI sits above
                // or below the stack (nav controls, contextual actions, the dock) instead of
                // staying contained to the stack's own allotted area.
                .clipToBounds()
                .scrollable(
                    state = scrollableState,
                    orientation =
                        if (orientation == CardStackOrientation.HORIZONTAL) {
                            Orientation.Horizontal
                        } else {
                            Orientation.Vertical
                        },
                    enabled = interaction != null,
                    flingBehavior = flingBehavior,
                )
                .semantics {
                    isTraversalGroup = true
                    this[CardStackAnimationProfileKey] = animationProfile
                    this[CardStackMotionModeKey] = motionMode
                    this[CardStackAnimationSpecKey] = animationSpec
                },
        // Each entry wraps to its own card's size, not this root's full area -- without this,
        // Box's default TopStart pins every card to the top-left corner instead of centering it.
        // The settle axis additionally carries stackPeakFraction, which slides the whole stack
        // along that axis; the other axis stays centred.
        contentAlignment = cardStackPeakAlignment(stackPeakFraction, orientation),
    ) {
        entries.forEach { entry ->
            // A card index identifies a stable card while focus changes, so Compose can
            // interpolate that card's prior pose into its new pose without composing a
            // second, outgoing stack.
            val stableItemKey = itemKey(entry)
            val focusRequester =
                focusRequesters.getOrPut(stableItemKey) { FocusRequester() }
            key(stableItemKey) {
                AnimatedCardStackEntry(
                    entry = entry,
                    stableItemKey = stableItemKey,
                    animationSpec = animationSpec,
                    motionMode = motionMode,
                    timing = timing,
                    dimFactor = dimFactor,
                    orientation = orientation,
                    isFocused = interaction?.let { stableItemKey == it.focusedItemKey } ?: true,
                    isScrolling = isScrolling,
                    interaction =
                        interaction?.copy(
                            onNavigate = ::navigateFromKeyboard,
                            keyboardFocusRequester = focusRequester,
                        ),
                    content = { entry, modifier ->
                        content(
                            entry,
                            modifier.cardStackTapToFocus(
                                entry = entry,
                                isFocused = stableItemKey == interaction?.focusedItemKey,
                                interaction = interaction,
                            ),
                        )
                    },
                )
            }
        }
    }
}

/**
 * Carries this stack's scroll position onward from [initialVelocity] with real momentum, then
 * magnetizes it onto the nearest card and commits that card -- the continuous-scroll half of
 * [CardStackScroll].
 *
 * The three phases are deliberately sequential rather than a single "animate to the target card"
 * step, because that is what makes the motion continuous:
 *  1. **Decay.** Android's own spline fling curve carries the position on from the velocity the
 *     finger actually released at. Nothing decides a destination here; the physics do, exactly as
 *     `OverScroller` does for a ScrollView. Hitting either end of the stack stops it, so a hard
 *     fling parks against the boundary instead of running on into empty space.
 *  2. **Magnetize.** Only once the decay has come to rest does the position spring onto the
 *     nearest card, seeded with whatever velocity the decay had left. This is the analogue of
 *     Calm's debounced `magnetize()`: a correction applied to an already-settled position, never a
 *     hand-off from one motion to another. [CardStackScroll.magnet] supplies both halves of that
 *     debounce -- the beat the position is left standing where the decay stopped, and how firmly
 *     it is then pulled home. The wait is an ordinary cancellable suspension, so a finger landing
 *     during it catches the stack exactly where the fling parked it, which is the whole point of
 *     leaving it there.
 *  3. **Commit.** The magnetized position -- an exact multiple of
 *     [CardStackScroll.distancePerCardPx] -- is reported to `onSettle` with zero velocity, so the
 *     caller's own [com.riffle.core.domain.launcher.cards.CardStackController.settle] resolves to
 *     precisely the card the scroll stopped on, and the position is zeroed in the same breath
 *     because that new card is what it will be measured from next.
 *
 * Cancellation -- a new finger landing mid-fling -- simply leaves the position where it had got
 * to, which is what lets the next drag catch the moving stack.
 */
@Suppress("LongParameterList")
private suspend fun runCardStackScrollFling(
    initialVelocity: Float,
    scroll: CardStackScroll,
    scrollPx: MutableFloatState,
    lastHapticIndex: MutableIntState,
    decaySpec: DecayAnimationSpec<Float>,
    interaction: () -> CardStackInteraction?,
) {
    val range = cardStackScrollPxRange(scroll)

    fun publish(position: Float) {
        publishCardStackScrollPosition(
            position = position,
            scroll = scroll,
            scrollPx = scrollPx,
            lastHapticIndex = lastHapticIndex,
            interaction = interaction(),
        )
    }

    var restingVelocity = 0f
    AnimationState(initialValue = scrollPx.floatValue, initialVelocity = initialVelocity)
        .animateDecay(decaySpec) {
            val clamped = value.coerceIn(range)
            publish(clamped)
            if (clamped == value) {
                restingVelocity = velocity
            } else {
                // Ran into the first or last card: stop there with no leftover momentum rather
                // than continuing to decay against a position that can no longer move.
                restingVelocity = 0f
                cancelAnimation()
            }
        }

    val magnetizedPx = cardStackMagnetizedScrollPx(scrollPx.floatValue, scroll)
    if (magnetizedPx != scrollPx.floatValue) {
        // Calm's magnetize is posted behind its last scroll callback rather than run inline; the
        // stack visibly sits where the fling ran out for that beat before easing home. Waiting
        // here reproduces that, and because it is a plain cancellable delay the decayed position
        // stays catchable by a new finger throughout.
        delay(scroll.magnet.settleDelayMillis)
        AnimationState(initialValue = scrollPx.floatValue, initialVelocity = restingVelocity)
            .animateTo(
                targetValue = magnetizedPx,
                animationSpec = cardStackMagnetizeSpec(scroll.magnet),
            ) {
                publish(value.coerceIn(range))
            }
    }

    // Zeroing the position and committing the card it landed on have to reach the caller as one
    // pair of writes, before the next composition. The position is measured *from* the focused
    // card, so a composition that sees the new focused card while still holding the old position
    // -- or the reverse -- renders a card that neither value meant: overshot by exactly the
    // distance just travelled, for one visible frame. Committing first and clearing a frame later
    // (or clearing first and committing later) is precisely that frame.
    scrollPx.floatValue = 0f
    interaction()?.onLiveDrag?.invoke(null)
    // No haptic here: arriving on this card was itself a crossing, already ticked by publish()
    // above (see CardStackInteraction.onSettleHaptic). Ticking again would double up on every
    // gesture that moved the stack at all, and invent one for a fling that came back to the card
    // it started on.
    interaction()?.onSettle?.invoke(magnetizedPx, 0f)
}

/**
 * Writes one live scroll position out to both the state the renderer reads and the caller's own
 * [CardStackInteraction.onLiveDrag], ticking [CardStackInteraction.onSettleHaptic] whenever the
 * position has crossed onto a different card since the last tick. Shared by the finger's own drag
 * and by [runCardStackScrollFling]'s decay and magnetize phases, so one continuous motion ticks
 * once per card crossed however the position happens to be moving at the time.
 */
private fun publishCardStackScrollPosition(
    position: Float,
    scroll: CardStackScroll?,
    scrollPx: MutableFloatState,
    lastHapticIndex: MutableIntState,
    interaction: CardStackInteraction?,
) {
    scrollPx.floatValue = position
    interaction?.onLiveDrag?.invoke(position)
    if (scroll == null) return
    val crossedIndex = cardStackSettledCardIndex(position, scroll)
    if (lastHapticIndex.intValue != crossedIndex) {
        lastHapticIndex.intValue = crossedIndex
        interaction?.onSettleHaptic?.invoke()
    }
}

internal data class CardStackTransitionPose(
    val alpha: Float,
    val horizontalTravelFraction: Float,
    val verticalTravelFraction: Float,
)

internal data class CardStackRenderedPose(
    val alpha: Float,
    val offset: Float,
    val verticalOffset: Float,
)

internal enum class CardStackMotionMode {
    ANIMATED,
    SNAP,
}

internal enum class CardStackAnimationTiming { ENTER, REFLOW, SETTLE }

internal val CardStackAnimationProfileKey =
    SemanticsPropertyKey<CardStackAnimationProfile>("CardStackAnimationProfile")

internal val CardStackAnimationSpecKey = SemanticsPropertyKey<CardStackAnimationSpec>("CardStackAnimationSpec")

internal val CardStackMotionModeKey = SemanticsPropertyKey<CardStackMotionMode>("CardStackMotionMode")

/** Stable card identity exposed on the focus-owning semantic entry. */
internal val CardStackItemKey = SemanticsPropertyKey<Any>("CardStackItemKey")

/**
 * [stackPeakFraction] as a [BiasAlignment] along this stack's own settle axis, with the other axis
 * left centred. A [BiasAlignment] bias runs -1 (start of the axis) to 1 (end), placing the child's
 * *centre* at that point -- the same thing the reference "Calm" launcher's own
 * `CardStackLayout.activeTopPadding` computes as `viewportHeight * peakFraction - cardHeight / 2`.
 * A fraction outside 0..1 would push the focused card entirely off its own stack, so it is held to
 * that range rather than trusted.
 */
internal fun cardStackPeakAlignment(
    stackPeakFraction: Float,
    orientation: CardStackOrientation,
): BiasAlignment {
    val bias = stackPeakFraction.coerceIn(0f, 1f) * 2f - 1f
    return if (orientation == CardStackOrientation.HORIZONTAL) {
        BiasAlignment(horizontalBias = bias, verticalBias = 0f)
    } else {
        BiasAlignment(horizontalBias = 0f, verticalBias = bias)
    }
}

internal fun cardStackMotionMode(reducedMotion: Boolean): CardStackMotionMode =
    if (reducedMotion) {
        CardStackMotionMode.SNAP
    } else {
        CardStackMotionMode.ANIMATED
    }

internal fun cardStackTransitionPose(animationProfile: CardStackAnimationProfile): CardStackTransitionPose {
    return cardStackTransitionPose(animationProfile.spec)
}

internal fun cardStackTransitionPose(spec: CardStackAnimationSpec): CardStackTransitionPose {
    return CardStackTransitionPose(
        alpha = spec.enteringAlpha,
        horizontalTravelFraction = spec.horizontalTravelFraction,
        verticalTravelFraction = spec.verticalTravelFraction,
    )
}

internal fun cardStackRenderedPose(
    entry: CardStackLayoutEntry,
    animationProfile: CardStackAnimationProfile,
    entering: Boolean,
    width: Float,
    height: Float,
): CardStackRenderedPose {
    return cardStackRenderedPose(entry, animationProfile.spec, entering, width, height)
}

internal fun cardStackRenderedPose(
    entry: CardStackLayoutEntry,
    animationSpec: CardStackAnimationSpec,
    entering: Boolean,
    width: Float,
    height: Float,
): CardStackRenderedPose {
    if (!entering) return CardStackRenderedPose(entry.alpha, entry.offset, entry.verticalOffset)
    val pose = cardStackTransitionPose(animationSpec)
    return CardStackRenderedPose(
        alpha = entry.alpha * pose.alpha,
        offset = entry.offset + width * pose.horizontalTravelFraction,
        verticalOffset = entry.verticalOffset + height * pose.verticalTravelFraction,
    )
}

@Composable
@Suppress("LongMethod", "LongParameterList")
private fun AnimatedCardStackEntry(
    entry: CardStackLayoutEntry,
    stableItemKey: Any,
    animationSpec: CardStackAnimationSpec,
    motionMode: CardStackMotionMode,
    timing: CardStackAnimationTiming,
    isFocused: Boolean,
    interaction: CardStackInteraction?,
    isScrolling: State<Boolean>,
    dimFactor: Float = 1f,
    orientation: CardStackOrientation = CardStackOrientation.VERTICAL,
    content: @Composable (CardStackLayoutEntry, Modifier) -> Unit,
) {
    val spec = animationSpec
    var hasEntered by remember(stableItemKey) { mutableStateOf(motionMode == CardStackMotionMode.SNAP) }
    LaunchedEffect(stableItemKey, motionMode) { hasEntered = true }
    BoxWithConstraints {
        // entry.offset/verticalOffset (and every CardStackLayoutPolicy step that produces them)
        // are dp-scale, per the domain layer's own resolveCardStack/CardStackLayoutPolicy tests --
        // so width/height stay dp-scale here too (maxWidth.value, not maxWidth.toPx()), keeping
        // this whole pose in one consistent unit. The actual dp->px conversion happens once, at
        // the graphicsLayer assignment below, where GraphicsLayerScope's own Density applies.
        val renderedPose =
            cardStackRenderedPose(
                entry = entry,
                animationSpec = animationSpec,
                entering = !hasEntered,
                width = maxWidth.value,
                height = maxHeight.value,
            )
        val animationSpec =
            cardStackAnimationSpec(
                spec = spec,
                motionMode = motionMode,
                timing = if (hasEntered) timing else CardStackAnimationTiming.ENTER,
                settleStepCount = interaction?.settleStepCount ?: 1,
            )

        // While this stack's own scroll position is moving, every property below snaps straight to
        // its target instead of animating toward it -- the target itself is already changing every
        // frame (a caller recomputing `entries` from CardStackInteraction.onLiveDrag's report), so
        // animating on top would forever chase a moving target and visibly lag behind. The motion
        // of the scroll position *is* the animation here: the finger's own event rate supplies the
        // smoothness during a drag, and the fling/magnetize physics supply it afterwards (see
        // CardStackScroll), exactly as the reference "Calm" launcher's card stack recomputes every
        // card's pose from live scroll callbacks rather than running a property animator alongside
        // them.
        fun liveAwareSpec(animatesThisProperty: Boolean): AnimationSpec<Float> =
            if (isScrolling.value || !animatesThisProperty) snap() else animationSpec
        val alpha by animateFloatAsState(
            targetValue = renderedPose.alpha * dimFactor,
            animationSpec = liveAwareSpec(spec.animatesAlpha),
            label = "card-stack-alpha",
        )
        val offset by animateFloatAsState(
            targetValue = renderedPose.offset,
            animationSpec = liveAwareSpec(spec.animatesHorizontalTranslation),
            label = "card-stack-horizontal-offset",
        )
        val verticalOffset by animateFloatAsState(
            targetValue = renderedPose.verticalOffset,
            animationSpec = liveAwareSpec(spec.animatesVerticalTranslation),
            label = "card-stack-vertical-offset",
        )
        val scale by animateFloatAsState(
            targetValue = entry.scale,
            animationSpec = liveAwareSpec(spec.animatesScale),
            label = "card-stack-scale",
        )
        val rotationDegrees by animateFloatAsState(
            targetValue = entry.rotationDegrees,
            animationSpec = liveAwareSpec(spec.animatesRotation),
            label = "card-stack-rotation",
        )

        Box(
            modifier =
                Modifier
                    .zIndex(entry.order.toFloat())
                    // The visually focused card is the highest-order entry. Make it the
                    // first card reached by accessibility traversal without changing its
                    // deterministic back-to-front composition order.
                    .semantics(mergeDescendants = isFocused) {
                        traversalIndex = -entry.order.toFloat()
                        this[CardStackItemKey] = stableItemKey
                        if (!isFocused) invisibleToUser()
                    }
                    .then(
                        if (isFocused) {
                            Modifier.cardStackKeyboardInput(interaction, orientation)
                        } else {
                            Modifier
                        },
                    )
                    .graphicsLayer {
                        // offset/verticalOffset are dp-scale (see their computation in
                        // CardStackLayoutPolicy/resolveCardStack), but GraphicsLayerScope's
                        // translationX/Y are pixels, not dp -- unlike Modifier.offset(x = ...dp),
                        // this scope does not convert automatically. Without the explicit
                        // .dp.toPx() below, every configured offset/spacing/curve value rendered
                        // at only 1/density of its intended distance (invisible on most real
                        // devices); GraphicsLayerScope itself implements Density, so this dp.toPx()
                        // needs no external density lookup.
                        // HORIZONTAL rotates the whole coordinate system 90 degrees: the settle
                        // axis (verticalOffset by default) becomes translationX, and the
                        // fan/stagger axis (offset by default) becomes translationY.
                        if (orientation == CardStackOrientation.HORIZONTAL) {
                            translationX = verticalOffset.dp.toPx()
                            translationY = offset.dp.toPx()
                        } else {
                            translationX = offset.dp.toPx()
                            translationY = verticalOffset.dp.toPx()
                        }
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        rotationZ = rotationDegrees
                    },
        ) {
            content(entry, Modifier)
        }
    }
}

private fun Modifier.cardStackKeyboardInput(
    interaction: CardStackInteraction?,
    orientation: CardStackOrientation,
): Modifier {
    val requester = interaction?.keyboardFocusRequester ?: return this
    val previousKeys =
        if (orientation == CardStackOrientation.HORIZONTAL) {
            setOf(Key.DirectionLeft)
        } else {
            setOf(Key.DirectionUp, Key.PageUp)
        }
    val nextKeys =
        if (orientation == CardStackOrientation.HORIZONTAL) {
            setOf(Key.DirectionRight)
        } else {
            setOf(Key.DirectionDown, Key.PageDown)
        }
    return focusRequester(requester)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (event.key) {
                in previousKeys -> interaction.onNavigate?.invoke(CardStackNavigationDirection.PREVIOUS) ?: false
                in nextKeys -> interaction.onNavigate?.invoke(CardStackNavigationDirection.NEXT) ?: false

                Key.DirectionCenter,
                Key.Enter,
                Key.NumPadEnter,
                Key.Spacebar,
                ->
                    interaction.onExpand?.let { expand ->
                        expand()
                        true
                    } ?: false

                else -> false
            }
        }
}

internal fun cardStackAnimationSpec(
    spec: CardStackAnimationSpec,
    motionMode: CardStackMotionMode,
    timing: CardStackAnimationTiming,
    settleStepCount: Int = 1,
): AnimationSpec<Float> {
    if (motionMode == CardStackMotionMode.SNAP) return snap()
    val durationMillis = cardStackAnimationDuration(spec, timing, settleStepCount)
    return when (spec.easing) {
        CardStackAnimationEasing.STANDARD -> tween(durationMillis = durationMillis, easing = LinearOutSlowInEasing)
        CardStackAnimationEasing.EMPHASIZED -> tween(durationMillis = durationMillis, easing = FastOutSlowInEasing)
        CardStackAnimationEasing.GENTLE_SPRING ->
            spring(
                dampingRatio = (1f - spec.springBouncinessPercent / 100f * 0.55f).coerceAtLeast(0.45f),
                stiffness =
                    Spring.StiffnessMedium *
                        (DEFAULT_CARD_STACK_ANIMATION_DURATION_MILLIS.toFloat() / durationMillis).coerceIn(0.2f, 3f),
            )
    }
}

internal fun cardStackAnimationDuration(
    spec: CardStackAnimationSpec,
    timing: CardStackAnimationTiming,
    settleStepCount: Int = 1,
): Int =
    when (timing) {
        CardStackAnimationTiming.ENTER -> spec.enterDurationMillis
        CardStackAnimationTiming.REFLOW -> spec.durationMillis
        CardStackAnimationTiming.SETTLE -> cardStackSettleDurationMillis(spec.settleDurationMillis, settleStepCount)
    }

/**
 * Scales a settle's base duration by how many cards it's actually skipping, so a multi-card fling
 * or hard drag (see [com.riffle.core.domain.launcher.cards.CardStackController.settle]'s doc)
 * reads as travel across those cards instead of the same brief flick a single-card settle uses.
 * Capped at [SETTLE_DURATION_STEP_CAP] steps' worth of scaling so an extreme skip across a long
 * stack still settles in a bounded time rather than growing without limit.
 */
internal fun cardStackSettleDurationMillis(
    baseDurationMillis: Int,
    settleStepCount: Int,
): Int {
    require(baseDurationMillis >= 0) { "Base duration must not be negative." }
    require(settleStepCount >= 1) { "Settle step count must be at least 1." }
    return baseDurationMillis * settleStepCount.coerceAtMost(SETTLE_DURATION_STEP_CAP)
}

private const val DEFAULT_CARD_STACK_ANIMATION_DURATION_MILLIS = 220
private const val SETTLE_DURATION_STEP_CAP = 4

/**
 * How far this stack's own axis has to travel to advance the scroll by one card, when a caller
 * doesn't say. Matches the settle distance threshold the primary notification stack and the feed
 * stack both use, so the default feel is the tuned one.
 */
internal const val DEFAULT_CARD_STACK_SCROLL_DISTANCE_PER_CARD_PX = 64f

/** The [CardStack] `stackPeakFraction` that centres the focused card -- this stack's prior fixed behavior. */
internal const val CENTERED_CARD_STACK_PEAK_FRACTION = 0.5f

/**
 * The nudge that pulls an already-decelerated scroll position onto the nearest card. Deliberately
 * unbouncy at every strength: it is a correction to a position that has already stopped moving on
 * its own (see [runCardStackScrollFling]), so any overshoot here would read as the stack second-
 * guessing where the fling left it. Only how *briskly* it travels is tunable --
 * [CardStackMagnet.stiffnessScale] against a [Spring.StiffnessMediumLow] base, so the default
 * strength lands near the single fixed stiffness this replaced.
 */
private fun cardStackMagnetizeSpec(magnet: CardStackMagnet): AnimationSpec<Float> =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow * magnet.stiffnessScale,
    )

/**
 * Focuses [entry] on a plain tap, for a not-yet-focused entry only -- refocusing an already-focused
 * card is a no-op (see [com.riffle.core.domain.launcher.cards.CardStackController.jumpTo]), and
 * skipping the modifier entirely there leaves a caller's own click handling (e.g. tapping the
 * focused card to launch its app) as the sole click handler on that entry instead of stacking two.
 *
 * Runs on [PointerEventPass.Initial] and never consumes anything, mirroring how
 * `adaptiveStageStagePagerDrag` used to claim priority over `cardStackPointerInput` before its own
 * migration to `HorizontalPager` (#1126): this stack's root now installs its own
 * `Modifier.scrollable` for the drag/settle gesture (see [CardStack]'s own body), and this tap
 * detector needs to reach its own tap-or-not verdict independent of whatever pass/timing that
 * ancestor's Main-pass gesture recognition uses internally -- a `clickable`/`detectTapGestures`
 * here, both of which only see events *after* an ancestor scrollable's Main-pass processing, proved
 * unreliable for a zero-duration synthetic tap in `GeneratedPageSurfaceInteractionTest`.
 */
@Suppress("LoopWithTooManyJumpStatements")
private fun Modifier.cardStackTapToFocus(
    entry: CardStackLayoutEntry,
    isFocused: Boolean,
    interaction: CardStackInteraction?,
): Modifier {
    if (interaction == null || isFocused) return this
    // composed + rememberUpdatedState (mirroring dockSwipeUpGestureInput's identical need) rather
    // than keying pointerInput on `interaction`/`entry` directly: both are fresh instances every
    // recomposition, and keying on them would restart this gesture's coroutine mid-tap. Keying on
    // entry.cardIndex instead -- stable for a given card across recompositions -- avoids that while
    // still resetting the recognizer if this slot starts rendering a different card.
    return composed {
        val currentInteraction by rememberUpdatedState(interaction)
        val currentEntry by rememberUpdatedState(entry)
        pointerInput(entry.cardIndex) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var isTap = true
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    val delta = change.position - down.position
                    if (abs(delta.x) > viewConfiguration.touchSlop || abs(delta.y) > viewConfiguration.touchSlop) {
                        isTap = false
                    }
                    if (!change.pressed) break
                }
                if (isTap) {
                    currentInteraction.onFocusRequest(currentEntry)
                }
            }
        }
    }
}
