package com.riffle.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.glassSpotlightSource
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one glass material at its three weights (Thin, Regular, Thick), with the spotlight lighting a
 * surface from the colours of the things it holds, and with reduced transparency on. Robolectric
 * cannot build the backdrop shader (see `GlassSurface.kt`), so these render the material's
 * tint-layer fallback plus the sheen and the spotlight, which is what the weights and the lit edges
 * look like on a device without a backdrop to sample.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class GlassMaterialScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threeWeightsWithTheSpotlightOnTheRegularAndThickSurfaces() {
        composeRule.setContent { ScreenshotBackdrop { WeightsSample() } }
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun threeWeightsInTheDarkTheme() {
        composeRule.setContent { ScreenshotBackdrop { WeightsSample() } }
        composeRule.captureScreen()
    }

    @Test
    fun reducedTransparencyDrawsOpaqueGlassWithAFaintSheenAndSpotlight() {
        composeRule.setContent { ScreenshotBackdrop(reducedTransparency = true) { WeightsSample() } }
        composeRule.captureScreen()
    }
}

@Composable
private fun WeightsSample() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        GlassWeight.entries.forEach { weight ->
            GlassSurface(
                modifier = Modifier.fillMaxWidth().height(96.dp),
                weight = weight,
                shape = RiffleShapes.large,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
                spotlight = weight != GlassWeight.Thin,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = weight.name, style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047)).forEachIndexed { index, color ->
                            Box(
                                modifier =
                                    Modifier
                                        .size(40.dp)
                                        .background(color, CircleShape)
                                        .glassSpotlightSource(key = "${weight.name}-$index", color = color),
                            )
                        }
                    }
                }
            }
        }
    }
}
