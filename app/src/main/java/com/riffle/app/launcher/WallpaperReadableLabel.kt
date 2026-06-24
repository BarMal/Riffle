package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = settings.backgroundAlphaPercent / 100f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = settings.textSizeSp.sp),
            color = Color.White,
            maxLines = settings.maxLines,
            overflow = TextOverflow.Ellipsis,
            softWrap = true,
            textAlign = TextAlign.Center,
        )
    }
}
