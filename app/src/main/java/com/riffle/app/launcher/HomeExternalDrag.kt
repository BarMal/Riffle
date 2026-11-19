package com.riffle.app.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.LauncherItem
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId

/** Where a Home drag that did not start on a Home page comes from. */
sealed interface ExternalHomeDragSource {
    /** An app held in the Library: dropping it adds it to Home as a new item. */
    data class Library(val app: InstalledApp) : ExternalHomeDragSource

    /** An item pinned in the Dock: dropping it on Home moves it there. */
    data class Dock(val itemId: LauncherItemId) : ExternalHomeDragSource
}

/**
 * What Home offers a surface that starts a drag it cannot finish itself (the Library, the Dock): the drag continues as
 * a Home drag (the lifted item, the push ring, the folder hold, the page turns, the Dock) from where the finger is.
 * Set by the Home surface while it is composed.
 */
interface HomeExternalDrag {
    /** Begins the drag at [pointerInRoot]; false when Home cannot take it. */
    fun start(
        source: ExternalHomeDragSource,
        pointerInRoot: Offset,
    ): Boolean

    fun move(pointerInRoot: Offset)

    /** The finger let go: what the plan says is done. True when it was released over the Dock (nothing is done). */
    fun end(): Boolean

    /** The drag ended without a drop. */
    fun cancel()
}

internal const val EXTERNAL_DRAG_PAGE_ID = "external-drag"

/** The session of a drag that starts outside the pages: no origin cell, so no source ghost on a page. */
internal fun externalDragSession(
    item: LauncherItem,
    source: ExternalHomeDragSource,
    targetPageId: LauncherPageId,
    pointerInRoot: Offset,
): HomeDragSession =
    HomeDragSession(
        item = item,
        originPageId = LauncherPageId(EXTERNAL_DRAG_PAGE_ID),
        originCell = GridCell(0, 0),
        targetPageId = targetPageId,
        projectedCell = GridCell(0, 0),
        pointerInRoot = pointerInRoot,
        external = source,
    )

/** What releasing an [HomeDragSession.external] drag does; null: nothing (a cancel, or over the Dock). */
internal fun externalDropAction(
    session: HomeDragSession,
    folderLabel: String?,
): LauncherShellAction? {
    val source = session.external ?: return null
    val plan = session.plan
    return when {
        session.isOverRemove ->
            (source as? ExternalHomeDragSource.Dock)?.let { LauncherShellAction.RemoveDockShortcut(it.itemId) }
        session.isOverDock ->
            (source as? ExternalHomeDragSource.Library)?.let { LauncherShellAction.AddAppToDock(it.app) }
        plan == null -> null
        plan.dropOn != null && plan.landing != null ->
            LauncherShellAction.DropExternalHomeItem(
                source = source,
                targetPageId = session.targetPageId,
                cell = plan.landing!!,
                dropOnItemId = plan.dropOn,
                folderLabel = folderLabel,
            )
        else ->
            (plan.landing ?: plan.spillLanding)?.let { cell ->
                LauncherShellAction.DropExternalHomeItem(
                    source = source,
                    targetPageId = session.targetPageId,
                    cell = cell,
                    pushed = plan.pushed,
                )
            }
    }
}

/** Whether a lifted [item] can be taken off Home by dropping it on the Remove target (Library apps are not on Home). */
internal fun HomeDragSession.canBeRemoved(): Boolean = external !is ExternalHomeDragSource.Library

/**
 * The Remove target of a Home drag: a glass pill (at least 48 dp) at the top of the screen, or at the bottom when the
 * Dock is on the top edge, that grows while the finger is over it. Dropping on it removes the lifted item from Home.
 * Where the finger is read in the draw phase (hover is a derived boolean that changes only on entering and leaving).
 */
@Composable
internal fun BoxScope.HomeRemoveTarget(
    session: () -> HomeDragSession?,
    atBottom: Boolean,
    reducedMotion: Boolean,
    onBoundsChanged: (Rect?) -> Unit,
) {
    CountRecomposition(HOME_REMOVE_TARGET_PROBE_KEY)
    val hovered by remember { derivedStateOf { session()?.isOverRemove == true } }
    val grow = remember { Animatable(1f) }
    LaunchedEffect(hovered, reducedMotion) {
        val target = if (hovered) REMOVE_TARGET_HOVER_SCALE else 1f
        if (reducedMotion) grow.snapTo(target) else grow.animateTo(target)
    }
    DisposableEffect(Unit) { onDispose { onBoundsChanged(null) } }
    val label = stringResource(R.string.home_remove_target)
    val description = stringResource(R.string.menu_remove_from_home)
    Box(
        modifier =
            Modifier
                .align(if (atBottom) Alignment.BottomCenter else Alignment.TopCenter)
                .windowInsetsPadding(if (atBottom) WindowInsets.navigationBars else WindowInsets.statusBars)
                .padding(RiffleSpacing.m),
    ) {
        GlassSurface(
            modifier =
                Modifier
                    .testTag(HOME_REMOVE_TARGET_TEST_TAG)
                    .defaultMinSize(minWidth = REMOVE_TARGET_MIN_WIDTH_DP.dp, minHeight = REMOVE_TARGET_HEIGHT_DP.dp)
                    .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
                    .graphicsLayer {
                        scaleX = grow.value
                        scaleY = grow.value
                    }
                    .semantics { contentDescription = description },
            weight = GlassWeight.Thick,
            shape = RiffleShapes.full,
            tint = MaterialTheme.colorScheme.errorContainer,
        ) {
            Box(
                modifier =
                    Modifier.defaultMinSize(
                        minWidth = REMOVE_TARGET_MIN_WIDTH_DP.dp,
                        minHeight = REMOVE_TARGET_HEIGHT_DP.dp,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

internal const val HOME_REMOVE_TARGET_TEST_TAG = "home-remove-target"
internal const val HOME_REMOVE_TARGET_PROBE_KEY = "home-remove-target"
private const val REMOVE_TARGET_HEIGHT_DP = 56
private const val REMOVE_TARGET_MIN_WIDTH_DP = 144
private const val REMOVE_TARGET_HOVER_SCALE = 1.15f
