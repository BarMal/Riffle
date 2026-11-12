package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The canonical "choose one of a small fixed set of options" settings control, a radio group: the options are
 * mutually exclusive, each is one 48 dp target that is spoken as a radio button with its selected state, and the group
 * is traversed as one.
 *
 * A [FilterChip] carries its selected state to accessibility services as selected, not as
 * disabled -- unlike a disabled button, which TalkBack announces as unavailable rather than as
 * the current value. [onSelect] is not re-fired for an option that is already selected: some
 * call sites are not idempotent, and the control this replaces never re-dispatched the current value either.
 *
 * [optionTag] names an option's node for tests; [optionDescription] is what TalkBack reads instead of the label when
 * an option needs its meaning spoken with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongParameterList")
@Composable
internal fun <T> SettingsChoiceRow(
    title: String,
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    subtitle: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    optionTag: ((T) -> String)? = null,
    optionDescription: (@Composable (T) -> String)? = null,
) {
    Column(modifier = modifier.selectableGroup()) {
        SettingsListRow(title = title, subtitle = subtitle)
        // Wrapping, not scrolling: every option stays on screen and reachable at any width or font size.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            options.forEach { option ->
                val spoken = optionDescription?.let { describe -> describe(option) }
                FilterChip(
                    modifier =
                        Modifier
                            .let { base -> optionTag?.let { tag -> base.testTag(tag(option)) } ?: base }
                            .semantics {
                                role = Role.RadioButton
                                if (spoken != null) contentDescription = spoken
                            },
                    selected = option == selected,
                    enabled = enabled,
                    onClick = { if (option != selected) onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}
