package com.riffle.core.domain.launcher.onboarding

import com.riffle.core.domain.launcher.stream.StreamSourceIds
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds

/**
 * The platform access the Stream's sections can need, one value per explicit, user-initiated flow the app already
 * has. [source] is a source that needs it (the app maps it to the existing flow); the domain never requests anything.
 */
enum class SourcePermission(val source: SourceId) {
    /** Notification listener access: notifications and media controls. */
    NOTIFICATION_ACCESS(SourceIds.NOTIFICATIONS),

    /** Calendar read permission: the agenda. */
    CALENDAR(SourceIds.CALENDAR),

    /** Usage access: recent apps. */
    USAGE_ACCESS(SourceIds.RECENT_APPS),
}

/**
 * What the person gains by allowing the access a Stream section is waiting for, so its empty state can say it in one
 * sentence next to its "Allow" button (the same wording pattern wherever one shows). [GENERIC] is for a source this
 * does not know: the sentence then stays general.
 */
enum class PermissionGain {
    /** Notification access: the notifications section. */
    NOTIFICATIONS,

    /** Notification access: what is playing, with its controls. */
    MEDIA,

    /** Usage access: the apps used lately. */
    RECENT_APPS,

    /** Calendar access: today's events. */
    CALENDAR,
    GENERIC,
    ;

    companion object {
        /** The gain of the first of [sources] this knows, or [GENERIC]. */
        fun of(sources: List<SourceId>): PermissionGain =
            sources.firstNotNullOfOrNull { source -> forSource(source) } ?: GENERIC

        private fun forSource(source: SourceId): PermissionGain? =
            when (source) {
                SourceIds.NOTIFICATIONS -> NOTIFICATIONS
                SourceIds.MEDIA -> MEDIA
                SourceIds.RECENT_APPS -> RECENT_APPS
                SourceIds.CALENDAR, StreamSourceIds.AGENDA -> CALENDAR
                else -> null
            }
    }
}
