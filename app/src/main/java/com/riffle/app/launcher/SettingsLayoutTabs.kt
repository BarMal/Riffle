package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass

/** The width from which a Sources, Privacy or Hidden items page may lay its sections in two columns. */
internal const val TWO_PANE_MIN_WIDTH_DP = 600

/** The layouts (postures) a per-layout page can show; each keeps its own rules. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WorkspacesLayoutTabs(
    tabs: List<SettingsLayoutDeviceTab>,
    selected: HomeLayoutDeviceClass,
    onSelect: (HomeLayoutDeviceClass) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab.deviceClass == selected,
                onClick = { onSelect(tab.deviceClass) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                modifier =
                    Modifier.semantics {
                        contentDescription = WorkspacesSettingsText.layoutName(tab.deviceClass)
                    },
                label = { Text(text = tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

/** One selectable radio option; the whole row is the touch target (at least 48 dp). */
@Composable
internal fun SettingsRadioOptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RiffleShapes.medium,
        color = container,
        tonalElevation = RiffleElevation.level0,
    ) {
        Row(
            modifier =
                Modifier
                    .heightIn(min = RiffleSpacing.xxxl)
                    .padding(horizontal = RiffleSpacing.m, vertical = RiffleSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
