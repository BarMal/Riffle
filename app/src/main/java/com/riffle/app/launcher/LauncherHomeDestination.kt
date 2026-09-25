@file:Suppress("LongParameterList")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.riffle.app.launcher.notifications.AppStageShellState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.cards.AdaptiveStageInteractionContext
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneLayoutPolicy
import com.riffle.core.domain.launcher.cards.AdaptiveStagePaneMode
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.home.DockPosition

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
    val dockHost = rememberHomeDockHostState()
    val presentation = standardHomePresentation(state, widgetRenderers)
    // Single source of truth for where the one dock sits (#1205): the user's configured edge if any,
    // else the device class's template default. Every mode's content and the dock agree on it.
    val dockPosition =
        resolveDockPosition(state.homeLayout.dock.position, state.settingsLayoutDeviceClass.templateDockPosition)
    // Cards' stages feed both its surface and its reading of the dock, so they are reconciled here,
    // once -- the reconciler carries the previous snapshot, so a second one would keep its own history.
    val cardsShellState =
        if (state.homeLayout.viewMode.homeSurfaceKind() == HomeSurfaceKind.CARDS) {
            rememberAppStageShellState(state)
        } else {
            null
        }
    val dockInterpreter =
        cardsShellState?.let { shellState ->
            cardsDockInterpreter(
                state = state,
                shellState = shellState,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
            )
        } ?: HomeDockInterpreter()

    Box(modifier = Modifier.fillMaxSize()) {
        // The one dock, outside the mode surface: composed at the same place whichever surface runs
        // below, so a mode switch keeps this instance and its position (#1205, Decision 2). What
        // differs by mode reaches it only through [dockInterpreter]. Composed first so the mode's
        // overlays cover it; the grid's own frame sits beneath it (see HOME_CONTENT_Z_INDEX).
        HomeDockHost(
            layout = state.homeLayout,
            installedApps = state.installedApps,
            presentation = presentation,
            position = dockPosition,
            hostState = dockHost,
            appIconLoader = appIconLoader,
            onAction = onAction,
            interpreter = dockInterpreter,
            haptics = haptics,
        )
        if (cardsShellState != null) {
            CardsHomeSurface(
                state = state,
                shellState = cardsShellState,
                dockHost = dockHost,
                dockPosition = dockPosition,
                appIconLoader = appIconLoader,
                adaptiveStageWindowLayout = adaptiveStageWindowLayout,
                adaptiveStageContext = adaptiveStageContext,
                onAdaptiveStageContextChanged = onAdaptiveStageContextChanged,
                onAction = onAction,
            )
        } else {
            StandardHomeSurface(
                state = state,
                presentation = presentation,
                appIconLoader = appIconLoader,
                widgetRenderers = widgetRenderers,
                haptics = haptics,
                dockHost = dockHost,
                onAction = onAction,
            )
        }
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

/**
 * Keeps the stage clear of the shared dock [HomeDockHost] draws over it, on
 * whichever physical edge the dock sits on -- an absolute edge, so this uses [absolutePadding]
 * rather than [Modifier.padding]'s start/end, which would mirror in RTL.
 */
private fun Modifier.dockInteractionPadding(
    position: DockPosition,
    extent: Dp,
): Modifier =
    when (position) {
        DockPosition.TOP -> absolutePadding(top = extent)
        DockPosition.BOTTOM -> absolutePadding(bottom = extent)
        DockPosition.LEFT -> absolutePadding(left = extent)
        DockPosition.RIGHT -> absolutePadding(right = extent)
    }

/**
 * The dynamic side means "a notification arrived", the same de-duplicated list grid mode draws (a
 * pinned app is on the static side and excluded here), but a tap brings the app's stage forward
 * instead of opening it. A pinned app's own stage is reached from its static icon.
 *
 * The merged "All notifications" entry is offered only on a wide (unfolded) layout and only when
 * opted in -- on a compact layout that view lives on the spine instead. Kept last, as the rail
 * kept it.
 */
private fun cardsDockDynamicEntries(
    state: LauncherShellState,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    selectedStageId: AppStageId?,
): List<DockDynamicEntry> {
    val showUnfoldedAllNotifications =
        adaptiveStageWindowLayout.showsUnfoldedCardsLayout() &&
            state.launcherSettings.cards.unfoldedShowAllNotifications
    return dockNotificationShelfState(
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
}

/**
 * Cards' reading of the shared dock: the dynamic entries select a stage, and a pinned icon brings
 * its app's stage forward when it has one (opening stays on the icon's long-press menu). Lives
 * here, with the rest of Cards, so the dock itself never knows about stages.
 */
private fun cardsDockInterpreter(
    state: LauncherShellState,
    shellState: AppStageShellState,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
): HomeDockInterpreter {
    val selectedStageId =
        shellState.snapshot.selectedStage?.id.takeUnless { adaptiveStageContext.allNotificationsSelected }
    return HomeDockInterpreter(
        dynamicEntries =
            cardsDockDynamicEntries(state, adaptiveStageWindowLayout, adaptiveStageContext, selectedStageId),
        // The merged page is not a stage, so there is no action to send: it is a choice about what
        // this surface shows, which the interaction context holds.
        onShowAllNotifications = {
            onAdaptiveStageContextChanged(adaptiveStageContext.copy(allNotificationsSelected = true))
        },
        staticTapBehaviour =
            DockStaticTapBehaviour.SelectStageIfBacked(
                shellState.snapshot.stages.map { stage -> stage.id }.toSet(),
            ),
        // The stages already are the notifications, so the expanded shelf is a panel-only mini-home
        // surface here -- the card row would just show them a second time.
        showExpandedNotificationShelf = false,
    )
}

@Composable
private fun CardsHomeSurface(
    state: LauncherShellState,
    shellState: AppStageShellState,
    dockHost: HomeDockHostState,
    dockPosition: DockPosition,
    appIconLoader: AppIconLoader,
    adaptiveStageWindowLayout: AdaptiveStageWindowLayout?,
    adaptiveStageContext: AdaptiveStageInteractionContext,
    onAdaptiveStageContextChanged: (AdaptiveStageInteractionContext) -> Unit,
    onAction: (LauncherShellAction) -> Unit,
) {
    // The shared dock is drawn by HomeDockHost over this surface; the stage lays out inside the
    // room it reserves, on whichever edge it sits.
    val dockInteractionExtent =
        dockHost.reservedExtent(state.homeLayout.visibleTo(state.installedApps), dockPosition)

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveStageAppStageSurface(
            state = state,
            shellState = shellState,
            modifier = Modifier.dockInteractionPadding(dockPosition, dockInteractionExtent),
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
    presentation: StandardHomePresentation,
    appIconLoader: AppIconLoader,
    widgetRenderers: LauncherWidgetRenderers,
    haptics: LauncherHaptics,
    dockHost: HomeDockHostState,
    onAction: (LauncherShellAction) -> Unit,
) {
    StandardHome(
        layout = state.homeLayout,
        installedApps = state.installedApps,
        interactions = StandardHomeInteractions(haptics = haptics),
        presentation = presentation,
        appIconLoader = appIconLoader,
        widgetPreviewImageLoader = widgetRenderers.previewImageLoader,
        deviceClass = state.settingsLayoutDeviceClass,
        dockHost = dockHost,
        onAction = onAction,
    )
}

/** What the home grid and the shared dock both render from, whichever mode is showing. */
private fun standardHomePresentation(
    state: LauncherShellState,
    widgetRenderers: LauncherWidgetRenderers,
): StandardHomePresentation =
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
    )

internal fun cardsPanelInsetPolicy(state: LauncherShellState): HomeInsetPolicy {
    return homeInsetPolicy(state.launcherSettings.appearance)
}
