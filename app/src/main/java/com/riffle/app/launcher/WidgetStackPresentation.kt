package com.riffle.app.launcher

/** What a stack needs to know of the Home around it: which way the pages scroll, and reduced motion. */
internal data class WidgetStackPresentation(
    /** Whether Home's pages scroll up and down; the stack then swipes sideways, so it never meets the pager. */
    val pagesScrollVertically: Boolean = false,
    val reducedMotion: Boolean = false,
)
