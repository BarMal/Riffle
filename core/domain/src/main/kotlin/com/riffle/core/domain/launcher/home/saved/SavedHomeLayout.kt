package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.pool.ArrangementPage
import com.riffle.core.domain.launcher.workspace.pool.PoolFolder
import com.riffle.core.domain.launcher.workspace.pool.PoolItem
import com.riffle.core.domain.launcher.workspace.pool.PoolItemId

/** Why a saved Home layout exists (ADR 0003, Amendment 17). */
enum class SavedHomeLayoutKind {
    /** Saved by the user with "Save current Home"; counts toward [SavedHomeLayouts.MAX_PER_DEVICE_CLASS]. */
    SAVED,

    /** Taken by Riffle just before a saved layout replaced Home; one per device class, overwritten by the next. */
    BEFORE_ACTIVATION,
}

/**
 * A snapshot of Home's pages: their grids, apps, folders, widgets and widget stacks (never the Dock, settings or
 * Stream). It is a value, not a live link: changes to Home never change it. Widgets are held as unbound descriptions
 * (provider, span constraints, stack order); a saved layout never holds a widget host id, so saving allocates nothing
 * and nothing can release an id the layout shares with Home.
 */
data class SavedHomeLayout(
    val id: String,
    val name: String,
    val kind: SavedHomeLayoutKind = SavedHomeLayoutKind.SAVED,
    val savedAtEpochMillis: Long = 0L,
    val pages: List<ArrangementPage> = emptyList(),
    val items: Map<PoolItemId, PoolItem> = emptyMap(),
) {
    /** What the list shows: how many pages the layout has. */
    val pageCount: Int get() = pages.size

    /** What the list shows: apps, folders and widgets placed on the pages (a folder or a stack counts once). */
    val itemCount: Int get() = pages.sumOf { it.placements.size }

    /** The apps inside folders are not counted by [itemCount]. */
    val folderEntryCount: Int get() = items.values.filterIsInstance<PoolFolder>().sumOf { it.entries.size }
}

/** The outcome of a change to [SavedHomeLayouts]; the layouts are unchanged for every rejection. */
sealed interface SavedHomeChange {
    data class Changed(val layouts: SavedHomeLayouts, val layout: SavedHomeLayout) : SavedHomeChange

    /** The device class already holds [SavedHomeLayouts.MAX_PER_DEVICE_CLASS] saved layouts. */
    data object Full : SavedHomeChange

    data object BlankName : SavedHomeChange

    data object Unknown : SavedHomeChange
}

/**
 * Every saved Home layout, per device class (the arrangement model of ADR 0003, decision 3: a layout belongs to the
 * class it was saved on). Pure value type; the order of a list is the order of saving, the automatic one last.
 */
@Suppress("TooManyFunctions") // One small operation per menu action.
data class SavedHomeLayouts(
    val byDeviceClass: Map<HomeLayoutDeviceClass, List<SavedHomeLayout>> = emptyMap(),
) {
    val isEmpty: Boolean get() = byDeviceClass.values.all { it.isEmpty() }

    fun forDeviceClass(deviceClass: HomeLayoutDeviceClass): List<SavedHomeLayout> = byDeviceClass[deviceClass].orEmpty()

    fun find(
        deviceClass: HomeLayoutDeviceClass,
        id: String,
    ): SavedHomeLayout? = forDeviceClass(deviceClass).firstOrNull { it.id == id }

    /** How many the user saved; the automatic one never counts. */
    fun savedCount(deviceClass: HomeLayoutDeviceClass): Int =
        forDeviceClass(deviceClass).count { it.kind == SavedHomeLayoutKind.SAVED }

    fun isFull(deviceClass: HomeLayoutDeviceClass): Boolean = savedCount(deviceClass) >= MAX_PER_DEVICE_CLASS

    /** Adds [layout] (whose kind must be [SavedHomeLayoutKind.SAVED]) unless the class is full or the name blank. */
    fun add(
        deviceClass: HomeLayoutDeviceClass,
        layout: SavedHomeLayout,
    ): SavedHomeChange {
        val name = layout.name.cleanName()
        return when {
            name.isEmpty() -> SavedHomeChange.BlankName
            layout.kind == SavedHomeLayoutKind.SAVED && isFull(deviceClass) -> SavedHomeChange.Full
            else -> {
                val added = layout.copy(name = name)
                SavedHomeChange.Changed(withList(deviceClass, savedFirst(forDeviceClass(deviceClass) + added)), added)
            }
        }
    }

    /**
     * Keeps [layout] (kind [SavedHomeLayoutKind.BEFORE_ACTIVATION]) as the one automatic layout of [deviceClass],
     * replacing the previous automatic one. Never fails and never counts toward the cap.
     */
    fun withAutomatic(
        deviceClass: HomeLayoutDeviceClass,
        layout: SavedHomeLayout,
    ): SavedHomeLayouts {
        val kept = forDeviceClass(deviceClass).filter { it.kind != SavedHomeLayoutKind.BEFORE_ACTIVATION }
        return withList(deviceClass, kept + layout.copy(kind = SavedHomeLayoutKind.BEFORE_ACTIVATION))
    }

    /**
     * Renames a layout. Renaming the automatic one promotes it to a saved layout (the user named it, so the next
     * activation must not overwrite it); that needs room, else [SavedHomeChange.Full].
     */
    fun rename(
        deviceClass: HomeLayoutDeviceClass,
        id: String,
        name: String,
    ): SavedHomeChange {
        val current = find(deviceClass, id)
        val clean = name.cleanName()
        return when {
            current == null -> SavedHomeChange.Unknown
            clean.isEmpty() -> SavedHomeChange.BlankName
            current.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION && isFull(deviceClass) -> SavedHomeChange.Full
            else -> {
                val renamed = current.copy(name = clean, kind = SavedHomeLayoutKind.SAVED)
                replaceOne(deviceClass, id, renamed)
            }
        }
    }

    /** Replaces the pages and items of [id] with [content] (a fresh capture), keeping its name and kind. */
    fun replaceContent(
        deviceClass: HomeLayoutDeviceClass,
        id: String,
        content: SavedHomeLayout,
    ): SavedHomeChange {
        val current = find(deviceClass, id) ?: return SavedHomeChange.Unknown
        return replaceOne(
            deviceClass,
            id,
            current.copy(
                pages = content.pages,
                items = content.items,
                savedAtEpochMillis = content.savedAtEpochMillis,
            ),
        )
    }

    fun delete(
        deviceClass: HomeLayoutDeviceClass,
        id: String,
    ): SavedHomeChange {
        val current = find(deviceClass, id) ?: return SavedHomeChange.Unknown
        return SavedHomeChange.Changed(
            withList(deviceClass, forDeviceClass(deviceClass).filterNot { it.id == id }),
            current,
        )
    }

    /** Puts a layout back where [delete] took it from (Undo of a delete). Rejected when it would exceed the cap. */
    fun restore(
        deviceClass: HomeLayoutDeviceClass,
        layout: SavedHomeLayout,
    ): SavedHomeChange =
        when {
            find(deviceClass, layout.id) != null -> SavedHomeChange.Changed(this, layout)
            layout.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION ->
                SavedHomeChange.Changed(withAutomatic(deviceClass, layout), layout)
            else -> add(deviceClass, layout)
        }

    private fun replaceOne(
        deviceClass: HomeLayoutDeviceClass,
        id: String,
        replacement: SavedHomeLayout,
    ): SavedHomeChange =
        SavedHomeChange.Changed(
            withList(deviceClass, forDeviceClass(deviceClass).map { if (it.id == id) replacement else it }),
            replacement,
        )

    private fun savedFirst(list: List<SavedHomeLayout>): List<SavedHomeLayout> =
        list.filter { it.kind == SavedHomeLayoutKind.SAVED } + list.filter { it.kind != SavedHomeLayoutKind.SAVED }

    private fun withList(
        deviceClass: HomeLayoutDeviceClass,
        list: List<SavedHomeLayout>,
    ): SavedHomeLayouts =
        copy(byDeviceClass = (byDeviceClass + (deviceClass to savedFirst(list))).filterValues { it.isNotEmpty() })

    companion object {
        const val MAX_PER_DEVICE_CLASS = 12
        const val MAX_NAME_LENGTH = 40

        /** [name] trimmed, inner whitespace collapsed and cut to [MAX_NAME_LENGTH]. */
        fun String.cleanName(): String = trim().replace(WHITESPACE, " ").take(MAX_NAME_LENGTH).trim()

        private val WHITESPACE = Regex("\\s+")

        /**
         * The first "<prefix> N" (N from 1) that [existing] does not use; [format] builds the text from N so the
         * wording stays in resources ("Home layout 1").
         */
        fun nextName(
            existing: Collection<String>,
            format: (Int) -> String,
        ): String {
            val used = existing.map { it.cleanName().lowercase() }.toSet()
            return generateSequence(1) { it + 1 }.map(format).first { it.cleanName().lowercase() !in used }
        }
    }
}
