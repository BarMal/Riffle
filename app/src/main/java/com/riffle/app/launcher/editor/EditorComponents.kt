package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing

private const val DISABLED_ALPHA = 0.38f

/**
 * One selectable option (radio or checkbox). A disabled option stays visible with its [reason] and cannot be
 * selected, so people learn why something is unavailable instead of wondering where it went. The whole row is
 * the touch target (at least 48 dp) and TalkBack reads title, hint and reason together.
 */
@Suppress("LongParameterList")
@Composable
internal fun EditorChoiceRow(
    title: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    reason: String? = null,
    multiple: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = if (multiple) Role.Checkbox else Role.RadioButton,
                    onClick = onClick,
                ),
        shape = RiffleShapes.medium,
        color = container,
        tonalElevation = RiffleElevation.level0,
    ) {
        Row(
            modifier =
                Modifier.heightIn(
                    min = RiffleSpacing.xxxl,
                ).padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (multiple) {
                Checkbox(checked = selected, onCheckedChange = null, enabled = enabled)
            } else {
                RadioButton(selected = selected, onClick = null, enabled = enabled)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = textColor(enabled))
                supporting?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = secondaryColor(enabled))
                }
                reason?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun textColor(enabled: Boolean): Color =
    if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(
            alpha = DISABLED_ALPHA,
        )
    }

@Composable
private fun secondaryColor(enabled: Boolean): Color =
    if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA)
    }

@Composable
internal fun EditorSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { heading() },
    )
}

/** A dismissible notice. Announced politely by screen readers when it appears. */
@Composable
internal fun EditorNotice(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RiffleShapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        tonalElevation = RiffleElevation.level0,
    ) {
        Row(
            modifier = Modifier.padding(start = RiffleSpacing.m, end = RiffleSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = RiffleSpacing.s),
            )
            TextButton(onClick = onDismiss) { Text(EditorText.DISMISS) }
        }
    }
}
