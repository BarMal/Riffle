package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppPackageName
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

        val previewHeight =
            composeRule.onNodeWithTag(WIDGET_PICKER_PREVIEW_TEST_TAG).fetchSemanticsNode().boundsInRoot.height

        assertTrue(previewHeight <= with(composeRule.density) { 240.dp.toPx() })
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
