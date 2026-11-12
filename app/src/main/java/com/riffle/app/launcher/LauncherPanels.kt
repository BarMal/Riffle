package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppDrawerProfileFilter
import com.riffle.core.domain.launcher.designsystem.GlassWeight

internal fun shouldShowAppDrawerClearFilters(
    query: String,
    profileFilter: AppDrawerProfileFilter,
): Boolean = query.isNotBlank() || profileFilter != AppDrawerProfileFilter.ALL

internal fun appDrawerClearFilterActions(): List<LauncherShellAction> =
    listOf(
        LauncherShellAction.AppDrawerQueryChanged(""),
        LauncherShellAction.AppDrawerProfileFilterSelected(AppDrawerProfileFilter.ALL),
    )

@Composable
fun LauncherPanel(
    title: String,
    onAction: (LauncherShellAction) -> Unit,
    showSettingsAction: Boolean = true,
    windowInsets: WindowInsets = WindowInsets.safeDrawing,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(windowInsets)
                .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            modifier =
                Modifier
                    .widthIn(max = PANEL_MAX_WIDTH_DP.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            weight = GlassWeight.Thick,
            shape = LocalLauncherPanelShape.current,
            tint = launcherPanelSurfaceColor(),
            contentColor = launcherPanelContentColor(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showSettingsAction) {
                            TextButton(onClick = { onAction(LauncherShellAction.OpenSettings) }) {
                                Text(text = "Settings")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
internal fun HomeInsetPolicy.safeDrawingPanelInsets(): WindowInsets {
    var insets = WindowInsets.safeDrawing
    if (!reserveStatusBar) {
        insets = insets.exclude(WindowInsets.statusBars)
    }
    if (!reserveNavigationBar) {
        insets = insets.exclude(WindowInsets.navigationBars)
    }
    return insets
}

private const val PANEL_MAX_WIDTH_DP = 840
