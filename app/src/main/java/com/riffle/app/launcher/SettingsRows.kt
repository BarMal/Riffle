package com.riffle.app.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.riffle.app.R

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

@Composable
private fun Modifier.settingsRowModifier(): Modifier =
    fillMaxWidth()
        .clip(LocalLauncherCardShape.current)

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

/**
 * A bounded whole number changed one step at a time, for values that read better as "4" than as a slider (grid
 * columns and rows). Two 48 dp buttons that say what they change; the row speaks its current value.
 */
@Composable
internal fun SettingsStepperRow(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: (Int) -> String = Int::toString,
) {
    val label = valueLabel(value)
    ListItem(
        modifier =
            modifier
                .settingsRowModifier()
                .semantics { stateDescription = label },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = { SettingsPrimaryText(text = title) },
        supportingContent = { SettingsSecondaryText(text = label) },
        trailingContent = {
            val decrease = stringResource(R.string.settings_stepper_decrease, title)
            val increase = stringResource(R.string.settings_stepper_increase, title)
            Row {
                IconButton(
                    enabled = value > valueRange.first,
                    onClick = { onValueChange((value - 1).coerceIn(valueRange)) },
                    modifier = Modifier.semantics { contentDescription = decrease },
                ) {
                    Text(text = "\u2212")
                }
                IconButton(
                    enabled = value < valueRange.last,
                    onClick = { onValueChange((value + 1).coerceIn(valueRange)) },
                    modifier = Modifier.semantics { contentDescription = increase },
                ) {
                    Text(text = "+")
                }
            }
        },
    )
}

/** Marks a row as the home of one [SettingKey], so search can open its page scrolled to it. */
internal val SettingAnchorKey = SemanticsPropertyKey<String>("SettingAnchor")
internal var SemanticsPropertyReceiver.settingAnchor by SettingAnchorKey

/** Where the rows of the open page register to be scrolled to; null where nothing scrolls to a setting. */
internal class SettingsAnchors {
    @OptIn(ExperimentalFoundationApi::class)
    val requesters = mutableStateMapOf<SettingKey, BringIntoViewRequester>()
}

internal val LocalSettingsAnchors = staticCompositionLocalOf<SettingsAnchors?> { null }

/** The row (or group) that is [key]'s control: searchable, and scrolled into view when search opens its page. */
@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.settingAnchor(key: SettingKey): Modifier =
    composed {
        val anchors = LocalSettingsAnchors.current
        val requester = remember { BringIntoViewRequester() }
        DisposableEffect(anchors, key, requester) {
            anchors?.requesters?.put(key, requester)
            onDispose { if (anchors?.requesters?.get(key) === requester) anchors.requesters.remove(key) }
        }
        bringIntoViewRequester(requester).semantics { settingAnchor = key.name }
    }
