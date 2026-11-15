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
import com.riffle.core.domain.launcher.stream.ArticleContent
import com.riffle.core.domain.launcher.stream.BrowseCardHeight
import com.riffle.core.domain.launcher.stream.EventContent
import com.riffle.core.domain.launcher.stream.NotificationBody
import com.riffle.core.domain.launcher.stream.NotificationBrowseText
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.StreamCardActions
import com.riffle.core.domain.launcher.stream.StreamCardSizes

/** The largest font scale a card's fixed parts grow with. */
internal const val MAX_FONT_SCALE_FOR_CARDS = 1.8f

/** The size of the picture thumbnail at the trailing edge of a notification's browse card. */
internal val BrowseThumbnailSize: Dp = BrowseCardHeight.THUMBNAIL_DP.dp

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
    val sizes = LocalStreamCardSizes.current
    return remember(entries, cardWidth, density, style, you, sizes) {
        browseHeights(entries, cardWidth, BrowseMeasure(measurer, style, density, you), sizes)
    }
}

/** The heights [rememberBrowseHeights] gives, for a given [sizes] (so a line budget can be tried without composing). */
internal fun browseHeights(
    entries: List<StackEntry>,
    cardWidth: Dp,
    measure: BrowseMeasure,
    sizes: StreamCardSizes,
): List<Dp> {
    val scale = measure.density.fontScale.coerceIn(1f, MAX_FONT_SCALE_FOR_CARDS)
    return entries.map { entry ->
        val notification = (entry as? ContentEntry)?.takeIf { !it.nextUp }?.content as? NotificationContent
        if (notification == null) {
            // The posture's size for the kind: a card of text grows to the larger maximum, one of fixed content
            // (the player, the checklist) a little more so the larger text fits.
            sizes.cardHeightDp(entry.height.value, textDriven = entry.isTextDriven()).dp * scale
        } else {
            notificationBrowseHeight(notification, cardWidth, measure, scale, sizes)
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
    sizes: StreamCardSizes = StreamCardSizes.FOLDED,
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
                NotificationBrowseText.blocks(content, sizes).sumOf { block ->
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
        sizes = sizes,
    ).dp
}

/** The picture a browse card shows as its trailing thumbnail: the notification's, else its own layout's. */
internal val NotificationContent.browseThumbnail get() = picture ?: custom?.thumbnail

/** Whether the card's height is its text's (it grows to the larger maximum), not a design with fixed content. */
internal fun StackEntry.isTextDriven(): Boolean {
    val content = (this as? ContentEntry)?.takeIf { !it.nextUp }?.content ?: return false
    return content is NotificationContent || content is EventContent ||
        (content is ArticleContent && content.image == null)
}
