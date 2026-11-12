package com.riffle.app.launcher

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.exclusions.ExclusionsSettingsController
import com.riffle.app.launcher.ics.IcsFeedsController
import com.riffle.app.launcher.workspace.SourceContentLevelsController
import com.riffle.app.launcher.workspace.SourcesSettingsController
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.sources.SearchQueryHolder
import kotlinx.coroutines.flow.StateFlow

/**
 * What the Sources, Privacy, Hidden items and Calendar feeds pages need from the shell, provided by the shell
 * whenever it hosts the sources (always in the app), so other hosts of the settings surface (tests, screenshots) draw
 * none of them.
 *
 * [version] changes whenever the stored workspaces do. [onRequestSourceAccess] is the existing explicit
 * user-initiated flow for a source (never called except from a tap). [exclusions] drives Settings > Hidden items
 * and rules (null where the runtime has no exclusion repository, which then shows the page as not loaded).
 * [icsFeeds] drives Settings > Calendar feeds (ICS) and the Sources page's feed section (null: neither is shown).
 * [globalSearchQuery] is the shared query holder the Search source page reports on (only whether one is set).
 * [contentLevels] drives the content-level controls of Settings > Privacy and the source pages (null: not shown).
 */
@Suppress("LongParameterList") // One bundle of the sources runtime's settings controllers.
internal class WorkspaceSettingsHost(
    val sources: SourcesSettingsController,
    val version: StateFlow<Int>,
    val currentLayout: HomeLayoutDeviceClass,
    val onRequestSourceAccess: (SourceId) -> Unit,
    val exclusions: ExclusionsSettingsController? = null,
    val icsFeeds: IcsFeedsController? = null,
    val globalSearchQuery: SearchQueryHolder? = null,
    val contentLevels: SourceContentLevelsController? = null,
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
