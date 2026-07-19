package com.riffle.app.launcher.notifications

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidNotificationStageActionGatewayTest {
    @Test
    fun `media actions match playback state capabilities`() {
        val actions =
            PlaybackState.ACTION_PLAY or
                PlaybackState.ACTION_SKIP_TO_NEXT
        val playbackState = playbackState(PlaybackState.STATE_PAUSED, actions)

        assertEquals(
            setOf(MediaCommand.PLAY, MediaCommand.NEXT),
            mediaCommandsForPlaybackState(playbackState),
        )
    }

    @Test
    fun `media actions are empty without playback capabilities`() {
        assertEquals(
            emptySet<MediaCommand>(),
            mediaCommandsForPlaybackState(playbackState(PlaybackState.STATE_NONE, 0L)),
        )
    }

    @Test
    fun `play pause capability pauses active playback`() {
        assertEquals(
            setOf(MediaCommand.PAUSE),
            mediaCommandsForPlaybackState(
                playbackState(PlaybackState.STATE_PLAYING, PlaybackState.ACTION_PLAY_PAUSE),
            ),
        )
    }

    @Test
    fun `play pause capability resumes inactive playback`() {
        assertEquals(
            setOf(MediaCommand.PLAY),
            mediaCommandsForPlaybackState(
                playbackState(PlaybackState.STATE_PAUSED, PlaybackState.ACTION_PLAY_PAUSE),
            ),
        )
    }

    private fun playbackState(
        state: Int,
        actions: Long,
    ): PlaybackState =
        PlaybackState.Builder()
            .setState(state, 0L, 1f)
            .setActions(actions)
            .build()
}
