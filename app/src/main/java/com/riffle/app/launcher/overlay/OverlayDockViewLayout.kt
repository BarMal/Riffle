package com.riffle.app.launcher.overlay

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import com.riffle.core.domain.launcher.settings.overlayDockVerticalOffsetFromDrag
import kotlin.math.abs

internal const val COLLAPSED_HANDLE_TOUCH_TARGET_WIDTH_DP = 48
internal const val EXPANDED_ITEM_SPACING_DP = 6

private const val EXPANDED_LABEL_HEIGHT_DP = 24
private const val EXPANDED_CLOSE_BUTTON_HEIGHT_DP = 44
private const val MAX_TALL_EXPANDED_DOCK_SCREEN_FRACTION = 0.7f

internal val OverlayDockEdge.edgeGravity: Int
    get() =
        when (this) {
            OverlayDockEdge.START -> Gravity.START
            OverlayDockEdge.END -> Gravity.END
        }

internal fun OverlayDockSettings.collapsedHandleTouchTargetWidthDp(): Int =
    handleThicknessDp.coerceAtLeast(COLLAPSED_HANDLE_TOUCH_TARGET_WIDTH_DP)

internal val OverlayDockExpandedOrientation.linearOrientation: Int
    get() =
        when (this) {
            OverlayDockExpandedOrientation.WIDE -> LinearLayout.HORIZONTAL
            OverlayDockExpandedOrientation.TALL -> LinearLayout.VERTICAL
        }

internal val OverlayDockExpandedOrientation.itemGravity: Int
    get() =
        when (this) {
            OverlayDockExpandedOrientation.WIDE -> Gravity.CENTER_VERTICAL
            OverlayDockExpandedOrientation.TALL -> Gravity.CENTER_HORIZONTAL
        }

internal fun LinearLayout.LayoutParams.withItemMargin(
    orientation: OverlayDockExpandedOrientation,
    spacingPx: Int,
): LinearLayout.LayoutParams =
    apply {
        when (orientation) {
            OverlayDockExpandedOrientation.WIDE -> marginEnd = spacingPx
            OverlayDockExpandedOrientation.TALL -> bottomMargin = spacingPx
        }
    }

internal fun Context.tallExpandedDockHeightDp(
    shortcuts: List<AppShortcutItem>,
    settings: OverlayDockSettings,
): Int =
    tallExpandedDockHeightDp(
        shortcutCount = shortcuts.size,
        settings = settings,
        availableHeightDp = resources.configuration.screenHeightDp,
    )

internal fun tallExpandedDockHeightDp(
    shortcutCount: Int,
    settings: OverlayDockSettings,
    availableHeightDp: Int,
): Int {
    val itemHeightDp =
        settings.expandedIconSizeDp.coerceAtLeast(0) +
            if (settings.showLabels) {
                EXPANDED_LABEL_HEIGHT_DP
            } else {
                0
            } +
            EXPANDED_ITEM_SPACING_DP
    val desiredHeightDp =
        (
            (shortcutCount.coerceAtLeast(1).toLong() * itemHeightDp.toLong()) +
                EXPANDED_CLOSE_BUTTON_HEIGHT_DP.toLong()
        )
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
    val maxHeightDp =
        (availableHeightDp.coerceAtLeast(0) * MAX_TALL_EXPANDED_DOCK_SCREEN_FRACTION)
            .toInt()
            .coerceAtLeast(0)

    return desiredHeightDp.coerceAtMost(maxHeightDp)
}

internal fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

internal fun View.setPadding(
    horizontal: Int,
    vertical: Int,
) {
    setPadding(horizontal, vertical, horizontal, vertical)
}

internal fun View.setDragPositionListener(
    settings: OverlayDockSettings,
    onVerticalOffsetChange: (Int) -> Unit,
    onVerticalOffsetCommit: (Int) -> Unit,
) {
    val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    var startRawY = 0f
    var latestOffsetDp = settings.verticalOffsetDp
    var dragging = false

    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawY = event.rawY
                latestOffsetDp = settings.verticalOffsetDp
                dragging = false
                true
            }

            MotionEvent.ACTION_MOVE -> {
                val dragDeltaPx = event.rawY - startRawY
                if (abs(dragDeltaPx) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    latestOffsetDp =
                        overlayDockVerticalOffsetFromDrag(
                            startOffsetDp = settings.verticalOffsetDp,
                            dragDeltaPx = dragDeltaPx,
                            density = context.resources.displayMetrics.density,
                        )
                    onVerticalOffsetChange(latestOffsetDp)
                }
                true
            }

            MotionEvent.ACTION_UP -> {
                if (dragging) {
                    onVerticalOffsetCommit(latestOffsetDp)
                } else {
                    view.performClick()
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }
}
