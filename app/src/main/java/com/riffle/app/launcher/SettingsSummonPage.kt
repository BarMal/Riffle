package com.riffle.app.launcher

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.glance.labelRes
import com.riffle.app.launcher.summon.summonAddChoices
import com.riffle.app.launcher.summon.summonHeaderChoices
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.summon.MAX_SUMMON_SLOT_COUNT
import com.riffle.core.domain.launcher.summon.MIN_SUMMON_SLOT_COUNT
import com.riffle.core.domain.launcher.summon.SUMMON_ACCEPT_MILLIS_RANGE
import com.riffle.core.domain.launcher.summon.SUMMON_ARM_MILLIS_RANGE
import com.riffle.core.domain.launcher.summon.SUMMON_ICON_SIZE_DP_RANGE
import com.riffle.core.domain.launcher.summon.SUMMON_PREVIEW_MILLIS_RANGE
import com.riffle.core.domain.launcher.summon.SUMMON_SLIDE_MILLIS_RANGE
import com.riffle.core.domain.launcher.summon.SummonHeader
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot

private val PICKER_MAX_HEIGHT = 420.dp
private const val TIMING_STEP = 25
private const val SLIDE_STEP = 50
private const val ICON_SIZE_STEP = 4

/** Settings > Home: the row that opens Summon's page and says whether it is on. */
@Composable
internal fun SettingsSummonRow(
    state: SettingsSurfaceState,
    onOpen: () -> Unit,
) {
    val on = state.settings.features.summon
    val status = if (on) R.string.settings_summon_row_on else R.string.settings_summon_row_off
    SettingsSection(title = stringResource(R.string.settings_page_summon)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_summon),
            subtitle = stringResource(status),
            onClick = onOpen,
        )
    }
}

/**
 * Settings > Home > Summon: the switch, how many places the ring has, their order (with buttons, so reordering needs
 * no drag), what can be added, what sits at the top of the screen, and the timings. Every edit is one
 * [LauncherShellAction.SetSummonSettings] with the whole new value.
 */
@Composable
@Suppress("LongMethod")
internal fun SettingsSummonPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val summon = state.settings.summon
    val set: (SummonSettings) -> Unit = { onAction(LauncherShellAction.SetSummonSettings(it)) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var choosingHeader by rememberSaveable { mutableStateOf(false) }
    val label = rememberSummonSlotLabel(state.homeLayout, state.installedApps)
    val context = LocalContext.current

    SettingsSection(title = stringResource(R.string.settings_page_summon)) {
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_MODE),
            title = SettingKey.SUMMON_MODE.label(),
            subtitle = stringResource(R.string.settings_summon_mode_subtitle),
            checked = state.settings.features.summon,
            onCheckedChange = { on -> onAction(LauncherShellAction.SetSummonMode(on)) },
        )
        SettingsStepperRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_SLOT_COUNT),
            title = SettingKey.SUMMON_SLOT_COUNT.label(),
            value = summon.slotCount,
            valueRange = MIN_SUMMON_SLOT_COUNT..MAX_SUMMON_SLOT_COUNT,
            onValueChange = { set(summon.withSlotCount(it)) },
        )
        SettingsSteppedSlider(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_ICON_SIZE),
            title = SettingKey.SUMMON_ICON_SIZE.label(),
            value = summon.iconSizeDp,
            values = SUMMON_ICON_SIZE_DP_RANGE step ICON_SIZE_STEP,
            valueLabel = { context.getString(R.string.settings_summon_dp, it) },
            onValueChange = { set(summon.withIconSize(it)) },
        )
    }

    SettingsSection(title = SettingKey.SUMMON_SLOTS.label()) {
        SettingsListRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_SLOTS),
            title = SettingKey.SUMMON_SLOTS.label(),
            subtitle = stringResource(R.string.settings_summon_slots_subtitle),
        )
        summon.slots.forEachIndexed { index, slot ->
            val name = label(slot)
            SettingsListRow(
                modifier = Modifier.settingAnchor(SettingKey.SUMMON_SLOTS),
                title = name,
                subtitle =
                    if (index >= summon.slotCount) {
                        stringResource(
                            R.string.settings_summon_slot_off_ring,
                        )
                    } else {
                        null
                    },
                trailingContent = {
                    Row {
                        IconButton(enabled = index > 0, onClick = { set(summon.moved(index, index - 1)) }) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                stringResource(R.string.settings_summon_slot_move_up, name),
                            )
                        }
                        IconButton(
                            enabled = index < summon.slots.lastIndex,
                            onClick = { set(summon.moved(index, index + 1)) },
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                stringResource(R.string.settings_summon_slot_move_down, name),
                            )
                        }
                        IconButton(onClick = { set(summon.removed(index)) }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.settings_summon_slot_remove, name))
                        }
                    }
                },
            )
        }
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_ADD),
            title = SettingKey.SUMMON_ADD.label(),
            subtitle = stringResource(R.string.settings_summon_add_subtitle),
            onClick = { adding = true },
        )
    }

    SettingsSection(title = SettingKey.SUMMON_HEADER.label()) {
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_HEADER),
            title = SettingKey.SUMMON_HEADER.label(),
            subtitle = headerLabel(summon.header, state.homeLayout),
            onClick = { choosingHeader = true },
        )
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_HINTS),
            title = SettingKey.SUMMON_HINTS.label(),
            subtitle = stringResource(R.string.settings_summon_hints_subtitle),
            checked = summon.showHints,
            onCheckedChange = { on -> set(summon.copy(showHints = on)) },
        )
    }

    SettingsSection(title = stringResource(R.string.setting_summon_timing_section)) {
        TimingSlider(
            key = SettingKey.SUMMON_ARM_TIME,
            value = summon.timings.armMillis,
            values = SUMMON_ARM_MILLIS_RANGE step TIMING_STEP,
        ) { set(summon.copy(timings = summon.timings.copy(armMillis = it))) }
        TimingSlider(
            key = SettingKey.SUMMON_PREVIEW_TIME,
            value = summon.timings.previewMillis,
            values = SUMMON_PREVIEW_MILLIS_RANGE step TIMING_STEP,
        ) { set(summon.copy(timings = summon.timings.copy(previewMillis = it))) }
        TimingSlider(
            key = SettingKey.SUMMON_ACCEPT_TIME,
            value = summon.timings.acceptMillis,
            values = SUMMON_ACCEPT_MILLIS_RANGE step TIMING_STEP,
        ) { set(summon.copy(timings = summon.timings.copy(acceptMillis = it))) }
        TimingSlider(
            key = SettingKey.SUMMON_SLIDE_TIME,
            value = summon.timings.slideMillis,
            values = SUMMON_SLIDE_MILLIS_RANGE step SLIDE_STEP,
        ) { set(summon.copy(timings = summon.timings.copy(slideMillis = it))) }
    }

    if (adding) {
        SummonAddDialog(summon, state.homeLayout, state.installedApps, label, onAdd = { set(summon.added(it)) }) {
            adding = false
        }
    }
    if (choosingHeader) {
        SummonHeaderDialog(state.homeLayout, onChoose = { set(summon.copy(header = it)) }) { choosingHeader = false }
    }
}

@Composable
private fun TimingSlider(
    key: SettingKey,
    value: Int,
    values: IntProgression,
    onValueChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    SettingsSteppedSlider(
        modifier = Modifier.settingAnchor(key),
        title = key.label(),
        value = value,
        values = values,
        valueLabel = { context.getString(R.string.settings_summon_millis, it) },
        onValueChange = onValueChange,
    )
}

/** A slot's name in Settings: the app's or folder's label, a glance's name, or Favourites. */
@Composable
private fun rememberSummonSlotLabel(
    layout: HomeLayout,
    installed: List<InstalledApp>,
): (SummonSlot) -> String {
    val context = LocalContext.current
    val favourites = stringResource(R.string.summon_favourites)
    val apps = installed.associate { it.identity to it.label }
    val folders = layout.pages.flatMap { it.items }.filterIsInstance<FolderItem>().associate { it.id to it.label }
    return { slot ->
        when (slot) {
            is SummonSlot.App -> apps[slot.identity] ?: slot.identity.packageName.value
            is SummonSlot.Folder -> folders[slot.folderId] ?: slot.folderId.value
            is SummonSlot.Glance -> context.getString(slot.kind.labelRes())
            SummonSlot.Favourites -> favourites
        }
    }
}

@Composable
private fun headerLabel(
    header: SummonHeader,
    layout: HomeLayout,
): String {
    val choice = summonHeaderChoices(layout).firstOrNull { it.header == header }
    return when (header) {
        SummonHeader.None -> stringResource(R.string.settings_summon_header_none)
        SummonHeader.Clock -> stringResource(R.string.settings_summon_header_clock)
        SummonHeader.ClockAndDate -> stringResource(R.string.settings_summon_header_clock_and_date)
        is SummonHeader.Glance -> stringResource(header.kind.labelRes())
        is SummonHeader.HomeWidget ->
            stringResource(R.string.settings_summon_header_home_widget, choice?.widgetLabel ?: header.itemId.value)
    }
}

@Composable
private fun SummonAddDialog(
    summon: SummonSettings,
    layout: HomeLayout,
    installed: List<InstalledApp>,
    label: (SummonSlot) -> String,
    onAdd: (SummonSlot) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = summonAddChoices(summon, layout, installed)
    val groups =
        listOf(
            R.string.settings_summon_add_section_places to choices.places,
            R.string.settings_summon_add_section_glances to choices.glances,
            R.string.settings_summon_add_section_apps to choices.apps.map { SummonSlot.App(it.identity) },
        ).filter { it.second.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingKey.SUMMON_ADD.label()) },
        text = {
            if (groups.isEmpty()) {
                Text(stringResource(R.string.settings_summon_nothing_to_add))
            } else {
                val headings = groups.map { stringResource(it.first) }
                LazyColumn(Modifier.heightIn(max = PICKER_MAX_HEIGHT)) {
                    groups.forEachIndexed { groupIndex, (_, slots) ->
                        item { SettingsSecondaryText(text = headings[groupIndex]) }
                        items(slots) { slot ->
                            TextButton(onClick = {
                                onAdd(slot)
                                onDismiss()
                            }) { Text(label(slot)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}

@Composable
private fun SummonHeaderDialog(
    layout: HomeLayout,
    onChoose: (SummonHeader) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = summonHeaderChoices(layout)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SettingKey.SUMMON_HEADER.label()) },
        text = {
            LazyColumn(Modifier.heightIn(max = PICKER_MAX_HEIGHT)) {
                items(choices) { choice ->
                    TextButton(onClick = {
                        onChoose(choice.header)
                        onDismiss()
                    }) { Text(headerLabel(choice.header, layout)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
}
