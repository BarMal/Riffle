package com.riffle.app.launcher.pool

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.FolderPreviewIcon
import com.riffle.app.launcher.HomeGridLayoutMetrics
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.WallpaperReadableLabel
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLabelSettings
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.workspace.PageHost
import kotlin.math.roundToInt

internal const val POOL_HOME_PAGE_TEST_TAG = "workspace-preview-pool-home"
internal const val POOL_HOME_EMPTY_TEST_TAG = "workspace-preview-pool-home-empty"
internal const val POOL_HOME_FOLDER_TEST_TAG = "workspace-preview-pool-home-folder"

internal const val POOL_HOME_EMPTY_TITLE = "No home items found"
internal const val POOL_HOME_EMPTY_BODY =
    "The standard home has no items for this layout yet. Add some on Home, then use Refresh home items."
internal const val POOL_HOME_WIDGET_PLACEHOLDER = "Widget placeholder"
internal const val POOL_HOME_FOLDER_CLOSE = "Close"

private const val ICON_CELL_FRACTION = 0.8f
private val WidgetCorner = 16.dp
private val FolderRowIcon = 40.dp

/**
 * The preview's read-only view of the user's real home items. [resolve] maps a `home.grid` page to the pool's
 * page (null when the pool has none), [onOpen] launches an app or shortcut, [onReimport] (when present) catches the
 * pool up with the standard home. Nothing here edits items.
 */
internal class PlacedHomeContent(
    val resolve: (PageHost) -> LauncherPage?,
    val iconLoader: AppIconLoader,
    val widgetViews: HomeWidgetViewFactory,
    val labelSettings: HomeLabelSettings,
    val onOpen: (AppShortcutItem) -> Unit,
    val onReimport: (() -> Unit)? = null,
)

/** One placed home page of the pool, or an honest notice when there is none. */
@Composable
internal fun PoolHomePage(
    page: LauncherPage?,
    home: PlacedHomeContent,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    if (page == null) {
        Column(
            modifier =
                modifier.fillMaxSize().padding(contentPadding).padding(RiffleSpacing.l)
                    .testTag(POOL_HOME_EMPTY_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = POOL_HOME_EMPTY_TITLE,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = POOL_HOME_EMPTY_BODY,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        PoolHomeGrid(page, home, modifier.fillMaxSize().padding(contentPadding))
    }
}

@Composable
private fun PoolHomeGrid(
    page: LauncherPage,
    home: PlacedHomeContent,
    modifier: Modifier = Modifier,
) {
    var openFolder by remember(page.id) { mutableStateOf<FolderItem?>(null) }
    BoxWithConstraints(modifier = modifier.testTag(POOL_HOME_PAGE_TEST_TAG), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val cellPx =
            HomeGridLayoutMetrics().cellSizePx(
                grid = page.grid,
                maxWidthPx = with(density) { maxWidth.toPx() },
                maxHeightPx = with(density) { maxHeight.toPx() },
            )
        val cell = with(density) { cellPx.toDp() }
        Box(modifier = Modifier.requiredSize(cell * page.grid.columns, cell * page.grid.rows)) {
            page.items.forEach { item ->
                item.placement?.let { at ->
                    key(item.id.value) {
                        Box(
                            modifier =
                                Modifier
                                    .offset(cell * at.cell.column, cell * at.cell.row)
                                    .size(cell * at.span.columns, cell * at.span.rows),
                            contentAlignment = Alignment.Center,
                        ) {
                            PoolHomeItem(item, cell, home) { openFolder = it }
                        }
                    }
                }
            }
        }
    }
    openFolder?.let { folder ->
        PoolFolderDialog(folder, home, onDismiss = { openFolder = null })
    }
}

@Composable
private fun PoolHomeItem(
    item: LauncherItem,
    cell: Dp,
    home: PlacedHomeContent,
    onOpenFolder: (FolderItem) -> Unit,
) {
    val iconSize = minOf(home.labelSettings.iconSizeDp.dp, cell * ICON_CELL_FRACTION)
    when (item) {
        is AppShortcutItem ->
            ItemColumn(item.label, onClick = { home.onOpen(item) }) {
                LauncherAppIcon(item.appIdentity, item.label, home.iconLoader, Modifier.size(iconSize))
                WallpaperReadableLabel(item.label, home.labelSettings)
            }
        is FolderItem ->
            ItemColumn(item.label, onClick = { onOpenFolder(item) }) {
                FolderPreviewIcon(item, home.iconLoader, iconSize.value.roundToInt())
                WallpaperReadableLabel(item.label, home.labelSettings)
            }
        is WidgetItem -> PoolWidgetCell(item, home.widgetViews)
    }
}

@Composable
private fun ItemColumn(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.clickable(onClickLabel = "Open $label", role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

/** The hosted widget view when the host can draw one, else a marked placeholder. */
@Composable
private fun PoolWidgetCell(
    widget: WidgetItem,
    factory: HomeWidgetViewFactory,
) {
    val context = LocalContext.current
    val view = remember(context, widget.appWidgetId, factory) { factory.createHostedWidgetView(context, widget) }
    DisposableEffect(view) { onDispose { (view?.parent as? ViewGroup)?.removeView(view) } }
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(2.dp)
                .clip(RoundedCornerShape(WidgetCorner))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val widthDp = maxWidth.value.roundToInt().coerceAtLeast(1)
        val heightDp = maxHeight.value.roundToInt().coerceAtLeast(1)
        LaunchedEffect(widget.appWidgetId, widthDp, heightDp) {
            factory.updateHostedWidgetSize(widget, widthDp, heightDp)
        }
        if (view == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(RiffleSpacing.s)) {
                Text(
                    text = widget.label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = POOL_HOME_WIDGET_PLACEHOLDER,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { view })
        }
    }
}

@Composable
private fun PoolFolderDialog(
    folder: FolderItem,
    home: PlacedHomeContent,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(POOL_HOME_FOLDER_TEST_TAG),
        title = { Text(folder.label) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                folder.items.forEach { entry ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Open ${entry.label}", role = Role.Button) {
                                    onDismiss()
                                    home.onOpen(entry)
                                }
                                .padding(vertical = RiffleSpacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
                    ) {
                        LauncherAppIcon(entry.appIdentity, entry.label, home.iconLoader, Modifier.size(FolderRowIcon))
                        Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(POOL_HOME_FOLDER_CLOSE) } },
    )
}
