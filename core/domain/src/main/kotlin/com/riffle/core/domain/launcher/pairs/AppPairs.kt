package com.riffle.core.domain.launcher.pairs

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
 * A pair (ADR 0003, Amendment 7): two apps that open side by side, [first] on the start (left) pane. That is all
 * that is stored with when it was last opened; nothing about windows is stored. Named `AppPair` so it is not taken
 * for the Stream's or the Home's own "pair".
 */
data class AppPair(
    val id: String,
    val first: AppIdentity,
    val second: AppIdentity,
    val lastOpenedEpochMillis: Long = 0L,
) {
    init {
        require(id.isNotBlank()) { "Pair ids must not be blank." }
    }

    /** The ordered pair: A|B and B|A are different pairs. */
    val sides: Pair<AppIdentity, AppIdentity> get() = first to second

    fun uses(
        packageName: AppPackageName,
        profile: AppProfile,
    ): Boolean =
        (first.packageName == packageName && first.profile == profile) ||
            (second.packageName == packageName && second.profile == profile)
}

/**
 * The rules of the pairs list, pure over plain values. The list is the stack's order, front first: most recently
 * opened first (a new pair, and one that was just opened, goes to the front). Every function returns the list
 * unchanged when there is nothing to do, so a caller can compare and write only on a change.
 */
@Suppress("TooManyFunctions") // One small function per edit the stack offers.
object AppPairs {
    /** The most pairs kept: a stack stays a thing to flick through; the oldest drops when another is added. */
    const val CAP = 12

    /**
     * [pairs] with [pair] added at the front. The same ordered pair is never held twice: an existing pair of the
     * pair is brought to the front instead (and takes [AppPair.lastOpenedEpochMillis] of [pair]). An app cannot be
     * paired with itself.
     */
    fun add(
        pairs: List<AppPair>,
        pair: AppPair,
    ): List<AppPair> {
        if (pair.first.sameApp(pair.second)) return pairs
        val existing = pairs.firstOrNull { it.sides == pair.sides || it.id == pair.id }
        val entry = existing?.copy(lastOpenedEpochMillis = pair.lastOpenedEpochMillis) ?: pair
        return (listOf(entry) + pairs.filter { it !== existing }).capped()
    }

    /** [pairs] without [id]. */
    fun remove(
        pairs: List<AppPair>,
        id: String,
    ): List<AppPair> = pairs.filter { it.id != id }

    /** Undo of [remove]: [pair] back at [index] (clamped), unless its id or pair is already held. */
    fun restore(
        pairs: List<AppPair>,
        pair: AppPair,
        index: Int,
    ): List<AppPair> {
        if (pairs.any { it.id == pair.id || it.sides == pair.sides }) return pairs
        return pairs.toMutableList().apply { add(index.coerceIn(0, size), pair) }.capped()
    }

    /** The two sides of [id] exchanged. */
    fun swap(
        pairs: List<AppPair>,
        id: String,
    ): List<AppPair> {
        val pair = pairs.firstOrNull { it.id == id } ?: return pairs
        return replace(pairs, pair.copy(first = pair.second, second = pair.first))
    }

    /** The first app of [id] changed to [app]; the second app can not be chosen again (nothing changes then). */
    fun changeFirst(
        pairs: List<AppPair>,
        id: String,
        app: AppIdentity,
    ): List<AppPair> {
        val pair = pairs.firstOrNull { it.id == id } ?: return pairs
        return if (app.sameApp(pair.second)) pairs else replace(pairs, pair.copy(first = app))
    }

    /** The second app of [id] changed to [app]. */
    fun changeSecond(
        pairs: List<AppPair>,
        id: String,
        app: AppIdentity,
    ): List<AppPair> {
        val pair = pairs.firstOrNull { it.id == id } ?: return pairs
        return if (app.sameApp(pair.first)) pairs else replace(pairs, pair.copy(second = app))
    }

    /**
     * [edited] in place of the pair of its id. When that makes it the ordered pair another pair already is, the
     * edited one goes and the other comes to the front: the same pair is never held twice.
     */
    private fun replace(
        pairs: List<AppPair>,
        edited: AppPair,
    ): List<AppPair> {
        val duplicate = pairs.firstOrNull { it.id != edited.id && it.sides == edited.sides }
        return if (duplicate == null) {
            pairs.map { if (it.id == edited.id) edited else it }
        } else {
            listOf(duplicate) + pairs.filter { it.id != edited.id && it !== duplicate }
        }
    }

    /** [id] was opened at [nowEpochMillis]: it takes that time and comes to the front. */
    fun opened(
        pairs: List<AppPair>,
        id: String,
        nowEpochMillis: Long,
    ): List<AppPair> {
        val pair = pairs.firstOrNull { it.id == id } ?: return pairs
        val opened = pair.copy(lastOpenedEpochMillis = nowEpochMillis.coerceAtLeast(pair.lastOpenedEpochMillis))
        return (listOf(opened) + pairs.filter { it.id != id }).ordered()
    }

    /** The pairs that use the app [packageName] of [profile] go: a pair with an app that is gone is not kept. */
    fun removeApp(
        pairs: List<AppPair>,
        packageName: AppPackageName,
        profile: AppProfile,
    ): List<AppPair> = pairs.filterNot { it.uses(packageName, profile) }

    /** [pairs] without those that use an app that is not in [installed]; null (not known) keeps them all. */
    fun pruneUninstalled(
        pairs: List<AppPair>,
        installed: Set<AppIdentity>?,
    ): List<AppPair> {
        if (installed == null) return pairs
        val keys = installed.mapTo(HashSet()) { it.packageName to it.profile }
        return pairs.filter { pair -> listOf(pair.first, pair.second).all { it.key() in keys } }
    }

    /** Most recently opened first; equal times keep their order, so the stack never shuffles. */
    fun List<AppPair>.ordered(): List<AppPair> = sortedByDescending { it.lastOpenedEpochMillis }

    private fun List<AppPair>.capped(): List<AppPair> = take(CAP)

    /** The same app (package and profile; the activity is the launcher entry it was chosen as). */
    private fun AppIdentity.sameApp(other: AppIdentity): Boolean = key() == other.key()

    private fun AppIdentity.key(): Pair<AppPackageName, AppProfile> = packageName to profile

    /**
     * The pairs a migration adds after [existing]: those of [migrated] whose id or ordered pair is not already held,
     * behind the pairs that are (they were never opened, so they sit last, in the order given), up to the cap.
     * Idempotent: running it again with the same input changes nothing.
     */
    fun addMigrated(
        existing: List<AppPair>,
        migrated: List<AppPair>,
    ): List<AppPair> {
        var result = existing
        migrated.forEach { pair ->
            val held = result.any { it.id == pair.id || it.sides == pair.sides }
            if (!held && !pair.first.sameApp(pair.second) && result.size < CAP) result = result + pair
        }
        return result
    }
}

/** Where the pairs are kept. Blocking: called off the main thread. A failing read reads as an empty list. */
interface AppPairsStore {
    fun read(): List<AppPair>

    fun write(pairs: List<AppPair>)
}

/** The stored form of the pairs: ids, the two apps and the time; decoding never throws and applies the cap. */
object AppPairsCodec {
    private const val VERSION = 1

    fun encode(pairs: List<AppPair>): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "pairs" to
                arr(
                    pairs.map { pair ->
                        obj(
                            "id" to str(pair.id),
                            "first" to encodeApp(pair.first),
                            "second" to encodeApp(pair.second),
                            "openedAt" to num(pair.lastOpenedEpochMillis),
                        )
                    },
                ),
        )

    fun decode(value: StoredValue?): List<AppPair> {
        val root = value as? StoredValue.Obj ?: return emptyList()
        val decoded =
            root.array("pairs").mapNotNull { item ->
                val entry = item as? StoredValue.Obj ?: return@mapNotNull null
                val id = entry.string("id")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val first = entry.obj("first")?.let(::decodeApp) ?: return@mapNotNull null
                val second = entry.obj("second")?.let(::decodeApp) ?: return@mapNotNull null
                AppPair(id, first, second, (entry.long("openedAt") ?: 0L).coerceAtLeast(0L))
            }
        // Stored in the stack's order: kept as it is, with a pair that is held twice or paired with itself dropped.
        return AppPairs.addMigrated(emptyList(), decoded)
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
