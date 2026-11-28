package com.riffle.app.launcher.summon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.LocalGlassSpotlight
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.glance.GlanceWidget
import com.riffle.app.launcher.glassSpotlightSourceFor
import com.riffle.app.launcher.rememberAppIconColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight

private val READOUT_MAX_WIDTH: Dp = 420.dp
private val READOUT_ICON_SIZE: Dp = 40.dp
private val FOLDER_APP_ICON_SIZE: Dp = 28.dp
private val GLANCE_HEIGHT: Dp = 168.dp

// Nominal distances from the lit rim (beside the icon, top left) to each part of the card, for its glow.
private const val TITLE_DISTANCE_DP = 40f
private const val FIRST_ROW_DISTANCE_DP = 96f
private const val ROW_STEP_DP = 52f
private const val HINT_DISTANCE_DP = 280f
private const val MIN_GLOW_BLUR = 4f
private const val GLOW_BLUR_RANGE = 14f
private const val FOLDER_APPS_SHOWN = 8

/**
 * The readout: a card that says what the thumb is on, out of the thumb's way at the top (or bottom when the ring is
 * high). The icon lights the rim beside it (the local spotlight), and everything in the card glows in the same colour,
 * fading the further it sits from that lit rim.
 */
@Composable
internal fun SummonReadoutCard(
    readout: SummonReadout,
    placement: SummonReadoutPlacement,
    live: Boolean,
    showHint: Boolean,
    appIconLoader: AppIconLoader,
    insets: PaddingValues,
    /** Where the card is, so the gesture can tell when the finger is on it. */
    onPlaced: (LayoutCoordinates) -> Unit = {},
) {
    val entry = readout.entry
    val app = entry as? SummonEntry.App
    val light = readoutLight(entry, app?.let { rememberAppIconColor(it.identity, appIconLoader) })
    Box(
        modifier = Modifier.fillMaxSize().padding(insets).padding(RiffleSpacing.l),
        contentAlignment = if (placement == SummonReadoutPlacement.TOP) Alignment.TopCenter else Alignment.BottomCenter,
    ) {
        GlassSurface(
            modifier = Modifier.widthIn(max = READOUT_MAX_WIDTH).fillMaxWidth().onGloballyPositioned(onPlaced),
            weight = GlassWeight.Thick,
            shape = RoundedCornerShape(28.dp),
            spotlight = true,
            spotlightLocal = true,
            edgeTint = if (live) light else Color.Unspecified,
        ) {
            Column(Modifier.padding(RiffleSpacing.l), verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
                ReadoutHeader(readout, light, appIconLoader)
                readout.notifications.forEachIndexed { index, note ->
                    val glow = summonReadoutGlow(FIRST_ROW_DISTANCE_DP + index * ROW_STEP_DP)
                    Column {
                        if (note.title.isNotBlank()) {
                            GlowText(note.title, MaterialTheme.typography.titleSmall, light, glow)
                        }
                        if (note.text.isNotBlank()) {
                            GlowText(note.text, MaterialTheme.typography.bodyMedium, light, glow * 0.8f)
                        }
                    }
                }
                when (entry) {
                    is SummonEntry.Glance ->
                        Box(Modifier.fillMaxWidth().height(GLANCE_HEIGHT)) {
                            GlanceWidget(kind = entry.kind, span = entry.kind.defaultSpan, interactive = { false })
                        }
                    is SummonEntry.Folder -> FolderApps(entry, appIconLoader)
                    is SummonEntry.App, is SummonEntry.Favourites -> Unit
                }
                if (showHint) {
                    GlowText(
                        stringResource(readout.hint.label()),
                        MaterialTheme.typography.labelMedium,
                        light,
                        summonReadoutGlow(HINT_DISTANCE_DP),
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadoutHeader(
    readout: SummonReadout,
    light: Color,
    appIconLoader: AppIconLoader,
) {
    val entry = readout.entry
    val spotlight = LocalGlassSpotlight.current
    Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        val iconModifier = Modifier.glassSpotlightSourceFor(spotlight, "summon-readout", light)
        when (entry) {
            is SummonEntry.App ->
                LauncherAppIcon(
                    entry.identity,
                    entry.label,
                    appIconLoader,
                    iconModifier.size(READOUT_ICON_SIZE),
                )
            else -> Box(iconModifier.size(READOUT_ICON_SIZE), Alignment.Center) { SlotGlyph(entry, appIconLoader) }
        }
        Column {
            GlowText(entry.label, MaterialTheme.typography.titleLarge, light, summonReadoutGlow(TITLE_DISTANCE_DP))
            if (readout.notificationCount > 0) {
                GlowText(
                    stringResource(R.string.summon_card_count, readout.notificationCount),
                    MaterialTheme.typography.labelMedium,
                    light,
                    summonReadoutGlow(TITLE_DISTANCE_DP),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderApps(
    folder: SummonEntry.Folder,
    appIconLoader: AppIconLoader,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
    ) {
        folder.apps.take(FOLDER_APPS_SHOWN).forEach { app ->
            LauncherAppIcon(app.identity, app.label, appIconLoader, Modifier.size(FOLDER_APP_ICON_SIZE))
        }
    }
}

/** Text that glows in [light] with [glow] strength (0..1) under its own colour. */
@Composable
private fun GlowText(
    text: String,
    style: TextStyle,
    light: Color,
    glow: Float,
) {
    Text(
        text,
        style =
            style.copy(
                shadow =
                    Shadow(
                        color = light.copy(alpha = glow.coerceIn(0f, 1f)),
                        offset = Offset.Zero,
                        blurRadius = MIN_GLOW_BLUR + GLOW_BLUR_RANGE * glow,
                    ),
            ),
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** The colour the readout lights with: the app's own, gold for Favourites, else neutral. */
private fun readoutLight(
    entry: SummonEntry,
    appColor: Color?,
): Color =
    when (entry) {
        is SummonEntry.App -> summonGlowColor(appColor)
        is SummonEntry.Favourites -> FAVOURITES_STAR
        is SummonEntry.Folder, is SummonEntry.Glance -> Color.White
    }

private fun SummonReadoutHint.label(): Int =
    when (this) {
        SummonReadoutHint.RELEASE_TO_OPEN -> R.string.summon_hint_release_to_open
        SummonReadoutHint.OPEN_OR_KEEP -> R.string.summon_card_hint_preview
        SummonReadoutHint.RELEASE_TO_KEEP -> R.string.summon_card_hint_live
        SummonReadoutHint.REST_TO_KEEP -> R.string.summon_hint_rest_to_keep
        SummonReadoutHint.SLIDE_TO_KEEP -> R.string.summon_hint_slide_to_keep
        SummonReadoutHint.OPEN_FOLDER -> R.string.summon_hint_folder
        SummonReadoutHint.SHOW_FAVOURITES -> R.string.summon_hint_favourites
    }
