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
) {
    if (folder.items.isEmpty()) {
        EmptyFolderPreviewIcon()
    } else {
        FilledFolderPreviewIcon(
            folder = folder,
            appIconLoader = appIconLoader,
        )
    }
}

@Composable
private fun EmptyFolderPreviewIcon() {
    val outlineColor = MaterialTheme.colorScheme.outline

    Box(
        modifier =
            Modifier
                .size(HOME_ICON_SIZE_DP.dp)
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
) {
    Column(
        modifier =
            Modifier
                .size(HOME_ICON_SIZE_DP.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        folder.items.take(FOLDER_PREVIEW_ICON_COUNT).chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                rowItems.forEach { shortcut ->
                    LauncherAppIcon(
                        identity = shortcut.appIdentity,
                        label = shortcut.label,
                        iconLoader = appIconLoader,
                        modifier = Modifier.size(17.dp),
                        shape = RoundedCornerShape(5.dp),
                    )
                }
            }
        }
    }
}

private const val FOLDER_PREVIEW_ICON_COUNT = 4
private const val EMPTY_FOLDER_STROKE_WIDTH_DP = 2
private const val EMPTY_FOLDER_DASH_DP = 8
private const val EMPTY_FOLDER_GAP_DP = 6
