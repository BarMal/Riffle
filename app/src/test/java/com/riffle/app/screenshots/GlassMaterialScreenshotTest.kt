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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
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
 * surface from the colours of the things it holds, and with reduced transparency on. The material
 * is plain Compose drawing -- a flat fill and edge lights -- so these render exactly what a device
 * draws. [weightsAtSeveralSizes] is the one that matters for the material's geometry: the same
 * edge on a 48dp pill, a 168dp card, a 400dp card and a circle.
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
    @Config(qualifiers = WIDE_QUALIFIERS)
    fun weightsAtSeveralSizes() {
        composeRule.setContent { ScreenshotBackdrop { SizesSample() } }
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = WIDE_NIGHT_QUALIFIERS)
    fun weightsAtSeveralSizesInTheDarkTheme() {
        composeRule.setContent { ScreenshotBackdrop { SizesSample() } }
        composeRule.captureScreen()
    }

    @Test
    @Config(qualifiers = WIDE_NIGHT_QUALIFIERS)
    fun weightsAtSeveralSizesWithReducedTransparency() {
        composeRule.setContent { ScreenshotBackdrop(reducedTransparency = true) { SizesSample() } }
        composeRule.captureScreen()
    }

    @Test
    fun reducedTransparencyDrawsOpaqueGlassWithFaintEdgeLightsAndSpotlight() {
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

private const val WIDE_QUALIFIERS = "+w960dp-h920dp"
private const val WIDE_NIGHT_QUALIFIERS = "+w960dp-h920dp-night"

private val SAMPLE_WIDTH = 200.dp

/**
 * One column per weight, plus a Regular column whose surfaces carry an edge tint: a 48x200dp pill, a
 * 168dp card, a 400dp-tall card and a circle, so the edge can be judged at every height.
 */
@Composable
private fun SizesSample() {
    Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
    ) {
        listOf(GlassWeight.Thin, GlassWeight.Regular, GlassWeight.Thick, null).forEach { column ->
            val weight = column ?: GlassWeight.Regular
            val edgeTint = if (column == null) Color(0xFFE5733A) else Color.Unspecified
            val label = if (column == null) "Regular + tint" else weight.name
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SizedSurface(label, weight, RiffleShapes.full, SAMPLE_WIDTH, 48.dp, edgeTint)
                SizedSurface(label, weight, RiffleShapes.large, SAMPLE_WIDTH, 168.dp, edgeTint)
                SizedSurface(label, weight, RiffleShapes.large, SAMPLE_WIDTH, 400.dp, edgeTint)
                SizedSurface(label, weight, CircleShape, 72.dp, 72.dp, edgeTint)
            }
        }
    }
}

@Composable
private fun SizedSurface(
    label: String,
    weight: GlassWeight,
    shape: Shape,
    width: Dp,
    height: Dp,
    edgeTint: Color,
) {
    GlassSurface(
        modifier = Modifier.size(width, height),
        weight = weight,
        shape = shape,
        tint = MaterialTheme.colorScheme.surfaceContainerHigh,
        edgeTint = edgeTint,
    ) {
        if (width >= SAMPLE_WIDTH) {
            Box(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                Text(text = label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
