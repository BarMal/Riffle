package com.riffle.app.launcher.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.LensFilter
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensSort
import com.riffle.core.domain.launcher.workspace.LensSortField
import com.riffle.core.domain.launcher.workspace.SortDirection
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.editor.BindingFlow
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowAction
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowContext
import com.riffle.core.domain.launcher.workspace.editor.BindingFlowState
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice

/**
 * Step 1: pick sources and a lens. Presets come first; the custom controls (group, sort, limit, filter) sit
 * behind "Customise" so the default view stays short. A source that needs access is flagged and offers a
 * button that routes to the existing explicit flow: nothing here ever requests a permission itself.
 */
@Composable
internal fun EditorSourceStep(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var customising by rememberSaveable {
        mutableStateOf(
            state.draft.preset == null && state.draft.sources.isNotEmpty(),
        )
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorSectionTitle(EditorText.SOURCES_HEADING)
        context.sources.forEach { choice ->
            SourceRow(choice, choice.id in state.draft.sources, onFlowAction, onRequestSourceAccess)
        }
        EditorSectionTitle(EditorText.LENS_HEADING, Modifier.fillMaxWidth())
        PresetChips(state, context, onFlowAction)
        TextButton(onClick = { customising = !customising }) {
            Text(if (customising) "Hide custom options" else EditorText.CUSTOMISE)
        }
        if (customising) CustomLensControls(state, context, onFlowAction)
    }
}

@Composable
private fun SourceRow(
    choice: SourceChoice,
    selected: Boolean,
    onFlowAction: (BindingFlowAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
) {
    val badges =
        choice.badges.map {
            EditorText.badgeLabel(it)
        } + listOfNotNull(EditorText.NEEDS_ACCESS.takeIf { choice.needsPermission })
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        EditorChoiceRow(
            title = EditorText.sourceLabel(choice.id),
            selected = selected,
            enabled = choice.selectable || selected,
            onClick = { onFlowAction(BindingFlowAction.ToggleSource(choice.id)) },
            supporting = badges.joinToString(" - ").ifEmpty { null },
            reason = EditorText.UNAVAILABLE_SOURCE.takeIf { !choice.selectable },
            multiple = true,
        )
        if (choice.needsPermission) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                Text(
                    text = EditorText.NEEDS_ACCESS_RATIONALE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onRequestSourceAccess(choice.id) }) { Text(EditorText.ALLOW_ACCESS) }
            }
        }
    }
}

@Composable
private fun PresetChips(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    ChipRow {
        BindingFlow.presetChoices(state, context).forEach { choice ->
            FilterChip(
                selected = state.draft.preset == choice.preset,
                onClick = { onFlowAction(BindingFlowAction.ApplyPreset(choice.preset)) },
                enabled = choice.enabled && state.draft.sources.isNotEmpty(),
                label = { Text(EditorText.presetLabel(choice.preset)) },
            )
        }
    }
}

@Composable
private fun CustomLensControls(
    state: BindingFlowState,
    context: BindingFlowContext,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    val draft = state.draft
    val groupable = context.sources.filter { it.id in draft.sources }.all { it.groupable }
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        Text(EditorText.CUSTOM_LENS, style = MaterialTheme.typography.labelLarge)
        ChipRow {
            ChoiceChip(
                "Show all",
                draft.filter == LensFilter.All,
            ) { onFlowAction(BindingFlowAction.SetFilter(LensFilter.All)) }
            ChoiceChip("Only with actions", draft.filter == LensFilter.HasActions()) {
                onFlowAction(BindingFlowAction.SetFilter(LensFilter.HasActions()))
            }
        }
        ChipRow {
            ChoiceChip(
                "Not grouped",
                draft.group == LensGroup.None,
            ) { onFlowAction(BindingFlowAction.SetGroup(LensGroup.None)) }
            ChoiceChip("By group", draft.group == LensGroup.ByGroupKey, enabled = groupable) {
                onFlowAction(BindingFlowAction.SetGroup(LensGroup.ByGroupKey))
            }
            ChoiceChip(
                "By day",
                draft.group == LensGroup.ByDay,
            ) { onFlowAction(BindingFlowAction.SetGroup(LensGroup.ByDay)) }
        }
        SortChips(draft.sort, onFlowAction)
        LimitChips(draft.limit, onFlowAction)
    }
}

@Composable
private fun SortChips(
    sort: LensSort,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    val options =
        listOf(
            "Source order" to LensSort(),
            "A to Z" to LensSort(LensSortField.TITLE, SortDirection.ASCENDING),
            "Newest first" to LensSort(LensSortField.TIME, SortDirection.DESCENDING),
            "Oldest first" to LensSort(LensSortField.TIME, SortDirection.ASCENDING),
        )
    ChipRow {
        options.forEach { (label, option) ->
            ChoiceChip(label, sort == option) { onFlowAction(BindingFlowAction.SetSort(option)) }
        }
    }
}

@Composable
private fun LimitChips(
    limit: Int?,
    onFlowAction: (BindingFlowAction) -> Unit,
) {
    ChipRow {
        Text(
            EditorText.LIMIT_LABEL,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        LimitOptions.forEach { option ->
            ChoiceChip(option?.toString() ?: EditorText.NO_LIMIT, limit == option) {
                onFlowAction(BindingFlowAction.SetLimit(option))
            }
        }
    }
}

private val LimitOptions: List<Int?> = listOf(null, 1, 3, 5, 10)

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        content = content,
    )
}

@Composable
private fun ChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(selected = selected, onClick = onClick, enabled = enabled, label = { Text(label) })
}
