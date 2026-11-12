package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings by Place (docs/product/settings.md): the ten rows of the main page, Back going to the parent page, search
 * opening a page scrolled to the setting, and the Stream page writing the layout the Stream's own menus write.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPlacesTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun streamRepository() =
        CachedStreamLayoutRepository(
            object : StreamLayoutStorePort {
                override suspend fun read(): StreamLayouts? = null

                override suspend fun write(layouts: StreamLayouts) = Unit
            },
            CoroutineScope(Dispatchers.Unconfined + Job()),
        )

    private fun setSurface(
        repository: CachedStreamLayoutRepository = streamRepository(),
        height: Int = 600,
    ) {
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalStreamLayoutRepository provides repository) {
                    Box(modifier = Modifier.height(height.dp)) {
                        SettingsSurface(state = LauncherShellState().settingsSurfaceState(), onAction = {})
                    }
                }
            }
        }
    }

    @Test
    fun theMainPageListsTheTenPlacesAndNothingElse() {
        setSurface()

        settingsMainPageRows.forEach { page ->
            composeRule.onNodeWithTag(settingsMainRowTestTag(page)).performScrollTo().assertIsDisplayed()
        }
        val mainRows =
            SemanticsMatcher("is a main page row") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("settings-main-row-") == true
            }
        assertEquals(10, composeRule.onAllNodes(mainRows).fetchSemanticsNodes().size)
        // The pages the document removed are not on the main page.
        composeRule.onNodeWithText("Sources").assertDoesNotExist()
        composeRule.onNodeWithText("Permissions").assertDoesNotExist()
        composeRule.onNodeWithText("Contextual").assertDoesNotExist()
        composeRule.onNodeWithText("Workspaces").assertDoesNotExist()
    }

    @Test
    fun backFromASubPageGoesToItsParentNotTheMainPage() {
        setSurface()

        composeRule.onNodeWithTag(settingsMainRowTestTag(SettingsPage.LIBRARY)).performScrollTo().performClick()
        composeRule.onNodeWithText("Hidden apps").performScrollTo().performClick()
        composeRule.onNodeWithText("Search hidden apps").assertIsDisplayed()

        composeRule.onNodeWithText("Back").performClick()

        // The Library page, not the main page.
        composeRule.onNodeWithTag(LIBRARY_VIEW_TEST_TAG_PREFIX + "Categories").assertExists()
        composeRule.onNodeWithTag(settingsMainRowTestTag(SettingsPage.LIBRARY)).assertDoesNotExist()
    }

    @Test
    fun searchingASettingOpensItsPageScrolledToIt() {
        setSurface(height = 480)

        composeRule.onNodeWithTag(SETTINGS_SEARCH_FIELD_TEST_TAG).performTextInput("wifi")
        composeRule.onNodeWithTag("settings-result-stream_feeds#FEEDS_WIFI_ONLY").performScrollTo().performClick()

        // Well down the Feeds page, and on screen because search scrolled to it.
        composeRule.onNodeWithText("Only on Wi-Fi").assertIsDisplayed()
    }

    @Test
    fun theStreamPageWritesTheSameLayoutTheStreamsOwnMenusWrite() {
        val repository = streamRepository()
        setSurface(repository)

        composeRule.onNodeWithTag(settingsMainRowTestTag(SettingsPage.STREAM)).performScrollTo().performClick()
        composeRule.onNodeWithTag(
            streamShowAsTestTag(StreamSection.AGENDA, StreamView.STACK),
        ).performScrollTo().performClick()
        composeRule.onNodeWithTag(streamSectionTestTag(StreamSection.TASKS)).performScrollTo().performClick()
        composeRule.onNodeWithTag(streamMoveTestTag(StreamSection.FEEDS, -1)).performScrollTo().performClick()

        composeRule.runOnIdle {
            val layout = repository.layoutFor(HomeLayoutDeviceClass.PHONE)
            assertEquals(StreamView.STACK, layout.viewOf(StreamSection.AGENDA))
            assertEquals(false, layout.layoutOf(StreamSection.TASKS).enabled)
            assertEquals(StreamSection.FEEDS, layout.sections[StreamSection.entries.lastIndex - 1].section)
        }
        composeRule.onNodeWithTag(streamShowAsTestTag(StreamSection.AGENDA, StreamView.STACK)).assertIsSelected()
        composeRule.onNodeWithTag(streamShowAsTestTag(StreamSection.AGENDA, StreamView.LIST)).assertIsNotSelected()
    }

    @Test
    fun theTasksPageOnlySaysNoProviderIsConnectedYet() {
        setSurface()

        composeRule.onNodeWithTag(settingsMainRowTestTag(SettingsPage.STREAM)).performScrollTo().performClick()
        composeRule.onNodeWithText("No task provider is connected yet").performScrollTo().performClick()

        composeRule
            .onNodeWithText("No task provider is connected yet. Tasks will appear in the Stream once one is.")
            .assertIsDisplayed()
        // Nothing to enter yet: no token field on this page.
        composeRule.onNodeWithText("Token").assertDoesNotExist()
    }
}
