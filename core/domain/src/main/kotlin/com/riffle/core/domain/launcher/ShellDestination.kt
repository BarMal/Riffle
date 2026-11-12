package com.riffle.core.domain.launcher

enum class ShellDestination {
    HOME,
    APP_DRAWER,
    SEARCH,
    NOTIFICATIONS,
    SETTINGS,

    /** The Stream (ADR 0003): a full surface that replaces Home, with the Dock still drawn under it. */
    STREAM,
}
