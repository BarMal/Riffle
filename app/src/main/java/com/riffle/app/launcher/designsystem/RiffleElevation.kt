package com.riffle.app.launcher.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.RiffleElevationScale

/**
 * Four elevation levels (plus flat), used for both `tonalElevation` and `shadowElevation`. Riffle
 * depth comes from one soft light source, so a surface picks one level for both rather than mixing.
 */
object RiffleElevation {
    val level0: Dp = RiffleElevationScale.LEVEL_0.dp
    val level1: Dp = RiffleElevationScale.LEVEL_1.dp
    val level2: Dp = RiffleElevationScale.LEVEL_2.dp
    val level3: Dp = RiffleElevationScale.LEVEL_3.dp
    val level4: Dp = RiffleElevationScale.LEVEL_4.dp
}
