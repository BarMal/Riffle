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

/** One start-up milestone: [label] and the time it was reached, in milliseconds since the process started. */
data class StartMilestone(
    val label: String,
    val millis: Long,
)

/**
 * What the launcher saw on a process start (Settings > About > After-update starts): every cold start is kept (the last
 * ten), with [afterUpdate] telling the first start after an update from the rest, to look into "the home gesture
 * stops reaching the launcher after an update" (see `docs/product/places-views-plan.md`, "After-update starts").
 * Facts only; nothing here changes behaviour. The fields after [homeIntentsAtMillis] were added later: entries stored
 * before them decode with their defaults (every stored entry then was an after-update one).
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
    /** Whether this was the first start after an update (a new version code or last-update time). */
    val afterUpdate: Boolean = true,
    /** Whether the process was young when the activity was created; null when unknown (an older entry). */
    val cold: Boolean? = null,
    /** `Build.VERSION.SDK_INT` of the device; null when unknown. */
    val sdkInt: Int? = null,
    /** The intent the task of this activity is rooted in (its base intent) as text; null when it could not be read. */
    val taskBaseIntent: String? = null,
    /** The start-up milestones reached, in the order they were reached. */
    val milestones: List<StartMilestone> = emptyList(),
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
    val cold: Boolean? = null,
    val sdkInt: Int? = null,
    val taskBaseIntent: String? = null,
)

/** The stored diagnostics: the last package stamp seen and the bounded list of entries, newest first. */
data class AfterUpdateState(
    val lastVersionCode: Long? = null,
    val lastUpdateTimeMillis: Long? = null,
    val entries: List<AfterUpdateStart> = emptyList(),
)

/** The label of the first-frame milestone; its time is [AfterUpdateStart.firstFrameMillis]. */
const val FIRST_FRAME_LABEL = "first frame"

/** The rules, pure. */
object AfterUpdateLog {
    const val MAX_ENTRIES = 10
    const val WATCH_MILLIS = 5L * 60L * 1000L
    const val MAX_HOME_INTENTS = 50
    const val MAX_MILESTONES = 40

    /** A process whose first activity is created later than this after the process started was not a cold start. */
    const val COLD_START_MAX_MILLIS = 10_000L

    /** Whether an activity created [onCreateMillis] after the process started belongs to a cold start. */
    fun isColdStart(onCreateMillis: Long?): Boolean? = onCreateMillis?.let { it in 0..COLD_START_MAX_MILLIS }

    /**
     * The state after a process start: an entry is added for every start, marked [AfterUpdateStart.afterUpdate] when
     * the version code or the last-update time differs from what was stored, except on a fresh install (nothing stored
     * and the package installed for the first time), which is a normal start.
     */
    fun onStart(
        state: AfterUpdateState,
        pkg: PackageStamp,
        facts: StartFacts,
    ): AfterUpdateState {
        val changed = state.lastVersionCode != pkg.versionCode || state.lastUpdateTimeMillis != pkg.lastUpdateTimeMillis
        val fresh = state.lastVersionCode == null && pkg.firstInstallTimeMillis == pkg.lastUpdateTimeMillis
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
                afterUpdate = changed && !fresh,
                cold = facts.cold,
                sdkInt = facts.sdkInt,
                taskBaseIntent = facts.taskBaseIntent,
            )
        val stamped = state.copy(lastVersionCode = pkg.versionCode, lastUpdateTimeMillis = pkg.lastUpdateTimeMillis)
        return stamped.copy(entries = (listOf(entry) + state.entries).take(MAX_ENTRIES))
    }

    /** The entry started at [startedAtMillis] with its milestones replaced by [milestones] (the newest list). */
    fun withMilestones(
        state: AfterUpdateState,
        startedAtMillis: Long,
        milestones: List<StartMilestone>,
    ): AfterUpdateState =
        state.updated(startedAtMillis) { entry -> entry.copy(milestones = milestones.take(MAX_MILESTONES)) }

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
                appendLine(headline(e, time))
                appendLine("start: ${coldText(e.cold)}, Android SDK ${e.sdkInt ?: "unknown"}")
                appendLine("task root intent: ${e.taskBaseIntent ?: "unknown"}")
                appendLine("intent: ${e.intentText}")
                appendLine("isTaskRoot: ${e.isTaskRoot}")
                appendLine("home role held: ${e.homeRoleHeld ?: "unknown"}")
                appendLine("home resolves to: ${e.homeResolvesTo ?: "none"}")
                milestoneLines(e).forEach { appendLine(it) }
                append(
                    if (e.homeIntentsAtMillis.isEmpty()) {
                        "home intents in the first 5 minutes: none"
                    } else {
                        "home intents in the first 5 minutes: " + e.homeIntentsAtMillis.joinToString { time(it) }
                    },
                )
            }
        }

    private fun headline(
        e: AfterUpdateStart,
        time: (Long) -> String,
    ): String =
        if (e.afterUpdate) {
            "${time(e.atMillis)}  ${e.fromVersionCode ?: "?"} -> ${e.toVersionCode}  (after update)"
        } else {
            "${time(e.atMillis)}  ${e.toVersionCode}  (normal start)"
        }

    private fun coldText(cold: Boolean?): String =
        when (cold) {
            true -> "cold"
            false -> "not cold"
            null -> "unknown"
        }

    /** One `label: N ms` line per milestone, first frame included, in the order of the times (process start = 0). */
    private fun milestoneLines(e: AfterUpdateStart): List<String> {
        val all =
            e.milestones.filter { it.label != FIRST_FRAME_LABEL } +
                listOfNotNull(e.firstFrameMillis?.let { StartMilestone(FIRST_FRAME_LABEL, it) })
        val lines = all.sortedBy { it.millis }.map { "${it.label}: ${it.millis} ms" }
        return if (e.firstFrameMillis == null) lines + "$FIRST_FRAME_LABEL: not drawn" else lines
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
            "afterUpdate" to StoredValue.Bool(e.afterUpdate),
            "cold" to e.cold?.let { StoredValue.Bool(it) },
            "sdk" to e.sdkInt?.let { num(it) },
            "taskBase" to e.taskBaseIntent?.let { str(it) },
            "milestones" to arr(e.milestones.map { obj("label" to str(it.label), "ms" to num(it.millis)) }),
        )

    private fun decodeMilestone(value: StoredValue): StartMilestone? {
        val root = value as? StoredValue.Obj
        val label = root?.string("label")
        val millis = root?.long("ms")
        return if (label == null || millis == null) null else StartMilestone(label, millis)
    }

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
            // Entries stored before the log kept every start were all after-update ones.
            afterUpdate = root.bool("afterUpdate") ?: true,
            cold = root.bool("cold"),
            sdkInt = root.long("sdk")?.toInt(),
            taskBaseIntent = root.string("taskBase"),
            milestones =
                root.array("milestones").mapNotNull(::decodeMilestone).take(AfterUpdateLog.MAX_MILESTONES),
        )
    }
}
