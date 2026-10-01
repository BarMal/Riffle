package com.riffle.core.domain.launcher.workspace.exclusions

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import com.riffle.core.domain.launcher.apps.withHiddenApps
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationHideRule
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.sources.AppItemMapper
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemInput
import com.riffle.core.domain.launcher.workspace.sources.NotificationItemMapper
import com.riffle.core.domain.launcher.workspace.sources.ShortcutItemMapper
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The new filter over raw items must hide exactly what the legacy code paths hide for the same inputs. */
class ExclusionEquivalenceTest {
    private val layout = HomeLayoutDeviceClass.PHONE
    private val visibility = PROFILES.associate { it.id to AppProfileContentVisibility.VISIBLE }
    private val notificationMapper = NotificationItemMapper()

    private fun input(
        notifications: List<com.riffle.core.domain.launcher.notifications.LauncherNotification>,
        rules: List<NotificationHideRule>,
    ) = NotificationItemInput(notifications, NOW, rules, visibility)

    private fun rulesFor(
        hiddenApps: Set<com.riffle.core.domain.launcher.apps.AppIdentity>,
        hideRules: List<NotificationHideRule>,
    ) = ExclusionMigration.migrate(LayoutExclusionRules(), hiddenApps, hideRules).forLayout(layout)

    private fun ids(items: List<Item>) = items.map { it.id }

    @Test
    fun `notification and media items match the legacy hide filter on seeded corpora`() {
        repeat(200) { seed ->
            val random = Random(seed)
            val notifications = randomNotifications(random, 25)
            val hide = randomHideRules(random, random.nextInt(0, 6))
            val rules = rulesFor(emptySet(), hide)

            val legacyNotifications = notificationMapper.notificationItems(input(notifications, hide))
            val legacyMedia = notificationMapper.mediaItems(input(notifications, hide))
            val raw = input(notifications, emptyList())
            val rawNotifications = notificationMapper.notificationItems(raw)
            val rawMedia = notificationMapper.mediaItems(raw)

            assertEquals(
                ids(legacyNotifications),
                ids(SourceExclusionFilter.apply(rules, rawNotifications)),
                "seed $seed",
            )
            assertEquals(ids(legacyMedia), ids(SourceExclusionFilter.apply(rules, rawMedia)), "seed $seed")
        }
    }

    @Test
    fun `app and shortcut items match the legacy hidden apps on seeded corpora`() {
        val apps =
            PACKAGES.flatMap { pkg ->
                PROFILES.flatMap { profile ->
                    listOf("Main", "Alt").map { activity -> installed(appIdentity(pkg, activity, profile)) }
                }
            }
        val shortcuts =
            apps.associate {
                    app ->
                app.identity to listOf(AppShortcut(AppShortcutId("s"), app.identity, "Short"))
            }
        val appMapper = AppItemMapper()
        val shortcutMapper = ShortcutItemMapper()
        repeat(200) { seed ->
            val random = Random(seed)
            val hidden = apps.map { it.identity }.filter { random.nextInt(3) == 0 }.toSet()
            val rules = rulesFor(hidden, emptyList())

            assertEquals(
                ids(appMapper.allApps(apps.withHiddenApps(hidden))),
                ids(SourceExclusionFilter.apply(rules, appMapper.allApps(apps))),
                "all apps, seed $seed",
            )
            assertEquals(
                ids(shortcutMapper.quickActions(apps.withHiddenApps(hidden), shortcuts)),
                ids(SourceExclusionFilter.apply(rules, shortcutMapper.quickActions(apps, shortcuts))),
                "shortcuts, seed $seed",
            )
            apps.forEach { app -> assertEquals(app.identity in hidden, rules.isAppHidden(app.identity), "seed $seed") }
        }
    }

    @Test
    fun `recent apps match when each package has a single launcher activity`() {
        val apps = PACKAGES.flatMap { pkg -> PROFILES.map { installed(appIdentity(pkg, "Main", it)) } }
        val usages =
            PACKAGES.mapIndexed {
                    index,
                    pkg,
                ->
                RecentAppUsage(com.riffle.core.domain.launcher.apps.AppPackageName(pkg), 100L - index)
            }
        val mapper = AppItemMapper()
        repeat(100) { seed ->
            val random = Random(seed)
            val hidden = apps.map { it.identity }.filter { random.nextInt(3) == 0 }.toSet()
            val rules = rulesFor(hidden, emptyList())
            val legacy = mapper.recentApps(usages, apps.withHiddenApps(hidden))
            val viaRules = SourceExclusionFilter.apply(rules, mapper.recentApps(usages, apps, limit = 100))
            // Legacy resolves a package to its personal app first and falls back to work when personal is
            // hidden; the rules see the personal item and drop it. Compare only where no fallback happened.
            val fallback =
                PACKAGES.any {
                        pkg ->
                    appIdentity(pkg, "Main", AppProfile.personal()) in hidden &&
                        apps.any {
                            it.identity.packageName.value == pkg && it.identity !in hidden
                        }
                }
            if (!fallback) assertEquals(ids(legacy), ids(viaRules), "seed $seed")
        }
    }
}
