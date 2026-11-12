package com.riffle.app.launcher

import com.riffle.app.launcher.workspace.SourceAccessRoute
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus

/** Shared wording for the layout names and the "not loaded yet" note of the Sources, Privacy and Hidden items pages. */
internal object WorkspacesSettingsText {
    const val PREVIEW_OFF = "This page is not loaded yet. Go back and open it again."

    /** A landscape phone is the phone layout for every stored value (N10), so it is named as one. */
    fun layoutName(layout: HomeLayoutDeviceClass): String =
        when (layout) {
            HomeLayoutDeviceClass.PHONE, HomeLayoutDeviceClass.PHONE_LANDSCAPE -> "Phone (folded)"
            HomeLayoutDeviceClass.FOLDABLE -> "Foldable (unfolded)"
            HomeLayoutDeviceClass.TABLET -> "Tablet"
            HomeLayoutDeviceClass.DESKTOP -> "Desktop"
        }
}

/** Wording for the Sources page. */
internal object SourcesSettingsText {
    const val TITLE = "Sources"
    const val SOURCES_INTRO =
        "Turn a source off to stop Riffle reading it. Pages that use it then say it is turned off, with a " +
            "button to turn it back on. Nothing is deleted."
    const val ALLOW = "Allow"

    /** The label of the Allow button for a source's explicit access flow; null when it has none. */
    fun allowLabel(
        route: SourceAccessRoute,
        calendar: CalendarAccessStatus,
    ): String? =
        when (route) {
            SourceAccessRoute.CALENDAR -> calendar.calendarAccessActionLabel() ?: ALLOW
            SourceAccessRoute.NOTIFICATION_ACCESS -> "Allow notification access"
            SourceAccessRoute.USAGE_ACCESS -> "Open usage access settings"
            SourceAccessRoute.NONE -> null
        }

    /** Why access is asked for, shown next to the button so the reason is read before anything can appear. */
    fun rationale(
        route: SourceAccessRoute,
        calendar: CalendarAccessStatus,
    ): String? =
        when (route) {
            SourceAccessRoute.CALENDAR -> calendar.calendarAccessSettingsLabel()
            SourceAccessRoute.NOTIFICATION_ACCESS ->
                "Riffle reads notifications on this device only and never stores or sends them. This opens " +
                    "Android's notification access settings."
            SourceAccessRoute.USAGE_ACCESS ->
                "Riffle reads which apps you used lately, on this device only. This opens Android's usage " +
                    "access settings."
            SourceAccessRoute.NONE -> null
        }

    fun statusDescription(
        title: String,
        status: SourceStatus,
    ): String = "$title, ${status.label}"
}
