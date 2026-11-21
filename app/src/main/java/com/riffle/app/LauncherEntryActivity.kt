package com.riffle.app

import android.app.Activity
import android.os.Bundle
import com.riffle.app.launcher.AndroidHomeRoleGateway
import com.riffle.app.launcher.implicitHomeIntent
import com.riffle.app.launcher.openAppIntent
import com.riffle.core.domain.launcher.entry.LauncherEntryTarget
import com.riffle.core.domain.launcher.entry.launcherEntryTarget

/**
 * The launcher icon's entry point (the app icon, the installer's "Open" button, `getLaunchIntentForPackage`). It draws
 * nothing and ends at once. As the default Home it asks the system for Home with an implicit Home intent, so the
 * launcher's task is created from a Home intent and typed as the home task (a task created from the icon's own intent
 * is a standard task, which the system's Home gesture has nothing to animate to). Otherwise it opens the app
 * explicitly, so it can still be tried and set up before it is the default.
 *
 * It has its own empty task affinity and is `noHistory`, so it never roots, joins or lingers in the launcher's task.
 * Nothing here is read at construction: the system builds an activity before it has a context.
 */
class LauncherEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val target = launcherEntryTarget(homeRoleHeld = AndroidHomeRoleGateway(this).isDefaultHome())
            val started =
                runCatching {
                    startActivity(
                        when (target) {
                            LauncherEntryTarget.HOME -> implicitHomeIntent()
                            LauncherEntryTarget.OPEN_APP -> openAppIntent(this)
                        },
                    )
                }.isSuccess
            // Home could not be requested: the app itself still opens.
            if (!started) runCatching { startActivity(openAppIntent(this)) }
        } finally {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
