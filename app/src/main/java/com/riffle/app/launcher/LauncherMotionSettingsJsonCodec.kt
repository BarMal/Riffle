package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.MotionSettings
import org.json.JSONObject

internal fun encodeMotionSettings(settings: MotionSettings): JSONObject =
    JSONObject()
        .put("reducedMotion", settings.reducedMotion)

internal fun JSONObject.toMotionSettings(defaults: MotionSettings): MotionSettings =
    defaults.copy(
        reducedMotion = optBoolean("reducedMotion", defaults.reducedMotion),
    )
