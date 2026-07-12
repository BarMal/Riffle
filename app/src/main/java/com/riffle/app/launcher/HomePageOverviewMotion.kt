package com.riffle.app.launcher

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

internal fun homePageOverviewMotionPolicy(reducedMotion: Boolean): HomePageOverviewMotionPolicy =
    if (reducedMotion) HomePageOverviewMotionPolicy.NONE else HomePageOverviewMotionPolicy.ZOOM

internal enum class HomePageOverviewMotionPolicy {
    NONE,
    ZOOM,
    ;

    fun contentTransform(
        enteringOverview: Boolean,
        exitingOverview: Boolean,
    ) =
        when (this) {
            NONE ->
                EnterTransition.None togetherWith ExitTransition.None

            ZOOM ->
                when {
                    enteringOverview -> (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut()
                    exitingOverview -> fadeIn() togetherWith (fadeOut() + scaleOut(targetScale = 0.92f))
                    else -> fadeIn() togetherWith fadeOut()
                }
        }
}
