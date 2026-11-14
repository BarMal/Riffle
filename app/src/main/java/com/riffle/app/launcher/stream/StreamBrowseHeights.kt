package com.riffle.app.launcher.stream

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.BrowseCardHeight
import com.riffle.core.domain.launcher.stream.NotificationBody
import com.riffle.core.domain.launcher.stream.NotificationBrowseText
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamCardActions

/** The largest font scale a card's fixed parts grow with. */
internal const val MAX_FONT_SCALE_FOR_CARDS = 1.8f

/** The size of the picture thumbnail at the trailing edge of a notification's browse card. */
internal val BrowseThumbnailSize: Dp = BrowseCardHeight.THUMBNAIL_DP.dp

/** A block of a notification's browse text as it is written: a message says who wrote it first. */
internal fun NotificationBrowseText.Block.written(you: String): String =
    if (message) "${sender ?: you}: $text" else text

/**
 * How tall each card of a stack is drawn, [cardWidth] wide. A notification is as tall as what it shows
 * ([BrowseCardHeight]: its text measured at the width it is written at, its thumbnail, a live notification's buttons),
 * between the minimum and the maximum; every other card keeps the height of its kind. Worked out in composition from
 * the content and the width alone, so nothing waits for a layout pass and a moving stack measures nothing.
 */
@Composable
internal fun rememberBrowseHeights(
    entries: List<StackEntry>,
    cardWidth: Dp,
): List<Dp> {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val you = stringResource(R.string.stream_notification_you)
    return remember(entries, cardWidth, density, style, you) {
        val scale = density.fontScale.coerceIn(1f, MAX_FONT_SCALE_FOR_CARDS)
        entries.map { entry ->
            val notification = (entry as? ContentEntry)?.takeIf { !it.nextUp }?.content as? NotificationContent
            if (notification == null) {
                entry.height * scale
            } else {
                notificationBrowseHeight(notification, cardWidth, BrowseMeasure(measurer, style, density, you), scale)
            }
        }
    }
}

/** What measuring a card's text needs. */
internal class BrowseMeasure(
    val measurer: TextMeasurer,
    val style: TextStyle,
    val density: Density,
    val you: String,
)

/** The height of the browse card of [content], [cardWidth] wide. */
internal fun notificationBrowseHeight(
    content: NotificationContent,
    cardWidth: Dp,
    measure: BrowseMeasure,
    scale: Float,
): Dp {
    val hasThumbnail = content.browseThumbnail != null
    val textWidth =
        cardWidth - RiffleSpacing.l * 2 - if (hasThumbnail) BrowseThumbnailSize + RiffleSpacing.m else 0.dp
    val body = content.body
    val bodyDp =
        if (body is NotificationBody.Custom && body.content.embed) {
            EmbeddedBrowseMaxHeight.value
        } else {
            with(measure.density) {
                val widthPx = textWidth.roundToPx().coerceAtLeast(1)
                NotificationBrowseText.blocks(content).sumOf { block ->
                    measure.measurer.measure(
                        text = block.written(measure.you),
                        style = measure.style,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = block.maxLines,
                        constraints = Constraints(maxWidth = widthPx),
                    ).size.height
                }.toDp().value
            }
        }
    return BrowseCardHeight.notification(
        bodyDp = bodyDp,
        hasThumbnail = hasThumbnail,
        hasActions = StreamCardActions.onBrowseCard(content).isNotEmpty(),
        scale = scale,
    ).dp
}

/** The picture a browse card shows as its trailing thumbnail: the notification's, else its own layout's. */
internal val NotificationContent.browseThumbnail get() = picture ?: custom?.thumbnail
