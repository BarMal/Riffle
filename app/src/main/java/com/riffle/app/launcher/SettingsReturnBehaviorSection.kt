package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.workspace.ReturnBehavior

internal fun returnBehaviorTestTag(behavior: ReturnBehavior): String = "return-behavior-${behavior.name.lowercase()}"

/**
 * Home > Behaviour: which page of Home shows when the launcher comes back from an app or Home is pressed. A choice row
 * (a radio group), global; the start page itself is the page marked in Home's page overview.
 */
@Composable
internal fun SettingsReturnBehaviorSection(
    current: ReturnBehavior,
    onSelect: (ReturnBehavior) -> Unit,
) {
    SettingsSection(title = stringResource(R.string.settings_section_behaviour)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_RETURNING),
            title = ReturnBehaviorText.TITLE,
            subtitle = ReturnBehaviorText.summary(current),
            options = ReturnBehavior.entries,
            selected = current,
            onSelect = onSelect,
            label = { behavior -> ReturnBehaviorText.label(behavior) },
            optionTag = ::returnBehaviorTestTag,
            optionDescription = { behavior ->
                "${ReturnBehaviorText.label(behavior)}. ${ReturnBehaviorText.summary(behavior)}"
            },
        )
    }
}
