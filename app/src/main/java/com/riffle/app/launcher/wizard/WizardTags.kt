package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission

// Test tags of the content wizard (UI and screenshot tests; never shown or read out).

internal const val WIZARD_TAG = "content-wizard"
internal const val WIZARD_NEXT_TAG = "wizard-next"
internal const val WIZARD_BACK_TAG = "wizard-back"
internal const val WIZARD_CLOSE_TAG = "wizard-close"
internal const val WIZARD_ADVANCED_TAG = "wizard-advanced"
internal const val WIZARD_PROBLEM_TAG = "wizard-problem"
internal const val WIZARD_NOTICE_TAG = "wizard-notice"
internal const val WIZARD_PREVIEW_TAG = "wizard-preview"

internal const val WIZARD_QUERY_FIELD_TAG = "wizard-query"
internal const val WIZARD_APP_FILTER_TAG = "wizard-app-filter"

internal const val WIZARD_SAVE_SWITCH_TAG = "wizard-save-switch"
internal const val WIZARD_NAME_TAG = "wizard-name"

internal fun wizardFilterTag(id: String): String = "wizard-filter-$id"

internal fun wizardLookTag(id: String): String = "wizard-look-$id"

internal fun wizardAppTag(packageName: String): String = "wizard-app-$packageName"

internal fun wizardFeedTag(key: String): String = "wizard-feed-$key"

internal fun wizardGoalTag(key: String): String = "wizard-goal-$key"

internal fun wizardDestinationTag(
    destination: Destination,
    index: Int = 0,
): String = "wizard-destination-${destination.name.lowercase()}-$index"

internal fun wizardReviewAccessTag(permission: SourcePermission): String =
    "wizard-review-access-${permission.name.lowercase()}"
