package com.riffle.core.domain.launcher.dockpull

/**
 * A claimed Dock pull while the finger is down: [progress] runs 0..1 over [travelDp] along [direction] (the window's
 * extent along the pull axis), from [startProgress] (0 for a fresh pull, the settle's progress when the finger caught
 * one mid-flight). It tracks the finger 1:1; only the component along the pull moves it.
 */
data class DockPullDrag(
    val direction: DockPullDirection,
    val travelDp: Float,
    val startProgress: Float = 0f,
    val dragXDp: Float = 0f,
    val dragYDp: Float = 0f,
) {
    init {
        require(travelDp > 0f) { "A pull needs a positive travel distance: $travelDp" }
        require(startProgress in 0f..1f) { "Progress runs 0..1: $startProgress" }
    }

    /** Signed distance this gesture has moved along [direction]. */
    val gestureDistanceDp: Float get() = direction.along(dragXDp, dragYDp)

    val progress: Float get() = (startProgress + gestureDistanceDp / travelDp).coerceIn(0f, 1f)

    /** The finger moved by ([dxDp], [dyDp]) since the last event. */
    fun dragged(
        dxDp: Float,
        dyDp: Float,
    ): DockPullDrag = copy(dragXDp = dragXDp + dxDp, dragYDp = dragYDp + dyDp)

    /** How a release at ([velocityXDpPerSecond], [velocityYDpPerSecond]) ends the pull. */
    fun release(
        velocityXDpPerSecond: Float,
        velocityYDpPerSecond: Float,
        thresholds: DockPullThresholds = DockPullThresholds(),
    ): SettleOutcome =
        thresholds.outcomeFor(
            progress = progress,
            gestureDistanceDp = gestureDistanceDp,
            velocityDpPerSecond = direction.along(velocityXDpPerSecond, velocityYDpPerSecond),
        )
}

/**
 * How the Stream and Home are drawn while a pull is [progress] of the way (0..1) from one to the other. Offsets are
 * fractions of the window extent along the pull axis, positive in the pull direction (toward the interior).
 *
 * Opening: the Stream comes in from the Dock's edge ([streamOffsetFraction] -1 at 0, 0 at 1) and Home fades out
 * under it. Closing (the same pull from the Stream): the Stream leaves toward the interior (0 at 0, +1 at 1) and Home
 * fades back in. Under reduced motion nothing slides: the two crossfade.
 */
data class StreamPullFrame(
    val streamOffsetFraction: Float,
    val streamAlpha: Float,
    val homeAlpha: Float,
)

fun streamPullFrame(
    progress: Float,
    opening: Boolean,
    reducedMotion: Boolean,
): StreamPullFrame {
    val p = progress.coerceIn(0f, 1f)
    val revealed = if (opening) p else 1f - p
    return when {
        reducedMotion -> StreamPullFrame(streamOffsetFraction = 0f, streamAlpha = revealed, homeAlpha = 1f - revealed)
        opening -> StreamPullFrame(streamOffsetFraction = p - 1f, streamAlpha = 1f, homeAlpha = 1f - revealed)
        else -> StreamPullFrame(streamOffsetFraction = p, streamAlpha = 1f, homeAlpha = 1f - revealed)
    }
}
