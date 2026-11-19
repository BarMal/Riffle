package com.riffle.app.launcher.stream

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.screenshots.ScreenshotDevices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What "Open player" sends the session's PendingIntent with. Before this, it was sent with no options, which on
 * Android 14 and above is dropped without an error: the send succeeded, nothing opened, and no fallback ran.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ScreenshotDevices.SDK])
class SessionActivitySenderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val pending: PendingIntent =
        PendingIntent.getActivity(context, 0, Intent("com.example.music.OPEN"), PendingIntent.FLAG_IMMUTABLE)

    private fun sender(
        sdk: Int,
        sent: MutableList<Bundle?>,
        failWith: Exception? = null,
    ) = SessionActivitySender(context, sdk) { _, options ->
        sent += options
        failWith?.let { throw it }
    }

    @Test
    fun onAndroid14AndAboveTheSendOptsInToStartingTheActivity() {
        val sent = mutableListOf<Bundle?>()
        assertTrue(sender(34, sent).send(pending))
        val options = requireNotNull(sent.single())
        // The platform keeps the opt-in under a key of its own (it varies by version): compare with the same call.
        val expected =
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                .toBundle()
        val optIn = expected.keySet().filter { it.contains("pendingIntent", ignoreCase = true) }
        assertTrue("the platform carries the opt-in under a key of its own", optIn.isNotEmpty())
        optIn.forEach { key -> assertEquals(expected.get(key), options.get(key)) }
    }

    @Test
    fun beforeAndroid14NoOptionsAreSent() {
        val sent = mutableListOf<Bundle?>()
        assertTrue(sender(33, sent).send(pending))
        assertNull(sent.single())
    }

    @Test
    fun aCancelledIntentOrARefusedSendIsAFailureTheCallerFallsBackFrom() {
        assertFalse(sender(34, mutableListOf(), PendingIntent.CanceledException()).send(pending))
        assertFalse(sender(34, mutableListOf(), SecurityException("no")).send(pending))
    }
}
