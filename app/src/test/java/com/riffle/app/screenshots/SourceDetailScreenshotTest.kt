package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.ExclusionsPageCallbacks
import com.riffle.app.launcher.ExclusionsSettingsContent
import com.riffle.app.launcher.SOURCE_CONTENT_LEVEL_TAG
import com.riffle.app.launcher.SOURCE_DETAIL_STATUS_TAG
import com.riffle.app.launcher.SOURCE_DETAIL_SWITCH_TAG
import com.riffle.app.launcher.SOURCE_ICS_ADD_TAG
import com.riffle.app.launcher.SOURCE_OPEN_ALL_RULES_TAG
import com.riffle.app.launcher.SOURCE_SEARCH_GLOBAL_TAG
import com.riffle.app.launcher.SOURCE_SEARCH_MODEL_TAG
import com.riffle.app.launcher.SOURCE_USED_BY_EMPTY_TAG
import com.riffle.app.launcher.SourceDetailScaffold
import com.riffle.app.launcher.SourceHiddenAppsIntro
import com.riffle.app.launcher.SourceIcsSections
import com.riffle.app.launcher.SourceNotificationsIntro
import com.riffle.app.launcher.SourceOpenAllRulesRow
import com.riffle.app.launcher.SourceSearchSections
import com.riffle.app.launcher.exclusionRowTestTag
import com.riffle.app.launcher.ics.IcsFeedRow
import com.riffle.app.launcher.ics.IcsFeedsUiState
import com.riffle.app.launcher.sourcePlaceTestTag
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.SourcePlace
import com.riffle.core.domain.launcher.workspace.settings.SourcePlaceKind
import com.riffle.core.domain.launcher.workspace.settings.SourceRow
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private val AppSources = setOf(SourceIds.ALL_APPS, SourceIds.RECENT_APPS, SourceIds.QUICK_ACTIONS)

/**
 * The per-source detail pages over fixed fake rows, places and rules (no controllers, and no text fields: a focused
 * text field's cursor blink hangs the screenshot run, so the RSS page's embedded add form and the ICS add form are
 * not drawn here). Nothing here requests a permission: Allow only reports the tap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class SourceDetailScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val phone = HomeLayoutDeviceClass.PHONE
    private val foldable = HomeLayoutDeviceClass.FOLDABLE
    private val toggles = mutableListOf<Boolean>()
    private val allowed = mutableListOf<SourceId>()
    private val edited = mutableListOf<WorkspaceId>()
    private var openedRules = 0
    private var openedFeeds = 0

    private fun place(
        layout: HomeLayoutDeviceClass,
        kind: SourcePlaceKind,
        page: Int?,
        widget: Int? = null,
        saved: String? = null,
    ) = SourcePlace(
        layout = layout,
        workspaceId = WorkspaceId("w-${layout.name}"),
        workspaceName = if (layout == phone) "Nova" else "Nova (unfolded)",
        containerId = ContainerId("c-$page-$widget"),
        kind = kind,
        pageNumber = page,
        widgetNumber = widget,
        savedLensName = saved,
    )

    private val places =
        listOf(
            place(phone, SourcePlaceKind.PAGE, 1),
            place(phone, SourcePlaceKind.WIDGET, 2, widget = 1, saved = "Mail"),
            place(phone, SourcePlaceKind.DOCK, null),
            place(foldable, SourcePlaceKind.PAGE_SET, 3),
        )

    private fun row(
        id: SourceId,
        title: String,
        status: SourceStatus = SourceStatus.READY,
        enabled: Boolean = true,
        description: String = "What this source shows.",
    ) = SourceRow(id, title, description, status, enabled)

    private val notifications =
        row(
            SourceIds.NOTIFICATIONS,
            "Notifications",
            SourceStatus.NEEDS_PERMISSION,
            description = "Your notifications.",
        )
    private val apps = row(SourceIds.ALL_APPS, "Apps", description = "Every app you can launch.")

    @Test
    fun notificationsCompact() {
        renderNotifications()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun notificationsCompactDark() {
        renderNotifications()
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun notificationsCompactLargeFont() {
        renderNotifications()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun notificationsUnfolded() {
        renderNotifications()
    }

    @Test
    fun notificationsShowOnlyNotificationRulesAndAComingLaterContentLevel() {
        renderNotifications()

        composeRule.onNodeWithTag(SOURCE_CONTENT_LEVEL_TAG).performScrollTo().assertExists()
        composeRule.onNodeWithText("Coming later", substring = true).assertExists()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule)).performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.appRule)).assertDoesNotExist()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.feedRule)).assertDoesNotExist()
    }

    @Test
    fun theFullRulesPageIsOneTapAway() {
        renderNotifications()

        composeRule.onNodeWithTag(SOURCE_OPEN_ALL_RULES_TAG).performScrollTo().performClick()

        assertEquals(1, openedRules)
    }

    @Test
    fun allowOnlyReportsTheTapToTheExistingFlow() {
        renderNotifications()

        assertEquals(emptyList<SourceId>(), allowed)
        composeRule.onNodeWithText("Allow notification access").performScrollTo().performClick()

        assertEquals(listOf(SourceIds.NOTIFICATIONS), allowed)
    }

    @Test
    fun hiddenAppsShowsOnlyTheAppSourcesRules() {
        render(apps) {
            SourceHiddenAppsIntro()
            ExclusionsSettingsContent(
                model = ExclusionsSettingsFixtures.populated,
                viewed = phone,
                tabs = ExclusionsSettingsFixtures.tabs,
                callbacks = ExclusionsPageCallbacks(),
                sources = AppSources,
            )
        }

        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.appRule)).performScrollTo()
            .assertExists()
        composeRule.onNodeWithTag(exclusionRowTestTag(ExclusionsSettingsFixtures.textRule)).assertDoesNotExist()
    }

    @Test
    fun searchExplainsTheQueryModelAndNeverShowsAQuery() {
        render(row(SourceIds.SEARCH, "Search")) { SourceSearchSections(sharedQuerySet = false) }

        composeRule.onNodeWithTag(SOURCE_SEARCH_MODEL_TAG).performScrollTo().assertExists()
        composeRule.onNodeWithTag(SOURCE_SEARCH_GLOBAL_TAG).performScrollTo().assertExists()
        composeRule.onNodeWithText("No shared query is set", substring = true).assertExists()
    }

    @Test
    fun searchWithASharedQuerySaysSoWithoutItsText() {
        render(row(SourceIds.SEARCH, "Search")) { SourceSearchSections(sharedQuerySet = true) }

        composeRule.onNodeWithText("A shared query is set", substring = true).performScrollTo().assertExists()
    }

    @Test
    fun calendarFeedsListTheFeedsAndLinkToTheAddFlow() {
        val feeds =
            IcsFeedsUiState(
                loaded = true,
                rows = listOf(IcsFeedRow(IcsFeedId("work"), "Work", "calendar.example.com", true, false, null, null)),
            )
        render(row(SourceIds.ICS, "Calendar feeds")) {
            SourceIcsSections(
                state = feeds,
                onRefresh = {},
                onToggle = { _, _ -> },
                onRemove = {},
                onOpenFeeds = { openedFeeds++ },
            )
        }

        composeRule.onNodeWithTag(SOURCE_ICS_ADD_TAG).performScrollTo().performClick()

        assertEquals(1, openedFeeds)
    }

    @Test
    fun statusOnlySourcesShowStatusAndTheirOwnPermissionAffordance() {
        render(row(SourceIds.CALENDAR, "Calendar", SourceStatus.NEEDS_PERMISSION))

        composeRule.onNodeWithTag(SOURCE_DETAIL_STATUS_TAG)
            .assert(hasContentDescription("Calendar, Needs permission"))
        assertEquals(emptyList<SourceId>(), allowed)
        composeRule.onNodeWithText("Private and confidential events show no text.").assertExists()
    }

    @Test
    fun theSwitchTurnsTheSourceOffAndOn() {
        render(apps)

        composeRule.onNodeWithTag(SOURCE_DETAIL_SWITCH_TAG).performScrollTo().performClick()

        assertEquals(listOf(false), toggles)
    }

    @Test
    fun aPlaceOnThisDevicesLayoutOpensItsWorkspace() {
        render(apps)

        composeRule.onNodeWithTag(sourcePlaceTestTag(0)).performScrollTo().performClick()

        assertEquals(listOf(WorkspaceId("w-PHONE")), edited)
    }

    @Test
    fun aPlaceOnAnotherLayoutSaysWhyItCannotBeEditedHere() {
        render(apps)

        composeRule.onNodeWithTag(sourcePlaceTestTag(3)).performScrollTo()
            .assert(hasContentDescription("Switch Settings to this device's layout", substring = true))
        assertEquals(emptyList<WorkspaceId>(), edited)
    }

    @Test
    fun aSourceNoPageReadsSaysSo() {
        render(apps, placesOverride = emptyList())

        composeRule.onNodeWithTag(SOURCE_USED_BY_EMPTY_TAG).performScrollTo().assertExists()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun usedByListsPlacesWithTheirLayoutWhenMoreThanOneLayoutReadsTheSource() {
        render(apps)

        composeRule.onNodeWithText("Foldable (unfolded) > Nova (unfolded) > Page set (page 3)").assertExists()
    }

    private fun renderNotifications() {
        render(notifications) {
            SourceNotificationsIntro()
            ExclusionsSettingsContent(
                model = ExclusionsSettingsFixtures.populated,
                viewed = phone,
                tabs = ExclusionsSettingsFixtures.tabs,
                callbacks = ExclusionsPageCallbacks(),
                sources = setOf(SourceIds.NOTIFICATIONS),
            )
            SourceOpenAllRulesRow(onClick = { openedRules++ })
        }
    }

    private fun render(
        row: SourceRow,
        placesOverride: List<SourcePlace>? = places,
        specific: @Composable ColumnScope.() -> Unit = {},
    ) {
        composeRule.setContent {
            ScreenshotBackdrop {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    SourceDetailScaffold(
                        row = row,
                        calendarAccess = CalendarAccessStatus.NOT_GRANTED,
                        places = placesOverride,
                        showLayoutNames = true,
                        currentLayout = phone,
                        onToggle = { toggles += it },
                        onAllow = { allowed += it },
                        onEdit = { edited += it },
                        specific = specific,
                    )
                }
            }
        }
        composeRule.captureScreen()
    }
}
