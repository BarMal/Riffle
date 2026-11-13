package com.riffle.app.launcher.stream

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.ShowAsMenuItems
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamWideView

internal const val STREAM_OPTIONS_TEST_TAG = "stream-options"

/** The strings and state the Stream's own menu needs: Show as (wide postures only) and the hidden sections. */
internal class StreamMenuModel(
    val wideView: StreamWideView?,
    val hiddenSections: List<StreamSection>,
) {
    val isEmpty: Boolean get() = wideView == null && hiddenSections.isEmpty()
}

/** What the Stream's own menu can ask for; the surface turns each into a layout action and the Undo it offers. */
internal sealed interface StreamMenuAction {
    data class SetWideView(val view: StreamWideView) : StreamMenuAction

    data class ShowSection(val section: StreamSection) : StreamMenuAction
}

internal fun StreamWideView.titleRes(): Int =
    when (this) {
        StreamWideView.PAGES -> R.string.stream_wide_pages
        StreamWideView.STAGE -> R.string.stream_wide_stage
    }

/** The Stream-level menu: Show as Pages or Stage (wide postures), and a way back for every hidden section. */
@Composable
internal fun StreamMenu(
    menu: StreamMenuModel,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (StreamMenuAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        menu.wideView?.let { current ->
            ShowAsMenuItems(
                options = StreamWideView.entries,
                current = current,
                optionLabel = { option -> stringResource(option.titleRes()) },
                onSelect = { option ->
                    onDismiss()
                    onAction(StreamMenuAction.SetWideView(option))
                },
            )
        }
        if (menu.wideView != null && menu.hiddenSections.isNotEmpty()) HorizontalDivider()
        menu.hiddenSections.forEach { section ->
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(R.string.stream_show_hidden_section, stringResource(section.titleRes())),
                    )
                },
                onClick = {
                    onDismiss()
                    onAction(StreamMenuAction.ShowSection(section))
                },
            )
        }
    }
}

internal fun StreamMenuAction.toLayoutAction(): StreamLayoutAction =
    when (this) {
        is StreamMenuAction.SetWideView -> StreamLayoutAction.SetWideView(view)
        is StreamMenuAction.ShowSection -> StreamLayoutAction.SetEnabled(section, true)
    }
