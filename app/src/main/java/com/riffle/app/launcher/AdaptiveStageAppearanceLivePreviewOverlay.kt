package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings

/**
 * The full real Cards surface ([AdaptiveStageAppStageSurface]), fed synthetic notification
 * content instead of the user's real notifications, and rendered as a full-screen layer over
 * whatever destination is already showing (the caller is expected to have already navigated to
 * Home) rather than the small, static, non-interactive illustration in the Appearance settings
 * page itself ([AdaptiveStageAppearancePreview]). Reusing the real surface -- rather than a
 * second, hand-rolled rendering path -- means the same drag/fling/settle/focus mechanics the
 * real Cards mode uses apply here too: those are already driven entirely by local composable
 * state inside [AdaptiveStageAppStageSurface] (see its own `focusedCardIdValue`/`CardStackController`
 * plumbing), not by [onAction], so a no-op [onAction] here still leaves the stack genuinely
 * scrollable -- just without persisting anything or performing real notification actions, since
 * there's no real notification behind any of this synthetic content to act on.
 */
@Composable
internal fun AdaptiveStageAppearanceLivePreviewOverlay(
    appearance: AdaptiveStageAppearanceSettings,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val previewState = remember(appearance) { adaptiveStageLivePreviewState(appearance) }

    Box(modifier = modifier.fillMaxSize()) {
        AdaptiveStageAppStageSurface(
            state = previewState,
            onAction = {},
        )
        Surface(
            modifier =
                Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp)
                    .fillMaxWidth(),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Preview -- sample content, not your real notifications",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                TextButton(onClick = onDismiss) {
                    SettingsButtonText(text = "Done")
                }
            }
        }
    }
}

private fun adaptiveStageLivePreviewState(appearance: AdaptiveStageAppearanceSettings): LauncherShellState {
    val app =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.preview"),
                    activityName = AppActivityName(".Preview"),
                    profile = AppProfile.personal(),
                ),
            label = "Preview",
        )
    val notifications =
        PREVIEW_NOTIFICATION_CONTENT.mapIndexed { index, (title, text) ->
            LauncherNotification(
                key = LauncherNotificationKey("live-preview-$index"),
                packageName = app.identity.packageName,
                profileId = app.identity.profile.id,
                title = title,
                text = text,
                postedAtEpochMillis = PREVIEW_NOTIFICATION_CONTENT.size - index.toLong(),
            )
        }
    return LauncherShellState(
        notificationAccessStatus = NotificationAccessStatus.GRANTED,
        installedApps = listOf(app),
        profileContentVisibility = mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
        notificationGroupsByApp =
            listOf(
                AppNotificationGroup(
                    packageName = app.identity.packageName,
                    profileId = app.identity.profile.id,
                    latestCategory = NotificationCategory.MESSAGE,
                    latestAgeBucket = NotificationAgeBucket.RECENT,
                    notifications = notifications,
                ),
            ),
        launcherSettings = LauncherSettings(cards = CardsSettings(adaptiveStageAppearance = appearance)),
    )
}

private val PREVIEW_NOTIFICATION_CONTENT =
    listOf(
        "Focus mode" to "This is what your newest card looks like.",
        "Earlier activity" to "Older cards fan out behind the focused one.",
        "Yesterday" to "Drag or fling to move through the stack.",
        "Last week" to "Cards further back fade toward the edge.",
        "Last month" to "Tap a background card to bring it forward.",
    )
