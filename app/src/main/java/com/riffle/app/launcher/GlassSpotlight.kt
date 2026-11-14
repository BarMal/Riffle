package com.riffle.app.launcher

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.designsystem.GlassMaterialTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ceil

/*
 * The glass spotlight: each coloured thing's own colour glows onto the surface's two long edges at
 * the point along its run where that thing sits, as one continuous gradient, fading inward from the
 * edge. Used by the dock (icons) and by every glass surface that holds coloured things: open
 * folders, the search results panel, Stream cards.
 *
 * Ported from the desktop glass theme (the window edge-border compositor plugin and the status
 * bar's zone-colour edge strips): the surface's rim is lit by what it holds rather than by a fixed
 * accent, so a dock of blue and orange apps reads blue at one end and orange at the other. The
 * edge facing into the screen carries the stronger glow; the edge against the screen's own edge a
 * weaker one, like light spilling out of the surface.
 *
 * The run is the surface's longer axis by default ([GlassSpotlightAxis.Longest]), or fixed (the
 * dock's, which follows its edge). A source's colour is projected onto that axis wherever it sits
 * inside the surface; the outline it follows is the surface's own [androidx.compose.ui.graphics.Shape].
 *
 * Only icons with a vivid enough dominant colour light the edge (see `dominantColorOf`). Grey icons,
 * folders and widgets are still placed on the run, but as unlit points: the glow fades out across
 * them rather than a neighbour's colour carrying straight through, matching the desktop version's
 * "neutral content contributes 0".
 */

/**
 * One point along the surface's run: [position] is 0..1 from the run's start. An unlit point (an icon
 * with no colour of its own) carries its nearest lit neighbour's colour at zero alpha, so the glow
 * fades out toward it without the gradient passing through black on the way.
 */
internal data class GlassSpotlightStop(
    val position: Float,
    val color: Color,
)

/**
 * Turns icon centres (in px along the run, relative to the run's start) and their colours (null for
 * an icon with none) into the sorted gradient stops the edge glow is drawn from. Centres outside the
 * visible run -- icons scrolled out of the strip -- are dropped, so the glow follows what is
 * actually on screen. Nothing lit, nothing to draw.
 */
internal fun glassSpotlightStops(
    centers: List<Pair<Float, Color?>>,
    runLengthPx: Float,
): List<GlassSpotlightStop> {
    val visible =
        centers
            .filter { (centerPx, _) -> runLengthPx > 0f && centerPx in 0f..runLengthPx }
            .map { (centerPx, color) -> centerPx / runLengthPx to color?.let(::glassSpotlightGlowColor) }
            .sortedBy { (position, _) -> position }
    val lit = visible.mapNotNull { (position, color) -> color?.let { position to it } }
    return if (lit.isEmpty()) {
        emptyList()
    } else {
        visible.map { (position, color) ->
            GlassSpotlightStop(
                position = position,
                color = color ?: lit.minBy { (litPosition, _) -> abs(litPosition - position) }.second.copy(alpha = 0f),
            )
        }
    }
}

/**
 * Lifts an icon's dominant colour so it reads as light rather than paint: partially normalises its
 * brightness toward a max channel of 1 (fully would turn muted icons neon), then mixes a little
 * white in. Same two steps, same constants, as the desktop plugin's vivid-glow path.
 */
internal fun glassSpotlightGlowColor(color: Color): Color {
    val max = maxOf(color.red, color.green, color.blue)
    val scale = if (max > MIN_CHANNEL) (1f - SPOTLIGHT_VIVID_NORM) + SPOTLIGHT_VIVID_NORM / max else 1f
    val lift = { channel: Float ->
        val normalised = (channel * scale).coerceAtMost(1f)
        normalised + (1f - normalised) * SPOTLIGHT_WHITE_MIX
    }
    return Color(lift(color.red), lift(color.green), lift(color.blue))
}

/**
 * The gradient stops along one edge: the lit points themselves, held flat out to both ends of the
 * run so the nearest icon's colour reaches each corner instead of the glow stopping short of it.
 */
internal fun glassSpotlightEdgeStops(
    stops: List<GlassSpotlightStop>,
    alpha: Float,
): Array<Pair<Float, Color>> {
    if (stops.isEmpty() || alpha <= 0f) return emptyArray()
    val lit = stops.map { stop -> stop.position to stop.color.copy(alpha = stop.color.alpha * alpha) }
    return (listOf(0f to lit.first().second) + lit + listOf(1f to lit.last().second)).toTypedArray()
}

/** Which way a surface's spotlight runs. */
internal enum class GlassSpotlightAxis {
    Horizontal,
    Vertical,

    /** Along whichever of width and height is longer, so a card or a panel needs no choice. */
    Longest,
}

/** Whether the run goes along the x axis for a surface of [size] (a square runs horizontally). */
internal fun GlassSpotlightAxis.runsHorizontally(size: Size): Boolean =
    when (this) {
        GlassSpotlightAxis.Horizontal -> true
        GlassSpotlightAxis.Vertical -> false
        GlassSpotlightAxis.Longest -> size.width >= size.height
    }

/**
 * The corner radius, in px, the glow follows for a surface of [size] drawn as [shape]: the shape's
 * own radius when it has one (rounded and continuous corners alike), a square surface's none, and
 * half the short side (a pill) for any other outline, which is the closest a plain rounded
 * rectangle comes to an arbitrary one.
 */
internal fun glassSpotlightCornerRadiusPx(
    shape: Shape,
    size: Size,
    density: Density,
): Float =
    when (shape) {
        is CornerBasedShape -> shape.topStart.toPx(size, density)
        RectangleShape -> 0f
        else -> minOf(size.width, size.height) / 2f
    }.coerceIn(0f, minOf(size.width, size.height) / 2f)

/**
 * Collects where each icon currently sits and what colour it is, and eases the glow on screen
 * toward what that describes (see `GlassSpotlightFlow.kt`). Positions are reported in root
 * coordinates by [glassSpotlightSource]; nothing here recomposes the surface -- the glow is redrawn
 * from [shown], and only on the frames it actually changes.
 */
@Stable
internal class GlassSpotlightState(
    private val axis: GlassSpotlightAxis = GlassSpotlightAxis.Longest,
) {
    private val sources = mutableStateMapOf<Any, Pair<Offset, Color?>>()
    private var originInRoot by mutableStateOf(Offset.Zero)
    private var layoutSize by mutableStateOf(Size.Zero)

    // One array, eased in place on the main thread by [run]; re-set after every change under a
    // never-equal policy, so whatever reads [shown] (the draw cache) sees each change.
    private val shownState = mutableStateOf(emptyGlassSpotlightProfile(), neverEqualPolicy())
    private val scratch = emptyGlassSpotlightProfile()

    // The last display frame a settle stepped on, kept across restarts: while the icons keep
    // moving (a scroll), every frame brings a new target and restarts the settle, and each restart
    // must still step on its first frame rather than spend it taking a timestamp. 0 when settled.
    private var lastFrameNanos = 0L

    /** Bakes [shown] into the image drawn; reuses its buffers between frames. */
    val glowBaker = GlassSpotlightGlowBaker()

    /** The glow currently on screen. Reading it in a draw cache ties the cache to its changes. */
    val shown: FloatArray
        get() = shownState.value

    fun report(
        key: Any,
        centerInRoot: Offset,
        color: Color?,
    ) {
        // Every layout pass reports again; only a real change should invalidate the glow.
        val source = centerInRoot to color
        if (sources[key] != source) sources[key] = source
    }

    fun remove(key: Any) {
        sources.remove(key)
    }

    fun updateLayout(
        origin: Offset,
        size: Size,
    ) {
        originInRoot = origin
        layoutSize = size
    }

    /** Whether the glow runs along x for the surface as last laid out. */
    val runsHorizontally: Boolean
        get() = axis.runsHorizontally(layoutSize)

    fun stopsFor(
        size: Size,
        runsHorizontally: Boolean,
    ): List<GlassSpotlightStop> =
        glassSpotlightStops(
            centers =
                sources.values.map { (center, color) ->
                    val local = center - originInRoot
                    (if (runsHorizontally) local.x else local.y) to color
                },
            runLengthPx = if (runsHorizontally) size.width else size.height,
        )

    /**
     * Follows the icons for as long as the surface is on screen: each time they move, arrive, leave
     * or change colour, [shown] eases toward the new glow, one display frame at a time, then the
     * frame loop stops -- a surface at rest asks for no frames at all. With [animate] off (reduced
     * motion, or a flow of 0), and whenever nothing is lit yet, it jumps straight there instead, so
     * a surface never fades its glow in on launch or as icon colours finish loading.
     * [flowSeconds] is the flow's time constant (the "Spotlight flow" setting).
     */
    suspend fun run(
        animate: Boolean,
        flowSeconds: Float,
    ) {
        snapshotFlow { glassSpotlightTargetProfile(stopsFor(layoutSize, runsHorizontally)) }
            .collectLatest { target ->
                if (!target.contentEquals(shown)) {
                    if (animate && flowSeconds > 0f && glassSpotlightProfileIsLit(shown)) {
                        settleTowards(target, flowSeconds)
                    } else {
                        showNow(target)
                    }
                }
            }
    }

    private fun showNow(target: FloatArray) {
        val profile = shownState.value
        target.copyInto(profile)
        shownState.value = profile
        lastFrameNanos = 0L
    }

    private suspend fun settleTowards(
        target: FloatArray,
        flowSeconds: Float,
    ) {
        var elapsed = 0f
        var settled = false
        while (!settled) {
            val frame = withFrameNanos { it }
            val dt =
                if (lastFrameNanos == 0L) {
                    FIRST_FRAME_SECONDS
                } else {
                    ((frame - lastFrameNanos) / NANOS_PER_SECOND).coerceIn(0f, DOCK_SPOTLIGHT_MAX_FRAME_SECONDS)
                }
            lastFrameNanos = frame
            elapsed += dt
            val profile = shownState.value
            settled = stepGlassSpotlightProfile(profile, target, dt, flowSeconds, scratch) ||
                elapsed >= glassSpotlightMaxSettleSeconds(flowSeconds)
            if (settled) {
                target.copyInto(profile)
                lastFrameNanos = 0L
            }
            shownState.value = profile
        }
    }
}

private const val NANOS_PER_SECOND = 1_000_000_000f

/** The step taken on the first frame of a settle, with no previous frame to measure from. */
private const val FIRST_FRAME_SECONDS = 1f / 60f

/** The spotlight the glass surface around this composable is collecting into, if it has one. */
internal val LocalGlassSpotlight = staticCompositionLocalOf<GlassSpotlightState?> { null }

/**
 * Remembers a spotlight for one glass surface running along [axis], and keeps its glow following
 * the coloured things inside it -- or returns null, and runs nothing, when this surface draws no
 * background of its own ([enabled] false).
 */
@Composable
internal fun rememberGlassSpotlightState(
    axis: GlassSpotlightAxis = GlassSpotlightAxis.Longest,
    enabled: Boolean = true,
): GlassSpotlightState? {
    val glass = LocalLiquidGlassSettings.current
    val active = enabled
    val state = remember(axis) { GlassSpotlightState(axis) }
    if (active) {
        LaunchedEffect(state, glass.reducedMotion, glass.spotlightFlowSeconds) {
            state.run(animate = !glass.reducedMotion, flowSeconds = glass.spotlightFlowSeconds)
        }
    }
    return state.takeIf { active }
}

/**
 * The dominant colour of [identity]'s icon, loaded off the main thread the same way the generated
 * page's cards load theirs. Null outside a spotlit surface (nothing to load for), for a non-app slot,
 * or for an icon with no vivid colour.
 */
@Composable
internal fun rememberGlassSpotlightColor(
    identity: AppIdentity?,
    appIconLoader: AppIconLoader,
): Color? {
    val spotlit = LocalGlassSpotlight.current != null
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
 * Reports the centre of this icon's visible part and its [color] to the enclosing surface's spotlight
 * -- null [color] for an icon with none, which still marks its place on the run as unlit. A no-op
 * outside a spotlit surface.
 */
@Composable
internal fun Modifier.glassSpotlightSource(
    key: Any,
    color: Color?,
): Modifier {
    val spotlight = LocalGlassSpotlight.current ?: return this
    DisposableEffect(spotlight, key, color) {
        onDispose { spotlight.remove(key) }
    }
    return onGloballyPositioned { coordinates ->
        // Only what is actually on screen lights the edge: bounds clipped by the strip's own
        // scroll viewport, so an icon scrolled out of its section stops glowing -- rather than
        // casting its colour under the other section, which it still sits beneath, unclipped.
        val visible = coordinates.boundsInRoot()
        if (visible.width <= 0f || visible.height <= 0f) {
            spotlight.remove(key)
        } else {
            spotlight.report(key = key, centerInRoot = visible.center, color = color)
        }
    }
}

/**
 * Draws [spotlight]'s edge glow behind this node's content, following [shape]'s outline. Chain it
 * after the surface's own background, which already clips to that shape. [innerAtStart] says which
 * long edge faces into the screen and carries the stronger glow (the top of a bottom dock, say).
 * [alphaProvider] is the surface's own fade (the dock pull's background fade), read at draw time.
 * Under reduced transparency the glow is kept at half strength.
 */
@Composable
internal fun Modifier.glassSpotlight(
    spotlight: GlassSpotlightState?,
    shape: Shape,
    innerAtStart: Boolean = true,
    alphaProvider: () -> Float = { 1f },
): Modifier {
    if (spotlight == null) return this
    val reducedScale =
        if (LocalLiquidGlassSettings.current.reducedTransparency) {
            GlassMaterialTokens.REDUCED_TRANSPARENCY_SPOTLIGHT_SCALE
        } else {
            1f
        }
    return onGloballyPositioned { coordinates ->
        val size = coordinates.size
        spotlight.updateLayout(coordinates.positionInRoot(), Size(size.width.toFloat(), size.height.toFloat()))
    }.drawWithCache {
        // Reading the glow here ties the cache to it: it is rebaked only on frames it changes
        // (while it flows) and when the surface resizes -- never for a surface at rest.
        val profile = spotlight.shown
        val runsHorizontally = spotlight.runsHorizontally
        val runPx = if (runsHorizontally) size.width else size.height
        val crossPx = if (runsHorizontally) size.height else size.width
        val maxCrossDepth = crossPx * MAX_DEPTH_FRACTION
        val glow =
            if (glassSpotlightProfileIsLit(profile) && runPx >= 1f && crossPx >= 1f) {
                spotlight.glowBaker.bake(
                    profile = profile,
                    runPx = runPx,
                    crossPx = crossPx,
                    cornerRadiusPx = glassSpotlightCornerRadiusPx(shape, size, this),
                    innerAtStart = innerAtStart,
                    runsHorizontally = runsHorizontally,
                    edges =
                        GlassSpotlightGlowEdges(
                            innerDepthPx = SPOTLIGHT_INNER_DEPTH_DP.dp.toPx().coerceAtMost(maxCrossDepth),
                            outerDepthPx = SPOTLIGHT_OUTER_DEPTH_DP.dp.toPx().coerceAtMost(maxCrossDepth),
                            endDepthPx = SPOTLIGHT_END_DEPTH_DP.dp.toPx().coerceAtMost(runPx * MAX_DEPTH_FRACTION),
                            innerAlpha = SPOTLIGHT_INNER_ALPHA,
                            outerAlpha = SPOTLIGHT_OUTER_ALPHA,
                            endAlpha = SPOTLIGHT_END_ALPHA,
                        ),
                    texelPx = SPOTLIGHT_TEXEL_DP.dp.toPx(),
                )
            } else {
                null
            }
        val dstSize = IntSize(ceil(size.width).toInt(), ceil(size.height).toInt())
        onDrawBehind {
            val fade = alphaProvider().coerceIn(0f, 1f) * reducedScale
            if (glow != null && fade > 0f) {
                drawImage(image = glow, dstSize = dstSize, alpha = fade, filterQuality = FilterQuality.Low)
            }
        }
    }
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

// The short ends sit between the two long edges' strengths, so the rim reads as one continuous
// glow round the dock rather than two lit strips.
private const val SPOTLIGHT_END_DEPTH_DP = 8
private const val SPOTLIGHT_END_ALPHA = 0.42f

/** Keeps opposite edges' glows from meeting in the middle of a slim dock. */
private const val MAX_DEPTH_FRACTION = 0.3f

/** The glow is soft, so it is baked at this coarse a texel and stretched: ~12k texels for a phone dock. */
private const val SPOTLIGHT_TEXEL_DP = 1.5f
