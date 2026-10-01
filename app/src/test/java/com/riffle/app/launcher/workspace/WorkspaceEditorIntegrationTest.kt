package com.riffle.app.launcher.workspace

import com.riffle.app.launcher.CachedWorkspaceRepository
import com.riffle.app.launcher.WorkspaceStorePort
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.StaticLensResultProvider
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.testing.FakeItemSource
import com.riffle.core.domain.launcher.workspace.testing.FakeSourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceEditorIntegrationTest {
    private class FakeStore : WorkspaceStorePort {
        val writes = mutableListOf<WorkspaceSet>()

        override suspend fun read(): WorkspaceSet? = null

        override suspend fun write(set: WorkspaceSet) {
            writes += set
        }
    }

    private val phone = HomeLayoutDeviceClass.PHONE
    private val store = FakeStore()
    private val repository = CachedWorkspaceRepository(store, CoroutineScope(Dispatchers.Unconfined))
    private val calendar = SourceDescriptor(SourceIds.CALENDAR, setOf(SourceCapability.LIVE))
    private val apps = SourceDescriptor(SourceIds.ALL_APPS)
    private val runtime =
        WorkspaceRuntime(
            repository = repository,
            registry = FakeSourceRegistry(listOf(FakeItemSource(calendar), FakeItemSource(apps))),
            provider = StaticLensResultProvider(LensOutput.Loading),
            imageLoader = NoExpressionImageLoader,
            itemActions = WorkspaceItemActions(NoOpPort),
            sourceAccess = { mapOf(SourceIds.CALENDAR to SourceAccess.REQUIRED) },
        )

    @Test
    fun savingReplacesOnlyTheEditedWorkspaceAndPersists() =
        runBlocking {
            repository.initialize(WorkspaceBootstrap::seed)
            val layout = repository.load()!!.workspacesFor(phone)
            val edited = layout.active.copy(name = "Edited")

            runtime.saveEdited(phone, layout.activeId, edited)

            val saved = repository.load()!!.workspacesFor(phone)
            assertEquals("Edited", saved.active.name)
            assertEquals(layout.workspaces.size, saved.workspaces.size)
            assertEquals(layout.activeId, saved.activeId)
            assertEquals(1, store.writes.size)
            assertEquals(
                repository.load()!!.layouts.filterKeys { it != phone },
                store.writes.single().layouts.filterKeys { it != phone },
            )
        }

    @Test
    fun savingBeforeAnythingIsLoadedDoesNothing() {
        val id = WorkspaceId("x")

        runtime.saveEdited(phone, id, Workspace(id, "x"))

        assertTrue(store.writes.isEmpty())
    }

    @Test
    fun sourceChoicesCarryTheCurrentAccessAndDefaultToGranted() {
        val choices = runtime.sourceChoices().associateBy { it.descriptor.id }

        assertEquals(SourceAccess.REQUIRED, choices.getValue(SourceIds.CALENDAR).access)
        assertEquals(SourceAccess.GRANTED, choices.getValue(SourceIds.ALL_APPS).access)
    }

    @Test
    fun accessRoutesMapOntoTheExistingExplicitFlowsOnly() {
        assertEquals(SourceAccessRoute.CALENDAR, sourceAccessRouteFor(SourceIds.CALENDAR))
        assertEquals(SourceAccessRoute.NOTIFICATION_ACCESS, sourceAccessRouteFor(SourceIds.NOTIFICATIONS))
        assertEquals(SourceAccessRoute.NOTIFICATION_ACCESS, sourceAccessRouteFor(SourceIds.MEDIA))
        assertEquals(SourceAccessRoute.USAGE_ACCESS, sourceAccessRouteFor(SourceIds.RECENT_APPS))
        assertEquals(SourceAccessRoute.NONE, sourceAccessRouteFor(SourceIds.ALL_APPS))
        assertEquals(SourceAccessRoute.NONE, sourceAccessRouteFor(SourceId("something.else")))
    }

    @Test
    fun requestingAccessRunsExactlyTheMatchingExplicitFlow() {
        val calls = mutableListOf<String>()
        val launchers =
            SourceAccessLaunchers(
                calendar = { calls += "calendar" },
                notificationAccess = { calls += "notifications" },
                usageAccess = { calls += "usage" },
            )

        launchers.request(SourceIds.CALENDAR)
        launchers.request(SourceIds.NOTIFICATIONS)
        launchers.request(SourceIds.RECENT_APPS)
        launchers.request(SourceIds.ALL_APPS)

        assertEquals(listOf("calendar", "notifications", "usage"), calls)
    }

    @Test
    fun accessMapReadsEachGateWithoutAnythingElse() {
        val map =
            sourceAccessMap(
                notificationAccess = NotificationAccessStatus.GRANTED,
                calendarAccess = SourceAccess.REQUIRED,
                recentAppsAccess = SourceAccess.REQUIRED,
            )

        assertEquals(SourceAccess.GRANTED, map.getValue(SourceIds.NOTIFICATIONS))
        assertEquals(SourceAccess.GRANTED, map.getValue(SourceIds.MEDIA))
        assertEquals(SourceAccess.REQUIRED, map.getValue(SourceIds.CALENDAR))
        assertEquals(SourceAccess.REQUIRED, map.getValue(SourceIds.RECENT_APPS))
        assertEquals(4, map.size)
    }

    private object NoOpPort : ItemLaunchPort {
        override fun launchActivity(identity: AppIdentity) = false

        override fun launchPackage(packageName: String) = false

        override fun launchShortcut(shortcut: AppShortcut) = false

        override fun openDeepLink(uri: String) = false

        override fun dismissNotification(key: String) = false
    }
}
