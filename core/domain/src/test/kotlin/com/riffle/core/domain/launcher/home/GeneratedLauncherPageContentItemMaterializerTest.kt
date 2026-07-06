package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.notifications.AppNotificationGroupKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GeneratedLauncherPageContentItemMaterializerTest {
    private val materializer = GeneratedLauncherPageContentItemMaterializer()

    @Test
    fun appContentItemsMaterializeInInputOrderWithStableGeneratedIds() {
        val beta = appItem(packageName = "com.riffle.beta")
        val alpha = appItem(packageName = "com.riffle.alpha")
        val plan =
            GeneratedLauncherPageContentPlan(
                pageId = LauncherPageId("generated:apps"),
                kind = GeneratedLauncherPageKind.APP,
                canCreate = true,
                requiredDataSources = emptySet(),
                items = listOf(beta, alpha),
            )

        val result = materializer.materialize(plan) { app -> app.identity.packageName.value.substringAfterLast('.') }

        assertEquals(emptyList(), result.skippedItems)
        assertEquals(
            listOf(
                "generated-page:${beta.stableId.value}",
                "generated-page:${alpha.stableId.value}",
            ),
            result.items.map { item -> item.id.value },
        )
        assertEquals(listOf(beta.identity, alpha.identity), result.items.map { item -> item.appIdentity })
        assertEquals(listOf("beta", "alpha"), result.items.map { item -> item.label })
        result.items.forEach { item ->
            assertNull(item.appShortcutId)
            assertNull(item.placement)
        }
    }

    @Test
    fun appContentItemsPreserveProfileAwareIdentities() {
        val personal = appItem(packageName = "com.riffle.mail", profile = AppProfile.personal())
        val work = appItem(packageName = "com.riffle.mail", profile = workProfile("managed"))

        val result =
            materializer.materialize(listOf(personal, work)) { app ->
                app.identity.profile.id.value
            }

        assertEquals(listOf(personal.identity, work.identity), result.items.map { item -> item.appIdentity })
        assertEquals(
            listOf(
                "generated-page:${personal.stableId.value}",
                "generated-page:${work.stableId.value}",
            ),
            result.items.map { item -> item.id.value },
        )
        assertEquals(listOf("personal", "managed"), result.items.map { item -> item.label })
        assertEquals(emptyList(), result.skippedItems)
    }

    @Test
    fun duplicateStableIdsKeepFirstAppShortcutAndSkipLaterItems() {
        val stableId = GeneratedLauncherPageContentItemId("app:duplicate")
        val first = appItem(packageName = "com.riffle.first", stableId = stableId)
        val second = appItem(packageName = "com.riffle.second", stableId = stableId)

        val result =
            materializer.materialize(listOf(first, second)) { app ->
                app.identity.packageName.value
            }

        assertEquals(listOf(first.identity), result.items.map { item -> item.appIdentity })
        assertEquals(listOf("generated-page:${stableId.value}"), result.items.map { item -> item.id.value })
        assertEquals(
            listOf(
                GeneratedLauncherPageContentItemMaterializationSkip(
                    stableId = stableId,
                    reason = GeneratedLauncherPageContentItemMaterializationSkipReason.DUPLICATE_STABLE_ID,
                ),
            ),
            result.skippedItems,
        )
    }

    @Test
    fun notificationGroupContentIsSkippedAsUnsupportedWithoutReorderingApps() {
        val notificationGroup =
            GeneratedLauncherPageContentItem.NotificationGroup(
                key =
                    AppNotificationGroupKey(
                        packageName = AppPackageName("com.riffle.mail"),
                        profileId = AppProfileId("personal"),
                    ),
            )
        val app = appItem(packageName = "com.riffle.calendar")

        val result =
            materializer.materialize(listOf(notificationGroup, app)) { item ->
                item.identity.packageName.value.substringAfterLast('.')
            }

        assertEquals(listOf(app.identity), result.items.map { item -> item.appIdentity })
        assertEquals(listOf("calendar"), result.items.map { item -> item.label })
        assertEquals(
            listOf(
                GeneratedLauncherPageContentItemMaterializationSkip(
                    stableId = notificationGroup.stableId,
                    reason =
                        GeneratedLauncherPageContentItemMaterializationSkipReason
                            .UNSUPPORTED_NOTIFICATION_GROUP,
                ),
            ),
            result.skippedItems,
        )
    }

    private fun appItem(
        packageName: String,
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
        stableId: GeneratedLauncherPageContentItemId? = null,
    ): GeneratedLauncherPageContentItem.App {
        val item =
            GeneratedLauncherPageContentItem.App(
                identity =
                    AppIdentity(
                        packageName = AppPackageName(packageName),
                        activityName = AppActivityName(activityName),
                        profile = profile,
                    ),
            )

        return if (stableId == null) {
            item
        } else {
            item.copy(stableId = stableId)
        }
    }

    private fun workProfile(id: String): AppProfile =
        AppProfile(
            id = AppProfileId(id),
            type = AppProfileType.WORK,
        )
}
