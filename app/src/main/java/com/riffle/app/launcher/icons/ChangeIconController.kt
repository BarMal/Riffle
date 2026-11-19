package com.riffle.app.launcher.icons

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.riffle.core.domain.launcher.apps.AppIdentity

/** The app whose icon or name is being changed, and its own name. */
internal data class ChangeIconTarget(
    val identity: AppIdentity,
    val label: String,
)

/**
 * Holds which app the "Change icon" sheet is open for. The long-press menus of every surface only ask for it
 * (`LauncherShellAction.OpenChangeIcon`); the shell opens the one sheet over whatever is showing.
 */
internal class ChangeIconController {
    var target: ChangeIconTarget? by mutableStateOf(null)
        private set

    fun open(
        identity: AppIdentity,
        label: String,
    ) {
        target = ChangeIconTarget(identity, label)
    }

    fun close() {
        target = null
    }
}

internal val LocalChangeIconController = staticCompositionLocalOf<ChangeIconController?> { null }
