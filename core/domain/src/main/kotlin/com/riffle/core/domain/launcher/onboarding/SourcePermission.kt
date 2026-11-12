package com.riffle.core.domain.launcher.onboarding

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
