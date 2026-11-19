package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.apps.AppIdentity

/** One app of a [PairLaunch]: what to start and the name to tell the user if it cannot be started. */
data class PairApp(
    val identity: AppIdentity,
    val label: String,
)

/** Two apps to open side by side, [first] on the start (left) pane. */
data class PairLaunch(
    val first: PairApp,
    val second: PairApp,
)

/**
 * Opens two apps side by side (a pair of the Pairs stack, ADR 0003, Amendment 7). The platform implementation owns
 * the recipe (flags, delay) and the scheduling; the Stream only asks.
 */
interface PairLauncher {
    /**
     * Starts [pair]. Returns false, starting nothing, when a pair launch is already in flight (a second tap is
     * ignored); true when the launch was begun. A start that fails is reported to the user by the implementation and
     * never thrown.
     */
    fun launch(pair: PairLaunch): Boolean
}
