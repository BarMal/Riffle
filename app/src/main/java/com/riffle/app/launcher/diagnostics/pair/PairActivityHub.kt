package com.riffle.app.launcher.diagnostics.pair

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * The process-wide recorder of the app pair experiment and the one call that wires an activity to it. The scope is the
 * app's own, so the time-boxed watch survives the launcher being stopped by the very launch it is watching.
 */
object PairActivityHub {
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    private var appContext: Context? = null
    private var activityRef = WeakReference<Activity>(null)

    val recorder: PairLaunchRecorder by lazy { PairLaunchRecorder(scope) }

    /** Whether the launcher activity is resumed, for the scripted run. */
    val launcherState = PairLauncherState()

    /** The scripted run, at the same app-level scope as the recorder so it outlives the Settings page. */
    val scriptRunner: PairScriptRunner by lazy {
        PairScriptRunner(
            scope,
            System::currentTimeMillis,
            launcherState,
            { text -> appContext?.let { Toast.makeText(it, text, Toast.LENGTH_SHORT).show() } },
            { id, change -> AppPairExperiment.shared.updateScript(id, change) },
        )
    }

    /** The newest installed activity if there is one, else [fallback]: what a start made now should run from. */
    fun startContext(fallback: Context): Context = activityRef.get() ?: fallback

    private val everything =
        object : PairActivitySignals {
            override fun onSignal(
                name: String,
                state: PairWindowState,
            ) {
                recorder.onSignal(name, state)
                launcherState.onSignal(name, state)
            }
        }

    /** Forwards [activity]'s lifecycle, multi-window and configuration callbacks to [signals]; removed on destroy. */
    fun install(
        activity: ComponentActivity,
        signals: PairActivitySignals = everything,
    ) {
        appContext = activity.applicationContext
        activityRef = WeakReference(activity)
        val configuration = Consumer<Configuration> { signals.report(activity, "onConfigurationChanged") }
        val multiWindow =
            Consumer<androidx.core.app.MultiWindowModeChangedInfo> {
                signals.report(activity, "onMultiWindowModeChanged(${it.isInMultiWindowMode})", it.isInMultiWindowMode)
            }
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> signals.report(activity, "onStart")
                    Lifecycle.Event.ON_RESUME -> signals.report(activity, "onResume")
                    Lifecycle.Event.ON_PAUSE -> signals.report(activity, "onPause")
                    Lifecycle.Event.ON_STOP -> signals.report(activity, "onStop")
                    Lifecycle.Event.ON_DESTROY -> {
                        activity.removeOnConfigurationChangedListener(configuration)
                        activity.removeOnMultiWindowModeChangedListener(multiWindow)
                    }
                    else -> Unit
                }
            }
        activity.addOnConfigurationChangedListener(configuration)
        activity.addOnMultiWindowModeChangedListener(multiWindow)
        activity.lifecycle.addObserver(observer)
        signals.report(activity, "installed")
    }

    // A diagnostics probe must never take the launcher down, so a failed read of the window is just "unknown".
    private fun PairActivitySignals.report(
        activity: Activity,
        name: String,
        multiWindow: Boolean? = null,
    ) {
        val state =
            runCatching {
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                val config = activity.resources.configuration
                PairWindowState(
                    multiWindow = multiWindow ?: activity.isInMultiWindowMode,
                    bounds =
                        "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}] " +
                            "${bounds.width()}x${bounds.height()} px " +
                            "(${config.screenWidthDp}x${config.screenHeightDp} dp)",
                )
            }.getOrDefault(PairWindowState())
        onSignal(name, state)
    }
}

/** Tracks whether the launcher activity is RESUMED from the callbacks the hub forwards. */
class PairLauncherState :
    PairActivitySignals,
    PairScriptLifecycle {
    private val state = MutableStateFlow(false)

    override val resumed: StateFlow<Boolean> = state

    override fun onSignal(
        name: String,
        state: PairWindowState,
    ) {
        when (name) {
            "onResume" -> this.state.value = true
            "onPause" -> this.state.value = false
        }
    }
}
