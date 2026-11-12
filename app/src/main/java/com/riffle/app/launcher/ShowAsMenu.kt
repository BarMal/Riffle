package com.riffle.app.launcher

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing

/**
 * The "Show as" choice every Place offers (ADR 0003, rule 3): a "Show as" label, then one item per view with the
 * current one ticked and announced. Shared by the Stream's section headers and the Library header, so the two read and
 * sound the same. Place it in a menu's column; [onSelect] is called for the chosen view (the host closes the menu).
 */
@Composable
internal fun <T> ColumnScope.ShowAsMenuItems(
    options: List<T>,
    current: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Text(
        text = stringResource(R.string.stream_show_as),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.xs),
    )
    options.forEach { option ->
        val label = optionLabel(option)
        val currentDescription = stringResource(R.string.stream_current_view, label)
        DropdownMenuItem(
            text = { Text(label) },
            modifier = Modifier.semantics { if (option == current) stateDescription = currentDescription },
            onClick = { onSelect(option) },
            trailingIcon = { if (option == current) Text("✓") },
        )
    }
}
