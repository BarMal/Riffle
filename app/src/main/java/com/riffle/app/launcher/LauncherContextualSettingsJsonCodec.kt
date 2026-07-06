package com.riffle.app.launcher

import com.riffle.core.domain.launcher.contextual.ContextualSettings
import org.json.JSONObject

internal fun encodeContextual(settings: ContextualSettings): JSONObject =
    JSONObject()
        .put("enabled", settings.enabled)

internal fun JSONObject.toContextual(defaults: ContextualSettings): ContextualSettings =
    defaults.copy(
        enabled = optBoolean("enabled", defaults.enabled),
    )
