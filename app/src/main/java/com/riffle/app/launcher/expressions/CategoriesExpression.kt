package com.riffle.app.launcher.expressions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * The Categories expression (App Library style): one tonal card per group, each previewing up to
 * four icons in a 2x2 block under the group's name. Tapping an icon launches it; tapping the name
 * row calls [onGroupClick] so the host can open the whole group. Owns the vertical scroll axis.
 */
@Composable
fun CategoriesExpression(
    result: LensResult,
    onItemClick: (Item) -> Unit,
    modifier: Modifier = Modifier,
    onGroupClick: (ItemGroup) -> Unit = {},
    state: ExpressionState = ExpressionState.Ready,
    environment: ExpressionEnvironment = ExpressionEnvironment(),
) {
    val groups = remember(result) { result.asGroups().filter { it.items.isNotEmpty() } }
    ExpressionStateHost(
        state = state,
        isEmpty = groups.isEmpty(),
        reducedMotion = environment.reducedMotion,
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CategoryCardMinWidth),
            modifier = Modifier.fillMaxSize(),
            contentPadding = environment.contentPadding,
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
        ) {
            items(items = groups, key = { it.key }, contentType = { CARD_TYPE }) { group ->
                CategoryCard(
                    group = group,
                    loader = environment.imageLoader,
                    onItemClick = onItemClick,
                    onGroupClick = onGroupClick,
                )
            }
        }
    }
}

private val CategoryCardMinWidth = RiffleSpacing.xxxl * 4

private const val PREVIEW_COUNT = 4
private const val PREVIEW_COLUMNS = 2
private const val CARD_TYPE = "category"

@Composable
private fun CategoryCard(
    group: ItemGroup,
    loader: ExpressionImageLoader,
    onItemClick: (Item) -> Unit,
    onGroupClick: (ItemGroup) -> Unit,
) {
    val preview = group.items.take(PREVIEW_COUNT)
    val overflow = group.items.size - preview.size
    Surface(
        shape = RiffleShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = RiffleElevation.level1,
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            preview.chunked(PREVIEW_COLUMNS).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    rowItems.forEach { item -> CategoryIcon(item = item, loader = loader, onClick = onItemClick) }
                }
            }
            CategoryTitle(group = group, overflow = overflow, onClick = { onGroupClick(group) })
        }
    }
}

@Composable
private fun CategoryIcon(
    item: Item,
    loader: ExpressionImageLoader,
    onClick: (Item) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(ExpressionMinTouchTarget)
                .clickable(role = Role.Button) { onClick(item) }
                .semantics { contentDescription = item.displayTitle() },
        contentAlignment = Alignment.Center,
    ) {
        ItemImage(handle = item.icon, loader = loader, size = IconCellIconSize, shape = RiffleShapes.medium)
    }
}

@Composable
private fun CategoryTitle(
    group: ItemGroup,
    overflow: Int,
    onClick: () -> Unit,
) {
    val name = group.heading() ?: ExpressionText.HIDDEN_TITLE
    val text = if (overflow > 0) "$name  +$overflow" else name
    Text(
        text = text,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ExpressionMinTouchTarget)
                .clickable(onClickLabel = "Open $name", role = Role.Button, onClick = onClick)
                .padding(vertical = RiffleSpacing.s)
                .semantics { heading() },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
