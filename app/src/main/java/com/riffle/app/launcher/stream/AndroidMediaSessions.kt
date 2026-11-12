package com.riffle.app.launcher.stream

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.riffle.app.launcher.notifications.MediaCommand
import com.riffle.app.launcher.notifications.RiffleNotificationListenerService
import com.riffle.app.launcher.notifications.mediaCommandsForPlaybackActions
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionInfo
import com.riffle.core.domain.launcher.workspace.sources.MediaSessionReader

/** Drives a media session by package: the platform side of the Stream's transport actions. */
internal interface MediaTransport {
    /** [action] must be one of Play, Pause, Previous, Next, SeekTo or Open. */
    fun perform(
        packageName: String,
        action: StreamAction,
    ): StreamActionResult
}

/**
 * The active media sessions, read through `MediaSessionManager` with the notification listener's access (the user
 * grants notification access; this never asks for anything). Without access the platform throws, which reads as no
 * sessions. Everything here is transient: nothing is stored or logged, artwork stays in [MediaArtworkStore] memory.
 *
 * [observe] follows the session list and each session's playback state, metadata and queue; it only listens while
 * something observes. [activeSessions] blocks briefly: call it off the main thread.
 */
internal class AndroidMediaSessions(
    context: Context,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : MediaSessionReader, MediaTransport, SourceChangeSource {
    private val manager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, RiffleNotificationListenerService::class.java)

    private fun controllers(): List<MediaController> =
        runCatching { manager?.getActiveSessions(listener) }.getOrNull().orEmpty()

    override fun activeSessions(): List<MediaSessionInfo> {
        val artwork = HashMap<String, Bitmap>()
        val now = elapsedRealtime()
        val sessions =
            controllers().mapNotNull { controller ->
                runCatching {
                    val snapshot = controller.snapshot()
                    controller.metadata?.artwork()?.let { artwork[controller.packageName] = it }
                    snapshot.toMediaSessionInfo(now)
                }.getOrNull()
            }
        MediaArtworkStore.replaceAll(artwork)
        return sessions
    }

    override fun observe(onChanged: () -> Unit): () -> Unit {
        val sessionManager = manager ?: return {}
        val handler = Handler(Looper.getMainLooper())
        val registered = HashMap<MediaController, MediaController.Callback>()
        val callback =
            object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = onChanged()

                override fun onMetadataChanged(metadata: MediaMetadata?) = onChanged()

                override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) =
                    onChanged()
            }

        fun sync(current: List<MediaController>) {
            (registered.keys - current.toSet()).forEach { stale ->
                runCatching { stale.unregisterCallback(registered.getValue(stale)) }
                registered.remove(stale)
            }
            current.filter { it !in registered }.forEach { added ->
                runCatching { added.registerCallback(callback, handler) }
                registered[added] = callback
            }
        }
        val sessionsChanged =
            MediaSessionManager.OnActiveSessionsChangedListener { list ->
                sync(list.orEmpty())
                onChanged()
            }
        var active = true
        handler.post {
            if (active) {
                runCatching { sessionManager.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler) }
                sync(controllers())
            }
        }
        return {
            active = false
            handler.post {
                runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged) }
                registered.forEach { (controller, cb) -> runCatching { controller.unregisterCallback(cb) } }
                registered.clear()
            }
        }
    }

    override fun perform(
        packageName: String,
        action: StreamAction,
    ): StreamActionResult {
        val controller =
            controllers().firstOrNull { it.packageName == packageName } ?: return StreamActionResult.Unavailable
        return runCatching { controller.run(action) }.getOrDefault(StreamActionResult.Failed)
    }

    /** Runs [action] if the session's current actions allow it; otherwise it is not offered. */
    private fun MediaController.run(action: StreamAction): StreamActionResult {
        val command = commandFor(action)
        if (command == null) {
            return if (action == StreamAction.Open) StreamActionResult.Unavailable else StreamActionResult.NotOffered
        }
        command()
        return StreamActionResult.Performed
    }

    private fun MediaController.commandFor(action: StreamAction): (() -> Unit)? {
        val state = playbackState
        val allowed = state?.let { mediaCommandsForPlaybackActions(it.actions, it.state) }.orEmpty()
        val controls = transportControls
        return when (action) {
            StreamAction.Open -> sessionActivity?.let { { it.send() } }
            StreamAction.Play -> controls::play.takeIf { MediaCommand.PLAY in allowed }
            StreamAction.Pause -> controls::pause.takeIf { MediaCommand.PAUSE in allowed }
            StreamAction.Previous -> controls::skipToPrevious.takeIf { MediaCommand.PREVIOUS in allowed }
            StreamAction.Next -> controls::skipToNext.takeIf { MediaCommand.NEXT in allowed }
            is StreamAction.SeekTo ->
                if (state != null && state.actions and PlaybackState.ACTION_SEEK_TO != 0L) {
                    { controls.seekTo(action.positionMillis) }
                } else {
                    null
                }
            else -> null
        }
    }

    private fun MediaController.snapshot(): PlatformMediaSnapshot {
        val metadata = metadata
        val state = playbackState
        val queue = queue.orEmpty()
        val activeId = state?.activeQueueItemId ?: -1L
        return PlatformMediaSnapshot(
            packageName = packageName,
            title =
                metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist =
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            hasArtwork = metadata?.artwork() != null,
            playbackState = state?.state,
            actions = state?.actions ?: 0L,
            positionMillis = state?.position ?: -1L,
            positionUpdatedAtElapsed = state?.lastPositionUpdateTime ?: 0L,
            speed = state?.playbackSpeed ?: 1f,
            durationMillis = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            queue = queue.map { item -> item.description.title?.toString() to item.description.subtitle?.toString() },
            activeQueueIndex = queue.indexOfFirst { it.queueId == activeId },
        )
    }

    private fun MediaMetadata.artwork(): Bitmap? =
        getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
}
