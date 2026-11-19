package com.riffle.app.launcher.diagnostics

import com.riffle.core.domain.launcher.diagnostics.AfterUpdateLog
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateState
import com.riffle.core.domain.launcher.diagnostics.PackageStamp
import com.riffle.core.domain.launcher.diagnostics.StartFacts
import java.util.concurrent.Executor

/** What the recorder asks the platform for, off the main thread: the package's stamp and two Home facts. */
internal data class StartProbe(
    val pkg: PackageStamp,
    val homeRoleHeld: Boolean?,
    val homeResolvesTo: String?,
)

/**
 * Records the first start after an update ([AfterUpdateLog]). Everything runs on [executor], in call order, one task at
 * a time: the store is read and written there (never on the main thread, so no start-up read is added), and the
 * platform probe runs there too. Failures are swallowed: this is diagnostics and must never affect the launcher. No
 * timers: the five-minute window is judged from the timestamps of the calls.
 */
internal class AfterUpdateRecorder(
    private val store: AfterUpdateStorePort,
    private val executor: Executor,
) {
    /** The entry this process opened, or null when this start was no update; touched on [executor] only. */
    private var openEntryAtMillis: Long? = null

    /** Process start: [probe] runs on the executor and the start is recorded if the package changed since last time. */
    fun onStart(
        atMillis: Long,
        intentText: String,
        isTaskRoot: Boolean,
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
            )
        val before = store.read()
        val (after, added) = AfterUpdateLog.onStart(before, platform.pkg, facts)
        if (after != before) store.write(after)
        openEntryAtMillis = atMillis.takeIf { added }
    }

    /** The first frame was drawn [millis] after the process started. */
    fun onFirstFrame(millis: Long) =
        change { state, startedAt -> AfterUpdateLog.withFirstFrame(state, startedAt, millis) }

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
