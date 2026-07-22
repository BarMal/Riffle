package com.riffle.app.launcher.notifications

import com.riffle.app.launcher.TimeScapeArtworkRevisionLookup
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageContentKind
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStagePreferences
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.CardsSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStageNotificationContentTest {
    @Test
    fun `projects media with stable id and focused supported actions`() {
        val notification = notification(key = "media", postedAt = 20, isMedia = true, canDismiss = true)
        val availability = NotificationStageActionAvailability { _, _ -> setOf(NotificationStageAction.Open) }

        val cards =
            appStageNotificationCards(
                notifications = listOf(notification),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
                actionAvailability = availability,
            )

        assertEquals("stage-notification:personal:media", cards.single().content.id.value)
        assertEquals(AppStageContentKind.MEDIA, cards.single().content.kind)
        assertEquals(
            setOf(NotificationStageAction.Open, NotificationStageAction.Dismiss),
            cards.single().supportedActions,
        )
    }

    @Test
    fun `redacts quiet content and excludes locked content`() {
        val quiet = notification(key = "quiet", postedAt = 2)
        val locked = notification(key = "locked", postedAt = 3, profile = AppProfile.work().id)

        val cards =
            appStageNotificationCards(
                notifications = listOf(quiet, locked),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility =
                    mapOf(
                        AppProfile.personal().id to AppProfileContentVisibility.REDACTED_QUIET,
                        AppProfile.work().id to AppProfileContentVisibility.REDACTED_LOCKED,
                    ),
            )

        assertEquals(1, cards.size)
        assertTrue(cards.single().isRedacted)
        assertEquals("Hidden notification", cards.single().title)
        assertTrue(cards.single().supportedActions.isEmpty())
    }

    @Test
    fun `requires granted notification access`() {
        val cards =
            appStageNotificationCards(
                notifications = listOf(notification(key = "one", postedAt = 1)),
                notificationAccessStatus = NotificationAccessStatus.NOT_GRANTED,
                profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
            )

        assertTrue(cards.isEmpty())
    }

    @Test
    fun `projects revision-keyed artwork without persisting it in stage content`() {
        val notification =
            notification(key = "artwork", postedAt = 1).copy(largeIconPngBase64 = "encoded-artwork")
        val cards =
            appStageNotificationCards(
                notifications = listOf(notification),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility = mapOf(AppProfile.personal().id to AppProfileContentVisibility.VISIBLE),
                artworkRevisions = TimeScapeArtworkRevisionLookup { "revision-1" },
            )

        val card = cards.single()
        assertEquals("encoded-artwork", card.artworkBase64)
        assertEquals("stage-notification:personal:artwork:revision-1", card.artworkSourceKey)
        assertEquals("stage-notification:personal:artwork", card.content.id.value)
    }

    @Test
    fun `redacted cards omit artwork payload and cache identity`() {
        val notification =
            notification(key = "quiet-artwork", postedAt = 1).copy(largeIconPngBase64 = "encoded-artwork")
        val cards =
            appStageNotificationCards(
                notifications = listOf(notification),
                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                profileContentVisibility =
                    mapOf(AppProfile.personal().id to AppProfileContentVisibility.REDACTED_QUIET),
                artworkRevisions = TimeScapeArtworkRevisionLookup { "revision-1" },
            )

        val card = cards.single()
        assertEquals("Hidden notification", card.title)
        assertEquals("Content hidden for this profile", card.text)
        assertEquals(null, card.artworkBase64)
        assertEquals(null, card.artworkSourceKey)
    }

    @Test
    fun `projects empty pinned stage with installed app and enabled shortcuts`() {
        val app = app(packageName = "com.example.mail")
        val stageId = AppStageId(app.identity.packageName, app.identity.profile.id)
        val state =
            LauncherShellState(
                installedApps = listOf(app),
                appShortcutsByApp =
                    mapOf(
                        app.identity to
                            listOf(
                                shortcut(app, id = "compose", enabled = true),
                                shortcut(app, id = "archive", enabled = false),
                            ),
                    ),
            ).withPinnedStage(stageId)

        val shellState = state.appStageShellState()

        assertTrue(shellState.snapshot.stages.single().content.isEmpty())
        assertEquals(app, shellState.emptyAppCards.getValue(stageId).app)
        assertEquals(
            listOf("compose"),
            shellState.emptyAppCards.getValue(stageId).shortcuts.map { shortcut -> shortcut.id.value },
        )
    }

    @Test
    fun `reconciliation keeps notification card ids and chronological order across source reorder`() {
        val app = app(packageName = "com.example.inbox")
        val earlier = notification(key = "earlier", postedAt = 10, packageName = app.identity.packageName)
        val later = notification(key = "later", postedAt = 20, packageName = app.identity.packageName)
        val reconciler = AppStageShellStateReconciler()

        val initial = reconciler.reconcile(stateWithNotifications(app, listOf(earlier, later)))
        val refreshed = reconciler.reconcile(stateWithNotifications(app, listOf(later, earlier)))

        val expectedIds =
            listOf(
                "stage-notification:personal:later",
                "stage-notification:personal:earlier",
            )
        assertEquals(expectedIds, initial.notificationCards.map { card -> card.content.id.value })
        assertEquals(expectedIds, refreshed.notificationCards.map { card -> card.content.id.value })
        assertEquals(
            initial.snapshot.stages.single().content.map { content -> content.id },
            refreshed.snapshot.stages.single().content.map { content -> content.id },
        )
    }

    private fun LauncherShellState.withPinnedStage(stageId: AppStageId): LauncherShellState =
        copy(
            launcherSettings =
                LauncherSettings(
                    cards =
                        CardsSettings(
                            stagePreferencesByLayout =
                                mapOf(
                                    homeLayoutSet.activeKey to
                                        AppStagePreferences(pinnedStageIds = listOf(stageId)),
                                ),
                        ),
                ),
        )

    private fun stateWithNotifications(
        app: InstalledApp,
        notifications: List<LauncherNotification>,
    ): LauncherShellState =
        LauncherShellState(
            notificationAccessStatus = NotificationAccessStatus.GRANTED,
            profileContentVisibility = mapOf(app.identity.profile.id to AppProfileContentVisibility.VISIBLE),
            installedApps = listOf(app),
            notificationGroupsByApp =
                listOf(
                    AppNotificationGroup(
                        packageName = app.identity.packageName,
                        profileId = app.identity.profile.id,
                        latestCategory = NotificationCategory.UNKNOWN,
                        latestAgeBucket = NotificationAgeBucket.RECENT,
                        notifications = notifications,
                    ),
                ),
        )

    private fun app(packageName: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName(packageName),
                    activityName = AppActivityName(".MainActivity"),
                    profile = AppProfile.personal(),
                ),
            label = "Example",
        )

    private fun shortcut(
        app: InstalledApp,
        id: String,
        enabled: Boolean,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId(id),
            appIdentity = app.identity,
            shortLabel = id,
            enabled = enabled,
        )

    private fun notification(
        key: String,
        postedAt: Long,
        isMedia: Boolean = false,
        canDismiss: Boolean = false,
        profile: com.riffle.core.domain.launcher.apps.AppProfileId = AppProfile.personal().id,
        packageName: AppPackageName = AppPackageName("com.example.$key"),
    ) = LauncherNotification(
        key = LauncherNotificationKey(key),
        packageName = packageName,
        profileId = profile,
        isMediaSession = isMedia,
        canDismiss = canDismiss,
        title = "Title $key",
        text = "Text $key",
        postedAtEpochMillis = postedAt,
    )
}
