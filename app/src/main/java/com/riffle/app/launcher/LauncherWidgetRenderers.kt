package com.riffle.app.launcher

import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.app.launcher.widgets.HomeWidgetViewFactory

data class LauncherWidgetRenderers(
    val viewFactory: HomeWidgetViewFactory = EmptyHomeWidgetViewFactory,
    val previewImageLoader: WidgetPreviewImageLoader = EmptyWidgetPreviewImageLoader,
)
