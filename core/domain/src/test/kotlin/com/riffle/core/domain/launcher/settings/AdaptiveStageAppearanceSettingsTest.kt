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
        // yardstick for a static, never-touched illustration.
        val previewViewport = AdaptiveStageViewportDp(widthDp = 360, heightDp = 220)
        val settings = AdaptiveStageAppearanceSettings()

        val primaryResolution = settings.resolveCardStack(previewViewport, role = AdaptiveStageCardStackRole.PRIMARY)
        val previewResolution = settings.resolveCardStack(previewViewport, role = AdaptiveStageCardStackRole.PREVIEW)

        assertFalse(primaryResolution.isUsable)
        assertTrue(previewResolution.isUsable)
        assertEquals(settings.geometry.visibleDepth, previewResolution.layoutPolicy.maxVisibleDepth)
        assertEquals(1, primaryResolution.layoutPolicy.maxVisibleDepth)
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
