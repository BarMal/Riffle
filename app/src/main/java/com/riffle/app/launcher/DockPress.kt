package com.riffle.app.launcher

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.riffle.core.domain.launcher.dock.DockPress

/**
 * How a Dock press ended (ADR 0003, Amendment 3): [committed] says it passed the threshold, so the mode toggles;
 * [startedOnEntryKey] is the dynamic entry the finger landed on, null elsewhere; [travelDp] is how far it was pressed.
 */
internal data class DockPressRelease(
    val committed: Boolean,
    val startedOnEntryKey: String?,
    val travelDp: Float,
)

/**
 * The UI side of pressing the Dock down and releasing: holds the press while the finger is down and reports its end.
 *
 * Nothing here decides anything: whether a drag is a press and when a release commits are the domain's
 * ([DockPress]). What it gives a later slice to draw from is [progress] (0 to 1, read it in a draw block, not
 * composition) while the press runs, and [onReleased] when it ends, committed or not.
 */
@Stable
internal class DockPressState {
    /** Whether a press is under way: the finger is down and has been claimed as a press. */
    var pressing: Boolean by mutableStateOf(false)
        private set

    /** How far through the press the Dock is, 0 to 1; 1 means a release now commits. Zero when no press runs. */
    var progress: Float by mutableFloatStateOf(0f)
        private set

    /** The dynamic entry the running press started on, or null. */
    var startedOnEntryKey: String? = null
        private set

    /** Called once when a press ends, whether it committed or not. */
    var onReleased: (DockPressRelease) -> Unit = {}

    fun begin(entryKey: String?) {
        startedOnEntryKey = entryKey
        progress = 0f
        pressing = true
    }

    fun update(travelDp: Float) {
        if (pressing) progress = DockPress.progress(travelDp)
    }

    /** The finger lifted at [travelDp] down. */
    fun release(travelDp: Float) {
        if (!pressing) return
        val release = DockPressRelease(DockPress.commits(travelDp), startedOnEntryKey, travelDp)
        reset()
        onReleased(release)
    }

    /** The gesture was taken away: no commit. */
    fun cancel() {
        if (!pressing) return
        val release = DockPressRelease(committed = false, startedOnEntryKey = startedOnEntryKey, travelDp = 0f)
        reset()
        onReleased(release)
    }

    private fun reset() {
        pressing = false
        progress = 0f
        startedOnEntryKey = null
    }
}
