package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import com.riffle.core.domain.launcher.home.DockAlignment

/**
 * Where a Dock down a side stands along its edge: "Dock alignment" Start, Centre or End are the top, the middle and the
 * bottom of the edge there (along the top or bottom edge they are the left, the middle and the right, as
 * [toHorizontalAlignment] has them).
 */
internal fun DockAlignment.sideArrangement(runsAlongASide: Boolean): Arrangement.Vertical =
    if (!runsAlongASide) {
        Arrangement.Top
    } else {
        when (this) {
            DockAlignment.START -> Arrangement.Top
            DockAlignment.CENTER -> Arrangement.Center
            DockAlignment.END -> Arrangement.Bottom
        }
    }
