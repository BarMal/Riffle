package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/**
 * A media source the launcher remembers (ADR 0003, Amendment 4): the app (package and profile) and when it was last
 * seen playing. That is all that is ever stored; what it last played is kept in memory only ([RecentPlayersTracker]).
 */
data class RecentPlayer(
    val packageName: String,
    val profileId: String?,
    val lastPlayedEpochMillis: Long,
) {
    /** The app's key (`package:profile`), as the Stream's groups name an app. */
    val key: String get() = keyOf(packageName, profileId)

    companion object {
        fun keyOf(
            packageName: String,
            profileId: String?,
        ): String = "$packageName:${profileId.orEmpty()}"
    }
}

/**
 * The rules of the recent players list, pure over plain values: most recent first, at most [CAP], one entry per app.
 * Every function returns the list unchanged (the same instance's content) when there is nothing to do, so a caller can
 * compare and write only on a change.
 */
object RecentPlayers {
    /** The most players remembered. */
    const val CAP = 5

    /** [players] after [packageName] (in [profileId]) was seen playing at [nowEpochMillis]: first, once, capped. */
    fun recordPlaying(
        players: List<RecentPlayer>,
        packageName: String,
        profileId: String?,
        nowEpochMillis: Long,
    ): List<RecentPlayer> {
        val key = RecentPlayer.keyOf(packageName, profileId)
        val entry = RecentPlayer(packageName, profileId, nowEpochMillis.coerceAtLeast(0L))
        return (listOf(entry) + players.filter { it.key != key }).ordered()
    }

    /** [players] without the app [key]: the user dismissed it; it comes back only when it plays again. */
    fun dismiss(
        players: List<RecentPlayer>,
        key: String,
    ): List<RecentPlayer> = players.filter { it.key != key }

    /** [players] without the apps that are no longer installed. [installedPackages] null (unknown) keeps them all. */
    fun pruneUninstalled(
        players: List<RecentPlayer>,
        installedPackages: Set<String>?,
    ): List<RecentPlayer> =
        if (installedPackages == null) players else players.filter { it.packageName in installedPackages }

    /**
     * The players that get a quiet "source" card: those of [players] whose app has no active session
     * ([activePackages]), most recent first. An app with an active session already has its player card.
     */
    fun sources(
        players: List<RecentPlayer>,
        activePackages: Set<String>,
    ): List<RecentPlayer> = players.filter { it.packageName !in activePackages }.ordered()

    /** Most recent first (ties by key, so equal times never shuffle), one per app, at most [CAP]. */
    fun List<RecentPlayer>.ordered(): List<RecentPlayer> =
        sortedWith(compareByDescending<RecentPlayer> { it.lastPlayedEpochMillis }.thenBy { it.key })
            .distinctBy { it.key }
            .take(CAP)
}

/** Where the list is kept. Blocking: called off the main thread. A failing read reads as an empty list. */
interface RecentPlayersStore {
    fun read(): List<RecentPlayer>

    fun write(players: List<RecentPlayer>)
}

/** The stored form of the list: package, profile and time, nothing else; decoding never throws and applies the cap. */
object RecentPlayersCodec {
    private const val VERSION = 1

    fun encode(players: List<RecentPlayer>): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "players" to
                arr(
                    players.map { player ->
                        obj(
                            "package" to str(player.packageName),
                            "profile" to player.profileId?.let(::str),
                            "playedAt" to num(player.lastPlayedEpochMillis),
                        )
                    },
                ),
        )

    fun decode(value: StoredValue?): List<RecentPlayer> {
        val root = value as? StoredValue.Obj ?: return emptyList()
        val decoded =
            root.array("players").mapNotNull { item ->
                val entry = item as? StoredValue.Obj ?: return@mapNotNull null
                val packageName = entry.string("package")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                RecentPlayer(
                    packageName = packageName,
                    profileId = entry.string("profile")?.takeIf { it.isNotBlank() },
                    lastPlayedEpochMillis = (entry.long("playedAt") ?: 0L).coerceAtLeast(0L),
                )
            }
        return with(RecentPlayers) { decoded.ordered() }
    }
}

/** One app's session as the media source saw it this time; [playing] false is a paused or stopped session. */
data class PlayerSighting(
    val packageName: String,
    val profileId: String?,
    val playing: Boolean,
    val title: String? = null,
    val artist: String? = null,
)

/** A recent player that has no session: what its source card says. [title] and [artist] are null when not known. */
data class RecentPlayerSource(
    val player: RecentPlayer,
    val title: String?,
    val artist: String?,
)

/**
 * The recent players over a [store]: records the apps seen playing, drops uninstalled ones, forgets dismissed ones and
 * says which have no session. The list is read once and written only when it changed. What a player last played (title
 * and artist, text only) is held here in memory for the life of the process and never written: a source card whose app
 * was not seen this process says only its app's name.
 *
 * Thread-safe; [store] calls block, so call it off the main thread.
 */
class RecentPlayersTracker(
    private val store: RecentPlayersStore,
    private val clock: () -> Long,
) {
    private var players: List<RecentPlayer>? = null

    /** False once a read failed: what is on disk is unknown, so nothing replaces it. */
    private var writable = true
    private val lastPlayed = HashMap<String, Pair<String?, String?>>()

    /**
     * Takes this look at the sessions: records those playing, remembers the text of every session of a known or new
     * player, prunes uninstalled apps ([installedPackages], null when the installed apps are unknown) and returns the
     * sources: the remembered players that have no session, most recent first.
     */
    @Synchronized
    fun observe(
        sightings: List<PlayerSighting>,
        installedPackages: Set<String>?,
    ): List<RecentPlayerSource> {
        val before = loaded()
        var current = before
        val now = clock()
        sightings.filter { it.playing }.forEach { sighting ->
            current = RecentPlayers.recordPlaying(current, sighting.packageName, sighting.profileId, now)
        }
        current = RecentPlayers.pruneUninstalled(current, installedPackages)
        val remembered = current.map { it.key }.toSet()
        sightings.forEach { sighting ->
            val key = RecentPlayer.keyOf(sighting.packageName, sighting.profileId)
            if (key in remembered && (sighting.title != null || sighting.artist != null)) {
                lastPlayed[key] = sighting.title to sighting.artist
            }
        }
        lastPlayed.keys.retainAll(remembered)
        commit(before, current)
        val active = sightings.map { it.packageName }.toSet()
        return RecentPlayers.sources(current, active).map { player ->
            val text = lastPlayed[player.key]
            RecentPlayerSource(player, text?.first, text?.second)
        }
    }

    /** The user dismissed the source [key]: it is forgotten until it plays again. */
    @Synchronized
    fun dismiss(key: String) {
        val before = loaded()
        lastPlayed.remove(key)
        commit(before, RecentPlayers.dismiss(before, key))
    }

    /** Forgets every recent player, the text too. */
    @Synchronized
    fun clear() {
        val before = loaded()
        lastPlayed.clear()
        commit(before, emptyList())
    }

    private fun loaded(): List<RecentPlayer> =
        players
            ?: runCatching { store.read() }
                .onFailure { writable = false }
                .getOrDefault(emptyList())
                .also { players = it }

    private fun commit(
        before: List<RecentPlayer>,
        after: List<RecentPlayer>,
    ) {
        players = after
        if (after != before && writable) runCatching { store.write(after) }
    }
}
