package com.riffle.app.launcher.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.core.graphics.drawable.toBitmap
import com.riffle.core.domain.launcher.stream.CollectedLayoutText

/** One view of an inflated layout, as the walk found it: what the reader and the diagnostics outline are built from. */
internal class WalkedView(
    val depth: Int,
    val view: View,
    /** The view and every parent of it are visible. */
    val shown: Boolean,
)

/** What was read out of one layout: its texts in drawing order and its largest picture, sized down. */
internal class ReadLayout(
    val texts: List<CollectedLayoutText>,
    val picture: Bitmap?,
    /** How many of the layout's views take a tap. */
    val clickableCount: Int,
)

/**
 * Inflates another app's `RemoteViews` off-screen and reads it. The layout is untrusted: everything is in a try, the
 * views are laid out in a bounded box and never attached to a window, the number of views walked is capped, and a
 * picture is sized down as it is read. Main thread only (views are inflated).
 */
internal object RemoteViewsReader {
    /** The box the layout is laid out in, so text sizes and visibility are what the shade would show. */
    private const val LAYOUT_WIDTH_DP = 360
    private const val LAYOUT_HEIGHT_DP = 256

    /** The most views of one layout that are looked at. */
    const val MAX_VIEWS = 200

    /** A picture smaller than this on either side is an icon or a divider, not content. */
    const val MIN_PICTURE_EDGE_DP = 32

    /** The edge a picture is sized down to: the same bound as a contact photo in the image store. */
    const val MAX_PICTURE_EDGE_PX = NotificationImageStore.MAX_PERSON_EDGE_PX

    /** The inflated root of [views], measured and laid out off-screen, or null when the layout cannot be inflated. */
    fun inflate(
        context: Context,
        views: RemoteViews,
    ): View? =
        try {
            val parent = FrameLayout(context)
            val root = views.apply(context.applicationContext, parent)
            val density = context.resources.displayMetrics.density
            val width = (LAYOUT_WIDTH_DP * density).toInt()
            val height = (LAYOUT_HEIGHT_DP * density).toInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST),
            )
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            root
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception,
        ) {
            // Another app's layout: a missing resource, a class the platform refuses, a bad action. Never a crash.
            null
        } catch (
            @Suppress("SwallowedException") tooDeep: StackOverflowError,
        ) {
            null
        }

    /** The views under [root] in drawing order (a parent before its children), at most [MAX_VIEWS]. */
    fun walk(root: View): List<WalkedView> {
        val out = ArrayList<WalkedView>()
        val pending = ArrayDeque<WalkedView>()
        pending.addLast(WalkedView(0, root, root.visibility == View.VISIBLE))
        while (pending.isNotEmpty() && out.size < MAX_VIEWS) {
            val next = pending.removeAt(pending.size - 1)
            out += next
            val group = next.view as? ViewGroup ?: continue
            // Pushed last to first, so the first child is walked first.
            for (index in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(index) ?: continue
                pending.addLast(WalkedView(next.depth + 1, child, next.shown && child.visibility == View.VISIBLE))
            }
        }
        return out
    }

    /** Reads [views]: null when the layout cannot be inflated. */
    fun read(
        context: Context,
        views: RemoteViews,
    ): ReadLayout? {
        val root = inflate(context, views) ?: return null
        return try {
            read(context, walk(root))
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception,
        ) {
            null
        }
    }

    private fun read(
        context: Context,
        walked: List<WalkedView>,
    ): ReadLayout {
        val metrics = context.resources.displayMetrics
        val scaled = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, metrics).takeIf { it > 0f } ?: 1f
        val shown = walked.filter { it.shown }
        val texts =
            shown.mapNotNull { walkedView ->
                val view = walkedView.view as? TextView ?: return@mapNotNull null
                val text = view.text?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CollectedLayoutText(
                    text = text,
                    sizeSp = view.textSize / scaled,
                    bold = view.isBold(),
                    clickable = view.takesTap(),
                )
            }
        val minEdge = MIN_PICTURE_EDGE_DP * metrics.density
        val picture =
            shown
                .mapNotNull { (it.view as? ImageView)?.drawable }
                .filter { it.intrinsicWidth >= minEdge && it.intrinsicHeight >= minEdge }
                .maxByOrNull { it.intrinsicWidth.toLong() * it.intrinsicHeight }
                ?.let { drawable ->
                    val longest = maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight)
                    val scale = minOf(1f, MAX_PICTURE_EDGE_PX.toFloat() / longest)
                    drawable.toBitmap(
                        (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1),
                        (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1),
                    )
                }
        return ReadLayout(texts, picture, shown.count { it.view.takesTap() })
    }

    private fun TextView.isBold(): Boolean {
        val face: Typeface? = typeface
        return face?.isBold == true ||
            paint.isFakeBoldText ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && (face?.weight ?: 0) >= BOLD_WEIGHT)
    }

    private fun View.takesTap(): Boolean = isClickable || hasOnClickListeners()

    private const val BOLD_WEIGHT = 600
}
