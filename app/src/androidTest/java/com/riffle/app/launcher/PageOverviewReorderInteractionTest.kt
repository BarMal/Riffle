package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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
    fun longPressDragReordersAnOffScreenPageOnPhoneWidth() {
        verifyOffScreenPageDrag(width = 360.dp)
    }

    @Test
    fun longPressDragReordersAnOffScreenPageOnFoldableWidth() {
        verifyOffScreenPageDrag(width = 840.dp)
    }

    private fun verifyOffScreenPageDrag(width: Dp) {
        val actions = mutableListOf<LauncherShellAction>()
        setContent(width = width, onAction = actions::add)

        composeRule.onNodeWithText("Page 7").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Page 7").performTouchInput {
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

    private fun setContent(
        width: Dp,
        onAction: (LauncherShellAction) -> Unit,
    ) {
        composeRule.setContent {
            var layout by mutableStateOf(pageOverviewLayout())

            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .width(width)
                            .height(640.dp),
                ) {
                    PageOverviewControls(
                        layout = layout,
                        reducedMotion = false,
                        appIconLoader = EmptyAppIconLoader,
                        widgetViewFactory = EmptyHomeWidgetViewFactory,
                        onAction = { action ->
                            onAction(action)
                            if (action is LauncherShellAction.MoveHomePage) {
                                layout = layout.movePage(action.pageId, action.targetIndex)
                            }
                        },
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

    private fun HomeLayout.movePage(
        pageId: LauncherPageId,
        targetIndex: Int,
    ): HomeLayout {
        val page = pages.first { it.id == pageId }
        val reorderedPages = pages.filterNot { it.id == pageId }.toMutableList()
        reorderedPages.add(targetIndex, page)
        return copy(pages = reorderedPages)
    }
}
