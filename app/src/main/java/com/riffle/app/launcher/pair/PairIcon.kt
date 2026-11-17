package com.riffle.app.launcher.pair

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.core.domain.launcher.home.AppPairItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a screen reader says for a pair. */
internal val AppPairItem.accessibilityLabel: String get() = "${first.label} and ${second.label}, app pair"

/**
 * An app pair's tile: its two icons fused by a side blend (see [PairIconComposer]), made once off the main thread and
 * kept in [PairIconCache]. While it is being made the first app's icon stands in.
 */
@Composable
internal fun PairIcon(
    pair: AppPairItem,
    iconLoader: AppIconLoader,
    size: Dp,
    shape: Shape,
    modifier: Modifier = Modifier,
    cache: PairIconCache = PairIconCache.shared,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val key =
        remember(pair.first.appIdentity, pair.second.appIdentity, sizePx, rtl) {
            PairIconKey(pair.first.appIdentity, pair.second.appIdentity, sizePx, rtl)
        }
    var fused by remember(key) { mutableStateOf(cache.cached(key)) }
    LaunchedEffect(key, iconLoader) {
        if (fused == null) {
            fused =
                withContext(Dispatchers.Default) {
                    val first = iconLoader.cachedIconFor(key.first) ?: iconLoader.iconFor(key.first)
                    val second = iconLoader.cachedIconFor(key.second) ?: iconLoader.iconFor(key.second)
                    if (first == null || second == null) {
                        null
                    } else {
                        cache.getOrCompose(key) {
                            PairIconComposer
                                .compose(first.asAndroidBitmap(), second.asAndroidBitmap(), sizePx, rtl)
                                .asImageBitmap()
                        }
                    }
                }
        }
    }
    val bitmap = fused
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(shape),
        )
    } else {
        LauncherAppIcon(
            identity = pair.first.appIdentity,
            label = pair.first.label,
            iconLoader = iconLoader,
            modifier = modifier,
            shape = shape,
        )
    }
}
