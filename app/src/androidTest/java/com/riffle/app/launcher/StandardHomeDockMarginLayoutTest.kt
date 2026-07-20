package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.home.DockBackgroundSizing
import com.riffle.core.domain.launcher.home.GridInsets
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
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
        val dockBounds = composeRule.onNodeWithTag(HOME_DOCK_TEST_TAG).fetchSemanticsNode().boundsInRoot
        val density = composeRule.density

        with(density) {
            assertTrue(dockBounds.left >= rootBounds.left + horizontalMargin.toPx())
            assertTrue(dockBounds.right <= rootBounds.right - horizontalMargin.toPx())
            assertTrue(dockBounds.bottom <= rootBounds.bottom - bottomMargin.toPx())
        }
    }
}

private const val HOME_ROOT_TEST_TAG = "home-root"
