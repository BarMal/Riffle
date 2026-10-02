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
import com.riffle.core.domain.launcher.workspace.editor.LensDraft
import com.riffle.core.domain.launcher.workspace.editor.LensDraftAction
import com.riffle.core.domain.launcher.workspace.editor.LensDraftReducer
import com.riffle.core.domain.launcher.workspace.editor.LensQueryEdits
import com.riffle.core.domain.launcher.workspace.editor.SourceChoice
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus

/**
 * The lens builder: pick sources, then a lens. Presets come first; the custom controls (filter, group, sort, limit)
 * sit behind "Customise" so the default view stays short. Shared by the editor's Source step and Settings > Saved
 * lenses, so the lens rules live once: this composable only renders [draft] and forwards [LensDraftAction]s, and
 * [LensDraftReducer] (the same reducer the editor flow uses) decides what each one does.
 *
 * - [sources] are the registry's sources as the host sees them. A source that needs access is flagged and offers
 *   a button that routes to the existing explicit flow ([onRequestSourceAccess]): nothing here ever requests a
 *   permission itself. A [SourceChoice.status] (Settings) is shown as text with its reason.
 * - [queryFlush] lets a host apply a typed-but-not-yet-applied search text before it acts on the draft.
 * - The host owns the draft: keep a [LensDraft], pass each action through [LensDraftReducer.reduce], and build the
 *   lens with [LensDraft.toLens].
 */
@Composable
internal fun LensBuilder(
    draft: LensDraft,
    sources: List<SourceChoice>,
    onAction: (LensDraftAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
    queryFlush: PendingQueryFlush? = null,
) {
    var customising by rememberSaveable {
        mutableStateOf(draft.preset == null && draft.sources.isNotEmpty())
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        EditorSectionTitle(EditorText.SOURCES_HEADING)
        sources.forEach { choice ->
            SourceRow(choice, choice.id in draft.sources, onAction, onRequestSourceAccess)
            if (choice.id in draft.sources && LensQueryEdits.supportsQuery(choice.id)) {
                SourceQueryField(
                    source = choice.id,
                    applied = draft.queryFor(choice.id),
                    onApply = { text -> onAction(LensDraftAction.SetQuery(choice.id, text)) },
                    flush = queryFlush,
                )
            }
        }
        EditorSectionTitle(EditorText.LENS_HEADING, Modifier.fillMaxWidth())
        PresetChips(draft, sources, onAction)
        TextButton(onClick = { customising = !customising }) {
            Text(if (customising) "Hide custom options" else EditorText.CUSTOMISE)
        }
        if (customising) CustomLensControls(draft, sources, onAction)
    }
}

@Composable
private fun SourceRow(
    choice: SourceChoice,
    selected: Boolean,
    onAction: (LensDraftAction) -> Unit,
    onRequestSourceAccess: (SourceId) -> Unit,
) {
    val status = EditorReasonText.sourceStatusLabel(choice.status)
    val badges =
        choice.badges.map {
            EditorText.badgeLabel(it)
        } + listOfNotNull(EditorText.NEEDS_ACCESS.takeIf { choice.needsPermission && status == null }, status)
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        EditorChoiceRow(
            title = EditorText.sourceLabel(choice.id),
            selected = selected,
            enabled = choice.selectable || selected,
            onClick = { onAction(LensDraftAction.ToggleSource(choice.id)) },
            supporting = badges.joinToString(" - ").ifEmpty { null },
            reason = EditorText.UNAVAILABLE_SOURCE.takeIf { !choice.selectable },
            multiple = true,
        )
        if (choice.status == SourceStatus.OFF) {
            Text(
                text = EditorReasonText.SOURCE_OFF_NOTE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
    draft: LensDraft,
    sources: List<SourceChoice>,
    onAction: (LensDraftAction) -> Unit,
) {
    ChipRow {
        LensDraftReducer.presetChoices(draft, sources).forEach { choice ->
            FilterChip(
                selected = draft.preset == choice.preset,
                onClick = { onAction(LensDraftAction.ApplyPreset(choice.preset)) },
                enabled = choice.enabled && draft.sources.isNotEmpty(),
                label = { Text(EditorText.presetLabel(choice.preset)) },
            )
        }
    }
}

@Composable
private fun CustomLensControls(
    draft: LensDraft,
    sources: List<SourceChoice>,
    onAction: (LensDraftAction) -> Unit,
) {
    val groupable = sources.filter { it.id in draft.sources }.all { it.groupable }
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        Text(EditorText.CUSTOM_LENS, style = MaterialTheme.typography.labelLarge)
        ChipRow {
            ChoiceChip(
                "Show all",
                draft.filter == LensFilter.All,
            ) { onAction(LensDraftAction.SetFilter(LensFilter.All)) }
            ChoiceChip("Only with actions", draft.filter == LensFilter.HasActions()) {
                onAction(LensDraftAction.SetFilter(LensFilter.HasActions()))
            }
        }
        ChipRow {
            ChoiceChip(
                "Not grouped",
                draft.group == LensGroup.None,
            ) { onAction(LensDraftAction.SetGroup(LensGroup.None)) }
            ChoiceChip("By group", draft.group == LensGroup.ByGroupKey, enabled = groupable) {
                onAction(LensDraftAction.SetGroup(LensGroup.ByGroupKey))
            }
            ChoiceChip(
                "By day",
                draft.group == LensGroup.ByDay,
            ) { onAction(LensDraftAction.SetGroup(LensGroup.ByDay)) }
        }
        SortChips(draft.sort, onAction)
        LimitChips(draft.limit, onAction)
    }
}

@Composable
private fun SortChips(
    sort: LensSort,
    onAction: (LensDraftAction) -> Unit,
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
            ChoiceChip(label, sort == option) { onAction(LensDraftAction.SetSort(option)) }
        }
    }
}

@Composable
private fun LimitChips(
    limit: Int?,
    onAction: (LensDraftAction) -> Unit,
) {
    ChipRow {
        Text(
            EditorText.LIMIT_LABEL,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        LimitOptions.forEach { option ->
            ChoiceChip(option?.toString() ?: EditorText.NO_LIMIT, limit == option) {
                onAction(LensDraftAction.SetLimit(option))
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
