@file:Suppress("LongParameterList")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneLayoutPolicy
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneMode
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout

@Composable
fun HomeDestination(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers = LauncherWidgetRenderers(),
    haptics: LauncherHaptics = NoopLauncherHaptics,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout? = null,
    adaptiveStageContext: AdaptiveStageInteractionContext = AdaptiveStageInteractionContext(),
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    when (state.homeLayout.viewMode.homeSurfaceKind()) {
        HomeSurfaceKind.CARDS ->
            CardsHomeSurface(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                onAction = onAction,
            )

        HomeSurfaceKind.GRID ->
            StandardHomeSurface(
                state = state,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                onAction = onAction,
            )
    }
}

/**
 * Whether this window resolves to a wide (unfolded) Cards layout -- the multi-pane one, where the
 * merged All-notifications view has no spine to live on. Null (unmeasured, or previews) reads as not
 * wide, so the entry stays off until a real wide window is known.
 */
private fun AdaptiveStageWindowLayout?.showsUnfoldedCardsLayout(): Boolean {
    val mode = this?.let { window -> AdaptiveStagePaneLayoutPolicy().layoutFor(window).mode } ?: return false
    return mode == AdaptiveStagePaneMode.TWO_PANE || mode == AdaptiveStagePaneMode.THREE_PANE
}

@Composable
private fun CardsHomeSurface(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    val dockInteractionHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    // Reconciled once for the whole surface, so the dock and the stage agree on what exists -- the
    // reconciler carries the previous snapshot, so a second one would quietly keep its own history.
    val shellState = rememberAppStageShellState(state)
    val selectedStageId =
        shellState.snapshot.selectedStage?.id.takeUnless { adaptiveStageContext.allNotificationsSelected }
    // The dynamic side means "a notification arrived", the same de-duplicated list grid mode draws
    // (a pinned app is on the static side and excluded here), but a tap brings the app's stage
    // forward instead of opening it. A pinned app's own stage is reached from its static icon.
    // The merged "All notifications" entry is offered only on a wide (unfolded) layout and only
    // when opted in -- on a compact layout that view lives on the spine instead. Kept last, as the
    // rail kept it.
    val showUnfoldedAllNotifications =
        adaptiveStageWindowLayout.showsUnfoldedCardsLayout() &&
            state.launcherSettings.cards.unfoldedShowAllNotifications
    val dockDynamicEntries =
        dockNotificationShelfState(
            dock = state.homeLayout.visibleTo(state.installedApps).dock,
            groups = state.notificationGroupsByApp,
            notificationAccessStatus = state.notificationAccessStatus,
            apps = state.installedApps,
        ).dockNotificationCards().stageSelectingDockDynamicEntries(selectedStageId) +
            listOfNotNull(
                allNotificationsDockDynamicEntry(
                    isSelected = adaptiveStageContext.allNotificationsSelected,
                    badgeCount = state.notificationGroupsByApp.sumOf { group -> group.count },
                ).takeIf { showUnfoldedAllNotifications },
            )
    val dockStaticTapBehaviour =
        DockStaticTapBehaviour.SelectStageIfBacked(
            shellState.snapshot.stages.map { stage -> stage.id }.toSet(),
        )
    val dockInteractionHeight =
        maxOf(
            state.homeLayout.dockInteractionRegionHeightDp().dp,
            with(density) { dockInteractionHeightPx.intValue.toDp() },
        )

    Box(modifier = Modifier.fillMaxSize()) {
        // Cards mode reuses the standard Dock but must not show the standard grid pages (and any
        // icons placed on them) underneath TimeScape's own canvas -- see StandardHomeDockOnlySurface.
        StandardHomeDockOnlySurface(
            layout = state.homeLayout,
            installedApps = state.installedApps,
            interactions =
                StandardHomeInteractions(
                    haptics = haptics,
                    onDockInteractionHeightChanged = { heightPx ->
                        dockInteractionHeightPx.intValue = heightPx
                    },
                ),
            presentation =
                StandardHomePresentation(
                    notificationGroupsByApp = state.notificationGroupsByApp,
                    notificationAccessStatus = state.notificationAccessStatus,
                    installedApps = state.installedApps,
                    appShortcutsByApp = state.appShortcutsByApp,
                    homeGestures = state.launcherSettings.gestures.homeGestures,
                    dockGestures = state.launcherSettings.gestures.dockGestures,
                    reducedMotion = state.launcherSettings.motion.reducedMotion,
                    motionPerformanceTargetFps = state.launcherSettings.motion.performanceTargetFps,
                    widgetViewFactory = widgetRenderers.viewFactory,
                    homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
                    adaptiveStageAppearance = state.launcherSettings.cards.adaptiveStageAppearance,
                ),
            appIconLoader = appIconLoader,
            onAction = onAction,
            dynamicEntries = dockDynamicEntries,
            // The merged page is not a stage, so there is no action to send: it is a choice about
            // what this surface shows, which the interaction context holds.
            onShowAllNotifications = {
                onAdaptiveStageContextChanged(adaptiveStageContext.copy(allNotificationsSelected = true))
            },
            // In Cards a pinned app icon brings its stage forward rather than opening the app, when
            // it has a stage; opening stays on the icon's long-press menu.
            staticTapBehaviour = dockStaticTapBehaviour,
        )
        AdaptiveStageAppStageSurface(
            state = state,
            shellState = shellState,
            modifier = Modifier.padding(bottom = dockInteractionHeight),
            windowInsets = cardsPanelInsetPolicy(state).safeDrawingPanelInsets(),
            windowLayout = adaptiveStageWindowLayout,
            context = adaptiveStageContext,
            onContextChanged = onAdaptiveStageContextChanged,
            onAction = onAction,
            appIconLoader = appIconLoader,
        )
    }
}

@Composable
private fun StandardHomeSurface(
    state: LauncherShellState,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    onDockInteractionHeightChanged: (Int) -> Unit = {},
    onBottomControlsHeightChanged: (Int) -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
) {
    StandardHome(
        layout = state.homeLayout,
        installedApps = state.installedApps,
        interactions =
            StandardHomeInteractions(
                haptics = haptics,
                onDockInteractionHeightChanged = onDockInteractionHeightChanged,
                onBottomControlsHeightChanged = onBottomControlsHeightChanged,
            ),
        presentation =
            StandardHomePresentation(
                notificationGroupsByApp = state.notificationGroupsByApp,
                notificationAccessStatus = state.notificationAccessStatus,
                installedApps = state.installedApps,
                appShortcutsByApp = state.appShortcutsByApp,
                homeGestures = state.launcherSettings.gestures.homeGestures,
                dockGestures = state.launcherSettings.gestures.dockGestures,
                reducedMotion = state.launcherSettings.motion.reducedMotion,
                motionPerformanceTargetFps = state.launcherSettings.motion.performanceTargetFps,
                widgetViewFactory = widgetRenderers.viewFactory,
                widgetPicker =
                    StandardHomeWidgetPickerState(
                        providers = state.installedWidgetProviders,
                        profileContentVisibility = state.profileContentVisibility,
                        catalogStatus = state.widgetProviderCatalogStatus,
                        isOpen = state.isWidgetPickerOpen,
                        isTargetingDockPanel = state.isWidgetPickerTargetingDockPanel,
                    ),
                homeInsetPolicy = homeInsetPolicy(state.launcherSettings.appearance),
                adaptiveStageAppearance = state.launcherSettings.cards.adaptiveStageAppearance,
            ),
        appIconLoader = appIconLoader,
        widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
        deviceClass = state.settingsLayoutDeviceClass,
        onAction = onAction,
    )
}

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
