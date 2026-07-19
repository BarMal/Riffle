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
            mediaCommandsForPlaybackActions(actions),
        )
    }

    @Test
    fun `media actions are empty without playback capabilities`() {
        assertEquals(emptySet<MediaCommand>(), mediaCommandsForPlaybackActions(0L))
    }
}
