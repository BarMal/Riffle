package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.RiffleProduct
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.HomeLayout

@Composable
fun LauncherShell(
    viewModel: LauncherShellViewModel,
    onAction: (LauncherShellAction) -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LauncherShellContent(
        state = state,
        onAction = onAction,
    )
}

@Composable
fun LauncherShellContent(
    state: LauncherShellState,
    onAction: (LauncherShellAction) -> Unit,
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            if (state.shouldShowDefaultHomePrompt) {
                DefaultHomePrompt(onAction = onAction)
            } else {
                StandardHome(layout = state.homeLayout)
            }
        }
    }
}

@Composable
private fun DefaultHomePrompt(onAction: (LauncherShellAction) -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = RiffleProduct.DISPLAY_NAME,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Choose Riffle as your default home app to continue.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = { onAction(LauncherShellAction.RequestDefaultHome) }) {
            Text(text = "Set as default")
        }
    }
}

@Composable
private fun StandardHome(layout: HomeLayout) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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

@Preview
@Composable
private fun DefaultHomePromptPreview() {
    LauncherShellContent(
        state = LauncherShellState(homeRoleStatus = HomeRoleStatus.NOT_DEFAULT_HOME),
        onAction = {},
    )
}

@Preview
@Composable
private fun EmptyHomePreview() {
    LauncherShellContent(
        state = LauncherShellState(firstRunStatus = FirstRunStatus.COMPLETE),
        onAction = {},
    )
}
