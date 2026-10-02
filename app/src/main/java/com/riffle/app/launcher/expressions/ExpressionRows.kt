package com.riffle.app.launcher.expressions

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item

/** Formats an item's timestamp for trailing metadata. Injectable so screenshots do not depend on the clock. */
fun interface ExpressionTimeFormatter {
    fun format(epochMillis: Long): String
}

/** "5 min. ago" style text measured against the current time. */
object RelativeExpressionTimeFormatter : ExpressionTimeFormatter {
    override fun format(epochMillis: Long): String =
        DateUtils
            .getRelativeTimeSpanString(
                epochMillis,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
}

/** Leading icon size in list-like rows. */
private val RowIconSize = RiffleSpacing.xxxl

/** A standard list row: optional icon, title, optional subtitle, optional trailing time. */
@Composable
internal fun ItemRow(
    item: Item,
    loader: ExpressionImageLoader,
    timeFormatter: ExpressionTimeFormatter,
    onClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hideMenu = rememberItemHideMenu(item)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ExpressionMinTouchTarget)
                .clickable(role = Role.Button) { onClick(item) }
                .hideCustomActions(hideMenu)
                .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon -> ItemImage(handle = icon, loader = loader, size = RowIconSize) }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayTitle(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item.timeEpochMillis?.let { time ->
            Text(
                text = timeFormatter.format(time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemHideButton(hideMenu)
    }
}

/** A section heading row, exposed to accessibility services as a heading so they can jump between sections. */
@Composable
internal fun SectionHeading(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s)
                .semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * A text-first index entry: the title with its body snippet running on inline after it, so many
 * entries fit on screen. Optional small icon and trailing time.
 */
@Composable
internal fun IndexRow(
    item: Item,
    loader: ExpressionImageLoader,
    timeFormatter: ExpressionTimeFormatter,
    onClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor = MaterialTheme.colorScheme.onSurface
    val snippetColor = MaterialTheme.colorScheme.onSurfaceVariant
    val hideMenu = rememberItemHideMenu(item)
    val title = item.displayTitle()
    val snippet = item.body?.trim()?.takeIf { it.isNotEmpty() }
    val text =
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = titleColor)) { append(title) }
            if (snippet != null) {
                withStyle(SpanStyle(color = snippetColor)) { append("  $snippet") }
            }
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ExpressionMinTouchTarget)
                .clickable(role = Role.Button) { onClick(item) }
                .hideCustomActions(hideMenu)
                .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon -> ItemImage(handle = icon, loader = loader, size = RiffleSpacing.xxl) }
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        item.timeEpochMillis?.let { time ->
            Text(
                text = timeFormatter.format(time),
                style = MaterialTheme.typography.labelMedium,
                color = snippetColor,
            )
        }
        ItemHideButton(hideMenu)
    }
}
