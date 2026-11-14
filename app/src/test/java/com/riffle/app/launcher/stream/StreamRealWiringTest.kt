package com.riffle.app.launcher.stream

import com.riffle.app.launcher.sources.AppSourceDependencies
import com.riffle.app.launcher.sources.BuiltInSourceDependencies
import com.riffle.app.launcher.sources.CalendarSourceDependencies
import com.riffle.app.launcher.sources.InstalledAppSnapshot
import com.riffle.app.launcher.sources.MediaSourceDependencies
import com.riffle.app.launcher.sources.NotificationSourceDependencies
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.app.launcher.sources.streamSourceRegistry
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLoadingRule
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.workspace.settings.InMemoryDisabledSourcesStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceEnablement
import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The Stream's content wiring as the activity assembles it (`streamSourceRegistry`, `enabledStreamRegistry`,
 * `StreamContentProvider` with the production Loading deadline), with only the platform services faked and the item
 * sources running on a real reader thread. Each of the six sections must reach a terminal state, whatever the phone
 * has granted, never stay on Loading.
 */
class StreamRealWiringTest {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "wiring-test").apply { isDaemon = true } }

    @After
    fun stop() {
        executor.shutdownNow()
    }

    private fun provider(
        calendar: SourceAccess = SourceAccess.GRANTED,
        notifications: NotificationAccessStatus = NotificationAccessStatus.GRANTED,
        usage: SourceAccess = SourceAccess.GRANTED,
        activeNotifications: () -> List<com.riffle.core.domain.launcher.notifications.LauncherNotification> =
            { emptyList() },
        sessionsFail: Boolean = false,
        timeoutMillis: Long = StreamLoadingRule.TIMEOUT_MILLIS,
    ): StreamContentProvider {
        val deps =
            BuiltInSourceDependencies(
                executor = executor,
                nowEpochMillis = { NOW },
                apps =
                    AppSourceDependencies(
                        installedApps = { InstalledAppSnapshot(emptyList(), emptyMap()) },
                        shortcuts = { emptyMap() },
                        recentApps = { emptyList() },
                        recentAppsAccess = { usage },
                    ),
                notifications =
                    NotificationSourceDependencies(
                        repository = { activeNotifications() },
                        access = { notifications },
                        changes = SourceChangeSource.NONE,
                        hideRules = { emptyList() },
                    ),
                calendar =
                    CalendarSourceDependencies(
                        repository = { _, _ -> emptyList() },
                        access = { calendar },
                        agendaRepository = { from, _ -> listOf(CalendarEvent("e", "Standup", from + 1, from + 2)) },
                    ),
                media =
                    MediaSourceDependencies(
                        sessions = { if (sessionsFail) error("no media session service") else emptyList() },
                    ),
            )
        val registry =
            enabledStreamRegistry(streamSourceRegistry(deps), StoredSourceEnablement(InMemoryDisabledSourcesStore()))
        return StreamContentProvider(
            registry = registry,
            alarms = refreshingAlarmProvider(executor, SourceChangeSource.NONE) { null },
            scheduler = streamLoadingScheduler(),
            loadingTimeoutMillis = timeoutMillis,
        ) { StreamContext(nowEpochMillis = NOW) }
    }

    /** The first non-Loading result of every section, or null for one that stayed on Loading for [seconds]. */
    private fun settle(
        provider: StreamContentProvider,
        seconds: Long = 3,
    ): Map<StreamSection, StreamSectionResult?> {
        val latest = ConcurrentHashMap<StreamSection, StreamSectionResult>()
        val done = CountDownLatch(StreamSection.layoutSections.size)
        val seen = ConcurrentHashMap.newKeySet<StreamSection>()
        val subscriptions =
            StreamSection.layoutSections.map { section ->
                provider.observe(section) { result ->
                    latest[section] = result
                    if (result != StreamSectionResult.Loading && seen.add(section)) done.countDown()
                }
            }
        done.await(seconds, TimeUnit.SECONDS)
        subscriptions.forEach { it.cancel() }
        return StreamSection.layoutSections.associateWith { section ->
            latest[section]?.takeUnless { it == StreamSectionResult.Loading }
        }
    }

    private fun assertAllTerminal(results: Map<StreamSection, StreamSectionResult?>) =
        results.forEach { (section, result) -> assertNotNull("$section stayed on Loading", result) }

    @Test
    fun everySectionReachesAnAnswerWithEveryAccessGranted() {
        val results = settle(provider())
        assertAllTerminal(results)
        assertTrue(results.getValue(StreamSection.TODAY) is StreamSectionResult.Ready)
        assertEquals(StreamSectionResult.Empty, results.getValue(StreamSection.NOTIFICATIONS))
    }

    @Test
    fun everySectionReachesAnAnswerWithNothingGranted() {
        val results =
            settle(provider(SourceAccess.REQUIRED, NotificationAccessStatus.NOT_GRANTED, SourceAccess.REQUIRED))
        assertAllTerminal(results)
        assertTrue(results.getValue(StreamSection.NOTIFICATIONS) is StreamSectionResult.PermissionRequired)
        assertTrue(results.getValue(StreamSection.TODAY) is StreamSectionResult.PermissionRequired)
        assertTrue(results.getValue(StreamSection.RECENTS) is StreamSectionResult.PermissionRequired)
    }

    @Test
    fun aFailingPlatformReadIsUnavailableNotLoading() {
        val results = settle(provider(activeNotifications = { error("listener not bound") }, sessionsFail = true))
        assertAllTerminal(results)
        assertEquals(StreamSectionResult.Unavailable, results.getValue(StreamSection.NOTIFICATIONS))
    }

    @Test
    fun aReaderThreadThatIsStuckStillEndsAsUnavailableAtTheDeadline() {
        executor.execute { CountDownLatch(1).await() }
        val results = settle(provider(timeoutMillis = 200))
        assertAllTerminal(results)
        assertEquals(StreamSectionResult.Unavailable, results.getValue(StreamSection.NOTIFICATIONS))
    }

    private companion object {
        const val NOW = 1_767_225_600_000L
    }
}
