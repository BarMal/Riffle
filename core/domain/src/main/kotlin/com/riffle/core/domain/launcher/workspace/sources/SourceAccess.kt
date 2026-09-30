package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceState

/**
 * What a platform gate currently allows a source to read. Checking it never prompts: a source only
 * reports [REQUIRED] and leaves any request flow to an explicit, user-initiated settings action.
 */
enum class SourceAccess {
    GRANTED,
    REQUIRED,
    UNAVAILABLE,
}

/** Reads [items] only when access is [SourceAccess.GRANTED], so gated data is never even queried otherwise. */
fun sourceStateFor(
    access: SourceAccess,
    items: () -> List<Item>,
): SourceState =
    when (access) {
        SourceAccess.GRANTED -> SourceState.Ready(items())
        SourceAccess.REQUIRED -> SourceState.PermissionRequired
        SourceAccess.UNAVAILABLE -> SourceState.Unavailable
    }

/** An unknown status reads as [SourceAccess.REQUIRED]: no content is shown until access is confirmed. */
fun NotificationAccessStatus.toSourceAccess(): SourceAccess =
    when (this) {
        NotificationAccessStatus.GRANTED -> SourceAccess.GRANTED
        NotificationAccessStatus.UNKNOWN,
        NotificationAccessStatus.NOT_GRANTED,
        NotificationAccessStatus.REVOKED,
        -> SourceAccess.REQUIRED
    }
