package com.riffle.app.launcher

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.riffle.core.domain.launcher.dock.DockPress

/**
 * How a Dock press ended (ADR 0003, Amendment 3): [committed] says it passed the threshold, so the mode toggles;
 * [choosing] says it had turned into a selection (the lift then chooses, whatever the travel); [startedOnEntryKey] is
 * the dynamic entry the finger landed on, null elsewhere; [startedNearKey] is the selectable item nearest where it
 * landed (a pinned app's icon has no entry of its own, but something is nearest it); [travelDp] is how far it was
 * pressed.
 */
internal data class DockPressRelease(
    val committed: Boolean,
    val startedOnEntryKey: String?,
    val travelDp: Float,
    val choosing: Boolean = false,
    val startedNearKey: String? = null,
)

/**
 * The UI side of pressing the Dock down and releasing: holds the press while the finger is down and reports its end.
 *
 * Nothing here decides anything: whether a drag is a press and when a release commits are the domain's
 * ([DockPress]). What it gives whoever draws the press (the dip and the trampoline, `DockMotion.kt`) is [onBegan],
 * [onMoved] with the travel while it runs and [onReleased] when it ends, committed or not; [progress] (0 to 1, read it
 * in a draw block, not composition) is the same travel as a fraction of the threshold.
 *
 * A press held or slid becomes a selection ([beginChoosing]): from then on the finger's place is reported through
 * [onChoosingMoved] instead of the travel, and the release carries [DockPressRelease.choosing].
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

    /** The selectable item nearest where the running press landed, or null. */
    var startedNearKey: String? = null
        private set

    /** Called once when a press ends, whether it committed or not. */
    var onReleased: (DockPressRelease) -> Unit = {}

    /** Called when a press is claimed, before anything has moved: whoever draws it starts from here. */
    var onBegan: () -> Unit = {}

    /** Called with how far the Dock is pressed, in dp, at the claim and at every move after it. */
    var onMoved: (travelDp: Float) -> Unit = {}

    /** Whether the running press has turned into a selection. */
    var choosing: Boolean by mutableStateOf(false)
        private set

    /** Called once when the press turns into a selection, with the finger's place in the gesture's coordinates. */
    var onChoosingBegan: (position: Offset) -> Unit = {}

    /** Called with the finger's place at every move while choosing. */
    var onChoosingMoved: (position: Offset) -> Unit = {}

    fun begin(
        entryKey: String?,
        nearestSelectableKey: String? = null,
    ) {
        startedOnEntryKey = entryKey
        startedNearKey = nearestSelectableKey
        progress = 0f
        pressing = true
        onBegan()
    }

    fun update(travelDp: Float) {
        if (!pressing || choosing) return
        progress = DockPress.progress(travelDp)
        onMoved(travelDp)
    }

    /** The press turns into a selection, the finger at [position]. */
    fun beginChoosing(position: Offset) {
        if (!pressing || choosing) return
        choosing = true
        onChoosingBegan(position)
    }

    /** The finger moved to [position] while choosing. */
    fun choose(position: Offset) {
        if (pressing && choosing) onChoosingMoved(position)
    }

    /** The finger lifted at [travelDp] down. */
    fun release(travelDp: Float) {
        if (!pressing) return
        val release =
            DockPressRelease(DockPress.commits(travelDp), startedOnEntryKey, travelDp, choosing, startedNearKey)
        reset()
        onReleased(release)
    }

    /** The gesture was taken away: no commit. */
    fun cancel() {
        if (!pressing) return
        // A gesture taken away never chooses, even from a selection: nothing changes.
        val release =
            DockPressRelease(
                committed = false,
                startedOnEntryKey = startedOnEntryKey,
                travelDp = 0f,
                startedNearKey = startedNearKey,
            )
        reset()
        onReleased(release)
    }

    private fun reset() {
        pressing = false
        choosing = false
        progress = 0f
        startedOnEntryKey = null
        startedNearKey = null
    }
}
