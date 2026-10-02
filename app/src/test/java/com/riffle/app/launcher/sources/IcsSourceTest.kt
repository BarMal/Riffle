package com.riffle.app.launcher.sources

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.ItemPrivacy
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEvent
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeed
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedId
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

class IcsSourceTest {
    private var feeds: List<IcsFeed>? = emptyList()
    private val cached = HashMap<IcsFeedId, List<IcsEvent>>()
    private var listener: (() -> Unit)? = null
    private val changes =
        object : SourceChangeSource {
            override fun observe(onChanged: () -> Unit): () -> Unit {
                listener = onChanged
                return { listener = null }
            }
        }

    private fun deps(ics: IcsSourceDependencies = IcsSourceDependencies.NONE) =
        BuiltInSourceDependencies(
            executor = { task -> task.run() },
            nowEpochMillis = { NOW },
            apps =
                AppSourceDependencies(
                    installedApps = InstalledAppSnapshotProvider { null },
                    shortcuts = { emptyMap() },
                    recentApps = { emptyList() },
                    recentAppsAccess = { SourceAccess.GRANTED },
                ),
            notifications =
                NotificationSourceDependencies(
                    repository = { emptyList() },
                    access = { NotificationAccessStatus.GRANTED },
                    changes = SourceChangeSource.NONE,
                    hideRules = { emptyList() },
                ),
            ics = ics,
        )

    private fun source() =
        icsSource(
            deps(
                IcsSourceDependencies(
                    feeds = { feeds },
                    cachedEvents = { id -> cached[id] },
                    zone = { ZoneId.of("UTC") },
                    changes = changes,
                ),
            ),
        )

    private fun latest(): SourceState {
        var state: SourceState = SourceState.Loading
        source().subscribe { state = it }
        return state
    }

    private fun feed(
        id: String,
        enabled: Boolean = true,
    ): IcsFeed {
        val settings = IcsFeedSettings().withAdded(id, "https://$id.example.com/a.ics") { id }!!
        return settings.feeds.single().copy(enabled = enabled)
    }

    private fun event(
        uid: String,
        start: String,
        isPrivate: Boolean = false,
    ) = IcsEvent(
        uid = uid,
        title = "T-$uid",
        location = null,
        start = LocalDateTime.parse(start),
        zone = ZoneId.of("UTC"),
        allDay = false,
        duration = Duration.ofHours(1),
        rrule = null,
        isPrivate = isPrivate,
    )

    @Test
    fun noFeedsIsAnEmptyReadyList() {
        assertEquals(SourceState.Ready(emptyList()), latest())
    }

    @Test
    fun beforeTheStoresLoadItIsLoading() {
        feeds = null
        assertEquals(SourceState.Loading, latest())
    }

    @Test
    fun itIsCalendarLikeAndLiveOnlyWithAChangeSource() {
        val live = source().descriptor
        assertEquals(SourceIds.ICS, live.id)
        assertTrue(SourceCapability.PRIVACY_SENSITIVE in live.capabilities)
        assertTrue(SourceCapability.GROUPABLE in live.capabilities)
        assertTrue(SourceCapability.LIVE in live.capabilities)
        assertFalse(SourceCapability.LIVE in icsSource(deps()).descriptor.capabilities)
    }

    @Test
    fun itShowsUpcomingEventsOfEnabledFeedsWithPrivacy() {
        feeds = listOf(feed("work"), feed("home", enabled = false))
        cached[IcsFeedId("work")] =
            listOf(event("a", "2026-03-11T09:00", isPrivate = true), event("past", "2026-03-01T09:00"))
        cached[IcsFeedId("home")] = listOf(event("h", "2026-03-11T10:00"))

        val state = latest() as SourceState.Ready

        assertEquals(listOf("T-a"), state.items.map { it.title })
        assertEquals(ItemPrivacy.SENSITIVE, state.items.single().privacy)
        assertEquals("work", state.items.single().groupKey)
    }

    @Test
    fun aFeedWithoutCachedEventsShowsNothing() {
        feeds = listOf(feed("work"))
        assertEquals(SourceState.Ready(emptyList()), latest())
    }

    @Test
    fun aRefreshOrEditReloadsTheSource() {
        var state: SourceState = SourceState.Loading
        feeds = listOf(feed("work"))
        source().subscribe { state = it }
        assertEquals(SourceState.Ready(emptyList()), state)

        cached[IcsFeedId("work")] = listOf(event("a", "2026-03-11T09:00"))
        listener!!.invoke()

        assertEquals(listOf("T-a"), (state as SourceState.Ready).items.map { it.title })
    }

    @Test
    fun theRegistryExposesTheSourceByItsId() {
        val registry = builtInSourceRegistry(deps())
        assertEquals(SourceIds.ICS, registry.source(SourceIds.ICS)!!.descriptor.id)
    }

    private companion object {
        /** 2026-03-10T12:00:00Z. */
        const val NOW = 1_773_144_000_000L
    }
}
