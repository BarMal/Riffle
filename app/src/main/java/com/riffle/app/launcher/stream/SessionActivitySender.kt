package com.riffle.app.launcher.stream

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Bundle

/**
 * Sends a media session's own `sessionActivity` (the screen the player app wants shown for that session). Since Android
 * 14 the app that sends a PendingIntent must opt in to the activity it starts being allowed to start from the
 * background, or the platform drops the start without an error (the send "succeeds" and nothing opens). A launcher
 * that is on screen is entitled to the opt-in, so it is passed on API 34 and above and left out below it.
 *
 * [dispatch] is the platform send, a seam so the options can be checked without a device.
 */
internal class SessionActivitySender(
    private val context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
    private val dispatch: (PendingIntent, Bundle?) -> Unit = { pending, options ->
        pending.send(context, 0, null, null, null, null, options)
    },
) {
    /** The options a send carries: the opt-in on API 34 and above, none before it. */
    fun options(): Bundle? = if (sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) allowBackgroundStart() else null

    /** False when the intent was cancelled (its app has dropped it) or the send was refused. */
    fun send(pending: PendingIntent): Boolean =
        try {
            dispatch(pending, options())
            true
        } catch (_: PendingIntent.CanceledException) {
            false
        } catch (_: SecurityException) {
            false
        }

    @SuppressLint("NewApi") // Only reached on API 34 and above: see [options].
    private fun allowBackgroundStart(): Bundle =
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
}
