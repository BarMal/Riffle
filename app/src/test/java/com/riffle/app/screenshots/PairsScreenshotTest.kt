package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.stream.StreamCallbacks
import com.riffle.app.launcher.stream.StreamSurface
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.pairs.AppPair
import com.riffle.core.domain.launcher.stream.PairDraft
import com.riffle.core.domain.launcher.stream.PairSide
import com.riffle.core.domain.launcher.stream.PairsSection
import com.riffle.core.domain.launcher.stream.StreamLayout
import com.riffle.core.domain.launcher.stream.StreamPagePlanner
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Pairs stack (ADR 0003, Amendment 7): the blended ground, folded and unfolded, light and dark. */
internal object PairsFixtures {
    private val byIdentity = ScreenshotFixtures.installedApps.associate { it.identity to it.label }

    fun resolve(identity: AppIdentity): PairSide? =
        byIdentity[identity]?.let { PairSide(identity, it, ItemImageKeys.appIcon(identity)) }

    /** Three pairs: notes and maps sit side by side in the palette, mail and camera contrast, clock and notes close. */
    val three =
        listOf(
            AppPair("a", ScreenshotFixtures.notes.identity, ScreenshotFixtures.maps.identity, 3),
            AppPair("b", ScreenshotFixtures.mail.identity, ScreenshotFixtures.camera.identity, 2),
            AppPair("c", ScreenshotFixtures.clock.identity, ScreenshotFixtures.notes.identity, 1),
        )

    fun result(
        pairs: List<AppPair>,
        draft: PairDraft? = null,
    ): StreamSectionResult = PairsSection.result(pairs, draft, ::resolve)
}

private fun renderPairs(
    composeRule: androidx.compose.ui.test.junit4.ComposeContentTestRule,
    result: StreamSectionResult,
    paneWidth: Dp? = null,
    reducedTransparency: Boolean = false,
) {
    val sources = StreamSection.layoutSections.associateWith { StreamFixtures.ready(it) }
    composeRule.setContent {
        ScreenshotBackdrop(reducedTransparency = reducedTransparency) {
            val size = if (paneWidth != null) Modifier.width(paneWidth).fillMaxSize() else Modifier.fillMaxSize()
            Box(modifier = size) {
                StreamSurface(
                    layout = StreamLayout.DEFAULT,
                    results = sources + (StreamSection.PAIRS to result),
                    env = StreamFixtures.environment(),
                    callbacks = StreamCallbacks(),
                    contentPadding = PaddingValues(top = 48.dp, bottom = 96.dp),
                    initialPageKey = StreamPagePlanner.keyOf(StreamSection.PAIRS),
                )
            }
        }
    }
    composeRule.captureScreen()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class PairsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threePairsLight() = renderPairs(composeRule, PairsFixtures.result(PairsFixtures.three))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun threePairsDark() = renderPairs(composeRule, PairsFixtures.result(PairsFixtures.three))

    @Test
    fun draftWithOneHalfChosenLight() =
        renderPairs(
            composeRule,
            PairsFixtures.result(PairsFixtures.three, PairDraft(first = ScreenshotFixtures.mail.identity)),
        )

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun draftWithOneHalfChosenDark() =
        renderPairs(
            composeRule,
            PairsFixtures.result(PairsFixtures.three, PairDraft(first = ScreenshotFixtures.mail.identity)),
        )

    @Test
    fun emptyPageLight() = renderPairs(composeRule, PairsFixtures.result(emptyList()))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun emptyPageDark() = renderPairs(composeRule, PairsFixtures.result(emptyList()))

    @Test
    fun threePairsReducedTransparencyLight() =
        renderPairs(composeRule, PairsFixtures.result(PairsFixtures.three), reducedTransparency = true)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun threePairsReducedTransparencyDark() =
        renderPairs(composeRule, PairsFixtures.result(PairsFixtures.three), reducedTransparency = true)
}

/** The same on an unfolded posture: the Stream's column beside the Dock. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
class PairsUnfoldedScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun threePairsUnfoldedLight() = renderPairs(composeRule, PairsFixtures.result(PairsFixtures.three), COLUMN_WIDTH)

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun threePairsUnfoldedDark() = renderPairs(composeRule, PairsFixtures.result(PairsFixtures.three), COLUMN_WIDTH)

    @Test
    fun draftWithOneHalfChosenUnfoldedLight() =
        renderPairs(
            composeRule,
            PairsFixtures.result(PairsFixtures.three, PairDraft(first = ScreenshotFixtures.mail.identity)),
            COLUMN_WIDTH,
        )

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun emptyPageUnfoldedDark() = renderPairs(composeRule, PairsFixtures.result(emptyList()), COLUMN_WIDTH)

    private companion object {
        val COLUMN_WIDTH = 420.dp
    }
}
