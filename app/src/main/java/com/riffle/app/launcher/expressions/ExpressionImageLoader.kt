package com.riffle.app.launcher.expressions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/**
 * Resolves an [ItemImageHandle] to pixels. The domain only carries the opaque handle; expressions
 * ask this interface and never decode bitmaps themselves.
 *
 * Implementations must be main-safe: [load] is called from composition, so any file, network or
 * decode work has to hop to a background dispatcher inside the implementation.
 */
interface ExpressionImageLoader {
    /** The image for [handle] at roughly [sizePx] on its longest edge, or null if it cannot be resolved. */
    suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap?
}

/** Loads nothing, so every image draws its placeholder. The default until a real loader is injected. */
object NoExpressionImageLoader : ExpressionImageLoader {
    override suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap? = null
}

/**
 * Synchronous stand-in for previews and screenshot tests: every handle becomes a flat circle in a
 * colour derived from its key, so the same handle always draws the same and runs are reproducible.
 */
class FakeExpressionImageLoader : ExpressionImageLoader {
    override suspend fun load(
        handle: ItemImageHandle,
        sizePx: Int,
    ): ImageBitmap {
        val side = sizePx.coerceAtLeast(1)
        val bitmap = ImageBitmap(side, side)
        val paint = Paint().apply { color = PALETTE[handle.key.hashCode().mod(PALETTE.size)] }
        val radius = side / 2f
        Canvas(bitmap).drawCircle(Offset(radius, radius), radius, paint)
        return bitmap
    }

    private companion object {
        val PALETTE =
            listOf(
                Color(0xFF1A73E8),
                Color(0xFFD93025),
                Color(0xFF188038),
                Color(0xFFF9AB00),
                Color(0xFF9334E6),
                Color(0xFF12B5CB),
            )
    }
}

/**
 * An item's icon or image at [size], clipped to [shape]. A quiet surface-tone placeholder stands in
 * until (or unless) the loader returns. Decorative by default: the row or card that contains it
 * carries the description.
 */
@Composable
internal fun ItemImage(
    handle: ItemImageHandle?,
    loader: ExpressionImageLoader,
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    shape: Shape = RiffleShapes.small,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val image by produceState<ImageBitmap?>(initialValue = null, handle, loader, sizePx) {
        value = handle?.let { loader.load(it, sizePx) }
    }
    val base = modifier.size(size).clip(shape)
    val loaded = image
    if (loaded != null) {
        Image(
            bitmap = loaded,
            contentDescription = contentDescription,
            modifier = base,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = base.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
