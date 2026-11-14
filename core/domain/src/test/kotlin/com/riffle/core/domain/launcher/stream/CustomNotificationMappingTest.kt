package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemInput
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CustomNotificationMappingTest {
    private fun text(
        value: String,
        size: Float = 14f,
        bold: Boolean = false,
        clickable: Boolean = false,
    ) = CollectedLayoutText(value, size, bold, clickable)

    @Test
    fun `the largest text is the title and the others are the lines in drawing order`() {
        val extracted =
            CustomNotificationMapping.map(
                listOf(text("3 stops away"), text("Get off at Angel", 20f), text("Northern line")),
            )
        assertEquals("Get off at Angel", extracted.title)
        assertEquals(listOf("3 stops away", "Northern line"), extracted.lines)
    }

    @Test
    fun `a bold text wins a tie in size and the first wins a tie in both`() {
        assertEquals("Bold", CustomNotificationMapping.map(listOf(text("Plain"), text("Bold", bold = true))).title)
        assertEquals("First", CustomNotificationMapping.map(listOf(text("First"), text("Second"))).title)
    }

    @Test
    fun `repeats, blanks and the title said again are not lines, and the lines are capped`() {
        val extracted =
            CustomNotificationMapping.map(
                listOf(
                    text("Title", 20f),
                    text("  "),
                    text("a"),
                    text("a"),
                    text("Title"),
                    text("b"),
                    text("c"),
                    text("d"),
                ),
                maxLines = 3,
            )
        assertEquals("Title", extracted.title)
        assertEquals(listOf("a", "b", "c"), extracted.lines)
    }

    @Test
    fun `a layout's buttons are actions, not lines, and never its title`() {
        val extracted =
            CustomNotificationMapping.map(listOf(text("END TRIP", 22f, clickable = true), text("Walk 200 m")))
        assertEquals("Walk 200 m", extracted.title)
        assertEquals(emptyList(), extracted.lines)
    }

    @Test
    fun `a highlighted line stays a line and whitespace is tidied`() {
        val extracted = CustomNotificationMapping.map(listOf(text("Now", 18f), text("Delayed\n 4 min", bold = true)))
        assertEquals(listOf("Delayed 4 min"), extracted.lines)
    }

    @Test
    fun `no text at all extracts nothing`() {
        assertTrue(CustomNotificationMapping.map(emptyList()).isEmpty)
        assertTrue(CustomNotificationMapping.map(listOf(text("Next", clickable = true))).isEmpty)
    }

    // Through the item and the content mapper -----------------------------------------------------------------------

    private val custom =
        CustomNotificationContent(
            title = "Get off at Angel",
            lines = listOf("3 stops away", "Northern line"),
            bigLines = listOf("3 stops away", "Northern line", "Arrive 14:32"),
            thumbnail = ItemImageHandle("notification-custom:personal:nav"),
            revision = 4,
        )

    private fun content(
        custom: CustomNotificationContent?,
        levels: SourceContentLevels = SourceContentLevels.ALL_SHOWN,
        visibility: AppProfileContentVisibility = AppProfileContentVisibility.VISIBLE,
    ): NotificationContent {
        val items =
            NotificationItemMapper().notificationItems(
                NotificationItemInput(
                    notifications =
                        listOf(
                            LauncherNotification(
                                key = LauncherNotificationKey("nav"),
                                packageName = AppPackageName("com.example.nav"),
                                postedAtEpochMillis = 1_000L,
                            ),
                        ),
                    nowEpochMillis = 2_000L,
                    profileContentVisibility = mapOf(AppProfile.personal().id to visibility),
                    appLabel = { "Navigator" },
                    customContent = { custom },
                ),
            )
        return assertIs<NotificationContent>(StreamContentMapper.map(items.single(), levels))
    }

    @Test
    fun `the extracted title leads the card and the lines are its body, the open card's from the big layout`() {
        val mapped = content(custom)
        assertEquals(custom, mapped.custom)
        assertEquals("Get off at Angel", mapped.headline)
        assertEquals("Navigator", mapped.secondaryLabel)
        assertEquals(NotificationBody.Custom(custom), mapped.body)
    }

    @Test
    fun `a layout with no text is embedded and the card is titled with the app`() {
        val mapped = content(CustomNotificationContent(embed = true, revision = 1))
        assertEquals("Navigator", mapped.headline)
        assertEquals(true, (mapped.body as NotificationBody.Custom).content.embed)
    }

    @Test
    fun `without a layout of its own the notification has no custom content`() {
        val mapped = content(null)
        assertNull(mapped.custom)
        assertEquals(NotificationBody.None, mapped.body)
        assertEquals("Navigator", mapped.headline)
    }

    @Test
    fun `hide text extracts and embeds nothing and the card is hidden`() {
        val levels = SourceContentLevels().with(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT)
        for (hidden in listOf(content(custom, levels), content(custom.copy(embed = true), levels))) {
            assertNull(hidden.custom)
            assertNull(hidden.headline)
            assertEquals(true, hidden.hidden)
            assertEquals(NotificationBody.None, hidden.body)
        }
        // A quiet profile's notification is withheld where it is mapped: nothing of the layout is asked for.
        val quiet = content(custom, visibility = AppProfileContentVisibility.REDACTED_QUIET)
        assertNull(quiet.custom)
        assertNull(quiet.headline)
    }

    @Test
    fun `hide body keeps the title and drops the lines, the picture and the embedded layout`() {
        val levels = SourceContentLevels().with(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_BODY)
        val mapped = content(custom.copy(embed = true), levels)
        assertEquals("Get off at Angel", mapped.headline)
        assertEquals(emptyList(), mapped.custom?.lines)
        assertEquals(emptyList(), mapped.custom?.bigLines)
        assertNull(mapped.custom?.thumbnail)
        assertEquals(false, mapped.custom?.embed)
        assertEquals(NotificationBody.None, mapped.body)
    }

    @Test
    fun `the text the app filled in wins over its layout`() {
        val mapped = content(custom).copy(text = "Your trip")
        assertEquals(NotificationBody.Text("Your trip"), mapped.body)
    }
}
