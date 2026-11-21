package com.riffle.app.launcher.diagnostics

import android.os.Process
import android.os.SystemClock
import com.riffle.core.domain.launcher.diagnostics.StartMilestone

/**
 * The start-up milestones of this process (Settings > About > After-update starts): a label and the milliseconds since
 * the process started, kept in a small in-memory list and written once, off the main thread, after the first frame
 * (see [AfterUpdateProcess]). Marking is one clock read and a short lock; it starts nothing and touches no context, so
 * it is safe anywhere, including before an activity has a context. Each label is kept once, at its first time.
 */
internal object StartupTimeline {
    private const val MAX_MILESTONES = 40
    private val lock = Any()
    private val marks = ArrayList<StartMilestone>()

    /** Milliseconds since the process started, from the platform's process start time; 0 when it cannot be read. */
    private fun sinceProcessStart(): Long =
        runCatching { SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime() }.getOrDefault(0L)

    /** Records [label] now. Returns true when it was new (a label already recorded keeps its first time). */
    fun mark(label: String): Boolean = mark(label, sinceProcessStart())

    internal fun mark(
        label: String,
        millis: Long,
    ): Boolean =
        synchronized(lock) {
            if (marks.size >= MAX_MILESTONES || marks.any { it.label == label }) {
                false
            } else {
                marks.add(StartMilestone(label, millis.coerceAtLeast(0)))
            }
        }

    /** The time of [label], if it was reached. */
    fun millisOf(label: String): Long? = synchronized(lock) { marks.firstOrNull { it.label == label }?.millis }

    /** A copy of what was reached so far, in the order it was reached. */
    fun snapshot(): List<StartMilestone> = synchronized(lock) { marks.toList() }

    /** For tests. */
    internal fun clear() = synchronized(lock) { marks.clear() }
}

/** The labels of the milestones, as the log shows them. */
internal object StartMilestones {
    const val APPLICATION = "application"
    const val ON_CREATE_ENTERED = "MainActivity.onCreate entered"
    const val HANDED_OVER_TO_HOME = "handed over to a Home start"
    const val HOME_LAYOUT_OPENED = "home layout and pool read"
    const val DOCK_OVERRIDES_RESTORED = "dock overrides restored"
    const val SETTINGS_LOADED = "settings loaded"
    const val NOTIFICATIONS_LOADED = "notification snapshot loaded"
    const val HOME_LAYOUT_LOADED = "home layout set loaded"
    const val WORKSPACES_LOADED = "workspaces loaded"
    const val VIEW_MODEL_CREATED = "view model created"
    const val SET_CONTENT = "setContent called"
    const val ON_RESUME = "onResume"
    const val WINDOW_FOCUS = "window focus"
}
