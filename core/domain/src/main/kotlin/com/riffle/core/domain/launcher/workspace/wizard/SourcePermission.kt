package com.riffle.core.domain.launcher.workspace.wizard

import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/**
 * The platform access a content goal can need. One value per explicit, user-initiated flow the app already has
 * (`SourceAccessRoute` in the app maps the same sources to the same flows); the domain never requests anything.
 */
enum class SourcePermission {
    /** Notification listener access: notifications and media controls. */
    NOTIFICATION_ACCESS,

    /** Calendar read permission. */
    CALENDAR,

    /** Usage access: recent apps. */
    USAGE_ACCESS,
    ;

    companion object {
        /** The permission [id] needs, or null when it needs none (apps, shortcuts, RSS, ICS, search, home items). */
        fun forSource(id: SourceId): SourcePermission? =
            when (id) {
                SourceIds.NOTIFICATIONS, SourceIds.MEDIA -> NOTIFICATION_ACCESS
                SourceIds.CALENDAR -> CALENDAR
                SourceIds.RECENT_APPS -> USAGE_ACCESS
                else -> null
            }

        /**
         * What [permission] currently allows, from the per-source [access] map the editor already uses
         * (`SourceChoice.access`): the weakest access of the sources that need it. Granted when none is listed.
         */
        fun accessOf(
            permission: SourcePermission,
            access: Map<SourceId, SourceAccess>,
        ): SourceAccess {
            val values = access.filterKeys { forSource(it) == permission }.values
            return when {
                SourceAccess.REQUIRED in values -> SourceAccess.REQUIRED
                values.isNotEmpty() && values.all { it == SourceAccess.UNAVAILABLE } -> SourceAccess.UNAVAILABLE
                else -> SourceAccess.GRANTED
            }
        }
    }
}
