package com.riffle.app.launcher.stream

import android.media.session.PlaybackState
import com.riffle.core.domain.launcher.workspace.sources.MediaControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaSessionMappingTest {
    private fun snapshot(
        state: Int? = PlaybackState.STATE_PLAYING,
        actions: Long = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SEEK_TO,
        position: Long = 10_000,
        duration: Long = 100_000,
        queue: List<Pair<String?, String?>> = emptyList(),
        active: Int = -1,
    ) = PlatformMediaSnapshot(
        packageName = "player",
        title = "Song",
        artist = " ",
        hasArtwork = true,
        playbackState = state,
        actions = actions,
        positionMillis = position,
        positionUpdatedAtElapsed = 1_000,
        speed = 1f,
        durationMillis = duration,
        queue = queue,
        activeQueueIndex = active,
    )

    @Test
    fun aPlayingSessionOffersOnlyWhatItsActionsAllow_andPositionAdvancesWithTheClock() {
        val info = snapshot().toMediaSessionInfo(nowElapsed = 6_000)

        assertEquals(setOf(MediaControl.PAUSE, MediaControl.NEXT, MediaControl.SEEK), info.controls)
        assertEquals(true, info.isPlaying)
        assertEquals(15_000L, info.positionMillis)
        assertEquals(100_000L, info.durationMillis)
        assertNull("a blank artist is no artist", info.artist)
    }

    @Test
    fun aPausedSessionKeepsItsPositionAndSeekingNeedsADuration() {
        val paused =
            snapshot(
                state = PlaybackState.STATE_PAUSED,
                actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_SEEK_TO,
            )
                .toMediaSessionInfo(nowElapsed = 99_000)
        assertEquals(10_000L, paused.positionMillis)
        assertEquals(setOf(MediaControl.PLAY, MediaControl.SEEK), paused.controls)

        val live = snapshot(duration = 0).toMediaSessionInfo(nowElapsed = 1_000)
        assertNull(live.durationMillis)
        assertEquals(setOf(MediaControl.PAUSE, MediaControl.NEXT), live.controls)
    }

    @Test
    fun aSessionWithoutPlaybackStateHasNoControlsOrPosition() {
        val info = snapshot(state = null).toMediaSessionInfo(nowElapsed = 5_000)
        assertEquals(emptySet<MediaControl>(), info.controls)
        assertNull(info.isPlaying)
        assertNull(info.positionMillis)
    }

    @Test
    fun theQueueIsWhatComesAfterTheActiveEntry() {
        val queue = listOf("One" to null, "Two" to "Band", null to "x", "Four" to null)
        val info = snapshot(queue = queue, active = 0).toMediaSessionInfo(nowElapsed = 1_000)
        assertEquals(listOf("Two", "Four"), info.queue.map { it.title })
        assertEquals("Band", info.queue.first().artist)

        assertEquals(emptyList<Any>(), snapshot(queue = queue, active = -1).toMediaSessionInfo(1_000).queue)
    }
}
