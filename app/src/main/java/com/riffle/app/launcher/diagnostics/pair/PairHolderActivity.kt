package com.riffle.app.launcher.diagnostics.pair

import android.app.Activity
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import com.riffle.app.R

/**
 * The holder of the "Held trampoline" methods (round 3): an ordinary, resizable, opaque activity in its own task. It
 * resumes full-screen, starts the first app next to itself, waits until it is itself in multi-window mode, starts the
 * second app from the split and finishes; [PairHolderMachine] decides when, this class only feeds it events (through
 * [PairHolderSession]) and shows one line of text. Diagnostics only: a plain themed surface, no glass.
 */
class PairHolderActivity : Activity() {
    private var session: PairHolderSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(
            FrameLayout(this).apply {
                addView(
                    TextView(context).apply {
                        text = getString(R.string.app_pair_holder_text)
                        textSize = TEXT_SIZE_SP
                    },
                    FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER),
                )
            },
        )
        val request = AndroidHolderIntents.requestFrom(intent.extras)
        if (request == null) {
            finishAndRemoveTask()
            return
        }
        session =
            PairHolderSession(request, ActivityHolderHost(this), SystemClock::uptimeMillis) {
                AppPairExperiment.shared.onHolderReport(it)
            }.also { it.on(HolderEvent.Created) }
    }

    override fun onStart() {
        super.onStart()
        session?.on(HolderEvent.Started)
    }

    override fun onResume() {
        super.onResume()
        session?.on(HolderEvent.Resumed)
    }

    override fun onPause() {
        super.onPause()
        session?.on(HolderEvent.Paused)
    }

    override fun onStop() {
        super.onStop()
        session?.on(HolderEvent.Stopped)
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.on(HolderEvent.Destroyed)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        session?.on(HolderEvent.FocusChanged(hasFocus))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        session?.on(HolderEvent.ConfigurationChanged)
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        session?.on(HolderEvent.MultiWindowChanged(isInMultiWindowMode))
    }

    private companion object {
        const val TEXT_SIZE_SP = 18f
    }
}
