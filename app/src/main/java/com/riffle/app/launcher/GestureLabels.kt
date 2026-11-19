package com.riffle.app.launcher

import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.GestureSettings
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureSurface
import com.riffle.core.domain.launcher.settings.toHomeGesture

/** The words for a Home gesture and for the action bound to it, read from string resources. */
internal fun LauncherGestureAction.label(text: SettingsText): String =
    when (this) {
        LauncherGestureAction.NONE -> text.text(R.string.gesture_action_none)
        LauncherGestureAction.OPEN_SEARCH -> text.text(R.string.gesture_action_open_search)
        LauncherGestureAction.OPEN_SETTINGS -> text.text(R.string.gesture_action_open_settings)
        LauncherGestureAction.ENTER_HOME_EDIT_MODE -> text.text(R.string.gesture_action_enter_home_edit_mode)
        LauncherGestureAction.ENTER_HOME_PAGE_OVERVIEW -> text.text(R.string.gesture_action_enter_home_page_overview)
        LauncherGestureAction.ENTER_FULLSCREEN_HOME -> text.text(R.string.gesture_action_enter_fullscreen_home)
        LauncherGestureAction.SELECT_NEXT_HOME_PAGE -> text.text(R.string.gesture_action_select_next_home_page)
        LauncherGestureAction.SELECT_PREVIOUS_HOME_PAGE -> text.text(R.string.gesture_action_select_previous_home_page)
        LauncherGestureAction.LAUNCH_APP -> text.text(R.string.gesture_action_launch_app)
        LauncherGestureAction.LAUNCH_APP_SHORTCUT -> text.text(R.string.gesture_action_launch_app_shortcut)
        LauncherGestureAction.OPEN_STREAM -> text.text(R.string.gesture_action_open_stream)
        LauncherGestureAction.OPEN_LIBRARY -> text.text(R.string.gesture_action_open_library)
    }

internal fun HomeGesture.label(text: SettingsText): String = text.text(settingKey().labelRes)

internal fun homeGestureConflictSummary(
    settings: GestureSettings,
    text: SettingsText,
): String? =
    settings.conflicts
        .filter { conflict -> conflict.surface == LauncherGestureSurface.HOME_PAGE }
        .takeIf { conflicts -> conflicts.isNotEmpty() }
        ?.joinToString(separator = "\n", prefix = text.text(R.string.gesture_conflicts_prefix)) { conflict ->
            "${conflict.action.label(text)}: " +
                conflict.gestures.joinToString { gesture -> gesture.toHomeGesture().label(text) }
        }
