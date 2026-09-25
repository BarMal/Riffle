package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps
import com.riffle.core.domain.launcher.settings.MotionSettings
import com.riffle.core.domain.launcher.settings.ReducedMotionPreference
import org.json.JSONObject

private const val REDUCED_MOTION_PREFERENCE_KEY = "reducedMotionPreference"
private const val LEGACY_REDUCED_MOTION_KEY = "reducedMotion"

/**
 * Encodes the persisted motion intent. The runtime system reduced-motion state is never written. The legacy
 * boolean is still written (true only for an explicit "On") so older builds restoring a backup keep the intent.
 */
internal fun encodeMotionSettings(settings: MotionSettings): JSONObject =
    JSONObject()
        .put(REDUCED_MOTION_PREFERENCE_KEY, settings.reducedMotionPreference.name)
        .put(LEGACY_REDUCED_MOTION_KEY, settings.reducedMotionPreference == ReducedMotionPreference.ON)
        .put("performanceTargetFps", settings.performanceTargetFps.name)

/**
 * Decodes motion settings, migrating the legacy `reducedMotion` boolean when no tri-state preference is
 * stored: `true` becomes [ReducedMotionPreference.ON] and `false` becomes [ReducedMotionPreference.SYSTEM].
 */
internal fun JSONObject.toMotionSettings(defaults: MotionSettings): MotionSettings =
    defaults.copy(
        reducedMotionPreference = decodeReducedMotionPreference(defaults.reducedMotionPreference),
        performanceTargetFps =
            runCatching { MotionPerformanceTargetFps.valueOf(optString("performanceTargetFps")) }
                .getOrDefault(defaults.performanceTargetFps),
    )

private fun JSONObject.decodeReducedMotionPreference(default: ReducedMotionPreference): ReducedMotionPreference =
    when {
        has(REDUCED_MOTION_PREFERENCE_KEY) ->
            runCatching { ReducedMotionPreference.valueOf(optString(REDUCED_MOTION_PREFERENCE_KEY)) }
                .getOrDefault(default)

        has(LEGACY_REDUCED_MOTION_KEY) ->
            ReducedMotionPreference.fromLegacyReducedMotion(optBoolean(LEGACY_REDUCED_MOTION_KEY, false))

        else -> default
    }
