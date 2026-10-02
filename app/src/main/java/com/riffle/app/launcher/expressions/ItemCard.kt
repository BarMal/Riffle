package com.riffle.app.launcher.expressions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction

/** At most this many actions are drawn as buttons; more would not fit a card at large font scales. */
private const val MAX_CARD_ACTIONS = 3

private const val BODY_MAX_LINES = 4

/** The text on an action button. */
internal fun ItemAction.label(): String =
    when (this) {
        is ItemAction.Open -> "Open"
        is ItemAction.Dismiss -> "Dismiss"
        is ItemAction.Reply -> "Reply"
        is ItemAction.Snooze -> "Snooze"
        is ItemAction.Custom -> label
    }

/**
 * The face shared by the Card and CardStack expressions: optional artwork, icon, title, subtitle,
 * body snippet, time and up to [MAX_CARD_ACTIONS] actions. The whole face is one tap target for
 * [onItemClick]; action buttons keep their own (Material) minimum target size. [hideEnabled] false hides the Hide
 * button and its accessibility actions (the stack passes it for every card but the focused one).
 */
@Composable
internal fun ItemCard(
    item: Item,
    environment: ExpressionEnvironment,
    onItemClick: (Item) -> Unit,
    onAction: (Item, ItemAction) -> Unit,
    modifier: Modifier = Modifier,
    hideEnabled: Boolean = true,
) {
    val hideMenu = rememberItemHideMenu(item).takeIf { hideEnabled }
    Surface(
        modifier = modifier,
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = RiffleElevation.level2,
        shadowElevation = RiffleElevation.level2,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onItemClick(item) }
                    .hideCustomActions(hideMenu)
                    .padding(RiffleSpacing.l),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            item.image?.let { image ->
                ItemImage(
                    handle = image,
                    loader = environment.imageLoader,
                    size = CardArtworkHeight,
                    shape = RiffleShapes.medium,
                    fillWidth = true,
                )
            }
            CardHeader(item = item, environment = environment, hideMenu = hideMenu)
            item.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = BODY_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            CardActions(item = item, onAction = onAction)
        }
    }
}

private val CardArtworkHeight = RiffleSpacing.xxxl * 2

@Composable
private fun CardHeader(
    item: Item,
    environment: ExpressionEnvironment,
    hideMenu: ItemHideMenu?,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item.icon?.let { icon ->
            ItemImage(handle = icon, loader = environment.imageLoader, size = RiffleSpacing.xxxl)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
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
                text = environment.timeFormatter.format(time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ItemHideButton(hideMenu)
    }
}

@Composable
private fun CardActions(
    item: Item,
    onAction: (Item, ItemAction) -> Unit,
) {
    val actions = item.actions.take(MAX_CARD_ACTIONS)
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        actions.forEach { action ->
            TextButton(onClick = { onAction(item, action) }) { Text(action.label()) }
        }
    }
}
