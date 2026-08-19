@file:Suppress("MaxLineLength")

package com.riffle.app.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.cards.CardStackAnimationProfile
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import com.riffle.core.domain.launcher.settings.AdaptiveStageCardStackRole
import com.riffle.core.domain.launcher.settings.AdaptiveStageRendererCapabilities
import com.riffle.core.domain.launcher.settings.AdaptiveStageViewportDp

/** Stable, non-sensitive content rendered through the same card surface and stack projection as Home. */
@Composable
internal fun AdaptiveStageAppearancePreview(
    appearance: AdaptiveStageAppearanceSettings,
    globalReducedMotion: Boolean,
    rendererCapabilities: AdaptiveStageRendererCapabilities = adaptiveStageRendererCapabilities(),
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.semantics { contentDescription = "Cards appearance preview" },
        contentAlignment = Alignment.Center,
    ) {
        val resolution =
            appearance.resolveCardStack(
                viewport = AdaptiveStageViewportDp(maxWidth.value.toInt(), maxHeight.value.toInt()),
                capabilities = rendererCapabilities,
                globalReducedMotion = globalReducedMotion,
                // This preview is never touched -- it's a static illustration of the appearance
                // choices, not a real, tappable card stack -- so it only needs to stay legible,
                // not clear PRIMARY's much larger touch-reachable floor.
                role = AdaptiveStageCardStackRole.PREVIEW,
            )
        val effectiveAppearance = appearance.effectiveFor(rendererCapabilities)
        if (!resolution.isUsable) {
            Text(
                text = "Preview needs more space to render",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@BoxWithConstraints
        }
        // Synthetic but textured (not flat) artwork, so surface settings that only act on real
        // artwork -- blur strength, and the artwork half of saturation/contrast -- have something
        // to visibly affect here. A flat-color bitmap would blur to itself.
        val previewArtwork = remember { PREVIEW_APP_COLORS.map(::adaptiveStagePreviewArtwork) }
        // A middle-focused card so both an earlier and a later card fan out around it. With the
        // first card focused (the earlier default), every background card had to sit to one side
        // of focus, and the whole assembly read as a single curved shape -- toggling the "Fan
        // direction" setting then just flipped the direction of that visible curve, which looked
        // like a "curve orientation" control rather than "which side do earlier vs. later cards
        // stack toward." A symmetric layout makes the actual meaning legible: flipping fan
        // direction visibly swaps earlier and later cards side-to-side; curve depth changes the
        // arc's magnitude on both sides without switching them.
        CardStack(
            entries =
                resolution.layoutPolicy.entries(
                    cardCount = 3,
                    activeIndex = 1,
                    reducedMotion = resolution.reducedMotion,
                ),
            modifier = Modifier.fillMaxSize(),
            animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
            animationSpec = resolution.animation,
            reducedMotion = resolution.reducedMotion,
            stackPeakFraction = resolution.stackPeakFraction,
            // Keying on `appearance.motion` replays the entering flight-in animation whenever a
            // Motion tab setting changes, instead of only ever once at first composition. Enter
            // duration, easing, spring bounciness, and travel intensity already have a resting
            // (post-entrance) effect elsewhere in the stack, but parallax intensity's only effect
            // anywhere is on that one-time entrance -- see CardStack.kt's cardStackRenderedPose --
            // so without this, dragging its slider had literally no visible effect on an
            // already-mounted preview.
            itemKey = { entry -> "preview-${entry.cardIndex}-${appearance.motion}" },
        ) { entry, _ ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AdaptiveStageCardSurface(
                    appearance = effectiveAppearance,
                    background =
                        AdaptiveStageCardBackground(
                            artwork = previewArtwork[entry.cardIndex],
                            appSeed = PREVIEW_APP_SEEDS[entry.cardIndex],
                            appColor = PREVIEW_APP_COLORS[entry.cardIndex],
                            wallpaperAccent = MaterialTheme.colorScheme.tertiary,
                        ),
                    modifier =
                        Modifier
                            .requiredWidth(resolution.cardWidthDp.dp)
                            .requiredHeight(resolution.cardHeightDp.dp),
                    contentPadding = adaptiveStageResolvedContentPadding(resolution),
                    rendererCapabilities = rendererCapabilities,
                ) {
                    Text(
                        text = if (entry.cardIndex == 1) "Focus mode" else "Adjacent activity",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Synthetic preview",
                        modifier = Modifier.align(Alignment.BottomStart),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val PREVIEW_APP_SEEDS = listOf("calendar", "music", "messages")
private val PREVIEW_APP_COLORS =
    listOf(
        androidx.compose.ui.graphics.Color(0xFF355C7D),
        androidx.compose.ui.graphics.Color(0xFF6C5B7B),
        androidx.compose.ui.graphics.Color(0xFFC06C84),
    )

/** A small gradient-plus-highlight bitmap -- not a flat fill, so blur has visible texture to act on. */
private fun adaptiveStagePreviewArtwork(color: androidx.compose.ui.graphics.Color): ImageBitmap {
    val size = 120
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val darkArgb = color.copy(alpha = 1f).toArgb()
    val lightArgb =
        color.copy(
            red = (color.red + (1f - color.red) * 0.6f).coerceIn(0f, 1f),
            green = (color.green + (1f - color.green) * 0.6f).coerceIn(0f, 1f),
            blue = (color.blue + (1f - color.blue) * 0.6f).coerceIn(0f, 1f),
        ).toArgb()
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader =
                RadialGradient(
                    size * 0.35f,
                    size * 0.3f,
                    size * 0.9f,
                    lightArgb,
                    darkArgb,
                    Shader.TileMode.CLAMP,
                )
        }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    return bitmap.asImageBitmap()
}
