package com.riffle.app.launcher.expressions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.settings.ExclusionHideActions
import com.riffle.core.domain.launcher.workspace.settings.HideChoice
import com.riffle.core.domain.launcher.workspace.settings.HideKind

/** What picking a Hide choice does; supplied by the surface (the preview), never by an expression. */
fun interface ItemHider {
    fun hide(
        item: Item,
        kind: HideKind,
    )
}

/**
 * The surface's Hide handler, or null (the default) when it offers none. With none, expressions draw exactly what
 * they always did: no overflow button, no long-press and no extra accessibility actions. The editor's preview
 * leaves it unset, so a draft lens never creates a rule.
 */
internal val LocalItemHider = staticCompositionLocalOf<ItemHider?> { null }

internal const val ITEM_HIDE_BUTTON_TEST_TAG = "item-hide-button"
internal const val ITEM_HIDE_MENU_TEST_TAG = "item-hide-menu"

/** The Hide choices one item offers and what each does. Never holds more than the item it was made for. */
internal class ItemHideMenu(
    val choices: List<HideChoice>,
    private val onPick: (HideKind) -> Unit,
) {
    fun pick(kind: HideKind) = onPick(kind)

    /** One TalkBack custom action per choice, so Hide is reachable without the button or a long press. */
    fun customActions(): List<CustomAccessibilityAction> =
        choices.map { choice ->
            CustomAccessibilityAction(choice.label) {
                pick(choice.kind)
                true
            }
        }
}

/** The menu for [item], or null when the surface offers no Hide or the item supports no choice. */
@Composable
internal fun rememberItemHideMenu(item: Item): ItemHideMenu? {
    val hider = LocalItemHider.current
    val choices = remember(item) { ExclusionHideActions.menuChoicesFor(item) }
    return remember(hider, item, choices) {
        if (hider == null || choices.isEmpty()) null else ItemHideMenu(choices) { kind -> hider.hide(item, kind) }
    }
}

/** Adds the Hide choices as TalkBack custom actions; unchanged when [menu] is null. */
internal fun Modifier.hideCustomActions(menu: ItemHideMenu?): Modifier =
    if (menu == null) this else semantics { customActions = menu.customActions() }

/**
 * The click behaviour of an icon-only cell, which has no room for a button: a plain click, plus (when [menu] exists)
 * a long press that calls [onShowMenu], labelled for TalkBack, and the custom actions. Long press is only added
 * where the only other gesture is a scroll, so it needs no arbitration.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.clickWithHide(
    menu: ItemHideMenu?,
    onShowMenu: () -> Unit,
    onClick: () -> Unit,
): Modifier =
    if (menu == null) {
        clickable(role = Role.Button, onClick = onClick)
    } else {
        combinedClickable(
            role = Role.Button,
            onLongClickLabel = ExpressionText.HIDE_LONG_PRESS_LABEL,
            onLongClick = onShowMenu,
            onClick = onClick,
        ).hideCustomActions(menu)
    }

/** The dropdown with one entry per choice, shown while [expanded]. */
@Composable
internal fun ItemHideDropdown(
    menu: ItemHideMenu,
    expanded: Boolean,
    onDismiss: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ITEM_HIDE_MENU_TEST_TAG),
    ) {
        menu.choices.forEach { choice ->
            DropdownMenuItem(
                text = { Text(choice.label) },
                onClick = {
                    onDismiss()
                    menu.pick(choice.kind)
                },
            )
        }
    }
}

/**
 * A 48 dp "More options" button that opens the Hide dropdown; draws nothing when [menu] is null. The press is an
 * ordinary click, so it needs no gesture arbitration with the row, the card stack or any scroll.
 */
@Composable
internal fun ItemHideButton(
    menu: ItemHideMenu?,
    modifier: Modifier = Modifier,
) {
    if (menu == null) return
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }, modifier = modifier.testTag(ITEM_HIDE_BUTTON_TEST_TAG)) {
        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = ExpressionText.MORE_OPTIONS)
        ItemHideDropdown(menu, expanded) { expanded = false }
    }
}
