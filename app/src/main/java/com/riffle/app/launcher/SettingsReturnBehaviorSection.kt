package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.core.domain.launcher.workspace.ReturnBehavior

internal fun returnBehaviorTestTag(behavior: ReturnBehavior): String = "return-behavior-${behavior.name.lowercase()}"

/**
 * Which page of the active workspace shows when the launcher comes back from an app or Home is pressed. A
 * three-option radio group, global rather than per workspace; the per-workspace part (the start page) is set
 * in the editor. It never changes which workspace is active.
 */
@Composable
internal fun SettingsReturnBehaviorSection(
    current: ReturnBehavior,
    onSelect: (ReturnBehavior) -> Unit,
) {
    SettingsSection(title = ReturnBehaviorText.TITLE) {
        ReturnBehavior.entries.forEach { behavior ->
            val label = ReturnBehaviorText.label(behavior)
            val summary = ReturnBehaviorText.summary(behavior)
            val isSelected = behavior == current
            ListItem(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RiffleShapes.medium)
                        .clickable(role = Role.RadioButton, onClick = { onSelect(behavior) })
                        .testTag(returnBehaviorTestTag(behavior))
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$label. $summary"
                            selected = isSelected
                            if (isSelected) stateDescription = ReturnBehaviorText.SELECTED
                        },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { RadioButton(selected = isSelected, onClick = null) },
                headlineContent = { Text(text = label) },
                supportingContent = { Text(text = summary) },
            )
        }
    }
}
