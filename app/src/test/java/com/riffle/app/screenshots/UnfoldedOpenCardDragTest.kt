package com.riffle.app.screenshots

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import org.robolectric.annotation.Config

/** [OpenCardDragScenarios] on an unfolded foldable, the stack in its column beside the Dock. */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class UnfoldedOpenCardDragTest : OpenCardDragScenarios() {
    override val deviceClass = HomeLayoutDeviceClass.FOLDABLE

    override fun streamState(reducedMotion: Boolean): LauncherShellState =
        ScreenshotFixtures.homeState(HomeLayoutDeviceClass.FOLDABLE, null).withHomePages(3).withManyApps()
            .withAFullFirstPage()
            .copy(destination = ShellDestination.STREAM)
            .withMotion(reducedMotion)
}
