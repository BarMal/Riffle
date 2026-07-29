package com.riffle.app.launcher

import android.os.Build
import android.widget.RemoteViews
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riffle.app.launcher.widgets.loadWidgetPreviewWithFallback
import com.riffle.app.launcher.widgets.renderWidgetPreviewRemoteViews
import com.riffle.core.domain.launcher.WidgetProviderCatalogStatus
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderDimensions
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPickerSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun distinguishesLoadingAndRetryableProviderReadFailureFromAnEmptyCatalog() {
        var retryCount = 0
        var status by mutableStateOf(WidgetProviderCatalogStatus.LOADING)
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = emptyList(),
                    catalogStatus = status,
                    onRetryRequested = { retryCount += 1 },
                    onAction = {},
                )
            }
        }

        composeRule.onNodeWithText("Loading widgets…").assertIsDisplayed()
        composeRule.runOnIdle { status = WidgetProviderCatalogStatus.FAILED }
        composeRule.onNodeWithText("Widgets couldn’t be loaded").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.runOnIdle { assertEquals(1, retryCount) }
    }

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
    fun lockedProfileDoesNotLoadOrOfferItsWidgetForPlacement() {
        var previewLoadCount = 0
        val provider = widgetProvider()
        val loader =
            object : WidgetPreviewImageLoader {
                override suspend fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? {
                    previewLoadCount += 1
                    return ImageBitmap(width = 1, height = 1)
                }
            }
        composeRule.setContent {
            MaterialTheme {
                WidgetPickerSurface(
                    providers = listOf(provider),
                    profileContentVisibility =
                        mapOf(
                            provider.identity.profile.id to
                                AppProfileContentVisibility.REDACTED_LOCKED,
                        ),
                    previewImageLoader = loader,
                    onAction = {},
                )
            }
        }

        composeRule
            .onNodeWithText("This profile is locked. Unlock it to preview or place its widgets.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Add Clock").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(0, previewLoadCount) }
    }

    @Test
    fun android15GeneratedRemoteViewsAreRetrievedAndRenderedBeforeLegacyPreview() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val generatedRemoteViews =
            RemoteViews("android", android.R.layout.simple_list_item_1).apply {
                setTextViewText(android.R.id.text1, "Generated widget preview")
            }
        var generatedPreviewRetrieved = false
        var legacyPreviewLoaded = false

        val preview =
            runBlocking {
                loadWidgetPreviewWithFallback(
                    loadGeneratedPreview = {
                        generatedPreviewRetrieved = true
                        generatedRemoteViews
                    },
                    renderGeneratedPreview = { remoteViews ->
                        withContext(Dispatchers.Main.immediate) {
                            renderWidgetPreviewRemoteViews(
                                context = context,
                                remoteViews = remoteViews,
                                intrinsicWidth = 200,
                                intrinsicHeight = 100,
                            )
                        }
                    },
                    loadLegacyPreview = {
                        legacyPreviewLoaded = true
                        ImageBitmap(width = 1, height = 1)
                    },
                )
            }

        assertTrue(generatedPreviewRetrieved)
        assertFalse(legacyPreviewLoaded)
        assertEquals(200, preview?.width)
        assertEquals(100, preview?.height)
    }

    @Test
    fun android15GeneratedRenderFailureFallsBackToLegacyPreview() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        var legacyPreviewLoaded = false

        val preview =
            runBlocking {
                loadWidgetPreviewWithFallback(
                    loadGeneratedPreview = {
                        RemoteViews("android", android.R.layout.simple_list_item_1)
                    },
                    renderGeneratedPreview = {
                        error("Generated preview resource is malformed")
                    },
                    loadLegacyPreview = {
                        legacyPreviewLoaded = true
                        ImageBitmap(width = 7, height = 5)
                    },
                )
            }

        assertTrue(legacyPreviewLoaded)
        assertEquals(7, preview?.width)
        assertEquals(5, preview?.height)
    }

    @Test
    fun android15MissingGeneratedRemoteViewsFallsBackWithoutRendering() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM)
        var generatedPreviewRendered = false

        val preview =
            runBlocking {
                loadWidgetPreviewWithFallback(
                    loadGeneratedPreview = { null },
                    renderGeneratedPreview = {
                        generatedPreviewRendered = true
                        ImageBitmap(width = 1, height = 1)
                    },
                    loadLegacyPreview = {
                        ImageBitmap(width = 9, height = 6)
                    },
                )
            }

        assertFalse(generatedPreviewRendered)
        assertEquals(9, preview?.width)
        assertEquals(6, preview?.height)
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
    override suspend fun previewFor(identity: WidgetProviderIdentity): ImageBitmap? = error("Preview provider failed")
}
