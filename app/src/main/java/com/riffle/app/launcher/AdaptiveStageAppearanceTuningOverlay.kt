package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.DockPosition
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
 * Tuning the Cards appearance on the surface it actually shapes.
 *
 * The real [AdaptiveStageAppStageSurface] fills the screen, fed synthetic notification content
 * rather than the user's own -- tuning something this visible should not put real notifications on
 * screen. Over it sits the same editor the settings page uses, in a sheet that swipes up to reach
 * every control and down to get out of the way.
 *
 * The controls dispatch the real actions, so what is being previewed is the setting itself rather
 * than a copy of it: there is nothing to apply or discard, and the surface behind redraws as each
 * control moves. That is the whole point -- a change and its effect are visible at the same time,
 * which the page's small static illustration could never manage.
 */
@Composable
internal fun AdaptiveStageAppearanceTuningOverlay(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    var target by rememberSaveable { mutableStateOf(AdaptiveStageAppearanceEditorTarget.FOLDED) }
    var isSheetExpanded by rememberSaveable { mutableStateOf(true) }
    val previewState = adaptiveStageTuningPreviewState(state.appearanceFor(target))

    Box(modifier = modifier.fillMaxSize().testTag(APPEARANCE_TUNING_OVERLAY_TEST_TAG)) {
        AdaptiveStageAppStageSurface(
            state = previewState,
            onAction = {},
        )
        AppearanceTuningSheet(
            isExpanded = isSheetExpanded,
            onExpandedChange = { expanded -> isSheetExpanded = expanded },
            onDismiss = onDismiss,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            AdaptiveStageAppearanceEditor(
                state = state,
                target = target,
                onTargetChange = { next -> target = next },
                onAction = onAction,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

/**
 * The sheet the controls live in.
 *
 * Collapsed it is a handle and a title, so the surface behind is unobstructed while a change is
 * judged; expanded it is tall enough to work in but never the whole screen, because the point is to
 * see what the controls are doing. The swipe reuses [dockShelfGestureExpandedState] rather than
 * inventing a second sense of "pull this open", and the handle is clickable too so the sheet is
 * reachable without a drag.
 */
@Composable
private fun AppearanceTuningSheet(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val expandedHeight = maxHeight * APPEARANCE_SHEET_EXPANDED_FRACTION
        val height by animateDpAsState(
            targetValue = if (isExpanded) expandedHeight else APPEARANCE_SHEET_COLLAPSED_HEIGHT_DP.dp,
            label = "appearance-sheet-height",
        )

        Surface(
            modifier = Modifier.fillMaxWidth().height(height),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp,
        ) {
            // The controls stay composed while collapsed, measured to whatever the sheet has left
            // -- which is nothing -- so the sheet keeps the tab and scroll position it was on
            // rather than resetting every time it is swiped shut.
            Column(modifier = Modifier.fillMaxSize()) {
                AppearanceTuningSheetHandle(
                    isExpanded = isExpanded,
                    onExpandedChange = onExpandedChange,
                    onDismiss = onDismiss,
                )
                content()
            }
        }
    }
}

@Composable
private fun AppearanceTuningSheetHandle(
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnExpandedChange by rememberUpdatedState(onExpandedChange)
    val toggleLabel = if (isExpanded) "Hide appearance controls" else "Show appearance controls"

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag(APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG)
                .semantics { contentDescription = toggleLabel }
                .clickable { currentOnExpandedChange(!isExpanded) }
                .pointerInput(isExpanded) {
                    var dragPx = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragPx = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragPx += amount
                        },
                        onDragEnd = {
                            dockShelfGestureExpandedState(
                                isExpanded = isExpanded,
                                horizontalDragPx = 0f,
                                verticalDragPx = dragPx,
                                position = DockPosition.BOTTOM,
                            )?.let { expanded -> currentOnExpandedChange(expanded) }
                        },
                    )
                },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 8.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Sample content, not your real notifications",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onDismiss) {
                SettingsButtonText(text = "Done")
            }
        }
    }
}

/**
 * The surface behind, with content of its own.
 *
 * Both appearance fields are set, not just the folded one: the stage reads the unfolded appearance
 * instead on a wide window, which this overlay does not otherwise account for -- so tuning on such
 * a window without this would show the *other*, unedited appearance rather than the one in hand.
 */
private fun adaptiveStageTuningPreviewState(appearance: AdaptiveStageAppearanceSettings): LauncherShellState {
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
                key = LauncherNotificationKey("appearance-tuning-$index"),
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
        launcherSettings =
            LauncherSettings(
                cards = CardsSettings(adaptiveStageAppearance = appearance, unfoldedAppearance = appearance),
            ),
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

/** Tall enough to work in, short enough that the surface being tuned stays worth looking at. */
private const val APPEARANCE_SHEET_EXPANDED_FRACTION = 0.6f
private const val APPEARANCE_SHEET_COLLAPSED_HEIGHT_DP = 56

internal const val APPEARANCE_TUNING_OVERLAY_TEST_TAG = "appearance-tuning-overlay"
internal const val APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG = "appearance-tuning-sheet-handle"
