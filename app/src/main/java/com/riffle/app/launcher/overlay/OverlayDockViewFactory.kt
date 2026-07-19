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
import com.riffle.core.domain.launcher.apps.AppShortcut
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
            contentDescription = "Open Riffle overlay dock"
            addView(
                View(context).apply {
                    background = context.handleGripBackground(settings)
                    layoutParams =
                        FrameLayout.LayoutParams(
                            context.dp(settings.handleThicknessDp),
                            context.dp(settings.handleHeightDp),
                            Gravity.CENTER_VERTICAL or settings.edge.edgeGravity,
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
        content: OverlayDockShortcuts,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
        onRequestUsageAccess: () -> Unit,
    ): View =
        FrameLayout(context).apply {
            background = context.roundedBackground(alphaPercent = settings.handleAlphaPercent)
            setPadding(context.dp(8))
            contentDescription = "Riffle overlay dock"

            addView(
                expandedDockScrollView(
                    content = content,
                    settings = settings,
                    onCollapse = onCollapse,
                    onLaunch = onLaunch,
                    onRequestUsageAccess = onRequestUsageAccess,
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
                context.dp(settings.collapsedHandleTouchTargetWidthDp())
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
                    shortcut.appShortcutId
                        ?.let { shortcutId ->
                            appLauncher.launchShortcut(
                                AppShortcut(
                                    id = shortcutId,
                                    appIdentity = shortcut.appIdentity,
                                    shortLabel = shortcut.label,
                                ),
                            )
                        }
                        ?: appLauncher.launch(shortcut.appIdentity)
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
        content: OverlayDockShortcuts,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
        onRequestUsageAccess: () -> Unit,
    ): View =
        when (settings.expandedOrientation) {
            OverlayDockExpandedOrientation.WIDE ->
                HorizontalScrollView(context).apply {
                    isHorizontalScrollBarEnabled = false
                    addView(expandedDockItems(content, settings, onCollapse, onLaunch, onRequestUsageAccess))
                }

            OverlayDockExpandedOrientation.TALL ->
                ScrollView(context).apply {
                    isVerticalScrollBarEnabled = false
                    layoutParams =
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            context.dp(
                                context.tallExpandedDockHeightDp(
                                    content.pinnedShortcuts + content.recentShortcuts,
                                    settings,
                                ),
                            ),
                        )
                    addView(expandedDockItems(content, settings, onCollapse, onLaunch, onRequestUsageAccess))
                }
        }

    private fun expandedDockItems(
        content: OverlayDockShortcuts,
        settings: OverlayDockSettings,
        onCollapse: () -> Unit,
        onLaunch: () -> Unit,
        onRequestUsageAccess: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = settings.expandedOrientation.itemGravity
            if (content.pinnedShortcuts.isNotEmpty()) {
                addView(dockSection("Docked apps", content.pinnedShortcuts, settings, onLaunch))
            }
            if (content.recentShortcuts.isNotEmpty()) {
                addView(dockSection("Recent apps", content.recentShortcuts, settings, onLaunch))
            } else if (content.recentAppsAccessRequired) {
                addView(usageAccessButton(onRequestUsageAccess))
            }
            if (content.pinnedShortcuts.isEmpty() && content.recentShortcuts.isEmpty()) {
                addView(emptyDockText())
            }
            addView(collapseButton(onCollapse))
        }

    private fun dockSection(
        title: String,
        shortcuts: List<AppShortcutItem>,
        settings: OverlayDockSettings,
        onLaunch: () -> Unit,
    ): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                TextView(context).apply {
                    text = title
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setPadding(horizontal = context.dp(4), vertical = context.dp(4))
                },
            )
            addView(
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
                },
            )
        }

    private fun emptyDockText(): TextView =
        TextView(context).apply {
            text = "Dock empty"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(horizontal = context.dp(12), vertical = context.dp(8))
        }

    private fun usageAccessButton(onRequestUsageAccess: () -> Unit): TextView =
        TextView(context).apply {
            text = "Enable recent apps"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(horizontal = context.dp(12), vertical = context.dp(8))
            minHeight = context.dp(USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP)
            minWidth = context.dp(USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP)
            contentDescription = "Allow Usage Access to show recent apps"
            setOnClickListener { onRequestUsageAccess() }
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

internal const val USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP = 48
