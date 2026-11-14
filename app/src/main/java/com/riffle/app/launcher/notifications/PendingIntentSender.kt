package com.riffle.app.launcher.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi

/**
 * How a notification's pending intents (its content intent and its action buttons) are sent by the launcher.
 *
 * From Android 14 a `PendingIntent` that starts an activity is a background activity start unless the sender opts in:
 * the system then does not count the sender's own standing (a launcher the user is looking at) and a notification
 * listener has none, so the app's activity was silently not shown. The sender opts in with
 * `ActivityOptions.setPendingIntentBackgroundActivityStartMode(MODE_BACKGROUND_ACTIVITY_START_ALLOWED)` (API 34). Below
 * 34 there is nothing to opt into and the plain send is used.
 *
 * The decision (which options, what a cancelled intent or a refused opt-in means) is [send], pure over a dispatch
 * function so it is tested on the JVM; [dispatcher] is the platform call.
 */
internal object PendingIntentSender {
    /** The sender's opt-in, or null below API 34 (or when the platform cannot build it). */
    fun backgroundStartOptions(sdkInt: Int = Build.VERSION.SDK_INT): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) {
            runCatching { optIn() }.getOrNull()
        } else {
            null
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun optIn(): Bundle =
        ActivityOptions
            .makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()

    /**
     * Sends with [options] when there are any. A cancelled intent (its app dropped it) is a failure and is not retried;
     * any other refusal of the options (an OEM quirk) is retried once plain, which is what the launcher did before.
     */
    fun send(
        options: Bundle?,
        dispatch: (Bundle?) -> Unit,
    ): NotificationStageActionResult {
        val first = options?.let { attempt(it, dispatch) }
        // Performed or cancelled is final; only a refused opt-in (null) is tried again, plain.
        return first ?: attempt(null, dispatch) ?: NotificationStageActionResult.Failed
    }

    /** Performed, or Failed for a cancelled intent; null when the platform refused the send for another reason. */
    private fun attempt(
        options: Bundle?,
        dispatch: (Bundle?) -> Unit,
    ): NotificationStageActionResult? =
        try {
            dispatch(options)
            NotificationStageActionResult.Performed
        } catch (_: PendingIntent.CanceledException) {
            NotificationStageActionResult.Failed
        } catch (_: RuntimeException) {
            null
        }

    /** The platform send of [pendingIntent], with [fillIn] merged into it when the action has a reply to carry. */
    fun dispatcher(
        context: Context,
        pendingIntent: PendingIntent,
        fillIn: Intent? = null,
    ): (Bundle?) -> Unit = { options -> pendingIntent.send(context, 0, fillIn, null, null, null, options) }
}
