package com.riffle.app.launcher.sources

import com.riffle.app.launcher.stream.MediaResumer
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.RecentAppRepository
import com.riffle.core.domain.launcher.apps.RecentAppUsage
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.LauncherNotificationMessage
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.stream.MediaAction
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationAction
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentPlayer
import com.riffle.core.domain.launcher.stream.RecentPlayersStore
import com.riffle.core.domain.launcher.stream.RecentPlayersTracker
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContentMapper
import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import com.riffle.core.domain.launcher.workspace.sources.CalendarEventRepository
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSourcesTest {
    private val personal = AppProfile.personal()
    private val player =
        InstalledApp(AppIdentity(AppPackageName("player"), AppActivityName("player.Main"), personal), label = "Player")
    private var queried = mutableListOf<Int>()

    private val calendar =
        CalendarSourceDependencies(
            repository =
                CalendarEventRepository { from, limit ->
                    queried += limit
                    (1..8).map { CalendarEvent("lens$it", "Lens $it", from + it, from + it + 5) }
                },
            access = { SourceAccess.GRANTED },
            agendaRepository =
                CalendarEventRepository { from, limit ->
                    queried += limit
                    (1..30).map { CalendarEvent("e$it", "Event $it", from + it * HOUR, from + it * HOUR + 5) }
                },
        )

    private fun registry(
        media: MediaSourceDependencies = MediaSourceDependencies.NONE,
        forStream: Boolean = true,
        notifications: List<LauncherNotification> = emptyList(),
        packageLabels: PackageLabelResolver = PackageLabelResolver.NONE,
    ): SourceRegistry {
        val deps =
            BuiltInSourceDependencies(
                executor = { it.run() },
                nowEpochMillis = { NOW },
                apps =
                    AppSourceDependencies(
                        installedApps =
                            InstalledAppSnapshotProvider {
                                InstalledAppSnapshot(
                                    listOf(player),
                                    mapOf(personal.id to AppProfileContentVisibility.VISIBLE),
                                )
                            },
                        shortcuts = AppShortcutRepository { emptyMap() },
                        recentApps = RecentAppRepository { listOf(RecentAppUsage(AppPackageName("player"), 5L)) },
                        recentAppsAccess = { SourceAccess.GRANTED },
                        packageLabels = packageLabels,
                    ),
                notifications =
                    NotificationSourceDependencies(
                        repository = LauncherNotificationRepository { notifications },
                        access = { NotificationAccessStatus.GRANTED },
                        changes = SourceChangeSource.NONE,
                        hideRules = { emptyList() },
                        canReply = { true },
                        canSnooze = { true },
                    ),
                calendar = calendar,
                media = media,
            )
        return if (forStream) streamSourceRegistry(deps) else builtInSourceRegistry(deps)
    }

    private fun SourceRegistry.latest(id: SourceId): SourceState {
        var state: SourceState = SourceState.Loading
        source(id)!!.subscribe { state = it }
        return state
    }

    @Test
    fun theAgendaHasItsOwnWindow_andTheLensCalendarSourceIsUnchanged() {
        val agenda = registry().latest(StreamSourceIds.AGENDA) as SourceState.Ready
        assertEquals(30, agenda.items.size)
        assertTrue(
            "agenda items are calendar items, so Hide rules and privacy apply",
            agenda.items.all {
                it.sourceId == SourceIds.CALENDAR
            },
        )

        queried.clear()
        val lens = registry().latest(SourceIds.CALENDAR) as SourceState.Ready
        assertEquals(1, lens.items.size)
        assertEquals(listOf(5), queried)
    }

    @Test
    fun theUserFacingSourceListDoesNotGainTheAgendaSource() {
        assertEquals(SourceIds.BUILT_IN.toSet(), registry(forStream = false).descriptors().map { it.id }.toSet())
        assertNull(registry(forStream = false).source(StreamSourceIds.AGENDA))
    }

    @Test
    fun theMediaSourceIsEnrichedFromTheSessionBehindItsNotification() {
        val mediaNotification =
            LauncherNotification(
                key = LauncherNotificationKey("m"),
                packageName = AppPackageName("player"),
                isMediaSession = true,
                title = "Song",
                text = "Band",
                canDismiss = false,
                postedAtEpochMillis = NOW - 1,
            )
        val sessions =
            MediaSourceDependencies(
                sessions =
                    {
                        listOf(
                            MediaSessionInfo(
                                "player",
                                isPlaying = true,
                                controls = setOf(MediaControl.PAUSE),
                                positionMillis = 7,
                            ),
                            MediaSessionInfo("ghost", isPlaying = true, controls = setOf(MediaControl.PLAY)),
                        )
                    },
            )
        val state =
            registry(
                sessions,
                notifications = listOf(mediaNotification),
            ).latest(SourceIds.MEDIA) as SourceState.Ready
        val media = StreamContentMapper.map(state.items.single()) as MediaContent
        assertEquals(true, media.isPlaying)
        assertEquals(setOf(MediaAction.OPEN, MediaAction.PAUSE), media.actions)

        val none = registry(notifications = emptyList()).latest(SourceIds.MEDIA) as SourceState.Ready
        assertTrue("a session without a visible media notification shows nothing", none.items.isEmpty())
    }

    @Test
    fun aNotificationFromAnAppWithNoLauncherEntryIsNamedByThePlatformThenItsTitleNotItsPackage() {
        val recorder =
            LauncherNotification(
                key = LauncherNotificationKey("r"),
                packageName = AppPackageName("recorder"),
                title = "Recording screen",
                text = "Tap to stop",
                canDismiss = true,
                postedAtEpochMillis = NOW - 1,
            )

        fun label(labels: PackageLabelResolver): String? {
            val registry = registry(notifications = listOf(recorder), packageLabels = labels)
            return (registry.latest(SourceIds.NOTIFICATIONS) as SourceState.Ready).items.single().groupLabel
        }

        assertEquals("Screen recorder", label { pkg -> "Screen recorder".takeIf { pkg == "recorder" } })
        assertEquals("Recording screen", label(PackageLabelResolver.NONE))
    }

    private class FakeRecentStore(var players: List<RecentPlayer> = emptyList()) : RecentPlayersStore {
        override fun read(): List<RecentPlayer> = players

        override fun write(players: List<RecentPlayer>) {
            this.players = players
        }
    }

    private fun mediaNotification(pkg: String) =
        LauncherNotification(
            key = LauncherNotificationKey("m-$pkg"),
            packageName = AppPackageName(pkg),
            isMediaSession = true,
            title = "Song",
            text = "Band",
            canDismiss = false,
            postedAtEpochMillis = NOW - 1,
        )

    @Test
    fun aPlayerThatWasPlayingBecomesARecentPlayerWhenItsSessionEndsAndIsResumableWhenItsAppSaysSo() {
        val store = FakeRecentStore()
        var live: List<MediaSessionInfo> =
            listOf(MediaSessionInfo("player", isPlaying = true, controls = setOf(MediaControl.PAUSE)))
        val resumer =
            object : MediaResumer {
                override fun canResume(packageName: String) = packageName == "player"

                override fun resume(packageName: String) = StreamActionResult.Performed
            }
        val media =
            MediaSourceDependencies(
                sessions = { live },
                recentPlayers = RecentPlayersTracker(store) { NOW },
                resumer = resumer,
            )
        // While it plays: its own player card, no source card, and it is remembered.
        val playing = registry(media, notifications = listOf(mediaNotification("player"))).latest(SourceIds.MEDIA)
        assertEquals(1, (playing as SourceState.Ready).items.size)
        assertEquals(listOf("player"), store.players.map { it.packageName })

        // Its session ended: a quiet source card with the last text this process saw, and Play.
        live = emptyList()
        val ended = registry(media).latest(SourceIds.MEDIA) as SourceState.Ready
        val card = StreamContentMapper.map(ended.items.single()) as MediaContent
        assertTrue(card.source)
        assertEquals("Player", card.appLabel)
        assertEquals("Song", card.title)
        assertEquals("Band", card.artist)
        assertEquals(setOf(MediaAction.OPEN, MediaAction.PLAY, MediaAction.DISMISS), card.actions)
    }

    @Test
    fun anAppThatIsNoLongerInstalledLeavesTheRecentPlayersAndWithoutATrackerNothingIsRemembered() {
        val store =
            FakeRecentStore(listOf(RecentPlayer("gone", "personal", 5L), RecentPlayer("player", "personal", 4L)))
        val media =
            MediaSourceDependencies(sessions = { emptyList() }, recentPlayers = RecentPlayersTracker(store) { NOW })
        val state = registry(media).latest(SourceIds.MEDIA) as SourceState.Ready
        assertEquals(listOf("player"), state.items.map { (it.target as ItemTarget.App).packageName })
        assertEquals(listOf("player"), store.players.map { it.packageName })

        val none = registry(MediaSourceDependencies(sessions = { emptyList() })).latest(SourceIds.MEDIA)
        assertTrue((none as SourceState.Ready).items.isEmpty())
    }

    @Test
    fun aMessagingNotificationCarriesItsThreadAndReplyFromTheLiveSnapshot() {
        val chat =
            LauncherNotification(
                key = LauncherNotificationKey("c"),
                packageName = AppPackageName("player"),
                canDismiss = true,
                title = "Group",
                text = "two",
                messages =
                    listOf(
                        LauncherNotificationMessage("A", "one", 1),
                        LauncherNotificationMessage("B", "two", 2),
                    ),
                postedAtEpochMillis = NOW - 1,
            )
        val state = registry(notifications = listOf(chat)).latest(SourceIds.NOTIFICATIONS) as SourceState.Ready
        val content = StreamContentMapper.map(state.items.single()) as NotificationContent
        assertEquals(listOf("one", "two"), content.thread.map { it.text })
        assertTrue(NotificationAction.REPLY in content.actions && NotificationAction.SNOOZE in content.actions)
        assertEquals("c", content.notificationKey)
    }

    private companion object {
        const val NOW = 864_000_000L
        const val HOUR = 3_600_000L
    }
}
