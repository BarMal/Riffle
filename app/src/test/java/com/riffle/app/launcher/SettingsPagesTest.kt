package com.riffle.app.launcher

import com.riffle.app.R
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.search.LauncherSearchProvider
import com.riffle.core.domain.launcher.search.LauncherSearchResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The shape of Settings (docs/product/settings.md): a tree of pages with the eleven places at its first level, every
 * setting on a page that can be reached, every page and setting found by search, and the wording the document fixes.
 */
class SettingsPagesTest {
    private val text = SettingsTestText

    private fun title(page: SettingsPage) = text.text(page.titleRes)

    @Test
    fun labelsSelectedThemeOptionsClearly() {
        assertEquals("Dark", themeOptionLabel("DARK", isSelected = false))
        assertEquals("Dark (selected)", themeOptionLabel("DARK", isSelected = true))
    }

    @Test
    fun theMainPageHasExactlyTheElevenPlacesInOrder() {
        // Layout (per posture, ADR 0003 Amendment 2) joined the original ten places, after the Dock it positions.
        assertEquals(
            listOf(
                "Home",
                "Dock",
                "Layout",
                "Library",
                "Stream",
                "Appearance",
                "Gestures",
                "Motion and haptics",
                "Privacy and access",
                "Backup",
                "About",
            ),
            settingsMainPageRows.map(::title),
        )
        assertEquals(settingsMainPageRows, SettingsPage.MAIN.children())
    }

    @Test
    fun everyPageHasAParentAndTheTreeHasNoOrphansOrCycles() {
        assertEquals(null, SettingsPage.MAIN.parent)
        SettingsPage.entries.filter { it != SettingsPage.MAIN }.forEach { page ->
            assertNotNull("$page has a parent", page.parent)
            // Walking up reaches MAIN in at most the number of pages: no cycle, no detached branch.
            var steps = 0
            var current: SettingsPage? = page
            while (current != null && current != SettingsPage.MAIN) {
                current = current.parent
                assertTrue("$page does not loop", ++steps <= SettingsPage.entries.size)
            }
            assertEquals("$page reaches the main page", SettingsPage.MAIN, current)
        }
    }

    @Test
    fun everyPageIsReachableFromTheMainPageByOpeningRows() {
        val reached = mutableSetOf(SettingsPage.MAIN)
        val queue = ArrayDeque(listOf(SettingsPage.MAIN))
        while (queue.isNotEmpty()) {
            queue.removeFirst().children().filter(reached::add).forEach(queue::addLast)
        }
        assertEquals(SettingsPage.entries.toSet(), reached)
    }

    @Test
    fun theSubPagesLiveWhereTheDocumentPutsThem() {
        assertEquals(SettingsPage.DOCK, SettingsPage.FLOATING_DOCK.parent)
        assertEquals(SettingsPage.LIBRARY, SettingsPage.HIDDEN_APPS.parent)
        assertEquals(
            listOf(
                SettingsPage.STREAM_CARDS,
                SettingsPage.STREAM_NOTIFICATIONS,
                SettingsPage.STREAM_AGENDA,
                SettingsPage.STREAM_TASKS,
                SettingsPage.STREAM_FEEDS,
            ),
            SettingsPage.STREAM.children(),
        )
        assertEquals(SettingsPage.STREAM_NOTIFICATIONS, SettingsPage.HIDDEN_ITEMS.parent)
        assertEquals(SettingsPage.STREAM_AGENDA, SettingsPage.CALENDAR_FEEDS.parent)
    }

    @Test
    fun backAlwaysGoesToTheParentPage() {
        SettingsPage.entries.filter { it != SettingsPage.MAIN }.forEach { page ->
            assertEquals("$page", page.parent, settingsBackTarget(page))
        }
        assertEquals(SettingsPage.MAIN, settingsBackTarget(SettingsPage.MAIN))
        // The one the audit found going to the main page: Hidden apps goes to the Library.
        assertEquals(SettingsPage.LIBRARY, settingsBackTarget(SettingsPage.HIDDEN_APPS))
    }

    @Test
    fun theNamingTableIsUsedForPagesAndSettings() {
        assertEquals("Floating dock", title(SettingsPage.FLOATING_DOCK))
        assertEquals("Hidden items", title(SettingsPage.HIDDEN_ITEMS))
        assertEquals("Hidden apps", title(SettingsPage.HIDDEN_APPS))
        assertEquals("Calendar feeds", title(SettingsPage.CALENDAR_FEEDS))
        assertEquals("Library", title(SettingsPage.LIBRARY))
        assertEquals("Stream", title(SettingsPage.STREAM))
        assertEquals("Colour mode", text.text(SettingKey.APPEARANCE_COLOUR_MODE.labelRes))
        assertEquals("Corners", text.text(SettingKey.APPEARANCE_CORNERS.labelRes))
        assertEquals("Icon size", text.text(SettingKey.DOCK_ICON_SIZE.labelRes))
        assertEquals("Swipes and pinches on Home", text.valueOf("settings_page_gestures_summary"))
    }

    @Test
    fun theRemovedPagesAreGone() {
        val names = SettingsPage.entries.map { it.name }.toSet()
        listOf("SOURCES", "LAYOUT", "PERMISSIONS", "CONTEXTUAL", "RSS", "ICS_FEEDS", "APPS", "WORKSPACES").forEach {
            assertFalse("$it is gone", it in names)
        }
        assertTrue(names.none { it.startsWith("SOURCE_") })
        assertTrue(names.none { it.contains("DEVELOPER") })
    }

    @Test
    fun everySettingIsOnAReachablePageAndHasALabelAndKeywords() {
        SettingKey.entries.forEach { key ->
            assertTrue("${key.name} is on a page below the main page", key.page != SettingsPage.MAIN)
            assertTrue("${key.name} has a label", text.text(key.labelRes).isNotBlank())
            assertTrue("${key.name} has keywords", text.text(key.keywordsRes).isNotBlank())
        }
        // Every page that holds controls holds at least one setting.
        val pagesWithSettings = SettingKey.entries.map { it.page }.toSet()
        listOf(
            SettingsPage.HOME, SettingsPage.DOCK, SettingsPage.FLOATING_DOCK, SettingsPage.LIBRARY,
            SettingsPage.STREAM, SettingsPage.STREAM_CARDS, SettingsPage.STREAM_NOTIFICATIONS,
            SettingsPage.STREAM_AGENDA,
            SettingsPage.CALENDAR_FEEDS, SettingsPage.STREAM_TASKS, SettingsPage.STREAM_FEEDS,
            SettingsPage.APPEARANCE, SettingsPage.GESTURES, SettingsPage.MOTION, SettingsPage.PRIVACY,
            SettingsPage.BACKUP,
        ).forEach { assertTrue("$it holds settings", it in pagesWithSettings) }
    }

    @Test
    fun everySettingKeyIsAnchoredOnItsRowInTheSource() {
        // A setting that is indexed but whose row never marks itself would open its page and not scroll to it.
        val sources =
            File("src/main/java/com/riffle/app/launcher")
                .walkTopDown()
                .filter { it.extension == "kt" && it.name != "SettingKey.kt" }
                .joinToString("\n") { it.readText() }
        SettingKey.entries.forEach { key ->
            val anchored =
                sources.contains("settingAnchor(SettingKey.${key.name})") ||
                    sources.contains("key = SettingKey.${key.name},") ||
                    sources.contains("anchor = SettingKey.${key.name},") ||
                    Regex("->\\s*SettingKey\\.${key.name}\\b").containsMatchIn(sources)
            assertTrue("${key.name} is anchored on a row", anchored)
        }
    }

    @Test
    fun theSearchIndexHoldsEveryPageAndEverySettingOnce() {
        val index = settingsSearchIndex(text)

        assertEquals(index.size, index.map { it.id }.toSet().size)
        SettingsPage.entries.filter { it != SettingsPage.MAIN }.forEach { page ->
            assertTrue("$page is indexed", index.any { it.page == page && it.key == null })
        }
        SettingKey.entries.forEach { key ->
            assertTrue("$key is indexed", index.any { it.key == key && it.page == key.page })
        }
        assertEquals(SettingsPage.entries.size - 1 + SettingKey.entries.size, index.size)
    }

    @Test
    fun everyIndexedEntryIsFoundByItsOwnLabel() {
        val index = settingsSearchIndex(text)

        index.forEach { entry ->
            assertTrue(
                "${entry.id} is found by '${entry.label}'",
                index.matching(entry.label).any { it.id == entry.id },
            )
        }
    }

    @Test
    fun searchMatchesLabelsKeywordsAndPathsAndOpensTheRightPage() {
        val index = settingsSearchIndex(text)

        assertEquals(SettingKey.DOCK_POSITION, index.matching("dock edge").first { it.key != null }.key)
        assertTrue(index.matching("wifi").any { it.key == SettingKey.FEEDS_WIFI_ONLY })
        assertTrue(index.matching("hex").any { it.key == SettingKey.APPEARANCE_CUSTOM_ACCENT })
        assertTrue(index.matching("pinch").any { it.key == SettingKey.GESTURE_PINCH_IN })
        assertTrue(index.matching("stream notifications").any { it.page == SettingsPage.STREAM_NOTIFICATIONS })
        assertTrue(index.matching("").isEmpty())
        assertTrue(index.matching("zzzzqq").isEmpty())
        assertEquals(
            SettingsPage.STREAM_AGENDA,
            index.matching("include calendar feeds").first { it.key == SettingKey.AGENDA_INCLUDE_FEEDS }.page,
        )
    }

    @Test
    fun theAliasThatMatchedNothingIsGone() {
        // "double tap" was an alias of Gestures although no gesture is a double tap.
        val index = settingsSearchIndex(text)
        assertTrue(index.matching("double tap").isEmpty())
    }

    @Test
    fun searchSummaryOnlyShowsWhileSearching() {
        assertEquals(null, settingsSearchSummaryText(text, "  ", 3))
        assertEquals("1 setting matching \"dock\"", settingsSearchSummaryText(text, "dock", 1))
        assertEquals("4 settings matching \"dock\"", settingsSearchSummaryText(text, "dock", 4))
    }

    @Test
    fun theMainRowsShowTheStateThatMatters() {
        val status =
            SettingsOverviewStatus(
                homeRoleStatus = HomeRoleStatus.DEFAULT_HOME,
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                overlayDockPermissionStatus = OverlayDockPermissionStatus.GRANTED,
                hiddenAppCount = 3,
            )

        assertEquals(
            "Notifications not allowed · Home set · Floating dock allowed",
            status.rowSummary(SettingsPage.PRIVACY, text),
        )
        assertTrue(status.rowSummary(SettingsPage.LIBRARY, text).contains("3 hidden apps"))
        assertEquals(text.text(R.string.settings_page_dock_summary), status.rowSummary(SettingsPage.DOCK, text))
    }

    @Test
    fun launcherSearchOffersEverySettingAndPageAndEachOpensItsTarget() {
        val entries = settingsLauncherSearchEntries(text)

        assertEquals(SettingsPage.entries.size - 1 + SettingKey.entries.size, entries.size)
        entries.forEach { entry ->
            val target = requireNotNull(entry.id.settingsTarget()) { "${entry.id.value} resolves" }
            assertEquals(entry.id.value.substringBefore('#'), target.page.name.lowercase())
        }
        val position = entries.first { it.id.value == "dock#DOCK_POSITION" }
        assertEquals(SettingsTarget(SettingsPage.DOCK, SettingKey.DOCK_POSITION), position.id.settingsTarget())
        assertEquals(
            SettingsTarget(SettingsPage.FLOATING_DOCK),
            entries.first {
                it.id.value == "floating_dock"
            }.id.settingsTarget(),
        )
    }

    @Test
    fun launcherSearchFindsASettingByItsKeyword() {
        val hits =
            LauncherSearchProvider()
                .search(query = "spotlight", apps = emptyList(), settingsEntries = settingsLauncherSearchEntries(text))
                .filterIsInstance<LauncherSearchResult.Setting>()

        assertTrue(hits.any { it.entry.id.value == "appearance#APPEARANCE_SPOTLIGHT" })
    }

    @Test
    fun theTextHolderOffersNothingUntilInstalledAndThenTheIndex() {
        SettingsTextHolder.install(null as SettingsText?)
        assertTrue(settingsLauncherSearchEntries().isEmpty())
        SettingsTextHolder.install(text)
        assertFalse(settingsLauncherSearchEntries().isEmpty())
        SettingsTextHolder.install(null as SettingsText?)
    }

    @Test
    fun settingsTextUsesBritishSpelling() {
        val americanWords =
            Regex(
                "\\b(colors?|colorful|center(ed)?|customize|gray|favorites?|minimize|behavior)\\b",
                RegexOption.IGNORE_CASE,
            )
        SettingsTestText.values
            .filterKeys { it.startsWith("setting") }
            .forEach { (name, value) ->
                assertFalse("$name uses American spelling: $value", americanWords.containsMatchIn(value))
            }
    }

    @Test
    fun layoutPageUsesFoldedAndUnfoldedTabsForFoldableDevices() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutPageTabs(setOf(HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.FOLDABLE))
                .map { tab -> tab.label },
        )
    }

    @Test
    fun layoutPageShowsFoldedAndUnfoldedTabsForSingleLayoutDevices() {
        assertEquals(
            listOf("Folded", "Unfolded"),
            settingsLayoutPageTabs(setOf(HomeLayoutDeviceClass.PHONE)).map { tab -> tab.label },
        )
    }
}
