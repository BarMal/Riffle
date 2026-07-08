package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsClickableRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = {
        SettingsButtonText(text = "Open")
    },
) {
    SettingsListItem(
        modifier =
            modifier
                .settingsRowModifier()
                .clickable(role = Role.Button, onClick = onClick),
        title = title,
        subtitle = subtitle,
        trailingContent = trailingContent,
    )
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    SettingsListItem(
        modifier =
            modifier
                .settingsRowModifier()
                .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
        title = title,
        subtitle = subtitle,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
    )
}

@Composable
internal fun SettingsListRow(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    SettingsListItem(
        modifier = modifier.settingsRowModifier(),
        title = title,
        subtitle = subtitle,
        trailingContent = trailingContent,
    )
}

private fun Modifier.settingsRowModifier(): Modifier =
    fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))

@Composable
private fun SettingsListItem(
    title: String,
    subtitle: String?,
    modifier: Modifier,
    trailingContent: @Composable (() -> Unit)?,
) {
    if (subtitle == null) {
        ListItem(
            modifier = modifier,
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = { SettingsPrimaryText(text = title) },
            trailingContent = trailingContent,
        )
    } else {
        ListItem(
            modifier = modifier,
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
            headlineContent = { SettingsPrimaryText(text = title) },
            supportingContent = { SettingsSecondaryText(text = subtitle) },
            trailingContent = trailingContent,
        )
    }
}
