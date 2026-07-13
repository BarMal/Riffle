package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.zIndex
import com.riffle.core.domain.launcher.cards.CardStackLayoutEntry

@Composable
internal fun CardStack(
    entries: List<CardStackLayoutEntry>,
    modifier: Modifier = Modifier,
    content: @Composable (CardStackLayoutEntry) -> Unit,
) {
    Box(
        modifier =
            modifier.semantics {
                isTraversalGroup = true
            },
    ) {
        entries.forEach { entry ->
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
