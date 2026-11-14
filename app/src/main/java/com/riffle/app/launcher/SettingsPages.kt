@file:Suppress("MaxLineLength")

package com.riffle.app.launcher

import androidx.annotation.StringRes
import com.riffle.app.R
import com.riffle.core.domain.launcher.HomeRoleStatus
import com.riffle.core.domain.launcher.OverlayDockPermissionStatus
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus

/**
 * Every page of Settings, as a tree (docs/product/settings.md). [parent] is where Back leads and where the page is
 * listed; [MAIN] is the root. Titles, summaries and search keywords are string resources.
 */
enum class SettingsPage(
    val parent: SettingsPage?,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    @StringRes val keywordsRes: Int,
) {
    MAIN(null, R.string.settings_page_main, R.string.settings_page_main_summary, R.string.settings_page_main_keywords),
    HOME(MAIN, R.string.settings_page_home, R.string.settings_page_home_summary, R.string.settings_page_home_keywords),
    DOCK(MAIN, R.string.settings_page_dock, R.string.settings_page_dock_summary, R.string.settings_page_dock_keywords),
    FLOATING_DOCK(
        DOCK,
        R.string.settings_page_floating_dock,
        R.string.settings_page_floating_dock_summary,
        R.string.settings_page_floating_dock_keywords,
    ),
    LIBRARY(
        MAIN,
        R.string.settings_page_library,
        R.string.settings_page_library_summary,
        R.string.settings_page_library_keywords,
    ),
    HIDDEN_APPS(
        LIBRARY,
        R.string.settings_page_hidden_apps,
        R.string.settings_page_hidden_apps_summary,
        R.string.settings_page_hidden_apps_keywords,
    ),
    STREAM(
        MAIN,
        R.string.settings_page_stream,
        R.string.settings_page_stream_summary,
        R.string.settings_page_stream_keywords,
    ),
    STREAM_CARDS(
        STREAM,
        R.string.settings_page_stream_cards,
        R.string.settings_page_stream_cards_summary,
        R.string.settings_page_stream_cards_keywords,
    ),
    STREAM_NOTIFICATIONS(
        STREAM,
        R.string.settings_page_stream_notifications,
        R.string.settings_page_stream_notifications_summary,
        R.string.settings_page_stream_notifications_keywords,
    ),
    HIDDEN_ITEMS(
        STREAM_NOTIFICATIONS,
        R.string.settings_page_hidden_items,
        R.string.settings_page_hidden_items_summary,
        R.string.settings_page_hidden_items_keywords,
    ),
    STREAM_AGENDA(
        STREAM,
        R.string.settings_page_stream_agenda,
        R.string.settings_page_stream_agenda_summary,
        R.string.settings_page_stream_agenda_keywords,
    ),
    CALENDAR_FEEDS(
        STREAM_AGENDA,
        R.string.settings_page_calendar_feeds,
        R.string.settings_page_calendar_feeds_summary,
        R.string.settings_page_calendar_feeds_keywords,
    ),
    STREAM_TASKS(
        STREAM,
        R.string.settings_page_stream_tasks,
        R.string.settings_page_stream_tasks_summary,
        R.string.settings_page_stream_tasks_keywords,
    ),
    STREAM_FEEDS(
        STREAM,
        R.string.settings_page_stream_feeds,
        R.string.settings_page_stream_feeds_summary,
        R.string.settings_page_stream_feeds_keywords,
    ),
    APPEARANCE(
        MAIN,
        R.string.settings_page_appearance,
        R.string.settings_page_appearance_summary,
        R.string.settings_page_appearance_keywords,
    ),
    GESTURES(
        MAIN,
        R.string.settings_page_gestures,
        R.string.settings_page_gestures_summary,
        R.string.settings_page_gestures_keywords,
    ),
    MOTION(
        MAIN,
        R.string.settings_page_motion,
        R.string.settings_page_motion_summary,
        R.string.settings_page_motion_keywords,
    ),
    PRIVACY(
        MAIN,
        R.string.settings_page_privacy,
        R.string.settings_page_privacy_summary,
        R.string.settings_page_privacy_keywords,
    ),
    BACKUP(
        MAIN,
        R.string.settings_page_backup,
        R.string.settings_page_backup_summary,
        R.string.settings_page_backup_keywords,
    ),
    VERSION(
        MAIN,
        R.string.settings_page_version,
        R.string.settings_page_version_summary,
        R.string.settings_page_version_keywords,
    ),
    APP_PUBLISH(
        VERSION,
        R.string.settings_page_app_publish,
        R.string.settings_page_app_publish_summary,
        R.string.settings_page_app_publish_keywords,
    ),
}

/** The rows of the main page, in order: the ten places Settings is organised by. */
internal val settingsMainPageRows: List<SettingsPage> =
    listOf(
        SettingsPage.HOME,
        SettingsPage.DOCK,
        SettingsPage.LIBRARY,
        SettingsPage.STREAM,
        SettingsPage.APPEARANCE,
        SettingsPage.GESTURES,
        SettingsPage.MOTION,
        SettingsPage.PRIVACY,
        SettingsPage.BACKUP,
        SettingsPage.VERSION,
    )

/** Where Back leads from [page]: always its parent in the tree; the main page is its own root. */
internal fun settingsBackTarget(page: SettingsPage): SettingsPage = page.parent ?: SettingsPage.MAIN

/** [page] and every page above it, nearest first, without the main page. */
internal fun SettingsPage.ancestry(): List<SettingsPage> =
    generateSequence(this) { it.parent }.filter { it != SettingsPage.MAIN }.toList()

/** The sub-pages listed on [page] (its children in the tree), in declaration order. */
internal fun SettingsPage.children(): List<SettingsPage> = SettingsPage.entries.filter { it.parent == this }

internal data class SettingsOverviewStatus(
    val homeRoleStatus: HomeRoleStatus = HomeRoleStatus.UNKNOWN,
    val notificationAccessStatus: NotificationAccessStatus = NotificationAccessStatus.UNKNOWN,
    val overlayDockPermissionStatus: OverlayDockPermissionStatus = OverlayDockPermissionStatus.UNKNOWN,
    val hiddenAppCount: Int = 0,
)

internal fun SettingsSurfaceState.settingsOverviewStatus(): SettingsOverviewStatus =
    SettingsOverviewStatus(
        homeRoleStatus = homeRoleStatus,
        notificationAccessStatus = notificationAccessStatus,
        overlayDockPermissionStatus = overlayDockPermissionStatus,
        hiddenAppCount = hiddenApps.size,
    )

internal fun settingsSearchSummaryText(
    text: SettingsText,
    query: String,
    resultCount: Int,
): String? =
    query
        .trim()
        .takeIf(String::isNotEmpty)
        ?.let { trimmedQuery ->
            if (resultCount == 1) {
                text.text(R.string.settings_search_summary_one, trimmedQuery)
            } else {
                text.text(R.string.settings_search_summary_many, resultCount, trimmedQuery)
            }
        }

internal fun settingsLayoutPageTabs(availableDeviceClasses: Set<HomeLayoutDeviceClass>): List<SettingsLayoutDeviceTab> =
    settingsLayoutDeviceTabs(availableDeviceClasses)
