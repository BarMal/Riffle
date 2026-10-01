package com.riffle.app.launcher.sources

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
import com.riffle.core.domain.launcher.notifications.LauncherNotificationRepository
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.sources.CalendarEvent
import com.riffle.core.domain.launcher.workspace.sources.CalendarEventRepository
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInItemSourcesTest {
    private val personal = AppProfile.personal()
    private val mail =
        InstalledApp(AppIdentity(AppPackageName("mail"), AppActivityName("mail.Main"), personal), label = "Mail")

    private var notificationReads = 0
    private var usageReads = 0
    private var calendarReads = 0
    private var notificationAccess = NotificationAccessStatus.GRANTED
    private var usageAccess = SourceAccess.GRANTED
    private val notificationChanges = FakeChanges()

    private class FakeChanges : SourceChangeSource {
        var listener: (() -> Unit)? = null
        var observers = 0

        override fun observe(onChanged: () -> Unit): () -> Unit {
            observers++
            listener = onChanged
            return { observers-- }
        }
    }

    private fun registry(
        calendar: CalendarSourceDependencies = CalendarSourceDependencies.UNAVAILABLE,
        appChanges: SourceChangeSource = SourceChangeSource.NONE,
    ): SourceRegistry =
        builtInSourceRegistry(
            BuiltInSourceDependencies(
                executor = { task -> task.run() },
                nowEpochMillis = { NOW },
                apps =
                    AppSourceDependencies(
                        installedApps =
                            InstalledAppSnapshotProvider {
                                InstalledAppSnapshot(
                                    listOf(mail),
                                    mapOf(personal.id to AppProfileContentVisibility.VISIBLE),
                                )
                            },
                        shortcuts = AppShortcutRepository { emptyMap() },
                        recentApps =
                            RecentAppRepository {
                                usageReads++
                                listOf(RecentAppUsage(AppPackageName("mail"), 5L))
                            },
                        recentAppsAccess = { usageAccess },
                        changes = appChanges,
                    ),
                notifications =
                    NotificationSourceDependencies(
                        repository =
                            LauncherNotificationRepository {
                                notificationReads++
                                listOf(
                                    LauncherNotification(
                                        key = LauncherNotificationKey("k"),
                                        packageName = AppPackageName("mail"),
                                        title = "Hello",
                                        postedAtEpochMillis = NOW - 1,
                                    ),
                                )
                            },
                        access = { notificationAccess },
                        changes = notificationChanges,
                        hideRules = { emptyList() },
                    ),
                calendar = calendar,
            ),
        )

    private fun SourceRegistry.latest(id: SourceId): SourceState {
        var state: SourceState = SourceState.Loading
        source(id)!!.subscribe { state = it }
        return state
    }

    @Test
    fun registryExposesEveryBuiltInSource() {
        val registry = registry()
        val ids = registry.descriptors().map { it.id }.toSet()
        assertEquals(SourceIds.BUILT_IN.toSet(), ids)
        SourceIds.BUILT_IN.forEach { id -> assertNotNull(registry.source(id)) }
    }

    @Test
    fun allAppsEmitItemsWithAppTargets() {
        val state = registry().latest(SourceIds.ALL_APPS) as SourceState.Ready
        assertEquals(listOf("Mail"), state.items.map { it.title })
    }

    @Test
    fun notificationsAreGatedAndNeverReadWithoutAccess() {
        notificationAccess = NotificationAccessStatus.NOT_GRANTED
        val registry = registry()

        assertEquals(SourceState.PermissionRequired, registry.latest(SourceIds.NOTIFICATIONS))
        assertEquals(SourceState.PermissionRequired, registry.latest(SourceIds.MEDIA))
        assertEquals(0, notificationReads)
    }

    @Test
    fun notificationsEmitGroupedItemsOnceAccessIsGranted() {
        val state = registry().latest(SourceIds.NOTIFICATIONS) as SourceState.Ready
        val item = state.items.single()
        assertEquals("Mail", item.groupLabel)
        assertEquals("Hello", item.title)
    }

    @Test
    fun recentsNeedUsageAccessAndDoNotReadWithout() {
        usageAccess = SourceAccess.REQUIRED
        assertEquals(SourceState.PermissionRequired, registry().latest(SourceIds.RECENT_APPS))
        assertEquals(0, usageReads)

        usageAccess = SourceAccess.GRANTED
        val state = registry().latest(SourceIds.RECENT_APPS) as SourceState.Ready
        assertEquals(1, state.items.size)
    }

    @Test
    fun calendarIsUnavailableWithoutAPlatformAdapterAndNeverReads() {
        assertEquals(SourceState.Unavailable, registry().latest(SourceIds.CALENDAR))
        assertEquals(0, calendarReads)
    }

    @Test
    fun calendarEmitsNextEventWhenAccessIsGranted() {
        val calendar =
            CalendarSourceDependencies(
                repository =
                    CalendarEventRepository { from, _ ->
                        calendarReads++
                        listOf(CalendarEvent("e", "Standup", from + 10, from + 20))
                    },
                access = { SourceAccess.GRANTED },
            )
        val state = registry(calendar).latest(SourceIds.CALENDAR) as SourceState.Ready
        assertEquals(listOf("Standup"), state.items.map { it.title })
        assertEquals(1, calendarReads)
    }

    private fun gatedCalendar(
        changes: SourceChangeSource,
        access: () -> SourceAccess,
    ) = CalendarSourceDependencies(
        repository =
            CalendarEventRepository { from, _ ->
                calendarReads++
                listOf(CalendarEvent("e", "Standup", from + 10, from + 20))
            },
        access = access,
        changes = changes,
    )

    @Test
    fun calendarWithoutPermissionNeverReadsAndNeverPrompts() {
        val calendar = gatedCalendar(FakeChanges()) { SourceAccess.REQUIRED }
        assertEquals(SourceState.PermissionRequired, registry(calendar).latest(SourceIds.CALENDAR))
        assertEquals(0, calendarReads)
    }

    @Test
    fun calendarIsLiveOnceAChangeSourceExistsAndObservesOnlyWhileSubscribed() {
        val changes = FakeChanges()
        val calendar = gatedCalendar(changes) { SourceAccess.GRANTED }
        val registry = registry(calendar)
        val source = registry.source(SourceIds.CALENDAR)!!

        assertTrue(SourceCapability.LIVE in source.descriptor.capabilities)
        assertEquals(0, changes.observers)
        val subscription = source.subscribe { }
        assertEquals(1, changes.observers)
        subscription.cancel()
        assertEquals(0, changes.observers)
    }

    @Test
    fun calendarFollowsGrantAndRevocationWhenNotifiedOfTheChange() {
        val changes = FakeChanges()
        var access = SourceAccess.REQUIRED
        val calendar = gatedCalendar(changes) { access }
        var state: SourceState = SourceState.Loading
        registry(calendar).source(SourceIds.CALENDAR)!!.subscribe { state = it }
        assertEquals(SourceState.PermissionRequired, state)
        assertEquals(0, calendarReads)

        access = SourceAccess.GRANTED
        changes.listener!!.invoke()
        assertTrue(state is SourceState.Ready)

        access = SourceAccess.REQUIRED
        changes.listener!!.invoke()
        assertEquals(SourceState.PermissionRequired, state)
    }

    @Test
    fun capabilitiesAreHonest() {
        val byId = registry().descriptors().associateBy { it.id }

        assertEquals(
            setOf(
                SourceCapability.LIVE,
                SourceCapability.GROUPABLE,
                SourceCapability.ACTIONABLE,
                SourceCapability.PRIVACY_SENSITIVE,
            ),
            byId.getValue(SourceIds.NOTIFICATIONS).capabilities,
        )
        assertTrue(SourceCapability.PRIVACY_SENSITIVE in byId.getValue(SourceIds.MEDIA).capabilities)
        assertTrue(SourceCapability.PRIVACY_SENSITIVE in byId.getValue(SourceIds.CALENDAR).capabilities)
        assertFalse(SourceCapability.LIVE in byId.getValue(SourceIds.CALENDAR).capabilities)
        assertFalse(SourceCapability.LIVE in byId.getValue(SourceIds.RECENT_APPS).capabilities)
        assertFalse(SourceCapability.LIVE in byId.getValue(SourceIds.ALL_APPS).capabilities)
        assertTrue(SourceCapability.SEARCHABLE in byId.getValue(SourceIds.ALL_APPS).capabilities)
        assertNotNull(byId[SourceIds.QUICK_ACTIONS])
    }

    @Test
    fun appSourcesAreLiveOnlyWhenAChangeSourceIsWired() {
        val byId = registry(appChanges = FakeChanges()).descriptors().associateBy { it.id }
        assertTrue(SourceCapability.LIVE in byId.getValue(SourceIds.ALL_APPS).capabilities)
        assertTrue(SourceCapability.LIVE in byId.getValue(SourceIds.QUICK_ACTIONS).capabilities)
    }

    @Test
    fun manyObserversShareOneUpstreamAndItIsReleasedWithTheLast() {
        val source = registry().source(SourceIds.NOTIFICATIONS)!!
        val first = source.subscribe { }
        val second = source.subscribe { }

        assertEquals(1, notificationChanges.observers)
        assertEquals(1, notificationReads)

        first.cancel()
        assertEquals(1, notificationChanges.observers)
        second.cancel()
        assertEquals(0, notificationChanges.observers)
    }

    @Test
    fun aChangeReloadsAndReachesEveryObserver() {
        val source = registry().source(SourceIds.NOTIFICATIONS)!!
        val a = mutableListOf<SourceState>()
        val b = mutableListOf<SourceState>()
        source.subscribe { a += it }
        source.subscribe { b += it }

        notificationChanges.listener!!.invoke()

        assertEquals(2, notificationReads)
        assertEquals(a.last(), b.last())
        assertTrue(a.last() is SourceState.Ready)
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
