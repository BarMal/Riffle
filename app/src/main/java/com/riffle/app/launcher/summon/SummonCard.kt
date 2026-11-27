package com.riffle.app.launcher.summon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glance.GlanceWidget
import com.riffle.app.launcher.rememberAppIconColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import kotlin.math.roundToInt

private val CARD_MAX_WIDTH: Dp = 380.dp
private val CARD_ICON_SIZE: Dp = 40.dp
private val GLANCE_CARD_HEIGHT: Dp = 168.dp
private val MIN_TOUCH: Dp = 48.dp
private const val TEXT_LINES = 3

/**
 * A ring slot's card: an app's newest notification (with how many it has) or a glance itself. While the thumb rests
 * on the slot it previews ([live] once pushed through: the card's edge lights and its buttons show); kept after
 * release it becomes [interactive], its buttons and the glance's own controls taking taps.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SummonCard(
    entry: SummonEntry,
    live: Boolean,
    interactive: Boolean,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = entry as? SummonEntry.App
    val appColor = app?.let { rememberAppIconColor(it.identity, appIconLoader) }
    GlassSurface(
        modifier = modifier.widthIn(max = CARD_MAX_WIDTH).fillMaxWidth(),
        weight = GlassWeight.Regular,
        shape = RoundedCornerShape(28.dp),
        edgeTint = if (live || interactive) appColor ?: MaterialTheme.colorScheme.primary else Color.Unspecified,
    ) {
        Column(Modifier.padding(RiffleSpacing.l), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
            when (entry) {
                is SummonEntry.App -> AppCardBody(entry, appIconLoader)
                is SummonEntry.Glance ->
                    Box(Modifier.fillMaxWidth().height(GLANCE_CARD_HEIGHT)) {
                        GlanceWidget(kind = entry.kind, span = entry.kind.defaultSpan, interactive = { interactive })
                    }
                is SummonEntry.Folder, is SummonEntry.Favourites -> Unit
            }
            if (live || interactive) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
                    summonCardActions(entry).forEach { button ->
                        TextButton(
                            enabled = interactive,
                            onClick = { onAction(button.action) },
                            modifier = Modifier.sizeIn(minHeight = MIN_TOUCH),
                        ) { Text(stringResource(button.label)) }
                    }
                }
            }
            if (!interactive) {
                val hint = if (live) R.string.summon_card_hint_live else R.string.summon_card_hint_preview
                Text(stringResource(hint), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun AppCardBody(
    entry: SummonEntry.App,
    appIconLoader: AppIconLoader,
) {
    val group = entry.notifications ?: return
    val top = group.notifications.firstOrNull()
    Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        LauncherAppIcon(entry.identity, entry.label, appIconLoader, Modifier.size(CARD_ICON_SIZE), CircleShape)
        Column {
            Text(entry.label, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.summon_card_count, group.count), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (top != null) {
        if (top.title.isNotBlank()) Text(top.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        if (top.text.isNotBlank()) {
            Text(
                top.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = TEXT_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A card kept after release: in thumb reach at the bottom, interactive, and closed by a tap anywhere outside it. Its
 * buttons run their action and close it.
 */
@Composable
internal fun SummonKeptCard(
    entry: SummonEntry,
    appIconLoader: AppIconLoader,
    onAction: (LauncherShellAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val closeLabel = stringResource(R.string.summon_card_close)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = onDismiss,
                ).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(RiffleSpacing.l),
        contentAlignment = Alignment.BottomCenter,
    ) {
        SummonCard(
            entry = entry,
            live = true,
            interactive = true,
            appIconLoader = appIconLoader,
            onAction = { action ->
                onDismiss()
                onAction(action)
            },
        )
    }
}
