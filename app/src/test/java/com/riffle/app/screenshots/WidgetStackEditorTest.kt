package com.riffle.app.screenshots

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.ConfirmRemoveWidgetStackDialog
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.WIDGET_STACK_EDITOR_TEST_TAG
import com.riffle.app.launcher.WIDGET_STACK_REMOVE_CONFIRM_TEST_TAG
import com.riffle.app.launcher.WidgetStackEditorSurface
import com.riffle.app.launcher.WidgetStackMemberIcons
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridDimensions
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Edit stack sheet and the question before removing a stack (ADR 0003, Amendment 14), drawn for real. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WidgetStackEditorTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val actions = mutableListOf<LauncherShellAction>()
    private var dismissed = 0
    private val stack = fixtureStack(labels = listOf("Weather", "Calendar", "Notes", "Clock"), shown = 1)

    /** Solid squares for the first two widgets' apps; the others have none, so their rows show an initial. */
    private val icons =
        WidgetStackMemberIcons { member ->
            when (member.label) {
                "Weather" -> solid(0xFF3E7CB1)
                "Calendar" -> solid(0xFFB1593E)
                else -> null
            }
        }

    private fun solid(argb: Long): ImageBitmap =
        ImageBitmap(ICON_PX, ICON_PX).also { bitmap ->
            android.graphics.Canvas(bitmap.asAndroidBitmap()).drawColor(argb.toInt())
        }

    private fun layoutWith(vararg extra: WidgetItem): HomeLayout =
        HomeLayoutDefaults.standard().let { standard ->
            val page =
                LauncherPage(
                    id = LauncherPageId("home"),
                    grid = GridDimensions(4, 4),
                    items = listOf(stack) + extra.toList(),
                )
            standard.copy(pages = listOf(page), selectedPageId = page.id)
        }

    private fun neighbour(
        host: Int,
        column: Int,
        row: Int,
    ) = WidgetItem(
        id = LauncherItemId("widget:$host"),
        appWidgetId = HostedWidgetId(host),
        label = "Filler $host",
        placement = GridPlacement(GridCell(column, row), GridSpan(2, 2)),
    )

    private fun show(layout: HomeLayout = layoutWith()) {
        composeRule.setContent {
            ScreenshotBackdrop {
                WidgetStackEditorSurface(
                    stack = stack,
                    layout = layout,
                    icons = icons,
                    onAction = { actions += it },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun theSheetListsTheWidgetsInOrderWithTheirNamesAndAppIcons() {
        show()
        composeRule.onNodeWithTag(WIDGET_STACK_EDITOR_TEST_TAG).captureRoboImage()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun theSheetInTheDarkTheme() {
        show()
        composeRule.onNodeWithTag(WIDGET_STACK_EDITOR_TEST_TAG).captureRoboImage()
    }

    @Test
    fun everyRowButtonIsA48dpTargetAndTheFirstCannotMoveUpNorTheLastDown() {
        show()

        composeRule.onNodeWithContentDescription("Move Weather up").assertIsNotEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Move Clock down").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Move Calendar down").assertIsEnabled()
        composeRule.onNodeWithContentDescription("Remove Notes from the stack").assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription("Move Notes out to Home").assertIsEnabled().assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun theButtonsReorderRemoveAndMoveOut() {
        show()

        composeRule.onNodeWithContentDescription("Move Calendar up").performClick()
        composeRule.onNodeWithContentDescription("Move Calendar down").performClick()
        composeRule.onNodeWithContentDescription("Move Notes out to Home").performClick()
        composeRule.onNodeWithContentDescription("Remove Weather from the stack").performClick()

        assertEquals(
            listOf(
                LauncherShellAction.MoveStackedWidget(stack.id, 1, 0),
                LauncherShellAction.MoveStackedWidget(stack.id, 1, 2),
                LauncherShellAction.MoveStackedWidgetOut(stack.id, 2),
                LauncherShellAction.RemoveStackedWidget(stack.id, 0),
            ),
            actions,
        )
    }

    @Test
    fun withNoRoomOnThePageMovingOutIsDisabledAndTheSheetSaysSo() {
        show(layoutWith(neighbour(10, 2, 0), neighbour(11, 0, 2), neighbour(12, 2, 2)))

        composeRule.onNodeWithContentDescription("Move Notes out to Home").assertIsNotEnabled()
        assertEquals(
            4,
            composeRule.onAllNodesWithText("No room on this page to move it out.").fetchSemanticsNodes().size,
        )
        composeRule.onNodeWithTag(WIDGET_STACK_EDITOR_TEST_TAG).captureRoboImage()
    }

    @Test
    fun removingAStackAsksFirstAndSaysHowManyWidgetsGo() {
        var confirmed = 0
        composeRule.setContent {
            ScreenshotBackdrop {
                ConfirmRemoveWidgetStackDialog(
                    widgetCount = 4,
                    onConfirm = { confirmed++ },
                    onDismiss = { dismissed++ },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WIDGET_STACK_REMOVE_CONFIRM_TEST_TAG).performClick()

        assertEquals(1, confirmed)
        assertEquals(
            1,
            composeRule.onAllNodesWithText("The 4 widgets in it are removed from Home.").fetchSemanticsNodes().size,
        )
    }

    private companion object {
        const val ICON_PX = 96
    }
}
