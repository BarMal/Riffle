package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import kotlin.math.abs

/** Callbacks supplied by a surface that owns durable card focus. */
internal data class CardStackInteraction(
    val focusedItemKey: Any?,
    val onFocusRequest: (CardStackLayoutEntry) -> Unit,
    val onSettle: (verticalDragPx: Float, verticalVelocityPxPerSecond: Float) -> Unit,
    val onSettleHaptic: () -> Unit = {},
)

@Composable
internal fun CardStack(
    entries: List<CardStackLayoutEntry>,
    modifier: Modifier = Modifier,
    animationProfile: CardStackAnimationProfile = CardStackAnimationProfile.STACK_REFLOW,
    reducedMotion: Boolean = false,
    itemKey: (CardStackLayoutEntry) -> Any = { entry -> entry.cardIndex },
    interaction: CardStackInteraction? = null,
    content: @Composable (CardStackLayoutEntry, Modifier) -> Unit,
) {
    val motionMode = cardStackMotionMode(reducedMotion)

    Box(
        modifier =
            modifier.semantics {
                isTraversalGroup = true
                this[CardStackAnimationProfileKey] = animationProfile
                this[CardStackMotionModeKey] = motionMode
            },
    ) {
        entries.forEach { entry ->
            // A card index identifies a stable card while focus changes, so Compose can
            // interpolate that card's prior pose into its new pose without composing a
            // second, outgoing stack.
            val stableItemKey = itemKey(entry)
            key(stableItemKey) {
                AnimatedCardStackEntry(
                    entry = entry,
                    stableItemKey = stableItemKey,
                    animationProfile = animationProfile,
                    motionMode = motionMode,
                    content = { entry, modifier ->
                        content(
                            entry,
                            modifier.cardStackPointerInput(
                                entry = entry,
                                stableItemKey = stableItemKey,
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

internal val CardStackAnimationProfileKey =
    SemanticsPropertyKey<CardStackAnimationProfile>("CardStackAnimationProfile")

internal val CardStackMotionModeKey = SemanticsPropertyKey<CardStackMotionMode>("CardStackMotionMode")

internal fun cardStackMotionMode(reducedMotion: Boolean): CardStackMotionMode =
    if (reducedMotion) {
        CardStackMotionMode.SNAP
    } else {
        CardStackMotionMode.ANIMATED
    }

internal fun cardStackTransitionPose(animationProfile: CardStackAnimationProfile): CardStackTransitionPose {
    val spec = animationProfile.spec

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
    if (!entering) return CardStackRenderedPose(entry.alpha, entry.offset, entry.verticalOffset)
    val pose = cardStackTransitionPose(animationProfile)
    return CardStackRenderedPose(
        alpha = entry.alpha * pose.alpha,
        offset = entry.offset + width * pose.horizontalTravelFraction,
        verticalOffset = entry.verticalOffset + height * pose.verticalTravelFraction,
    )
}

@Composable
private fun AnimatedCardStackEntry(
    entry: CardStackLayoutEntry,
    stableItemKey: Any,
    animationProfile: CardStackAnimationProfile,
    motionMode: CardStackMotionMode,
    content: @Composable (CardStackLayoutEntry, Modifier) -> Unit,
) {
    val spec = animationProfile.spec
    var hasEntered by remember(stableItemKey) { mutableStateOf(motionMode == CardStackMotionMode.SNAP) }
    LaunchedEffect(stableItemKey, motionMode) { hasEntered = true }
    val density = LocalDensity.current
    BoxWithConstraints {
        val renderedPose =
            cardStackRenderedPose(
                entry = entry,
                animationProfile = animationProfile,
                entering = !hasEntered,
                width = with(density) { maxWidth.toPx() },
                height = with(density) { maxHeight.toPx() },
            )
        val animationSpec =
            if (motionMode == CardStackMotionMode.SNAP) {
                snap()
            } else {
                tween<Float>(durationMillis = spec.durationMillis)
            }
        val alpha by animateFloatAsState(
            targetValue = renderedPose.alpha,
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
                    .semantics {
                        traversalIndex = -entry.order.toFloat()
                    }
                    .graphicsLayer {
                        translationX = offset
                        translationY = verticalOffset
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

@Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
private fun Modifier.cardStackPointerInput(
    entry: CardStackLayoutEntry,
    stableItemKey: Any,
    isFocused: Boolean,
    interaction: CardStackInteraction?,
): Modifier {
    if (interaction == null) return this
    return pointerInput(stableItemKey, isFocused, interaction) {
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
                if (axis == CardStackGestureAxis.VERTICAL) change.consume()
                if (!change.pressed) {
                    // A background-card tap focuses the card without consuming an ancestor's
                    // horizontal page/stage drag before that drag's axis is known.
                    if (axis == null && !isFocused) change.consume()
                    break
                }
            }

            when {
                cancelled -> Unit
                axis == null -> interaction.onFocusRequest(entry)
                axis == CardStackGestureAxis.VERTICAL ->
                    interaction.run {
                        onSettle(verticalDrag, velocityTracker.calculateVelocity().y)
                        onSettleHaptic()
                    }
                // Horizontal gestures remain unconsumed for the owning page/stage surface.
                else -> Unit
            }
        }
    }
}

private enum class CardStackGestureAxis { VERTICAL, HORIZONTAL }
