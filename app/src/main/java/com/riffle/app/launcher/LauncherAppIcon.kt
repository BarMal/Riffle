package com.riffle.app.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity

fun interface AppIconLoader {
    fun iconFor(identity: AppIdentity): ImageBitmap?
}

object EmptyAppIconLoader : AppIconLoader {
    override fun iconFor(identity: AppIdentity): ImageBitmap? = null
}

@Composable
fun LauncherAppIcon(
    identity: AppIdentity,
    label: String,
    iconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val icon = remember(identity, iconLoader) { iconLoader.iconFor(identity) }

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = "$label icon",
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(shape),
        )
    } else {
        AppIconPlaceholder(
            label = label,
            modifier = modifier,
            shape = shape,
        )
    }
}

@Composable
private fun AppIconPlaceholder(
    label: String,
    modifier: Modifier,
    shape: Shape,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label.firstOrNull()?.uppercase().orEmpty(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

fun Modifier.launcherIconSize() = size(40.dp)
