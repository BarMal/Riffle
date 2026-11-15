package com.riffle.app.launcher.stream

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.StackFit
import com.riffle.core.domain.launcher.stream.StreamCardSizes
import com.riffle.core.domain.launcher.stream.StreamCardStackSpec

/**
 * A stack that fits its room (device fixes, round 7, item 1): the sizes with a line budget (the text a notification
 * shows), the cards' heights (a media card's artwork gives height down to [MediaArtworkFloor]), how many strips peek.
 */
internal class FittedStack(
    val sizes: StreamCardSizes,
    val heights: List<Dp>,
    val peeks: Int,
)

/** The least height a media card is drawn at when the stack needs its room: its header, its deck, some artwork. */
internal val MediaArtworkFloor: Dp = 176.dp

/** Whether the strips the posture normally shows (three) still stand, or fewer fit. */
private val StripsWanted = StreamCardStackSpec.VISIBLE_PEEKS

/**
 * The stack's sizes, heights and strips for [areaHeight] (the page's room above its footer): as the posture draws it
 * when it fits; else the notification text gives lines (a line budget of 4, 3, 2, 1: whole lines, never a cut one),
 * then a media card's artwork gives height, and strips go when nothing can give more. The stack's top is then never
 * above the area's top ([StreamPageInsets.top] of air is kept on a phone that has some).
 */
@Composable
internal fun rememberFittedStack(
    entries: List<StackEntry>,
    cardWidth: Dp,
    areaHeight: Dp,
    insets: StreamPageInsets,
): FittedStack {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.bodyMedium
    val density = LocalDensity.current
    val you = stringResource(R.string.stream_notification_you)
    val sizes = LocalStreamCardSizes.current
    return remember(entries, cardWidth, areaHeight, insets, density, style, you, sizes) {
        val measure = BrowseMeasure(measurer, style, density, you)
        fitStack(entries, cardWidth, areaHeight, insets, measure, sizes)
    }
}

internal fun fitStack(
    entries: List<StackEntry>,
    cardWidth: Dp,
    areaHeight: Dp,
    insets: StreamPageInsets,
    measure: BrowseMeasure,
    sizes: StreamCardSizes,
): FittedStack {
    val scale = measure.density.fontScale.coerceIn(1f, MAX_FONT_SCALE_FOR_CARDS)
    val topRoom = if (insets.top == 0.dp) 0f else StreamCardSizes.TOP_ROOM_DP
    val room = areaHeight.value - insets.stackBottom.value - topRoom
    val behind = (entries.size - 1).coerceAtLeast(0)
    val wantedStrips = minOf(StripsWanted, behind)
    val levels =
        (sizes.maxBodyLines downTo 1).filter { it == sizes.maxBodyLines || it <= 4 }.ifEmpty { listOf(1) }
    var last: FittedStack? = null
    for (level in levels) {
        val budget = sizes.copy(maxBodyLines = level, maxThreadLines = minOf(sizes.maxThreadLines, level))
        val heights = browseHeights(entries, cardWidth, measure, budget)
        val wanted =
            budget.peeksFor(
                count = budget.maxPeeks + 1,
                areaHeightDp = areaHeight.value,
                frontHeightDp = heights.maxOfOrNull { it.value } ?: StreamCardStackSpec.CARD_HEIGHT_DP,
                bottomInsetDp = insets.stackBottom.value,
            ).coerceAtMost(behind)
        val floors =
            entries.mapIndexed { i, entry ->
                val media = ((entry as? ContentEntry)?.takeIf { !it.nextUp }?.content) is MediaContent
                if (media) minOf(heights[i].value, MediaArtworkFloor.value * scale) else heights[i].value
            }
        val fit = StackFit.fit(heights.map { it.value }, floors, budget.pitchDp, wanted, room)
        val fitted = FittedStack(budget, fit.heightsDp.map { it.dp }, fit.peeks)
        last = fitted
        if (fit.peeks >= wantedStrips.coerceAtMost(wanted)) return fitted
    }
    return checkNotNull(last)
}
