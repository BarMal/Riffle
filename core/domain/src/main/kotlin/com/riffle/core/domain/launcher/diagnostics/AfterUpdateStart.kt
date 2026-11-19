package com.riffle.core.domain.launcher.diagnostics

import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string

/**
 * What the launcher saw on the first process start after an update (Settings > About > Diagnostics > After-update
 * starts), kept to look into "the home gesture stops reaching the launcher after an update" (see
 * `docs/product/places-views-plan.md`, "After-update starts"). Facts only; nothing here changes behaviour.
 */
data class AfterUpdateStart(
    /** When the start was recorded, epoch milliseconds. */
    val atMillis: Long,
    /** The version code before the update; null when this build did not know (it recorded nothing earlier). */
    val fromVersionCode: Long?,
    val toVersionCode: Long,
    /** The intent the activity was started with: action, categories and flags as text. */
    val intentText: String,
    val isTaskRoot: Boolean,
    /** Whether the app held the Home role; null when it could not be asked. */
    val homeRoleHeld: Boolean?,
    /** The component that `ACTION_MAIN` + `CATEGORY_HOME` resolved to; null when none or unknown. */
    val homeResolvesTo: String?,
    /** Process start to first frame, milliseconds; null until the first frame was drawn. */
    val firstFrameMillis: Long?,
    /** When a new intent carrying `CATEGORY_HOME` arrived in the first minutes of this run (timestamps only). */
    val homeIntentsAtMillis: List<Long> = emptyList(),
)

/** The package facts a start is compared with: the stored ones tell whether an update happened. */
data class PackageStamp(
    val versionCode: Long,
    val lastUpdateTimeMillis: Long,
    val firstInstallTimeMillis: Long,
)

/** What is known when the activity starts, before the first frame. */
data class StartFacts(
    val atMillis: Long,
    val intentText: String,
    val isTaskRoot: Boolean,
    val homeRoleHeld: Boolean?,
    val homeResolvesTo: String?,
)

/** The stored diagnostics: the last package stamp seen and the bounded list of entries, newest first. */
data class AfterUpdateState(
    val lastVersionCode: Long? = null,
    val lastUpdateTimeMillis: Long? = null,
    val entries: List<AfterUpdateStart> = emptyList(),
)

/** The rules, pure. */
object AfterUpdateLog {
    const val MAX_ENTRIES = 10
    const val WATCH_MILLIS = 5L * 60L * 1000L
    const val MAX_HOME_INTENTS = 50

    /**
     * The state after a process start, and whether an entry was added. An entry is added when the version code or
     * the last-update time differs from what was stored, except on a fresh install (nothing stored and the package
     * installed for the first time), which only records the stamp.
     */
    fun onStart(
        state: AfterUpdateState,
        pkg: PackageStamp,
        facts: StartFacts,
    ): Pair<AfterUpdateState, Boolean> {
        val changed = state.lastVersionCode != pkg.versionCode || state.lastUpdateTimeMillis != pkg.lastUpdateTimeMillis
        val fresh = state.lastVersionCode == null && pkg.firstInstallTimeMillis == pkg.lastUpdateTimeMillis
        if (!changed || fresh) {
            return if (changed) {
                state.copy(lastVersionCode = pkg.versionCode, lastUpdateTimeMillis = pkg.lastUpdateTimeMillis) to false
            } else {
                state to false
            }
        }
        val stamped = state.copy(lastVersionCode = pkg.versionCode, lastUpdateTimeMillis = pkg.lastUpdateTimeMillis)
        val entry =
            AfterUpdateStart(
                atMillis = facts.atMillis,
                fromVersionCode = state.lastVersionCode,
                toVersionCode = pkg.versionCode,
                intentText = facts.intentText,
                isTaskRoot = facts.isTaskRoot,
                homeRoleHeld = facts.homeRoleHeld,
                homeResolvesTo = facts.homeResolvesTo,
                firstFrameMillis = null,
            )
        return stamped.copy(entries = (listOf(entry) + state.entries).take(MAX_ENTRIES)) to true
    }

    /** The entry started at [startedAtMillis] with its time to first frame set (once). */
    fun withFirstFrame(
        state: AfterUpdateState,
        startedAtMillis: Long,
        millis: Long,
    ): AfterUpdateState =
        state.updated(startedAtMillis) { entry ->
            if (entry.firstFrameMillis == null) entry.copy(firstFrameMillis = millis.coerceAtLeast(0)) else entry
        }

    /** A Home intent at [nowMillis]: added to the entry started at [startedAtMillis] while its watch window is open. */
    fun withHomeIntent(
        state: AfterUpdateState,
        startedAtMillis: Long,
        nowMillis: Long,
    ): AfterUpdateState =
        state.updated(startedAtMillis) { entry ->
            if (nowMillis - startedAtMillis !in 0..WATCH_MILLIS || entry.homeIntentsAtMillis.size >= MAX_HOME_INTENTS) {
                entry
            } else {
                entry.copy(homeIntentsAtMillis = entry.homeIntentsAtMillis + nowMillis)
            }
        }

    /** [entries] as plain text for the page and the Copy button; [time] formats an epoch time. */
    fun asText(
        entries: List<AfterUpdateStart>,
        time: (Long) -> String,
    ): String =
        entries.joinToString(separator = "\n\n") { e ->
            buildString {
                appendLine("${time(e.atMillis)}  ${e.fromVersionCode ?: "?"} -> ${e.toVersionCode}")
                appendLine("intent: ${e.intentText}")
                appendLine("isTaskRoot: ${e.isTaskRoot}")
                appendLine("home role held: ${e.homeRoleHeld ?: "unknown"}")
                appendLine("home resolves to: ${e.homeResolvesTo ?: "none"}")
                appendLine("first frame: ${e.firstFrameMillis?.let { "$it ms" } ?: "not drawn"}")
                append(
                    if (e.homeIntentsAtMillis.isEmpty()) {
                        "home intents in the first 5 minutes: none"
                    } else {
                        "home intents in the first 5 minutes: " + e.homeIntentsAtMillis.joinToString { time(it) }
                    },
                )
            }
        }

    private fun AfterUpdateState.updated(
        startedAtMillis: Long,
        change: (AfterUpdateStart) -> AfterUpdateStart,
    ): AfterUpdateState = copy(entries = entries.map { if (it.atMillis == startedAtMillis) change(it) else it })
}

/**
 * Codec of [AfterUpdateState] over [StoredValue]: additive and safe, decoding never throws, bad entries are dropped
 * and the list is held to [AfterUpdateLog.MAX_ENTRIES].
 */
object AfterUpdateCodec {
    const val VERSION = 1

    fun encode(state: AfterUpdateState): StoredValue.Obj =
        obj(
            "version" to num(VERSION),
            "lastVersionCode" to state.lastVersionCode?.let { num(it) },
            "lastUpdateTime" to state.lastUpdateTimeMillis?.let { num(it) },
            "entries" to arr(state.entries.map(::encodeEntry)),
        )

    fun decode(value: StoredValue?): AfterUpdateState {
        val root = value as? StoredValue.Obj ?: return AfterUpdateState()
        return AfterUpdateState(
            lastVersionCode = root.long("lastVersionCode"),
            lastUpdateTimeMillis = root.long("lastUpdateTime"),
            entries = root.array("entries").mapNotNull(::decodeEntry).take(AfterUpdateLog.MAX_ENTRIES),
        )
    }

    private fun encodeEntry(e: AfterUpdateStart): StoredValue.Obj =
        obj(
            "at" to num(e.atMillis),
            "from" to e.fromVersionCode?.let { num(it) },
            "to" to num(e.toVersionCode),
            "intent" to str(e.intentText),
            "taskRoot" to StoredValue.Bool(e.isTaskRoot),
            "role" to e.homeRoleHeld?.let { StoredValue.Bool(it) },
            "homeResolvesTo" to e.homeResolvesTo?.let { str(it) },
            "firstFrame" to e.firstFrameMillis?.let { num(it) },
            "homeIntents" to arr(e.homeIntentsAtMillis.map { num(it) }),
        )

    private fun decodeEntry(value: StoredValue): AfterUpdateStart? {
        val root = value as? StoredValue.Obj
        val at = root?.long("at")
        val to = root?.long("to")
        if (root == null || at == null || to == null) return null
        return AfterUpdateStart(
            atMillis = at,
            fromVersionCode = root.long("from"),
            toVersionCode = to,
            intentText = root.string("intent") ?: "",
            isTaskRoot = root.bool("taskRoot") ?: false,
            homeRoleHeld = root.bool("role"),
            homeResolvesTo = root.string("homeResolvesTo"),
            firstFrameMillis = root.long("firstFrame"),
            homeIntentsAtMillis =
                root.array("homeIntents").mapNotNull { (it as? StoredValue.Num)?.value }
                    .take(AfterUpdateLog.MAX_HOME_INTENTS),
        )
    }
}
