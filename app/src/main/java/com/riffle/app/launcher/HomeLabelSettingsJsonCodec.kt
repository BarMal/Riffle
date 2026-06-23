package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLabelSettings
import org.json.JSONObject

internal fun encodeLabels(settings: HomeLabelSettings): JSONObject =
    JSONObject()
        .put("backgroundAlphaPercent", settings.backgroundAlphaPercent)

internal fun JSONObject.toLabelSettings(defaults: HomeLabelSettings): HomeLabelSettings =
    HomeLabelSettings(
        backgroundAlphaPercent = optInt("backgroundAlphaPercent", defaults.backgroundAlphaPercent),
    )
