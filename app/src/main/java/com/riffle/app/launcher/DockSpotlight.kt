package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.isHorizontalEdge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The dock's spotlight: each icon's own colour glows onto the dock's two long edges at the point
 * along the run where that icon sits, as one continuous gradient, fading inward from the edge.
 *
 * Ported from the desktop glass theme (the window edge-border compositor plugin and the status
 * bar's zone-colour edge strips): the surface's rim is lit by what it holds rather than by a fixed
 * accent, so a dock of blue and orange apps reads blue at one end and orange at the other. The
 * edge facing into the screen carries the stronger glow; the edge against the screen's own edge a
 * weaker one, like light spilling out of the dock.
 *
 * Only icons with a vivid enough dominant colour contribute (see `dominantColorOf`) -- grey icons,
 * folders and widgets add nothing, matching the desktop version's "neutral content contributes 0".
 */

/** One lit point along the dock's run: [position] is 0..1 from the run's start. */
internal data class DockSpotlightStop(
    val position: Float,
    val color: Color,
)

/**
 * Turns icon centres (in px along the run, relative to the run's start) into the sorted gradient
 * stops the edge glow is drawn from. Centres outside the visible run -- icons scrolled out of the
 * strip -- are dropped, so the glow follows what is actually on screen.
 */
internal fun dockSpotlightStops(
    centers: List<Pair<Float, Color>>,
    runLengthPx: Float,
): List<DockSpotlightStop> {
    if (runLengthPx <= 0f) return emptyList()
    return centers
        .filter { (centerPx, _) -> centerPx in 0f..runLengthPx }
        .map { (centerPx, color) -> DockSpotlightStop(centerPx / runLengthPx, dockSpotlightGlowColor(color)) }
        .sortedBy { stop -> stop.position }
}

/**
 * Lifts an icon's dominant colour so it reads as light rather than paint: partially normalises its
 * brightness toward a max channel of 1 (fully would turn muted icons neon), then mixes a little
 * white in. Same two steps, same constants, as the desktop plugin's vivid-glow path.
 */
internal fun dockSpotlightGlowColor(color: Color): Color {
    val max = maxOf(color.red, color.green, color.blue)
    val scale = if (max > MIN_CHANNEL) (1f - SPOTLIGHT_VIVID_NORM) + SPOTLIGHT_VIVID_NORM / max else 1f
    fun lift(channel: Float): Float {
        val normalised = (channel * scale).coerceAtMost(1f)
        return normalised + (1f - normalised) * SPOTLIGHT_WHITE_MIX
    }
    return Color(lift(color.red), lift(color.green), lift(color.blue))
}

/**
 * The gradient stops along one edge: the lit points themselves, held flat out to both ends of the
 * run so the nearest icon's colour reaches each corner instead of the glow stopping short of it.
 */
internal fun dockSpotlightEdgeStops(
    stops: List<DockSpotlightStop>,
    alpha: Float,
): Array<Pair<Float, Color>> {
    if (stops.isEmpty() || alpha <= 0f) return emptyArray()
    val lit = stops.map { stop -> stop.position to stop.color.copy(alpha = alpha) }
    return (listOf(0f to lit.first().second) + lit + listOf(1f to lit.last().second)).toTypedArray()
}

/**
 * Collects where each icon currently sits and what colour it is. Positions are reported in root
 * coordinates by [dockSpotlightSource] and only read at draw time, so scrolling the strip or an
 * icon arriving moves the glow without recomposing the dock.
 */
@Stable
internal class DockSpotlightState {
    private val sources = mutableStateMapOf<Any, Pair<Offset, Color>>()
    private var originInRoot by mutableStateOf(Offset.Zero)

    fun report(
        key: Any,
        centerInRoot: Offset,
        color: Color,
    ) {
        sources[key] = centerInRoot to color
    }

    fun remove(key: Any) {
        sources.remove(key)
    }

    fun updateOrigin(origin: Offset) {
        originInRoot = origin
    }

    fun stopsFor(
        size: Size,
        runsHorizontally: Boolean,
    ): List<DockSpotlightStop> =
        dockSpotlightStops(
            centers =
                sources.values.map { (center, color) ->
                    val local = center - originInRoot
                    (if (runsHorizontally) local.x else local.y) to color
                },
            runLengthPx = if (runsHorizontally) size.width else size.height,
        )
}

/** The spotlight the dock strip around this composable is collecting into, if it has one. */
internal val LocalDockSpotlight = staticCompositionLocalOf<DockSpotlightState?> { null }

/** Remembers a spotlight for one dock strip, or null when the glass theme is switched off. */
@Composable
internal fun rememberDockSpotlightState(): DockSpotlightState? {
    val enabled = LocalLiquidGlassSettings.current.enabled
    val state = remember { DockSpotlightState() }
    return state.takeIf { enabled }
}

/**
 * The dominant colour of [identity]'s icon, loaded off the main thread the same way the generated
 * page's cards load theirs. Null outside a spotlit dock (nothing to load for), for a non-app slot,
 * or for an icon with no vivid colour.
 */
@Composable
internal fun rememberDockSpotlightColor(
    identity: AppIdentity?,
    appIconLoader: AppIconLoader,
): Color? {
    val spotlit = LocalDockSpotlight.current != null
    var color by remember(identity, appIconLoader, spotlit) {
        mutableStateOf(identity?.takeIf { spotlit }?.let(appIconLoader::cachedColorFor))
    }
    LaunchedEffect(identity, appIconLoader, spotlit) {
        if (!spotlit || identity == null) return@LaunchedEffect
        color =
            appIconLoader.cachedColorFor(identity)
                ?: withContext(Dispatchers.Default) { appIconLoader.colorFor(identity) }
    }
    return color
}

/**
 * Reports this icon's centre and [color] to the enclosing dock's spotlight. A no-op outside a
 * spotlit dock, or for an icon with no colour of its own (null [color]).
 */
@Composable
internal fun Modifier.dockSpotlightSource(
    key: Any,
    color: Color?,
): Modifier {
    val spotlight = LocalDockSpotlight.current ?: return this
    DisposableEffect(spotlight, key, color) {
        onDispose { spotlight.remove(key) }
    }
    return if (color == null) {
        this
    } else {
        onGloballyPositioned { coordinates ->
            val size = coordinates.size
            spotlight.report(
                key = key,
                centerInRoot = coordinates.positionInRoot() + Offset(size.width / 2f, size.height / 2f),
                color = color,
            )
        }
    }
}

/**
 * Draws [spotlight]'s edge glow behind this node's content. Chain it after the dock's own
 * background, which already clips to the dock's shape, so the glow follows the rounded corners.
 * [alphaProvider] is the dock pull's background fade, read at draw time.
 */
internal fun Modifier.dockSpotlight(
    spotlight: DockSpotlightState?,
    position: DockPosition,
    alphaProvider: () -> Float,
): Modifier {
    if (spotlight == null) return this
    return onGloballyPositioned { coordinates -> spotlight.updateOrigin(coordinates.positionInRoot()) }
        .drawBehind {
            val fade = alphaProvider().coerceIn(0f, 1f)
            if (fade <= 0f) return@drawBehind
            val runsHorizontally = position.isHorizontalEdge
            val stops = spotlight.stopsFor(size, runsHorizontally)
            if (stops.isEmpty()) return@drawBehind
            // The edge facing into the screen is the inner one: the top of a bottom dock, the
            // right of a left dock, and so on.
            val innerAtStart = position == DockPosition.BOTTOM || position == DockPosition.RIGHT
            drawSpotlightEdge(
                stops = stops,
                runsHorizontally = runsHorizontally,
                atStart = innerAtStart,
                depthPx = SPOTLIGHT_INNER_DEPTH_DP.dp.toPx(),
                alpha = SPOTLIGHT_INNER_ALPHA * fade,
            )
            drawSpotlightEdge(
                stops = stops,
                runsHorizontally = runsHorizontally,
                atStart = !innerAtStart,
                depthPx = SPOTLIGHT_OUTER_DEPTH_DP.dp.toPx(),
                alpha = SPOTLIGHT_OUTER_ALPHA * fade,
            )
        }
}

/**
 * One edge's glow: the colour gradient along the run, masked by an inward fade across [depthPx].
 * [atStart] picks the top (horizontal run) or left (vertical run) edge.
 */
@Suppress("LongParameterList")
private fun DrawScope.drawSpotlightEdge(
    stops: List<DockSpotlightStop>,
    runsHorizontally: Boolean,
    atStart: Boolean,
    depthPx: Float,
    alpha: Float,
) {
    val edgeStops = dockSpotlightEdgeStops(stops, alpha)
    if (edgeStops.isEmpty()) return
    val crossLength = if (runsHorizontally) size.height else size.width
    val depth = depthPx.coerceAtMost(crossLength * SPOTLIGHT_MAX_DEPTH_FRACTION)
    if (depth <= 0f) return

    val along =
        if (runsHorizontally) {
            Brush.horizontalGradient(colorStops = edgeStops, startX = 0f, endX = size.width)
        } else {
            Brush.verticalGradient(colorStops = edgeStops, startY = 0f, endY = size.height)
        }
    val edgeStart = if (atStart) 0f else crossLength - depth
    val (topLeft, bandSize) =
        if (runsHorizontally) {
            Offset(0f, edgeStart) to Size(size.width, depth)
        } else {
            Offset(edgeStart, 0f) to Size(depth, size.height)
        }

    // Colour along the run, then mask it to fade inward from the edge -- the same two-pass
    // "gradient, then destination-in fade" the status bar's edge strips use.
    drawContext.canvas.saveLayer(Rect(topLeft, bandSize), Paint())
    drawRect(brush = along, topLeft = topLeft, size = bandSize)
    val opaque = Color.Black
    val clear = Color.Black.copy(alpha = 0f)
    val fadeColors = if (atStart) listOf(opaque, clear) else listOf(clear, opaque)
    val fade =
        if (runsHorizontally) {
            Brush.verticalGradient(fadeColors, startY = topLeft.y, endY = topLeft.y + depth)
        } else {
            Brush.horizontalGradient(fadeColors, startX = topLeft.x, endX = topLeft.x + depth)
        }
    drawRect(brush = fade, topLeft = topLeft, size = bandSize, blendMode = BlendMode.DstIn)
    drawContext.canvas.restore()
}

private const val MIN_CHANNEL = 1e-4f
private const val SPOTLIGHT_VIVID_NORM = 0.45f
private const val SPOTLIGHT_WHITE_MIX = 0.12f

// Edge depths and strengths, from the status bar's 7px/0.55 and 5px/0.30 strips -- scaled up a
// touch because a dock is taller than a bar pill.
private const val SPOTLIGHT_INNER_DEPTH_DP = 10
private const val SPOTLIGHT_OUTER_DEPTH_DP = 7
private const val SPOTLIGHT_INNER_ALPHA = 0.55f
private const val SPOTLIGHT_OUTER_ALPHA = 0.30f

/** Keeps the two edges' glows from meeting in the middle of a slim dock. */
private const val SPOTLIGHT_MAX_DEPTH_FRACTION = 0.3f
