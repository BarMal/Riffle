package com.riffle.core.domain.launcher

enum class ShellDestination {
    HOME,

    /** The Library (ADR 0003): a Thick glass sheet over Home or the Stream, the Dock still drawn under it. */
    APP_DRAWER,

    /** The Library opened with its search field focused and the keyboard up (swipe down on Home, `OPEN_SEARCH`). */
    SEARCH,
    NOTIFICATIONS,
    SETTINGS,

    /** The Stream (ADR 0003): a full surface that replaces Home, with the Dock still drawn under it. */
    STREAM,
    ;

    /** Whether this destination shows the Library sheet. */
    val isLibrary: Boolean get() = this == APP_DRAWER || this == SEARCH
}
