package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.stream.LiveNotificationRule.Facts
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemInput
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveNotificationRuleTest {
    @Test
    fun `an ongoing notification with a layout of its own, a progress bar or a live category is live`() {
        assertTrue(LiveNotificationRule.isLive(Facts(ongoing = true, customViews = true)))
        assertTrue(LiveNotificationRule.isLive(Facts(ongoing = true, progress = true)))
        for (category in listOf(
            NotificationCategory.NAVIGATION,
            NotificationCategory.TRANSPORT,
            NotificationCategory.PROGRESS,
        )) {
            assertTrue(LiveNotificationRule.isLive(Facts(ongoing = true, category = category)), category.name)
        }
    }

    @Test
    fun `an ongoing notification with none of those is not live`() {
        assertFalse(LiveNotificationRule.isLive(Facts(ongoing = true)))
        assertFalse(LiveNotificationRule.isLive(Facts(ongoing = true, category = NotificationCategory.SERVICE)))
        assertFalse(LiveNotificationRule.isLive(Facts(ongoing = true, category = NotificationCategory.MESSAGE)))
    }

    @Test
    fun `nothing that is not ongoing is live`() {
        assertFalse(LiveNotificationRule.isLive(Facts(ongoing = false, customViews = true, progress = true)))
        assertFalse(LiveNotificationRule.isLive(Facts(ongoing = false, category = NotificationCategory.NAVIGATION)))
    }

    // Through the item, the content and the card's controls ---------------------------------------------------------

    private val own =
        listOf(NotificationOwnAction("provider:0", "End Trip"), NotificationOwnAction("provider:1", "Next"))

    private fun content(
        live: Boolean,
        levels: SourceContentLevels = SourceContentLevels.ALL_SHOWN,
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
                    profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
                    ownActions = { own },
                    isLive = { live },
                ),
            )
        return StreamContentMapper.map(items.single(), levels) as NotificationContent
    }

    @Test
    fun `a live notification carries its own buttons on the browse card, in its order`() {
        val live = content(live = true)
        assertTrue(live.live)
        assertEquals(
            listOf(CardAction.Own("provider:0", "End Trip"), CardAction.Own("provider:1", "Next")),
            StreamCardActions.onBrowseCard(live),
        )
        // They stay on the open card as well.
        assertTrue(StreamCardActions.forContent(live).containsAll(StreamCardActions.onBrowseCard(live)))
    }

    @Test
    fun `any other notification keeps its buttons for the open card`() {
        val plain = content(live = false)
        assertFalse(plain.live)
        assertEquals(emptyList(), StreamCardActions.onBrowseCard(plain))
        assertEquals(2, StreamCardActions.forContent(plain).filterIsInstance<CardAction.Own>().size)
    }

    @Test
    fun `a live notification whose text is hidden shows no buttons`() {
        val hidden =
            content(live = true, SourceContentLevels().with(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT))
        assertEquals(emptyList(), StreamCardActions.onBrowseCard(hidden))
    }

    @Test
    fun `no other kind of content has buttons on its browse card`() {
        assertEquals(emptyList(), StreamCardActions.onBrowseCard(TaskContent(StreamContentId("t"), "Task")))
    }
}
