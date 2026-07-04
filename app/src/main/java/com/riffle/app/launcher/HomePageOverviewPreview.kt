package com.riffle.app.launcher

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem

@Composable
internal fun PageOverviewPreview(
    page: LauncherPage,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
) {
    val previewItems = page.previewItems()

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PAGE_OVERVIEW_PREVIEW_HEIGHT_DP.dp)
                .clip(RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CORNER_RADIUS_DP.dp)),
        shape = RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CORNER_RADIUS_DP.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = PAGE_OVERVIEW_PREVIEW_SURFACE_ALPHA),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(PAGE_OVERVIEW_PREVIEW_PADDING_DP.dp),
        ) {
            val columns = page.grid.columns.coerceAtLeast(1)
            val rows = page.grid.rows.coerceAtLeast(1)
            val gap = PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP.dp
            val cellWidth = (maxWidth - (gap * (columns - 1))) / columns
            val cellHeight = (maxHeight - (gap * (rows - 1))) / rows

            PageOverviewGridBackground(
                columns = columns,
                rows = rows,
            )
            previewItems.forEach { previewItem ->
                val itemWidth = (cellWidth * previewItem.columns) + (gap * (previewItem.columns - 1))
                val itemHeight = (cellHeight * previewItem.rows) + (gap * (previewItem.rows - 1))

                Box(
                    modifier =
                        Modifier
                            .absoluteOffset(
                                x = (cellWidth + gap) * previewItem.cell.column,
                                y = (cellHeight + gap) * previewItem.cell.row,
                            )
                            .width(itemWidth)
                            .height(itemHeight)
                            .clip(RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CELL_CORNER_RADIUS_DP.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    PageOverviewPreviewItem(
                        item = previewItem.item,
                        appIconLoader = appIconLoader,
                        widgetViewFactory = widgetViewFactory,
                    )
                }
            }
        }
    }
}

@Composable
private fun PageOverviewGridBackground(
    columns: Int,
    rows: Int,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP.dp),
    ) {
        repeat(rows) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP.dp),
            ) {
                repeat(columns) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .aspectRatio(1f, matchHeightConstraintsFirst = true)
                                .clip(RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_CELL_CORNER_RADIUS_DP.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageOverviewPreviewItem(
    item: LauncherItem,
    appIconLoader: AppIconLoader,
    widgetViewFactory: HomeWidgetViewFactory,
) {
    when (item) {
        is AppShortcutItem ->
            LauncherAppIcon(
                identity = item.appIdentity,
                label = item.label,
                iconLoader = appIconLoader,
                modifier = Modifier.fillMaxSize().padding(PAGE_OVERVIEW_PREVIEW_ITEM_PADDING_DP.dp),
                shape = RoundedCornerShape(PAGE_OVERVIEW_PREVIEW_ICON_CORNER_RADIUS_DP.dp),
            )

        is FolderItem ->
            PageOverviewFolderPreview(
                folder = item,
                appIconLoader = appIconLoader,
            )

        is WidgetItem ->
            PageOverviewWidgetPreview(
                widget = item,
                widgetViewFactory = widgetViewFactory,
            )
    }
}

@Composable
private fun PageOverviewFolderPreview(
    folder: FolderItem,
    appIconLoader: AppIconLoader,
) {
    if (folder.items.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.56f)),
        )
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(PAGE_OVERVIEW_FOLDER_PADDING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_FOLDER_GAP_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        folder.items.take(PAGE_OVERVIEW_FOLDER_ICON_COUNT).chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(PAGE_OVERVIEW_FOLDER_GAP_DP.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowItems.forEach { shortcut ->
                    LauncherAppIcon(
                        identity = shortcut.appIdentity,
                        label = shortcut.label,
                        iconLoader = appIconLoader,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        shape = RoundedCornerShape(PAGE_OVERVIEW_FOLDER_ICON_CORNER_RADIUS_DP.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PageOverviewWidgetPreview(
    widget: WidgetItem,
    widgetViewFactory: HomeWidgetViewFactory,
) {
    val context = LocalContext.current
    val hostedWidgetView =
        remember(context, widget.appWidgetId, widgetViewFactory) {
            widgetViewFactory.createHostedWidgetView(context, widget)
        }

    DisposableEffect(hostedWidgetView) {
        onDispose {
            hostedWidgetView?.removeFromParent()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.tertiaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (hostedWidgetView == null) {
            Text(
                text = widget.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { hostedWidgetView },
                update = { view -> view.setOnLongClickListener(null) },
            )
        }
    }
}

private fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}

private const val PAGE_OVERVIEW_PREVIEW_HEIGHT_DP = 118
private const val PAGE_OVERVIEW_PREVIEW_CORNER_RADIUS_DP = 14
private const val PAGE_OVERVIEW_PREVIEW_PADDING_DP = 8
private const val PAGE_OVERVIEW_PREVIEW_CELL_GAP_DP = 3
private const val PAGE_OVERVIEW_PREVIEW_CELL_CORNER_RADIUS_DP = 3
private const val PAGE_OVERVIEW_PREVIEW_SURFACE_ALPHA = 0.68f
private const val PAGE_OVERVIEW_PREVIEW_ITEM_PADDING_DP = 2
private const val PAGE_OVERVIEW_PREVIEW_ICON_CORNER_RADIUS_DP = 6
private const val PAGE_OVERVIEW_FOLDER_PADDING_DP = 3
private const val PAGE_OVERVIEW_FOLDER_GAP_DP = 2
private const val PAGE_OVERVIEW_FOLDER_ICON_COUNT = 4
private const val PAGE_OVERVIEW_FOLDER_ICON_CORNER_RADIUS_DP = 4
