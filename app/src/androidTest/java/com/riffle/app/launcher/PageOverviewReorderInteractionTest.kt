package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageOverviewReorderInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressDragReordersAnOffScreenPageAtPhoneAndFoldableWidths() {
        // Keep both widths in one Compose rule lifecycle. Android instrumentation may schedule
        // separate test methods on different threads, which can leave Compose's layout observer
        // owned by the previous thread between methods.
        val containerWidth = mutableStateOf(360.dp)
        val actions = mutableListOf<LauncherShellAction>()
        setContent(containerWidth = containerWidth, onAction = actions::add)

        listOf(360.dp, 840.dp).forEach { width ->
            composeRule.runOnIdle {
                containerWidth.value = width
                actions.clear()
            }
            verifyOffScreenPageDrag(actions)
        }
    }

    private fun verifyOffScreenPageDrag(actions: List<LauncherShellAction>) {
        scrollToPageOverviewCard(pageId = "page-7")
        composeRule.onNodeWithTag(pageOverviewCardTestTag("page-7")).assertIsDisplayed()
        composeRule.onNodeWithTag(pageOverviewCardTestTag("page-7")).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(x = -width.toFloat(), y = 0f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(
                listOf(LauncherShellAction.MoveHomePage(LauncherPageId("page-7"), targetIndex = 5)),
                actions,
            )
        }
    }

    private fun scrollToPageOverviewCard(pageId: String) {
        repeat(8) {
            composeRule.onNodeWithTag(PAGE_OVERVIEW_STRIP_TEST_TAG).performTouchInput {
                down(center)
                moveBy(Offset(x = -width.toFloat(), y = 0f))
                up()
            }
            // Let LazyRow finish applying each gesture before dispatching the next one. Without
            // this boundary, rapid test input can overlap the previous layout observation.
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag(pageOverviewCardTestTag(pageId)).assertIsDisplayed()
    }

    private fun setContent(
        containerWidth: MutableState<Dp>,
        onAction: (LauncherShellAction) -> Unit,
    ) {
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .width(containerWidth.value)
                            .height(640.dp),
                ) {
                    PageOverviewControls(
                        layout = pageOverviewLayout(),
                        // The drag/drop contract is independent from reflow animation. Keeping
                        // motion disabled prevents preview springs from racing test input.
                        reducedMotion = true,
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    private fun pageOverviewLayout(): HomeLayout {
        val baseLayout = HomeLayoutDefaults.standard()
        val pages =
            (1..7).map { number ->
                LauncherPage(
                    id = LauncherPageId("page-$number"),
                    grid = baseLayout.settings.grid.dimensions,
                )
            }

        return baseLayout.copy(pages = pages, selectedPageId = pages.first().id)
    }
}
