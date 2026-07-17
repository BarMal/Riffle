package com.riffle.app.launcher.widgets

import android.content.Context
import android.view.View
import com.riffle.core.domain.launcher.home.WidgetItem

fun interface HomeWidgetViewFactory {
    fun createHostedWidgetView(
        context: Context,
        widget: WidgetItem,
    ): View?

    /**
     * Called after the host has measured a widget's committed visible bounds.
     * Implementations that do not host Android AppWidgets can safely ignore it.
     */
    fun updateHostedWidgetSize(
        widget: WidgetItem,
        widthDp: Int,
        heightDp: Int,
    ) = Unit
}

object EmptyHomeWidgetViewFactory : HomeWidgetViewFactory {
    override fun createHostedWidgetView(
        context: Context,
        widget: WidgetItem,
    ): View? = null
}
