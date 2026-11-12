package com.riffle.app.launcher.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.onboarding.SourcePermission
import com.riffle.core.domain.launcher.settings.resolveLiquidGlass
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

/**
 * Onboarding over the shell (drawn after the home, so its Back wins).
 *
 * Order with the first-run flow: the Home-role card ("Set up Riffle") comes first. Onboarding decides what to show
 * only once the workspaces have loaded and that card is gone (dismissed, or Riffle is the Home app) and no Home-role
 * request is in flight, so the two never stack and the Home-role request is never delayed. A fresh install then gets
 * the four-step set-up; an existing user gets nothing, and finds it again in Settings.
 */
@Composable
internal fun OnboardingLayer(
    host: OnboardingHost,
    state: LauncherShellState,
    workspacesLoaded: Boolean,
    onAction: (LauncherShellAction) -> Unit,
) {
    val ready =
        workspacesLoaded && !state.shouldShowSetupCard && state.firstRunStatus != FirstRunStatus.REQUESTING_HOME_ROLE
    LaunchedEffect(host, ready) {
        if (ready) host.controller.decideAtLaunch(host.freshInstall)
    }
    val surface by host.controller.surface.collectAsState()
    if (surface != OnboardingSurface.SetUp) return
    val appearance = state.launcherSettings.appearance
    RiffleLauncherTheme(
        themeMode = appearance.themeMode,
        themeAccent = appearance.themeAccent,
        themeColors = appearance.themeColors,
        themeCornerStyle = appearance.themeCornerStyle,
        themeTypography = appearance.themeTypography,
        reducedTransparency = appearance.reducedTransparency,
        liquidGlass = state.launcherSettings.resolveLiquidGlass(),
    ) {
        val granted =
            remember(state.notificationAccessStatus, state.calendarAccessStatus) {
                PermissionGranted { permission ->
                    when (permission) {
                        SourcePermission.NOTIFICATION_ACCESS ->
                            state.notificationAccessStatus == NotificationAccessStatus.GRANTED
                        SourcePermission.CALENDAR -> state.calendarAccessStatus == CalendarAccessStatus.GRANTED
                        // Usage access has no status in the shell state; the Stream's Recents section shows it.
                        SourcePermission.USAGE_ACCESS -> false
                    }
                }
            }
        Box(modifier = Modifier.fillMaxSize()) {
            OnboardingScreen(
                granted = granted,
                onAllow = host.launchPermission,
                onFinished = {
                    host.controller.completed()
                    onAction(LauncherShellAction.OpenHome)
                },
                onSkip = {
                    host.controller.cancelled()
                    onAction(LauncherShellAction.OpenHome)
                },
            )
        }
    }
}
