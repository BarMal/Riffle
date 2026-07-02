package com.riffle.app.launcher.overlay

import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.setPadding
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings

internal class OverlayDockViewFactory(
    private val context: Context,
    private val appLauncher: AndroidAppLauncher,
) {
    fun collapsedHandleView(
        settings: OverlayDockSettings,
        onExpand: () -> Unit,
        onVerticalOffsetChange: (Int) -> Unit,
        onVerticalOffsetCommit: (Int) -> Unit,
    ): View =
        FrameLayout(context).apply {
            background = context.edgeHandleBackground(settings)
            contentDescription = "Open Riffle overlay dock"
            addView(
                View(context).apply {
                    background = context.handleGripBackground(settings.handleAlphaPercent)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            context.dp(GRIP_WIDTH_DP),
                            context.dp((settings.handleHeightDp / 2).coerceAtLeast(GRIP_MIN_HEIGHT_DP)),
                            Gravity.CENTER,
                        )
                },
            )
            setOnClickListener { onExpand() }
            setDragPositionListener(
                settings = settings,
                onVerticalOffsetChange = onVerticalOffsetChange,
                onVerticalOffsetCommit = onVerticalOffsetCommit,
            )
        }

    fun expandedDockView(
        shortcuts: List<AppShortcutItem>,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
    ): View =
        FrameLayout(context).apply {
            background = context.roundedBackground(alphaPercent = settings.handleAlphaPercent)
            setPadding(context.dp(8))
            contentDescription = "Riffle overlay dock"

            addView(
                expandedDockScrollView(
                    shortcuts = shortcuts,
                    settings = settings,
                    onCollapse = onCollapse,
                    onLaunch = onLaunch,
                ),
            )
        }

    fun overlayLayoutParams(
        settings: OverlayDockSettings,
        expanded: Boolean,
    ): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            if (expanded) {
                WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                context.dp(settings.handleThicknessDp)
            },
            if (expanded) {
                WindowManager.LayoutParams.WRAP_CONTENT
            } else {
                context.dp(settings.handleHeightDp)
            },
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
        settings: OverlayDockSettings,
        itemOrientation: OverlayDockExpandedOrientation,
        onLaunch: () -> Unit,
    ): View {
        val iconSizeDp = settings.expandedIconSizeDp
        val labelWidthDp = iconSizeDp + 16
        val iconButton =
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
                background = context.transparentRoundedBackground()
                contentDescription = shortcut.label
                setPadding(context.dp(8))
                layoutParams = LinearLayout.LayoutParams(context.dp(iconSizeDp), context.dp(iconSizeDp))
                setOnClickListener {
                    appLauncher.launch(shortcut.appIdentity)
                    onLaunch()
                }
            }

        if (!settings.showLabels) {
            return iconButton.apply {
                layoutParams =
                    LinearLayout.LayoutParams(context.dp(iconSizeDp), context.dp(iconSizeDp))
                        .withItemMargin(
                            orientation = itemOrientation,
                            spacingPx = context.dp(EXPANDED_ITEM_SPACING_DP),
                        )
            }
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams =
                LinearLayout.LayoutParams(context.dp(labelWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT)
                    .withItemMargin(
                        orientation = itemOrientation,
                        spacingPx = context.dp(EXPANDED_ITEM_SPACING_DP),
                    )
            addView(iconButton)
            addView(
                TextView(context).apply {
                    text = shortcut.label
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams =
                        LinearLayout.LayoutParams(
                            context.dp(labelWidthDp),
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                        )
                },
            )
        }
    }

    private fun expandedDockScrollView(
        shortcuts: List<AppShortcutItem>,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
    ): View =
        when (settings.expandedOrientation) {
            OverlayDockExpandedOrientation.WIDE ->
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(expandedDockItems(shortcuts, settings, onCollapse, onLaunch))
                }

            OverlayDockExpandedOrientation.TALL ->
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            context.dp(context.tallExpandedDockHeightDp(shortcuts, settings)),
                        )
                    addView(expandedDockItems(shortcuts, settings, onCollapse, onLaunch))
                }
        }

    private fun expandedDockItems(
        shortcuts: List<AppShortcutItem>,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = settings.expandedOrientation.linearOrientation
            gravity = settings.expandedOrientation.itemGravity
            shortcuts.forEach { shortcut ->
                addView(
                    shortcutButton(
                        shortcut = shortcut,
                        settings = settings,
                        itemOrientation = settings.expandedOrientation,
                        onLaunch = onLaunch,
                    ),
                )
            }
            if (shortcuts.isEmpty()) {
                addView(emptyDockText())
            }
            addView(collapseButton(onCollapse))
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
}
