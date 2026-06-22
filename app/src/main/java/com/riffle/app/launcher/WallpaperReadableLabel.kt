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

@Composable
fun WallpaperReadableLabel(text: String) {
    Text(
        modifier =
            Modifier
                .widthIn(max = 76.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.54f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        maxLines = 1,
        textAlign = TextAlign.Center,
    )
}
