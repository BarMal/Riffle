package com.riffle.app.launcher.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.designsystem.RiffleSpacingScale

/** Spacing scale (docs/product/design-language.md). Prefer these over raw dp literals for gaps and padding. */
object RiffleSpacing {
    val xxs: Dp = RiffleSpacingScale.XXS.dp
    val xs: Dp = RiffleSpacingScale.XS.dp
    val s: Dp = RiffleSpacingScale.S.dp
    val m: Dp = RiffleSpacingScale.M.dp
    val l: Dp = RiffleSpacingScale.L.dp
    val xl: Dp = RiffleSpacingScale.XL.dp
    val xxl: Dp = RiffleSpacingScale.XXL.dp
    val xxxl: Dp = RiffleSpacingScale.XXXL.dp
}
