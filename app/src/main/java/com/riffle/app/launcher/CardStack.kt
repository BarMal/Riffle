package com.riffle.app.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
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
        entries.forEach { entry ->
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
