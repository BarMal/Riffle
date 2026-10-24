package com.riffle.app.launcher

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.protectsWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The platform side of Settings > Privacy > "Hide launcher content in screenshots and recents": one call that
 * marks the window secure or not. Behind an interface so the policy and the sync are tested without a window.
 */
internal fun interface ScreenContentProtectionPort {
    fun setProtected(protected: Boolean)
}

/**
 * Sets `FLAG_SECURE` on [activity]'s window (blank screenshots, recordings, casting and recents thumbnail) and, on
 * Android 13 and later, also turns off the recents screenshot itself, so no stale thumbnail captured before the
 * flag was set can show. Clearing reverses both.
 */
internal class WindowScreenContentProtectionPort(
    private val activity: ComponentActivity,
) : ScreenContentProtectionPort {
    override fun setProtected(protected: Boolean) {
        if (protected) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!protected)
        }
    }
}

/** Whether the launcher window must be secure now: the setting is on and the surface can show item content. */
internal fun launcherScreenProtected(state: LauncherShellState): Boolean =
    state.launcherSettings.privacy.protectsWindow(state.destination)

/** The distinct protection decisions over [state], in order: what the sync applies. */
internal fun screenProtectionDecisions(state: Flow<LauncherShellState>): Flow<Boolean> =
    state.map(::launcherScreenProtected).distinctUntilChanged()

/**
 * Keeps the window's secure flag in step with the setting and the destination for as long as the activity exists
 * (not only while started, so a change made just before leaving still applies to the recents thumbnail).
 */
internal fun ComponentActivity.startScreenContentProtectionSync(
    state: StateFlow<LauncherShellState>,
    port: ScreenContentProtectionPort = WindowScreenContentProtectionPort(this),
) {
    lifecycleScope.launch {
        lifecycle.repeatOnLifecycle(Lifecycle.State.CREATED) {
            screenProtectionDecisions(state).collect(port::setProtected)
        }
    }
}
