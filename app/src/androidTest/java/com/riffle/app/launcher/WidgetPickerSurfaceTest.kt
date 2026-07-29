package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPickerSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun accessibleAddActionsSelectTargetsWithoutEmittingAnAddRequest() {
        val selectedTargets = mutableListOf<WidgetAddTarget>()
        val emittedActions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = listOf(widgetProvider()),
                    onAccessiblePlacementRequested = { _, target -> selectedTargets += target },
                    onAction = { action -> emittedActions += action },
                )
            }
        }

        val actions = composeRule.onNodeWithTag(WIDGET_PROVIDER_TILE_TEST_TAG).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        actions.first { action -> action.label == "Add Clock to Home" }.action()
        actions.first { action -> action.label == "Add Clock to Dock" }.action()

        composeRule.runOnIdle {
            assertEquals(listOf(WidgetAddTarget.HOME, WidgetAddTarget.DOCK), selectedTargets)
            assertTrue(emittedActions.isEmpty())
        }
    }

    @Test
    fun visibleAddMenuUsesTheSamePlacementSelectionFlow() {
        var selectedTarget: WidgetAddTarget? = null
        val emittedActions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = listOf(widgetProvider()),
                    onAccessiblePlacementRequested = { _, target -> selectedTarget = target },
                    onAction = { action -> emittedActions += action },
                )
            }
        }

        composeRule.onNodeWithText("Add Clock").performClick()
        composeRule.onNodeWithText("Choose Home position").performClick()

        composeRule.runOnIdle {
            assertEquals(WidgetAddTarget.HOME, selectedTarget)
            assertTrue(emittedActions.isEmpty())
        }
    }

    @Test
    fun accessiblePlacementCanBeCancelledWithoutEmittingAnAddRequest() {
        var cancelled = false
        val emittedActions = mutableListOf<LauncherShellAction>()
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = listOf(widgetProvider()),
                    accessiblePlacement =
                        WidgetPickerAccessiblePlacement(
                            provider = widgetProvider(),
                            target = WidgetAddTarget.HOME,
                            initialPageId = LauncherPageId("home"),
                            candidates =
                                listOf(
                                    WidgetPickerPlacementCandidate(
                                        pageId = LauncherPageId("home"),
                                        cell = GridCell(column = 0, row = 0),
                                        span = GridSpan(columns = 2, rows = 1),
                                    ),
                                ),
                        ),
                    onAccessiblePlacementCancelled = { cancelled = true },
                    onAction = { action -> emittedActions += action },
                )
            }
        }

        composeRule.onNodeWithText("Placement preview").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle {
            assertTrue(cancelled)
            assertTrue(emittedActions.isEmpty())
        }
    }

    @Test
    fun keepsThePickerAvailableWhenAProviderPreviewFails() {
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = listOf(widgetProvider()),
                    previewImageLoader = ThrowingWidgetPreviewImageLoader,
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Clock").assertIsDisplayed()
        composeRule.onNodeWithText("Add Clock").assertIsDisplayed()
    }

    @Test
    fun insetsTheActualPickerSurfaceFromEveryCompactWindowEdge() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 300.dp, height = 500.dp)) {
                    WidgetPickerSurface(
                        providers = listOf(widgetProvider()),
                        previewImageLoader = ThrowingWidgetPreviewImageLoader,
                        onAction = {},
                    )
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(WIDGET_PICKER_ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val panelBounds = composeRule.onNodeWithTag(WIDGET_PICKER_PANEL_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(panelBounds.width < rootBounds.width)
        assertTrue(panelBounds.left > rootBounds.left)
        assertTrue(panelBounds.right < rootBounds.right)
        assertTrue(panelBounds.top > rootBounds.top)
        assertTrue(panelBounds.bottom < rootBounds.bottom)
    }

    @Test
    fun capsAndCentersThePickerPanelOnWideWindows() {
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.requiredSize(width = 1200.dp, height = 700.dp)) {
                    WidgetPickerSurface(
                        providers = listOf(widgetProvider()),
                        previewImageLoader = ThrowingWidgetPreviewImageLoader,
                        onAction = {},
                    )
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(WIDGET_PICKER_ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val panelBounds = composeRule.onNodeWithTag(WIDGET_PICKER_PANEL_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val expectedPanelWidth = with(composeRule.density) { (840 - (12 * 2)).dp.toPx() }
        val leftMargin = panelBounds.left - rootBounds.left
        val rightMargin = rootBounds.right - panelBounds.right

        assertEquals(expectedPanelWidth, panelBounds.width, 1f)
        assertEquals(leftMargin, rightMargin, 1f)
        assertTrue(leftMargin > 0f)
    }

    @Test
    fun boundsAnExtremeFallbackPreviewHeight() {
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers =
                        listOf(
                            widgetProvider().copy(
                                dimensions = WidgetProviderDimensions(minWidthDp = 1, minHeightDp = 10_000),
                            ),
                        ),
                    previewImageLoader = ThrowingWidgetPreviewImageLoader,
                    onAction = {},
                )
            }
        }

        val previewBounds =
            composeRule.onNodeWithTag(WIDGET_PICKER_PREVIEW_TEST_TAG).fetchSemanticsNode().boundsInRoot

        with(composeRule.density) {
            assertEquals(180.dp.toPx(), previewBounds.width, 1f)
            assertEquals(96.dp.toPx(), previewBounds.height, 1f)
        }
    }

    @Test
    fun cancelledProviderDragEndsThePickerHandoff() {
        var dragStarted = false
        var dragCancelled = false
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = listOf(widgetProvider()),
                    onWidgetDragStarted = { dragStarted = true },
                    onWidgetDragCancelled = { dragCancelled = true },
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithTag(WIDGET_PROVIDER_TILE_TEST_TAG).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(8f, 8f))
            cancel()
        }

        composeRule.runOnIdle {
            assertTrue(dragStarted)
            assertTrue(dragCancelled)
        }
    }

    @Test
    fun providerDropUsesThePickerRootCoordinateSpaceAndBounds() {
        var droppedPosition: Offset? = null
        var droppedRootSize: IntSize? = null
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 300.dp, height = 500.dp)) {
                    WidgetPickerSurface(
                        providers = listOf(widgetProvider()),
                        onWidgetDropped = { _, position, rootSize ->
                            droppedPosition = position
                            droppedRootSize = rootSize
                        },
                        onAction = {},
                    )
                }
            }
        }
        val rootBounds =
            composeRule.onNodeWithTag(WIDGET_PICKER_ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithTag(WIDGET_PROVIDER_TILE_TEST_TAG).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(12f, 16f))
            up()
        }

        composeRule.runOnIdle {
            assertEquals(IntSize(rootBounds.width.toInt(), rootBounds.height.toInt()), droppedRootSize)
            assertTrue(droppedPosition!!.x in 0f..rootBounds.width)
            assertTrue(droppedPosition!!.y in 0f..rootBounds.height)
        }
    }

    @Test
    fun providerDragReportsItsRootPositionBeforeDrop() {
        var movedPosition: Offset? = null
        var movedRootSize: IntSize? = null
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 300.dp, height = 500.dp)) {
                    WidgetPickerSurface(
                        providers = listOf(widgetProvider()),
                        onWidgetDragMoved = { _, position, rootSize ->
                            movedPosition = position
                            movedRootSize = rootSize
                        },
                        onAction = {},
                    )
                }
            }
        }

        composeRule.onNodeWithTag(WIDGET_PROVIDER_TILE_TEST_TAG).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveBy(Offset(12f, 16f))
            cancel()
        }

        composeRule.runOnIdle {
            assertTrue(movedPosition != null)
            assertEquals(IntSize(300, 500), movedRootSize)
        }
    }

    @Test
    fun edgeHoverRecomputesDestinationPreviewBeforeDroppingOnTheSelectedPage() {
        composeRule.mainClock.autoAdvance = false
        val firstPageId = LauncherPageId("first")
        val secondPageId = LauncherPageId("second")
        val standardLayout = HomeLayoutDefaults.standard()
        val firstPage = standardLayout.selectedPage.copy(id = firstPageId)
        val secondPage = LauncherPage(id = secondPageId, grid = firstPage.grid)
        var selectedPageAction: LauncherShellAction.SelectHomePage? = null
        var addRequest: LauncherShellAction.RequestAddWidget? = null

        composeRule.setContent {
            var layout by
                remember {
                    mutableStateOf(
                        standardLayout.copy(
                            pages = listOf(firstPage, secondPage),
                            selectedPageId = firstPageId,
                        ),
                    )
                }
            MaterialTheme {
                Box(modifier = Modifier.size(width = 400.dp, height = 700.dp)) {
                    StandardHome(
                        layout = layout,
                        installedApps = emptyList(),
                        interactions = StandardHomeInteractions(),
                        presentation =
                            StandardHomePresentation(
                                appShortcutsByApp = emptyMap(),
                                reducedMotion = true,
                                widgetPicker =
                                    StandardHomeWidgetPickerState(
                                        providers = listOf(widgetProvider()),
                                        isOpen = true,
                                    ),
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = { action ->
                            when (action) {
                                is LauncherShellAction.SelectHomePage -> {
                                    selectedPageAction = action
                                    layout = layout.copy(selectedPageId = action.pageId)
                                }

                                is LauncherShellAction.RequestAddWidget -> addRequest = action
                                else -> Unit
                            }
                        },
                    )
                }
            }
        }
        val sourceNode = composeRule.onNodeWithTag(WIDGET_PROVIDER_TILE_TEST_TAG)
        val sourceBounds = sourceNode.fetchSemanticsNode().boundsInRoot
        val workspaceBounds =
            composeRule
                .onNodeWithTag(widgetPickerWorkspaceGridTestTag(firstPageId))
                .fetchSemanticsNode()
                .boundsInRoot
        val targetInSource =
            Offset(
                x = workspaceBounds.right - 2f - sourceBounds.left,
                y = workspaceBounds.center.y - sourceBounds.top,
            )

        sourceNode.performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 50L)
            moveTo(targetInSource)
        }
        composeRule.mainClock.advanceTimeBy(700L)
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(secondPageId, selectedPageAction?.pageId)
        }
        composeRule.onNodeWithTag(widgetPickerWorkspaceGridTestTag(secondPageId)).assertIsDisplayed()
        composeRule.onNodeWithTag(WIDGET_PICKER_DRAG_PREVIEW_TEST_TAG).assertIsDisplayed()

        sourceNode.performTouchInput { up() }
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            assertEquals(secondPageId, addRequest?.targetPageId)
            assertEquals(GridCell(column = firstPage.grid.columns - 1, row = firstPage.grid.rows / 2), addRequest?.targetCell)
        }
    }

    private fun widgetProvider(): InstalledWidgetProvider =
        InstalledWidgetProvider(
            identity =
                WidgetProviderIdentity(
                    packageName = AppPackageName("com.example.clock"),
                    className = WidgetProviderClassName(".ClockWidget"),
                ),
            label = "Clock",
            dimensions = WidgetProviderDimensions(minWidthDp = 120, minHeightDp = 80),
        )
}

private object ThrowingWidgetPreviewImageLoader : WidgetPreviewImageLoader {
    override fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? = error("Preview provider failed")
}
