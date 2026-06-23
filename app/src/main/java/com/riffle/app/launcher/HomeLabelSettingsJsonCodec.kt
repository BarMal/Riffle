package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLabelSettings
import org.json.JSONObject

internal fun encodeLabels(settings: HomeLabelSettings): JSONObject =
    JSONObject()
        .put("backgroundAlphaPercent", settings.backgroundAlphaPercent)
        .put("textSizeSp", settings.textSizeSp)
        .put("showText", settings.showText)
        .put("maxWidthDp", settings.maxWidthDp)
        .put("maxLines", settings.maxLines)

internal fun JSONObject.toLabelSettings(defaults: HomeLabelSettings): HomeLabelSettings =
    HomeLabelSettings(
        backgroundAlphaPercent = optInt("backgroundAlphaPercent", defaults.backgroundAlphaPercent),
        textSizeSp = optInt("textSizeSp", defaults.textSizeSp),
        showText = optBoolean("showText", defaults.showText),
        maxWidthDp = optInt("maxWidthDp", defaults.maxWidthDp),
        maxLines = optInt("maxLines", defaults.maxLines),
    )
