package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.HomeLabelSettings

@Composable
internal fun SearchWebGridItem(
    result: SearchGridResult.Web,
    labelSettings: HomeLabelSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    val metrics = HomeGridLayoutMetrics()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = metrics.homeItemContentHeightDp(labelSettings).dp)
                .clickable { onAction(result.action) }
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            modifier = Modifier.size(HOME_ICON_SIZE_DP.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "Web",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        WallpaperReadableLabel(
            text = result.label,
            settings = labelSettings,
        )
    }
}
