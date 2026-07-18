package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.home.WidgetResizeConstraints
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
        .put("minColumns", widget.resizeConstraints.minSpan.columns)
        .put("minRows", widget.resizeConstraints.minSpan.rows)
        .put("maxColumns", widget.resizeConstraints.maxSpan?.columns)
        .put("maxRows", widget.resizeConstraints.maxSpan?.rows)
        .put("supportsHorizontalResize", widget.resizeConstraints.supportsHorizontalResize)
        .put("supportsVerticalResize", widget.resizeConstraints.supportsVerticalResize)

fun JSONObject.toWidget(): WidgetItem =
    WidgetItem(
        id = LauncherItemId(getString("id")),
        appWidgetId = HostedWidgetId(getInt("appWidgetId")),
        label = optString("label", ""),
        resizeConstraints =
            WidgetResizeConstraints(
                minSpan = GridSpan(optInt("minColumns", 1), optInt("minRows", 1)),
                maxSpan =
                    optInt("maxColumns", 0).takeIf { it > 0 }?.let { columns ->
                        GridSpan(columns, optInt("maxRows", 0).coerceAtLeast(1))
                    },
                supportsHorizontalResize = optBoolean("supportsHorizontalResize", true),
                supportsVerticalResize = optBoolean("supportsVerticalResize", true),
            ),
        placement = toPlacementOrNull(),
    )
