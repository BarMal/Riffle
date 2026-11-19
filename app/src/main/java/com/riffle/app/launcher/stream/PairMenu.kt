package com.riffle.app.launcher.stream

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import com.riffle.app.R
import com.riffle.core.domain.launcher.stream.AppPairContent
import com.riffle.core.domain.launcher.stream.PairCardKind
import com.riffle.core.domain.launcher.stream.StreamAction

/**
 * The long-press menu of a saved pair and its accessibility actions: Open side by side, Swap sides, Change first app,
 * Change second app and Remove. No "Pin to the Dock": the stack replaced the Dock's pairs. A draft and the New card
 * have no menu.
 */
internal class PairMenu(
    private val items: List<Item>,
    private val expandedState: androidx.compose.runtime.MutableState<Boolean>,
) {
    class Item(
        val label: String,
        val run: () -> Unit,
    )

    /** The menu's items as custom accessibility actions (Open side by side is the card's own click). */
    val actions: List<CustomAccessibilityAction>
        get() = items.drop(1).map { item -> CustomAccessibilityAction(item.label) { item.run().let { true } } }

    val isAvailable: Boolean get() = items.isNotEmpty()

    fun show() {
        if (isAvailable) expandedState.value = true
    }

    @Composable
    fun Menu() {
        if (!isAvailable) return
        DropdownMenu(expanded = expandedState.value, onDismissRequest = { expandedState.value = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        expandedState.value = false
                        item.run()
                    },
                )
            }
        }
    }
}

@Composable
internal fun rememberPairMenu(
    content: AppPairContent,
    callbacks: StreamCallbacks,
): PairMenu {
    val expanded = remember { mutableStateOf(false) }
    val open = stringResource(R.string.stream_pair_open_side_by_side)
    val swap = stringResource(R.string.stream_pair_swap_sides)
    val first = stringResource(R.string.stream_pair_change_first)
    val second = stringResource(R.string.stream_pair_change_second)
    val remove = stringResource(R.string.stream_pair_remove)
    val id = content.pairId
    val items =
        if (content.kind != PairCardKind.SAVED || id == null) {
            emptyList()
        } else {
            listOf(
                PairMenu.Item(open) { callbacks.onAction(content, StreamAction.Open) },
                PairMenu.Item(swap) { callbacks.onPairEdit(PairEdit.SwapSides(id)) },
                PairMenu.Item(first) { callbacks.onPairEdit(PairEdit.ChangeApp(id, secondHalf = false)) },
                PairMenu.Item(second) { callbacks.onPairEdit(PairEdit.ChangeApp(id, secondHalf = true)) },
                PairMenu.Item(remove) { callbacks.onAction(content, StreamAction.Dismiss) },
            )
        }
    return remember(content, items.size, callbacks) { PairMenu(items, expanded) }
}

/** A tap target of a pair's half: a tap does [onClick]; a long-press opens [menu] where the pair has one. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.pairClickable(
    onClick: () -> Unit,
    label: String?,
    menu: PairMenu,
): Modifier =
    combinedClickable(
        role = Role.Button,
        onClickLabel = label,
        onClick = onClick,
        onLongClick = if (menu.isAvailable) ({ menu.show() }) else null,
    )
