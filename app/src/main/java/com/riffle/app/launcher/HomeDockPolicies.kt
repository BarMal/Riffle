package com.riffle.app.launcher

import android.os.Build
import android.view.View
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.settings.MotionPerformanceTargetFps

private const val DOCK_SHELF_SYSTEM_GESTURE_ZONE_DP = 24
internal const val REDUCED_MOTION_DOCK_SHELF_DURATION_MILLIS = 80

internal fun dockShelfMotionPolicy(reducedMotion: Boolean): DockShelfMotionPolicy =
    if (reducedMotion) {
        DockShelfMotionPolicy.ReducedShortTween
    } else {
        DockShelfMotionPolicy.StandardSpring
    }

internal enum class DockShelfMotionPolicy {
    StandardSpring,
    ReducedShortTween,
}

internal fun dockShelfGesturePolicy(
    isDockVisible: Boolean,
    homeInsetPolicy: HomeInsetPolicy,
): DockShelfGesturePolicy =
    DockShelfGesturePolicy(
        enabled = isDockVisible,
        bottomSystemGestureExclusionDp =
            if (homeInsetPolicy.reserveNavigationBar) {
                0
            } else {
                DOCK_SHELF_SYSTEM_GESTURE_ZONE_DP
            },
    )

internal data class DockShelfGesturePolicy(
    val enabled: Boolean,
    val bottomSystemGestureExclusionDp: Int,
)

internal fun Modifier.dockShelfMotion(policy: DockShelfMotionPolicy): Modifier =
    animateContentSize(animationSpec = dockShelfSizeAnimation(policy))

internal fun Modifier.dockShelfFrameRatePreference(
    targetFps: MotionPerformanceTargetFps,
): Modifier =
    composed {
        val view = LocalView.current
        val frameRate = targetFps.framesPerSecond.toFloat()
        DisposableEffect(view, frameRate) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.setFrameRate(frameRate, View.FRAME_RATE_COMPATIBILITY_DEFAULT)
            }
            onDispose {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    view.setFrameRate(0f, View.FRAME_RATE_COMPATIBILITY_DEFAULT)
                }
            }
        }
        this
    }

internal fun Modifier.dockShelfPolicies(interactions: DockInteractions): Modifier =
    dockShelfSystemGestureExclusion(
        dockShelfGesturePolicy(
            isDockVisible = true,
            homeInsetPolicy = interactions.homeInsetPolicy,
        ),
    )

internal fun Modifier.dockShelfSystemGestureExclusion(policy: DockShelfGesturePolicy): Modifier =
    composed {
        val bottomSystemGestureExclusionPx =
            with(LocalDensity.current) { policy.bottomSystemGestureExclusionDp.dp.toPx() }
        if (!policy.enabled || bottomSystemGestureExclusionPx <= 0f) {
            this
        } else {
            systemGestureExclusion { coordinates ->
                Rect(
                    left = 0f,
                    top = (coordinates.size.height - bottomSystemGestureExclusionPx).coerceAtLeast(0f),
                    right = coordinates.size.width.toFloat(),
                    bottom = coordinates.size.height.toFloat(),
                )
            }
        }
    }

private fun dockShelfSizeAnimation(policy: DockShelfMotionPolicy): FiniteAnimationSpec<IntSize> =
    when (policy) {
        DockShelfMotionPolicy.ReducedShortTween ->
            tween(
                durationMillis = REDUCED_MOTION_DOCK_SHELF_DURATION_MILLIS,
                easing = LinearOutSlowInEasing,
            )

        DockShelfMotionPolicy.StandardSpring ->
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
    }
