package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.cards.CardStackAnimationEasing
import com.riffle.core.domain.launcher.cards.CardStackAnimationSpec
import com.riffle.core.domain.launcher.cards.CardStackLayoutPolicy
import com.riffle.core.domain.launcher.cards.CardStackMagnet
import com.riffle.core.domain.launcher.cards.DEFAULT_CARD_STACK_MAGNET_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.cards.MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT
import com.riffle.core.domain.launcher.cards.MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT
import kotlin.math.ceil
import kotlin.math.min

/**
 * Versioned, renderer-independent appearance intent for the optional AdaptiveStage card surface.
 * Values are always normalized through [coerce] before persistence or use.
 */
data class AdaptiveStageAppearanceSettings(
    val version: Int = CURRENT_ADAPTIVE_STAGE_APPEARANCE_VERSION,
    val geometry: AdaptiveStageGeometry = AdaptiveStageGeometry(),
    val surface: AdaptiveStageSurface = AdaptiveStageSurface(),
    val typography: AdaptiveStageTypography = AdaptiveStageTypography(),
    val motion: AdaptiveStageMotion = AdaptiveStageMotion(),
) {
    fun coerce(): AdaptiveStageAppearanceSettings =
        copy(
            version = version.coerceIn(1, CURRENT_ADAPTIVE_STAGE_APPEARANCE_VERSION),
            geometry = geometry.coerce(),
            surface = surface.coerce(),
            typography = typography.coerce(),
            motion = motion.coerce(),
        )

    /** Resolves platform limitations without rewriting the stored user preference. */
    fun effectiveFor(capabilities: AdaptiveStageRendererCapabilities): AdaptiveStageAppearanceSettings =
        coerce().let { settings ->
            settings.copy(
                surface =
                    settings.surface.copy(
                        blurStrengthPercent =
                            if (capabilities.supportsBlur && !settings.motion.reducedTransparency) {
                                settings.surface.blurStrengthPercent
                            } else {
                                0
                            },
                        glassTransparencyPercent =
                            if (settings.motion.reducedTransparency) {
                                0
                            } else {
                                settings.surface.glassTransparencyPercent
                            },
                        textureIntensityPercent =
                            if (capabilities.supportsTexture) settings.surface.textureIntensityPercent else 0,
                    ),
                motion =
                    if (settings.motion.reducedMotion) {
                        settings.motion.copy(
                            settleDurationMillis = 0,
                            reflowDurationMillis = 0,
                            enterDurationMillis = 0,
                            exitDurationMillis = 0,
                            expandDurationMillis = 0,
                            easing = AdaptiveStageEasing.STANDARD,
                            springBouncinessPercent = 0,
                            travelIntensityPercent = 0,
                            parallaxIntensityPercent = 0,
                            rotationIntensityPercent = 0,
                            // Every other field here drops to zero because reduced motion means
                            // less travel; the magnet is the one knob where that same intent means
                            // its *maximum*. Its weak end deliberately leaves the stack drifting
                            // toward a card over a longer beat -- exactly the lingering motion this
                            // mode exists to remove -- so it goes to the strongest setting, which
                            // takes the shortest, most direct path onto the card and stops.
                            magnetStrengthPercent = MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT,
                        )
                    } else {
                        settings.motion
                    },
            )
        }

    /**
     * Converts persisted intent to the card-stack primitives used by renderers. The resolution is
     * viewport- and inset-aware; callers must use [isUsable] to choose a non-stack fallback on a
     * space-constrained surface.
     *
     * [role] only changes the minimum-size floor [isUsable] enforces: [AdaptiveStageCardStackRole.PRIMARY]
     * (the default) expects a large, touch-reachable card sized against a whole viewport, while
     * [AdaptiveStageCardStackRole.RAIL] expects a small tile within a physically narrow strip -- callers
     * pass that strip's own real bounds as [viewport] rather than a whole-window size, so every other
     * part of this resolution (travel, offset/scale/alpha steps) already scales itself to fit.
     * [AdaptiveStageCardStackRole.PREVIEW] is for static illustrations (e.g. the settings preview):
     * never touched, so it only needs to stay legible, not reachable -- a much smaller floor than
     * [AdaptiveStageCardStackRole.PRIMARY] enforces for the same viewport.
     */
    @Suppress("LongMethod")
    fun resolveCardStack(
        viewport: AdaptiveStageViewportDp,
        capabilities: AdaptiveStageRendererCapabilities = AdaptiveStageRendererCapabilities(),
        globalReducedMotion: Boolean = false,
        role: AdaptiveStageCardStackRole = AdaptiveStageCardStackRole.PRIMARY,
    ): AdaptiveStageCardStackResolution {
        val appearance = effectiveForResolution(capabilities, globalReducedMotion)
        val focusedScale = appearance.geometry.focusedScalePercent / 100f
        val stackBounds = resolveStackBounds(appearance.geometry, appearance.motion, focusedScale)
        val cardSize = appearance.resolveCardSize(viewport, stackBounds, role)
        val requestedPadding = appearance.geometry.contentPaddingDp
        val isUsable = cardSize.isUsable(role) && appearance.hasReachableStackLayout()
        val depth = if (isUsable) appearance.geometry.visibleDepth else 1
        val horizontalTravel =
            ((viewport.safeWidthDp - cardSize.widthDp * stackBounds.maxWidthScale) / 2f).coerceAtLeast(0f)
        val verticalTravel =
            ((viewport.safeHeightDp - cardSize.heightDp * stackBounds.maxHeightScale) / 2f).coerceAtLeast(0f)
        val motionScale = appearance.motion.travelIntensityPercent / 100f
        val offsetDirection = appearance.geometry.fanDirection.toOffsetDirection()
        val verticalOffsetDirection = appearance.geometry.verticalFanDirection.toOffsetDirection()
        val focusedGap =
            if (offsetDirection == 0f) {
                0f
            } else {
                min(appearance.geometry.focusedGapDp * motionScale, horizontalTravel)
            }
        val horizontalStep =
            min(
                appearance.geometry.horizontalOffsetDp * motionScale,
                (horizontalTravel - focusedGap).coerceAtLeast(0f) / depth,
            )
        // Reduced motion removes animated travel, but cards still need a static
        // separation so every visible card remains reachable by touch.
        val verticalLayoutScale = if (appearance.motion.reducedMotion) 1f else motionScale
        val verticalStep =
            min(
                appearance.geometry.verticalSpacingDp * verticalLayoutScale,
                verticalTravel / depth,
            )
        val remainingVerticalTravel = (verticalTravel - verticalStep * depth).coerceAtLeast(0f)
        // The total curve displacement CardStackLayoutPolicy reaches at maxVisibleDepth itself
        // (it eases every nearer depth's own share of this peak via a smootherstep ramp, not a
        // per-depth-squared coefficient) -- still bounded by both the user's own per-depth dp
        // budget and by whatever vertical travel room remains after the linear spacing above, but
        // no longer crushed by a depth-squared division that left the curve barely visible past a
        // couple of cards regardless of how high curveDp or visibleDepth were set.
        val curveStep =
            min(
                appearance.geometry.curveDp * motionScale * depth,
                remainingVerticalTravel,
            )
        val layoutPolicy =
            CardStackLayoutPolicy(
                maxVisibleDepth = depth,
                scaleStep = stackBounds.scaleStep,
                offsetStep = horizontalStep,
                focusedGap = focusedGap,
                offsetDirection = offsetDirection,
                alphaStep = appearance.geometry.overlapPercent / 100f / depth,
                verticalOffsetStep = verticalStep,
                curveStep = curveStep,
                rotationStep = stackBounds.rotationStep,
                reducedMotionScaleStep = 0f,
                reducedMotionOffsetStep = 0f,
                verticalOffsetDirection = verticalOffsetDirection,
                // Held to `depth`, so a stack forced down to a single ring by a space-constrained
                // viewport (see `depth` above) cannot end up reaching *further* above focus than
                // below it. Above that floor the setting is passed through untouched, and its
                // symmetric default resolves to null -- the policy's own "no separate range".
                aboveFocusDepth =
                    appearance.geometry.aboveFocusDepth
                        .takeIf { it != SYMMETRIC_ABOVE_FOCUS_DEPTH }
                        ?.coerceAtMost(depth),
            )
        val animated = !appearance.motion.reducedMotion && isUsable
        return AdaptiveStageCardStackResolution(
            isUsable = isUsable,
            cardWidthDp = cardSize.widthDp,
            cardHeightDp = cardSize.heightDp,
            contentPaddingDp = requestedPadding.coerceAtMost(min(cardSize.widthDp, cardSize.heightDp) / 4),
            focusedScale = focusedScale,
            reducedMotion = appearance.motion.reducedMotion,
            layoutPolicy = layoutPolicy,
            stackPeakFraction = appearance.geometry.stackPeakPercent / 100f,
            animation =
                CardStackAnimationSpec(
                    horizontalTravelFraction = if (animated) appearance.motion.travelIntensityPercent / 100f else 0f,
                    verticalTravelFraction = if (animated) appearance.motion.parallaxIntensityPercent / 100f else 0f,
                    reflowsStack = animated,
                    animatesAlpha = animated,
                    animatesHorizontalTranslation = animated,
                    animatesVerticalTranslation = animated,
                    animatesScale = animated,
                    animatesRotation = animated,
                    durationMillis = maxOf(1, appearance.motion.reflowDurationMillis),
                    enterDurationMillis = maxOf(1, appearance.motion.enterDurationMillis),
                    settleDurationMillis = maxOf(1, appearance.motion.settleDurationMillis),
                    easing = appearance.motion.easing.cardStackEasing(),
                    springBouncinessPercent = appearance.motion.springBouncinessPercent,
                ),
            magnet = CardStackMagnet(strengthPercent = appearance.motion.magnetStrengthPercent),
        )
    }

    companion object {
        /** Plain default field values for the folded (single-stage, full-size) layout. */
        fun modern(): AdaptiveStageAppearanceSettings = AdaptiveStageAppearanceSettings()

        /**
         * Plain default field values for an unfolded, docked-rail layout: linear and spaced with no
         * overlap between adjacent tiles, as opposed to [modern]'s curved, tight, overlapping fan. Each
         * layout ships only this one literal set of field values -- there is no preset system (#1058
         * removed it in favor of direct, per-field editing for both layouts).
         *
         * [AdaptiveStageGeometry.verticalSpacingDp] of 72 mirrors the value the previous hand-rolled
         * rail policy needed before tiles were reliably tappable -- see #1054's fix: a rail tile is
         * roughly icon (40dp) + label + padding tall, so a background tile's own center needs to clear
         * that whole height, not a fraction of it, to land outside the focused tile's larger hit box
         * (Compose routes a pointer event to whichever entry is topmost by z-order at that point).
         * [AdaptiveStageGeometry.visibleDepth] of 2 matches that same hand-rolled policy's
         * `maxVisibleDepth` for the same reason: [resolveCardStack] divides the available travel by
         * `visibleDepth` (so every ring stays within the viewport), so a larger depth here would
         * silently claw back the 72dp step below the tappable threshold on a physically narrow rail.
         */
        fun unfolded(): AdaptiveStageAppearanceSettings =
            AdaptiveStageAppearanceSettings(
                geometry =
                    AdaptiveStageGeometry(
                        cardAspectRatioPercent = 100,
                        focusedScalePercent = 100,
                        focusedGapDp = 8,
                        visibleDepth = 2,
                        overlapPercent = 0,
                        verticalSpacingDp = 72,
                        horizontalOffsetDp = 0,
                        curveDp = 0,
                        fanDirection = AdaptiveStageFanDirection.NONE,
                        rotationDegrees = 0,
                        cornerRadiusDp = 16,
                        contentPaddingDp = 4,
                    ),
                motion =
                    AdaptiveStageMotion(
                        parallaxIntensityPercent = 0,
                        rotationIntensityPercent = 0,
                    ),
            )
    }
}

private fun AdaptiveStageEasing.cardStackEasing(): CardStackAnimationEasing =
    when (this) {
        AdaptiveStageEasing.STANDARD -> CardStackAnimationEasing.STANDARD
        AdaptiveStageEasing.EMPHASIZED -> CardStackAnimationEasing.EMPHASIZED
        AdaptiveStageEasing.GENTLE_SPRING -> CardStackAnimationEasing.GENTLE_SPRING
    }

private fun AdaptiveStageAppearanceSettings.effectiveForResolution(
    capabilities: AdaptiveStageRendererCapabilities,
    globalReducedMotion: Boolean,
): AdaptiveStageAppearanceSettings =
    copy(motion = motion.copy(reducedMotion = motion.reducedMotion || globalReducedMotion))
        .effectiveFor(capabilities)

private fun AdaptiveStageAppearanceSettings.staticVerticalSeparationDp(): Int =
    if (motion.reducedMotion) {
        geometry.verticalSpacingDp * geometry.visibleDepth
    } else {
        0
    }

private fun AdaptiveStageAppearanceSettings.hasReachableStackLayout(): Boolean {
    return !motion.reducedMotion || geometry.verticalSpacingDp > 0
}

private fun AdaptiveStageAppearanceSettings.resolveCardSize(
    viewport: AdaptiveStageViewportDp,
    stackBounds: ResolvedAdaptiveStageStackBounds,
    role: AdaptiveStageCardStackRole,
): ResolvedAdaptiveStageCardSize =
    resolveCardSize(
        viewport = viewport,
        requestedPadding = geometry.contentPaddingDp,
        geometry = geometry,
        stackBounds = stackBounds,
        reservedVerticalSpaceDp = staticVerticalSeparationDp(),
        // RAIL sizes a tile against its own narrow physical strip (#1054): every dp there is
        // needed just to keep tiles reachable, so it keeps filling the full strip exactly as
        // before, ignoring geometry.cardSizePercent entirely. PRIMARY's margin *is*
        // geometry.cardSizePercent's complement -- the user's single "how much of the screen does
        // this claim" knob, independent of cardAspectRatioPercent's separate "what shape" knob (see
        // that field's own doc). 100% (the max) means zero reserved margin: a genuinely full-bleed
        // card reaching the real screen edge, not the old hardcoded 75%-of-viewport ceiling. Lower
        // values reserve real stage margin so resolveCardStack's fan/offset/rotation has room to be
        // visible instead of fighting over leftover scraps, taking a cue from the reference "Calm"
        // timescape, which sizes its own focused card to ~58% of screen height rather than filling
        // it. PREVIEW keeps its own small fixed margin for the same reason, capped by its own tiny
        // usability floor: at the settings preview's small box with a portrait-default card aspect
        // ratio, the height axis is already the binding constraint before any margin is applied, so
        // PREVIEW_FAN_STAGE_MARGIN_FRACTION can only be as large as previewRoleIsUsableAtA...
        // (AdaptiveStageAppearanceSettingsTest) still tolerates -- see that constant's own doc.
        fanStageMarginFraction =
            when (role) {
                AdaptiveStageCardStackRole.PRIMARY -> 1f - geometry.cardSizePercent / 100f
                AdaptiveStageCardStackRole.RAIL -> 0f
                AdaptiveStageCardStackRole.PREVIEW -> PREVIEW_FAN_STAGE_MARGIN_FRACTION
            },
        // PREVIEW additionally scales by the user's chosen size on top of its own fixed margin
        // above, so the illustration keeps reflecting that setting even though its margin doesn't
        // move with it. PRIMARY folds cardSizePercent entirely into its margin above instead (no
        // separate scale here) so a 100% choice reaches the real, un-shrunk fitted size rather than
        // that size multiplied by itself. RAIL ignores it, matching its margin above.
        sizeScaleFraction =
            when (role) {
                AdaptiveStageCardStackRole.PRIMARY, AdaptiveStageCardStackRole.RAIL -> 1f
                AdaptiveStageCardStackRole.PREVIEW -> geometry.cardSizePercent / 100f
            },
    )

private fun resolveCardSize(
    viewport: AdaptiveStageViewportDp,
    requestedPadding: Int,
    geometry: AdaptiveStageGeometry,
    stackBounds: ResolvedAdaptiveStageStackBounds,
    reservedVerticalSpaceDp: Int = 0,
    fanStageMarginFraction: Float = 0f,
    sizeScaleFraction: Float = 1f,
): ResolvedAdaptiveStageCardSize {
    val stageWidthDp = (viewport.safeWidthDp * (1f - fanStageMarginFraction))
    val stageHeightDp = (viewport.safeHeightDp * (1f - fanStageMarginFraction))
    // geometry.contentPaddingDp is a single raw dp value shared by two very differently-sized
    // stages: it's subtracted (twice, once per side) from this fit envelope below, and separately
    // becomes the rendered card's own interior content inset (resolveCardStack's own
    // `contentPaddingDp` output, coerced against the *already-fitted* card size there). On
    // PRIMARY's real, large viewport a user's max padding (64dp) is trivial next to a
    // several-hundred-dp stage. On PREVIEW's small illustrative box, the same raw 64dp eats a much
    // larger share of the fit envelope -- combined with a wide cardAspectRatioPercent (which
    // narrows the height-bound fit further, see this function's own `aspectRatio` use below), that
    // was enough to push the fitted card under PREVIEW's own usability floor, surfacing "Preview
    // needs more space to render" at slider values PRIMARY handles without issue. Capping the
    // padding actually spent on *this* fit step to a fraction of the stage's own smaller dimension
    // keeps every role's fit self-scaling to its own box; the separate, already-capped rendered
    // content-padding output is untouched, so a real device with real screen space still gets the
    // user's full chosen padding.
    val fitPadding =
        requestedPadding.toFloat()
            .coerceAtMost(min(stageWidthDp, stageHeightDp) * MAX_CONTENT_PADDING_STAGE_FRACTION)
    val availableWidth = (stageWidthDp - fitPadding * 2).coerceAtLeast(0f)
    val availableHeight = (stageHeightDp - fitPadding * 2 - reservedVerticalSpaceDp).coerceAtLeast(0f)
    val aspectRatio = geometry.cardAspectRatioPercent / 100f
    val fittedWidth = min(availableWidth / stackBounds.maxWidthScale, availableHeight / stackBounds.maxHeightScale)
    val width = (fittedWidth * sizeScaleFraction).toInt()
    val height = if (aspectRatio == 0f) 0 else (width / aspectRatio).toInt()
    val focusedWidth = ceil(width * stackBounds.focusedScale).toInt()
    val focusedHeight = ceil(height * stackBounds.focusedScale).toInt()
    return ResolvedAdaptiveStageCardSize(
        widthDp = width,
        heightDp = height,
        focusedWidthDp = focusedWidth,
        focusedHeightDp = focusedHeight,
        fitsAvailableSpace = focusedWidth <= availableWidth && focusedHeight <= availableHeight,
    )
}

/**
 * How much of the stage's own smaller dimension [AdaptiveStageGeometry.contentPaddingDp] is
 * allowed to consume when fitting a card -- see the padding cap's own call-site doc in
 * [resolveCardSize] for why this exists. 0.15 was chosen by walking every role's fit at the
 * padding field's full valid range (0-64dp) crossed with the aspect ratio field's full valid
 * range (55%-160%) against representative viewport sizes for each role (a full phone/tablet
 * screen for PRIMARY/RAIL, the settings preview's small illustrative box for PREVIEW): the
 * smallest fraction that kept every one of those combinations usable, so this protects the
 * tightest real case without being any more conservative than it needs to be.
 */
private const val MAX_CONTENT_PADDING_STAGE_FRACTION = 0.15f

/**
 * [resolveCardSize]'s margin for [AdaptiveStageCardStackRole.PREVIEW]. Before this existed, the
 * settings preview's card filled its whole box (fanStageMarginFraction 0), which -- combined with
 * the preview's landscape-shaped box (wide, short) and the default portrait card aspect ratio --
 * left [AdaptiveStageAppearanceSettings.resolveCardStack]'s vertical travel budget at exactly
 * zero: the height axis alone determined the card's size, with nothing held back. Vertical
 * spacing/curve sliders had no visible effect in the preview specifically (though they still
 * worked on the real, larger PRIMARY stage) as a direct result.
 *
 * Unlike [AdaptiveStageCardStackRole.PRIMARY] (whose margin is entirely
 * `1f - geometry.cardSizePercent / 100f`, so a 100% choice reaches a genuinely full-bleed real
 * card with zero reserved margin), PREVIEW keeps this small fixed floor regardless of that same
 * setting -- see [resolveCardSize]'s `sizeScaleFraction` doc for how PREVIEW still reflects the
 * user's chosen size on top of this floor. A static illustration whose entire point is
 * demonstrating spacing/fan settings would otherwise go margin-less (and travel-less) right along
 * with a user's real 100% choice, defeating its own purpose.
 *
 * 0.17 is a deliberately small, conservative value -- not chosen for a strong visual effect, but
 * because [AdaptiveStageCardStackRole.PREVIEW]'s own usability floor
 * ([MIN_ADAPTIVE_STAGE_LEGIBLE_PREVIEW_CARD_WIDTH_DP]/HEIGHT), combined with the *default*
 * [AdaptiveStageGeometry.cardSizePercent]'s own further shrink on top of this margin (see
 * [resolveCardSize]'s `sizeScaleFraction` doc), stays close to binding at the smallest realistic
 * preview box -- see the two-role comparison test previewRoleIsUsableAtASettingsPreviewSizeThatPrimaryRoleRejects
 * in AdaptiveStageAppearanceSettingsTest, which pins that exact viewport. Unlike PRIMARY's much
 * larger margin, this can't chase a strong fan effect without either shrinking the preview card
 * below legibility at small settings-page sizes, or reworking the preview box's own aspect ratio
 * (out of scope here) -- so some vertical travel, rather than none, is the realistic ceiling.
 */
private const val PREVIEW_FAN_STAGE_MARGIN_FRACTION = 0.17f

/**
 * The focused card's own size no longer reserves extra room for the *worst-case rotated*
 * background silhouette (rotation can put a background card's rendered footprint outside its own
 * unrotated bounding box) -- the reference "Calm" timescape doesn't either: background cards are
 * allowed to extend past the focused card's own footprint, exactly as Calm's `clipChildren = false`
 * stack permits. That reservation was the dominant cost eating the fan/offset travel budget.
 *
 * [maxWidthScale]/[maxHeightScale] still guard the one thing that isn't a deliberate stylistic
 * choice: a background card can render *larger* than the focused card whenever
 * [AdaptiveStageGeometry.focusedScalePercent] shrinks the focused card below 100% -- the
 * shallowest background card (depth 1) is barely shrunk from its own full footprint regardless of
 * [focusedScale], so it can be the single largest rendered element in the stack. Both fields take
 * whichever of the two is bigger, so sizing and travel both stay conservative enough to contain
 * every *rendered* card, not just the focused one.
 */
private fun resolveStackBounds(
    geometry: AdaptiveStageGeometry,
    motion: AdaptiveStageMotion,
    focusedScale: Float,
): ResolvedAdaptiveStageStackBounds {
    val depth = geometry.visibleDepth
    val scaleStep = (1f - MIN_ADAPTIVE_STAGE_BACKGROUND_CARD_SCALE) / depth
    val rotationStep = geometry.rotationDegrees * motion.rotationIntensityPercent / 100f
    val aspectRatio = geometry.cardAspectRatioPercent / 100f
    val maxRenderScale = maxOf(focusedScale, 1f - scaleStep)
    return ResolvedAdaptiveStageStackBounds(
        focusedScale = focusedScale,
        scaleStep = scaleStep,
        rotationStep = rotationStep,
        maxWidthScale = maxRenderScale,
        maxHeightScale = maxRenderScale / aspectRatio,
    )
}

private data class ResolvedAdaptiveStageStackBounds(
    val focusedScale: Float,
    val scaleStep: Float,
    val rotationStep: Float,
    val maxWidthScale: Float,
    val maxHeightScale: Float,
)

private data class ResolvedAdaptiveStageCardSize(
    val widthDp: Int,
    val heightDp: Int,
    val focusedWidthDp: Int,
    val focusedHeightDp: Int,
    val fitsAvailableSpace: Boolean,
) {
    fun isUsable(role: AdaptiveStageCardStackRole): Boolean {
        val (minWidthDp, minHeightDp) = role.minimumUsableSizeDp()
        // Compares the card's longer/shorter rendered side against the longer/shorter configured
        // floor, rather than literally width-vs-width and height-vs-height. A portrait card (the
        // only shape these floors were ever exercised against before wide aspect ratios existed)
        // has height as its long side, so this is unchanged from a direct width/height comparison
        // there. A landscape card has width as its long side instead -- checking its (necessarily
        // shorter) height against the taller MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP floor
        // rejected every wide card as "needs more space" regardless of how large it actually
        // rendered, since resolveCardSize's width ceiling (screen width, effectively
        // aspect-ratio-independent) means a wider aspect ratio can only ever produce a *shorter*
        // card, never a taller one.
        val longFloorDp = maxOf(minWidthDp, minHeightDp)
        val shortFloorDp = minOf(minWidthDp, minHeightDp)
        val longSideDp = maxOf(focusedWidthDp, focusedHeightDp)
        val shortSideDp = minOf(focusedWidthDp, focusedHeightDp)
        return longSideDp >= longFloorDp && shortSideDp >= shortFloorDp && fitsAvailableSpace
    }
}

/**
 * Which kind of [CardStackLayoutPolicy]-driven surface [AdaptiveStageAppearanceSettings.resolveCardStack]
 * is sizing for -- see that function's doc for how this changes reachability sizing.
 */
enum class AdaptiveStageCardStackRole { PRIMARY, RAIL, PREVIEW }

private fun AdaptiveStageCardStackRole.minimumUsableSizeDp(): Pair<Int, Int> =
    when (this) {
        AdaptiveStageCardStackRole.PRIMARY ->
            MIN_ADAPTIVE_STAGE_REACHABLE_CARD_WIDTH_DP to MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP
        AdaptiveStageCardStackRole.RAIL ->
            MIN_ADAPTIVE_STAGE_REACHABLE_RAIL_TILE_WIDTH_DP to MIN_ADAPTIVE_STAGE_REACHABLE_RAIL_TILE_HEIGHT_DP
        AdaptiveStageCardStackRole.PREVIEW ->
            MIN_ADAPTIVE_STAGE_LEGIBLE_PREVIEW_CARD_WIDTH_DP to MIN_ADAPTIVE_STAGE_LEGIBLE_PREVIEW_CARD_HEIGHT_DP
    }

data class AdaptiveStageViewportDp(
    val widthDp: Int,
    val heightDp: Int,
    val insets: AdaptiveStageInsetsDp = AdaptiveStageInsetsDp(),
) {
    val safeWidthDp: Int get() = (widthDp - insets.startDp - insets.endDp).coerceAtLeast(0)
    val safeHeightDp: Int get() = (heightDp - insets.topDp - insets.bottomDp).coerceAtLeast(0)
}

data class AdaptiveStageInsetsDp(
    val startDp: Int = 0,
    val topDp: Int = 0,
    val endDp: Int = 0,
    val bottomDp: Int = 0,
)

/** Renderer contract joining appearance intent to the existing stack layout and animation APIs. */
data class AdaptiveStageCardStackResolution(
    val isUsable: Boolean,
    val cardWidthDp: Int,
    val cardHeightDp: Int,
    val contentPaddingDp: Int,
    val focusedScale: Float,
    val reducedMotion: Boolean,
    val layoutPolicy: CardStackLayoutPolicy,
    val animation: CardStackAnimationSpec,
    /** [AdaptiveStageGeometry.stackPeakPercent] as the 0..1 fraction a renderer positions with. */
    val stackPeakFraction: Float = CENTERED_ADAPTIVE_STAGE_STACK_PEAK_PERCENT / 100f,
    /** Only consumed by surfaces that opt into a continuously-scrolling position. */
    val magnet: CardStackMagnet = CardStackMagnet(),
)

data class AdaptiveStageGeometry(
    val cardAspectRatioPercent: Int = 72,
    /**
     * How much of the screen [AdaptiveStageCardStackRole.PRIMARY]'s stage claims, independent of
     * [cardAspectRatioPercent]. Before this existed, aspect ratio was the *only* size-adjacent
     * knob: [resolveCardSize] always picked the largest card that fit the given shape, so
     * "make the card smaller" and "change its shape" were the same control -- shrinking one axis
     * always grew the other to compensate, never leaving genuine empty stage around a deliberately
     * small card, and there was no way to reach a genuinely edge-to-edge card at all. 100 now means
     * exactly that: zero reserved stage margin, a real card reaching the screen edge (minus only
     * [contentPaddingDp] and system insets). 75 -- not 100 -- is the *default* here: it reproduces
     * this surface's original, pre-this-field fixed 25%-of-viewport margin exactly, so existing
     * installs see no visual change until a user actually moves this slider. Values below it widen
     * that reserved margin further, leaving more room for [resolveCardStack]'s fan/offset/rotation
     * to be visible around a deliberately smaller card.
     */
    val cardSizePercent: Int = 75,
    val focusedScalePercent: Int = 100,
    val focusedGapDp: Int = 12,
    val visibleDepth: Int = 4,
    /**
     * How many already-passed cards stay visible above focus. [SYMMETRIC_ABOVE_FOCUS_DEPTH] (the
     * default) keeps the stack symmetric about focus -- this surface's only prior behavior -- so
     * existing installs see no change until a user moves this slider. See
     * [CardStackLayoutPolicy.aboveFocusDepth] for what a shorter range does to an outgoing card's
     * scale, alpha, rotation and fan. The reference "Calm" launcher's own equivalent
     * (`aboveFocusCards`) defaults to 2 against 3 visible cards, so its outgoing side is
     * deliberately tighter than its incoming one.
     */
    val aboveFocusDepth: Int = SYMMETRIC_ABOVE_FOCUS_DEPTH,
    /**
     * Where down the stage the focused card's centre sits, as a percentage. 50 (the default)
     * centres it -- this surface's only prior behavior. The reference "Calm" launcher's own
     * `stackPeakPosition` defaults to 20 instead: its focused card sits high, so the room below it
     * belongs to the cards still to come rather than being split evenly with the ones already
     * gone. Pairs naturally with [aboveFocusDepth], which decides how much of that upper room the
     * departing cards need.
     */
    val stackPeakPercent: Int = CENTERED_ADAPTIVE_STAGE_STACK_PEAK_PERCENT,
    val overlapPercent: Int = 22,
    val verticalSpacingDp: Int = 8,
    val horizontalOffsetDp: Int = 20,
    val curveDp: Int = 6,
    val fanDirection: AdaptiveStageFanDirection = AdaptiveStageFanDirection.END,
    val verticalFanDirection: AdaptiveStageFanDirection = AdaptiveStageFanDirection.START,
    /**
     * How far the outermost visible card is tilted, and -- via its sign -- which way the whole fan
     * leans. See [MIN_ADAPTIVE_STAGE_ROTATION_DEGREES]. Negative mirrors the lean; 0 is flat.
     */
    val rotationDegrees: Int = 4,
    val cornerRadiusDp: Int = 28,
    val contentPaddingDp: Int = 20,
) {
    fun coerce(): AdaptiveStageGeometry =
        copy(
            cardAspectRatioPercent =
                cardAspectRatioPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT,
                    MAX_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT,
                ),
            cardSizePercent =
                cardSizePercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_CARD_SIZE_PERCENT,
                    MAX_ADAPTIVE_STAGE_CARD_SIZE_PERCENT,
                ),
            focusedScalePercent =
                focusedScalePercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT,
                    MAX_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT,
                ),
            focusedGapDp =
                focusedGapDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_FOCUSED_GAP_DP,
                    MAX_ADAPTIVE_STAGE_FOCUSED_GAP_DP,
                ),
            visibleDepth =
                visibleDepth.coerceIn(
                    MIN_ADAPTIVE_STAGE_VISIBLE_DEPTH,
                    MAX_ADAPTIVE_STAGE_VISIBLE_DEPTH,
                ),
            aboveFocusDepth =
                aboveFocusDepth.coerceIn(
                    SYMMETRIC_ABOVE_FOCUS_DEPTH,
                    MAX_ADAPTIVE_STAGE_VISIBLE_DEPTH,
                ),
            stackPeakPercent =
                stackPeakPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_STACK_PEAK_PERCENT,
                    MAX_ADAPTIVE_STAGE_STACK_PEAK_PERCENT,
                ),
            overlapPercent =
                overlapPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_OVERLAP_PERCENT,
                    MAX_ADAPTIVE_STAGE_OVERLAP_PERCENT,
                ),
            verticalSpacingDp =
                verticalSpacingDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_VERTICAL_SPACING_DP,
                    MAX_ADAPTIVE_STAGE_VERTICAL_SPACING_DP,
                ),
            horizontalOffsetDp =
                horizontalOffsetDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP,
                    MAX_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP,
                ),
            curveDp =
                curveDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_CURVE_DP,
                    MAX_ADAPTIVE_STAGE_CURVE_DP,
                ),
            rotationDegrees =
                rotationDegrees.coerceIn(
                    MIN_ADAPTIVE_STAGE_ROTATION_DEGREES,
                    MAX_ADAPTIVE_STAGE_ROTATION_DEGREES,
                ),
            cornerRadiusDp =
                cornerRadiusDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_CORNER_RADIUS_DP,
                    MAX_ADAPTIVE_STAGE_CORNER_RADIUS_DP,
                ),
            contentPaddingDp =
                contentPaddingDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_CONTENT_PADDING_DP,
                    MAX_ADAPTIVE_STAGE_CONTENT_PADDING_DP,
                ),
        )
}

enum class AdaptiveStageFanDirection { NONE, START, END }

private fun AdaptiveStageFanDirection.toOffsetDirection(): Float =
    when (this) {
        AdaptiveStageFanDirection.NONE -> 0f
        AdaptiveStageFanDirection.START -> -1f
        AdaptiveStageFanDirection.END -> 1f
    }

data class AdaptiveStageSurface(
    val backgroundSource: AdaptiveStageBackgroundSource = AdaptiveStageBackgroundSource.APP_DERIVED_GRADIENT,
    /**
     * How the card's own surface is layered. See [AdaptiveStageCardEffect]; this decides whether
     * the background treatment reaches the card's edges or is framed by an inset content face.
     */
    val cardEffect: AdaptiveStageCardEffect = AdaptiveStageCardEffect.FROSTED,
    val customBackgroundArgb: Long = 0xFF1B1B1FL,
    val glassTransparencyPercent: Int = 38,
    val glassTintArgb: Long = 0xCCFFFFFFL,
    val blurStrengthPercent: Int = 28,
    val saturationPercent: Int = 100,
    val contrastPercent: Int = 100,
    val outlineWidthDp: Int = 1,
    val highlightPercent: Int = 36,
    val shadowElevationDp: Int = 12,
    val textureIntensityPercent: Int = 0,
) {
    fun coerce(): AdaptiveStageSurface =
        copy(
            customBackgroundArgb =
                customBackgroundArgb.coerceIn(
                    MIN_ADAPTIVE_STAGE_ARGB,
                    MAX_ADAPTIVE_STAGE_ARGB,
                ),
            glassTintArgb =
                glassTintArgb.coerceIn(
                    MIN_ADAPTIVE_STAGE_ARGB,
                    MAX_ADAPTIVE_STAGE_ARGB,
                ),
            glassTransparencyPercent =
                glassTransparencyPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT,
                    MAX_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT,
                ),
            blurStrengthPercent =
                blurStrengthPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT,
                    MAX_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT,
                ),
            saturationPercent =
                saturationPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_SATURATION_PERCENT,
                    MAX_ADAPTIVE_STAGE_SATURATION_PERCENT,
                ),
            contrastPercent =
                contrastPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_CONTRAST_PERCENT,
                    MAX_ADAPTIVE_STAGE_CONTRAST_PERCENT,
                ),
            outlineWidthDp =
                outlineWidthDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP,
                    MAX_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP,
                ),
            highlightPercent =
                highlightPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT,
                    MAX_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT,
                ),
            shadowElevationDp =
                shadowElevationDp.coerceIn(
                    MIN_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP,
                    MAX_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP,
                ),
            textureIntensityPercent =
                textureIntensityPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT,
                    MAX_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT,
                ),
        )
}

/**
 * How a card's surface is layered, mirroring the reference "Calm" launcher's own `CardEffect`
 * (NONE/FROSTED/GLASS) rather than Riffle's previous single hardcoded treatment.
 *
 * [GLASS] is that previous treatment: the background layer (gradient, artwork, tint, texture) is
 * drawn full-bleed, and the content then sits on its *own* opaque face inset from the card edge by
 * the content padding plus an extra bezel. That inset is the only reason any of the background is
 * visible at all -- the content face is opaque, so without a frame around it the artwork and
 * gradient would be completely hidden. The cost is that every card carries a translucent double
 * border, and the background treatment is reduced to a thin decorative rim.
 *
 * [FROSTED] keeps the same tinted treatment but drops the inset face, so the background reaches
 * every edge and the content sits directly on it. Same colours, no border, and the gradient or
 * artwork actually fills the card instead of framing it.
 *
 * [SOLID] is Calm's `NONE`: one opaque colour, edge to edge, with no gradient, artwork, tint or
 * outline. The plainest, highest-contrast option, and the cheapest to draw.
 */
enum class AdaptiveStageCardEffect {
    SOLID,
    FROSTED,
    GLASS,
}

enum class AdaptiveStageBackgroundSource {
    NOTIFICATION_ARTWORK,
    APP_ICON_TREATMENT,
    APP_DERIVED_SOLID,
    APP_DERIVED_GRADIENT,
    SYSTEM_WALLPAPER_ACCENT,
    CUSTOM_SOLID,
}

data class AdaptiveStageTypography(
    val accentSource: AdaptiveStageAccentSource = AdaptiveStageAccentSource.APP_DERIVED,
    val customAccentArgb: Long = 0xFF6750A4L,
    val automaticForegroundContrast: Boolean = true,
    val contentDensity: AdaptiveStageContentDensity = AdaptiveStageContentDensity.COMFORTABLE,
    val textScalePercent: Int = 100,
) {
    fun coerce(): AdaptiveStageTypography =
        copy(
            customAccentArgb = customAccentArgb.coerceIn(MIN_ADAPTIVE_STAGE_ARGB, MAX_ADAPTIVE_STAGE_ARGB),
            textScalePercent =
                textScalePercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT,
                    MAX_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT,
                ),
        )
}

enum class AdaptiveStageAccentSource { APP_DERIVED, SYSTEM_WALLPAPER, CUSTOM }

enum class AdaptiveStageContentDensity { COMPACT, COMFORTABLE, EXPANDED }

data class AdaptiveStageMotion(
    val settleDurationMillis: Int = 220,
    val reflowDurationMillis: Int = 260,
    val enterDurationMillis: Int = 240,
    val exitDurationMillis: Int = 180,
    val expandDurationMillis: Int = 280,
    val easing: AdaptiveStageEasing = AdaptiveStageEasing.GENTLE_SPRING,
    val springBouncinessPercent: Int = 20,
    val travelIntensityPercent: Int = 100,
    val parallaxIntensityPercent: Int = 18,
    val rotationIntensityPercent: Int = 100,
    val hapticStrength: AdaptiveStageHapticStrength = AdaptiveStageHapticStrength.MEDIUM,
    /**
     * How long a card stack's scroll position is left standing where its own momentum fling
     * stopped before being pulled onto the nearest card, and how firmly it is then pulled. See
     * [com.riffle.core.domain.launcher.cards.CardStackMagnet]; only surfaces that opt into the
     * continuously-scrolling position (a `CardStackScroll`) have a magnetize phase to tune.
     */
    val magnetStrengthPercent: Int = DEFAULT_CARD_STACK_MAGNET_STRENGTH_PERCENT,
    val reducedMotion: Boolean = false,
    val reducedTransparency: Boolean = false,
) {
    fun coerce(): AdaptiveStageMotion =
        copy(
            settleDurationMillis =
                settleDurationMillis.coerceIn(
                    MIN_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS,
                    MAX_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS,
                ),
            reflowDurationMillis =
                reflowDurationMillis.coerceIn(
                    MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                    MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                ),
            enterDurationMillis =
                enterDurationMillis.coerceIn(
                    MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                    MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                ),
            exitDurationMillis =
                exitDurationMillis.coerceIn(
                    MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                    MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                ),
            expandDurationMillis =
                expandDurationMillis.coerceIn(
                    MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                    MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS,
                ),
            springBouncinessPercent =
                springBouncinessPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT,
                    MAX_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT,
                ),
            travelIntensityPercent =
                travelIntensityPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT,
                    MAX_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT,
                ),
            parallaxIntensityPercent =
                parallaxIntensityPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT,
                    MAX_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT,
                ),
            rotationIntensityPercent =
                rotationIntensityPercent.coerceIn(
                    MIN_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT,
                    MAX_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT,
                ),
            magnetStrengthPercent =
                magnetStrengthPercent.coerceIn(
                    MIN_CARD_STACK_MAGNET_STRENGTH_PERCENT,
                    MAX_CARD_STACK_MAGNET_STRENGTH_PERCENT,
                ),
        )
}

enum class AdaptiveStageEasing { STANDARD, EMPHASIZED, GENTLE_SPRING }

enum class AdaptiveStageHapticStrength { OFF, LIGHT, MEDIUM, STRONG }

data class AdaptiveStageRendererCapabilities(val supportsBlur: Boolean = true, val supportsTexture: Boolean = true)

const val CURRENT_ADAPTIVE_STAGE_APPEARANCE_VERSION = 1
const val MIN_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT = 55

// Was 100 (square) -- resolveCardSize computes height = width / (cardAspectRatioPercent / 100),
// so 100 was a hard ceiling on ever rendering a card wider than it is tall. 160 permits up to a
// roughly 8:5 landscape card.
const val MAX_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT = 160

// 100 (the max) reserves zero PRIMARY stage margin -- a genuinely edge-to-edge card, not merely
// "the largest size the aspect ratio allows within a hidden fixed margin" as it used to mean.
// Below that, the card shrinks (keeping its shape) and leaves genuine empty stage around it --
// unlike focusedScalePercent, which only ever affects the *focused* card relative to its own
// background cards, this scales the whole fitted stack uniformly. See cardSizePercent's own doc
// for why the *default* (75) sits below this max rather than at it.
const val MIN_ADAPTIVE_STAGE_CARD_SIZE_PERCENT = 40
const val MAX_ADAPTIVE_STAGE_CARD_SIZE_PERCENT = 100
const val MIN_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT = 85
const val MAX_ADAPTIVE_STAGE_FOCUSED_SCALE_PERCENT = 115
const val MIN_ADAPTIVE_STAGE_FOCUSED_GAP_DP = 0
const val MAX_ADAPTIVE_STAGE_FOCUSED_GAP_DP = 64
const val MIN_ADAPTIVE_STAGE_VISIBLE_DEPTH = 1
const val MAX_ADAPTIVE_STAGE_VISIBLE_DEPTH = 6

/**
 * The [AdaptiveStageGeometry.aboveFocusDepth] value meaning "however far [
 * AdaptiveStageGeometry.visibleDepth] reaches" -- a stack symmetric about focus, which is what this
 * surface did before the field existed. It doubles as the field's minimum: it sits below every real
 * depth, and the alternative reading of 0 ("show nothing above focus") would make the stack's whole
 * history vanish the instant a card is scrolled past, which is not a look worth offering.
 */
const val SYMMETRIC_ABOVE_FOCUS_DEPTH = 0

/**
 * The stage's own reachable band for [AdaptiveStageGeometry.stackPeakPercent]. Deliberately not the
 * full 0..100 the reference "Calm" launcher allows: Calm clamps its own computed padding against
 * the real card height afterwards, so its extremes collapse back to something reachable, whereas
 * this value drives an alignment bias that has no such backstop and would happily park the focused
 * card half off its own stage.
 */
const val MIN_ADAPTIVE_STAGE_STACK_PEAK_PERCENT = 15
const val MAX_ADAPTIVE_STAGE_STACK_PEAK_PERCENT = 85

/** Centres the focused card on the stage -- this surface's prior fixed behavior. */
const val CENTERED_ADAPTIVE_STAGE_STACK_PEAK_PERCENT = 50
const val MIN_ADAPTIVE_STAGE_OVERLAP_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_OVERLAP_PERCENT = 60
const val MIN_ADAPTIVE_STAGE_VERTICAL_SPACING_DP = 0
const val MAX_ADAPTIVE_STAGE_VERTICAL_SPACING_DP = 96
const val MIN_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP = 0
const val MAX_ADAPTIVE_STAGE_HORIZONTAL_OFFSET_DP = 160
const val MIN_ADAPTIVE_STAGE_CURVE_DP = 0
const val MAX_ADAPTIVE_STAGE_CURVE_DP = 96

/**
 * [AdaptiveStageGeometry.rotationDegrees] is signed: its magnitude is how far the outermost visible
 * card is tilted, and its sign is which way the fan leans. Was floored at 0, which offered only one
 * of the two handednesses -- a stack could lean one way or lie flat, never the other way, even
 * though every other lean in this model (see [AdaptiveStageFanDirection], applied to both the
 * horizontal and vertical axes) is direction-selectable. Nothing downstream ever required a
 * non-negative rotation: [CardStackLayoutPolicy] has no such invariant, and `resolveStackBounds`
 * passes the step straight through without letting it influence card sizing, so the floor was the
 * only thing withholding the mirrored half of the range.
 *
 * 0 still means no rotation and remains the midpoint of the slider, so a stored value is unaffected
 * by the widened range.
 */
const val MIN_ADAPTIVE_STAGE_ROTATION_DEGREES = -18
const val MAX_ADAPTIVE_STAGE_ROTATION_DEGREES = 18
const val MIN_ADAPTIVE_STAGE_CORNER_RADIUS_DP = 0
const val MAX_ADAPTIVE_STAGE_CORNER_RADIUS_DP = 64
const val MIN_ADAPTIVE_STAGE_CONTENT_PADDING_DP = 0
const val MAX_ADAPTIVE_STAGE_CONTENT_PADDING_DP = 64
const val MIN_ADAPTIVE_STAGE_ARGB = 0L
const val MAX_ADAPTIVE_STAGE_ARGB = 0xFFFFFFFFL
const val MIN_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_GLASS_TRANSPARENCY_PERCENT = 95
const val MIN_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_BLUR_STRENGTH_PERCENT = 100
const val MIN_ADAPTIVE_STAGE_SATURATION_PERCENT = 50
const val MAX_ADAPTIVE_STAGE_SATURATION_PERCENT = 150
const val MIN_ADAPTIVE_STAGE_CONTRAST_PERCENT = 75
const val MAX_ADAPTIVE_STAGE_CONTRAST_PERCENT = 150
const val MIN_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP = 0
const val MAX_ADAPTIVE_STAGE_OUTLINE_WIDTH_DP = 4
const val MIN_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_HIGHLIGHT_PERCENT = 100
const val MIN_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP = 0
const val MAX_ADAPTIVE_STAGE_SHADOW_ELEVATION_DP = 32
const val MIN_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT = 0

// Was 40, paired with a /500f alpha divisor in AdaptiveStageTexture -- max alpha was only 0.08
// (8% opacity), barely perceptible. Now 100, paired with a /250f divisor, for a max alpha of 0.4.
const val MAX_ADAPTIVE_STAGE_TEXTURE_INTENSITY_PERCENT = 100
const val MIN_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT = 85
const val MAX_ADAPTIVE_STAGE_TEXT_SCALE_PERCENT = 130
const val MIN_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS = 80
const val MAX_ADAPTIVE_STAGE_SETTLE_DURATION_MILLIS = 600
const val MIN_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS = 80
const val MAX_ADAPTIVE_STAGE_TRANSITION_DURATION_MILLIS = 700
const val MIN_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_SPRING_BOUNCINESS_PERCENT = 40
const val MIN_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_TRAVEL_INTENSITY_PERCENT = 150
const val MIN_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_PARALLAX_INTENSITY_PERCENT = 50
const val MIN_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT = 0
const val MAX_ADAPTIVE_STAGE_ROTATION_INTENSITY_PERCENT = 150
const val MIN_ADAPTIVE_STAGE_REACHABLE_CARD_WIDTH_DP = 160
const val MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP = 220

/**
 * A rail tile is deliberately much smaller than a full "reachable card" -- these mirror Android's own
 * accepted minimum touch-target size rather than [MIN_ADAPTIVE_STAGE_REACHABLE_CARD_WIDTH_DP]/
 * [MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP], which describe a full notification card, not an icon
 * tile.
 */
const val MIN_ADAPTIVE_STAGE_REACHABLE_RAIL_TILE_WIDTH_DP = 40
const val MIN_ADAPTIVE_STAGE_REACHABLE_RAIL_TILE_HEIGHT_DP = 40

/**
 * A settings preview is never touched -- it's a static illustration of the appearance choices,
 * not a real card stack -- so it only needs to stay legible, not [MIN_ADAPTIVE_STAGE_REACHABLE_CARD_WIDTH_DP]/
 * [MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP]'s touch-reachable size.
 */
const val MIN_ADAPTIVE_STAGE_LEGIBLE_PREVIEW_CARD_WIDTH_DP = 100
const val MIN_ADAPTIVE_STAGE_LEGIBLE_PREVIEW_CARD_HEIGHT_DP = 120
const val MIN_ADAPTIVE_STAGE_BACKGROUND_CARD_SCALE = 0.94f
