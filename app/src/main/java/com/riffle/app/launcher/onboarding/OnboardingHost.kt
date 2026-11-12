package com.riffle.app.launcher.onboarding

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.HomeLayoutRuntime
import com.riffle.core.domain.launcher.onboarding.SourcePermission

/**
 * What the shell needs to run onboarding: the process's [controller], whether this install looked fresh at start
 * ([freshInstall]), and the existing explicit permission flows ([launchPermission], called only from an "Allow" tap in
 * the Stream step).
 */
class OnboardingHost internal constructor(
    internal val controller: OnboardingController,
    internal val freshInstall: () -> Boolean,
    internal val launchPermission: (SourcePermission) -> Unit,
)

/** Settings > Set up Riffle again; null where the shell runs no onboarding (tests, previews). */
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
internal fun androidOnboardingHost(
    context: Context,
    homeLayoutRuntime: HomeLayoutRuntime,
    workspaces: CachedWorkspaceRepository,
    launchPermission: (SourcePermission) -> Unit,
): OnboardingHost =
    OnboardingHost(
        controller = OnboardingProcess.controller(context),
        freshInstall = { homeLayoutRuntime.nothingStoredAtStart && workspaces.foundStored == false },
        launchPermission = launchPermission,
    )
