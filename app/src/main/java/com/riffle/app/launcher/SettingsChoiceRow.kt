package com.riffle.app.launcher

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The canonical "choose one of a small fixed set of options" settings control.
 *
 * A [FilterChip] carries its selected state to accessibility services as selected, not as
 * disabled -- unlike a disabled button, which TalkBack announces as unavailable rather than as
 * the current value. [onSelect] is not re-fired for an option that is already selected: some
 * call sites (e.g. re-seeding a layout template) are not idempotent, and the control this
 * replaces never re-dispatched the current value either, since that option's button was disabled.
 */
@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        SettingsListRow(title = title, subtitle = subtitle)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { if (option != selected) onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
