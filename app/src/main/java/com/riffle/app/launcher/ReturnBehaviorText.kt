package com.riffle.app.launcher

import com.riffle.core.domain.launcher.workspace.ReturnBehavior

/** User-visible wording of the Return setting. */
internal object ReturnBehaviorText {
    const val TITLE = "Returning to Home"
    const val RESTORE = "Restore"
    const val RESTORE_SUMMARY = "Back to the page you were on"
    const val FIRST_PAGE = "First page"
    const val FIRST_PAGE_SUMMARY = "Always the first page, never the Finder"
    const val START_PAGE = "Start page"
    const val START_PAGE_SUMMARY = "The page set as the workspace's start page"
    const val SELECTED = "Selected"

    fun label(behavior: ReturnBehavior): String =
        when (behavior) {
            ReturnBehavior.RESTORE -> RESTORE
            ReturnBehavior.FIRST_PAGE -> FIRST_PAGE
            ReturnBehavior.START_PAGE -> START_PAGE
        }

    fun summary(behavior: ReturnBehavior): String =
        when (behavior) {
            ReturnBehavior.RESTORE -> RESTORE_SUMMARY
            ReturnBehavior.FIRST_PAGE -> FIRST_PAGE_SUMMARY
            ReturnBehavior.START_PAGE -> START_PAGE_SUMMARY
        }
}
