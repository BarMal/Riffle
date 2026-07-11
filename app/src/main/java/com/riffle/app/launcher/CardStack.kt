package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry

@Composable
internal fun CardStack(
    entries: List<CardStackLayoutEntry>,
    modifier: Modifier = Modifier,
    content: @Composable (CardStackLayoutEntry) -> Unit,
) {
    Box(modifier = modifier) {
        entries.forEach { entry ->
            Box(
                modifier =
                    Modifier
                        .zIndex(entry.order.toFloat())
                        .graphicsLayer {
                            translationX = entry.offset
                            translationY = entry.verticalOffset
                            scaleX = entry.scale
                            scaleY = entry.scale
                            alpha = entry.alpha
                            rotationZ = entry.rotationDegrees
                        },
            ) {
                content(entry)
            }
        }
    }
}
