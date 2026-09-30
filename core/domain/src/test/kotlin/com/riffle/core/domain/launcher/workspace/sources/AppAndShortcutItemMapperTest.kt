package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppAndShortcutItemMapperTest {
    private val apps = AppItemMapper()

    private fun app(
        pkg: String,
        label: String = pkg,
        profile: AppProfile = AppProfile.personal(),
        visibility: AppVisibility = AppVisibility.VISIBLE,
        enabled: Boolean = true,
        category: String? = null,
    ) = InstalledApp(
        identity = AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"), profile),
        label = label,
        visibility = visibility,
        enabled = enabled,
        category = category,
    )

    @Test
    fun `all apps keeps visible enabled apps in catalogue order with app targets`() {
        val items =
            apps.allApps(
                listOf(
                    app("b", "Beta", category = "tools"),
                    app("a", "alpha"),
                    app("h", "Hidden", visibility = AppVisibility.HIDDEN),
                    app("d", "Disabled", enabled = false),
                ),
            )

        assertEquals(listOf("alpha", "Beta"), items.map { it.title })
        assertEquals(ItemTarget.App("a", "personal"), items[0].target)
        assertEquals("tools", items[1].groupKey)
        assertNull(items[0].groupKey)
        assertTrue(items.all { it.sourceId == SourceIds.ALL_APPS && it.icon != null })
        assertEquals(items.size, items.map { it.id }.toSet().size)
    }

    @Test
    fun `same package in two profiles yields distinct items`() {
        val items = apps.allApps(listOf(app("a"), app("a", profile = AppProfile.work())))
        assertEquals(2, items.map { it.id }.toSet().size)
    }

    @Test
    fun `recents resolve packages most recent first and skip unknown or duplicate packages`() {
        val items =
            apps.recentApps(
                usages =
                    listOf(
                        RecentAppUsage(AppPackageName("a"), 10L),
                        RecentAppUsage(AppPackageName("gone"), 50L),
                        RecentAppUsage(AppPackageName("b"), 30L),
                        RecentAppUsage(AppPackageName("a"), 5L),
                    ),
                apps = listOf(app("a"), app("b"), app("a", profile = AppProfile.work())),
            )

        assertEquals(listOf("b", "a"), items.map { (it.target as ItemTarget.App).packageName })
        assertEquals(listOf(30L, 10L), items.map { it.timeEpochMillis })
        assertEquals("personal", (items[1].target as ItemTarget.App).profileId)
        assertTrue(items.all { it.sourceId == SourceIds.RECENT_APPS })
    }

    @Test
    fun `recents respect the limit and hidden apps`() {
        val usages = (1..5).map { RecentAppUsage(AppPackageName("p$it"), it * 10L) }
        val installed =
            (1..5).map {
                app(
                    "p$it",
                    visibility = if (it == 5) AppVisibility.HIDDEN else AppVisibility.VISIBLE,
                )
            }
        assertEquals(2, apps.recentApps(usages, installed, limit = 2).size)
        assertEquals(
            listOf("p4", "p3"),
            apps.recentApps(usages, installed, limit = 2).map { (it.target as ItemTarget.App).packageName },
        )
    }

    @Test
    fun `shortcuts map enabled shortcuts of visible apps to shortcut targets grouped by app`() {
        val mail = app("mail", "Mail")
        val hidden = app("secret", visibility = AppVisibility.HIDDEN)
        val byApp =
            mapOf(
                mail.identity to
                    listOf(
                        AppShortcut(AppShortcutId("compose"), mail.identity, "Compose", "Compose a message"),
                        AppShortcut(AppShortcutId("off"), mail.identity, "Off", enabled = false),
                    ),
                hidden.identity to listOf(AppShortcut(AppShortcutId("x"), hidden.identity, "X")),
            )

        val items = ShortcutItemMapper().quickActions(listOf(mail, hidden), byApp)

        val item = assertNotNull(items.singleOrNull())
        assertEquals(ItemTarget.Shortcut("mail", "compose", "personal"), item.target)
        assertEquals("Compose", item.title)
        assertEquals("Mail", item.groupLabel)
        assertEquals("Compose a message", item.body)
        assertEquals(SourceIds.QUICK_ACTIONS, item.sourceId)
    }
}
