package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.FolderItem

@Composable
internal fun FolderPreviewIcon(
    folder: FolderItem,
    appIconLoader: AppIconLoader,
    iconSizeDp: Int,
) {
    if (folder.items.isEmpty()) {
        EmptyFolderPreviewIcon(iconSizeDp)
    } else {
        FilledFolderPreviewIcon(
            folder = folder,
            appIconLoader = appIconLoader,
            iconSizeDp = iconSizeDp,
        )
    }
}

@Composable
private fun EmptyFolderPreviewIcon(iconSizeDp: Int) {
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier =
            Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(EMPTY_FOLDER_CORNER_RADIUS_DP.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = EMPTY_FOLDER_ALPHA))
                .drawBehind {
                    drawRoundRect(
                        color = outlineColor,
                        style =
                            Stroke(
                                width = EMPTY_FOLDER_STROKE_WIDTH_DP.dp.toPx(),
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        intervals =
                                            floatArrayOf(
                                                EMPTY_FOLDER_DASH_DP.dp.toPx(),
                                                EMPTY_FOLDER_GAP_DP.dp.toPx(),
                                            ),
                                    ),
                            ),
                    )
                },
    )
}

@Composable
private fun FilledFolderPreviewIcon(
    folder: FolderItem,
    appIconLoader: AppIconLoader,
    iconSizeDp: Int,
) {
    val layout = folderPreviewLayout(iconSizeDp)
    Column(
        modifier =
            Modifier
                .size(iconSizeDp.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(layout.paddingDp.dp),
        verticalArrangement = Arrangement.spacedBy(layout.spacingDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        folder.items.take(FOLDER_PREVIEW_ICON_COUNT).chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(layout.spacingDp.dp)) {
                rowItems.forEach { shortcut ->
                    LauncherAppIcon(
                        identity = shortcut.appIdentity,
                        label = shortcut.label,
                        iconLoader = appIconLoader,
                        modifier = Modifier.size(layout.childIconSizeDp.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                }
            }
        }
    }
}

internal data class FolderPreviewLayout(
    val paddingDp: Int,
    val spacingDp: Int,
    val childIconSizeDp: Int,
)

internal fun folderPreviewLayout(iconSizeDp: Int): FolderPreviewLayout {
    val paddingDp = (iconSizeDp / 10).coerceAtLeast(1)
    val spacingDp = (iconSizeDp / 22).coerceAtLeast(1)
    return FolderPreviewLayout(
        paddingDp = paddingDp,
        spacingDp = spacingDp,
        childIconSizeDp = ((iconSizeDp - (paddingDp * 2) - spacingDp) / 2).coerceAtLeast(1),
    )
}

private const val FOLDER_PREVIEW_ICON_COUNT = 4
private const val EMPTY_FOLDER_STROKE_WIDTH_DP = 2
private const val EMPTY_FOLDER_DASH_DP = 8
private const val EMPTY_FOLDER_GAP_DP = 6
private const val EMPTY_FOLDER_CORNER_RADIUS_DP = 12
private const val EMPTY_FOLDER_ALPHA = 0.72f
