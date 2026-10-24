package com.riffle.app.launcher.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.pool.READ_ONLY_HOME_NOTICE
import com.riffle.app.launcher.workspace.WorkspacePreviewTheme
import com.riffle.core.domain.launcher.FirstRunStatus
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughExit
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often "still loading" is re-checked while the home holds its edits (normally for well under a second). */
private const val LOADING_RECHECK_MILLIS = 250L

/**
 * Onboarding over the shell (drawn after the home and the workspace editor, so its Back wins).
 *
 * Order with the first-run flow: the Home-role card ("Set up Riffle") comes first. Onboarding decides what to show
 * only once the workspaces have loaded and that card is gone (dismissed, or Riffle is the Home app) and no Home-role
 * request is in flight, so the two never stack and the Home-role request is never delayed. Then a fresh install gets
 * the walkthrough, an existing user the offer card (on the home only).
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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val latestOnAction by rememberUpdatedState(onAction)
    WorkspacePreviewTheme(state, workspace = null) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (val shown = surface) {
                OnboardingSurface.Offer ->
                    if (state.destination == ShellDestination.HOME) {
                        OnboardingOfferCard(
                            onAccept = host.controller::acceptOffer,
                            onDismiss = host.controller::dismissOffer,
                            modifier =
                                Modifier
                                    .align(Alignment.TopCenter)
                                    .windowInsetsPadding(WindowInsets.safeDrawing)
                                    .padding(RiffleSpacing.l),
                        )
                    }
                is OnboardingSurface.Walkthrough ->
                    WalkthroughLayer(host, shown, state) { session, ui ->
                        finish(host, session, ui, state, snackbar, scope) { latestOnAction(it) }
                    }
                OnboardingSurface.None -> Unit
            }
            SnackbarHost(
                hostState = snackbar,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(RiffleSpacing.l),
            )
        }
    }
}

@Composable
private fun WalkthroughLayer(
    host: OnboardingHost,
    surface: OnboardingSurface.Walkthrough,
    state: LauncherShellState,
    onFinished: (WalkthroughSession, WalkthroughUi) -> Unit,
) {
    val latestState by rememberUpdatedState(state)
    val session =
        remember(host, surface) {
            WalkthroughSession(
                contextOf = { WalkthroughContexts.build(host.inputs(surface, latestState)) },
                launchPermission = host.launchPermission,
            )
        }
    val ui by session.ui.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, session) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_PAUSE -> session.onHostPaused()
                    Lifecycle.Event.ON_RESUME -> session.onHostResumed()
                    else -> Unit
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(session, ui.state.exit) {
        when (ui.state.exit) {
            WalkthroughExit.Cancelled -> host.controller.walkthroughCancelled()
            WalkthroughExit.Finished -> onFinished(session, ui)
            null -> Unit
        }
    }
    BackHandler { session.dispatch(WalkthroughAction.Back) }
    val version by host.version.collectAsState()
    var block by remember { mutableStateOf(host.applyBlock()) }
    LaunchedEffect(version) {
        block = host.applyBlock()
        while (block == ApplyBlock.LOADING) {
            delay(LOADING_RECHECK_MILLIS)
            block = host.applyBlock()
        }
    }
    val apps = remember(state.installedApps) { WalkthroughContexts.pickableApps(state.installedApps) }
    WalkthroughScreen(
        model =
            WalkthroughScreenModel(
                ui = ui,
                apps = apps,
                applyBlock = block,
                reducedMotion = state.launcherSettings.motion.reducedMotion,
            ),
        onAction = session::dispatch,
        onCheckAgain = session::checkPermissionAgain,
    )
}

/**
 * Applies the finished walkthrough. Applied: onboarding is completed, the home is shown, and the snackbar offers
 * Undo (the returned `undo` values). Refused (read-only home, still loading, a stale state): back on Review with the
 * reason, nothing written.
 */
@Suppress("LongParameterList")
private fun finish(
    host: OnboardingHost,
    session: WalkthroughSession,
    ui: WalkthroughUi,
    state: LauncherShellState,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onAction: (LauncherShellAction) -> Unit,
) {
    when (val outcome = host.applier.apply(ui.state, ui.context, state.homeLayoutSet.activeKey.deviceClass)) {
        is WalkthroughOutcome.Applied -> {
            host.controller.walkthroughCompleted()
            host.onApplied()
            onAction(LauncherShellAction.OpenHome)
            scope.launch {
                val result =
                    snackbar.showSnackbar(
                        message = WalkthroughText.READY,
                        actionLabel = WalkthroughText.UNDO,
                        duration = SnackbarDuration.Long,
                    )
                if (result == SnackbarResult.ActionPerformed) {
                    val undone = host.applier.undo(outcome)
                    if (undone) host.onApplied()
                    snackbar.showSnackbar(if (undone) WalkthroughText.UNDONE else WalkthroughText.UNDO_FAILED)
                }
            }
        }
        WalkthroughOutcome.ReadOnly -> refused(session, snackbar, scope, READ_ONLY_HOME_NOTICE)
        WalkthroughOutcome.Loading -> refused(session, snackbar, scope, WalkthroughText.LOADING)
        is WalkthroughOutcome.Rejected ->
            refused(
                session,
                snackbar,
                scope,
                WalkthroughText.problem(outcome.problems.firstOrNull() ?: WalkthroughProblem.InvalidWorkspace),
            )
    }
}

private fun refused(
    session: WalkthroughSession,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    message: String,
) {
    session.reopenReview()
    scope.launch { snackbar.showSnackbar(message) }
}
