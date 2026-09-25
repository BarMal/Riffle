package com.riffle.app.launcher

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.settings.SystemAnimationSignals
import com.riffle.core.domain.launcher.settings.withSystemReducedMotion

/** Platform animation state, behind an interface so the launcher can be tested without a device. */
interface SystemAnimationSignalsSource {
    fun current(): SystemAnimationSignals

    /** Calls [onChanged] whenever the platform animation state may have changed; close the handle to stop. */
    fun observe(onChanged: () -> Unit): AutoCloseable
}

/**
 * Reads `Settings.Global.ANIMATOR_DURATION_SCALE`, which the developer "Animator duration scale" option and
 * the accessibility "Remove animations" switch both write, and observes it with a [ContentObserver].
 */
internal class AndroidSystemAnimationSignalsSource(
    private val contentResolver: ContentResolver,
) : SystemAnimationSignalsSource {
    override fun current(): SystemAnimationSignals =
        SystemAnimationSignals(
            animatorDurationScale =
                runCatching {
                    Settings.Global.getFloat(
                        contentResolver,
                        Settings.Global.ANIMATOR_DURATION_SCALE,
                        SystemAnimationSignals.DEFAULT_ANIMATOR_DURATION_SCALE,
                    )
                }.getOrDefault(SystemAnimationSignals.DEFAULT_ANIMATOR_DURATION_SCALE),
        )

    override fun observe(onChanged: () -> Unit): AutoCloseable {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    onChanged()
                }
            }
        contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        return AutoCloseable { contentResolver.unregisterContentObserver(observer) }
    }
}

/** The platform's current reduced-motion state, updated live while the caller is composed. */
@Composable
internal fun rememberSystemReducedMotion(source: SystemAnimationSignalsSource? = null): Boolean {
    val context = LocalContext.current
    val signalsSource =
        remember(source, context) {
            source ?: AndroidSystemAnimationSignalsSource(context.applicationContext.contentResolver)
        }
    var systemReducedMotion by remember(signalsSource) { mutableStateOf(signalsSource.current().reducedMotion) }
    DisposableEffect(signalsSource) {
        systemReducedMotion = signalsSource.current().reducedMotion
        val registration = signalsSource.observe { systemReducedMotion = signalsSource.current().reducedMotion }
        onDispose { registration.close() }
    }
    return systemReducedMotion
}

/** Resolves the effective reduced-motion state for every surface below the shell, without persisting it. */
internal fun LauncherShellState.withSystemReducedMotion(systemReducedMotion: Boolean): LauncherShellState {
    val settings = launcherSettings.withSystemReducedMotion(systemReducedMotion)
    return if (settings === launcherSettings) this else copy(launcherSettings = settings)
}
