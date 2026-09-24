package com.riffle.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.riffle.app.launcher.RiffleLauncherTheme
import com.riffle.core.domain.launcher.cards.AdaptiveStageHingeBounds
import com.riffle.core.domain.launcher.cards.AdaptiveStagePosture
import com.riffle.core.domain.launcher.cards.AdaptiveStageWindowLayout

/**
 * The launcher's own theme over a fixed stand-in wallpaper.
 *
 * The real launcher draws over the system wallpaper, which a JVM test does not have; a bundled
 * gradient keeps translucent surfaces (dock, cards glass) legible and identical between runs.
 * The theme follows the Robolectric `night` qualifier through its SYSTEM mode, which is how the
 * dark-theme variants are produced.
 */
@Composable
internal fun ScreenshotBackdrop(content: @Composable () -> Unit) {
    RiffleLauncherTheme {
        Box(modifier = Modifier.fillMaxSize().background(ScreenshotWallpaper)) {
            content()
        }
    }
}

/**
 * The Cards window input MainActivity would derive from Jetpack WindowManager for the current
 * Robolectric display, with the posture and (for [AdaptiveStagePosture.TABLETOP]) the horizontal
 * separating hinge a folding feature would have reported -- Robolectric cannot report one itself.
 */
@Composable
internal fun screenshotWindowLayout(posture: AdaptiveStagePosture): AdaptiveStageWindowLayout {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val hinges =
        if (posture == AdaptiveStagePosture.TABLETOP) {
            listOf(
                AdaptiveStageHingeBounds(
                    leftDp = 0,
                    topDp = heightDp / 2,
                    rightDp = widthDp,
                    bottomDp = heightDp / 2,
                ),
            )
        } else {
            emptyList()
        }
    return AdaptiveStageWindowLayout(
        widthDp = widthDp,
        heightDp = heightDp,
        separatingHinges = hinges,
        posture = posture,
    )
}

/**
 * Settles the composition and records the whole window.
 *
 * Outside a Roborazzi record/verify/compare run the capture is a no-op, so a test only renders.
 * The file name comes from the calling test class and method (see `roborazzi.record.namingStrategy`
 * in gradle.properties).
 */
internal fun ComposeContentTestRule.captureScreen() {
    waitForIdle()
    onRoot().captureRoboImage()
}

private val ScreenshotWallpaper =
    Brush.verticalGradient(
        colors =
            listOf(
                Color(0xFF28324A),
                Color(0xFF4A5A7A),
                Color(0xFF8C7A9E),
            ),
    )
