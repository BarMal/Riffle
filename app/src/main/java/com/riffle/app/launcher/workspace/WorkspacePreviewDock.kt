package com.riffle.app.launcher.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.FolderPreviewIcon
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.containers.LensBoundExpression
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.pool.PlacedHomeContent
import com.riffle.core.domain.launcher.home.FolderItem
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.dock.PreviewDock
import com.riffle.core.domain.launcher.workspace.dock.PreviewDockModel
import com.riffle.core.domain.launcher.workspace.dock.PreviewDockPin

internal const val PREVIEW_DOCK_PINS_TEST_TAG = "workspace-preview-dock-pins"
internal const val PREVIEW_DOCK_DYNAMIC_TEST_TAG = "workspace-preview-dock-dynamic"
internal const val PREVIEW_DOCK_EMPTY_TEST_TAG = "workspace-preview-dock-empty"
internal const val PREVIEW_DOCK_FOLDER_TEST_TAG = "workspace-preview-dock-folder"

internal fun previewDockPinTestTag(pin: PreviewDockPin): String = "workspace-preview-dock-pin-${pin.key}"

private val FolderRowIcon = 40.dp

/**
 * The read-only dock content of the Workspaces (preview): the pinned section ([model]) and, when the workspace has
 * one, its dynamic section ([dynamicSection], drawn by the same expression pipeline as every page). Both sit in a
 * single run: pinned pins scroll horizontally if they do not fit; the dynamic section takes the other half.
 *
 * Taps launch through [home] (the same launchers as the home page); a folder opens a read-only list. There is no
 * drag, reorder, long-press menu or expand gesture here and none is attached, so the dock pull stays with the
 * existing workspace-menu wiring and no gesture arbitration is added.
 */
@Composable
internal fun PreviewDockContent(
    model: PreviewDockModel,
    home: PlacedHomeContent,
    dynamicSection: LensBinding?,
    services: ContainerServices,
    modifier: Modifier = Modifier,
) {
    var openFolder by remember { mutableStateOf<FolderItem?>(null) }
    Row(
        modifier = modifier.fillMaxSize().padding(horizontal = RiffleSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        if (!model.hasNoPins) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight().testTag(PREVIEW_DOCK_PINS_TEST_TAG),
                contentAlignment = Alignment.Center,
            ) {
                PinnedRun(model, home) { openFolder = it }
            }
        }
        if (dynamicSection != null) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag(PREVIEW_DOCK_DYNAMIC_TEST_TAG)) {
                LensBoundExpression(dynamicSection, services, Modifier.fillMaxSize())
            }
        }
        if (model.hasNoPins && dynamicSection == null) {
            Text(
                text = if (model.isShown) WorkspacePreviewText.DOCK_EMPTY else WorkspacePreviewText.DOCK_HIDDEN,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f).testTag(PREVIEW_DOCK_EMPTY_TEST_TAG),
            )
        }
    }
    openFolder?.let { folder -> DockFolderDialog(folder, home, onDismiss = { openFolder = null }) }
}

@Composable
private fun PinnedRun(
    model: PreviewDockModel,
    home: PlacedHomeContent,
    onOpenFolder: (FolderItem) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(model.itemSpacingDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        model.pins.forEach { pin ->
            Box(
                modifier =
                    Modifier
                        .size(model.targetSizeDp.dp)
                        .testTag(previewDockPinTestTag(pin))
                        .clickable(
                            onClickLabel = PreviewDock.actionLabel(pin),
                            role = Role.Button,
                            onClick = {
                                when (pin) {
                                    is PreviewDockPin.App -> home.onOpen(pin.item)
                                    is PreviewDockPin.Folder -> onOpenFolder(pin.item)
                                }
                            },
                        )
                        .clearAndSetSemantics { contentDescription = PreviewDock.spokenLabel(pin) },
                contentAlignment = Alignment.Center,
            ) {
                when (pin) {
                    is PreviewDockPin.App ->
                        LauncherAppIcon(
                            pin.item.appIdentity,
                            pin.label,
                            home.iconLoader,
                            Modifier.size(model.iconSizeDp.dp),
                        )
                    is PreviewDockPin.Folder -> FolderPreviewIcon(pin.item, home.iconLoader, model.iconSizeDp)
                }
            }
        }
    }
}

@Composable
private fun DockFolderDialog(
    folder: FolderItem,
    home: PlacedHomeContent,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PREVIEW_DOCK_FOLDER_TEST_TAG),
        title = { Text(folder.label) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                folder.items.forEach { entry ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClickLabel = "Open ${entry.label}", role = Role.Button) {
                                    onDismiss()
                                    home.onOpen(entry)
                                }
                                .padding(vertical = RiffleSpacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
                    ) {
                        LauncherAppIcon(entry.appIdentity, entry.label, home.iconLoader, Modifier.size(FolderRowIcon))
                        Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(WorkspacePreviewText.CLOSE) } },
    )
}
