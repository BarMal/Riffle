package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.riffle.app.launcher.designsystem.RiffleElevation
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.settings.LensDetailModel
import com.riffle.core.domain.launcher.workspace.settings.UsedByRow

internal fun lensUsedByTestTag(index: Int): String = "lens-used-by-$index"

@Composable
internal fun DrawsAs(detail: LensDetailModel) {
    if (detail.drawableAs.isNotEmpty()) {
        Column(
            modifier = Modifier.fillMaxWidth().testTag(LENS_DRAWS_AS_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
        ) {
            DetailHeading(LensesSettingsText.DRAWS_AS_HEADING)
            Text(
                text = LensesDetailText.drawableAs(detail.drawableAs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The dry run of 4.4: the containers this edit would stop working, each with the domain's reasons. */
@Composable
internal fun BreaksNotice(detail: LensDetailModel) {
    if (detail.broken.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag(LENS_BREAKS_TEST_TAG),
            shape = RiffleShapes.medium,
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            tonalElevation = RiffleElevation.level0,
        ) {
            Column(
                modifier = Modifier.padding(RiffleSpacing.m).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
            ) {
                Text(LensesDetailText.breaksSummary(detail.broken.size), style = MaterialTheme.typography.bodyMedium)
                Text(LensesSettingsText.ADVANCED_BREAKS_NOTE, style = MaterialTheme.typography.bodySmall)
                detail.broken.forEach { use ->
                    Text(LensesDetailText.broken(use), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun UsedBySection(
    detail: LensDetailModel,
    canEdit: Boolean,
    onEdit: (WorkspaceId) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs)) {
        DetailHeading(LensesSettingsText.USED_BY_HEADING)
        when {
            detail.isNew ->
                Text(
                    text = LensesSettingsText.USED_BY_NEW,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            detail.usedBy.isEmpty() ->
                Text(
                    text = LensesSettingsText.NOT_USED,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            else -> {
                detail.usedBy.forEachIndexed { index, row -> UsedByItem(index, row, canEdit, onEdit) }
                if (!canEdit) {
                    Text(
                        text = LensesSettingsText.EDIT_OTHER_LAYOUT_REASON,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * One container that uses the lens. When the editor can open it (this device's layout) the whole row is the button
 * and says so; otherwise it is plain text, never a dead control.
 */
@Composable
private fun UsedByItem(
    index: Int,
    row: UsedByRow,
    canEdit: Boolean,
    onEdit: (WorkspaceId) -> Unit,
) {
    val base =
        Modifier
            .fillMaxWidth()
            .testTag(lensUsedByTestTag(index))
            .clip(RiffleShapes.medium)
    val rowModifier =
        if (canEdit) {
            base.clickable(role = Role.Button, onClick = { onEdit(row.workspaceId) })
        } else {
            base
        }
    ListItem(
        modifier =
            rowModifier.semantics(mergeDescendants = true) {
                contentDescription = LensesDetailText.usedBySpoken(row, canEdit)
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text = LensesDetailText.usedBy(row), style = MaterialTheme.typography.bodyMedium) },
        trailingContent =
            if (canEdit) {
                { Text(text = LensesSettingsText.EDIT_WORKSPACE, style = MaterialTheme.typography.labelLarge) }
            } else {
                null
            },
    )
}
