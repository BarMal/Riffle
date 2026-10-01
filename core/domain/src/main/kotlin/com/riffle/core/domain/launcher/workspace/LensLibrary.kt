package com.riffle.core.domain.launcher.workspace

/** Random, immutable id of a [SavedLens]; a stored contract like [SourceIds]. Never shown to the user. */
@JvmInline
value class LensId(val value: String) {
    init {
        require(value.isNotBlank()) { "Lens ids must not be blank." }
    }
}

/** Where a saved lens came from, when it was not made by the user. Identity for reinstalls, not the name. */
sealed interface LensOrigin {
    /** A lens a built-in preset installed; [key] is stable across releases (for example "finder"). */
    data class Preset(val presetId: String, val key: String) : LensOrigin
}

/** A named, reusable lens definition. Definition only: never item content. */
data class SavedLens(
    val id: LensId,
    val name: String,
    val lens: Lens,
    val origin: LensOrigin? = null,
)

/** Why a library operation did not apply. */
enum class LibraryProblem {
    BLANK_NAME,
    NAME_TOO_LONG,
    NAME_TAKEN,
    LIBRARY_FULL,
    UNKNOWN_LENS,
}

sealed interface LibraryAdd {
    data class Added(val library: LensLibrary, val id: LensId) : LibraryAdd

    data class Rejected(val problem: LibraryProblem) : LibraryAdd
}

const val MAX_SAVED_LENSES = 100
const val MAX_SAVED_LENS_NAME = 40

/**
 * One per layout (device class), owned by that layout's [LayoutWorkspaces]. Ordered for display, at most
 * [MAX_SAVED_LENSES] entries, names trimmed, 1..[MAX_SAVED_LENS_NAME] chars and unique case-insensitively.
 * Operations return the receiver unchanged when they cannot apply (the WS5 convention); [tryAdd] and
 * [nameProblem] say why.
 */
data class LensLibrary(val lenses: List<SavedLens> = emptyList()) {
    fun find(id: LensId): SavedLens? = lenses.firstOrNull { it.id == id }

    /** Null when [name] would be accepted (for a new entry, or for [excluding] being renamed). */
    fun nameProblem(
        name: String,
        excluding: LensId? = null,
    ): LibraryProblem? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> LibraryProblem.BLANK_NAME
            trimmed.length > MAX_SAVED_LENS_NAME -> LibraryProblem.NAME_TOO_LONG
            lenses.any { it.id != excluding && it.name.equals(trimmed, ignoreCase = true) } -> LibraryProblem.NAME_TAKEN
            else -> null
        }
    }

    fun tryAdd(
        name: String,
        lens: Lens,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
        origin: LensOrigin? = null,
    ): LibraryAdd {
        val problem = if (lenses.size >= MAX_SAVED_LENSES) LibraryProblem.LIBRARY_FULL else nameProblem(name)
        if (problem != null) return LibraryAdd.Rejected(problem)
        val entry = SavedLens(LensId(ids.next()), name.trim(), lens, origin)
        val added = insert(entry, lenses.size)
        return if (added == null) LibraryAdd.Rejected(LibraryProblem.NAME_TAKEN) else LibraryAdd.Added(added, entry.id)
    }

    fun rename(
        id: LensId,
        name: String,
    ): LensLibrary =
        if (find(id) == null || nameProblem(name, id) != null) {
            this
        } else {
            copy(lenses = lenses.map { if (it.id == id) it.copy(name = name.trim()) else it })
        }

    /** Replaces the definition of [id]. Dependents' snapshots are refreshed by [LensLibraryOps.applyEdit]. */
    fun update(
        id: LensId,
        lens: Lens,
    ): LensLibrary = copy(lenses = lenses.map { if (it.id == id) it.copy(lens = lens) else it })

    /** Inserts a copy right after [id], named "<name> copy" ("copy 2"...), with a fresh id and no origin. */
    fun duplicate(
        id: LensId,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): LensLibrary {
        val source = find(id) ?: return this
        val name = LensNames.unique(source.name, LensNames.of(lenses)) { if (it == 1) " copy" else " copy $it" }
        val entry = SavedLens(LensId(ids.next()), name, source.lens)
        return insert(entry, lenses.indexOfFirst { it.id == id } + 1) ?: this
    }

    fun move(
        id: LensId,
        toIndex: Int,
    ): LensLibrary {
        val moving = find(id) ?: return this
        val without = lenses.filterNot { it.id == id }
        return copy(lenses = without.toMutableList().apply { add(toIndex.coerceIn(0, without.size), moving) })
    }

    fun remove(id: LensId): LensLibrary = copy(lenses = lenses.filterNot { it.id == id })

    /**
     * Appends a copy of [lens] (from another library or a preset) with a fresh id and a collision-free
     * name, keeping its origin. Returns null when the library is full.
     */
    fun addCopy(
        lens: SavedLens,
        ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    ): Pair<LensLibrary, LensId>? {
        val name = LensNames.unique(lens.name, LensNames.of(lenses)) { " ${it + 1}" }
        val entry = SavedLens(LensId(ids.next()), name, lens.lens, lens.origin)
        return insert(entry, lenses.size)?.let { it to entry.id }
    }

    private fun insert(
        entry: SavedLens,
        index: Int,
    ): LensLibrary? =
        if (lenses.size >= MAX_SAVED_LENSES || find(entry.id) != null) {
            null
        } else {
            copy(lenses = lenses.toMutableList().apply { add(index.coerceIn(0, lenses.size), entry) })
        }
}
