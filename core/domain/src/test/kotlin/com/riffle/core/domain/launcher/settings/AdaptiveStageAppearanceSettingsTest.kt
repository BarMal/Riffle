package com.riffle.core.domain.launcher.settings

import com.riffle.core.domain.launcher.cards.CardStackAnimationEasing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AdaptiveStageAppearanceSettingsTest {
    @Test
    fun modernDefaultIsCoherentAndMatchesThePlainConstructorDefault() {
        val modern = AdaptiveStageAppearanceSettings.modern()

        // There is no preset system (#1058 removed it): modern() is just the folded layout's own
        // literal default field values, identical to the plain no-arg constructor.
        assertEquals(AdaptiveStageAppearanceSettings(), modern)
        assertTrue(modern.geometry.visibleDepth >= 1)
        assertTrue(modern.geometry.cardAspectRatioPercent in 55..100)
    }

    @Test
    fun coercionKeepsEveryImportedValueInItsSafeRange() {
        val coerced =
            AdaptiveStageAppearanceSettings(
                version = 99,
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = -1, visibleDepth = 99, contentPaddingDp = -1),
                surface = AdaptiveStageSurface(blurStrengthPercent = 999, customBackgroundArgb = -1),
                typography = AdaptiveStageTypography(textScalePercent = 999, customAccentArgb = -1),
                motion = AdaptiveStageMotion(settleDurationMillis = -1, parallaxIntensityPercent = 999),
            ).coerce()

        assertEquals(CURRENT_ADAPTIVE_STAGE_APPEARANCE_VERSION, coerced.version)
        assertEquals(55, coerced.geometry.cardAspectRatioPercent)
        assertEquals(6, coerced.geometry.visibleDepth)
        assertEquals(0, coerced.geometry.contentPaddingDp)
        assertEquals(100, coerced.surface.blurStrengthPercent)
        assertEquals(0, coerced.surface.customBackgroundArgb)
        assertEquals(130, coerced.typography.textScalePercent)
        assertEquals(80, coerced.motion.settleDurationMillis)
        assertEquals(50, coerced.motion.parallaxIntensityPercent)
    }

    @Test
    fun capabilityAndAccessibilityFallbacksDoNotRewriteIntent() {
        val stored =
            AdaptiveStageAppearanceSettings(
                surface = AdaptiveStageSurface(blurStrengthPercent = 60, textureIntensityPercent = 20),
                motion = AdaptiveStageMotion(reducedMotion = true, reducedTransparency = true),
            )

        val effective =
            stored.effectiveFor(
                AdaptiveStageRendererCapabilities(
                    supportsBlur = false,
                    supportsTexture = false,
                ),
            )

        assertEquals(60, stored.surface.blurStrengthPercent)
        assertEquals(0, effective.surface.blurStrengthPercent)
        assertEquals(0, effective.surface.textureIntensityPercent)
        assertEquals(0, effective.motion.enterDurationMillis)
        assertEquals(AdaptiveStageEasing.STANDARD, effective.motion.easing)
        assertEquals(0, effective.motion.springBouncinessPercent)
        assertEquals(0, effective.motion.travelIntensityPercent)
        assertFalse(effective.surface.glassTransparencyPercent > 0)
    }

    @Test
    fun resolvesAppearanceIntoBoundedCardStackTokens() {
        val viewport =
            AdaptiveStageViewportDp(
                widthDp = 800,
                heightDp = 1200,
                insets = AdaptiveStageInsetsDp(startDp = 24, topDp = 48, endDp = 24, bottomDp = 48),
            )
        val resolution = AdaptiveStageAppearanceSettings().resolveCardStack(viewport)
        val entries = resolution.layoutPolicy.entries(cardCount = 9, activeIndex = 4)
        val horizontalTravel = (viewport.safeWidthDp - resolution.cardWidthDp) / 2f
        val verticalTravel = (viewport.safeHeightDp - resolution.cardHeightDp) / 2f

        assertTrue(resolution.isUsable)
        assertTrue(resolution.cardWidthDp <= viewport.safeWidthDp)
        assertTrue(resolution.cardHeightDp <= viewport.safeHeightDp)
        assertTrue(entries.all { entry -> kotlin.math.abs(entry.offset) <= horizontalTravel })
        assertTrue(entries.all { entry -> kotlin.math.abs(entry.verticalOffset) <= verticalTravel })
        assertTrue(resolution.animation.reflowsStack)
    }

    @Test
    fun primaryRoleReservesGenuineFanStageInsteadOfLettingTheFocusedCardFillTheViewport() {
        // A representative real Home viewport (not the settings preview's small box). Before
        // PRIMARY_FAN_STAGE_MARGIN_FRACTION existed, the focused card filled essentially the whole
        // viewport, which starved every fan/offset/rotation control down to a couple of dp
        // regardless of the user's configured geometry -- see the reference "Calm" timescape's own
        // ~58%-of-viewport focused card for the same idea.
        val viewport = AdaptiveStageViewportDp(widthDp = 376, heightDp = 684)
        val resolution = AdaptiveStageAppearanceSettings().resolveCardStack(viewport)

        assertTrue(resolution.isUsable)
        assertTrue(resolution.cardWidthDp < (viewport.safeWidthDp * 0.85).toInt())
    }

    @Test
    fun primaryRoleAppliesTheConfiguredHorizontalOffsetInsteadOfCrushingItToAFewDp() {
        val viewport = AdaptiveStageViewportDp(widthDp = 376, heightDp = 684)
        val resolution = AdaptiveStageAppearanceSettings().resolveCardStack(viewport)
        val depthOneOffset =
            resolution.layoutPolicy.entries(cardCount = 5, activeIndex = 2).first { it.depth == 1 }.offset

        // Default horizontalOffsetDp is 20dp; the pre-fix starved travel budget crushed this down
        // to roughly 2dp regardless of the configured value.
        assertTrue(kotlin.math.abs(depthOneOffset) > 15f)
    }

    @Test
    fun railVerticalTravelReservesCardHeightNotCardWidth() {
        // Regression guard for a copy/paste bug where verticalTravel's reservation multiplied
        // stackBounds.maxHeightScale by cardWidthDp instead of cardHeightDp -- invisible whenever
        // width and height happen to be equal, so this deliberately uses a non-square aspect ratio
        // (cardHeightDp is meaningfully larger than cardWidthDp here) to catch it. The buggy
        // version reserves too little vertical room and lets verticalStep reach further toward the
        // full requested spacing than the real geometry allows.
        val viewport = AdaptiveStageViewportDp(widthDp = 200, heightDp = 800)
        val settings =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = 60, verticalSpacingDp = 96, visibleDepth = 1),
            )

        val resolution = settings.resolveCardStack(viewport, role = AdaptiveStageCardStackRole.RAIL)
        val backgroundOffset =
            resolution.layoutPolicy.entries(cardCount = 3, activeIndex = 1).first { it.depth > 0 }.verticalOffset

        assertTrue(resolution.isUsable)
        assertTrue(resolution.cardHeightDp > resolution.cardWidthDp)
        assertTrue(kotlin.math.abs(backgroundOffset) < 96f)
    }

    @Test
    fun projectsPersistedMotionIntoTheCardStackRendererTokens() {
        val resolution =
            AdaptiveStageAppearanceSettings(
                motion =
                    AdaptiveStageMotion(
                        settleDurationMillis = 410,
                        reflowDurationMillis = 360,
                        enterDurationMillis = 300,
                        easing = AdaptiveStageEasing.EMPHASIZED,
                        springBouncinessPercent = 35,
                    ),
            ).resolveCardStack(AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200))

        assertEquals(360, resolution.animation.durationMillis)
        assertEquals(300, resolution.animation.enterDurationMillis)
        assertEquals(410, resolution.animation.settleDurationMillis)
        assertEquals(CardStackAnimationEasing.EMPHASIZED, resolution.animation.easing)
        assertEquals(35, resolution.animation.springBouncinessPercent)
    }

    @Test
    fun focusedScaleCannotOverflowTheSafeViewport() {
        val viewport = AdaptiveStageViewportDp(widthDp = 400, heightDp = 400)
        val resolution =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = 100, focusedScalePercent = 115),
            ).resolveCardStack(viewport)

        assertTrue(resolution.isUsable)
        assertTrue(kotlin.math.ceil(resolution.cardWidthDp * resolution.focusedScale) <= viewport.safeWidthDp)
        assertTrue(kotlin.math.ceil(resolution.cardHeightDp * resolution.focusedScale) <= viewport.safeHeightDp)
    }

    @Test
    fun minimumFocusedScaleKeepsBackgroundCardsWithinHorizontalAndVerticalBounds() {
        val viewport = AdaptiveStageViewportDp(widthDp = 400, heightDp = 400)
        val resolution =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = 100, focusedScalePercent = 85),
            ).resolveCardStack(viewport)
        val backgroundEntries =
            resolution.layoutPolicy.entries(cardCount = 9, activeIndex = 4).filter { it.depth > 0 }

        assertTrue(resolution.isUsable)
        assertTrue(
            backgroundEntries.all { entry ->
                kotlin.math.abs(entry.offset) + resolution.cardWidthDp * entry.scale / 2f <= viewport.safeWidthDp / 2f
            },
        )
        assertTrue(
            backgroundEntries.all { entry ->
                kotlin.math.abs(entry.verticalOffset) + resolution.cardHeightDp * entry.scale / 2f <=
                    viewport.safeHeightDp / 2f
            },
        )
    }

    @Test
    fun maximumRotationKeepsBackgroundCardsWithinHorizontalAndVerticalBounds() {
        val viewport = AdaptiveStageViewportDp(widthDp = 400, heightDp = 400)
        val resolution =
            AdaptiveStageAppearanceSettings(
                geometry =
                    AdaptiveStageGeometry(
                        cardAspectRatioPercent = 100,
                        focusedScalePercent = 85,
                        visibleDepth = 1,
                        rotationDegrees = 18,
                    ),
            ).resolveCardStack(viewport)
        assertTrue(resolution.isUsable)
        assertTrue(
            listOf(0, 2).all { focusedIndex ->
                resolution.layoutPolicy.entries(cardCount = 3, activeIndex = focusedIndex)
                    .filter { it.depth > 0 }
                    .all { entry ->
                        val angleRadians = Math.toRadians(entry.rotationDegrees.toDouble())
                        val renderedWidth =
                            entry.scale *
                                (
                                    resolution.cardWidthDp * kotlin.math.cos(angleRadians) +
                                        resolution.cardHeightDp * kotlin.math.abs(kotlin.math.sin(angleRadians))
                                )
                        kotlin.math.abs(entry.offset) + renderedWidth / 2f <=
                            viewport.safeWidthDp / 2f + 0.01f
                    }
            },
        )
        assertTrue(
            listOf(0, 2).all { focusedIndex ->
                resolution.layoutPolicy.entries(cardCount = 3, activeIndex = focusedIndex)
                    .filter { it.depth > 0 }
                    .all { entry ->
                        val angleRadians = Math.toRadians(entry.rotationDegrees.toDouble())
                        val renderedHeight =
                            entry.scale *
                                (
                                    resolution.cardWidthDp * kotlin.math.abs(kotlin.math.sin(angleRadians)) +
                                        resolution.cardHeightDp * kotlin.math.cos(angleRadians)
                                )
                        kotlin.math.abs(entry.verticalOffset) + renderedHeight / 2f <=
                            viewport.safeHeightDp / 2f + 0.01f
                    }
            },
        )
    }

    @Test
    fun focusedGapAndFanDirectionAffectBoundedLayoutTokens() {
        val viewport = AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200)

        fun resolve(
            direction: AdaptiveStageFanDirection,
            gapDp: Int,
        ) = AdaptiveStageAppearanceSettings(
            geometry = AdaptiveStageGeometry(fanDirection = direction, focusedGapDp = gapDp),
        ).resolveCardStack(viewport)

        val towardEnd = resolve(AdaptiveStageFanDirection.END, gapDp = 32)
        val towardStart = resolve(AdaptiveStageFanDirection.START, gapDp = 32)
        val noFan = resolve(AdaptiveStageFanDirection.NONE, gapDp = 32)
        val noGap = resolve(AdaptiveStageFanDirection.END, gapDp = 0)
        val endEntry = towardEnd.layoutPolicy.entries(cardCount = 3, activeIndex = 1).last { it.cardIndex == 2 }
        val startEntry = towardStart.layoutPolicy.entries(cardCount = 3, activeIndex = 1).last { it.cardIndex == 2 }
        val noFanEntry = noFan.layoutPolicy.entries(cardCount = 3, activeIndex = 1).last { it.cardIndex == 2 }
        val noGapEntry = noGap.layoutPolicy.entries(cardCount = 3, activeIndex = 1).last { it.cardIndex == 2 }
        val horizontalTravel = (viewport.safeWidthDp - towardEnd.cardWidthDp * towardEnd.focusedScale) / 2f

        assertTrue(endEntry.offset > noGapEntry.offset)
        assertEquals(-endEntry.offset, startEntry.offset)
        assertEquals(0f, noFanEntry.offset)
        assertTrue(kotlin.math.abs(endEntry.offset) <= horizontalTravel)
    }

    @Test
    fun verticalFanDirectionIsIndependentOfHorizontalFanDirection() {
        val viewport = AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200)

        fun resolve(verticalDirection: AdaptiveStageFanDirection) =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(verticalFanDirection = verticalDirection),
            ).resolveCardStack(viewport)

        val down = resolve(AdaptiveStageFanDirection.END)
        val up = resolve(AdaptiveStageFanDirection.START)
        val flat = resolve(AdaptiveStageFanDirection.NONE)

        fun laterCardVerticalOffset(resolution: AdaptiveStageCardStackResolution) =
            resolution.layoutPolicy.entries(cardCount = 3, activeIndex = 1).last { it.cardIndex == 2 }.verticalOffset

        assertTrue(laterCardVerticalOffset(down) > 0f)
        assertEquals(-laterCardVerticalOffset(down), laterCardVerticalOffset(up))
        assertEquals(0f, laterCardVerticalOffset(flat))
        // Horizontal fan direction (default END) is untouched by the vertical-only setting above.
        val stillFansHorizontally =
            down.layoutPolicy.entries(cardCount = 3, activeIndex = 1).last { it.cardIndex == 2 }.offset
        assertTrue(stillFansHorizontally > 0f)
    }

    @Test
    fun cardSizeScalesTheCardDownWithoutChangingItsAspectRatio() {
        // Before cardSizePercent existed, aspect ratio was the only size-adjacent knob: shrinking
        // the card meant reshaping it (implying a fixed area), never leaving genuine empty stage
        // around a deliberately small card of the same shape.
        val viewport = AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200)
        val full =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = 80, cardSizePercent = 100),
            ).resolveCardStack(viewport)
        val half =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = 80, cardSizePercent = 50),
            ).resolveCardStack(viewport)

        assertTrue(half.isUsable)
        assertTrue(half.cardWidthDp < full.cardWidthDp)
        val fullAspectRatio = full.cardWidthDp.toFloat() / full.cardHeightDp
        val halfAspectRatio = half.cardWidthDp.toFloat() / half.cardHeightDp
        assertEquals(fullAspectRatio, halfAspectRatio, 0.02f)
    }

    @Test
    fun cardSizeAtMaximumReachesAGenuinelyFullBleedCardWithNoReservedMargin() {
        // The regression this guards: 100% used to still leave PRIMARY's old fixed 25%-of-viewport
        // margin in place regardless of this setting, so the card could never actually reach the
        // screen edge no matter how high a user pushed the slider. A square viewport and square
        // card aspect ratio make width and height equally binding, so the fitted size is exactly
        // determined by the viewport and content padding alone if (and only if) zero stage margin
        // is genuinely being reserved.
        val viewport = AdaptiveStageViewportDp(widthDp = 400, heightDp = 400)
        val resolution =
            AdaptiveStageAppearanceSettings(
                geometry =
                    AdaptiveStageGeometry(
                        cardAspectRatioPercent = 100,
                        cardSizePercent = MAX_ADAPTIVE_STAGE_CARD_SIZE_PERCENT,
                    ),
            ).resolveCardStack(viewport)
        val expectedFullBleedSizeDp = viewport.widthDp - 2 * AdaptiveStageGeometry().contentPaddingDp

        assertTrue(resolution.isUsable)
        assertEquals(expectedFullBleedSizeDp, resolution.cardWidthDp)
        assertEquals(expectedFullBleedSizeDp, resolution.cardHeightDp)
    }

    @Test
    fun cardSizeIsClampedToItsSafeRange() {
        val coerced = AdaptiveStageGeometry(cardSizePercent = 5).coerce()
        val coercedHigh = AdaptiveStageGeometry(cardSizePercent = 500).coerce()

        assertEquals(MIN_ADAPTIVE_STAGE_CARD_SIZE_PERCENT, coerced.cardSizePercent)
        assertEquals(MAX_ADAPTIVE_STAGE_CARD_SIZE_PERCENT, coercedHigh.cardSizePercent)
    }

    @Test
    fun railRoleIgnoresCardSizeToStayFillingItsNarrowStrip() {
        // RAIL must keep filling its own physical strip regardless of the user's PRIMARY-facing
        // cardSizePercent choice, exactly like it already ignores the fan-stage margin (#1054).
        val railViewport = AdaptiveStageViewportDp(widthDp = 104, heightDp = 720)
        val settings =
            AdaptiveStageAppearanceSettings.unfolded()
                .copy(geometry = AdaptiveStageAppearanceSettings.unfolded().geometry.copy(cardSizePercent = 50))

        val full =
            AdaptiveStageAppearanceSettings.unfolded()
                .resolveCardStack(railViewport, role = AdaptiveStageCardStackRole.RAIL)
        val shrunk = settings.resolveCardStack(railViewport, role = AdaptiveStageCardStackRole.RAIL)

        assertEquals(full.cardWidthDp, shrunk.cardWidthDp)
        assertEquals(full.cardHeightDp, shrunk.cardHeightDp)
    }

    @Test
    fun cardAspectRatioCanRenderWiderThanTallNowThatTheRangeExtendsPastSquare() {
        val viewport = AdaptiveStageViewportDp(widthDp = 1200, heightDp = 1200)
        val wide =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = MAX_ADAPTIVE_STAGE_CARD_ASPECT_RATIO_PERCENT),
            ).resolveCardStack(viewport)

        assertTrue(wide.cardWidthDp > wide.cardHeightDp)
    }

    @Test
    fun wideAspectRatiosStayUsableOnARealisticPhoneViewportInsteadOfNeedingMoreSpace() {
        // A regression guard: resolveCardSize's width ceiling is effectively independent of aspect
        // ratio (it's bounded by screen width, not by the requested shape), so a wider aspect ratio
        // can only ever produce a *shorter* card, never a taller one. Before isUsable() compared a
        // card's longer/shorter rendered side against the longer/shorter configured floor (instead
        // of literally width-vs-width and height-vs-height), every aspect ratio above ~110% was
        // rejected as "needs more space" on an ordinary phone-sized viewport, regardless of how
        // large the card actually rendered -- because its (now shorter) height fell under
        // MIN_ADAPTIVE_STAGE_REACHABLE_CARD_HEIGHT_DP, a floor calibrated for a portrait card's
        // long side, not its short one.
        val viewport = AdaptiveStageViewportDp(widthDp = 376, heightDp = 684)

        val moderatelyWide =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(cardAspectRatioPercent = 140),
            ).resolveCardStack(viewport)

        assertTrue(moderatelyWide.isUsable)
        assertTrue(moderatelyWide.cardWidthDp > moderatelyWide.cardHeightDp)
    }

    @Test
    fun reducedMotionResolutionUsesStaticStackTokens() {
        val resolution =
            AdaptiveStageAppearanceSettings(motion = AdaptiveStageMotion(reducedMotion = true))
                .resolveCardStack(AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200))
        val entries =
            resolution.layoutPolicy.entries(
                cardCount = 3,
                activeIndex = 1,
                reducedMotion = resolution.reducedMotion,
            )

        assertFalse(resolution.animation.animatesScale)
        assertFalse(resolution.animation.animatesRotation)
        assertEquals(0f, entries.maxOf { kotlin.math.abs(it.offset) })
        assertEquals(0f, entries.maxOf { kotlin.math.abs(it.rotationDegrees) })
        assertTrue(entries.any { it.verticalOffset != 0f })
    }

    @Test
    fun reducedMotionWideShortViewportReservesReachableBackgroundCardSeparation() {
        val resolution =
            AdaptiveStageAppearanceSettings(motion = AdaptiveStageMotion(reducedMotion = true))
                .resolveCardStack(AdaptiveStageViewportDp(widthDp = 1_200, heightDp = 800))
        val entries =
            resolution.layoutPolicy.entries(
                cardCount = 3,
                activeIndex = 1,
                reducedMotion = resolution.reducedMotion,
            )

        assertTrue(resolution.isUsable)
        assertTrue(entries.any { it.verticalOffset != 0f })
    }

    @Test
    fun reducedMotionZeroSpacingUsesTheReachableListFallback() {
        val resolution =
            AdaptiveStageAppearanceSettings(
                geometry = AdaptiveStageGeometry(verticalSpacingDp = 0),
                motion = AdaptiveStageMotion(reducedMotion = true),
            ).resolveCardStack(AdaptiveStageViewportDp(widthDp = 1_200, heightDp = 800))

        assertFalse(resolution.isUsable)
    }

    @Test
    fun globalReducedMotionUsesStaticAdaptiveStageTokensWithoutChangingStoredIntent() {
        val storedAppearance = AdaptiveStageAppearanceSettings()
        val resolution =
            LauncherSettings(
                cards = CardsSettings(adaptiveStageAppearance = storedAppearance),
                motion = MotionSettings(reducedMotion = true),
            ).resolveAdaptiveStageCardStack(AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200))
        val entries =
            resolution.layoutPolicy.entries(
                cardCount = 3,
                activeIndex = 1,
                reducedMotion = resolution.reducedMotion,
            )

        assertFalse(storedAppearance.motion.reducedMotion)
        assertTrue(resolution.reducedMotion)
        assertFalse(resolution.animation.reflowsStack)
        assertFalse(resolution.animation.animatesScale)
        assertFalse(resolution.animation.animatesRotation)
        assertEquals(0f, entries.maxOf { kotlin.math.abs(it.offset) })
        assertEquals(0f, entries.maxOf { kotlin.math.abs(it.rotationDegrees) })
        assertTrue(entries.any { it.verticalOffset != 0f })
    }

    @Test
    fun defaultRoleMatchesPrimaryByteForByte() {
        val viewport = AdaptiveStageViewportDp(widthDp = 800, heightDp = 1200)
        val settings = AdaptiveStageAppearanceSettings()

        assertEquals(
            settings.resolveCardStack(viewport, role = AdaptiveStageCardStackRole.PRIMARY),
            settings.resolveCardStack(viewport),
        )
    }

    @Test
    fun railRoleIsUsableAtAPhysicallyNarrowViewportThatPrimaryRoleRejects() {
        // A rail's own real strip -- narrow cross-axis, generous along-axis -- is far too small for
        // AdaptiveStageCardStackRole.PRIMARY's "reachable card" floor, but that floor is the wrong
        // yardstick for a small tile; RAIL's floor is Android's own minimum touch-target size instead.
        val railViewport = AdaptiveStageViewportDp(widthDp = 104, heightDp = 720)
        val settings = AdaptiveStageAppearanceSettings.unfolded()

        val primaryResolution = settings.resolveCardStack(railViewport, role = AdaptiveStageCardStackRole.PRIMARY)
        val railResolution = settings.resolveCardStack(railViewport, role = AdaptiveStageCardStackRole.RAIL)

        assertFalse(primaryResolution.isUsable)
        assertTrue(railResolution.isUsable)
        // A collapsed-to-depth-1 result (the PRIMARY-role fallback for an unusable layout) is exactly
        // the bug #1055 is fixing: verify RAIL role keeps the real configured depth instead.
        assertEquals(settings.geometry.visibleDepth, railResolution.layoutPolicy.maxVisibleDepth)
        assertEquals(1, primaryResolution.layoutPolicy.maxVisibleDepth)
    }

    @Test
    fun previewRoleIsUsableAtASettingsPreviewSizeThatPrimaryRoleRejects() {
        // The stack-bounding box reserves room for every background card's rotated silhouette, so
        // the focused card ends up much smaller than the viewport -- too small to clear PRIMARY's
        // touch-reachable floor at the settings preview's actual size, even though the illustration
        // itself is still perfectly legible. PREVIEW role exists because that floor is the wrong
        // yardstick for a static, never-touched illustration. 360x300 approximates the preview's
        // actual on-screen box (see AdaptiveStageAppearancePreview's placement in
        // AdaptiveStageAppearancePageContent).
        val previewViewport = AdaptiveStageViewportDp(widthDp = 360, heightDp = 300)
        val settings = AdaptiveStageAppearanceSettings()

        val primaryResolution = settings.resolveCardStack(previewViewport, role = AdaptiveStageCardStackRole.PRIMARY)
        val previewResolution = settings.resolveCardStack(previewViewport, role = AdaptiveStageCardStackRole.PREVIEW)

        assertFalse(primaryResolution.isUsable)
        assertTrue(previewResolution.isUsable)
        assertEquals(settings.geometry.visibleDepth, previewResolution.layoutPolicy.maxVisibleDepth)
        assertEquals(1, primaryResolution.layoutPolicy.maxVisibleDepth)
    }

    @Test
    fun previewRoleHasRealVerticalTravelAtARealisticSettingsPreviewBoxSize() {
        // Before PREVIEW got its own fan-stage margin, this box's height axis alone determined
        // the card's size (fanStageMarginFraction 0), leaving zero vertical travel -- vertical
        // spacing/curve had no visible effect specifically in this preview, regardless of their
        // slider values. 340x300 approximates the preview's actual on-screen box (see
        // AdaptiveStageAppearancePreview's placement in AdaptiveStageAppearancePageContent).
        val settings = AdaptiveStageAppearanceSettings()
        val resolution =
            settings.resolveCardStack(
                AdaptiveStageViewportDp(widthDp = 340, heightDp = 300),
                role = AdaptiveStageCardStackRole.PREVIEW,
            )
        val entries = resolution.layoutPolicy.entries(cardCount = 3, activeIndex = 0)

        assertTrue(resolution.isUsable)
        assertTrue(entries.any { entry -> entry.verticalOffset != 0f })
    }

    @Test
    fun unfoldedDefaultIsLinearSpacedAndNonOverlappingUnlikeModern() {
        val modern = AdaptiveStageAppearanceSettings.modern()
        val unfolded = AdaptiveStageAppearanceSettings.unfolded()

        assertEquals(0, unfolded.geometry.overlapPercent)
        assertEquals(0, unfolded.geometry.curveDp)
        assertEquals(0, unfolded.geometry.rotationDegrees)
        assertEquals(AdaptiveStageFanDirection.NONE, unfolded.geometry.fanDirection)
        assertTrue(unfolded.geometry.verticalSpacingDp > 0)
        assertTrue(modern.geometry.overlapPercent > 0)
        assertTrue(modern.geometry.curveDp > 0)
        assertNotEquals(modern.geometry, unfolded.geometry)
    }

    @Test
    fun railCardStackResolvesFromUnfoldedAppearanceAgainstItsOwnNarrowViewport() {
        val unfoldedAppearance =
            AdaptiveStageAppearanceSettings.unfolded()
                .copy(geometry = AdaptiveStageGeometry(overlapPercent = 0, verticalSpacingDp = 72))
        val railViewport = AdaptiveStageViewportDp(widthDp = 104, heightDp = 720)
        val resolution =
            LauncherSettings(cards = CardsSettings(unfoldedAppearance = unfoldedAppearance))
                .resolveAdaptiveStageRailCardStack(railViewport)
        val entries = resolution.layoutPolicy.entries(cardCount = 5, activeIndex = 2)

        assertTrue(resolution.isUsable)
        assertTrue(entries.filter { it.depth > 0 }.all { it.verticalOffset != 0f })
    }
}
