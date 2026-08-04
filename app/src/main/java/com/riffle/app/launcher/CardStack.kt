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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
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
    /** Requester owned by the currently focused rendered entry. */
    val keyboardFocusRequester: FocusRequester? = null,
)

@Composable
@Suppress("LongParameterList")
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
            modifier.semantics {
                isTraversalGroup = true
                this[CardStackAnimationProfileKey] = animationProfile
                this[CardStackMotionModeKey] = motionMode
                this[CardStackAnimationSpecKey] = animationSpec
            },
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
@Suppress("LongParameterList")
private fun AnimatedCardStackEntry(
    entry: CardStackLayoutEntry,
    stableItemKey: Any,
    animationSpec: CardStackAnimationSpec,
    motionMode: CardStackMotionMode,
    timing: CardStackAnimationTiming,
    isFocused: Boolean,
    interaction: CardStackInteraction?,
    dimFactor: Float = 1f,
    orientation: CardStackOrientation = CardStackOrientation.VERTICAL,
    content: @Composable (CardStackLayoutEntry, Modifier) -> Unit,
) {
    val spec = animationSpec
    var hasEntered by remember(stableItemKey) { mutableStateOf(motionMode == CardStackMotionMode.SNAP) }
    LaunchedEffect(stableItemKey, motionMode) { hasEntered = true }
    val density = LocalDensity.current
    BoxWithConstraints {
        val renderedPose =
            cardStackRenderedPose(
                entry = entry,
                animationSpec = animationSpec,
                entering = !hasEntered,
                width = with(density) { maxWidth.toPx() },
                height = with(density) { maxHeight.toPx() },
            )
        val animationSpec =
            cardStackAnimationSpec(
                spec = spec,
                motionMode = motionMode,
                timing = if (hasEntered) timing else CardStackAnimationTiming.ENTER,
            )
        val alpha by animateFloatAsState(
            targetValue = renderedPose.alpha * dimFactor,
            animationSpec = if (spec.animatesAlpha) animationSpec else snap(),
            label = "card-stack-alpha",
        )
        val offset by animateFloatAsState(
            targetValue = renderedPose.offset,
            animationSpec = if (spec.animatesHorizontalTranslation) animationSpec else snap(),
            label = "card-stack-horizontal-offset",
        )
        val verticalOffset by animateFloatAsState(
            targetValue = renderedPose.verticalOffset,
            animationSpec = if (spec.animatesVerticalTranslation) animationSpec else snap(),
            label = "card-stack-vertical-offset",
        )
        val scale by animateFloatAsState(
            targetValue = entry.scale,
            animationSpec = if (spec.animatesScale) animationSpec else snap(),
            label = "card-stack-scale",
        )
        val rotationDegrees by animateFloatAsState(
            targetValue = entry.rotationDegrees,
            animationSpec = if (spec.animatesRotation) animationSpec else snap(),
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
                        // HORIZONTAL rotates the whole coordinate system 90 degrees: the settle
                        // axis (verticalOffset by default) becomes translationX, and the
                        // fan/stagger axis (offset by default) becomes translationY.
                        if (orientation == CardStackOrientation.HORIZONTAL) {
                            translationX = verticalOffset
                            translationY = offset
                        } else {
                            translationX = offset
                            translationY = verticalOffset
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
        if (orientation == CardStackOrientation.HORIZONTAL) setOf(Key.DirectionLeft) else setOf(Key.DirectionUp, Key.PageUp)
    val nextKeys =
        if (orientation == CardStackOrientation.HORIZONTAL) setOf(Key.DirectionRight) else setOf(Key.DirectionDown, Key.PageDown)
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
): AnimationSpec<Float> {
    if (motionMode == CardStackMotionMode.SNAP) return snap()
    val durationMillis = cardStackAnimationDuration(spec, timing)
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
): Int =
    when (timing) {
        CardStackAnimationTiming.ENTER -> spec.enterDurationMillis
        CardStackAnimationTiming.REFLOW -> spec.durationMillis
        CardStackAnimationTiming.SETTLE -> spec.settleDurationMillis
    }

private const val DEFAULT_CARD_STACK_ANIMATION_DURATION_MILLIS = 220

@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
private fun Modifier.cardStackPointerInput(
    entry: CardStackLayoutEntry,
    stableItemKey: Any,
    isFocused: Boolean,
    interaction: CardStackInteraction?,
    orientation: CardStackOrientation,
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
    return pointerInput(stableItemKey, isFocused, interaction, orientation) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId: PointerId = down.id
            var verticalDrag = 0f
            var horizontalDrag = 0f
            var axis: CardStackGestureAxis? = null
            var cancelled = false
            val velocityTracker = VelocityTracker()

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
                    axis =
                        if (abs(verticalDrag) > abs(horizontalDrag)) {
                            CardStackGestureAxis.VERTICAL
                        } else {
                            CardStackGestureAxis.HORIZONTAL
                        }
                }
                if (axis == ownAxis) change.consume()
                if (!change.pressed) {
                    // A background-card tap focuses the card without consuming an ancestor's
                    // cross-axis page/stage drag before that drag's axis is known.
                    if (axis == null && !isFocused) change.consume()
                    break
                }
            }

            when {
                cancelled -> Unit
                axis == null -> interaction.onFocusRequest(entry)
                axis == ownAxis ->
                    interaction.run {
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

private enum class CardStackGestureAxis { VERTICAL, HORIZONTAL }
