package com.riffle.app.launcher.savedhome

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageType
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.saved.DockApp
import com.riffle.core.domain.launcher.home.saved.SavedHomeCapture
import com.riffle.core.domain.launcher.home.saved.SavedHomeChange
import com.riffle.core.domain.launcher.home.saved.SavedHomeContext
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayout
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutKind
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts
import com.riffle.core.domain.launcher.home.saved.SavedHomeReconciler
import com.riffle.core.domain.launcher.home.workspaceGrid
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/** What the controller reads of the shell when it acts: the layout of the device class tab Settings shows. */
internal data class SavedHomeEnvironment(
    val deviceClass: HomeLayoutDeviceClass,
    /** That device class's layout: its Home pages and its Dock. */
    val layout: HomeLayout,
    /** Launchable apps installed now. */
    val installed: Set<AppIdentity>,
    val hidden: Set<AppIdentity>,
    /** False until the installed-app list has loaded: acting on an empty list would drop every app. */
    val appsLoaded: Boolean,
    val simpleMode: Boolean,
)

/** The shell side of the controller. Everything but [environment] and [applyHome] has a default. */
internal interface SavedHomeAccess {
    fun environment(): SavedHomeEnvironment

    /** Replaces the Home pages of the environment's layout (the Dock is kept). Main thread. */
    fun applyHome(layout: HomeLayout)

    /** Opens an Undo window for widget host ids ([com.riffle.core.domain.launcher.home.HomeLayoutRepository]). */
    fun holdWidgetIdReleases() = Unit

    fun resumeWidgetIdReleases() = Unit
}

/** The platform side of re-binding a saved widget: a host id bound to [provider] and ready to show, or null. */
fun interface SavedHomeWidgetBinder {
    fun bind(provider: WidgetProviderIdentity): HostedWidgetId?
}

internal sealed interface SavedHomeActivation {
    /**
     * Home now shows the layout. [before] is the Home as it was, to put back exactly ([activate] already kept it as the
     * automatic layout); [placeholders] widgets could not be bound and show as not set up.
     */
    data class Activated(
        val name: String,
        val before: HomeLayout,
        val droppedApps: Int,
        val placeholders: Int,
    ) : SavedHomeActivation

    data object Unknown : SavedHomeActivation

    data object AppsNotReady : SavedHomeActivation

    data object SimpleMode : SavedHomeActivation

    /** The safety copy could not be written, so Home was not touched. */
    data object NotSaved : SavedHomeActivation
}

/**
 * The saved Home layouts' actions (ADR 0003, Amendment 17). The only code that replaces Home from a saved layout, and
 * it never does so without a way back:
 *
 * 1. the Home about to be replaced is written, durably, as the automatic layout ("Before ..."), and only then
 * 2. Home is replaced, and the caller offers Undo with the returned [SavedHomeActivation.Activated.before] (the exact
 *    previous layout, live widget ids included, which stay allocated until the Undo window closes).
 *
 * That order survives a crash at any point: before step 1 nothing changed; between 1 and 2 Home is unchanged and a
 * spare copy exists; after 2 the copy holds the previous Home.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One function per menu action, and the shell's seams.
internal class SavedHomeLayoutsController(
    private val store: SavedHomeLayoutsStore,
    private val access: SavedHomeAccess,
    private val providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
    private val binder: SavedHomeWidgetBinder,
    private val nowEpochMillis: () -> Long,
    private val newId: () -> String,
) {
    val layouts: StateFlow<SavedHomeLayouts> get() = store.layouts

    suspend fun load() {
        store.load()
    }

    suspend fun save(name: String): SavedHomeChange {
        val env = access.environment()
        val layout = capture(env.layout, name, SavedHomeLayoutKind.SAVED)
        return change { it.add(env.deviceClass, layout) }
    }

    suspend fun rename(
        id: String,
        name: String,
    ): SavedHomeChange {
        val deviceClass = access.environment().deviceClass
        return change { it.rename(deviceClass, id, name) }
    }

    suspend fun replaceWithCurrent(id: String): SavedHomeChange {
        val env = access.environment()
        val layout = capture(env.layout, name = id, kind = SavedHomeLayoutKind.SAVED)
        return change { it.replaceContent(env.deviceClass, id, layout) }
    }

    suspend fun delete(id: String): SavedHomeChange {
        val deviceClass = access.environment().deviceClass
        return change { it.delete(deviceClass, id) }
    }

    /** Puts a deleted layout back (Undo of [delete]). */
    suspend fun restore(layout: SavedHomeLayout): SavedHomeChange {
        val deviceClass = access.environment().deviceClass
        return change { it.restore(deviceClass, layout) }
    }

    /** Runs [apply] on the loaded layouts, writes the result when it changed something, and reports the outcome. */
    private suspend fun change(apply: (SavedHomeLayouts) -> SavedHomeChange): SavedHomeChange {
        var outcome: SavedHomeChange = SavedHomeChange.Unknown
        store.update { current ->
            apply(current).also { outcome = it }.let { (it as? SavedHomeChange.Changed)?.layouts ?: current }
        }
        return outcome
    }

    /**
     * Activates the layout [id] of the current device class. [beforeName] names the automatic layout
     * ("Before <name>") from the activated one.
     */
    @Suppress("ReturnCount")
    suspend fun activate(
        id: String,
        beforeName: (SavedHomeLayout) -> String,
    ): SavedHomeActivation {
        val env = access.environment()
        val saved = store.load().find(env.deviceClass, id) ?: return SavedHomeActivation.Unknown
        if (env.simpleMode) return SavedHomeActivation.SimpleMode
        if (!env.appsLoaded) return SavedHomeActivation.AppsNotReady
        val live = env.layout.pages.filter { it.type == LauncherPageType.Home }
        val reconciled = SavedHomeReconciler.reconcile(saved, context(env, live))
        // 1. The Home that is about to go is kept first, and durably.
        val before = capture(env.layout, beforeName(saved), SavedHomeLayoutKind.BEFORE_ACTIVATION)
        val kept = runCatching { store.update { it.withAutomatic(env.deviceClass, before) } }
        kept.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        if (kept.isFailure) return SavedHomeActivation.NotSaved
        // 2. Then Home changes. Widget ids of what leaves are held until the Undo window closes.
        val bound = bind(reconciled.binds)
        val pages = SavedHomeReconciler.withBoundHosts(reconciled.pages, bound)
        access.holdWidgetIdReleases()
        access.applyHome(SavedHomeReconciler.replacingHomePages(env.layout, pages))
        return SavedHomeActivation.Activated(
            name = saved.name,
            before = env.layout,
            droppedApps = reconciled.droppedApps,
            placeholders = reconciled.binds.size - bound.size,
        )
    }

    /** Puts [before] back (Undo of [activate]). */
    fun undo(before: HomeLayout) = access.applyHome(before)

    /** Ends the Undo window opened by [activate]; call exactly once for every [SavedHomeActivation.Activated]. */
    fun closeUndoWindow() = access.resumeWidgetIdReleases()

    private fun bind(
        binds: List<com.riffle.core.domain.launcher.home.saved.WidgetBind>,
    ): Map<Pair<LauncherItemId, Int>, HostedWidgetId> =
        buildMap {
            binds.forEach { request ->
                runCatching {
                    binder.bind(
                        request.provider,
                    )
                }.getOrNull()?.let { put(request.itemId to request.memberIndex, it) }
            }
        }

    private fun capture(
        layout: HomeLayout,
        name: String,
        kind: SavedHomeLayoutKind,
    ): SavedHomeLayout = SavedHomeCapture.capture(layout.pages, providerOf, newId(), name, kind, nowEpochMillis())

    private fun context(
        env: SavedHomeEnvironment,
        live: List<com.riffle.core.domain.launcher.home.LauncherPage>,
    ) = SavedHomeContext(
        installed = env.installed,
        hidden = env.hidden,
        dockApps = dockApps(env.layout),
        grid = env.layout.workspaceGrid,
        livePages = live,
        liveProviderOf = providerOf,
        newId = { "h" + newId().filter { it.isLetterOrDigit() }.take(ID_LENGTH) },
    )

    private fun dockApps(layout: HomeLayout): Set<DockApp> =
        (layout.dock.items + layout.dock.panel?.items.orEmpty()).flatMap(::dockApps).toSet()

    private fun dockApps(item: LauncherItem): List<DockApp> =
        when (item) {
            is AppShortcutItem -> listOf(DockApp(item.appIdentity, item.appShortcutId))
            is FolderItem -> item.items.map { DockApp(it.appIdentity, it.appShortcutId) }
            is WidgetItem -> emptyList()
        }

    private companion object {
        const val ID_LENGTH = 12
    }
}
