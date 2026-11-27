package com.riffle.app.launcher.summon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LABEL_TEXT_SHADOW
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.rememberAppIconColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.summon.RingLayout
import com.riffle.core.domain.launcher.summon.RingPoint
import com.riffle.core.domain.launcher.summon.SummonPhase
import kotlin.math.roundToInt

internal val SUMMON_SLOT_SIZE: Dp = 56.dp
private val SLOT_ICON_SIZE: Dp = 36.dp
private val FOLDER_ICON_SIZE: Dp = 18.dp
private val MIN_FAVOURITE_ICON_SIZE: Dp = 24.dp
private val FAVOURITES_ROW_GAP: Dp = RiffleSpacing.m
private val FAVOURITES_ROW_PADDING: Dp = RiffleSpacing.m
private const val LABEL_WIDTH_FRACTION = 1f

/**
 * The open ring: each slot of the current level at its place around the thumb, the name of what is aimed at above it
 * (the thumb hides the centre) or, once its card is previewing, the card there instead, and the Favourites row once
 * Favourites has been entered. [onDockIconPlaced] reports
 * where each Dock icon is, so the gesture can tell which one is under the finger.
 */
@Composable
internal fun SummonRing(
    phase: SummonPhase.Open,
    model: SummonRingModel,
    appIconLoader: AppIconLoader,
    look: SummonRingLook,
    onDockIconPlaced: (Int, LayoutCoordinates) -> Unit,
    /** What to do while nothing is aimed at; null when hints are off. */
    hint: String?,
    heightPx: Float,
) {
    val entries = model.level(phase.path)
    val aimed = phase.aim?.let { entries.getOrNull(it) }
    val dockAimed = phase.dockAim?.let { model.favourites.getOrNull(it) }
    val density = LocalDensity.current
    val halfSlotPx = with(density) { SUMMON_SLOT_SIZE.toPx() / 2f }

    entries.take(phase.layout.angles.size).forEachIndexed { index, entry ->
        val centre = phase.layout.position(index)
        SummonSlot(
            entry = entry,
            aimed = index == phase.aim,
            appIconLoader = appIconLoader,
            look = look,
            modifier = Modifier.offset { centre.minus(halfSlotPx) },
        )
    }

    // Once something is aimed at, the readout says what it is; until then, how to aim.
    if (hint != null && aimed == null && dockAimed == null) {
        SummonLabel(hint = hint, centre = phase.layout.centre, radius = phase.layout.radius)
    }

    if (phase.favouritesOpen) {
        SummonFavouritesRow(
            apps = model.favourites,
            aim = phase.dockAim,
            ring = phase.layout,
            heightPx = heightPx,
            appIconLoader = appIconLoader,
            look = look,
            onIconPlaced = onDockIconPlaced,
        )
    }
}

@Composable
private fun SummonSlot(
    entry: SummonEntry,
    aimed: Boolean,
    appIconLoader: AppIconLoader,
    look: SummonRingLook,
    modifier: Modifier,
) {
    val state = animateSummonFocus(aimed, look.reducedMotion)
    val focus =
        Modifier.graphicsLayer {
            scaleX = state.scale
            scaleY = state.scale
        }
    when (entry) {
        // An app is its own light: the icon alone, glowing in its colour, with nothing drawn round it.
        is SummonEntry.App -> {
            val glow = summonGlowColor(rememberAppIconColor(entry.identity, appIconLoader))
            Box(modifier.size(SUMMON_SLOT_SIZE).then(focus), contentAlignment = Alignment.Center) {
                LauncherAppIcon(
                    entry.identity,
                    entry.label,
                    appIconLoader,
                    Modifier.size(look.iconSize).summonGlow(glow) { state.glow },
                )
            }
        }
        // Glyphs (folders, glances, Favourites) keep a glass disc: a bare glyph would vanish on a light wallpaper.
        else -> {
            val glow = if (entry is SummonEntry.Favourites) FAVOURITES_STAR else Color.White
            GlassSurface(
                modifier = modifier.size(SUMMON_SLOT_SIZE).then(focus).summonGlow(glow) { state.glow },
                weight = if (aimed) GlassWeight.Regular else GlassWeight.Thin,
                shape = CircleShape,
                edgeTint = if (aimed) glow else Color.Unspecified,
            ) {
                Box(Modifier.size(SUMMON_SLOT_SIZE), contentAlignment = Alignment.Center) {
                    SlotGlyph(entry, appIconLoader)
                }
            }
        }
    }
}

@Composable
internal fun SlotGlyph(
    entry: SummonEntry,
    appIconLoader: AppIconLoader,
) {
    when (entry) {
        is SummonEntry.App ->
            LauncherAppIcon(entry.identity, entry.label, appIconLoader, Modifier.size(SLOT_ICON_SIZE))
        is SummonEntry.Folder ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                entry.apps.take(FOLDER_PREVIEW_COUNT).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        row.forEach { app ->
                            LauncherAppIcon(
                                app.identity,
                                app.label,
                                appIconLoader,
                                Modifier.size(FOLDER_ICON_SIZE),
                            )
                        }
                    }
                }
            }
        is SummonEntry.Glance -> Icon(entry.kind.glyph(), contentDescription = null)
        is SummonEntry.Favourites ->
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = FAVOURITES_STAR,
            )
    }
}

private const val FOLDER_PREVIEW_COUNT = 4

private fun GlanceWidgetKind.glyph(): ImageVector =
    when (this) {
        GlanceWidgetKind.NOW_PLAYING -> Icons.Filled.PlayArrow
        GlanceWidgetKind.NEXT_EVENT -> Icons.Filled.DateRange
        GlanceWidgetKind.TOP_STORY -> Icons.AutoMirrored.Filled.List
    }

/** The name of what is aimed at, centred above the ring; below it when the ring sits too high for room above. */
@Composable
private fun SummonLabel(
    hint: String,
    centre: RingPoint,
    radius: Float,
) {
    val density = LocalDensity.current
    val labelHeightPx = with(density) { LABEL_HEIGHT.toPx() }
    val slotPx = with(density) { SUMMON_SLOT_SIZE.toPx() }
    val above = centre.y - radius - slotPx - labelHeightPx
    val top = if (above > labelHeightPx) above else centre.y + radius + slotPx
    Column(
        modifier =
            Modifier
                .fillMaxWidth(LABEL_WIDTH_FRACTION)
                .offset { IntOffset(0, top.roundToInt()) }
                .padding(horizontal = RiffleSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            hint,
            style = MaterialTheme.typography.labelMedium.copy(shadow = LABEL_TEXT_SHADOW),
            color = Color.White,
            textAlign = TextAlign.Center,
        )
    }
}

private val LABEL_HEIGHT: Dp = 64.dp

/** The Dock's pinned apps in a row under the ring (over it when the ring is low), only once Favourites is entered. */
@Composable
private fun SummonFavouritesRow(
    apps: List<SummonEntry.App>,
    aim: Int?,
    ring: RingLayout,
    heightPx: Float,
    appIconLoader: AppIconLoader,
    look: SummonRingLook,
    onIconPlaced: (Int, LayoutCoordinates) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { FAVOURITES_GAP.toPx() }
    val rowPx = with(density) { (look.iconSize + FAVOURITES_ROW_PADDING * 2).toPx() }
    val under = ring.centre.y + ring.radius + gapPx
    // Under the ring when there is room, else over it: the Dock is where the thumb keeps going.
    val top = if (under + rowPx <= heightPx) under else ring.centre.y - ring.radius - gapPx - rowPx
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, top.roundToInt().coerceAtLeast(0)) }
                .padding(horizontal = RiffleSpacing.l),
        contentAlignment = Alignment.TopCenter,
    ) {
        val iconSize = summonFavouriteIconSize(maxWidth, apps.size, look.iconSize)
        GlassSurface(weight = GlassWeight.Regular, shape = RoundedCornerShape(percent = 50)) {
            Row(
                modifier = Modifier.padding(FAVOURITES_ROW_PADDING),
                horizontalArrangement = Arrangement.spacedBy(FAVOURITES_ROW_GAP),
            ) {
                apps.forEachIndexed { index, app ->
                    val focus = animateSummonFocus(index == aim, look.reducedMotion)
                    val glow = summonGlowColor(rememberAppIconColor(app.identity, appIconLoader))
                    LauncherAppIcon(
                        app.identity,
                        app.label,
                        appIconLoader,
                        Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                scaleX = focus.scale
                                scaleY = focus.scale
                            }.summonGlow(glow) { focus.glow }
                            .onGloballyPositioned { coordinates -> onIconPlaced(index, coordinates) },
                    )
                }
            }
        }
    }
}

private val FAVOURITES_GAP: Dp = 72.dp
internal val FAVOURITES_STAR = Color(0xFFFFC857)

/** How big each Favourite is so the whole row fits [availableWidth]: full size when it can, never below a floor. */
internal fun summonFavouriteIconSize(
    availableWidth: Dp,
    count: Int,
    fullSize: Dp,
): Dp {
    if (count == 0) return fullSize
    val fitting = (availableWidth - FAVOURITES_ROW_PADDING * 2 - FAVOURITES_ROW_GAP * (count - 1)) / count
    return fitting.coerceIn(MIN_FAVOURITE_ICON_SIZE.coerceAtMost(fullSize), fullSize)
}

/** How the ring draws: its icons' size and whether motion is reduced. */
internal data class SummonRingLook(
    val iconSize: Dp,
    val reducedMotion: Boolean,
)

/** The Favourites row's whole width with [count] icons of [iconSize]. */
internal fun summonFavouritesRowWidth(
    iconSize: Dp,
    count: Int,
): Dp = iconSize * count + FAVOURITES_ROW_GAP * (count - 1).coerceAtLeast(0) + FAVOURITES_ROW_PADDING * 2

private fun RingPoint.minus(half: Float): IntOffset = IntOffset((x - half).roundToInt(), (y - half).roundToInt())
