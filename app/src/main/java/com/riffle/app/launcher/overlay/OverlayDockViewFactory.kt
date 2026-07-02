package com.riffle.app.launcher.overlay

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.setPadding
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.settings.OverlayDockEdge
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

internal class OverlayDockViewFactory(
    private val context: Context,
    private val appLauncher: AndroidAppLauncher,
) {
    fun collapsedHandleView(
        settings: OverlayDockSettings,
        onExpand: () -> Unit,
    ): View =
        TextView(context).apply {
            text = ""
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = edgeHandleBackground(settings)
            contentDescription = "Open Riffle overlay dock"
            layoutParams =
                FrameLayout.LayoutParams(
                    context.dp(EDGE_HANDLE_THICKNESS_DP),
                    context.dp(settings.handleHeightDp),
                )
            setOnClickListener { onExpand() }
        }

    fun expandedDockView(
        shortcuts: List<AppShortcutItem>,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
    ): View =
        FrameLayout(context).apply {
            background = roundedBackground(alphaPercent = settings.handleAlphaPercent)
            setPadding(context.dp(8))
            contentDescription = "Riffle overlay dock"

            addView(
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(
                        LinearLayout(context).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            shortcuts.forEach { shortcut -> addView(shortcutButton(shortcut, onLaunch)) }
                            if (shortcuts.isEmpty()) {
                                addView(emptyDockText())
                            }
                            addView(collapseButton(onCollapse))
                        },
                    )
                },
            )
        }

    fun overlayLayoutParams(settings: OverlayDockSettings): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or settings.edge.edgeGravity
            x = 0
            y = context.dp(settings.verticalOffsetDp)
        }

    private fun shortcutButton(
        shortcut: AppShortcutItem,
        onLaunch: () -> Unit,
    ): ImageButton =
        ImageButton(context).apply {
            val icon =
                runCatching {
                    context.packageManager.getActivityIcon(
                        ComponentName(
                            shortcut.appIdentity.packageName.value,
                            shortcut.appIdentity.activityName.value,
                        ),
                    )
                }.getOrNull()
            setImageDrawable(icon)
            background = transparentRoundedBackground()
            contentDescription = shortcut.label
            setPadding(context.dp(8))
            layoutParams =
                LinearLayout.LayoutParams(context.dp(52), context.dp(52))
                    .apply { marginEnd = context.dp(6) }
            setOnClickListener {
                appLauncher.launch(shortcut.appIdentity)
                onLaunch()
            }
        }

    private fun emptyDockText(): TextView =
        TextView(context).apply {
            text = "Dock empty"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(horizontal = context.dp(12), vertical = context.dp(8))
        }

    private fun collapseButton(onCollapse: () -> Unit): TextView =
        TextView(context).apply {
            text = "X"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(context.dp(12))
            contentDescription = "Close Riffle overlay dock"
            setOnClickListener { onCollapse() }
        }

    private fun roundedBackground(alphaPercent: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpFloat(28)
            setColor(Color.argb(alphaPercent.toColorAlpha(), 31, 36, 42))
        }

    private fun edgeHandleBackground(settings: OverlayDockSettings): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii =
                when (settings.edge) {
                    OverlayDockEdge.START ->
                        floatArrayOf(
                            0f,
                            0f,
                            context.dpFloat(14),
                            context.dpFloat(14),
                            context.dpFloat(14),
                            context.dpFloat(14),
                            0f,
                            0f,
                        )

                    OverlayDockEdge.END ->
                        floatArrayOf(
                            context.dpFloat(14),
                            context.dpFloat(14),
                            0f,
                            0f,
                            0f,
                            0f,
                            context.dpFloat(14),
                            context.dpFloat(14),
                        )
                }
            setColor(Color.argb(settings.handleAlphaPercent.toColorAlpha(), 31, 36, 42))
        }

    private fun transparentRoundedBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dpFloat(18)
            setColor(Color.argb(24, 255, 255, 255))
        }

    private fun View.setPadding(
        horizontal: Int,
        vertical: Int,
    ) {
        setPadding(horizontal, vertical, horizontal, vertical)
    }
}

private const val EDGE_HANDLE_THICKNESS_DP = 18

private val OverlayDockEdge.edgeGravity: Int
    get() =
        when (this) {
            OverlayDockEdge.START -> Gravity.START
            OverlayDockEdge.END -> Gravity.END
        }

private fun Int.toColorAlpha(): Int = (this.coerceIn(0, 100) * 255) / 100

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun Context.dpFloat(value: Int): Float = value * resources.displayMetrics.density
