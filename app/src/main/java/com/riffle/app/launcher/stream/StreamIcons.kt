package com.riffle.app.launcher.stream

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The three transport glyphs the core icon set lacks, drawn on the standard 24 dp grid. */
internal object StreamIcons {
    val Pause: ImageVector by lazy {
        glyph("StreamPause") {
            moveTo(6f, 19f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineTo(6f)
            verticalLineToRelative(14f)
            close()
            moveTo(14f, 5f)
            verticalLineToRelative(14f)
            horizontalLineToRelative(4f)
            verticalLineTo(5f)
            horizontalLineToRelative(-4f)
            close()
        }
    }

    val SkipNext: ImageVector by lazy {
        glyph("StreamSkipNext") {
            moveTo(6f, 18f)
            lineToRelative(8.5f, -6f)
            lineTo(6f, 6f)
            verticalLineToRelative(12f)
            close()
            moveTo(16f, 6f)
            verticalLineToRelative(12f)
            horizontalLineToRelative(2f)
            verticalLineTo(6f)
            horizontalLineToRelative(-2f)
            close()
        }
    }

    val SkipPrevious: ImageVector by lazy {
        glyph("StreamSkipPrevious") {
            moveTo(6f, 6f)
            horizontalLineToRelative(2f)
            verticalLineToRelative(12f)
            horizontalLineTo(6f)
            close()
            moveTo(9.5f, 12f)
            lineToRelative(8.5f, 6f)
            verticalLineTo(6f)
            close()
        }
    }

    /** A clock: Recents. */
    val Recents: ImageVector by lazy {
        pathGlyph(
            "StreamRecents",
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 " +
                "-8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zM12.5,7H11v6l5.25,3.15 0.75,-1.23 -4.5,-2.67z",
        )
    }

    /** A bell with a clock: Snooze. */
    val Snooze: ImageVector by lazy {
        pathGlyph(
            "StreamSnooze",
            "M7.88,3.39L6.6,1.86 2,5.71l1.29,1.53 4.59,-3.85zM22,5.72l-4.6,-3.86 -1.29,1.53 4.6,3.86L22,5.72z" +
                "M12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9c4.97,0 9,-4.03 9,-9s-4.03,-9 -9,-9z" +
                "M12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z" +
                "M9,11h3.63L9,15.2L9,17h6v-2h-3.63L15,10.8L15,9L9,9v2z",
        )
    }

    /** A calendar page: Agenda. */
    val Agenda: ImageVector by lazy {
        pathGlyph(
            "StreamAgenda",
            "M17,12h-5v5h5v-5zM16,1v2H8V1H6v2H5c-1.11,0 -1.99,0.9 -1.99,2L3,19c0,1.1 0.89,2 2,2h14c1.1,0 2,-0.9 2," +
                "-2V5" +
                "c0,-1.1 -0.9,-2 -2,-2h-1V1h-2zM19,19H5V8h14v11z",
        )
    }

    /** A feed mark: Feeds. */
    val Feeds: ImageVector by lazy {
        pathGlyph(
            "StreamFeeds",
            "M6.18,15.64a2.18,2.18 0,0 1,2.18 2.18C8.36,19 7.38,20 6.18,20 5,20 4,19 4,17.82a2.18,2.18 0,0 1,2.18 " +
                "-2.18M4,4.44A15.56,15.56 0 0,1 19.56,20h-2.83A12.73,12.73 0 0,0 4,7.27V4.44m0,5.66a9.9,9.9 0 0,1 " +
                "9.9,9.9h-2.83A7.07,7.07 0 0,0 4,12.93V10.1z",
        )
    }

    private fun pathGlyph(
        name: String,
        pathData: String,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black))
            .build()

    private fun glyph(
        name: String,
        build: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit,
    ): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .path(fill = SolidColor(Color.Black), pathBuilder = build)
            .build()
}
