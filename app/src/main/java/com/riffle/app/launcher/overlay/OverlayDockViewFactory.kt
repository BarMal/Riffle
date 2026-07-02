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

internal class OverlayDockViewFactory(
    private val context: Context,
    private val appLauncher: AndroidAppLauncher,
) {
    fun collapsedHandleView(
        shortcuts: List<AppShortcutItem>,
        onExpand: () -> Unit,
    ): View =
        TextView(context).apply {
            text = shortcuts.size.coerceAtMost(MAX_COLLAPSED_COUNT).takeIf { count -> count > 0 }?.toString() ?: "+"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12.dp)
            background = pillBackground(alpha = COLLAPSED_ALPHA)
            contentDescription = "Open Riffle overlay dock"
            setOnClickListener { onExpand() }
        }

    fun expandedDockView(
        shortcuts: List<AppShortcutItem>,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
    ): View =
        FrameLayout(context).apply {
            background = pillBackground(alpha = EXPANDED_ALPHA)
            setPadding(8.dp)
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

    fun overlayLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = 18.dp
            y = 96.dp
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
            setPadding(8.dp)
            layoutParams = LinearLayout.LayoutParams(52.dp, 52.dp).apply { marginEnd = 6.dp }
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
            setPadding(horizontal = 12.dp, vertical = 8.dp)
        }

    private fun collapseButton(onCollapse: () -> Unit): TextView =
        TextView(context).apply {
            text = "X"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(12.dp)
            contentDescription = "Close Riffle overlay dock"
            setOnClickListener { onCollapse() }
        }

    private fun pillBackground(alpha: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 28.dp.toFloat()
            setColor(Color.argb(alpha, 31, 36, 42))
        }

    private fun transparentRoundedBackground(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 18.dp.toFloat()
            setColor(Color.argb(24, 255, 255, 255))
        }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    private fun View.setPadding(
        horizontal: Int,
        vertical: Int,
    ) {
        setPadding(horizontal, vertical, horizontal, vertical)
    }
}

private const val COLLAPSED_ALPHA = 218
private const val EXPANDED_ALPHA = 232
private const val MAX_COLLAPSED_COUNT = 9
