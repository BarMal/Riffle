package com.riffle.app.launcher

import androidx.compose.ui.graphics.ImageBitmap
import com.riffle.core.domain.launcher.home.StackedWidget

/** The app icon of the app a stack's widget comes from; null when it has none to show (the row shows an initial). */
internal fun interface WidgetStackMemberIcons {
    fun iconFor(member: StackedWidget): ImageBitmap?
}
