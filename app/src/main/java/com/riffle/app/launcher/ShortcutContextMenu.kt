package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpOffset
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.WidgetItem

internal enum class ShortcutContextSurface {
    HOME,
    DOCK,

    /**
     * The dock's panel. Its own surface because the panel is not one of the layout's pages, so the
     * home removal -- which acts on the selected page -- never matches an item on it.
     */
    DOCK_PANEL,
}

internal data class ShortcutContextMenuItem(
    val label: String,
    val action: LauncherShellAction? = null,
    val enabled: Boolean = true,
    val submenuItems: List<ShortcutContextMenuItem> = emptyList(),
    /** For an item that is not a shell action (the dock's "Open in Stream"); [action] is ignored when this is set. */
    val onSelect: (() -> Unit)? = null,
)

internal fun shortcutContextMenuItems(
    shortcut: AppShortcutItem,
    surface: ShortcutContextSurface,
    text: SettingsText,
    appShortcuts: List<AppShortcut> = emptyList(),
): List<ShortcutContextMenuItem> {
    val platformShortcutItems =
        appShortcuts.map { appShortcut ->
            ShortcutContextMenuItem(
                label = appShortcut.contextMenuLabel,
                action = LauncherShellAction.LaunchAppShortcut(appShortcut),
                enabled = appShortcut.enabled,
            )
        }
    val managementItems =
        listOf(
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_app_info),
                action = shortcut.openAppInfoAction(),
            ),
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_hide_app),
                action = LauncherShellAction.HideApp(shortcut.appIdentity),
            ),
            ShortcutContextMenuItem(
                label = text.text(R.string.menu_uninstall),
                action = LauncherShellAction.UninstallApp(shortcut.appIdentity),
            ),
        ) +
            surface.dockManagementItems(shortcut, text) +
            ShortcutContextMenuItem(
                label = surface.removeLabel(text),
                action = surface.removeAction(shortcut),
            )

    return appShortcutMenuItems(platformShortcutItems, text.text(R.string.menu_more_shortcuts)) + managementItems
}

/** How many of an app's shortcuts stand directly in its long-press menu; any more are behind a "More" row. */
internal const val INLINE_APP_SHORTCUT_LIMIT = 4

/**
 * The app's own shortcuts as the top of its long-press menu: up to [INLINE_APP_SHORTCUT_LIMIT] of them as rows of
 * their own, and a "More" row holding the rest only when there are more than that.
 */
internal fun appShortcutMenuItems(
    shortcuts: List<ShortcutContextMenuItem>,
    moreLabel: String,
): List<ShortcutContextMenuItem> {
    val rest = shortcuts.drop(INLINE_APP_SHORTCUT_LIMIT)
    val more =
        rest.takeIf(List<ShortcutContextMenuItem>::isNotEmpty)?.let {
            ShortcutContextMenuItem(label = moreLabel, submenuItems = it)
        }
    return shortcuts.take(INLINE_APP_SHORTCUT_LIMIT) + listOfNotNull(more)
}

@Composable
internal fun ShortcutContextMenu(
    expanded: Boolean,
    items: List<ShortcutContextMenuItem>,
    onDismissRequest: () -> Unit,
    onAction: (LauncherShellAction) -> Unit,
    offset: DpOffset = DpOffset.Zero,
) {
    var submenuItems by remember(items) { mutableStateOf<List<ShortcutContextMenuItem>?>(null) }
    val visibleItems = submenuItems ?: items
    val dismissMenu = {
        submenuItems = null
        onDismissRequest()
    }

    RiffleContextMenu(
        expanded = expanded,
        onDismissRequest = dismissMenu,
        offset = offset,
    ) {
        if (submenuItems != null) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.menu_back)) },
                onClick = { submenuItems = null },
            )
        }
        visibleItems.forEach { item ->
            DropdownMenuItem(
                text = { Text(text = item.label) },
                enabled = item.enabled,
                trailingIcon =
                    item.submenuItems.takeIf(List<ShortcutContextMenuItem>::isNotEmpty)?.let {
                        { Text(text = "›") }
                    },
                onClick = {
                    if (item.submenuItems.isNotEmpty()) {
                        submenuItems = item.submenuItems
                    } else {
                        dismissMenu()
                        item.onSelect?.invoke() ?: onAction(requireNotNull(item.action))
                    }
                },
            )
        }
    }
}

@Composable
internal fun RiffleContextMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    CloseWhenTheHoldMoves(expanded = expanded, onDismissRequest = onDismissRequest)
    // A menu opened inside the Dock is a Dock gesture performed: the hint for it retires.
    val dock = LocalDockChoice.current
    LaunchedEffect(expanded) { if (expanded) dock?.menuOpened() }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.testTag(RIFFLE_CONTEXT_MENU_TEST_TAG),
        offset = offset,
        shape = LocalLauncherPanelShape.current,
        // The popup draws nothing of its own: the glass below is the menu's whole surface, so it
        // is the same material as every other panel.
        containerColor = Color.Transparent,
        tonalElevation = RiffleElevation.level0,
        shadowElevation = RiffleElevation.level3,
        border = null,
        content = {
            GlassSurface(
                weight = GlassWeight.Thick,
                shape = LocalLauncherPanelShape.current,
                tint = launcherMenuSurfaceColor(),
                contentColor = launcherMenuContentColor(),
            ) {
                Column { content() }
            }
        },
    )
}

internal const val RIFFLE_CONTEXT_MENU_TEST_TAG = "riffle-context-menu"

private fun ShortcutContextSurface.removeLabel(text: SettingsText): String =
    when (this) {
        ShortcutContextSurface.HOME -> text.text(R.string.menu_remove_from_home)
        ShortcutContextSurface.DOCK -> text.text(R.string.menu_remove_from_dock)
        ShortcutContextSurface.DOCK_PANEL -> text.text(R.string.menu_remove_from_panel)
    }

private fun ShortcutContextSurface.removeAction(shortcut: AppShortcutItem): LauncherShellAction =
    when (this) {
        ShortcutContextSurface.HOME -> LauncherShellAction.RemoveHomeShortcut(shortcut.id)
        ShortcutContextSurface.DOCK -> LauncherShellAction.RemoveDockShortcut(shortcut.id)
        ShortcutContextSurface.DOCK_PANEL -> LauncherShellAction.RemoveDockPanelItem(shortcut.id)
    }

/**
 * Where a widget goes when removed depends on what is hosting its grid. The dock's panel is not one
 * of the layout's pages, so the home removal never matches an item on it.
 */
internal fun WidgetItem.removeActionFor(surface: ShortcutContextSurface): LauncherShellAction =
    when (surface) {
        ShortcutContextSurface.DOCK_PANEL -> LauncherShellAction.RemoveDockPanelItem(id)
        // A stack asks first: removing it removes every widget in it.
        ShortcutContextSurface.HOME ->
            if (isStack) {
                LauncherShellAction.ConfirmRemoveWidgetStack(id)
            } else {
                LauncherShellAction.RemoveHomeShortcut(id)
            }
        ShortcutContextSurface.DOCK -> LauncherShellAction.RemoveHomeShortcut(id)
    }

internal fun ShortcutContextSurface.widgetRemoveLabel(text: SettingsText): String =
    when (this) {
        ShortcutContextSurface.DOCK_PANEL -> text.text(R.string.menu_remove_from_panel)
        ShortcutContextSurface.HOME, ShortcutContextSurface.DOCK -> text.text(R.string.menu_remove_from_home)
    }

private fun ShortcutContextSurface.dockManagementItems(
    shortcut: AppShortcutItem,
    text: SettingsText,
): List<ShortcutContextMenuItem> =
    when (this) {
        ShortcutContextSurface.HOME ->
            listOf(
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_add_to_dock),
                    action =
                        LauncherShellAction.AddAppToDock(
                            InstalledApp(
                                identity = shortcut.appIdentity,
                                label = shortcut.label,
                            ),
                        ),
                ),
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_add_to_floating_dock),
                    action =
                        LauncherShellAction.AddAppToFloatingDock(
                            InstalledApp(
                                identity = shortcut.appIdentity,
                                label = shortcut.label,
                            ),
                        ),
                ),
            )

        ShortcutContextSurface.DOCK ->
            listOf(
                ShortcutContextMenuItem(
                    label = text.text(R.string.menu_move_to_home),
                    action = LauncherShellAction.MoveDockItemToHome(shortcut.id),
                ),
            )

        // No "move to" offer yet: moving between the panel and a home page means re-placing on a
        // different grid, which the panel's own drag support has to land first.
        ShortcutContextSurface.DOCK_PANEL -> emptyList()
    }

internal val AppShortcut.contextMenuLabel: String
    get() = longLabel ?: shortLabel

/**
 * "The menu unless it moves" for the Dock: a long-press opens the menu, and when the same press then moves past touch
 * slop (the scrub in the contextual mode, a reorder drag in Home mode) the Dock tells its open menus to close
 * ([DockChoiceState.dismissMenus]).
 */
@Composable
private fun CloseWhenTheHoldMoves(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
) {
    val dismissals = LocalDockChoice.current?.menuDismissals ?: return
    val latestExpanded = rememberUpdatedState(expanded)
    val latestDismiss = rememberUpdatedState(onDismissRequest)
    var seen by remember { mutableIntStateOf(dismissals) }
    LaunchedEffect(dismissals) {
        if (dismissals != seen) {
            seen = dismissals
            if (latestExpanded.value) latestDismiss.value()
        }
    }
}
