package com.riffle.app.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riffle.core.domain.launcher.home.HomeLabelSettings

@Composable
fun WallpaperReadableLabel(
    text: String,
    settings: HomeLabelSettings,
) {
    Text(
        modifier =
            Modifier
                .widthIn(max = 76.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = settings.backgroundAlphaPercent / 100f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = settings.textSizeSp.sp),
        color = Color.White,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}
