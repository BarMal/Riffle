package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry

@Composable
internal fun CardStack(
    entries: List<CardStackLayoutEntry>,
    modifier: Modifier = Modifier,
    animationProfile: CardStackAnimationProfile = CardStackAnimationProfile.STACK_REFLOW,
    reducedMotion: Boolean = false,
    content: @Composable (CardStackLayoutEntry) -> Unit,
) {
    val motionMode = cardStackMotionMode(animationProfile, reducedMotion)

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
            key(entry.cardIndex) {
                AnimatedCardStackEntry(
                    entry = entry,
                    animationProfile = animationProfile,
                    motionMode = motionMode,
                    content = content,
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

internal fun cardStackMotionMode(
    animationProfile: CardStackAnimationProfile,
    reducedMotion: Boolean,
): CardStackMotionMode =
    if (reducedMotion || animationProfile == CardStackAnimationProfile.STACK_REFLOW) {
        CardStackMotionMode.SNAP
    } else {
        CardStackMotionMode.ANIMATED
    }

internal fun cardStackTransitionPose(
    animationProfile: CardStackAnimationProfile,
    entering: Boolean,
): CardStackTransitionPose {
    val spec = animationProfile.spec
    val travelDirection = if (entering) 1f else -1f

    return CardStackTransitionPose(
        alpha = if (entering) spec.enteringAlpha else spec.exitingAlpha,
        horizontalTravelFraction = spec.horizontalTravelFraction.directedTravel(travelDirection),
        verticalTravelFraction = spec.verticalTravelFraction.directedTravel(travelDirection),
    )
}

private fun Float.directedTravel(direction: Float): Float {
    return if (this == 0f) 0f else this * direction
}

internal fun cardStackRenderedPose(
    entry: CardStackLayoutEntry,
    animationProfile: CardStackAnimationProfile,
    entering: Boolean,
    width: Float,
    height: Float,
): CardStackRenderedPose {
    if (!entering) return CardStackRenderedPose(entry.alpha, entry.offset, entry.verticalOffset)
    val pose = cardStackTransitionPose(animationProfile, entering = true)
    return CardStackRenderedPose(
        alpha = entry.alpha * pose.alpha,
        offset = entry.offset + width * pose.horizontalTravelFraction,
        verticalOffset = entry.verticalOffset + height * pose.verticalTravelFraction,
    )
}

@Composable
private fun AnimatedCardStackEntry(
    entry: CardStackLayoutEntry,
    animationProfile: CardStackAnimationProfile,
    motionMode: CardStackMotionMode,
    content: @Composable (CardStackLayoutEntry) -> Unit,
) {
    val spec = animationProfile.spec
    var hasEntered by remember(entry.cardIndex) { mutableStateOf(motionMode == CardStackMotionMode.SNAP) }
    LaunchedEffect(entry.cardIndex, motionMode) { hasEntered = true }
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
            content(entry)
        }
    }
}
