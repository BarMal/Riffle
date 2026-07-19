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

        assertEquals(
            setOf(MediaCommand.PLAY, MediaCommand.NEXT),
            mediaCommandsForPlaybackActions(actions, PlaybackState.STATE_PAUSED),
        )
    }

    @Test
    fun `media actions are empty without playback capabilities`() {
        assertEquals(
            emptySet<MediaCommand>(),
            mediaCommandsForPlaybackActions(0L, PlaybackState.STATE_NONE),
        )
    }

    @Test
    fun `play pause capability pauses active playback`() {
        assertEquals(
            setOf(MediaCommand.PAUSE),
            mediaCommandsForPlaybackActions(
                PlaybackState.ACTION_PLAY_PAUSE,
                PlaybackState.STATE_PLAYING,
            ),
        )
    }

    @Test
    fun `play pause capability resumes inactive playback`() {
        assertEquals(
            setOf(MediaCommand.PLAY),
            mediaCommandsForPlaybackActions(
                PlaybackState.ACTION_PLAY_PAUSE,
                PlaybackState.STATE_PAUSED,
            ),
        )
    }
}
