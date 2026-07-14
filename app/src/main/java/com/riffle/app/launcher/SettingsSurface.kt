package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SettingsSurface(
    state: SettingsSurfaceState,
    initialPage: SettingsPage = SettingsPage.MAIN,
    onAction: (LauncherShellAction) -> Unit,
) {
    val selectedPage = remember(initialPage) { mutableStateOf(initialPage) }
    val pageScrollStates =
        remember {
            SettingsPage.entries.associateWith { ScrollState(initial = 0) }
        }

    BackHandler(enabled = selectedPage.value != SettingsPage.MAIN) {
        selectedPage.value = SettingsPage.MAIN
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            SettingsPageHeader(
                title = selectedPage.value.title,
                appVersionLabel = state.appVersionLabel,
                showBack = selectedPage.value != SettingsPage.MAIN,
                onBack = { selectedPage.value = SettingsPage.MAIN },
                onAction = onAction,
            )
            Spacer(modifier = Modifier.height(24.dp))
            SettingsPageContent(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                        .align(Alignment.CenterHorizontally)
                        .testTag(SETTINGS_PAGE_CONTENT_TEST_TAG)
                        .verticalScroll(pageScrollStates.getValue(selectedPage.value)),
                state = state,
                page = selectedPage.value,
                onPageSelected = { page -> selectedPage.value = page },
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ColumnScope.SettingsPageHeader(
    title: String,
    appVersionLabel: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = SETTINGS_PAGE_MAX_WIDTH_DP.dp)
                .align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                text = appVersionLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showBack) {
                TextButton(onClick = onBack) {
                    SettingsButtonText(text = "Back")
                }
            }
            TextButton(onClick = { onAction(LauncherShellAction.OpenDefaultHome) }) {
                SettingsButtonText(text = "Home")
            }
        }
    }
}

@Composable
internal fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 8.dp),
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                content()
            }
        }
    }
}

private const val SETTINGS_PAGE_MAX_WIDTH_DP = 840
internal const val SETTINGS_PAGE_CONTENT_TEST_TAG = "settings-page-content"
