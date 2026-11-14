package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.designsystem.RiffleMotionTokens
import com.riffle.core.domain.launcher.dock.DockMotionTuning
import com.riffle.core.domain.launcher.dockpull.pullDirection
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeEntrance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The entrance of the Dock and the page handle when Home appears (see [HomeEntrance]): one progress, 0 to 1, that both
 * read where they are drawn (a graphics layer: alpha and a translation toward the Dock's edge), so the entrance
 * relays nothing out and recomposes nothing per frame. Nothing about where the Dock or the handle is laid out depends
 * on it: they stand in their final place from the first frame and only their alpha and translation change.
 *
 * It plays when Home appears after having been hidden (a cold start; returning to Home after another app, the lock
 * screen or Settings covered it) and is held at 1 otherwise: a sheet, the Stream or search coming and going over Home
 * does not replay it, nor does a configuration change.
 */
@Stable
internal class HomeEntranceState(
    initial: Float,
    private val scope: CoroutineScope,
) {
    private val animatable = Animatable(initial)
    private var job: Job? = null

    /** The Dock's edge: the direction the Dock and the handle rise from. */
    var edge: DockPosition by mutableStateOf(DockPosition.BOTTOM)

    /** Reduced motion: a plain short fade, no rise. */
    var reducedMotion: Boolean by mutableStateOf(false)

    /** Where the entrance is, 0 to 1. Read it in a graphics layer or a draw block, not in composition. */
    val progress: Float get() = animatable.value

    /**
     * Whether the entrance has arrived (or was never due): the Dock's area may ease size changes from then on. A
     * state of its own, set when the entrance starts and ends, so reading it in composition recomposes nothing per
     * frame.
     */
    var arrived: Boolean by mutableStateOf(initial >= 1f)
        private set

    /** Starts the entrance from nothing, on the calm spring at the opening speed (a short fade under reduced motion). */
    fun play(tuning: DockMotionTuning) {
        job?.cancel()
        arrived = false
        job =
            scope.launch {
                animatable.snapTo(0f)
                if (reducedMotion) {
                    animatable.animateTo(1f, tween(RiffleMotionTokens.DURATION_REDUCED_MOTION_MILLIS))
                } else {
                    val calm = RiffleMotion.smooth<Float>(tuning, visibilityThreshold = VISIBILITY_THRESHOLD)
                    animatable.animateTo(1f, calm)
                }
                arrived = true
            }
    }

    /** Home is hidden: the next appearance starts from nothing. */
    fun hide() {
        job?.cancel()
        arrived = false
        job = scope.launch { animatable.snapTo(0f) }
    }

    private companion object {
        const val VISIBILITY_THRESHOLD = 0.001f
    }
}

/** What the Dock and the handle are drawn at now: for tests, read where it is drawn. */
internal data class HomeEntranceFrame(
    val alpha: Float,
    val translationXDp: Float,
    val translationYDp: Float,
)

internal val HomeEntranceFrameKey = SemanticsPropertyKey<() -> HomeEntranceFrame>("HomeEntranceFrame")
internal var SemanticsPropertyReceiver.homeEntranceFrame by HomeEntranceFrameKey

/** The entrance the Dock and the page handle read, provided by the Home destination; null where there is none. */
internal val LocalHomeEntrance = staticCompositionLocalOf<HomeEntranceState?> { null }

/**
 * The entrance for the Home destination: starts at nothing on a cold start and whenever Home appears again after the
 * activity stopped, and holds at 1 through configuration changes (the activity being recreated is not Home appearing).
 */
@Composable
internal fun rememberHomeEntrance(
    edge: DockPosition,
    reducedMotion: Boolean,
    tuning: DockMotionTuning,
): HomeEntranceState {
    val scope = rememberCoroutineScope()
    // Survives a configuration change, so a rotation or a fold does not replay it.
    val played = rememberSaveable { mutableStateOf(false) }
    val state = remember { HomeEntranceState(if (played.value) 1f else 0f, scope) }
    state.edge = edge
    state.reducedMotion = reducedMotion
    val latestTuning by rememberUpdatedState(tuning)
    val owner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(owner, state) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START ->
                        if (!played.value) {
                            played.value = true
                            state.play(latestTuning)
                        }
                    Lifecycle.Event.ON_STOP ->
                        if (activity?.isChangingConfigurations != true) {
                            played.value = false
                            state.hide()
                        }
                    else -> Unit
                }
            }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * Draws this with the Home entrance: faded in and shifted toward the Dock's edge by what is left of the rise. Read in
 * the draw phase; a no-op without an entrance. Both the Dock and the handle use it, so they come in together.
 */
internal fun Modifier.homeEntranceLayer(entrance: HomeEntranceState?): Modifier =
    if (entrance == null) {
        this
    } else {
        graphicsLayer {
            val frame = entrance.frame()
            alpha = alpha * frame.alpha
            translationX = frame.translationXDp * density
            translationY = frame.translationYDp * density
        }
    }

/** Exposes the entrance's current frame as semantics (a lambda, so reading it recomposes nothing). */
internal fun Modifier.exposeHomeEntrance(entrance: HomeEntranceState?): Modifier =
    if (entrance == null) {
        this
    } else {
        semantics { homeEntranceFrame = { entrance.frame() } }
    }

/** The frame now, in dp: the translation is toward the Dock's edge, the way a press dips the Dock. */
internal fun HomeEntranceState.frame(): HomeEntranceFrame {
    val pull = edge.pullDirection
    val rise = HomeEntrance.offsetDp(progress, reducedMotion)
    // A dip of the Dock is `-unit * dip`; the entrance starts that far toward the edge and comes back to 0.
    return HomeEntranceFrame(
        alpha = HomeEntrance.alpha(progress),
        translationXDp = -pull.unitX * rise,
        translationYDp = -pull.unitY * rise,
    )
}
