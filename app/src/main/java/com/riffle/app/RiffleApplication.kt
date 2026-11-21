package com.riffle.app

import android.app.Application
import com.riffle.app.launcher.diagnostics.StartMilestones
import com.riffle.app.launcher.diagnostics.StartupTimeline

/**
 * Only stamps the first milestone of the start-up log (Settings > About > After-update starts): the first app code to
 * run in the process. It reads nothing, starts nothing and holds no state of its own.
 */
class RiffleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        StartupTimeline.mark(StartMilestones.APPLICATION)
    }
}
