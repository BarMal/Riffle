package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout

@Composable
fun StandardHome(
    layout: HomeLayout,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeToolbar(onAction = onAction)
        WorkspaceGrid(
            grid = layout.selectedPage.grid,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        )
        PageIndicator(
            pageCount = layout.pages.size,
            selectedPageIndex = layout.selectedPageIndex,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Dock(dock = layout.dock)
    }
}

@Composable
private fun HomeToolbar(onAction: (LauncherShellAction) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { onAction(LauncherShellAction.OpenSearch) },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(text = "Search")
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenAppDrawer) }) {
            Text(text = "Apps")
        }
        TextButton(onClick = { onAction(LauncherShellAction.OpenSettings) }) {
            Text(text = "Settings")
        }
    }
}

@Composable
private fun WorkspaceGrid(
    grid: GridDimensions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        repeat(grid.rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(grid.columns) {
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PageIndicator(
    pageCount: Int,
    selectedPageIndex: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            val color =
                if (index == selectedPageIndex) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                }

            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(color),
            )
        }
    }
}

@Composable
private fun Dock(dock: DockModel) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(dock.capacity) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(16.dp),
                        ),
            )
        }
    }
}
