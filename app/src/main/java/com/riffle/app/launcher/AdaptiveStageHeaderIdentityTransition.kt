package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import com.riffle.app.launcher.designsystem.RiffleMotion
import com.riffle.core.domain.launcher.apps.AppIdentity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The header used to draw a second, static copy of whichever app's stage is shown, right next to a
 * dock that already shows the same app's icon as the entry point the user tapped to get here. This
 * is the fix: the exact icon the user tapped in the dock flies from its dock position into the
 * header's small rest slot and settles there, instead of a disconnected duplicate appearing beside it.
 *
 * A real [androidx.compose.animation.SharedTransitionLayout] needs an
 * [androidx.compose.animation.AnimatedVisibilityScope] on both ends of the transition -- there isn't
 * one here, because neither side ever leaves composition: the dock and `AdaptiveStageStageHeader`
 * are persistent siblings under `HomeDestination`'s own `Box` (see `LauncherHomeDestination.kt`), and
 * a tap only changes which stage the header shows, not whether either surface is present. So this is
 * the manual approximation the task calls for instead: capture the tapped dock icon's root-coordinate
 * bounds at the moment of the tap ([DockInteractions.onIdentityTransitionRequested], surfaced through
 * [HomeDockHostState.identityTransitionRequest] -- the existing "what the dock and the mode surface
 * both need" hoisting point, not a new channel), then animate a standalone icon from there to the
 * header's own measured rest slot with [RiffleMotion.gentle] (this app's shared spring for "large,
 * expressive travel... coming to rest" -- the closest existing match to a `TactileMotionSpec`, reused
 * rather than duplicated), crossfading into the header's real icon on arrival.
 */
internal fun shouldAnimateIdentityTransition(
    request: DockIdentityTransitionRequest?,
    shownIdentity: AppIdentity?,
    reducedMotion: Boolean,
): Boolean = request != null && shownIdentity != null && !reducedMotion && request.identity == shownIdentity

/** [this] translated from root coordinates into a space whose origin is [originInRoot]. */
internal fun Rect.relativeTo(originInRoot: Offset): Rect = translate(-originInRoot)

/**
 * Renders the flying icon itself, driven by two [Animatable]s (top-left position and edge length --
 * the source and rest bounds are both square icon slots, so one size animates both axes) rather than
 * one [Animatable] over a [Rect], which has no built-in vector converter. Calls [onSettled] once, when
 * both finish, so the caller can hand the identity back to the header's own icon and tick the settle
 * haptic (mirrors [CardStackInteraction.onSettleHaptic]'s own mechanism: one haptic exactly at
 * arrival, not continuously through the travel).
 */
@Composable
internal fun DockIdentityOverlayIcon(
    request: DockIdentityTransitionRequest,
    headerOriginInRoot: Offset,
    restBoundsInRoot: Rect,
    label: String,
    appIconLoader: AppIconLoader,
    onSettled: () -> Unit,
) {
    val sourceLocal = request.sourceBounds.relativeTo(headerOriginInRoot)
    val restLocal = restBoundsInRoot.relativeTo(headerOriginInRoot)
    val density = LocalDensity.current

    val position = remember(request.requestId) { Animatable(sourceLocal.topLeft, Offset.VectorConverter) }
    val edgeLengthPx = remember(request.requestId) { Animatable(sourceLocal.width) }

    LaunchedEffect(request.requestId, restLocal) {
        val positionJob = launch { position.animateTo(restLocal.topLeft, animationSpec = RiffleMotion.gentle()) }
        val sizeJob = launch { edgeLengthPx.animateTo(restLocal.width, animationSpec = RiffleMotion.gentle()) }
        positionJob.join()
        sizeJob.join()
        onSettled()
    }

    Box(
        modifier =
            Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .size(with(density) { edgeLengthPx.value.toDp() }),
    ) {
        LauncherAppIcon(
            identity = request.identity,
            label = label,
            iconLoader = appIconLoader,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
