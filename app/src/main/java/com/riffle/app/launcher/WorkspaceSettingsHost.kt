package com.riffle.app.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.containers.ContainerServices
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.ics.IcsFeedsController
import com.riffle.app.launcher.workspace.LensesSettingsController
import com.riffle.app.launcher.workspace.SourcesSettingsController
import com.riffle.app.launcher.workspace.WorkspacesSettingsController
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Workspaces and Sources settings pages need from the shell, provided only while the Workspaces
 * (preview) switch is on so every other host of the settings surface (tests, screenshots) draws neither page.
 *
 * [version] changes whenever the stored workspaces do (an edit, a menu switch), so the page re-plans.
 * [currentLayout] is the layout this device is showing now: the only one the editor can open. [onEdit] opens
 * the existing editor through the menu's Edit route; [onRequestSourceAccess] is the existing explicit
 * user-initiated flow for a source (never called except from a tap). [exclusions] drives Settings > Hidden items
 * and rules (null where the runtime has no exclusion repository, which then shows the page as not loaded).
 * [icsFeeds] drives Settings > Calendar feeds (ICS) and the Sources page's feed section (null: neither is shown).
 * [lenses] drives Settings > Saved lenses (null: it shows as not available) and [previewServices] draws its live
 * preview through the same provider the containers use (null: the page says the preview is unavailable).
 * [globalSearchQuery] is the shared query holder the Search source page reports on (only whether one is set).
 */
@Suppress("LongParameterList") // One bundle of the preview runtime's settings controllers.
internal class WorkspaceSettingsHost(
    val workspaces: WorkspacesSettingsController,
    val sources: SourcesSettingsController,
    val version: StateFlow<Int>,
    val currentLayout: HomeLayoutDeviceClass,
    val onEdit: (WorkspaceId) -> Unit,
    val onRequestSourceAccess: (SourceId) -> Unit,
    val exclusions: ExclusionsSettingsController? = null,
    val icsFeeds: IcsFeedsController? = null,
    val lenses: LensesSettingsController? = null,
    val previewServices: ((reducedMotion: Boolean) -> ContainerServices)? = null,
    val globalSearchQuery: SearchQueryHolder? = null,
)

internal val LocalWorkspaceSettingsHost = staticCompositionLocalOf<WorkspaceSettingsHost?> { null }

/** Where Settings pages announce things (with an optional Undo); null where no surface provides one. */
internal val LocalSettingsSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

@Composable
internal fun SettingsPreviewOffNote() {
    Text(
        modifier = Modifier.padding(horizontal = RiffleSpacing.s, vertical = RiffleSpacing.xl),
        text = WorkspacesSettingsText.PREVIEW_OFF,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
