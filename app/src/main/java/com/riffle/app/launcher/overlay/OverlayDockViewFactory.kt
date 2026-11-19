package com.riffle.app.launcher.overlay

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
import com.riffle.app.R
import com.riffle.app.launcher.apps.AndroidAppLauncher
import com.riffle.app.launcher.icons.AppIconBitmaps
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.settings.OverlayDockExpandedOrientation
import com.riffle.core.domain.launcher.settings.OverlayDockSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Suppress("TooManyFunctions")
internal class OverlayDockViewFactory(
    private val context: Context,
    private val appLauncher: AndroidAppLauncher,
    /** The one icon pipeline: the Floating dock draws app icons in the chosen style like every other surface. */
    private val icons: AppIconBitmaps = AppIconPipeline.shared(context),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Stops the icon loads still running; the views are gone. */
    fun release() = scope.cancel()

    // Resolved off the main thread; the button shows nothing for a moment and then the icon.
    private fun loadAppIcon(
        button: ImageButton,
        identity: AppIdentity,
        sizePx: Int,
    ) {
        scope.launch {
            val bitmap = runCatching { icons.forActivity(identity, sizePx.coerceAtLeast(1)) }.getOrNull()
            withContext(Dispatchers.Main) { bitmap?.let(button::setImageBitmap) }
        }
    }

    fun collapsedHandleView(
        settings: OverlayDockSettings,
        onExpand: () -> Unit,
        onVerticalOffsetChange: (Int) -> Unit,
        onVerticalOffsetCommit: (Int) -> Unit,
    ): View =
        FrameLayout(context).apply {
            contentDescription = context.getString(R.string.overlay_dock_open_description)
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
            contentDescription = context.getString(R.string.overlay_dock_description)

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
                loadAppIcon(this, shortcut.appIdentity, (context.dp(iconSizeDp) - 2 * context.dp(ICON_PADDING_DP)))
                background = context.transparentRoundedBackground()
                contentDescription = shortcut.label
                setPadding(context.dp(ICON_PADDING_DP))
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
            text = context.getString(R.string.overlay_dock_empty)
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(horizontal = context.dp(12), vertical = context.dp(8))
        }

    private fun usageAccessButton(onRequestUsageAccess: () -> Unit): TextView =
        TextView(context).apply {
            text = context.getString(R.string.overlay_dock_enable_recents)
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(horizontal = context.dp(12), vertical = context.dp(8))
            minHeight = context.dp(USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP)
            minWidth = context.dp(USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP)
            contentDescription = context.getString(R.string.overlay_dock_usage_access_description)
            setOnClickListener { onRequestUsageAccess() }
        }

    private fun collapseButton(onCollapse: () -> Unit): TextView =
        TextView(context).apply {
            text = "X"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(context.dp(12))
            contentDescription = context.getString(R.string.overlay_dock_close_description)
            setOnClickListener { onCollapse() }
        }
}

internal const val USAGE_ACCESS_ACTION_MIN_TOUCH_TARGET_DP = 48
