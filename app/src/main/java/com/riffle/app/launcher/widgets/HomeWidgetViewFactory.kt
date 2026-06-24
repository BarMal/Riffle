package com.riffle.app.launcher.widgets

import android.content.Context
import android.view.View
import com.riffle.core.domain.launcher.home.WidgetItem

fun interface HomeWidgetViewFactory {
    fun createHostedWidgetView(
        context: Context,
        widget: WidgetItem,
    ): View?
}

object EmptyHomeWidgetViewFactory : HomeWidgetViewFactory {
    override fun createHostedWidgetView(
        context: Context,
        widget: WidgetItem,
    ): View? = null
}
