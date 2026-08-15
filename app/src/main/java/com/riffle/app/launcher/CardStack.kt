@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.riffle.core.domain.launcher.cards.CardStackNavigationDirection
import kotlin.math.abs

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

/** Callbacks supplied by a surface that owns durable card focus. */
internal data class CardStackInteraction(
    val focusedItemKey: Any?,
    val onFocusRequest: (CardStackLayoutEntry) -> Unit,
    /** Named for the [CardStackOrientation.VERTICAL] default; carries horizontal drag/velocity
     *  instead when [CardStackOrientation.HORIZONTAL] -- treat both params as "this stack's own
     *  primary-axis drag," not literally vertical. */
    val onSettle: (verticalDragPx: Float, verticalVelocityPxPerSecond: Float) -> Unit,
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
     * Called continuously while this stack's own drag/settle axis gesture is in progress, with
     * the raw signed pixel delta along that axis since the gesture began; called with `null` when
     * no such gesture is active (including right after one ends). A caller that wants the stack
     * to visibly track the finger during the drag itself -- not just once it settles -- uses this
     * to recompute a *fractional* activeIndex every frame (see [com.riffle.core.domain.launcher
     * .cards.CardStackLayoutPolicy]'s Float `entries()` overload), the same way the reference
     * "Calm" launcher's own card stack continuously recomputes every card's pose from live scroll
     * position rather than a discrete index -- not by uniformly shifting the whole rendered stack,
     * which is what an earlier, simpler version of this mechanism did.
     */
    val onLiveDrag: ((dragPx: Float?) -> Unit)? = null,
)

@Composable
@Suppress("LongMethod", "LongParameterList")
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
    content: @Composable (CardStackLayoutEntry, Modifier) -> Unit,
) {
    val motionMode = cardStackMotionMode(reducedMotion)
    val focusRequesters = remember { mutableMapOf<Any, FocusRequester>() }
    // Whether this stack's own drag/settle axis gesture is currently in progress -- written live,
    // every pointer move, by whichever entry the gesture actually started on
    // (cardStackPointerInput below). Used only to suspend every entry's animateFloatAsState during
    // the drag (see AnimatedCardStackEntry), so a caller that recomputes `entries` every frame from
    // CardStackInteraction.onLiveDrag's report (a fractional activeIndex -- see that callback's own
    // doc) renders each new pose immediately instead of chasing a constantly-moving animation
    // target. Purely a rendering concern -- distinct from onLiveDrag itself, which is what actually
    // lets a caller recompute those entries in the first place.
    val isDragging = remember { mutableStateOf(false) }
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

    Box(
        modifier =
            modifier
                // Fanned/rotated background entries can translate beyond the focused card's own
                // footprint by design (see AdaptiveStageAppearanceSettings' fan-stage-margin
                // doc) -- without this, that overflow bled into whatever sibling UI sits above
                // or below the stack (nav controls, contextual actions, the dock) instead of
                // staying contained to the stack's own allotted area.
                .clipToBounds()
                .semantics {
                    isTraversalGroup = true
                    this[CardStackAnimationProfileKey] = animationProfile
                    this[CardStackMotionModeKey] = motionMode
                    this[CardStackAnimationSpecKey] = animationSpec
                },
        // Each entry wraps to its own card's size, not this root's full area -- without this,
        // Box's default TopStart pins every card to the top-left corner instead of centering it.
        contentAlignment = Alignment.Center,
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
                    isDragging = isDragging,
                    interaction =
                        interaction?.copy(
                            onNavigate = ::navigateFromKeyboard,
                            keyboardFocusRequester = focusRequester,
                        ),
                    content = { entry, modifier ->
                        content(
                            entry,
                            modifier.cardStackPointerInput(
                                entry = entry,
                                stableItemKey = stableItemKey,
                                isFocused = stableItemKey == interaction?.focusedItemKey,
                                interaction = interaction,
                                orientation = orientation,
                                isDragging = isDragging,
                            ),
                        )
                    },
                )
            }
        }
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
    isDragging: State<Boolean>,
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

        // While this stack's own gesture is live, every property below snaps straight to its
        // target instead of animating toward it -- the target itself is already changing every
        // frame (a caller recomputing `entries` from CardStackInteraction.onLiveDrag's report), so
        // animating on top would forever chase a moving target and visibly lag behind the finger.
        // The high frame rate of the drag's own pointer events supplies the smoothness instead,
        // exactly as the reference "Calm" launcher's card stack relies on live scroll callbacks
        // rather than a property animator while a finger is down.
        fun liveAwareSpec(animatesThisProperty: Boolean): AnimationSpec<Float> =
            if (isDragging.value || !animatesThisProperty) snap() else animationSpec
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
 * How much larger the perpendicular drag must be than this stack's own axis for a nascent gesture
 * to be handed to an ancestor page/stage surface instead of driving this stack's own settle. Any
 * drag whose perpendicular component is within this factor of the own-axis component stays with
 * this stack; only a clearly cross-axis pan reaches the ancestor. See [cardStackPointerInput].
 */
private const val PASS_THROUGH_AXIS_LEAD_RATIO = 1.5f

@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "LongMethod")
private fun Modifier.cardStackPointerInput(
    entry: CardStackLayoutEntry,
    stableItemKey: Any,
    isFocused: Boolean,
    interaction: CardStackInteraction?,
    orientation: CardStackOrientation,
    isDragging: MutableState<Boolean>,
): Modifier {
    if (interaction == null) return this
    // This stack's own drag-to-navigate axis follows orientation; the other axis is always left
    // unconsumed for an ancestor's page/stage drag, exactly as the VERTICAL default always has.
    val ownAxis =
        if (orientation == CardStackOrientation.HORIZONTAL) {
            CardStackGestureAxis.HORIZONTAL
        } else {
            CardStackGestureAxis.VERTICAL
        }
    // composed + rememberUpdatedState (mirroring dockSwipeUpGestureInput's identical need) rather
    // than keying pointerInput on `interaction` directly: interaction is a fresh data class
    // instance every recomposition (its own onLiveDrag closure among them), and since onLiveDrag
    // firing is exactly what drives its *caller* to recompose every drag frame, keying on it would
    // restart this gesture's coroutine on every frame of the very drag it's reporting -- the
    // restarted coroutine's awaitFirstDown() then just waits for a down event that already
    // happened, silently dropping the rest of that touch. Reading the always-current value inside
    // the coroutine via rememberUpdatedState keeps callbacks fresh without tearing the gesture down
    // mid-drag; stableItemKey/isFocused/orientation are stable for a gesture's whole duration (see
    // their own docs) so keying on those is safe.
    return composed {
        val currentInteraction by rememberUpdatedState(interaction)
        pointerInput(stableItemKey, isFocused, orientation) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val pointerId: PointerId = down.id
                var verticalDrag = 0f
                var horizontalDrag = 0f
                var axis: CardStackGestureAxis? = null
                var cancelled = false
                val velocityTracker = VelocityTracker()
                // Every subsequent sample is added inside the loop below, but the touch-down
                // itself -- captured here, before that loop starts -- was never added. Harmless
                // for an ordinary drag with plenty of later samples to establish velocity from,
                // but a genuinely quick, short flick can produce only one or two motion events
                // total before release; omitting the gesture's own starting point/time
                // disproportionately starves calculateVelocity() of data for exactly that style
                // of gesture, understating velocity for the fastest flicks specifically -- they
                // fell below onSettle's fling threshold and read as unresponsive.
                velocityTracker.addPosition(down.uptimeMillis, down.position)

                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.size != 1) {
                        cancelled = true
                        break
                    }
                    val change = event.changes.firstOrNull { it.id == pointerId }
                    if (change == null) {
                        cancelled = true
                        break
                    }
                    val delta = change.position - change.previousPosition
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    verticalDrag += delta.y
                    horizontalDrag += delta.x
                    if (
                        axis == null &&
                        (
                            abs(verticalDrag) > viewConfiguration.touchSlop ||
                                abs(horizontalDrag) > viewConfiguration.touchSlop
                        )
                    ) {
                        // Latch to this stack's own axis unless the perpendicular drag *clearly*
                        // dominates. The older "whichever is momentarily larger" tiebreak fired the
                        // moment either axis first crossed touchSlop, and a genuinely vertical fling
                        // whose ballistic phase drifted a couple of pixels sideways would latch to
                        // the pass-through axis on that first frame just because the perpendicular
                        // drag happened to edge the own axis by a hair. From then on `change.consume()`
                        // was skipped and `onSettle` was never called, but the finalisation block
                        // below still fired `isDragging = false` and `onLiveDrag(null)`, so the
                        // stack visibly snapped back to the card the drag started on -- reading as
                        // an unresponsive fling. Only give up to the perpendicular axis when it
                        // leads by [PASS_THROUGH_AXIS_LEAD_RATIO], leaving the near-straight own-axis
                        // fling on this stack and a clearly cross-axis pan free to reach the
                        // ancestor page/stage surface as before.
                        val ownDrag =
                            if (ownAxis == CardStackGestureAxis.VERTICAL) verticalDrag else horizontalDrag
                        val perpendicularDrag =
                            if (ownAxis == CardStackGestureAxis.VERTICAL) horizontalDrag else verticalDrag
                        axis =
                            if (abs(perpendicularDrag) > abs(ownDrag) * PASS_THROUGH_AXIS_LEAD_RATIO) {
                                if (ownAxis == CardStackGestureAxis.VERTICAL) {
                                    CardStackGestureAxis.HORIZONTAL
                                } else {
                                    CardStackGestureAxis.VERTICAL
                                }
                            } else {
                                ownAxis
                            }
                    }
                    if (axis == ownAxis) {
                        change.consume()
                        // Reports this gesture upward so a caller can recompute a live, fractional
                        // activeIndex every frame (see CardStackInteraction.onLiveDrag's own doc);
                        // isDragging suspends this stack's own settle animation for the same frames
                        // so that recomputed pose renders immediately instead of being animated toward.
                        isDragging.value = true
                        currentInteraction?.onLiveDrag?.invoke(
                            if (orientation == CardStackOrientation.HORIZONTAL) horizontalDrag else verticalDrag,
                        )
                    }
                    if (!change.pressed) {
                        // A background-card tap focuses the card without consuming an ancestor's
                        // cross-axis page/stage drag before that drag's axis is known.
                        if (axis == null && !isFocused) change.consume()
                        break
                    }
                }
                isDragging.value = false
                currentInteraction?.onLiveDrag?.invoke(null)

                when {
                    cancelled -> Unit
                    axis == null -> currentInteraction?.onFocusRequest?.invoke(entry)
                    axis == ownAxis ->
                        currentInteraction?.run {
                            val (dragPx, velocityPxPerSecond) =
                                if (orientation == CardStackOrientation.HORIZONTAL) {
                                    horizontalDrag to velocityTracker.calculateVelocity().x
                                } else {
                                    verticalDrag to velocityTracker.calculateVelocity().y
                                }
                            onSettle(dragPx, velocityPxPerSecond)
                            onSettleHaptic()
                        }
                    // Cross-axis gestures remain unconsumed for the owning page/stage surface.
                    else -> Unit
                }
            }
        }
    }
}

private enum class CardStackGestureAxis { VERTICAL, HORIZONTAL }
