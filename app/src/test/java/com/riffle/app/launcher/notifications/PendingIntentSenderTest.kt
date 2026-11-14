package com.riffle.app.launcher.notifications

import android.app.PendingIntent
import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class PendingIntentSenderTest {
    @Test
    fun theOptInIsSentWhereThePlatformHasOne() {
        val options = Bundle()
        val seen = mutableListOf<Bundle?>()
        val result = PendingIntentSender.send(options) { seen += it }
        assertEquals(NotificationStageActionResult.Performed, result)
        assertEquals(1, seen.size)
        assertSame(options, seen.single())
    }

    @Test
    fun withoutAnOptInTheIntentIsSentPlain() {
        val seen = mutableListOf<Bundle?>()
        assertEquals(NotificationStageActionResult.Performed, PendingIntentSender.send(null) { seen += it })
        assertNull(seen.single())
    }

    @Test
    fun aCancelledIntentFailsOnceSoTheCallerCanLaunchTheApp() {
        var sends = 0
        val result =
            PendingIntentSender.send(Bundle()) {
                sends++
                throw PendingIntent.CanceledException()
            }
        assertEquals(NotificationStageActionResult.Failed, result)
        assertEquals(1, sends)
    }

    @Test
    fun aRefusedOptInIsRetriedPlain() {
        val seen = mutableListOf<Bundle?>()
        val result =
            PendingIntentSender.send(Bundle()) { options ->
                seen += options
                if (options != null) throw SecurityException("refused")
            }
        assertEquals(NotificationStageActionResult.Performed, result)
        assertEquals(2, seen.size)
        assertNull(seen[1])
    }

    @Test
    fun aPlainSendThatFailsIsAFailure() {
        val result = PendingIntentSender.send(null) { throw IllegalStateException("gone") }
        assertEquals(NotificationStageActionResult.Failed, result)
    }

    @Test
    fun theOptInIsBuiltOnlyFromApi34() {
        assertNull(PendingIntentSender.backgroundStartOptions(sdkInt = 33))
    }
}
