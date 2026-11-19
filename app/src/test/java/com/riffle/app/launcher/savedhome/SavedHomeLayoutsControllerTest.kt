package com.riffle.app.launcher.savedhome

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridPlacement
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.saved.SavedHomeChange
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedHomeLayoutsControllerTest {
    private val events = mutableListOf<String>()
    private var counter = 0

    private fun id(name: String) = AppIdentity(AppPackageName("com.example.$name"), AppActivityName("Main"))

    private fun app(
        name: String,
        column: Int,
    ) = AppShortcutItem(LauncherItemId("i-$name"), id(name), name, null, GridPlacement(GridCell(column, 0)))

    private fun layoutWith(vararg items: com.riffle.core.domain.launcher.home.LauncherItem): HomeLayout =
        HomeLayoutDefaults.standard().let { base ->
            base.copy(
                pages =
                    listOf(
                        LauncherPage(LauncherPageId("home"), grid = base.pages.first().grid, items = items.toList()),
                    ),
            )
        }

    private inner class Access(var layout: HomeLayout, var loaded: Boolean = true) : SavedHomeAccess {
        override fun environment() =
            SavedHomeEnvironment(
                HomeLayoutDeviceClass.PHONE,
                layout,
                installed = setOf(id("mail"), id("maps")),
                hidden = emptySet(),
                appsLoaded = loaded,
                simpleMode = false,
            )

        override fun applyHome(layout: HomeLayout) {
            events += "apply"
            this.layout = layout
        }

        override fun holdWidgetIdReleases() {
            events += "hold"
        }

        override fun resumeWidgetIdReleases() {
            events += "resume"
        }
    }

    private fun controller(
        access: Access,
        store: SavedHomeLayoutsStore = InMemorySavedHomeLayoutsStore(),
        binder: SavedHomeWidgetBinder = SavedHomeWidgetBinder { null },
    ) = SavedHomeLayoutsController(store, access, { null }, binder, { 7L }, { "id${counter++}" })

    private class RecordingStore(
        private val events: MutableList<String>,
        private val inner: SavedHomeLayoutsStore = InMemorySavedHomeLayoutsStore(),
        private val failWrites: Boolean = false,
    ) : SavedHomeLayoutsStore by inner {
        override suspend fun update(
            transform: (
                com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts,
            ) -> com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts,
        ): com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts {
            events += "write"
            if (failWrites) error("disk full")
            return inner.update(transform)
        }
    }

    @Test
    fun activatingKeepsThePreviousHomeFirstThenReplacesItAndUndoPutsItBackExactly() =
        runBlocking {
            val original = layoutWith(app("mail", 0), app("maps", 1))
            val access = Access(original)
            val store = RecordingStore(events)
            val c = controller(access, store)
            val saved = (c.save("Plain") as SavedHomeChange.Changed).layout
            access.layout = layoutWith(app("maps", 2))
            val before = access.layout
            events.clear()
            val result = c.activate(saved.id) { "Before ${it.name}" } as SavedHomeActivation.Activated
            // Safety copy first (durable write), then the replacement, with the widget ids held.
            assertEquals(listOf("write", "hold", "apply"), events)
            assertEquals(before, result.before)
            val auto =
                c.layouts.value.forDeviceClass(HomeLayoutDeviceClass.PHONE).single {
                    it.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION
                }
            assertEquals("Before Plain", auto.name)
            assertEquals(1, auto.itemCount)
            assertEquals(
                listOf("mail", "maps"),
                access.layout.pages.flatMap { it.items }.map { (it as AppShortcutItem).label },
            )
            c.undo(result.before)
            assertEquals(before, access.layout)
            c.closeUndoWindow()
            assertEquals("resume", events.last())
        }

    @Test
    fun theAutomaticCopyAloneRestoresThePreviousHomeAfterACrashAtAnyPoint() =
        runBlocking {
            val original = layoutWith(app("mail", 0), app("maps", 1))
            val store = InMemorySavedHomeLayoutsStore()
            val access = Access(original)
            val c = controller(access, store)
            val saved = (c.save("Other") as SavedHomeChange.Changed).layout
            access.layout = layoutWith(app("mail", 3))
            val previous = access.layout
            c.activate(saved.id) { "Before ${it.name}" }
            // The process dies here: only the store survives. Home from the store's automatic copy:
            val survivor = Access(layoutWith(app("mail", 3)))
            val c2 = controller(survivor, InMemorySavedHomeLayoutsStore(store.layouts.value))
            val auto =
                c2.layouts.value.forDeviceClass(HomeLayoutDeviceClass.PHONE).single {
                    it.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION
                }
            c2.activate(auto.id) { "x" }
            val restored = survivor.layout.pages.flatMap { it.items }.map { (it as AppShortcutItem) }
            assertEquals(previous.pages.flatMap { it.items }.map { it.placement }, restored.map { it.placement })
            assertEquals(listOf("mail"), restored.map { it.label })
        }

    @Test
    fun ifTheSafetyCopyCannotBeWrittenHomeIsNotTouched() =
        runBlocking {
            val original = layoutWith(app("mail", 0))
            val access = Access(original)
            val good = controller(access)
            val saved = (good.save("A") as SavedHomeChange.Changed).layout
            val c =
                controller(
                    access,
                    RecordingStore(events, InMemorySavedHomeLayoutsStore(good.layouts.value), failWrites = true),
                )
            events.clear()
            assertEquals(SavedHomeActivation.NotSaved, c.activate(saved.id) { "B" })
            assertFalse("apply" in events)
            assertEquals(original, access.layout)
        }

    @Test
    fun nothingHappensWhileTheInstalledAppsAreStillLoading() =
        runBlocking {
            val access = Access(layoutWith(app("mail", 0)), loaded = false)
            val c = controller(access)
            val saved = (c.save("A") as SavedHomeChange.Changed).layout
            events.clear()
            assertEquals(SavedHomeActivation.AppsNotReady, c.activate(saved.id) { "B" })
            assertTrue(events.isEmpty())
        }

    @Test
    fun aWidgetThatCanBeBoundIsBoundAndOneThatCannotIsAPlaceholder() =
        runBlocking {
            val widget =
                WidgetItem(LauncherItemId("w"), HostedWidgetId(5), "Clock", placement = GridPlacement(GridCell(0, 1)))
            val provider =
                com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity(
                    AppPackageName("com.example.clock"),
                    com.riffle.core.domain.launcher.widgets.WidgetProviderClassName("com.example.clock.W"),
                )
            val access = Access(layoutWith(widget))
            val store = InMemorySavedHomeLayoutsStore()

            fun make(binder: SavedHomeWidgetBinder) =
                SavedHomeLayoutsController(store, access, { provider }, binder, { 1L }, { "x${counter++}" })
            val saved = (make { null }.save("W") as SavedHomeChange.Changed).layout
            access.layout = layoutWith()
            val placeholder = make { null }.activate(saved.id) { "B" } as SavedHomeActivation.Activated
            assertEquals(1, placeholder.placeholders)
            access.layout = layoutWith()
            val bound = make { HostedWidgetId(42) }.activate(saved.id) { "B" } as SavedHomeActivation.Activated
            assertEquals(0, bound.placeholders)
            assertEquals(HostedWidgetId(42), (access.layout.pages.single().items.single() as WidgetItem).appWidgetId)
        }
}
