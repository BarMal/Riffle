package com.riffle.app.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.riffle.app.launcher.LauncherShellContent
import com.riffle.app.launcher.search.DOCK_SEARCH_FIELD_TEST_TAG
import com.riffle.app.launcher.search.wider.StaticWiderSearchHost
import com.riffle.app.launcher.search.wider.WIDER_ALLOW_TEST_TAG_PREFIX
import com.riffle.app.launcher.search.wider.WIDER_CALL_TEST_TAG
import com.riffle.app.launcher.search.wider.WIDER_COPY_BOTH_TEST_TAG
import com.riffle.app.launcher.search.wider.WIDER_HEADER_TEST_TAG_PREFIX
import com.riffle.app.launcher.search.wider.WIDER_MESSAGE_TEST_TAG
import com.riffle.app.launcher.search.wider.WIDER_ROW_TEST_TAG_PREFIX
import com.riffle.app.launcher.search.wider.WIDER_SHOW_MORE_TEST_TAG_PREFIX
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.search.wider.CalculatorSource
import com.riffle.core.domain.launcher.search.wider.ContactRecord
import com.riffle.core.domain.launcher.search.wider.ContactsReader
import com.riffle.core.domain.launcher.search.wider.ContactsSource
import com.riffle.core.domain.launcher.search.wider.FileKind
import com.riffle.core.domain.launcher.search.wider.FileRecord
import com.riffle.core.domain.launcher.search.wider.FilesReader
import com.riffle.core.domain.launcher.search.wider.FilesSource
import com.riffle.core.domain.launcher.search.wider.SearchSource
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.SystemSettingEntry
import com.riffle.core.domain.launcher.search.wider.SystemSettingsSource
import com.riffle.core.domain.launcher.search.wider.WiderSearchAction
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import com.riffle.core.domain.launcher.settings.SearchSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Wider search in the Dock's surface (ADR 0003, Amendment 20): the calculator's answer, groups of system settings,
 * contacts and files under the apps, headers only when more than one thing has results, "Show more" past the cap, the
 * inline "Allow" row, and what a tap, a long-press and Enter do. The sources are fixtures with neutral names; the host
 * records what it was asked to do.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class WiderSearchTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val performed = mutableListOf<WiderSearchAction>()
    private val requested = mutableListOf<SearchSourceId>()

    private val settingEntries =
        listOf(
            SystemSettingEntry("notifications", "Notifications", listOf("alerts"), "settings.example.NOTIFICATIONS"),
            SystemSettingEntry("nfc", "NFC", listOf("contactless"), "settings.example.NFC"),
            SystemSettingEntry("wifi", "Wi-Fi", listOf("network"), "settings.example.WIFI"),
            SystemSettingEntry("display", "Display", listOf("notch brightness"), "settings.example.DISPLAY"),
            SystemSettingEntry("storage", "Storage", listOf("notes space"), "settings.example.STORAGE"),
            SystemSettingEntry("sound", "Sound", listOf("notification volume"), "settings.example.SOUND"),
            SystemSettingEntry("battery", "Battery", listOf("notice power"), "settings.example.BATTERY"),
        )

    private val contacts =
        listOf(
            ContactRecord("content://contacts/lookup/example-1", "Noor Example", "+15550100", null),
            ContactRecord("content://contacts/lookup/example-2", "Noah Sample", null, null),
        )

    private val files =
        listOf(
            FileRecord("content://files/example-1", "Notes for Monday.txt", "text/plain", FileKind.DOCUMENT, 20),
            FileRecord("content://files/example-2", "Notebook scan.jpg", "image/jpeg", FileKind.IMAGE, 10),
        )

    private fun sources(
        withContacts: Boolean = true,
        withFiles: Boolean = false,
    ): List<SearchSource> =
        listOfNotNull(
            CalculatorSource(),
            SystemSettingsSource({ settingEntries }),
            ContactsSource(ContactsReader { _, _ -> contacts }).takeIf { withContacts },
            FilesSource(FilesReader { _, _ -> files }).takeIf { withFiles },
        )

    private fun state(
        query: String,
        search: SearchSettings = SearchSettings(),
        apps: List<com.riffle.core.domain.launcher.apps.InstalledApp> = listOf(ScreenshotFixtures.notes),
    ): LauncherShellState {
        val base =
            ScreenshotFixtures.homeState().copy(
                destination = ShellDestination.SEARCH,
                searchQuery = query,
                searchResults = apps,
            )
        val settings = base.launcherSettings
        return base.copy(
            launcherSettings =
                settings.copy(
                    search = search,
                    motion = settings.motion.copy(reducedMotionPreference = ReducedMotionPreference.ON),
                ),
        )
    }

    private fun show(
        state: LauncherShellState,
        granted: Set<SearchSourceId> = emptySet(),
        sources: List<SearchSource> = sources(),
    ) {
        val host =
            StaticWiderSearchHost(
                sources = sources,
                granted = granted,
                onPerform = { performed += it },
                onRequest = { requested += it },
            )
        composeRule.setContent {
            CompositionLocalProvider(com.riffle.app.launcher.search.wider.LocalWiderSearchHost provides host) {
                ScreenshotBackdrop {
                    LauncherShellContent(
                        state = state,
                        appIconLoader = SolidColorAppIconLoader(),
                        stream = emptyStreamHost(),
                        onAction = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun waitFor(tag: String) =
        composeRule.waitUntil(WAIT_MILLIS) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }

    private fun row(key: String) = WIDER_ROW_TEST_TAG_PREFIX + key

    private fun gone(tag: String) = composeRule.onAllNodesWithTag(tag).assertCountEquals(0)

    @Test
    fun aSumIsAnsweredAndATapCopiesTheAnswer() {
        show(state("12 × 3 + 4", apps = emptyList()))
        waitFor(row("calc:12 × 3 + 4"))
        composeRule.onNodeWithTag(row("calc:12 × 3 + 4")).performClick()
        assertEquals(listOf<WiderSearchAction>(WiderSearchAction.Copy("40")), performed)
        composeRule.captureScreen()
    }

    @Test
    fun theLongPressAndTheTrailingButtonCopyTheSumWithItsAnswer() {
        show(state("12*3+4", apps = emptyList()))
        waitFor(row("calc:12*3+4"))
        composeRule.onNodeWithTag(row("calc:12*3+4")).performTouchInput { longClick() }
        composeRule.onNodeWithTag(WIDER_COPY_BOTH_TEST_TAG).performClick()
        assertEquals(
            listOf<WiderSearchAction>(
                WiderSearchAction.Copy("12*3+4 = 40"),
                WiderSearchAction.Copy("12*3+4 = 40"),
            ),
            performed,
        )
    }

    @Test
    fun aUnitConversionIsAnswered() {
        show(state("12 km in miles", apps = emptyList()))
        waitFor(row("unit:12 km"))
        composeRule.captureScreen()
        composeRule.onNodeWithTag(row("unit:12 km")).performClick()
        assertEquals(listOf<WiderSearchAction>(WiderSearchAction.Copy("7.456454 mi")), performed)
    }

    @Test
    fun enterCopiesTheAnswer() {
        show(state("2+2", apps = emptyList()))
        waitFor(row("calc:2+2"))
        composeRule.onNodeWithTag(DOCK_SEARCH_FIELD_TEST_TAG).performImeAction()
        assertEquals(listOf<WiderSearchAction>(WiderSearchAction.Copy("4")), performed)
    }

    @Test
    fun anAppQueryShowsNoWiderRowsAndNoHeaders() {
        show(state("cal", search = SearchSettings(contacts = false)))
        composeRule.waitForIdle()
        gone(WIDER_HEADER_TEST_TAG_PREFIX + SearchSourceId.CONTACTS.name)
        gone(WIDER_HEADER_TEST_TAG_PREFIX + SearchSourceId.SYSTEM_SETTINGS.name)
    }

    @Test
    fun appsSettingsAndContactsAreGroupedWithHeadersLightTheme() = mixed()

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun appsSettingsAndContactsAreGroupedWithHeadersDarkTheme() = mixed()

    private fun mixed() {
        show(
            state("no", search = SearchSettings(contacts = true, resultsPerGroup = 3)),
            granted = setOf(SearchSourceId.CONTACTS),
        )
        waitFor(row("contact:content://contacts/lookup/example-1"))
        waitFor(row("system-setting:notifications"))
        composeRule.onNodeWithTag(WIDER_HEADER_TEST_TAG_PREFIX + SearchSourceId.CONTACTS.name).assertExists()
        composeRule.onNodeWithTag(WIDER_HEADER_TEST_TAG_PREFIX + SearchSourceId.SYSTEM_SETTINGS.name).assertExists()
        composeRule.captureScreen()
    }

    @Test
    fun aGroupPastTheCapSaysShowMoreAndTheTapOpensIt() {
        show(state("no", search = SearchSettings(contacts = false, resultsPerGroup = 3), apps = emptyList()))
        waitFor(row("system-setting:notifications"))
        gone(row("system-setting:battery"))
        val more = WIDER_SHOW_MORE_TEST_TAG_PREFIX + SearchSourceId.SYSTEM_SETTINGS.name
        composeRule.onNodeWithTag(more).performClick()
        waitFor(row("system-setting:battery"))
        gone(more)
    }

    @Test
    fun theOrderOfTheGroupsFollowsTheSetting() {
        val order = listOf(SearchSourceId.CONTACTS, SearchSourceId.SYSTEM_SETTINGS, SearchSourceId.FILES)
        show(
            state("no", search = SearchSettings(contacts = true, groupOrder = order)),
            granted = setOf(SearchSourceId.CONTACTS),
        )
        waitFor(row("contact:content://contacts/lookup/example-1"))
        waitFor(row("system-setting:notifications"))
        val contactTop =
            composeRule.onNodeWithTag(row("contact:content://contacts/lookup/example-1"))
                .fetchSemanticsNode().boundsInRoot.top
        val settingTop =
            composeRule.onNodeWithTag(row("system-setting:notifications")).fetchSemanticsNode().boundsInRoot.top
        // Results grow up from the field: the first group stands nearest the apps, so lowest.
        assertTrue("contacts first means lower on screen", contactTop > settingTop)
    }

    @Test
    fun aContactWithANumberCanBeCalledAndMessagedAndOneWithoutCannot() {
        show(
            state("noor", search = SearchSettings(contacts = true, systemSettings = false), apps = emptyList()),
            granted = setOf(SearchSourceId.CONTACTS),
        )
        waitFor(row("contact:content://contacts/lookup/example-1"))
        composeRule.onNodeWithTag(WIDER_CALL_TEST_TAG).performClick()
        composeRule.onNodeWithTag(WIDER_MESSAGE_TEST_TAG).performClick()
        composeRule.onNodeWithTag(row("contact:content://contacts/lookup/example-1")).performClick()
        assertEquals(
            listOf(
                WiderSearchAction.Dial("+15550100"),
                WiderSearchAction.Message("+15550100"),
                WiderSearchAction.OpenContact("content://contacts/lookup/example-1"),
            ),
            performed,
        )
    }

    @Test
    fun aContactWithoutANumberHasNoCallOrMessageButton() {
        show(
            state("noah", search = SearchSettings(contacts = true, systemSettings = false), apps = emptyList()),
            granted = setOf(SearchSourceId.CONTACTS),
        )
        waitFor(row("contact:content://contacts/lookup/example-2"))
        gone(WIDER_CALL_TEST_TAG)
        gone(WIDER_MESSAGE_TEST_TAG)
    }

    @Test
    fun withContactsOnAndNoPermissionOneAllowRowStandsInItsPlaceAndNothingIsRequestedBeforeTheTap() {
        show(state("no", search = SearchSettings(contacts = true)))
        waitFor(WIDER_ALLOW_TEST_TAG_PREFIX + SearchSourceId.CONTACTS.name)
        assertEquals(emptyList<SearchSourceId>(), requested)
        gone(row("contact:content://contacts/lookup/example-1"))
        // Growing up, the groups stand in a short panel that opens at its bottom: the person scrolls to the top one.
        val allow = composeRule.onNodeWithTag(WIDER_ALLOW_TEST_TAG_PREFIX + SearchSourceId.CONTACTS.name)
        allow.performScrollTo()
        composeRule.captureScreen()
        allow.performClick()
        assertEquals(listOf(SearchSourceId.CONTACTS), requested)
    }

    @Test
    fun withContactsOffThereIsNoAllowRowAndNoContacts() {
        show(state("no", search = SearchSettings(contacts = false)))
        composeRule.waitForIdle()
        gone(WIDER_ALLOW_TEST_TAG_PREFIX + SearchSourceId.CONTACTS.name)
        gone(row("contact:content://contacts/lookup/example-1"))
    }

    @Test
    fun filesWithoutAccessOfferTheAllowRowAndTheFilesAppRow() {
        show(
            state("notes", search = SearchSettings(files = true, systemSettings = false), apps = emptyList()),
            sources = sources(withContacts = false, withFiles = true).map { it },
            granted = emptySet(),
        )
        waitFor(WIDER_ALLOW_TEST_TAG_PREFIX + SearchSourceId.FILES.name)
        waitFor(row("files-app"))
        composeRule.onNodeWithTag(row("files-app")).performClick()
        assertEquals(listOf<WiderSearchAction>(WiderSearchAction.OpenFilesApp("notes")), performed)
    }

    @Test
    fun aSwitchedOffSourceIsNeverQueried() {
        show(state("no", search = SearchSettings(systemSettings = false, contacts = false), apps = emptyList()))
        composeRule.waitForIdle()
        gone(row("system-setting:notifications"))
    }

    private companion object {
        const val WAIT_MILLIS = 10_000L
    }
}
