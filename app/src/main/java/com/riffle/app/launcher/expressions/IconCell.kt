package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item

/** Icon edge used by the icon expressions. */
internal val IconCellIconSize: Dp = RiffleSpacing.xxxl

/** Width of one cell in a row or grid: the icon plus room either side for a two-line label. */
internal val IconCellWidth: Dp = RiffleSpacing.xxxl + RiffleSpacing.xl

/**
 * One launchable icon with an optional label underneath. A cell without a visible label still
 * announces a name to accessibility services, so an icon-only row is never silent.
 */
@Composable
internal fun IconCell(
    item: Item,
    loader: ExpressionImageLoader,
    onClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
) {
    val hideMenu = rememberItemHideMenu(item)
    var hideOpen by remember { mutableStateOf(false) }
    val label = item.title?.takeIf { it.isNotBlank() }
    val visibleLabel = if (showLabel) label else null
    val announce =
        if (visibleLabel == null) {
            Modifier.semantics { contentDescription = label ?: ExpressionText.HIDDEN_TITLE }
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .clickWithHide(hideMenu, onShowMenu = { hideOpen = true }) { onClick(item) }
                .padding(RiffleSpacing.xs)
                .then(announce),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemImage(handle = item.icon, loader = loader, size = IconCellIconSize, shape = RiffleShapes.large)
        if (hideMenu != null) ItemHideDropdown(hideMenu, hideOpen) { hideOpen = false }
        if (visibleLabel != null) {
            Text(
                text = visibleLabel,
                modifier = Modifier.padding(top = RiffleSpacing.xs).widthIn(max = IconCellWidth),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
