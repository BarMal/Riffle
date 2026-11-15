package com.riffle.app.launcher.diagnostics.pair

import android.app.Activity
import android.os.Bundle
import com.riffle.app.launcher.diagnostics.pair.AndroidPairStarter.Companion.toIntent

/**
 * A transparent, no-history activity in its own task for the "Trampoline" methods of the app pair experiment: it makes
 * the two starts of [trampolineSteps] from a window that is on screen, reports each call's outcome to
 * [AppPairExperiment.shared] and finishes. It shows nothing.
 */
class PairTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = AndroidPairStarter.requestFrom(intent.extras)
        if (request != null) {
            val outcomes =
                trampolineSteps(request).mapIndexed { index, step ->
                    attempt("trampoline step ${index + 1}: ${step.describe(if (index == 0) "first" else "second")}") {
                        startActivity(step.toIntent())
                    }
                }
            AppPairExperiment.shared.onTrampolineReport(outcomes)
        }
        finish()
        overridePendingTransition(0, 0)
    }
}
