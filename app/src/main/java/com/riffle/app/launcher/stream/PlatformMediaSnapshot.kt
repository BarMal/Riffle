package com.riffle.app.launcher.stream

import android.media.session.PlaybackState
import com.riffle.app.launcher.notifications.MediaCommand
import com.riffle.app.launcher.notifications.mediaCommandsForPlaybackActions
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import com.riffle.core.domain.launcher.workspace.sources.MediaItemExt
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionQueueEntry

/**
 * What the platform said about one media session, as plain values so the mapping is testable without a device.
 * Transient: never stored, logged or put in diagnostics.
 */
internal data class PlatformMediaSnapshot(
    val packageName: String,
    val title: String?,
    val artist: String?,
    val hasArtwork: Boolean,
    /** A [PlaybackState] state constant, or null when the session has no playback state. */
    val playbackState: Int?,
    val actions: Long,
    val positionMillis: Long,
    /** `SystemClock.elapsedRealtime()` when [positionMillis] was last updated by the player. */
    val positionUpdatedAtElapsed: Long,
    val speed: Float,
    val durationMillis: Long,
    /** The whole queue in order, as (title, subtitle). */
    val queue: List<Pair<String?, String?>>,
    /** Index of the queue entry that is playing, or -1 when unknown. */
    val activeQueueIndex: Int,
)

internal fun PlatformMediaSnapshot.toMediaSessionInfo(nowElapsed: Long): MediaSessionInfo {
    val state = playbackState
    val playing = state?.let { it == PlaybackState.STATE_PLAYING }
    val duration = durationMillis.takeIf { it > 0L }
    val controls = if (state == null) emptySet() else controlsFor(state, duration)
    val position =
        if (state == null || positionMillis < 0L) {
            null
        } else {
            val advanced =
                if (playing == true) {
                    ((nowElapsed - positionUpdatedAtElapsed).coerceAtLeast(0L) * speed).toLong()
                } else {
                    0L
                }
            (positionMillis + advanced).let { if (duration != null) it.coerceAtMost(duration) else it }
        }
    val upcoming = if (activeQueueIndex >= 0) queue.drop(activeQueueIndex + 1) else emptyList()
    return MediaSessionInfo(
        packageName = packageName,
        title = title?.takeIf(String::isNotBlank),
        artist = artist?.takeIf(String::isNotBlank),
        hasArtwork = hasArtwork,
        isPlaying = playing,
        positionMillis = position,
        durationMillis = duration,
        controls = controls,
        queue =
            upcoming.filter { (title, _) -> !title.isNullOrBlank() }
                .take(MediaItemExt.MAX_QUEUE)
                .map { (title, subtitle) -> MediaSessionQueueEntry(title, subtitle?.takeIf(String::isNotBlank)) },
    )
}

private fun PlatformMediaSnapshot.controlsFor(
    state: Int,
    duration: Long?,
): Set<MediaControl> =
    buildSet {
        mediaCommandsForPlaybackActions(actions, state).forEach { command ->
            add(
                when (command) {
                    MediaCommand.PLAY -> MediaControl.PLAY
                    MediaCommand.PAUSE -> MediaControl.PAUSE
                    MediaCommand.PREVIOUS -> MediaControl.PREVIOUS
                    MediaCommand.NEXT -> MediaControl.NEXT
                },
            )
        }
        if (actions and PlaybackState.ACTION_SEEK_TO != 0L && duration != null) add(MediaControl.SEEK)
    }
