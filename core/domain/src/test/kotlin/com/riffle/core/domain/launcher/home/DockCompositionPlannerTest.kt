package com.riffle.core.domain.launcher.home

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dock's two halves and the rule that keeps them from showing the same app twice.
 */
class DockCompositionPlannerTest {
    private val planner = DockCompositionPlanner()

    @Test
    fun anAppPinnedToTheDockKeepsItsStaticSlotAndLosesItsNotificationEntry() {
        // The duplication this exists to stop: the pinned app's own icon already badges, so a
        // second entry for it in the dynamic section is the same app twice in one strip.
        val composition =
            planner.plan(
                dock = dock(pinned = listOf("com.example.chat")),
                groups = listOf(group("com.example.chat"), group("com.example.mail")),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            )

        assertEquals(
            listOf(AppPackageName("com.example.mail")),
            composition.notificationEntries.map { entry -> entry.key.packageName },
        )
        assertEquals(1, composition.staticItems.size)
    }

    @Test
    fun anAppThatIsNotPinnedKeepsItsNotificationEntry() {
        val composition =
            planner.plan(
                dock = dock(pinned = listOf("com.example.browser")),
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            )

        assertEquals(
            listOf(AppPackageName("com.example.chat")),
            composition.notificationEntries.map { entry -> entry.key.packageName },
        )
    }

    @Test
    fun theSamePackageInAnotherProfileIsNotTheSameApp() {
        // A work-profile clone is a separate launchable app with its own notifications, so pinning
        // the personal one must not silently suppress the work one's entry.
        val composition =
            planner.plan(
                dock = dock(pinned = listOf("com.example.chat")),
                groups = listOf(group("com.example.chat", profile = AppProfile.work())),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            )

        assertEquals(1, composition.notificationEntries.size)
    }

    @Test
    fun anAppInsideAPinnedFolderStillGetsItsNotificationEntry() {
        // A folder shows its own icon, not its contents', so this is not a visible duplicate --
        // and it is the case where surfacing the notification separately helps most.
        val chat = shortcut("com.example.chat")
        val composition =
            planner.plan(
                dock =
                    DockModel(
                        capacity = 5,
                        items =
                            listOf(
                                FolderItem(id = LauncherItemId("folder"), label = "Social", items = listOf(chat)),
                            ),
                        showNotificationCards = true,
                    ),
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            )

        assertEquals(1, composition.notificationEntries.size)
    }

    @Test
    fun aDockThatDoesNotWantNotificationsHidesTheSectionRatherThanPromptingForAccess() {
        // Opting the section out is a decision, not a reason to surface a permission fallback for
        // access this dock would never use.
        val composition =
            planner.plan(
                dock = dock(pinned = emptyList(), showNotificationCards = false),
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
            )

        assertEquals(DockNotificationCardDeckState.Hidden, composition.notifications)
    }

    @Test
    fun aDisabledDockComposesToNothingAtAll() {
        val composition =
            planner.plan(
                dock = dock(pinned = listOf("com.example.chat")).copy(isEnabled = false),
                groups = listOf(group("com.example.mail")),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
            )

        assertEquals(DockComposition.EMPTY, composition)
    }

    @Test
    fun permissionFallbacksStillReachTheSectionWhenItIsWanted() {
        // Delegated wholesale to DockNotificationCardPlanner so one place decides this, whichever
        // surface is asking.
        val composition =
            planner.plan(
                dock = dock(pinned = emptyList()),
                groups = listOf(group("com.example.chat")),
                notificationAccessStatus = NotificationAccessStatus.REVOKED,
            )

        assertEquals(
            DockNotificationCardDeckState.PermissionFallback(DockNotificationPermissionFallbackReason.REVOKED),
            composition.notifications,
        )
        assertTrue(composition.notificationEntries.isEmpty())
    }

    @Test
    fun theEntryCapAppliesAfterPinnedAppsAreExcluded() {
        // Excluding first is what lets the cap spend all of its slots on apps the dock is not
        // already showing; filtering afterwards would let a pinned app consume one and shrink the
        // section for no benefit.
        val composition =
            planner.plan(
                dock = dock(pinned = listOf("com.example.chat")),
                groups =
                    listOf(
                        group("com.example.chat"),
                        group("com.example.mail"),
                        group("com.example.calendar"),
                    ),
                groupsCap = 2,
            )

        assertEquals(
            listOf(AppPackageName("com.example.mail"), AppPackageName("com.example.calendar")),
            composition.notificationEntries.map { entry -> entry.key.packageName },
        )
    }

    private fun DockCompositionPlanner.plan(
        dock: DockModel,
        groups: List<AppNotificationGroup>,
        groupsCap: Int,
    ): DockComposition =
        plan(
            dock = dock,
            groups = groups,
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            maxNotificationEntries = groupsCap,
        )

    private fun dock(
        pinned: List<String>,
        showNotificationCards: Boolean = true,
    ): DockModel =
        DockModel(
            capacity = 5,
            items = pinned.map(::shortcut),
            showNotificationCards = showNotificationCards,
        )

    private fun shortcut(packageName: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(packageName),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName("$packageName.Main"),
                ),
            label = packageName,
        )

    private fun group(
        packageName: String,
        profile: AppProfile = AppProfile.personal(),
    ): AppNotificationGroup =
        AppNotificationGroup(
            packageName = AppPackageName(packageName),
            profileId = profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("$packageName:1"),
                        packageName = AppPackageName(packageName),
                        category = NotificationCategory.MESSAGE,
                        canDismiss = true,
                        postedAtEpochMillis = 1L,
                    ),
                ),
        )
}
