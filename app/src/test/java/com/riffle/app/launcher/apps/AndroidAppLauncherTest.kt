package com.riffle.app.launcher.apps

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidAppLauncherTest {
    @Test
    fun launcherLaunchFlagsStartAppsInTheirOwnTask() {
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
            LAUNCHER_LAUNCH_FLAGS,
        )
    }
}
