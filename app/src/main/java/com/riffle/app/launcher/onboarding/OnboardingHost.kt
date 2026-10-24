package com.riffle.app.launcher.onboarding

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.HomeLayoutRuntime
import com.riffle.app.launcher.workspace.WorkspaceRuntime
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the shell needs to run onboarding: the process's [controller], the [applier] over the stores, how to read a
 * walkthrough's [inputs] now, whether this install looked fresh at start ([freshInstall]), and the existing explicit
 * permission flows ([launchPermission], called only from a "Continue" tap). [version] is the workspace repository's
 * change counter (the walkthrough re-checks "still loading" on it). [onApplied] follows an apply and an Undo.
 */
@Suppress("LongParameterList") // The host's whole wiring, built once by `androidOnboardingHost`.
class OnboardingHost internal constructor(
    internal val controller: OnboardingController,
    internal val applier: WalkthroughApplier,
    internal val stores: WalkthroughStores,
    internal val inputs: (OnboardingSurface.Walkthrough, LauncherShellState) -> WalkthroughInputs,
    internal val freshInstall: () -> Boolean,
    internal val launchPermission: (SourcePermission) -> Unit,
    internal val version: StateFlow<Int> = MutableStateFlow(0),
    internal val onApplied: () -> Unit = {},
) {
    /** Why "Build my home" is unavailable now, if it is. */
    internal fun applyBlock(): ApplyBlock? =
        when {
            stores.readOnly -> ApplyBlock.READ_ONLY
            stores.loading -> ApplyBlock.LOADING
            else -> null
        }
}

/** Settings > Workspaces > "Set up your home again"; null where the shell runs no onboarding (tests, previews). */
internal val LocalWalkthroughLauncher = staticCompositionLocalOf<(() -> Unit)?> { null }

/** One onboarding controller per process, so [OnboardingController.decideAtLaunch] runs once per process start. */
internal object OnboardingProcess {
    @Volatile
    private var instance: OnboardingController? = null

    fun controller(context: Context): OnboardingController =
        instance ?: synchronized(this) {
            instance ?: OnboardingController(SharedPreferencesOnboardingStore(context)).also { instance = it }
        }
}

/**
 * The platform wiring. Fresh install = neither the home layout store nor the workspace store held anything when this
 * process started ([HomeLayoutRuntime.nothingStoredAtStart], [CachedWorkspaceRepository.foundStored]); a read that
 * failed never counts as fresh. Nothing here reads storage or platform state until onboarding asks.
 */
@Suppress("LongParameterList")
internal fun androidOnboardingHost(
    context: Context,
    homeLayoutRuntime: HomeLayoutRuntime,
    workspaces: CachedWorkspaceRepository,
    runtime: () -> WorkspaceRuntime,
    icsFeeds: () -> List<IcsFeed>,
    launchPermission: (SourcePermission) -> Unit,
    onApplied: () -> Unit,
): OnboardingHost {
    val stores =
        RepositoryWalkthroughStores(
            workspaces = workspaces,
            pool = homeLayoutRuntime.pool,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            isReadOnly = { homeLayoutRuntime.readOnlyHome != null },
            holdsEdits = { homeLayoutRuntime.poolHome?.awaitingWorkspaces == true },
            flushPool = { homeLayoutRuntime.poolHome?.flushNow() ?: homeLayoutRuntime.pool.flush() },
        )
    return OnboardingHost(
        controller = OnboardingProcess.controller(context),
        applier = WalkthroughApplier(stores),
        stores = stores,
        inputs = { surface, state ->
            val layoutClass = state.homeLayoutSet.activeKey.deviceClass
            WalkthroughInputs(
                origin = surface.origin,
                firstRun = surface.firstRun,
                sources = runtime().sourceChoices(),
                workspaces = workspaces.load(),
                layoutClass = layoutClass,
                pool = homeLayoutRuntime.pool.pool(layoutClass),
                installedApps = state.installedApps,
                rssFeeds = state.launcherSettings.rss.feeds,
                icsFeeds = icsFeeds(),
                gestures = state.launcherSettings.gestures.homeGestures,
            )
        },
        freshInstall = { homeLayoutRuntime.nothingStoredAtStart && workspaces.foundStored == false },
        launchPermission = launchPermission,
        version = workspaces.version,
        onApplied = onApplied,
    )
}
