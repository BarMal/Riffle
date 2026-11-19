package com.riffle.core.domain.launcher.layouts

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/**
 * A layout (ADR 0003, Amendment 7): two apps that open side by side, [first] on the start (left) pane. That is all
 * that is stored with when it was last opened; nothing about windows is stored. Named `AppLayout` so it is not taken
 * for the Stream's or the Home's own "layout".
 */
data class AppLayout(
    val id: String,
    val first: AppIdentity,
    val second: AppIdentity,
    val lastOpenedEpochMillis: Long = 0L,
) {
    init {
        require(id.isNotBlank()) { "Layout ids must not be blank." }
    }

    /** The ordered pair: A|B and B|A are different layouts. */
    val pair: Pair<AppIdentity, AppIdentity> get() = first to second

    fun uses(
        packageName: AppPackageName,
        profile: AppProfile,
    ): Boolean =
        (first.packageName == packageName && first.profile == profile) ||
            (second.packageName == packageName && second.profile == profile)
}

/**
 * The rules of the layouts list, pure over plain values. The list is the stack's order, front first: most recently
 * opened first (a new layout, and one that was just opened, goes to the front). Every function returns the list
 * unchanged when there is nothing to do, so a caller can compare and write only on a change.
 */
@Suppress("TooManyFunctions") // One small function per edit the stack offers.
object AppLayouts {
    /** The most layouts kept: a stack stays a thing to flick through; the oldest drops when another is added. */
    const val CAP = 12

    /**
     * [layouts] with [layout] added at the front. The same ordered pair is never held twice: an existing layout of the
     * pair is brought to the front instead (and takes [AppLayout.lastOpenedEpochMillis] of [layout]). An app cannot be
     * paired with itself.
     */
    fun add(
        layouts: List<AppLayout>,
        layout: AppLayout,
    ): List<AppLayout> {
        if (layout.first.sameApp(layout.second)) return layouts
        val existing = layouts.firstOrNull { it.pair == layout.pair || it.id == layout.id }
        val entry = existing?.copy(lastOpenedEpochMillis = layout.lastOpenedEpochMillis) ?: layout
        return (listOf(entry) + layouts.filter { it !== existing }).capped()
    }

    /** [layouts] without [id]. */
    fun remove(
        layouts: List<AppLayout>,
        id: String,
    ): List<AppLayout> = layouts.filter { it.id != id }

    /** Undo of [remove]: [layout] back at [index] (clamped), unless its id or pair is already held. */
    fun restore(
        layouts: List<AppLayout>,
        layout: AppLayout,
        index: Int,
    ): List<AppLayout> {
        if (layouts.any { it.id == layout.id || it.pair == layout.pair }) return layouts
        return layouts.toMutableList().apply { add(index.coerceIn(0, size), layout) }.capped()
    }

    /** The two sides of [id] exchanged. */
    fun swap(
        layouts: List<AppLayout>,
        id: String,
    ): List<AppLayout> {
        val layout = layouts.firstOrNull { it.id == id } ?: return layouts
        return replace(layouts, layout.copy(first = layout.second, second = layout.first))
    }

    /** The first app of [id] changed to [app]; the second app can not be chosen again (nothing changes then). */
    fun changeFirst(
        layouts: List<AppLayout>,
        id: String,
        app: AppIdentity,
    ): List<AppLayout> {
        val layout = layouts.firstOrNull { it.id == id } ?: return layouts
        return if (app.sameApp(layout.second)) layouts else replace(layouts, layout.copy(first = app))
    }

    /** The second app of [id] changed to [app]. */
    fun changeSecond(
        layouts: List<AppLayout>,
        id: String,
        app: AppIdentity,
    ): List<AppLayout> {
        val layout = layouts.firstOrNull { it.id == id } ?: return layouts
        return if (app.sameApp(layout.first)) layouts else replace(layouts, layout.copy(second = app))
    }

    /**
     * [edited] in place of the layout of its id. When that makes it the ordered pair another layout already is, the
     * edited one goes and the other comes to the front: the same pair is never held twice.
     */
    private fun replace(
        layouts: List<AppLayout>,
        edited: AppLayout,
    ): List<AppLayout> {
        val duplicate = layouts.firstOrNull { it.id != edited.id && it.pair == edited.pair }
        return if (duplicate == null) {
            layouts.map { if (it.id == edited.id) edited else it }
        } else {
            listOf(duplicate) + layouts.filter { it.id != edited.id && it !== duplicate }
        }
    }

    /** [id] was opened at [nowEpochMillis]: it takes that time and comes to the front. */
    fun opened(
        layouts: List<AppLayout>,
        id: String,
        nowEpochMillis: Long,
    ): List<AppLayout> {
        val layout = layouts.firstOrNull { it.id == id } ?: return layouts
        val opened = layout.copy(lastOpenedEpochMillis = nowEpochMillis.coerceAtLeast(layout.lastOpenedEpochMillis))
        return (listOf(opened) + layouts.filter { it.id != id }).ordered()
    }

    /** The layouts that use the app [packageName] of [profile] go: a layout with an app that is gone is not kept. */
    fun removeApp(
        layouts: List<AppLayout>,
        packageName: AppPackageName,
        profile: AppProfile,
    ): List<AppLayout> = layouts.filterNot { it.uses(packageName, profile) }

    /** [layouts] without those that use an app that is not in [installed]; null (not known) keeps them all. */
    fun pruneUninstalled(
        layouts: List<AppLayout>,
        installed: Set<AppIdentity>?,
    ): List<AppLayout> {
        if (installed == null) return layouts
        val keys = installed.mapTo(HashSet()) { it.packageName to it.profile }
        return layouts.filter { layout -> listOf(layout.first, layout.second).all { it.key() in keys } }
    }

    /** Most recently opened first; equal times keep their order, so the stack never shuffles. */
    fun List<AppLayout>.ordered(): List<AppLayout> = sortedByDescending { it.lastOpenedEpochMillis }

    private fun List<AppLayout>.capped(): List<AppLayout> = take(CAP)

    /** The same app (package and profile; the activity is the launcher entry it was chosen as). */
    private fun AppIdentity.sameApp(other: AppIdentity): Boolean = key() == other.key()

    private fun AppIdentity.key(): Pair<AppPackageName, AppProfile> = packageName to profile

    /**
     * The layouts a migration adds after [existing]: those of [migrated] whose id or ordered pair is not already held,
     * behind the layouts that are (they were never opened, so they sit last, in the order given), up to the cap.
     * Idempotent: running it again with the same input changes nothing.
     */
    fun addMigrated(
        existing: List<AppLayout>,
        migrated: List<AppLayout>,
    ): List<AppLayout> {
        var result = existing
        migrated.forEach { layout ->
            val held = result.any { it.id == layout.id || it.pair == layout.pair }
            if (!held && !layout.first.sameApp(layout.second) && result.size < CAP) result = result + layout
        }
        return result
    }
}

/** Where the layouts are kept. Blocking: called off the main thread. A failing read reads as an empty list. */
interface AppLayoutsStore {
    fun read(): List<AppLayout>

    fun write(layouts: List<AppLayout>)
}

/** The stored form of the layouts: ids, the two apps and the time; decoding never throws and applies the cap. */
object AppLayoutsCodec {
    private const val VERSION = 1

    fun encode(layouts: List<AppLayout>): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "layouts" to
                arr(
                    layouts.map { layout ->
                        obj(
                            "id" to str(layout.id),
                            "first" to encodeApp(layout.first),
                            "second" to encodeApp(layout.second),
                            "openedAt" to num(layout.lastOpenedEpochMillis),
                        )
                    },
                ),
        )

    fun decode(value: StoredValue?): List<AppLayout> {
        val root = value as? StoredValue.Obj ?: return emptyList()
        val decoded =
            root.array("layouts").mapNotNull { item ->
                val entry = item as? StoredValue.Obj ?: return@mapNotNull null
                val id = entry.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val first = entry.obj("first")?.let(::decodeApp) ?: return@mapNotNull null
                val second = entry.obj("second")?.let(::decodeApp) ?: return@mapNotNull null
                AppLayout(id, first, second, (entry.long("openedAt") ?: 0L).coerceAtLeast(0L))
            }
        // Stored in the stack's order: kept as it is, with a pair that is held twice or paired with itself dropped.
        return AppLayouts.addMigrated(emptyList(), decoded)
    }

    private fun encodeApp(app: AppIdentity): StoredValue.Obj =
        obj(
            "package" to str(app.packageName.value),
            "activity" to str(app.activityName.value),
            "profile" to str(app.profile.id.value),
            "profileType" to str(app.profile.type.name),
        )

    private fun decodeApp(root: StoredValue.Obj): AppIdentity? {
        val pkg = root.string("package")?.takeIf { it.isNotBlank() }
        val activity = root.string("activity")?.takeIf { it.isNotBlank() }
        val profile = decodeProfile(root)
        return if (pkg == null || activity == null || profile == null) {
            null
        } else {
            AppIdentity(AppPackageName(pkg), AppActivityName(activity), profile)
        }
    }

    private fun decodeProfile(root: StoredValue.Obj): AppProfile? {
        val id = root.string("profile")?.takeIf { it.isNotBlank() } ?: return null
        val type = root.string("profileType")?.let { name -> AppProfileType.entries.firstOrNull { it.name == name } }
        return type?.let { AppProfile(AppProfileId(id), it) }
    }
}
