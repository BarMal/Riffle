package com.riffle.app.launcher

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.rss.backgroundRefreshStatus
import com.riffle.app.launcher.rss.chipLabel
import com.riffle.app.launcher.rss.explanation
import com.riffle.core.domain.launcher.settings.FeedBackgroundRunRecord
import com.riffle.core.domain.launcher.settings.FeedRefreshIntervalOption
import com.riffle.core.domain.launcher.settings.RssSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val RSS_BACKGROUND_STATUS_TAG = "rss-background-status"

/**
 * Opt-in background refresh (issue #1393): the interval choice (Off by default), the Wi-Fi-only and
 * charging-only options, and a status line with the last background run. The constraint switches are shown
 * only once an interval is chosen, so a user who leaves it Off sees nothing to configure.
 */
@Composable
internal fun RssBackgroundRefreshSettings(
    settings: RssSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsChoiceRow(
            title = "Background refresh",
            subtitle = settings.refreshInterval.explanation(),
            options = FeedRefreshIntervalOption.entries,
            selected = settings.refreshInterval,
            onSelect = { option -> onAction(LauncherShellAction.SelectRssRefreshInterval(option)) },
            label = { option -> option.chipLabel() },
        )
        if (settings.refreshInterval.isEnabled) {
            SettingsSwitchRow(
                title = "Only on Wi-Fi",
                subtitle = "Skip background refresh on mobile data or other metered networks.",
                checked = settings.backgroundWifiOnly,
                onCheckedChange = { enabled -> onAction(LauncherShellAction.SetRssBackgroundWifiOnly(enabled)) },
            )
            SettingsSwitchRow(
                title = "Only while charging",
                subtitle = "Skip background refresh unless the device is plugged in.",
                checked = settings.backgroundChargingOnly,
                onCheckedChange = { enabled -> onAction(LauncherShellAction.SetRssBackgroundChargingOnly(enabled)) },
            )
            RssBackgroundStatusLine()
        }
    }
}

/** Reads the persisted last-run record off the main thread and shows it; hidden where no coordinator is wired. */
@Composable
private fun RssBackgroundStatusLine() {
    val coordinator = LocalFeedRefreshCoordinator.current ?: return
    var record by remember(coordinator) { mutableStateOf<FeedBackgroundRunRecord?>(null) }
    LaunchedEffect(coordinator) {
        record = withContext(Dispatchers.IO) { coordinator.lastBackgroundRun() }
    }
    SettingsTextColumn(
        modifier = Modifier.testTag(RSS_BACKGROUND_STATUS_TAG),
        title = "Background status",
        subtitle = backgroundRefreshStatus(record) { at -> DateUtils.getRelativeTimeSpanString(at) },
    )
}
