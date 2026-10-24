@file:Suppress("MatchingDeclarationName") // The walkthrough's shared rows and labels; ChoiceKind is only their option.

package com.riffle.app.launcher.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing

/** How a choice row selects: several at once (a checkbox), one of a group (a radio button) or a switch. */
internal enum class ChoiceKind {
    MULTIPLE,
    SINGLE,
    SWITCH,
}

/**
 * One choice as a single 48 dp (or taller) target: a card that reads its title, description, state and reason as
 * one sentence, with its selected state and role for TalkBack. A disabled row stays visible and focusable with its
 * reason. Selected rows get an outline in the primary colour, so the choice does not rely on the control alone.
 */
@Suppress("LongParameterList")
@Composable
internal fun WalkthroughChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ChoiceKind = ChoiceKind.MULTIPLE,
    description: String? = null,
    badge: String? = null,
    enabled: Boolean = true,
) {
    val container =
        if (selected && enabled) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    val border =
        if (selected && enabled) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    val select =
        when (kind) {
            ChoiceKind.SINGLE ->
                Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            ChoiceKind.MULTIPLE ->
                Modifier.toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Checkbox,
                    onValueChange = { onClick() },
                )
            ChoiceKind.SWITCH ->
                Modifier.toggleable(
                    value = selected,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = { onClick() },
                )
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RiffleShapes.medium,
        color = container,
        border = border,
        tonalElevation = RiffleElevation.level0,
    ) {
        Row(
            modifier =
                Modifier
                    .then(select)
                    .heightIn(min = MinTarget)
                    .padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.m),
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind == ChoiceKind.MULTIPLE) Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            if (kind == ChoiceKind.SINGLE) RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = primaryText(enabled))
                description?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = secondaryText(enabled))
                }
                badge?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (enabled) MaterialTheme.colorScheme.tertiary else secondaryText(false),
                    )
                }
            }
            if (kind == ChoiceKind.SWITCH) Switch(checked = selected, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** A step's heading and supporting text, at the top of its content. */
@Composable
internal fun StepIntro(
    title: String,
    body: String? = null,
    note: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
        )
        body?.let { Text(text = it, style = MaterialTheme.typography.bodyLarge) }
        note?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A section label inside a step (for example "How should it look?"). */
@Composable
internal fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = RiffleSpacing.s).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Problems and statuses, announced politely when they change. */
@Composable
internal fun PoliteNotice(
    text: String,
    error: Boolean = false,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RiffleShapes.medium,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = RiffleSpacing.l, vertical = RiffleSpacing.m),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun primaryText(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED)

@Composable
private fun secondaryText(enabled: Boolean): Color =
    if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED)
    }

internal val MinTarget = 56.dp
private const val DISABLED = 0.38f
