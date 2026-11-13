package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.onboarding.OnboardingStep
import com.riffle.core.domain.launcher.onboarding.SourcePermission

/** Wording for the four-step set-up. Plain strings so JVM tests can check them. */
internal object OnboardingText {
    const val SETTINGS_SECTION = "Set up"
    const val SETTINGS_ROW = "Set up Riffle again"
    const val SETTINGS_ROW_SUBTITLE =
        "A short tour of Home, the Dock, the Library and the Stream. Changes nothing you placed."
    const val NEXT = "Next"
    const val BACK = "Back"
    const val SKIP = "Skip set-up"
    const val DONE = "Done"
    const val NOT_NOW = "Not now"
    const val ALLOW = "Allow"
    const val ALLOWED = "Allowed"
    const val SKIPPED = "Skipped. The section shows Allow access until you allow it."
    const val STREAM_PERMISSIONS_HEADING = "Optional access"

    fun progress(
        step: OnboardingStep,
        count: Int = OnboardingStep.entries.size,
    ): String = "Step ${step.ordinal + 1} of $count"

    fun title(step: OnboardingStep): String =
        when (step) {
            OnboardingStep.HOME -> "Home"
            OnboardingStep.DOCK -> "The Dock"
            OnboardingStep.LIBRARY -> "The Library"
            OnboardingStep.STREAM -> "The Stream"
        }

    fun body(step: OnboardingStep): String =
        when (step) {
            OnboardingStep.HOME ->
                "Your apps, folders and widgets, placed by hand. Press and hold to arrange them, and swipe " +
                    "between pages."
            OnboardingStep.DOCK ->
                "Always visible. Pin favourites, and see apps with something new beside them. Pull up for the " +
                    "Library; press the Dock down and let go for the Stream, and again to come back."
            OnboardingStep.LIBRARY ->
                "Every installed app, sorted for you, with search at the top. Pull up on the Dock's pinned " +
                    "side, or swipe down on Home."
            OnboardingStep.STREAM ->
                "Now playing, notifications, recent apps, your agenda and feeds, as stacks of cards or lists. " +
                    "Each section can ask for access; every one is optional."
        }

    fun permissionTitle(permission: SourcePermission): String =
        when (permission) {
            SourcePermission.NOTIFICATION_ACCESS -> "Notifications and now playing"
            SourcePermission.CALENDAR -> "Agenda"
            SourcePermission.USAGE_ACCESS -> "Recent apps"
        }

    fun permissionExplanation(permission: SourcePermission): String =
        when (permission) {
            SourcePermission.NOTIFICATION_ACCESS ->
                "Riffle reads notifications and media controls on this device only, and never stores or sends them."
            SourcePermission.CALENDAR ->
                "Riffle reads your calendar events on this device only, to show what is next."
            SourcePermission.USAGE_ACCESS ->
                "Riffle reads which apps you used lately, on this device only, to show them as Recents."
        }
}
