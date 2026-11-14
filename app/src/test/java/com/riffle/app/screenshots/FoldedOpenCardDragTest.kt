package com.riffle.app.screenshots

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.robolectric.annotation.Config

/** [OpenCardDragScenarios] on a folded phone. */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class FoldedOpenCardDragTest : OpenCardDragScenarios() {
    override val deviceClass = HomeLayoutDeviceClass.PHONE

    override fun streamState(reducedMotion: Boolean): LauncherShellState =
        ScreenshotFixtures.homeState().withHomePages(2).withManyApps().withAFullFirstPage()
            .copy(destination = ShellDestination.STREAM)
            .withMotion(reducedMotion)
}
