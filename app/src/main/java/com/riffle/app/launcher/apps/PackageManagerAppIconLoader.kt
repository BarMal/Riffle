package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.launcher.AppIconLoader
import com.riffle.core.domain.launcher.apps.AppIdentity

class PackageManagerAppIconLoader(
    private val packageManager: PackageManager,
) : AppIconLoader {
    private val icons = mutableMapOf<AppIdentity, ImageBitmap>()

    override fun iconFor(identity: AppIdentity): ImageBitmap? =
        icons[identity] ?: loadIcon(identity)?.also { icon -> icons[identity] = icon }

    private fun loadIcon(identity: AppIdentity): ImageBitmap? =
        runCatching {
            packageManager
                .getActivityIcon(identity.componentName)
                .toLauncherImageBitmap()
        }.getOrNull()
}

private val AppIdentity.componentName: ComponentName
    get() = ComponentName(packageName.value, activityName.value)

private fun Drawable.toLauncherImageBitmap(): ImageBitmap =
    toBitmap(
        width = LAUNCHER_ICON_BITMAP_SIZE,
        height = LAUNCHER_ICON_BITMAP_SIZE,
    ).asImageBitmap()

private const val LAUNCHER_ICON_BITMAP_SIZE = 96
