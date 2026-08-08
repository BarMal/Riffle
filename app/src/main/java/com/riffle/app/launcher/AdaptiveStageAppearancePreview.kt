@file:Suppress("MaxLineLength")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        CardStack(
            entries = resolution.layoutPolicy.entries(cardCount = 3, activeIndex = 0, reducedMotion = resolution.reducedMotion),
            modifier = Modifier.fillMaxSize(),
            animationProfile = CardStackAnimationProfile.CARD_FLIGHT,
            animationSpec = resolution.animation,
            reducedMotion = resolution.reducedMotion,
            itemKey = { entry -> "preview-${entry.cardIndex}" },
        ) { entry, _ ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AdaptiveStageCardSurface(
                    appearance = effectiveAppearance,
                    background =
                        AdaptiveStageCardBackground(
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
                        text = if (entry.cardIndex == 0) "Focus mode" else "Earlier activity",
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
