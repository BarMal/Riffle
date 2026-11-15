package com.riffle.app.launcher.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import com.riffle.app.launcher.BoundedCache
import com.riffle.app.launcher.apps.dominantColorOf
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * A card's light comes from its own header image. The card as a whole is lit by the app it belongs to, but several
 * cards belong to one app (a mail app's shortcuts) and each shows its own icon, so the colour that lights the rim
 * beside an icon is read from the image that icon actually shows, not from the app the cards share.
 */

/** The colour one image lights its card's rim with: null is a neutral light (no vivid colour, or still being read). */
internal class CardLight(
    val color: Color?,
)

private class LightEntry(
    val color: Color?,
)

/** The colours read from images so far, keyed by the image handle and the loaded bitmap, bounded. */
private val lightCache = BoundedCache<Pair<String, Int>, LightEntry>(CARD_LIGHT_CACHE_ENTRIES)

private const val CARD_LIGHT_CACHE_ENTRIES = 96

/**
 * The light of [bitmap] (loaded from [handle]), or null while there is no bitmap yet (the card's own app colour stands
 * in). The colour is read once off the main thread and remembered per image, so a card that comes to the front, is
 * scrolled or recomposes reads it back at once; a new image (a re-posted sender photo) is a new key and is read afresh.
 */
@Composable
internal fun rememberImageLight(
    handle: ItemImageHandle?,
    bitmap: ImageBitmap?,
): CardLight? {
    if (bitmap == null) return null
    val key = handle?.key.orEmpty() to System.identityHashCode(bitmap)
    var entry by remember(key) { mutableStateOf(lightCache[key]) }
    LaunchedEffect(key) {
        if (entry == null) {
            val read = LightEntry(withContext(Dispatchers.Default) { dominantColorOf(bitmap) })
            lightCache[key] = read
            entry = read
        }
    }
    return CardLight(entry?.color)
}

/** The colour a card's light source reports, as `#AARRGGBB` or `neutral`: a test hook. */
internal val CardSpotlightLightKey = SemanticsPropertyKey<String>("CardSpotlightLight")

internal fun SemanticsPropertyReceiver.cardSpotlightLight(color: Color?) {
    this[CardSpotlightLightKey] = color?.let { "#%08X".format(it.toArgb()) } ?: "neutral"
}
