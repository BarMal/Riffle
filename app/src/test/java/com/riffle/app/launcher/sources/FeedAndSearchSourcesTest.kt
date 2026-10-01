package com.riffle.app.launcher.sources

import com.riffle.app.launcher.rss.CacheFreshness
import com.riffle.app.launcher.rss.CachedFeed
import com.riffle.app.launcher.rss.CachedFeedArticle
import com.riffle.app.launcher.rss.CachedFeedSnapshot
import com.riffle.app.launcher.rss.FeedArticleCacheRepository
import com.riffle.app.launcher.rss.FeedCacheResult
import com.riffle.app.launcher.rss.FeedChangeSignal
import com.riffle.app.launcher.rss.NoopFeedArticleCacheRepository
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppShortcutRepository
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.apps.RecentAppRepository
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.rss.ConfiguredFeedSource
import com.riffle.core.domain.launcher.rss.FeedConfiguration
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedProfileStatus
import com.riffle.core.domain.launcher.rss.FeedUrl
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntry
import com.riffle.core.domain.launcher.search.LauncherSearchSettingsEntryId
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.ItemTarget
import com.riffle.core.domain.launcher.workspace.ParameterizedItemSource
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceParameter
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.FeedItemMapper
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedAndSearchSourcesTest {
    private val personal = AppProfile.personal()
    private val camera =
        InstalledApp(AppIdentity(AppPackageName("cam"), AppActivityName("cam.Main"), personal), label = "Camera")

    private var cacheLoads = 0
    private var snapshotReads = 0
    private var feeds: List<FeedConfiguration> = emptyList()
    private var statuses: Map<AppProfileId, FeedProfileStatus> = emptyMap()
    private var cached: Map<FeedId, CachedFeed> = emptyMap()
    private var freshness = CacheFreshness.FRESH
    private var feedChanges: SourceChangeSource = SourceChangeSource.NONE
    private val query = SearchQueryHolder()

    private val cache: FeedArticleCacheRepository =
        object : FeedArticleCacheRepository by NoopFeedArticleCacheRepository {
            override fun loadFeed(
                feedId: FeedId,
                staleAfterMillis: Long,
            ): FeedCacheResult {
                cacheLoads++
                return cached[feedId]?.let { feed -> FeedCacheResult.Available(CachedFeedSnapshot(feed, freshness)) }
                    ?: FeedCacheResult.Empty
            }
        }

    private fun source(id: SourceId): ItemSource =
        builtInSourceRegistry(
            BuiltInSourceDependencies(
                executor = { task -> task.run() },
                nowEpochMillis = { 0L },
                apps =
                    AppSourceDependencies(
                        installedApps =
                            InstalledAppSnapshotProvider {
                                snapshotReads++
                                InstalledAppSnapshot(
                                    listOf(camera),
                                    mapOf(personal.id to AppProfileContentVisibility.VISIBLE),
                                )
                            },
                        shortcuts = AppShortcutRepository { emptyMap() },
                        recentApps = RecentAppRepository { emptyList() },
                        recentAppsAccess = { SourceAccess.REQUIRED },
                    ),
                notifications =
                    NotificationSourceDependencies(
                        repository = LauncherNotificationRepository { emptyList() },
                        access = { NotificationAccessStatus.NOT_GRANTED },
                        changes = SourceChangeSource.NONE,
                        hideRules = { emptyList() },
                    ),
                feeds = FeedSourceDependencies(ConfiguredFeedSource { feeds }, cache, { statuses }, feedChanges),
                search =
                    SearchSourceDependencies(
                        query,
                        {
                            listOf(
                                LauncherSearchSettingsEntry(
                                    LauncherSearchSettingsEntryId("display"),
                                    "Display",
                                    "Theme",
                                    "Look",
                                ),
                            )
                        },
                    ),
            ),
        ).source(id)!!

    private fun ItemSource.latest(): SourceState {
        var state: SourceState = SourceState.Loading
        subscribe { state = it }
        return state
    }

    private fun feed(
        name: String,
        enabled: Boolean = true,
        profile: AppProfile = personal,
    ) = FeedConfiguration(
        id = FeedId(name),
        url = FeedUrl.parse("https://$name.example/feed.xml").getOrThrow(),
        profile = profile,
        enabled = enabled,
    )

    private fun article(
        index: Int,
        title: String = "Title $index",
    ) = CachedFeedArticle(
        digest = index.toString(16).padStart(64, '0'),
        title = title,
        summary = "<p>Body $index</p>",
        canonicalUrl = "https://news.example/$index",
        publishedAtEpochMillis = index * 1000L,
        sourceOrder = index,
    )

    @Test
    fun noFeedsConfiguredIsReadyAndEmptyAndTouchesNoCache() {
        val state = source(SourceIds.RSS).latest()

        assertEquals(SourceState.Ready(emptyList()), state)
        assertEquals(0, cacheLoads)
    }

    @Test
    fun configuredFeedWithNothingCachedIsReadyAndEmpty() {
        feeds = listOf(feed("news"))

        assertEquals(SourceState.Ready(emptyList()), source(SourceIds.RSS).latest())
        assertEquals(1, cacheLoads)
    }

    @Test
    fun cachedArticlesBecomeGroupedSanitizedItems() {
        feeds = listOf(feed("news"))
        cached = mapOf(FeedId("news") to CachedFeed(FeedId("news"), listOf(article(1), article(2)), 0L))

        val items = (source(SourceIds.RSS).latest() as SourceState.Ready).items

        assertEquals(listOf("Title 2", "Title 1"), items.map { it.title })
        assertEquals("Body 2", items.first().body)
        assertEquals("news.example", items.first().groupLabel)
        assertEquals(ItemTarget.DeepLink("https://news.example/2"), items.first().target)
    }

    @Test
    fun staleCacheIsStillServedAndFlagged() {
        feeds = listOf(feed("news"))
        cached = mapOf(FeedId("news") to CachedFeed(FeedId("news"), listOf(article(1)), 0L))
        freshness = CacheFreshness.STALE

        val item = (source(SourceIds.RSS).latest() as SourceState.Ready).items.single()

        assertEquals(ItemExtValue.Flag(true), item.ext[FeedItemMapper.STALE_KEY])
    }

    @Test
    fun disabledAndLockedFeedsAreNeverReadFromTheCache() {
        val work = AppProfile.work()
        feeds = listOf(feed("off", enabled = false), feed("locked", profile = work))
        statuses = mapOf(work.id to FeedProfileStatus.LOCKED)
        cached =
            mapOf(
                FeedId("off") to CachedFeed(FeedId("off"), listOf(article(1)), 0L),
                FeedId("locked") to CachedFeed(FeedId("locked"), listOf(article(2)), 0L),
            )

        assertEquals(SourceState.Ready(emptyList()), source(SourceIds.RSS).latest())
        assertEquals(0, cacheLoads)
    }

    @Test
    fun nothingIsReadUntilSubscribedAndSharedByManyObservers() {
        feeds = listOf(feed("news"))
        val rss = source(SourceIds.RSS)
        assertEquals(0, cacheLoads)

        rss.subscribe { }
        rss.subscribe { }

        assertEquals(1, cacheLoads)
    }

    @Test
    fun rssDescriptorIsGroupableOnly() {
        assertEquals(setOf(SourceCapability.GROUPABLE), source(SourceIds.RSS).descriptor.capabilities)
    }

    @Test
    fun rssBecomesLiveAndReloadsWhenARefreshReportsCacheChanges() {
        val signal = FeedChangeSignal()
        feedChanges = signal
        feeds = listOf(feed("news"))
        val rss = source(SourceIds.RSS)
        assertEquals(
            setOf(SourceCapability.GROUPABLE, SourceCapability.LIVE),
            rss.descriptor.capabilities,
        )
        rss.subscribe { }
        assertEquals(1, cacheLoads)

        signal.notifyChanged()

        assertEquals(2, cacheLoads)
    }

    @Test
    fun searchWithAnEmptyQueryIsEmptyAndQueriesNothing() {
        assertEquals(SourceState.Ready(emptyList()), source(SourceIds.SEARCH).latest())
        assertEquals(0, snapshotReads)
    }

    @Test
    fun searchEmitsGroupedResultsAndFollowsTheQueryOnlyWhileObserved() {
        val search = source(SourceIds.SEARCH)
        var state: SourceState = SourceState.Loading
        val subscription = search.subscribe { state = it }

        query.set("cam")
        val items = (state as SourceState.Ready).items
        assertEquals(listOf("Camera"), items.map { it.title })
        assertEquals("apps", items.single().groupKey)

        query.set("display")
        assertEquals(
            listOf("settings"),
            (state as SourceState.Ready).items.map { it.groupKey },
        )

        subscription.cancel()
        val reads = snapshotReads
        query.set("cam")
        assertEquals(reads, snapshotReads)
    }

    @Test
    fun searchDescriptorIsSearchableAndLiveOnTheQuery() {
        val capabilities = source(SourceIds.SEARCH).descriptor.capabilities
        assertTrue(SourceCapability.SEARCHABLE in capabilities)
        assertTrue(SourceCapability.LIVE in capabilities)
        assertFalse(SourceCapability.PRIVACY_SENSITIVE in capabilities)
    }

    private fun parameterized(): ParameterizedItemSource = source(SourceIds.SEARCH) as ParameterizedItemSource

    private fun queryOf(text: String) = SourceParameter.query(text)!!

    @Test
    fun aLensQueryDrivesItsOwnResultsAndLeavesTheSharedQueryAlone() {
        val search = parameterized()
        var lensState: SourceState = SourceState.Loading
        var defaultState: SourceState = SourceState.Loading
        search.subscribe(queryOf("cam")) { lensState = it }
        search.subscribe { defaultState = it }

        assertEquals(listOf("Camera"), (lensState as SourceState.Ready).items.map { it.title })
        assertEquals(emptyList<Any>(), (defaultState as SourceState.Ready).items)

        query.set("display")
        assertEquals(listOf("settings"), (defaultState as SourceState.Ready).items.map { it.groupKey })
        assertEquals(listOf("Camera"), (lensState as SourceState.Ready).items.map { it.title })
    }

    @Test
    fun aLensQueryStreamQueriesNothingOnceItsObserversLeave() {
        val search = parameterized()
        val subscription = search.subscribe(queryOf("cam")) { }
        val reads = snapshotReads
        subscription.cancel()
        query.set("x")
        assertEquals(reads, snapshotReads)
    }

    @Test
    fun thePerLensQueryIsNotPartOfTheSourcesStringForm() {
        val search = parameterized()
        search.subscribe(queryOf("zq-sentinel")) { }
        assertFalse("zq-sentinel" in search.toString())
        assertFalse("zq-sentinel" in queryOf("zq-sentinel").toString())
    }
}
