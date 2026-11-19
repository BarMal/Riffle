package com.riffle.app.launcher

import androidx.compose.ui.graphics.ImageBitmap
import com.riffle.core.domain.launcher.home.StackedWidget

/**
 * The app icon of the app a stack's widget comes from; null when it has none to show (the row shows an initial).
 * [iconFor] may decode and so runs off the main thread; [cachedIconFor] never does (it is what the first frame draws).
 */
internal fun interface WidgetStackMemberIcons {
    fun iconFor(member: StackedWidget): ImageBitmap?

    fun cachedIconFor(member: StackedWidget): ImageBitmap? = iconFor(member)

    /** Changes with the icon style (a different icon is drawn for the same widget). */
    val styleVersion: Int get() = 0

    /** Whether the icons come cut to their own shape, so the row must not clip them to a circle. */
    val drawsOwnShape: Boolean get() = false
}
