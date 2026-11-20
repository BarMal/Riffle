package com.riffle.app.launcher.stream

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.core.domain.launcher.onboarding.PermissionGain
import com.riffle.core.domain.launcher.workspace.SourceId

/** The one sentence that says what allowing a section's access gains: the same pattern for every section. */
@StringRes
internal fun PermissionGain.messageRes(): Int =
    when (this) {
        PermissionGain.NOTIFICATIONS -> R.string.stream_permission_notifications
        PermissionGain.MEDIA -> R.string.stream_permission_media
        PermissionGain.RECENT_APPS -> R.string.stream_permission_recents
        PermissionGain.CALENDAR -> R.string.stream_permission_calendar
        PermissionGain.CONTACTS -> R.string.search_permission_contacts
        PermissionGain.FILES -> R.string.search_permission_files
        PermissionGain.GENERIC -> R.string.stream_permission_message
    }

/** What a section waiting for [sources] says: the sentence, and the label of its one inline action. */
@Composable
internal fun permissionMessage(sources: List<SourceId>): String =
    stringResource(PermissionGain.of(sources).messageRes())

/**
 * A section that would show something but lacks its permission: one sentence on what the person gains and an inline
 * "Allow" button, the same wherever one stands (a page's empty state, a Today notice, a Glance widget). Nothing is
 * asked at launch: [onAllow] runs only from the tap, and starts the flow that already exists for the source.
 */
@Composable
internal fun StreamPermissionPrompt(
    sources: List<SourceId>,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StreamStateMessage(
        message = permissionMessage(sources),
        modifier = modifier,
        actionLabel = stringResource(R.string.stream_permission_action),
        onAction = onAllow,
    )
}
