package com.riffle.app.launcher.summon

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
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
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.rememberAppIconColor
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.glance.GlanceWidgetKind
import com.riffle.core.domain.launcher.summon.RingPoint
import com.riffle.core.domain.launcher.summon.SummonPhase
import kotlin.math.roundToInt

internal val SUMMON_SLOT_SIZE: Dp = 56.dp
private val SLOT_ICON_SIZE: Dp = 36.dp
private val FOLDER_ICON_SIZE: Dp = 18.dp
private val DOCK_ICON_SIZE: Dp = 40.dp
private const val AIMED_SCALE = 1.12f
private const val LABEL_WIDTH_FRACTION = 1f

/**
 * The open ring: each slot of the current level at its place around the thumb, the name of what is aimed at above it
 * (the thumb hides the centre), and the Favourites row once Favourites has been entered. [onDockIconPlaced] reports
 * where each Dock icon is, so the gesture can tell which one is under the finger.
 */
@Composable
internal fun SummonRing(
    phase: SummonPhase.Open,
    model: SummonRingModel,
    appIconLoader: AppIconLoader,
    reducedMotion: Boolean,
    onDockIconPlaced: (Int, LayoutCoordinates) -> Unit,
    hint: String,
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
            reducedMotion = reducedMotion,
            modifier = Modifier.offset { centre.minus(halfSlotPx) },
        )
    }

    SummonLabel(
        title = (dockAimed ?: aimed)?.label.orEmpty(),
        hint = hint,
        centre = phase.layout.centre,
        radius = phase.layout.radius,
    )

    if (phase.favouritesOpen) {
        SummonFavouritesRow(
            apps = model.favourites,
            aim = phase.dockAim,
            below = phase.layout.centre,
            radius = phase.layout.radius,
            heightPx = heightPx,
            appIconLoader = appIconLoader,
            onIconPlaced = onDockIconPlaced,
        )
    }
}

@Composable
private fun SummonSlot(
    entry: SummonEntry,
    aimed: Boolean,
    appIconLoader: AppIconLoader,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val scale by animateFloatAsState(
        if (aimed) AIMED_SCALE else 1f,
        RiffleMotion.snappy(reducedMotion),
        label = "summon-slot",
    )
    val appColor = (entry as? SummonEntry.App)?.let { rememberAppIconColor(it.identity, appIconLoader) }
    GlassSurface(
        modifier =
            modifier
                .size(SUMMON_SLOT_SIZE)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        weight = if (aimed) GlassWeight.Regular else GlassWeight.Thin,
        shape = CircleShape,
        edgeTint = if (aimed) appColor ?: MaterialTheme.colorScheme.primary else Color.Unspecified,
    ) {
        Box(Modifier.size(SUMMON_SLOT_SIZE), contentAlignment = Alignment.Center) {
            SlotGlyph(entry, appIconLoader)
        }
    }
}

@Composable
private fun SlotGlyph(
    entry: SummonEntry,
    appIconLoader: AppIconLoader,
) {
    when (entry) {
        is SummonEntry.App ->
            LauncherAppIcon(entry.identity, entry.label, appIconLoader, Modifier.size(SLOT_ICON_SIZE), CircleShape)
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
                                CircleShape,
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
                tint = MaterialTheme.colorScheme.tertiary,
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
    title: String,
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
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(hint, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

private val LABEL_HEIGHT: Dp = 64.dp

/** The Dock's pinned apps in a row under the ring (over it when the ring is low), only once Favourites is entered. */
@Composable
private fun SummonFavouritesRow(
    apps: List<SummonEntry.App>,
    aim: Int?,
    below: RingPoint,
    radius: Float,
    heightPx: Float,
    appIconLoader: AppIconLoader,
    onIconPlaced: (Int, LayoutCoordinates) -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { FAVOURITES_GAP.toPx() }
    val rowPx = with(density) { (DOCK_ICON_SIZE + RiffleSpacing.m * 2).toPx() }
    val under = below.y + radius + gapPx
    // Under the ring when there is room, else over it: the Dock is where the thumb keeps going.
    val top = if (under + rowPx <= heightPx) under else below.y - radius - gapPx - rowPx
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, top.roundToInt().coerceAtLeast(0)) },
        contentAlignment = Alignment.TopCenter,
    ) {
        GlassSurface(weight = GlassWeight.Regular, shape = RoundedCornerShape(percent = 50)) {
            Row(
                modifier = Modifier.padding(RiffleSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
            ) {
                apps.forEachIndexed { index, app ->
                    val scale by animateFloatAsState(if (index == aim) AIMED_SCALE else 1f, label = "summon-dock")
                    LauncherAppIcon(
                        app.identity,
                        app.label,
                        appIconLoader,
                        Modifier
                            .size(DOCK_ICON_SIZE)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }.onGloballyPositioned { coordinates -> onIconPlaced(index, coordinates) },
                        CircleShape,
                    )
                }
            }
        }
    }
}

private val FAVOURITES_GAP: Dp = 72.dp

private fun RingPoint.minus(half: Float): IntOffset = IntOffset((x - half).roundToInt(), (y - half).roundToInt())
