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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.icons.ThemedIconColors
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AppIconLoader {
    fun iconFor(identity: AppIdentity): ImageBitmap?

    fun cachedIconFor(identity: AppIdentity): ImageBitmap? = null

    /** Dominant color derived from the app's icon, or null if unavailable (see [cachedColorFor]). */
    fun colorFor(identity: AppIdentity): Color?

    fun cachedColorFor(identity: AppIdentity): Color? = null

    fun preloadIcons(identities: List<AppIdentity>) {
        identities.forEach(::iconFor)
    }

    /**
     * Changes whenever the icon style does (shape, themed icons, icon pack, theme colours). Read in composition, so
     * whatever drew an icon draws it again; 0 for a loader that has no style.
     */
    val styleVersion: Int get() = 0

    /** [styleVersion] plus whatever applies to [identity] alone (its own icon choice): the key of its cached icon. */
    fun iconVersionFor(identity: AppIdentity): Int = styleVersion

    /** Applies the icon style and the theme's colours (called from composition, so it must be cheap). */
    fun applyStyle(
        style: IconStyleSettings,
        colors: ThemedIconColors,
    ) = Unit

    /** A package was installed, updated or removed: an icon pack may have changed. */
    fun onCatalogChanged() = Unit

    /**
     * Whether [identity]'s icon comes already cut to its own shape (a chosen shape, an icon pack or an icon the user
     * picked), so a surface must not clip it again to its own: the surface's clip would crop the corners.
     */
    fun drawsOwnShape(identity: AppIdentity): Boolean = false
}

object EmptyAppIconLoader : AppIconLoader {
    override fun iconFor(identity: AppIdentity): ImageBitmap? = null

    override fun colorFor(identity: AppIdentity): Color? = null

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
    // The version is read here so a change of style (or of this app's own icon) draws the icon again. The icon already
    // shown stays until its replacement is loaded: nothing flashes back to a placeholder.
    val version = iconLoader.iconVersionFor(identity)
    var icon by remember(identity, iconLoader) {
        mutableStateOf(iconLoader.cachedIconFor(identity))
    }

    LaunchedEffect(identity, iconLoader, version) {
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
            contentDescription = stringResource(R.string.app_icon_description, label),
            contentScale = ContentScale.Fit,
            modifier = if (iconLoader.drawsOwnShape(identity)) modifier else modifier.clip(shape),
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
