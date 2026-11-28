package com.riffle.app.launcher.summon

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.LauncherHaptics
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.LauncherWidgetRenderers
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glance.labelRes
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.EffectiveFeatures
import com.riffle.core.domain.launcher.summon.RingBounds
import com.riffle.core.domain.launcher.summon.SummonEvent
import com.riffle.core.domain.launcher.summon.SummonGestureConfig
import com.riffle.core.domain.launcher.summon.SummonKeepGesture
import com.riffle.core.domain.launcher.summon.SummonPhase
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.SummonTimings
import kotlinx.coroutines.delay

private val RING_RADIUS = 108.dp
private val SLOT_CLEARANCE = 34.dp
private val DEAD_ZONE = 28.dp
private val PUSH_THROUGH = 48.dp

/**
 * Home while Summon is on (`FeatureSettings.summon`): the top-of-screen area at rest, and the ring under the thumb on
 * a hold. [SummonController] runs the gesture; this draws it. Nothing here is stored: the arranged Home and the Dock
 * are untouched underneath and come back when Summon is turned off.
 */
@Composable
internal fun SummonHome(
    state: LauncherShellState,
    features: EffectiveFeatures,
    appIconLoader: AppIconLoader,
    haptics: LauncherHaptics,
    widgetRenderers: LauncherWidgetRenderers,
    onAction: (LauncherShellAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.launcherSettings.summon
    val model = rememberSummonRingModel(state)
    // A ring saved before collections may still hold Home folders: saving it copies them into collections, once.
    LaunchedEffect(settings.slots) {
        if (settings.slots.any { it is SummonSlot.Folder }) onAction(LauncherShellAction.SetSummonSettings(settings))
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val config = rememberSummonGestureConfig(maxWidth, maxHeight, insets, settings.timings, settings.keepGesture)
        val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
        val latest = rememberUpdatedState(SummonInputs(haptics, onAction, model, config))
        val controller =
            remember {
                SummonController(
                    haptics = { latest.value.haptics },
                    onAction = { latest.value.onAction },
                    model = { latest.value.model },
                    config = { latest.value.config },
                )
            }

        // Time-based steps (arming, previews, opening a folder, going back) wait for the reducer's next deadline.
        LaunchedEffect(controller.phase) {
            val deadline = controller.nextDeadline() ?: return@LaunchedEffect
            delay((deadline - SystemClock.uptimeMillis()).coerceAtLeast(0))
            controller.dispatch(SummonEvent.Tick(SystemClock.uptimeMillis()))
        }

        val ringActions = rememberSummonAccessibilityActions(model, onAction) { controller.menuOpen = true }
        val description = stringResource(R.string.summon_surface_description)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { controller.root = it }
                    .semantics {
                        contentDescription = description
                        customActions = ringActions
                    }.summonInput(controller),
        ) {
            val open = controller.phase as? SummonPhase.Open
            val calm = open == null && !controller.menuOpen && controller.keptCard == null
            AnimatedVisibility(visible = calm, enter = fadeIn(), exit = fadeOut()) {
                SummonHeaderArea(
                    header = settings.header,
                    homeLayout = state.homeLayout,
                    widgetViewFactory = widgetRenderers.viewFactory,
                    onAction = onAction,
                    modifier = Modifier.padding(insets).padding(RiffleSpacing.l),
                )
            }
            if (open != null) {
                SummonOpenRing(open, model, appIconLoader, state, controller, insets, heightPx)
            }
        }
        controller.keptCard?.let { card ->
            SummonKeptCard(
                entry = card,
                appIconLoader = appIconLoader,
                onAction = onAction,
                onDismiss = { controller.keptCard = null },
            )
        }
        if (controller.menuOpen) {
            SummonMenu(
                model = model,
                features = features,
                appIconLoader = appIconLoader,
                onAction = onAction,
                onDismiss = { controller.menuOpen = false },
            )
        }
    }
}

/** The ring under the thumb and the readout for what it is aimed at. */
@Composable
private fun SummonOpenRing(
    open: SummonPhase.Open,
    model: SummonRingModel,
    appIconLoader: AppIconLoader,
    state: LauncherShellState,
    controller: SummonController,
    insets: PaddingValues,
    heightPx: Float,
) {
    SummonRing(
        phase = open,
        model = model,
        appIconLoader = appIconLoader,
        look =
            SummonRingLook(
                iconSize = state.launcherSettings.summon.iconSizeDp.dp,
                reducedMotion = state.launcherSettings.motion.reducedMotion,
            ),
        onDockIconPlaced = controller::placeDockIcon,
        hint = ringHint(open).takeIf { state.launcherSettings.summon.showHints },
        heightPx = heightPx,
    )
    summonReadout(open, model, state.launcherSettings.summon.keepGesture)?.let { readout ->
        SummonReadoutCard(
            readout = readout,
            placement = summonReadoutPlacement(open.layout.centre.y, heightPx),
            live = open.live || open.overReadout,
            showHint = state.launcherSettings.summon.showHints,
            appIconLoader = appIconLoader,
            insets = insets,
            onPlaced = controller::placeReadout,
        )
    }
}

/** What the controller reads at each event, kept current across recompositions. */
private class SummonInputs(
    val haptics: LauncherHaptics,
    val onAction: (LauncherShellAction) -> Unit,
    val model: SummonRingModel,
    val config: SummonGestureConfig,
)

@Composable
private fun rememberSummonRingModel(state: LauncherShellState): SummonRingModel {
    val context = LocalContext.current
    val favouritesLabel = stringResource(R.string.summon_favourites)
    val appInfoLabel = stringResource(R.string.menu_app_info)
    val settings = state.launcherSettings.summon
    return remember(
        settings,
        state.homeLayout,
        state.installedApps,
        state.notificationGroupsByApp,
        state.appShortcutsByApp,
        favouritesLabel,
        appInfoLabel,
    ) {
        SummonRingModel.from(
            settings = settings,
            layout = state.homeLayout,
            installed = state.installedApps,
            glanceLabel = { kind -> context.getString(kind.labelRes()) },
            favouritesLabel = favouritesLabel,
            notifications = state.notificationGroupsByApp,
            shortcuts = state.appShortcutsByApp,
            appInfoLabel = appInfoLabel,
        )
    }
}

/** Sizes in pixels for this window: slot centres stay inside the system bars by half a slot. */
@Composable
private fun rememberSummonGestureConfig(
    width: Dp,
    height: Dp,
    insets: PaddingValues,
    timings: SummonTimings,
    keep: SummonKeepGesture,
): SummonGestureConfig {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    return remember(width, height, insets, timings, keep, density, layoutDirection, touchSlop) {
        with(density) {
            val half = SUMMON_SLOT_SIZE.toPx() / 2f
            SummonGestureConfig(
                timings = timings,
                bounds =
                    RingBounds(
                        left = insets.calculateLeftPadding(layoutDirection).toPx() + half,
                        top = insets.calculateTopPadding().toPx() + half,
                        right = width.toPx() - insets.calculateRightPadding(layoutDirection).toPx() - half,
                        bottom = height.toPx() - insets.calculateBottomPadding().toPx() - half,
                    ),
                radius = RING_RADIUS.toPx(),
                clearance = SLOT_CLEARANCE.toPx(),
                deadZone = DEAD_ZONE.toPx(),
                pushThrough = PUSH_THROUGH.toPx(),
                touchSlop = touchSlop,
                keep = keep,
            )
        }
    }
}

/** What to do while nothing is aimed at; once something is, the readout says. */
@Composable
private fun ringHint(phase: SummonPhase.Open): String =
    stringResource(if (phase.path.isNotEmpty()) R.string.summon_hint_back else R.string.summon_hint_aim)

/** TalkBack's route to everything the ring holds, plus the menu, without the gesture (AGENTS.md: a non-gesture way). */
@Composable
private fun rememberSummonAccessibilityActions(
    model: SummonRingModel,
    onAction: (LauncherShellAction) -> Unit,
    openMenu: () -> Unit,
): List<CustomAccessibilityAction> {
    val context = LocalContext.current
    val menuLabel = stringResource(R.string.summon_menu_open)
    val settingsLabel = stringResource(R.string.summon_menu_settings)
    return remember(model, menuLabel, settingsLabel) {
        summonApps(model).map { app ->
            CustomAccessibilityAction(context.getString(R.string.summon_open_app, app.label)) {
                onAction(LauncherShellAction.LaunchApp(app.identity))
                true
            }
        } +
            CustomAccessibilityAction(menuLabel) {
                openMenu()
                true
            } +
            CustomAccessibilityAction(settingsLabel) {
                onAction(LauncherShellAction.OpenSettings)
                true
            }
    }
}

/** Every app the ring reaches at its top level, Favourites included, once each. */
internal fun summonApps(model: SummonRingModel): List<SummonEntry.App> =
    (model.level(emptyList()).filterIsInstance<SummonEntry.App>() + model.favourites).distinctBy { it.key }
