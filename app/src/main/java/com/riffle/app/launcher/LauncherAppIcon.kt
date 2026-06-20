package com.riffle.app.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppIconLoader {
    fun iconFor(identity: AppIdentity): ImageBitmap?

    fun cachedIconFor(identity: AppIdentity): ImageBitmap? = null

    fun preloadIcons(identities: List<AppIdentity>) {
        identities.forEach(::iconFor)
    }
}

object EmptyAppIconLoader : AppIconLoader {
    override fun iconFor(identity: AppIdentity): ImageBitmap? = null

    override fun preloadIcons(identities: List<AppIdentity>) = Unit
}

@Composable
fun LauncherAppIcon(
    identity: AppIdentity,
    label: String,
    iconLoader: AppIconLoader,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    var icon by remember(identity, iconLoader) {
        mutableStateOf(iconLoader.cachedIconFor(identity))
    }

    LaunchedEffect(identity, iconLoader) {
        val cachedIcon = iconLoader.cachedIconFor(identity)
        icon =
            if (cachedIcon != null) {
                cachedIcon
            } else {
                withContext(Dispatchers.Default) { iconLoader.iconFor(identity) }
            }
    }
    val loadedIcon = icon

    if (loadedIcon != null) {
        Image(
            bitmap = loadedIcon,
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
