package com.riffle.app.launcher.designsystem

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.ContinuousCornerGeometry
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiffleDesignSystemTest {
    @Test
    fun materialShapesFollowTheRadiusScale() {
        val shapes = RiffleShapes.materialShapes()

        assertEquals(RiffleShapes.continuous(8.dp), shapes.extraSmall)
        assertEquals(RiffleShapes.continuous(8.dp), shapes.small)
        assertEquals(RiffleShapes.continuous(16.dp), shapes.medium)
        assertEquals(RiffleShapes.continuous(24.dp), shapes.large)
        assertEquals(RiffleShapes.continuous(32.dp), shapes.extraLarge)
    }

    @Test
    fun squareMaterialShapesAreSharp() {
        val shapes = RiffleShapes.materialShapes(square = true)

        listOf(shapes.extraSmall, shapes.small, shapes.medium, shapes.large, shapes.extraLarge).forEach { shape ->
            assertEquals(RiffleShapes.continuous(0.dp), shape)
        }
    }

    @Test
    fun continuousShapeCopyKeepsSmoothing() {
        val shape = ContinuousRoundedCornerShape(CornerSize(16.dp), smoothing = 0.3f)

        val copied = shape.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp))

        assertTrue(copied is ContinuousRoundedCornerShape)
        assertEquals(0.3f, (copied as ContinuousRoundedCornerShape).smoothing)
        assertEquals(CornerSize(16.dp), copied.topStart)
        assertEquals(CornerSize(0.dp), copied.bottomEnd)
    }

    @Test
    fun continuousShapesDefaultToAppleLikeSmoothing() {
        assertEquals(
            ContinuousCornerGeometry.DEFAULT_SMOOTHING,
            (RiffleShapes.continuous(24.dp) as ContinuousRoundedCornerShape).smoothing,
        )
        assertNotEquals(
            RiffleShapes.continuous(24.dp),
            RiffleShapes.continuous(24.dp, smoothing = 0f),
        )
    }

    @Test
    fun springsUseTheirTokens() {
        val snappy = RiffleMotion.snappy<Float>() as SpringSpec<*>
        val smooth = RiffleMotion.smooth<Float>() as SpringSpec<*>
        val gentle = RiffleMotion.gentle<Float>() as SpringSpec<*>

        assertEquals(RiffleMotionTokens.Snappy.stiffness, snappy.stiffness)
        assertEquals(RiffleMotionTokens.Smooth.stiffness, smooth.stiffness)
        assertEquals(RiffleMotionTokens.Gentle.dampingRatio, gentle.dampingRatio)
    }

    @Test
    fun reducedMotionSnapsEverySpecToItsEndState() {
        listOf(
            RiffleMotion.snappy<Float>(reducedMotion = true),
            RiffleMotion.smooth<Float>(reducedMotion = true),
            RiffleMotion.gentle<Float>(reducedMotion = true),
            RiffleMotion.standard<Float>(reducedMotion = true),
            RiffleMotion.emphasized<Float>(reducedMotion = true),
        ).forEach { spec -> assertTrue(spec is SnapSpec<*>) }
    }

    @Test
    fun spacingScaleIsExposedAsDp() {
        assertEquals(
            listOf(2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 24.dp, 32.dp, 48.dp),
            listOf(
                RiffleSpacing.xxs,
                RiffleSpacing.xs,
                RiffleSpacing.s,
                RiffleSpacing.m,
                RiffleSpacing.l,
                RiffleSpacing.xl,
                RiffleSpacing.xxl,
                RiffleSpacing.xxxl,
            ),
        )
    }
}
