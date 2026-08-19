package com.riffle.app.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import kotlinx.coroutines.launch

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
 *
 * [BottomSheetScaffold] rather than a hand-rolled sheet: it is the persistent (non-modal) one, so
 * the surface behind stays visible and interactive, and it brings the parts a hand-rolled version
 * quietly lacks -- a drag that tracks the finger, velocity-aware settling, and nested scroll, so
 * dragging the scrolling controls moves the sheet instead of doing nothing.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AdaptiveStageAppearanceTuningOverlay(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    var target by rememberSaveable { mutableStateOf(AdaptiveStageAppearanceEditorTarget.FOLDED) }
    val previewState = adaptiveStageTuningPreviewState(state.appearanceFor(target))
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val peekHeight =
        if (headerHeightPx > 0) {
            with(density) { headerHeightPx.toDp() }
        } else {
            APPEARANCE_SHEET_UNMEASURED_PEEK_HEIGHT_DP.dp
        }
    val scaffoldState =
        rememberBottomSheetScaffoldState(
            bottomSheetState =
                rememberStandardBottomSheetState(
                    initialValue = SheetValue.Expanded,
                    // Never hidden: collapsing leaves the header peeking, which is the way back.
                    skipHiddenState = true,
                ),
        )

    BottomSheetScaffold(
        modifier = modifier.testTag(APPEARANCE_TUNING_OVERLAY_TEST_TAG),
        scaffoldState = scaffoldState,
        // Measured rather than guessed: the header has to peek whole, and how tall it is depends on
        // the font scale. A fixed figure clips its button on a large one and leaves a gap on a small.
        sheetPeekHeight = peekHeight,
        // The scaffold's own handle slot wraps whatever it is given in a node that merges its
        // descendants, which would put the header's button out of reach of a screen reader. The
        // sheet drags from anywhere on it regardless of that slot, so the header sits in the content
        // and carries its own grab affordance.
        sheetDragHandle = null,
        sheetContent = {
            AppearanceTuningSheetHeader(
                sheetState = scaffoldState.bottomSheetState,
                onDismiss = onDismiss,
                modifier = Modifier.onSizeChanged { size -> headerHeightPx = size.height },
            )
            AdaptiveStageAppearanceEditor(
                state = state,
                target = target,
                onTargetChange = { next -> target = next },
                onAction = onAction,
                // Tall enough to work in, short enough that the surface being tuned stays worth
                // looking at -- the sheet takes its expanded height from what its content asks for.
                modifier = Modifier.fillMaxWidth().fillMaxHeight(APPEARANCE_SHEET_EXPANDED_FRACTION),
            )
        },
    ) {
        // The padding this hands back is how much the peeking sheet covers. Ignored on purpose:
        // the surface is the thing being judged, so it runs full-bleed underneath.
        AdaptiveStageAppStageSurface(
            state = previewState,
            onAction = {},
        )
    }
}

/**
 * What stays on screen when the sheet is swiped shut: the grab affordance, what the content behind
 * is, and the way out.
 *
 * The grab strip toggles on a tap as well, so the sheet is reachable without a gesture, and carries
 * the semantics for it either way. The drag itself belongs to the sheet, which drags from anywhere.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppearanceTuningSheetHeader(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val isExpanded = sheetState.currentValue == SheetValue.Expanded
    val toggleLabel = if (isExpanded) "Hide appearance controls" else "Show appearance controls"

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG)
                    .semantics { contentDescription = toggleLabel }
                    .clickable {
                        scope.launch {
                            if (isExpanded) sheetState.partialExpand() else sheetState.expand()
                        }
                    }
                    // Outside the pill so the target is the whole strip rather than 32dp of it.
                    .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 32.dp, height = 4.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp),
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

/**
 * What the sheet peeks by until its header has been measured.
 *
 * Only ever the first frame, and only ever unseen -- the sheet opens expanded, so nothing is
 * resting on this figure. It exists so the collapsed anchor is somewhere sensible rather than flush
 * with the bottom of the screen if a frame is drawn before the measurement lands.
 */
private const val APPEARANCE_SHEET_UNMEASURED_PEEK_HEIGHT_DP = 72

internal const val APPEARANCE_TUNING_OVERLAY_TEST_TAG = "appearance-tuning-overlay"
internal const val APPEARANCE_TUNING_SHEET_HANDLE_TEST_TAG = "appearance-tuning-sheet-handle"
