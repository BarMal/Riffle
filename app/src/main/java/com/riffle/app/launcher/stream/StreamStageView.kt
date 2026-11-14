@file:Suppress("LongParameterList")

package com.riffle.app.launcher.stream

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LocalLauncherCardShape
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.stream.StreamPage
import com.riffle.core.domain.launcher.stream.StreamPageBody
import com.riffle.core.domain.launcher.stream.StreamRailMode
import com.riffle.core.domain.launcher.stream.StreamStage

internal const val STREAM_STAGE_RAIL_TEST_TAG = "stream-stage-rail"
internal const val STREAM_STAGE_MAIN_TEST_TAG = "stream-stage-main"

internal fun streamStageThumbTestTag(pageKey: String): String = "stream-stage-thumb-$pageKey"

private val RailWidthFull = 176.dp
private val RailWidthIcons = 72.dp
private val ThumbHeight = 88.dp
private val ThumbFrontHeight = 72.dp
private val ThumbStep = RiffleSpacing.xs + RiffleSpacing.xxs
private val IconThumbSize = RiffleSpacing.xxxl + RiffleSpacing.s

/**
 * The Stage view of the whole Stream (ADR 0003, Views), after Stage Manager on macOS: a rail along the leading edge
 * holds one small live stage per page except the selected one, and the selected stage's stack fills the rest. Tapping
 * a rail stage brings it to the centre while the one that was there goes back to the rail, a shared-element
 * transition (bounds and content morph between the thumbnail and the page; reduced motion snaps). The rail scrolls;
 * in a narrow pane (below [StreamStage.RAIL_COLLAPSE_BELOW_DP]) it collapses to icons only.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun StreamStageView(
    pages: List<StreamPage>,
    selectedKey: String?,
    recentKeys: List<String>,
    previousKeys: List<String>,
    env: StreamEnvironment,
    callbacks: StreamCallbacks,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val railMode = StreamStage.railMode(maxWidth.value.toInt())
        SharedTransitionLayout {
            AnimatedContent(
                targetState = selectedKey,
                transitionSpec = {
                    if (env.reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        fadeIn(tween(STAGE_FADE_MILLIS)) togetherWith fadeOut(tween(STAGE_FADE_MILLIS))
                    }
                },
                label = "stream-stage",
            ) { key ->
                val model = StreamStage.model(pages, key, recentKeys, previousKeys)
                Row(modifier = Modifier.fillMaxSize()) {
                    StageRail(model.rail, railMode, env, onSelect, this@SharedTransitionLayout, this@AnimatedContent)
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().testTag(STREAM_STAGE_MAIN_TEST_TAG)) {
                        model.selected?.let { page ->
                            StreamPageView(
                                page = page,
                                env = env,
                                callbacks = callbacks,
                                modifier =
                                    stageBounds(
                                        page.key,
                                        env.reducedMotion,
                                        this@SharedTransitionLayout,
                                        this@AnimatedContent,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val STAGE_FADE_MILLIS = 160

/** The shared bounds of one stage: its thumbnail in the rail and its page in the centre are the same element. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun stageBounds(
    pageKey: String,
    reducedMotion: Boolean,
    shared: SharedTransitionScope,
    visibility: AnimatedContentScope,
): Modifier =
    with(shared) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = "stage-$pageKey"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> if (reducedMotion) snap() else tween(STAGE_MOVE_MILLIS) },
        )
    }

private const val STAGE_MOVE_MILLIS = 320

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun StageRail(
    rail: List<StreamPage>,
    mode: StreamRailMode,
    env: StreamEnvironment,
    onSelect: (String) -> Unit,
    shared: SharedTransitionScope,
    visibility: AnimatedContentScope,
) {
    Column(
        modifier =
            Modifier
                .width(if (mode == StreamRailMode.FULL) RailWidthFull else RailWidthIcons)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .selectableGroup()
                .testTag(STREAM_STAGE_RAIL_TEST_TAG)
                .padding(start = RiffleSpacing.l, end = RiffleSpacing.s, bottom = RiffleSpacing.l),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rail.forEach { page ->
            StageThumb(
                page = page,
                mode = mode,
                env = env,
                onClick = { onSelect(page.key) },
                modifier = stageBounds(page.key, env.reducedMotion, shared, visibility),
            )
        }
    }
}

/**
 * One stage in the rail: a miniature of the top of the page's stack (up to two more cards peeking out behind the front
 * one), with the app's icon or the section's glyph, its name and a line of what is on top. Icon only when [mode] is
 * [StreamRailMode.ICONS]. Lit by the owning app's colour like the cards it stands for.
 */
@Composable
@Suppress("LongMethod")
private fun StageThumb(
    page: StreamPage,
    mode: StreamRailMode,
    env: StreamEnvironment,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val title = page.title()
    val count = page.countText()
    val description = if (count.isEmpty()) title else stringResource(R.string.stream_stage_thumb, title, count)
    val tap =
        Modifier
            .clickable(
                role = Role.Tab,
                onClickLabel = stringResource(R.string.stream_stage_bring_forward, title),
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = description
                selected = false
            }
            .testTag(streamStageThumbTestTag(page.key))
    if (mode == StreamRailMode.ICONS) {
        StreamCardSurface(
            page.key,
            page.lead?.appRef(),
            page.lead?.iconHandle(),
            env,
            modifier.then(tap).size(IconThumbSize),
        ) {
            Box(modifier = it.fillMaxSize(), contentAlignment = Alignment.Center) {
                StreamPageGlyph(page, env, RiffleSpacing.xl)
            }
        }
        return
    }
    val behind = (page.stackSize() - 1).coerceIn(0, 2)
    Box(modifier = modifier.then(tap).fillMaxWidth().height(ThumbHeight)) {
        for (depth in behind downTo 1) {
            GlassSurface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = ThumbHeight - ThumbFrontHeight - ThumbStep * depth)
                        .padding(horizontal = RiffleSpacing.xs * depth)
                        .fillMaxWidth()
                        .height(ThumbFrontHeight)
                        .showOnlyBottom(ThumbStep + 2.dp),
                weight = GlassWeight.Regular,
                shape = LocalLauncherCardShape.current,
                tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {}
        }
        StreamCardSurface(
            key = page.key,
            app = page.lead?.appRef(),
            icon = page.lead?.iconHandle(),
            env = env,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(ThumbFrontHeight),
        ) { sourceModifier ->
            Column(
                modifier = sourceModifier.fillMaxSize().padding(RiffleSpacing.s),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xxs),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
                ) {
                    StreamPageGlyph(page, env, RiffleSpacing.l)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                page.lead?.let { lead ->
                    Text(
                        text = lead.peekText(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun StreamPage.stackSize(): Int = (body as? StreamPageBody.Content)?.items?.size ?: 0

/**
 * Draws only the bottom [strip] of this miniature: a thumbnail behind the front one in the rail is only ever seen as
 * the strip below the one in front of it. (The stack proper clips the real card instead; see `StackLayout`.)
 */
private fun Modifier.showOnlyBottom(strip: Dp): Modifier =
    drawWithContent {
        clipRect(top = (size.height - strip.toPx()).coerceAtLeast(0f)) { this@drawWithContent.drawContent() }
    }
