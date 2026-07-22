package com.riffle.app.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockAlignment
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StandardHomeDockMarginLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dockHonorsConfiguredHorizontalAndBottomScreenMargins() {
        val horizontalMargin = 48.dp
        val bottomMargin = 96.dp
        val layout =
            HomeLayoutDefaults.standard().let { standardLayout ->
                standardLayout.copy(
                    settings =
                        standardLayout.settings.copy(
                            grid =
                                standardLayout.settings.grid.copy(
                                    margin =
                                        GridInsets(
                                            start = horizontalMargin.value.toInt(),
                                            top = 24,
                                            end = horizontalMargin.value.toInt(),
                                            bottom = bottomMargin.value.toInt(),
                                        ),
                                ),
                        ),
                    dock = standardLayout.dock.copy(backgroundSizing = DockBackgroundSizing.FIXED),
                )
            }

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp).testTag(HOME_ROOT_TEST_TAG)) {
                    StandardHome(
                        layout = layout,
                        installedApps = emptyList(),
                        interactions = StandardHomeInteractions(),
                        presentation = StandardHomePresentation(appShortcutsByApp = emptyMap()),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                    )
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(HOME_ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val dockBounds = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val density = composeRule.density

        with(density) {
            assertTrue(dockBounds.left >= rootBounds.left + horizontalMargin.toPx())
            assertTrue(dockBounds.right <= rootBounds.right - horizontalMargin.toPx())
            assertTrue(dockBounds.bottom <= rootBounds.bottom - bottomMargin.toPx())
        }
    }

    @Test
    fun startAlignedDockUsesTheRightEdgeInRtl() {
        val horizontalMargin = 48.dp
        val layout =
            HomeLayoutDefaults.standard().let { standardLayout ->
                standardLayout.copy(
                    settings =
                        standardLayout.settings.copy(
                            grid =
                                standardLayout.settings.grid.copy(
                                    margin =
                                        GridInsets(
                                            start = horizontalMargin.value.toInt(),
                                            top = 24,
                                            end = horizontalMargin.value.toInt(),
                                            bottom = 32,
                                        ),
                                ),
                        ),
                    dock = standardLayout.dock.copy(alignment = DockAlignment.START),
                )
            }

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Box(modifier = Modifier.size(400.dp).testTag(HOME_ROOT_TEST_TAG)) {
                        StandardHome(
                            layout = layout,
                            installedApps = emptyList(),
                            interactions = StandardHomeInteractions(),
                            presentation = StandardHomePresentation(appShortcutsByApp = emptyMap()),
                            appIconLoader = EmptyAppIconLoader,
                            onAction = {},
                        )
                    }
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(HOME_ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val dockBounds = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot

        with(composeRule.density) {
            assertTrue(dockBounds.right >= rootBounds.right - horizontalMargin.toPx())
        }
        assertTrue(dockBounds.left > rootBounds.center.x)
    }

    @Test
    fun centerAlignedDockUsesSymmetricMarginsForLegacyAsymmetricLayouts() {
        val layout =
            HomeLayoutDefaults.standard().let { standardLayout ->
                standardLayout.copy(
                    settings =
                        standardLayout.settings.copy(
                            grid =
                                standardLayout.settings.grid.copy(
                                    margin = GridInsets(start = 72, top = 16, end = 12, bottom = 16),
                                ),
                        ),
                    dock = standardLayout.dock.copy(backgroundSizing = DockBackgroundSizing.FIXED),
                )
            }

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp).testTag(HOME_ROOT_TEST_TAG)) {
                    StandardHome(
                        layout = layout,
                        installedApps = emptyList(),
                        interactions = StandardHomeInteractions(),
                        presentation = StandardHomePresentation(appShortcutsByApp = emptyMap()),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                    )
                }
            }
        }

        val rootBounds = composeRule.onNodeWithTag(HOME_ROOT_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val dockBounds = composeRule.onNodeWithTag(HOME_DOCK_SURFACE_TEST_TAG).fetchSemanticsNode().boundsInRoot

        assertTrue(kotlin.math.abs(dockBounds.center.x - rootBounds.center.x) < 1f)
    }

    @Test
    fun normalDockAppIconKeepsItsSquareCornersOutsideEditMode() {
        val shortcut =
            AppShortcutItem(
                id = LauncherItemId("app:camera"),
                appIdentity =
                    AppIdentity(
                        packageName = AppPackageName("com.example.camera"),
                        activityName = AppActivityName(".CameraActivity"),
                    ),
                label = "Camera",
            )

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(120.dp)) {
                    Dock(
                        dock = DockModel(capacity = 1, items = listOf(shortcut)),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = SolidRedIconLoader,
                        interactions = DockInteractions(onAction = {}),
                    )
                }
            }
        }

        val rendered = composeRule.onNodeWithTag(dockItemTestTag(shortcut.id)).captureToImage()
        val pixels = rendered.toPixelMap()
        val corner = pixels[rendered.width / 12, rendered.height / 12]

        assertTrue(corner.red > 0.9f)
        assertTrue(corner.green < 0.1f)
        assertTrue(corner.blue < 0.1f)
    }
}

private const val HOME_ROOT_TEST_TAG = "home-root"

private object SolidRedIconLoader : AppIconLoader {
    private val icon =
        Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.RED)
        }.asImageBitmap()

    override fun iconFor(identity: AppIdentity): ImageBitmap = icon
}
