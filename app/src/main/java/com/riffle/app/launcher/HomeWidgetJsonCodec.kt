package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import org.json.JSONObject

fun encodeWidget(widget: WidgetItem): JSONObject =
    JSONObject()
        .put("type", "widget")
        .put("id", widget.id.value)
        .put("appWidgetId", widget.appWidgetId.value)
        .put("label", widget.label)
        .put("column", widget.placement?.cell?.column)
        .put("row", widget.placement?.cell?.row)
        .put("columns", widget.placement?.span?.columns)
        .put("rows", widget.placement?.span?.rows)

fun JSONObject.toWidget(): WidgetItem =
    WidgetItem(
        id = LauncherItemId(getString("id")),
        appWidgetId = HostedWidgetId(getInt("appWidgetId")),
        label = optString("label", ""),
        placement = toPlacementOrNull(),
    )
