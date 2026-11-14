package com.riffle.app.launcher.diagnostics

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import com.riffle.app.launcher.notifications.RemoteViewsReader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reads the structure of a notification's own layout for the "What apps publish" page. What it returns are inputs
 * (they hold a text view's text); `LayoutViewInput.toShape` keeps the length and drops the text, and only shapes are
 * shown or copied. Nothing is stored or logged.
 */
internal object LayoutOutlineReader {
    private const val OUTLINE_TIMEOUT_MILLIS = 2_000L

    /**
     * The views of [views] in drawing order, read by inflating the layout off-screen ([RemoteViewsReader], which
     * contains anything another app's layout throws). Views are inflated on the main thread: this waits for it, for a
     * bounded time. Null when the layout cannot be inflated or the main thread does not answer.
     */
    fun outlineOf(
        context: Context,
        views: RemoteViews,
    ): List<LayoutViewInput>? {
        val read = { runCatching { readOutline(context, views) }.getOrNull() }
        if (Looper.myLooper() == Looper.getMainLooper()) return read()
        val done = CountDownLatch(1)
        var result: List<LayoutViewInput>? = null
        Handler(Looper.getMainLooper()).post {
            result = read()
            done.countDown()
        }
        return if (done.await(OUTLINE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) result else null
    }

    fun readOutline(
        context: Context,
        views: RemoteViews,
    ): List<LayoutViewInput>? {
        val root = RemoteViewsReader.inflate(context, views) ?: return null
        val scaled = context.resources.displayMetrics.scaledDensity.takeIf { it > 0f } ?: 1f
        return RemoteViewsReader.walk(root).take(MAX_OUTLINE_VIEWS).map { walked ->
            val view = walked.view
            val picture = (view as? ImageView)?.drawable
            LayoutViewInput(
                depth = walked.depth,
                className = view.javaClass.name,
                resourceEntryName =
                    view.id.takeIf { it != View.NO_ID }
                        ?.let { id -> runCatching { view.resources.getResourceEntryName(id) }.getOrNull() },
                clickable = view.isClickable || view.hasOnClickListeners(),
                visibility = view.visibility,
                text = (view as? TextView)?.text,
                textSizeSp = (view as? TextView)?.let { it.textSize / scaled },
                imageWidthPx = picture?.intrinsicWidth?.takeIf { it > 0 },
                imageHeightPx = picture?.intrinsicHeight?.takeIf { it > 0 },
            )
        }
    }
}
