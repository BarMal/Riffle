package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.library.LIBRARY_PAGED_TEST_TAG
import com.riffle.app.launcher.library.LIBRARY_PAGE_TEST_TAG_PREFIX
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.library.LibraryView
import com.riffle.core.domain.launcher.settings.AppDrawerSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The paged Library View (ADR 0003, Amendment 1) on a device, through the real [HomeDestination]: pages of Home's
 * grid swipe sideways, the handle's dots quick-drag between them, and an app on a later page launches. The shell is a
 * recording lambda.
 */
@RunWith(AndroidJUnit4::class)
class PagedLibraryInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val apps = (1..APP_COUNT).map { index -> app(index) }

    @Test
    fun theFirstPageHoldsTheFirstAppsAndTheLastIsOnALaterPage() {
        setContent()

        composeRule.onNodeWithTag(LIBRARY_PAGED_TEST_TAG).assertExists()
        composeRule.onNodeWithText("App 01").assertExists()
        composeRule.onNodeWithText("App $APP_COUNT").assertDoesNotExist()
    }

    @Test
    fun swipingSidewaysShowsTheNextPageAndAnAppOnItLaunches() {
        val actions = setContent()

        composeRule.onNodeWithTag("${LIBRARY_PAGE_TEST_TAG_PREFIX}0").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("App $APP_COUNT").performClick()

        composeRule.runOnIdle {
            assertEquals(LauncherShellAction.LaunchApp(apps.last().identity), actions.first())
        }
    }

    @Test
    fun draggingAlongTheDotsQuickDragsToTheLastPage() {
        val actions = setContent()

        composeRule.onNodeWithContentDescription("Page selector").performTouchInput {
            swipe(start = Offset(1f, height / 2f), end = Offset(width - 1f, height / 2f))
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("App $APP_COUNT").assertExists()
        composeRule.runOnIdle {
            assertTrue("a drag along the dots opened search: $actions", LauncherShellAction.OpenSearch !in actions)
        }
    }

    private fun setContent(): MutableList<LauncherShellAction> {
        val actions = mutableListOf<LauncherShellAction>()
        val layout =
            HomeLayoutDefaults.standard().copy(viewMode = LauncherViewMode.STANDARD_APP_DRAWER)
        val base =
            LauncherShellState(
                homeLayout = layout,
                destination = ShellDestination.APP_DRAWER,
                installedApps = apps,
            )
        val state =
            base.copy(
                launcherSettings =
                    LauncherSettings(
                        appDrawer =
                            AppDrawerSettings(
                                libraryViews = mapOf(base.homeLayoutSet.activeKey.deviceClass to LibraryView.Paged),
                            ),
                    ),
            )
        composeRule.setContent {
            MaterialTheme {
                HomeDestination(
                    state = state,
                    appIconLoader = EmptyAppIconLoader,
                    onAction = { action -> actions += action },
                )
            }
        }
        composeRule.waitForIdle()
        return actions
    }

    private fun app(index: Int): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.paged$index"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = "App ${index.toString().padStart(2, '0')}",
        )

    private companion object {
        /** More than one page of the standard grid. */
        const val APP_COUNT = 30
    }
}
