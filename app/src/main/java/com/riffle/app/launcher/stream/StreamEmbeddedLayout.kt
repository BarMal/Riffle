package com.riffle.app.launcher.stream

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.notifications.NotificationCustomContentStore

/** Where a card finds the layout a notification draws itself with, to embed it: the listener's in-memory store. */
internal fun interface NotificationEmbeds {
    /** The layout of the notification [key]: the expanded one when [big]; null when it has none (or has gone). */
    fun viewsFor(
        key: String,
        big: Boolean,
    ): RemoteViews?
}

internal val LocalNotificationEmbeds =
    staticCompositionLocalOf<NotificationEmbeds> { NotificationEmbeds(NotificationCustomContentStore::viewsFor) }

/** The tag of an embedded layout (a test hook). */
internal const val STREAM_EMBEDDED_LAYOUT_TAG = "stream-embedded-layout"

/** How tall an embedded layout may be on a browse card and on an open card. */
internal val EmbeddedBrowseMaxHeight: Dp = 64.dp
internal val EmbeddedOpenMaxHeight: Dp = 256.dp

/**
 * The app's own layout inside a card, for a notification whose layout has no text to draw in Riffle's style (the last
 * of the three tiers: standard fields, extracted lines, this). It is as wide as the card's content and at most
 * [maxHeight] tall, clipped to the card's inner corner, and described to assistive technology by the app's name (what
 * the layout shows is the app's own). The layout is another app's: applying it is in a try, and one that fails leaves
 * nothing. [revision] changes when the app posts the notification again: the layout is then applied again, in place
 * where the platform allows it. On a browse card it takes no touch ([interactive] false), so the card's tap, the
 * stack's drag and the pager are as on any other card; on the open card its own buttons work.
 */
@Composable
internal fun EmbeddedNotificationLayout(
    notificationKey: String,
    appLabel: String?,
    big: Boolean,
    revision: Long,
    maxHeight: Dp,
    interactive: Boolean,
    modifier: Modifier = Modifier,
) {
    val embeds = LocalNotificationEmbeds.current
    val description = stringResource(R.string.stream_notification_layout, appLabel.orEmpty()).trim()
    AndroidView(
        factory = { context -> EmbeddedLayoutHost(context) },
        update = { host ->
            host.interactive = interactive
            host.show(embeds.viewsFor(notificationKey, big), revision)
        },
        onReset = { host -> host.show(null, -1L) },
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RiffleShapes.small)
                .testTag(STREAM_EMBEDDED_LAYOUT_TAG)
                .semantics { contentDescription = description },
    )
}

/** Holds the applied layout. Not attached to anything of the app's: only its `RemoteViews` are applied into it. */
internal class EmbeddedLayoutHost(context: Context) : FrameLayout(context) {
    var interactive: Boolean = false
    private var shownRevision: Long = -1L
    private var shownLayoutId: Int = 0

    /** How many times a layout was inflated afresh and applied in place (test hooks). */
    var applied: Int = 0
        private set
    var reapplied: Int = 0
        private set

    fun show(
        views: RemoteViews?,
        revision: Long,
    ) {
        if (views == null) {
            removeAllViews()
            shownRevision = -1L
            shownLayoutId = 0
            return
        }
        if (revision == shownRevision && childCount > 0) return
        val child: View? = getChildAt(0)
        val inPlace =
            child != null && shownLayoutId == views.layoutId &&
                runUntrusted {
                    views.reapply(context, child)
                    reapplied += 1
                }
        if (!inPlace) {
            removeAllViews()
            runUntrusted {
                addView(views.apply(context, this))
                applied += 1
            }
        }
        shownRevision = revision
        shownLayoutId = if (childCount > 0) views.layoutId else 0
    }

    /** True when [block] ran; another app's layout may throw anything, and never out of here. */
    private fun runUntrusted(block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") failure: Exception,
        ) {
            false
        } catch (
            @Suppress("SwallowedException") tooDeep: StackOverflowError,
        ) {
            false
        }

    // On a browse card the layout is a picture: no touch reaches the app's views, and none is taken from the card.
    override fun onInterceptTouchEvent(event: MotionEvent?): Boolean =
        !interactive || super.onInterceptTouchEvent(event)

    @Suppress("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent?): Boolean = interactive && super.onTouchEvent(event)
}
