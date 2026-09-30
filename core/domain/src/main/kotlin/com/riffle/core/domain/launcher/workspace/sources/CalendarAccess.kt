package com.riffle.core.domain.launcher.workspace.sources

/**
 * Where calendar (`READ_CALENDAR`) access stands. Deriving it never prompts: the launcher only asks when
 * the user explicitly acts (see [calendarAccessStep]).
 */
enum class CalendarAccessStatus {
    /** The platform could not be asked; treated as not granted and never as a reason to read data. */
    UNKNOWN,
    GRANTED,

    /** Not granted, and the system dialog can still be shown (never asked, or denied once). */
    NOT_GRANTED,

    /** Denied with "don't ask again" (or twice): the system dialog no longer appears, only app settings help. */
    DENIED_PERMANENTLY,
}

/**
 * Derives the status from platform facts.
 *
 * [hasBeenDenied] is the launcher's own record that a request of ours came back denied. Without it a
 * never-asked permission and a permanently denied one look identical to the platform
 * (`shouldShowRequestPermissionRationale` is false in both), so the record is what breaks request loops.
 */
fun calendarAccessStatus(
    granted: Boolean,
    shouldShowSystemRationale: Boolean,
    hasBeenDenied: Boolean,
): CalendarAccessStatus =
    when {
        granted -> CalendarAccessStatus.GRANTED
        shouldShowSystemRationale -> CalendarAccessStatus.NOT_GRANTED
        hasBeenDenied -> CalendarAccessStatus.DENIED_PERMANENTLY
        else -> CalendarAccessStatus.NOT_GRANTED
    }

/** Anything but a confirmed grant reads as [SourceAccess.REQUIRED]: no calendar data is queried. */
fun CalendarAccessStatus.toSourceAccess(): SourceAccess =
    when (this) {
        CalendarAccessStatus.GRANTED -> SourceAccess.GRANTED
        CalendarAccessStatus.UNKNOWN,
        CalendarAccessStatus.NOT_GRANTED,
        CalendarAccessStatus.DENIED_PERMANENTLY,
        -> SourceAccess.REQUIRED
    }

/** What an explicit "allow calendar access" action should do next. */
enum class CalendarAccessStep {
    /** Already granted; nothing to do. */
    NONE,

    /** Explain what calendar access is used for before anything else happens. */
    SHOW_RATIONALE,

    /** Show the system permission dialog. */
    REQUEST_PERMISSION,

    /** The dialog can no longer appear; send the user to the app's system settings. */
    OPEN_APP_SETTINGS,
}

/**
 * Decides the next step for a user-initiated request. [rationaleVisible] says the user has already been
 * shown why access is wanted (the Settings row always shows it; a surface that only has a button must
 * show the rationale first). Never returns [CalendarAccessStep.REQUEST_PERMISSION] for a permanent denial,
 * so a denied user is never looped through a dialog that cannot appear.
 */
fun calendarAccessStep(
    status: CalendarAccessStatus,
    rationaleVisible: Boolean,
): CalendarAccessStep =
    when (status) {
        CalendarAccessStatus.GRANTED -> CalendarAccessStep.NONE
        CalendarAccessStatus.DENIED_PERMANENTLY -> CalendarAccessStep.OPEN_APP_SETTINGS
        CalendarAccessStatus.UNKNOWN,
        CalendarAccessStatus.NOT_GRANTED,
        -> if (rationaleVisible) CalendarAccessStep.REQUEST_PERMISSION else CalendarAccessStep.SHOW_RATIONALE
    }
