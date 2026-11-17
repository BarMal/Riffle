package com.riffle.core.domain.launcher.dock

import com.riffle.core.domain.launcher.home.AppPairItem

/**
 * Opens an app pair's two apps side by side. The platform implementation owns the recipe (flags, delay) and the
 * scheduling; the Dock only asks.
 */
interface PairLauncher {
    /**
     * Starts [pair]. Returns false, starting nothing, when a pair launch is already in flight (a second tap is
     * ignored); true when the launch was begun. A start that fails is reported to the user by the implementation and
     * never thrown.
     */
    fun launch(pair: AppPairItem): Boolean
}
