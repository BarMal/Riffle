package com.riffle.app.launcher

import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.TimeScapeAccentSource
import com.riffle.core.domain.launcher.settings.TimeScapeAppearanceSettings
import com.riffle.core.domain.launcher.settings.TimeScapeBackgroundSource
import com.riffle.core.domain.launcher.settings.TimeScapeContentDensity
import com.riffle.core.domain.launcher.settings.TimeScapeGeometry
import com.riffle.core.domain.launcher.settings.TimeScapeMotion
import com.riffle.core.domain.launcher.settings.TimeScapeSurface
import com.riffle.core.domain.launcher.settings.TimeScapeTypography
import com.riffle.core.domain.launcher.settings.TimeScapeViewportDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimeScapeCardSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyBackgroundSourceRendersCardContentWithAFallback() {
        val appearances =
            TimeScapeBackgroundSource.entries.map { source ->
                TimeScapeAppearanceSettings(surface = TimeScapeSurface(backgroundSource = source))
            }

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    TimeScapeCardSurface(
                        appearance = appearance,
                        background = TimeScapeCardBackground(appSeed = "card-$index"),
                    ) {
                        Text("Card $index")
                    }
                }
            }
        }

        appearances.indices.forEach { index -> composeRule.onNodeWithText("Card $index").assertIsDisplayed() }
    }

    @Test
    fun reducedTransparencyKeepsAnOpaqueLegibleSurface() {
        val colors =
            resolveTimeScapeCardColors(
                appearance =
                    TimeScapeAppearanceSettings(
                        surface =
                            TimeScapeSurface(
                                customBackgroundArgb = 0xFF101010L,
                                backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            ),
                        motion = TimeScapeMotion(reducedTransparency = true),
                    ),
                background = TimeScapeCardBackground(),
                materialBackground = Color.White,
                materialAccent = Color.Blue,
            )

        assertEquals(1f, colors.glass.alpha)
        assertTrue(contrastRatio(colors.foreground, colors.glass) >= 4.5f)
    }

    @Test
    fun saturationAndContrastAdjustFallbackBackgrounds() {
        val original = Color(0.8f, 0.2f, 0.1f)

        val desaturated = timeScapeAdjustedColor(original, saturationPercent = 0, contrastPercent = 100)
        val contrasted = timeScapeAdjustedColor(original, saturationPercent = 100, contrastPercent = 150)

        assertEquals(desaturated.red, desaturated.green, 0.001f)
        assertEquals(desaturated.green, desaturated.blue, 0.001f)
        assertNotEquals(original, contrasted)
    }

    @Test
    fun typographyProjectsAccentTextScaleAndContentDensity() {
        var observedAccent = Color.Unspecified
        var observedFontScale = 0f
        val appearance =
            TimeScapeAppearanceSettings(
                typography =
                    TimeScapeTypography(
                        accentSource = TimeScapeAccentSource.CUSTOM,
                        customAccentArgb = 0xFF336699L,
                        contentDensity = TimeScapeContentDensity.EXPANDED,
                        textScalePercent = 130,
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                TimeScapeCardSurface(appearance, TimeScapeCardBackground()) {
                    observedAccent = MaterialTheme.colorScheme.primary
                    observedFontScale = LocalDensity.current.fontScale
                    Text("Styled card")
                }
            }
        }

        composeRule.onNodeWithText("Styled card").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(Color(0xFF336699), observedAccent)
            assertEquals(1.3f, observedFontScale, 0.001f)
            assertEquals(1.2f, timeScapeContentDensityScale(TimeScapeContentDensity.EXPANDED), 0.001f)
            assertEquals(0.8f, timeScapeContentDensityScale(TimeScapeContentDensity.COMPACT), 0.001f)
        }
    }

    @Test
    fun cardActionsRemainLegibleForLowContrastCustomAccents() {
        val actionColors = mutableMapOf<Int, Pair<Color, Color>>()
        val appearances =
            listOf(
                TimeScapeAppearanceSettings(
                    surface =
                        TimeScapeSurface(
                            backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            customBackgroundArgb = 0xFFFFFFFFL,
                            glassTintArgb = 0xFFFFFFFFL,
                            glassTransparencyPercent = 0,
                        ),
                    typography =
                        TimeScapeTypography(
                            accentSource = TimeScapeAccentSource.CUSTOM,
                            customAccentArgb = 0xFFFFFFFFL,
                        ),
                ),
                TimeScapeAppearanceSettings(
                    surface =
                        TimeScapeSurface(
                            backgroundSource = TimeScapeBackgroundSource.CUSTOM_SOLID,
                            customBackgroundArgb = 0xFF000000L,
                            glassTintArgb = 0xFF000000L,
                            glassTransparencyPercent = 0,
                        ),
                    typography =
                        TimeScapeTypography(
                            accentSource = TimeScapeAccentSource.CUSTOM,
                            customAccentArgb = 0xFF000000L,
                        ),
                ),
            )

        composeRule.setContent {
            MaterialTheme {
                appearances.forEachIndexed { index, appearance ->
                    TimeScapeCardSurface(appearance, TimeScapeCardBackground()) {
                        actionColors[index] = MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        TextButton(onClick = {}) { Text("Action $index") }
                    }
                }
            }
        }

        composeRule.onNodeWithText("Action 0").assertIsDisplayed()
        composeRule.onNodeWithText("Action 1").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(2, actionColors.size)
            appearances.indices.forEach { index ->
                val colors =
                    resolveTimeScapeCardColors(
                        appearance = appearances[index],
                        background = TimeScapeCardBackground(),
                        materialBackground = Color.Black,
                        materialAccent = Color.Blue,
                    )
                val (action, onAction) = requireNotNull(actionColors[index])
                assertTrue(contrastRatio(action, colors.glass) >= 4.5f)
                assertTrue(contrastRatio(onAction, action) >= 4.5f)
            }
        }
    }

    @Test
    fun oldPlatformDisablesBlurWithoutChangingStoredAppearance() {
        val appearance = TimeScapeAppearanceSettings(surface = TimeScapeSurface(blurStrengthPercent = 72))

        assertFalse(timeScapeRendererCapabilities(sdkInt = 30).supportsBlur)
        assertEquals(72, appearance.surface.blurStrengthPercent)
        assertEquals(0, appearance.effectiveFor(timeScapeRendererCapabilities(sdkInt = 30)).surface.blurStrengthPercent)
    }

    @Test
    fun corruptOrOversizedArtworkFallsBackAndDecodingIsBounded() {
        assertNull(decodeTimeScapeArtwork("not-base64"))
        assertNull(decodeTimeScapeArtwork("a".repeat(2_800_001)))
        assertEquals(2, timeScapeArtworkSampleSize(width = 1_024, height = 600))
        assertEquals(4, timeScapeArtworkSampleSize(width = 3_000, height = 900))
    }

    @Test
    fun constrainedViewportUsesTheReachableNotificationListFallback() {
        val profile = AppProfile.personal()
        val group =
            AppNotificationGroup(
                packageName = AppPackageName("com.riffle.mail"),
                profileId = profile.id,
                latestCategory = NotificationCategory.EMAIL,
                latestAgeBucket = NotificationAgeBucket.RECENT,
                notifications =
                    listOf(
                        LauncherNotification(
                            key = LauncherNotificationKey("mail-1"),
                            packageName = AppPackageName("com.riffle.mail"),
                            profileId = profile.id,
                            title = "Mail",
                            postedAtEpochMillis = 1L,
                        ),
                    ),
            )

        composeRule.setContent {
            MaterialTheme {
                GeneratedNotificationCardsPage(
                    groups = listOf(group),
                    notificationAccessStatus = NotificationAccessStatus.GRANTED,
                    apps = emptyList(),
                    onAction = {},
                    reducedMotion = false,
                    modifier = Modifier.requiredSize(80.dp),
                )
            }
        }

        composeRule.onNodeWithTag(GENERATED_NOTIFICATION_CARD_LIST_TEST_TAG).assertIsDisplayed()
    }

    @Test
    fun stackedCardsUseTheResolutionCappedContentPadding() {
        val resolution =
            TimeScapeAppearanceSettings(
                geometry = TimeScapeGeometry(contentPaddingDp = 64),
            ).resolveCardStack(
                viewport = TimeScapeViewportDp(widthDp = 288, heightDp = 420),
            )

        assertTrue(resolution.isUsable)
        assertEquals(
            resolution.contentPaddingDp.dp,
            generatedNotificationCardContentPadding(resolution),
        )
        assertTrue(resolution.contentPaddingDp <= resolution.cardWidthDp / 4)
        assertTrue(resolution.contentPaddingDp <= resolution.cardHeightDp / 4)
    }

    @Test
    fun resolvedStackRetainsFocusedCardAsHighestOrderEntryWithoutMotion() {
        val appearance = TimeScapeAppearanceSettings(motion = TimeScapeMotion(reducedMotion = true))
        val entries =
            appearance
                .resolveCardStack(
                    viewport = com.riffle.core.domain.launcher.settings.TimeScapeViewportDp(800, 1_200),
                    globalReducedMotion = true,
                ).layoutPolicy
                .entries(cardCount = 3, activeIndex = 1, reducedMotion = true)

        assertEquals(1, entries.maxBy { entry -> entry.order }.cardIndex)
        assertTrue(entries.all { entry -> entry.rotationDegrees == 0f })
    }
}
