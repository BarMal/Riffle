package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.SEARCH_ORDER_TEST_TAG_PREFIX
import com.riffle.app.launcher.SEARCH_PER_GROUP_TEST_TAG_PREFIX
import com.riffle.app.launcher.SEARCH_SOURCE_SWITCH_TEST_TAG_PREFIX
import com.riffle.app.launcher.SettingsSearchSourceRows
import com.riffle.app.launcher.SettingsSection
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.settings.SearchSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings > Library > Search: a switch per source, the order of the groups and the results per group. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = TALL_PHONE)
class SearchSourceSettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private var search by mutableStateOf(SearchSettings())

    private fun show() {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                    SettingsSection(title = "Search") {
                        SettingsSearchSourceRows(
                            settings = search,
                            onAction = { action ->
                                if (action is LauncherShellAction.SetSearchSettings) search = action.settings
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun switch(name: String) = composeRule.onNodeWithTag("$SEARCH_SOURCE_SWITCH_TEST_TAG_PREFIX$name")

    @Test
    fun theSectionShowsTheDefaults() {
        show()
        composeRule.captureScreen()
    }

    @Test
    fun theSwitchesStartAtTheDocumentedDefaultsAndEachOneChangesItsSource() {
        show()
        assertEquals(true, search.calculator)
        assertEquals(false, search.contacts)
        switch("contacts").performScrollTo().performClick()
        switch("files").performScrollTo().performClick()
        switch("calculator").performScrollTo().performClick()
        switch("web").performScrollTo().performClick()
        assertEquals(true, search.contacts)
        assertEquals(true, search.files)
        assertEquals(false, search.calculator)
        assertEquals(false, search.webHandoff)
        assertEquals(
            setOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.CONTACTS, SearchSourceId.FILES),
            search.enabledSources,
        )
    }

    @Test
    fun theGroupsMoveUpAndDownAndTheEndsCannotMoveFurther() {
        show()
        val first = SearchSourceId.SYSTEM_SETTINGS.name
        val last = SearchSourceId.FILES.name
        composeRule.onNodeWithTag("$SEARCH_ORDER_TEST_TAG_PREFIX$first-up").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("$SEARCH_ORDER_TEST_TAG_PREFIX$last-down").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("$SEARCH_ORDER_TEST_TAG_PREFIX$last-up").performScrollTo().performClick()
        assertEquals(
            listOf(SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.FILES, SearchSourceId.CONTACTS),
            search.groupOrder,
        )
        composeRule.onNodeWithTag("$SEARCH_ORDER_TEST_TAG_PREFIX$first-down").performScrollTo().assertIsEnabled()
    }

    @Test
    fun resultsPerGroupOffersThreeFiveAndEightAndStartsAtFive() {
        show()
        composeRule.onNodeWithTag("$SEARCH_PER_GROUP_TEST_TAG_PREFIX" + "5").performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("$SEARCH_PER_GROUP_TEST_TAG_PREFIX" + "8").performScrollTo().performClick()
        assertEquals(8, search.resultsPerGroup)
        composeRule.onNodeWithTag("$SEARCH_PER_GROUP_TEST_TAG_PREFIX" + "3").performScrollTo().performClick()
        assertEquals(3, search.resultsPerGroup)
    }
}

private const val TALL_PHONE = "w411dp-h940dp-normal-long-notround-any-420dpi-keyshidden-nonav"
