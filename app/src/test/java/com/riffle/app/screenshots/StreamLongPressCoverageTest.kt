package com.riffle.app.screenshots

import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Test
import org.robolectric.annotation.Config

/** The Stream (folded, and its column beside Home unfolded). */
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamLongPressCoverageTest : LongPressCoverageBase() {
    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun homeWithTheStreamOpenUnfolded() {
        show(unfolded().copy(destination = ShellDestination.STREAM), streamShown = true)
        assertNoDeadPoint("Home with the Stream open, unfolded", probe())
    }

    @Test
    fun theStreamFolded() {
        show(folded(DockPosition.BOTTOM).copy(destination = ShellDestination.STREAM), streamShown = true)
        assertNoDeadPoint("the Stream, folded", probe())
    }
}
