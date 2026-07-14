package com.riffle.app.launcher

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry
import kotlin.math.roundToInt

@Composable
internal fun CardStack(
    entries: List<CardStackLayoutEntry>,
    modifier: Modifier = Modifier,
    animationProfile: CardStackAnimationProfile = CardStackAnimationProfile.STACK_REFLOW,
    reducedMotion: Boolean = false,
    content: @Composable (CardStackLayoutEntry) -> Unit,
) {
    Box(
        modifier =
            modifier.semantics {
                isTraversalGroup = true
            },
    ) {
        AnimatedContent(
            targetState = entries,
            transitionSpec = {
                cardStackContentTransform(
                    animationProfile = animationProfile,
                    reducedMotion = reducedMotion,
                )
            },
            label = "card-stack-content",
        ) { visibleEntries ->
            visibleEntries.forEach { entry ->
                // A card index identifies a stable card while focus changes, so Compose can
                // interpolate that card's old pose into its new pose instead of restarting it.
                key(entry.cardIndex) {
                    AnimatedCardStackEntry(
                        entry = entry,
                        animationProfile = animationProfile,
                        reducedMotion = reducedMotion,
                        content = content,
                    )
                }
            }
        }
    }
}

internal data class CardStackTransitionPose(
    val alpha: Float,
    val horizontalTravelFraction: Float,
    val verticalTravelFraction: Float,
)

internal fun cardStackTransitionPose(
    animationProfile: CardStackAnimationProfile,
    entering: Boolean,
): CardStackTransitionPose {
    val spec = animationProfile.spec
    val travelDirection = if (entering) 1f else -1f

    return CardStackTransitionPose(
        alpha = if (entering) spec.enteringAlpha else spec.exitingAlpha,
        horizontalTravelFraction = spec.horizontalTravelFraction * travelDirection,
        verticalTravelFraction = spec.verticalTravelFraction * travelDirection,
    )
}

private fun cardStackContentTransform(
    animationProfile: CardStackAnimationProfile,
    reducedMotion: Boolean,
) = if (reducedMotion || animationProfile == CardStackAnimationProfile.STACK_REFLOW) {
        EnterTransition.None togetherWith ExitTransition.None
    } else {
        val enteringPose = cardStackTransitionPose(animationProfile, entering = true)
        val exitingPose = cardStackTransitionPose(animationProfile, entering = false)
        val spec = animationProfile.spec
        val alphaAnimation = tween<Float>(durationMillis = spec.durationMillis)
        val translationAnimation = tween<Int>(durationMillis = spec.durationMillis)
        val enterAlpha =
            if (spec.animatesAlpha) fadeIn(alphaAnimation, enteringPose.alpha) else EnterTransition.None
        val enterHorizontalTranslation =
            if (spec.animatesHorizontalTranslation) {
                slideInHorizontally(translationAnimation) { width ->
                    (width * enteringPose.horizontalTravelFraction).roundToInt()
                }
            } else {
                EnterTransition.None
            }
        val enterVerticalTranslation =
            if (spec.animatesVerticalTranslation) {
                slideInVertically(translationAnimation) { height ->
                    (height * enteringPose.verticalTravelFraction).roundToInt()
                }
            } else {
                EnterTransition.None
            }
        val exitAlpha =
            if (spec.animatesAlpha) fadeOut(alphaAnimation, exitingPose.alpha) else ExitTransition.None
        val exitHorizontalTranslation =
            if (spec.animatesHorizontalTranslation) {
                slideOutHorizontally(translationAnimation) { width ->
                    (width * exitingPose.horizontalTravelFraction).roundToInt()
                }
            } else {
                ExitTransition.None
            }
        val exitVerticalTranslation =
            if (spec.animatesVerticalTranslation) {
                slideOutVertically(translationAnimation) { height ->
                    (height * exitingPose.verticalTravelFraction).roundToInt()
                }
            } else {
                ExitTransition.None
            }
        val enter = enterAlpha + enterHorizontalTranslation + enterVerticalTranslation
        val exit = exitAlpha + exitHorizontalTranslation + exitVerticalTranslation

        enter togetherWith exit
    }

@Composable
private fun AnimatedCardStackEntry(
    entry: CardStackLayoutEntry,
    animationProfile: CardStackAnimationProfile,
    reducedMotion: Boolean,
    content: @Composable (CardStackLayoutEntry) -> Unit,
) {
    val spec = animationProfile.spec
    val animationSpec =
        if (reducedMotion) {
            snap()
        } else {
            tween<Float>(durationMillis = spec.durationMillis)
        }
    val alpha by animateFloatAsState(
        targetValue = entry.alpha,
        animationSpec = if (spec.animatesAlpha) animationSpec else snap(),
        label = "card-stack-alpha",
    )
    val offset by animateFloatAsState(
        targetValue = entry.offset,
        animationSpec = if (spec.animatesHorizontalTranslation) animationSpec else snap(),
        label = "card-stack-horizontal-offset",
    )
    val verticalOffset by animateFloatAsState(
        targetValue = entry.verticalOffset,
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
