package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.LocalStreamViewButtonRoom
import com.riffle.app.launcher.stream.StreamPageView
import com.riffle.app.launcher.stream.StreamViewButtonRoom
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamLayoutReducer
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Stream's view button stands at the top-end corner with no room reserved for it (a stack's geometry must not move
 * for a control): a page that would reach the corner says so, and the button is not drawn over it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class StreamViewButtonRoomPagesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val room = StreamViewButtonRoom()

    private fun pageOf(view: StreamView): StreamPage {
        val layout =
            StreamLayoutReducer.reduce(
                StreamLayout.DEFAULT,
                StreamLayoutAction.SetView(StreamSection.NOTIFICATIONS, view),
            )
        val results = StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) }
        return StreamPagePlanner.plan(layout, results).first { it.key == "notifications/chat:personal" }
    }

    private fun show(
        page: StreamPage,
        height: Dp,
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                CompositionLocalProvider(LocalStreamViewButtonRoom provides room) {
                    Box(modifier = Modifier.width(360.dp).height(height)) {
                        StreamPageView(
                            page,
                            StreamFixtures.environment(reducedMotion = true),
                            StreamFixtures.callbacks(),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aListStartsAtTheCornerSoTheButtonIsNotDrawnOverIt() {
        val page = pageOf(StreamView.LIST)
        show(page, 640.dp)
        assertEquals(false, room.isClear(page.key))
    }

    @Test
    fun aStackThatStopsShortOfTheCornerLeavesItForTheButton() {
        val page = pageOf(StreamView.STACK)
        show(page, 640.dp)
        assertTrue("the stack stops short of the corner", room.isClear(page.key))
    }

    @Test
    fun aStackInARoomTooShortToLeaveTheCornerDoesNot() {
        val page = pageOf(StreamView.STACK)
        show(page, 120.dp)
        assertEquals(false, room.isClear(page.key))
    }
}
