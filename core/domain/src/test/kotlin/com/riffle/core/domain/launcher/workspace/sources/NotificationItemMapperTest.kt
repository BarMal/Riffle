package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.notifications.NotificationHideRuleId
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotificationItemMapperTest {
    private val mapper = NotificationItemMapper()
    private val now = 1_000_000L
    private val profileKey = WorkspaceSourceIds.APP_PROFILE_EXT
    private val personal = AppProfile.personal().id
    private val work = AppProfile.work().id
    private val visible = mapOf(personal to AppProfileContentVisibility.VISIBLE)

    private fun notification(
        key: String,
        pkg: String = "chat",
        profile: AppProfileId = personal,
        title: String = "Title $key",
        posted: Long = now - 10,
        dismiss: Boolean = true,
        media: Boolean = false,
    ): LauncherNotification =
        LauncherNotification(
            key = LauncherNotificationKey(key),
            packageName = AppPackageName(pkg),
            profileId = profile,
            canDismiss = dismiss,
            isMediaSession = media,
            title = title,
            text = "Text $key",
            postedAtEpochMillis = posted,
        )

    private fun input(
        vararg notifications: LauncherNotification,
        visibility: Map<AppProfileId, AppProfileContentVisibility> = visible,
        rules: List<NotificationHideRule> = emptyList(),
    ) = NotificationItemInput(
        notifications = notifications.toList(),
        nowEpochMillis = now,
        hideRules = rules,
        profileContentVisibility = visibility,
        appLabel = { pkg -> if (pkg.value == "chat") "Chat" else null },
    )

    @Test
    fun `items are grouped by app with app name and visible content`() {
        val items =
            mapper.notificationItems(
                input(notification("a").copy(largeIconPngBase64 = "AAAA"), notification("b", pkg = "mail")),
            )

        assertEquals(2, items.size)
        val chat = items.first { it.groupKey == "chat:personal" }
        assertEquals("Chat", chat.groupLabel)
        assertEquals("Title a", chat.title)
        assertEquals("Text a", chat.body)
        assertEquals(ItemTarget.App("chat", "personal"), chat.target)
        assertEquals(ItemPrivacy.VISIBLE, chat.privacy)
        assertEquals(listOf(ItemAction.Open(), ItemAction.Dismiss()), chat.actions)
        assertEquals("notification-art:personal:a", chat.image?.key)
        assertEquals("mail", items.first { it.groupKey == "mail:personal" }.groupLabel)
        assertTrue(items.all { it.sourceId == SourceIds.NOTIFICATIONS })
    }

    @Test
    fun `undismissable notifications offer no dismiss action`() {
        val item = mapper.notificationItems(input(notification("a", dismiss = false))).single()
        assertEquals(listOf<ItemAction>(ItemAction.Open()), item.actions)
    }

    @Test
    fun `apps are ordered like the existing grouper with high priority first`() {
        val items =
            mapper.notificationItems(
                input(
                    notification("low", pkg = "a").copy(priority = NotificationPriority.LOW),
                    notification("high", pkg = "b").copy(priority = NotificationPriority.HIGH),
                ),
            )
        assertEquals(listOf("b:personal", "a:personal"), items.map(Item::groupKey))
    }

    @Test
    fun `hide rules and stale clearable notifications are dropped`() {
        val rule =
            NotificationHideRule(
                id = NotificationHideRuleId("r"),
                packageName = AppPackageName("chat"),
                profileId = personal,
                kind = NotificationHideRule.Kind.TITLE,
                value = "Title hidden",
            )
        val stale = notification("stale", posted = now - 8L * 24 * 60 * 60 * 1_000)
        val pinned = notification("ongoing", posted = now - 8L * 24 * 60 * 60 * 1_000, dismiss = false)

        val items =
            mapper.notificationItems(
                input(notification("hidden", title = "Title hidden"), stale, pinned, rules = listOf(rule)),
            )

        assertEquals(listOf("notifications:personal:ongoing"), items.map { it.id.value })
    }

    @Test
    fun `locked unavailable and unknown profiles are dropped`() {
        val items =
            mapper.notificationItems(
                input(
                    notification("locked", profile = AppProfileId("locked")),
                    notification("unavailable", profile = AppProfileId("unavailable")),
                    notification("unknown", profile = AppProfileId("unknown")),
                    visibility =
                        mapOf(
                            AppProfileId("locked") to AppProfileContentVisibility.REDACTED_LOCKED,
                            AppProfileId("unavailable") to AppProfileContentVisibility.REDACTED_UNAVAILABLE,
                        ),
                ),
            )
        assertTrue(items.isEmpty())
    }

    @Test
    fun `quiet profile notifications are sensitive and carry no content`() {
        val item =
            mapper.notificationItems(
                input(
                    notification("q", profile = work).copy(largeIconPngBase64 = "AAAA"),
                    visibility = mapOf(work to AppProfileContentVisibility.REDACTED_QUIET),
                ),
            ).single()

        assertEquals(ItemPrivacy.SENSITIVE, item.privacy)
        assertNull(item.title)
        assertNull(item.subtitle)
        assertNull(item.body)
        assertNull(item.image)
        assertTrue(item.actions.isEmpty())
        assertEquals(mapOf(profileKey to ItemExtValue.Text("work")), item.ext)
        assertEquals("chat:work", item.groupKey)
    }

    @Test
    fun `items carry the app profile ext from known types or well known ids`() {
        val custom = AppProfileId("user:10")
        val data =
            input(
                notification("p"),
                notification("w", profile = work),
                notification("c", profile = custom),
                notification("u", profile = AppProfileId("user:11")),
                visibility =
                    listOf(personal, work, custom, AppProfileId("user:11"))
                        .associateWith { AppProfileContentVisibility.VISIBLE },
            ).copy(profileTypes = mapOf(custom to AppProfileType.PRIVATE))

        val byGroup = mapper.notificationItems(data).associateBy { it.groupKey }

        assertEquals(ItemExtValue.Text("personal"), byGroup.getValue("chat:personal").ext[profileKey])
        assertEquals(ItemExtValue.Text("work"), byGroup.getValue("chat:work").ext[profileKey])
        assertEquals(ItemExtValue.Text("private"), byGroup.getValue("chat:user:10").ext[profileKey])
        assertNull(byGroup.getValue("chat:user:11").ext[profileKey])
    }

    @Test
    fun `media session notifications go to media items only`() {
        val data =
            input(
                notification("n"),
                notification("m", pkg = "player", media = true, title = "Song").copy(text = "Artist"),
            )

        val notifications = mapper.notificationItems(data)
        val media = mapper.mediaItems(data).single()

        assertEquals(listOf("n"), notifications.map { it.title?.removePrefix("Title ") })
        assertEquals(SourceIds.MEDIA, media.sourceId)
        assertEquals("Song", media.title)
        assertEquals("Artist", media.subtitle)
        assertNull(media.body)
    }

    @Test
    fun `access status maps to source access without ever granting unknown`() {
        assertEquals(SourceAccess.GRANTED, NotificationAccessStatus.GRANTED.toSourceAccess())
        listOf(NotificationAccessStatus.UNKNOWN, NotificationAccessStatus.NOT_GRANTED, NotificationAccessStatus.REVOKED)
            .forEach { assertEquals(SourceAccess.REQUIRED, it.toSourceAccess()) }
    }

    @Test
    fun `gated state never reads items unless access is granted`() {
        var reads = 0
        val read = {
            reads++
            emptyList<Item>()
        }
        assertEquals(SourceState.PermissionRequired, sourceStateFor(SourceAccess.REQUIRED, read))
        assertEquals(SourceState.Unavailable, sourceStateFor(SourceAccess.UNAVAILABLE, read))
        assertEquals(0, reads)
        assertEquals(SourceState.Ready(emptyList()), sourceStateFor(SourceAccess.GRANTED, read))
        assertEquals(1, reads)
    }
}
