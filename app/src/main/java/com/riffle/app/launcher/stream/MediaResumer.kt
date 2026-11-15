package com.riffle.app.launcher.stream

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.os.Handler
import android.os.Looper
import com.riffle.core.domain.launcher.stream.StreamActionResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Resumes a player that has no session (a recent player on Now). The platform route System UI's own media resumption
 * uses: connect to the app's media browser service and press Play on the session it hands back.
 */
internal interface MediaResumer {
    /** Whether [packageName] declares a media browser service, so [resume] can work. Cached; cheap; any thread. */
    fun canResume(packageName: String): Boolean

    /**
     * Asks the app to play again. Blocks until the app answered or [RESUME_TIMEOUT_MILLIS] passed: call it off the
     * main thread. [StreamActionResult.Performed] means Play was sent; anything else means the caller opens the app.
     */
    fun resume(packageName: String): StreamActionResult

    companion object {
        /** How long the app has to answer before Now opens it instead. */
        const val RESUME_TIMEOUT_MILLIS = 3_000L

        /** Resumes nothing: every app is opened instead. */
        val NONE: MediaResumer =
            object : MediaResumer {
                override fun canResume(packageName: String): Boolean = false

                override fun resume(packageName: String): StreamActionResult = StreamActionResult.Unavailable
            }
    }
}

/**
 * [MediaResumer] over [MediaBrowser]: the browser connects to the app's service (the service decides whether to let a
 * launcher in; one that refuses reads as a failure and the app is opened), [MediaController.TransportControls.play] is
 * sent on the session token it returns, then the connection is dropped. The browser is created and connected on the
 * main looper (its callbacks run there) and this call only waits, so the main thread is never blocked.
 */
internal class AndroidMediaResumer(
    private val context: Context,
    private val timeoutMillis: Long = MediaResumer.RESUME_TIMEOUT_MILLIS,
) : MediaResumer {
    private val services = ConcurrentHashMap<String, ComponentName>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val main = Handler(Looper.getMainLooper())

    override fun canResume(packageName: String): Boolean = serviceOf(packageName) != null

    override fun resume(packageName: String): StreamActionResult {
        val service = serviceOf(packageName) ?: return StreamActionResult.Unavailable
        val done = CountDownLatch(1)
        val outcome = AtomicReference<StreamActionResult>(StreamActionResult.Failed)
        val browser = AtomicReference<MediaBrowser?>(null)

        fun finish(result: StreamActionResult) {
            outcome.set(result)
            runCatching { browser.get()?.disconnect() }
            done.countDown()
        }
        main.post {
            val callback =
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        val token = browser.get()?.sessionToken
                        val sent =
                            token != null &&
                                runCatching { MediaController(context, token).transportControls.play() }.isSuccess
                        finish(if (sent) StreamActionResult.Performed else StreamActionResult.Failed)
                    }

                    override fun onConnectionFailed() = finish(StreamActionResult.Failed)

                    override fun onConnectionSuspended() = finish(StreamActionResult.Failed)
                }
            val created = runCatching { MediaBrowser(context, service, callback, null) }.getOrNull()
            browser.set(created)
            if (created == null || runCatching { created.connect() }.isFailure) finish(StreamActionResult.Failed)
        }
        val answered = runCatching { done.await(timeoutMillis, TimeUnit.MILLISECONDS) }.getOrDefault(false)
        // No answer in time: drop the connection, so a late answer plays nothing.
        if (!answered) main.post { runCatching { browser.get()?.disconnect() } }
        return if (answered) outcome.get() else StreamActionResult.Failed
    }

    private fun serviceOf(packageName: String): ComponentName? {
        val known = services[packageName]
        if (known != null || packageName in missing) return known
        val found = queryService(packageName)
        if (found == null) missing += packageName else services[packageName] = found
        return found
    }

    private fun queryService(packageName: String): ComponentName? =
        runCatching {
            context.packageManager
                .queryIntentServices(Intent(SERVICE_ACTION).setPackage(packageName), PackageManager.GET_META_DATA)
                .firstOrNull()
                ?.serviceInfo
                ?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()

    private companion object {
        const val SERVICE_ACTION = "android.media.browse.MediaBrowserService"
    }
}
