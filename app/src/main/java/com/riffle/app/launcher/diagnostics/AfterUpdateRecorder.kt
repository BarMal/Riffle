package com.riffle.app.launcher.diagnostics

import com.riffle.core.domain.launcher.diagnostics.AfterUpdateLog
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateState
import com.riffle.core.domain.launcher.diagnostics.PackageStamp
import com.riffle.core.domain.launcher.diagnostics.StartFacts
import com.riffle.core.domain.launcher.diagnostics.StartMilestone
import java.util.concurrent.Executor

/** What the recorder asks the platform for, off the main thread: the package's stamp and the Home and task facts. */
internal data class StartProbe(
    val pkg: PackageStamp,
    val homeRoleHeld: Boolean?,
    val homeResolvesTo: String?,
    /** The base intent of this activity's task as diagnostics text; null when it could not be read. */
    val taskBaseIntent: String? = null,
)

/**
 * Records every process start, marking the first one after an update ([AfterUpdateLog]). Everything runs on
 * [executor], in call order, one task at a time: the store is read and written there (never on the main thread, so no
 * start-up read is added), and the platform probe runs there too. Failures are swallowed: this is diagnostics and must
 * never affect the launcher. No timers: the five-minute window is judged from the timestamps of the calls.
 */
internal class AfterUpdateRecorder(
    private val store: AfterUpdateStorePort,
    private val executor: Executor,
) {
    /** The entry this process opened, or null until (or unless) it was recorded; touched on [executor] only. */
    private var openEntryAtMillis: Long? = null

    /** Process start: [probe] runs on the executor and this start is recorded, as an update start or a normal one. */
    fun onStart(
        atMillis: Long,
        intentText: String,
        isTaskRoot: Boolean,
        cold: Boolean? = null,
        sdkInt: Int? = null,
        probe: () -> StartProbe,
    ) = run {
        val platform = probe()
        val facts =
            StartFacts(
                atMillis = atMillis,
                intentText = intentText,
                isTaskRoot = isTaskRoot,
                homeRoleHeld = platform.homeRoleHeld,
                homeResolvesTo = platform.homeResolvesTo,
                cold = cold,
                sdkInt = sdkInt,
                taskBaseIntent = platform.taskBaseIntent,
            )
        val before = store.read()
        val after = AfterUpdateLog.onStart(before, platform.pkg, facts)
        store.write(after)
        openEntryAtMillis = atMillis
    }

    /**
     * The first frame was drawn [millis] after the process started, with the [milestones] reached so far: one write
     * for both.
     */
    fun onFirstFrame(
        millis: Long,
        milestones: List<StartMilestone> = emptyList(),
    ) = change { state, startedAt ->
        val framed = AfterUpdateLog.withFirstFrame(state, startedAt, millis)
        if (milestones.isEmpty()) framed else AfterUpdateLog.withMilestones(framed, startedAt, milestones)
    }

    /** A milestone was reached after the first frame: the newest list of [milestones] replaces the stored one. */
    fun onMilestones(milestones: List<StartMilestone>) =
        change { state, startedAt -> AfterUpdateLog.withMilestones(state, startedAt, milestones) }

    /** A new intent carrying `CATEGORY_HOME` arrived at [nowMillis]. */
    fun onHomeIntent(nowMillis: Long) =
        change { state, startedAt -> AfterUpdateLog.withHomeIntent(state, startedAt, nowMillis) }

    private fun change(edit: (AfterUpdateState, Long) -> AfterUpdateState) =
        run {
            val startedAt = openEntryAtMillis ?: return@run
            val before = store.read()
            val after = edit(before, startedAt)
            if (after != before) store.write(after)
        }

    private fun run(task: () -> Unit) = executor.execute { runCatching(task) }
}
