package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riffle.core.domain.launcher.home.HomeLabelSettings

@Composable
fun WallpaperReadableLabel(
    text: String,
    settings: HomeLabelSettings,
) {
    if (!settings.showText) {
        return
    }

    val metrics = HomeGridLayoutMetrics()
    val fixedWidthDp = metrics.fixedHomeLabelContainerWidthDp(settings)
    val colors = wallpaperReadableLabelColors(MaterialTheme.colorScheme)

    Box(
        modifier =
            Modifier
                .then(
                    if (fixedWidthDp == null) {
                        Modifier.widthIn(max = settings.maxWidthDp.dp)
                    } else {
                        Modifier.width(fixedWidthDp.dp)
                    },
                )
                .height(metrics.homeLabelContainerHeightDp(settings).dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (settings.showBackground) {
                        Modifier.background(
                            colors.background
                                .let {
                                        color ->
                                    color.copy(alpha = color.alpha * settings.backgroundAlphaPercent / 100f)
                                },
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = settings.textSizeSp.sp,
                    // With no background the wallpaper is the backdrop: a soft shadow keeps white text legible.
                    shadow = if (settings.showBackground) null else LABEL_TEXT_SHADOW,
                ),
            color = if (settings.showBackground) colors.text else Color.White,
            maxLines = settings.maxLines,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            textAlign = TextAlign.Center,
        )
    }
}

private val LABEL_TEXT_SHADOW =
    Shadow(color = Color.Black.copy(alpha = 0.55f), offset = Offset(0f, 1f), blurRadius = 6f)

internal data class WallpaperReadableLabelColors(
    val text: Color,
    val background: Color,
)

internal fun wallpaperReadableLabelColors(colorScheme: ColorScheme): WallpaperReadableLabelColors {
    val background = colorScheme.scrim
    return WallpaperReadableLabelColors(
        text = background.contentColor(Color.White),
        background = background,
    )
}
