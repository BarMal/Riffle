package com.riffle.app.launcher

import androidx.annotation.StringRes
import com.riffle.app.R

/**
 * Every individual setting, once. [page] is where it lives, [labelRes] its name and [keywordsRes] the words that find
 * it in search. A page row marks itself with `Modifier.settingAnchor(key)`; the search index is built from this list,
 * so a setting that is here is searchable and opens its page scrolled to it. Generated order follows the pages.
 */
enum class SettingKey(
    val page: SettingsPage,
    @StringRes val labelRes: Int,
    @StringRes val keywordsRes: Int,
) {
    SIMPLE_MODE(SettingsPage.HOME, R.string.setting_simple_mode, R.string.setting_simple_mode_keywords),
    HOME_MODE(SettingsPage.HOME, R.string.setting_home_mode, R.string.setting_home_mode_keywords),
    HOME_GRID_COLUMNS(
        SettingsPage.HOME,
        R.string.setting_home_grid_columns,
        R.string.setting_home_grid_columns_keywords,
    ),
    HOME_GRID_ROWS(SettingsPage.HOME, R.string.setting_home_grid_rows, R.string.setting_home_grid_rows_keywords),
    HOME_GRID_MARGIN_HORIZONTAL(
        SettingsPage.HOME,
        R.string.setting_home_grid_margin_horizontal,
        R.string.setting_home_grid_margin_horizontal_keywords,
    ),
    HOME_GRID_MARGIN_VERTICAL(
        SettingsPage.HOME,
        R.string.setting_home_grid_margin_vertical,
        R.string.setting_home_grid_margin_vertical_keywords,
    ),
    HOME_ICON_SIZE(SettingsPage.HOME, R.string.setting_home_icon_size, R.string.setting_home_icon_size_keywords),
    HOME_SHOW_LABELS(SettingsPage.HOME, R.string.setting_home_show_labels, R.string.setting_home_show_labels_keywords),
    HOME_LABEL_BACKGROUND(
        SettingsPage.HOME,
        R.string.setting_home_label_background,
        R.string.setting_home_label_background_keywords,
    ),
    HOME_LABEL_TEXT_SIZE(
        SettingsPage.HOME,
        R.string.setting_home_label_text_size,
        R.string.setting_home_label_text_size_keywords,
    ),
    HOME_LABEL_WIDTH(SettingsPage.HOME, R.string.setting_home_label_width, R.string.setting_home_label_width_keywords),
    HOME_LABEL_SIZING(
        SettingsPage.HOME,
        R.string.setting_home_label_sizing,
        R.string.setting_home_label_sizing_keywords,
    ),
    HOME_LABEL_LINES(SettingsPage.HOME, R.string.setting_home_label_lines, R.string.setting_home_label_lines_keywords),
    HOME_RETURNING(SettingsPage.HOME, R.string.setting_home_returning, R.string.setting_home_returning_keywords),
    HOME_WALLPAPER(SettingsPage.HOME, R.string.setting_home_wallpaper, R.string.setting_home_wallpaper_keywords),
    HOME_WALLPAPER_MOTION(
        SettingsPage.HOME,
        R.string.setting_home_wallpaper_motion,
        R.string.setting_home_wallpaper_motion_keywords,
    ),
    HOME_CHANGE_WALLPAPER(
        SettingsPage.HOME,
        R.string.setting_home_change_wallpaper,
        R.string.setting_home_change_wallpaper_keywords,
    ),
    HOME_HIDE_STATUS_BAR(
        SettingsPage.HOME,
        R.string.setting_home_hide_status_bar,
        R.string.setting_home_hide_status_bar_keywords,
    ),
    HOME_HIDE_NAVIGATION_BAR(
        SettingsPage.HOME,
        R.string.setting_home_hide_navigation_bar,
        R.string.setting_home_hide_navigation_bar_keywords,
    ),
    HOME_SET_UP_AGAIN(
        SettingsPage.HOME,
        R.string.setting_home_set_up_again,
        R.string.setting_home_set_up_again_keywords,
    ),
    DOCK_SHOW(SettingsPage.DOCK, R.string.setting_dock_show, R.string.setting_dock_show_keywords),
    DOCK_POSITION(SettingsPage.DOCK, R.string.setting_dock_position, R.string.setting_dock_position_keywords),
    DOCK_ICON_SIZE(SettingsPage.DOCK, R.string.setting_dock_icon_size, R.string.setting_dock_icon_size_keywords),
    DOCK_PINNED_ICONS(
        SettingsPage.DOCK,
        R.string.setting_dock_pinned_icons,
        R.string.setting_dock_pinned_icons_keywords,
    ),
    DOCK_WIDTH(SettingsPage.DOCK, R.string.setting_dock_width, R.string.setting_dock_width_keywords),
    DOCK_ALIGNMENT(SettingsPage.DOCK, R.string.setting_dock_alignment, R.string.setting_dock_alignment_keywords),
    DOCK_CORNER_RADIUS(
        SettingsPage.DOCK,
        R.string.setting_dock_corner_radius,
        R.string.setting_dock_corner_radius_keywords,
    ),
    DOCK_BACKGROUND(SettingsPage.DOCK, R.string.setting_dock_background, R.string.setting_dock_background_keywords),
    DOCK_GRID_SPACING(
        SettingsPage.DOCK,
        R.string.setting_dock_grid_spacing,
        R.string.setting_dock_grid_spacing_keywords,
    ),
    FEATURE_DOCK_LIVE_APPS(
        SettingsPage.DOCK,
        R.string.setting_feature_dock_live_apps,
        R.string.setting_feature_dock_live_apps_keywords,
    ),
    DOCK_LIVE_APPS(SettingsPage.DOCK, R.string.setting_dock_live_apps, R.string.setting_dock_live_apps_keywords),
    FLOATING_ENABLED(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_enabled,
        R.string.setting_floating_enabled_keywords,
    ),
    FLOATING_APPS(SettingsPage.FLOATING_DOCK, R.string.setting_floating_apps, R.string.setting_floating_apps_keywords),
    FLOATING_EDGE(SettingsPage.FLOATING_DOCK, R.string.setting_floating_edge, R.string.setting_floating_edge_keywords),
    FLOATING_HANDLE_THICKNESS(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_handle_thickness,
        R.string.setting_floating_handle_thickness_keywords,
    ),
    FLOATING_HANDLE_SIZE(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_handle_size,
        R.string.setting_floating_handle_size_keywords,
    ),
    FLOATING_HANDLE_POSITION(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_handle_position,
        R.string.setting_floating_handle_position_keywords,
    ),
    FLOATING_HANDLE_OPACITY(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_handle_opacity,
        R.string.setting_floating_handle_opacity_keywords,
    ),
    FLOATING_ICON_SIZE(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_icon_size,
        R.string.setting_floating_icon_size_keywords,
    ),
    FLOATING_ORIENTATION(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_orientation,
        R.string.setting_floating_orientation_keywords,
    ),
    FLOATING_LABELS(
        SettingsPage.FLOATING_DOCK,
        R.string.setting_floating_labels,
        R.string.setting_floating_labels_keywords,
    ),
    FEATURE_LIBRARY(
        SettingsPage.LIBRARY,
        R.string.setting_feature_library,
        R.string.setting_feature_library_keywords,
    ),
    LIBRARY_SHOW_AS(SettingsPage.LIBRARY, R.string.setting_library_show_as, R.string.setting_library_show_as_keywords),
    LIBRARY_GRID_COLUMNS(
        SettingsPage.LIBRARY,
        R.string.setting_library_grid_columns,
        R.string.setting_library_grid_columns_keywords,
    ),
    LIBRARY_SEARCH_RESULTS(
        SettingsPage.LIBRARY,
        R.string.setting_library_search_results,
        R.string.setting_library_search_results_keywords,
    ),
    LIBRARY_REFRESH_APPS(
        SettingsPage.LIBRARY,
        R.string.setting_library_refresh_apps,
        R.string.setting_library_refresh_apps_keywords,
    ),
    FEATURE_STREAM(
        SettingsPage.STREAM,
        R.string.setting_feature_stream,
        R.string.setting_feature_stream_keywords,
    ),
    STREAM_SECTION_NOW(
        SettingsPage.STREAM,
        R.string.setting_stream_section_now,
        R.string.setting_stream_section_now_keywords,
    ),
    STREAM_SECTION_NOTIFICATIONS(
        SettingsPage.STREAM,
        R.string.setting_stream_section_notifications,
        R.string.setting_stream_section_notifications_keywords,
    ),
    STREAM_SECTION_RECENTS(
        SettingsPage.STREAM,
        R.string.setting_stream_section_recents,
        R.string.setting_stream_section_recents_keywords,
    ),
    STREAM_SECTION_TODAY(
        SettingsPage.STREAM,
        R.string.setting_stream_section_today,
        R.string.setting_stream_section_today_keywords,
    ),
    STREAM_SECTION_FEEDS(
        SettingsPage.STREAM,
        R.string.setting_stream_section_feeds,
        R.string.setting_stream_section_feeds_keywords,
    ),
    NOTIFICATIONS_SOURCE(
        SettingsPage.STREAM_NOTIFICATIONS,
        R.string.setting_notifications_source,
        R.string.setting_notifications_source_keywords,
    ),
    AGENDA_CALENDAR(
        SettingsPage.STREAM_AGENDA,
        R.string.setting_agenda_calendar,
        R.string.setting_agenda_calendar_keywords,
    ),
    AGENDA_INCLUDE_FEEDS(
        SettingsPage.STREAM_AGENDA,
        R.string.setting_agenda_include_feeds,
        R.string.setting_agenda_include_feeds_keywords,
    ),
    CALENDAR_FEEDS_REFRESH(
        SettingsPage.CALENDAR_FEEDS,
        R.string.setting_calendar_feeds_refresh,
        R.string.setting_calendar_feeds_refresh_keywords,
    ),
    CALENDAR_FEEDS_ADD(
        SettingsPage.CALENDAR_FEEDS,
        R.string.setting_calendar_feeds_add,
        R.string.setting_calendar_feeds_add_keywords,
    ),
    TASKS_PROVIDER(
        SettingsPage.STREAM_TASKS,
        R.string.setting_tasks_provider,
        R.string.setting_tasks_provider_keywords,
    ),
    FEEDS_SOURCE(SettingsPage.STREAM_FEEDS, R.string.setting_feeds_source, R.string.setting_feeds_source_keywords),
    FEEDS_BACKGROUND_REFRESH(
        SettingsPage.STREAM_FEEDS,
        R.string.setting_feeds_background_refresh,
        R.string.setting_feeds_background_refresh_keywords,
    ),
    FEEDS_WIFI_ONLY(
        SettingsPage.STREAM_FEEDS,
        R.string.setting_feeds_wifi_only,
        R.string.setting_feeds_wifi_only_keywords,
    ),
    FEEDS_CHARGING_ONLY(
        SettingsPage.STREAM_FEEDS,
        R.string.setting_feeds_charging_only,
        R.string.setting_feeds_charging_only_keywords,
    ),
    FEEDS_LIST(SettingsPage.STREAM_FEEDS, R.string.setting_feeds_list, R.string.setting_feeds_list_keywords),
    FEEDS_ADD(SettingsPage.STREAM_FEEDS, R.string.setting_feeds_add, R.string.setting_feeds_add_keywords),
    APPEARANCE_COLOUR_MODE(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_colour_mode,
        R.string.setting_appearance_colour_mode_keywords,
    ),
    APPEARANCE_ACCENT(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_accent,
        R.string.setting_appearance_accent_keywords,
    ),
    APPEARANCE_CUSTOM_ACCENT(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_custom_accent,
        R.string.setting_appearance_custom_accent_keywords,
    ),
    APPEARANCE_SPOTLIGHT(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_spotlight,
        R.string.setting_appearance_spotlight_keywords,
    ),
    APPEARANCE_REDUCED_TRANSPARENCY(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_reduced_transparency,
        R.string.setting_appearance_reduced_transparency_keywords,
    ),
    APPEARANCE_CORNERS(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_corners,
        R.string.setting_appearance_corners_keywords,
    ),
    APPEARANCE_TYPOGRAPHY(
        SettingsPage.APPEARANCE,
        R.string.setting_appearance_typography,
        R.string.setting_appearance_typography_keywords,
    ),
    GESTURE_ONE_FINGER_UP(
        SettingsPage.GESTURES,
        R.string.setting_gesture_one_finger_up,
        R.string.setting_gesture_one_finger_up_keywords,
    ),
    GESTURE_ONE_FINGER_DOWN(
        SettingsPage.GESTURES,
        R.string.setting_gesture_one_finger_down,
        R.string.setting_gesture_one_finger_down_keywords,
    ),
    GESTURE_ONE_FINGER_LEFT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_one_finger_left,
        R.string.setting_gesture_one_finger_left_keywords,
    ),
    GESTURE_ONE_FINGER_RIGHT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_one_finger_right,
        R.string.setting_gesture_one_finger_right_keywords,
    ),
    GESTURE_TWO_FINGER_UP(
        SettingsPage.GESTURES,
        R.string.setting_gesture_two_finger_up,
        R.string.setting_gesture_two_finger_up_keywords,
    ),
    GESTURE_TWO_FINGER_DOWN(
        SettingsPage.GESTURES,
        R.string.setting_gesture_two_finger_down,
        R.string.setting_gesture_two_finger_down_keywords,
    ),
    GESTURE_TWO_FINGER_LEFT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_two_finger_left,
        R.string.setting_gesture_two_finger_left_keywords,
    ),
    GESTURE_TWO_FINGER_RIGHT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_two_finger_right,
        R.string.setting_gesture_two_finger_right_keywords,
    ),
    GESTURE_THREE_FINGER_UP(
        SettingsPage.GESTURES,
        R.string.setting_gesture_three_finger_up,
        R.string.setting_gesture_three_finger_up_keywords,
    ),
    GESTURE_THREE_FINGER_DOWN(
        SettingsPage.GESTURES,
        R.string.setting_gesture_three_finger_down,
        R.string.setting_gesture_three_finger_down_keywords,
    ),
    GESTURE_THREE_FINGER_LEFT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_three_finger_left,
        R.string.setting_gesture_three_finger_left_keywords,
    ),
    GESTURE_THREE_FINGER_RIGHT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_three_finger_right,
        R.string.setting_gesture_three_finger_right_keywords,
    ),
    GESTURE_PINCH_IN(
        SettingsPage.GESTURES,
        R.string.setting_gesture_pinch_in,
        R.string.setting_gesture_pinch_in_keywords,
    ),
    GESTURE_PINCH_OUT(
        SettingsPage.GESTURES,
        R.string.setting_gesture_pinch_out,
        R.string.setting_gesture_pinch_out_keywords,
    ),
    MOTION_PERFORMANCE(
        SettingsPage.MOTION,
        R.string.setting_motion_performance,
        R.string.setting_motion_performance_keywords,
    ),
    MOTION_REDUCED(SettingsPage.MOTION, R.string.setting_motion_reduced, R.string.setting_motion_reduced_keywords),
    MOTION_HAPTICS(SettingsPage.MOTION, R.string.setting_motion_haptics, R.string.setting_motion_haptics_keywords),
    MOTION_SPEED(SettingsPage.MOTION, R.string.setting_motion_speed, R.string.setting_motion_speed_keywords),
    MOTION_TRAMPOLINE(
        SettingsPage.MOTION,
        R.string.setting_motion_trampoline,
        R.string.setting_motion_trampoline_keywords,
    ),
    MOTION_INERTIA(SettingsPage.MOTION, R.string.setting_motion_inertia, R.string.setting_motion_inertia_keywords),
    MOTION_MAGNET(SettingsPage.MOTION, R.string.setting_motion_magnet, R.string.setting_motion_magnet_keywords),
    MOTION_HOLD(SettingsPage.MOTION, R.string.setting_motion_hold, R.string.setting_motion_hold_keywords),
    MOTION_TICKS(SettingsPage.MOTION, R.string.setting_motion_ticks, R.string.setting_motion_ticks_keywords),
    STREAM_CARD_TINT(
        SettingsPage.STREAM_CARDS,
        R.string.setting_stream_card_tint,
        R.string.setting_stream_card_tint_keywords,
    ),
    STREAM_CARD_GLOW(
        SettingsPage.STREAM_CARDS,
        R.string.setting_stream_card_glow,
        R.string.setting_stream_card_glow_keywords,
    ),
    STREAM_OPEN_CARD_POSITION(
        SettingsPage.STREAM_CARDS,
        R.string.setting_stream_open_card_position,
        R.string.setting_stream_open_card_position_keywords,
    ),
    STREAM_ACTIONS_POSITION(
        SettingsPage.STREAM_CARDS,
        R.string.setting_stream_actions_position,
        R.string.setting_stream_actions_position_keywords,
    ),
    ACCESS_DEFAULT_HOME(
        SettingsPage.PRIVACY,
        R.string.setting_access_default_home,
        R.string.setting_access_default_home_keywords,
    ),
    ACCESS_NOTIFICATIONS(
        SettingsPage.PRIVACY,
        R.string.setting_access_notifications,
        R.string.setting_access_notifications_keywords,
    ),
    ACCESS_USAGE(SettingsPage.PRIVACY, R.string.setting_access_usage, R.string.setting_access_usage_keywords),
    ACCESS_CALENDAR(SettingsPage.PRIVACY, R.string.setting_access_calendar, R.string.setting_access_calendar_keywords),
    ACCESS_FLOATING_DOCK(
        SettingsPage.PRIVACY,
        R.string.setting_access_floating_dock,
        R.string.setting_access_floating_dock_keywords,
    ),
    PRIVACY_SCREEN_PROTECTION(
        SettingsPage.PRIVACY,
        R.string.setting_privacy_screen_protection,
        R.string.setting_privacy_screen_protection_keywords,
    ),
    PRIVACY_CONTENT_LEVEL(
        SettingsPage.PRIVACY,
        R.string.setting_privacy_content_level,
        R.string.setting_privacy_content_level_keywords,
    ),
    BACKUP_EXPORT(SettingsPage.BACKUP, R.string.setting_backup_export, R.string.setting_backup_export_keywords),
    BACKUP_IMPORT(SettingsPage.BACKUP, R.string.setting_backup_import, R.string.setting_backup_import_keywords),
    LAYOUT_FOLDED_DOCK_EDGE(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_folded_dock_edge,
        R.string.setting_layout_folded_dock_edge_keywords,
    ),
    LAYOUT_FOLDED_PAGE_SCROLL(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_folded_page_scroll,
        R.string.setting_layout_folded_page_scroll_keywords,
    ),
    LAYOUT_FOLDED_INDICATOR(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_folded_indicator,
        R.string.setting_layout_folded_indicator_keywords,
    ),
    LAYOUT_FOLDED_QUICK_SCROLL(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_folded_quick_scroll,
        R.string.setting_layout_folded_quick_scroll_keywords,
    ),
    LAYOUT_UNFOLDED_DOCK_EDGE(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_unfolded_dock_edge,
        R.string.setting_layout_unfolded_dock_edge_keywords,
    ),
    LAYOUT_UNFOLDED_PAGE_SCROLL(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_unfolded_page_scroll,
        R.string.setting_layout_unfolded_page_scroll_keywords,
    ),
    LAYOUT_UNFOLDED_INDICATOR(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_unfolded_indicator,
        R.string.setting_layout_unfolded_indicator_keywords,
    ),
    LAYOUT_UNFOLDED_QUICK_SCROLL(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_unfolded_quick_scroll,
        R.string.setting_layout_unfolded_quick_scroll_keywords,
    ),
    LAYOUT_UNFOLDED_APP_PEEK_THROUGH(
        SettingsPage.POSTURE_LAYOUT,
        R.string.setting_layout_unfolded_peek_through,
        R.string.setting_layout_unfolded_peek_through_keywords,
    ),
}

/** The name this setting is shown under, the same string the search index uses. */
@androidx.compose.runtime.Composable
internal fun SettingKey.label(): String = androidx.compose.ui.res.stringResource(labelRes)
