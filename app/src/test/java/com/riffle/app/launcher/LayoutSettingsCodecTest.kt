package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.ModeSurface
import com.riffle.core.domain.launcher.home.dockFor
import com.riffle.core.domain.launcher.home.withDockEdge
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LayoutPosture
import com.riffle.core.domain.launcher.settings.LayoutSettings
import com.riffle.core.domain.launcher.settings.PageIndicatorPlacement
import com.riffle.core.domain.launcher.settings.PageScrollDirection
import com.riffle.core.domain.launcher.settings.PostureLayoutSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layout per posture (ADR 0003, Amendment 2) as it is stored: `layout.folded` and `layout.unfolded`, each with the
 * page scroll, the indicator and quick scroll. Old blobs hold none and decode to the defaults; the Dock edge is not
 * here at all, it stays where it always was (on each device class's Dock), so a stored choice keeps winning.
 */
class LayoutSettingsCodecTest {
    private val custom =
        LayoutSettings(
            folded =
                PostureLayoutSettings(
                    pageScroll = PageScrollDirection.VERTICAL,
                    indicator = PageIndicatorPlacement.OFF,
                    quickScroll = false,
                ),
            unfolded =
                PostureLayoutSettings(
                    pageScroll = PageScrollDirection.HORIZONTAL,
                    indicator = PageIndicatorPlacement.BESIDE_DOCK,
                    quickScroll = false,
                ),
        )

    private fun decodeLayout(layout: String) = decodeLauncherSettings("""{"version": 11, "layout": $layout}""").layout

    @Test
    fun theDefaultsAreFoldedSidewaysAndUnfoldedUpAndDown() {
        val defaults = LauncherSettings().layout
        assertEquals(PageScrollDirection.HORIZONTAL, defaults.folded.pageScroll)
        assertEquals(PageScrollDirection.VERTICAL, defaults.unfolded.pageScroll)
        assertEquals(LayoutSettings(), decodeLauncherSettings(encodeLauncherSettings(LauncherSettings())).layout)
    }

    @Test
    fun roundTripsBothPostures() {
        val settings = LauncherSettings(layout = custom)
        assertEquals(custom, decodeLauncherSettings(encodeLauncherSettings(settings)).layout)
    }

    @Test
    fun storesStableIdsPerPosture() {
        val layout = JSONObject(encodeLauncherSettings(LauncherSettings(layout = custom))).getJSONObject("layout")
        val folded = layout.getJSONObject("folded")
        assertEquals("vertical", folded.getString("pageScroll"))
        assertEquals("off", folded.getString("indicator"))
        assertFalse(folded.getBoolean("quickScroll"))
        assertEquals("horizontal", layout.getJSONObject("unfolded").getString("pageScroll"))
        assertEquals("beside_dock", layout.getJSONObject("unfolded").getString("indicator"))
    }

    @Test
    fun anOldBlobWithNoLayoutDecodesToTheDefaults() {
        // Settings saved by every build before the layout was per posture.
        assertEquals(LayoutSettings(), decodeLauncherSettings("""{"version": 11}""").layout)
        val older = """{"version": 5, "home": {"returnBehavior": "RESTORE"}}"""
        assertEquals(LayoutSettings(), decodeLauncherSettings(older).layout)
        assertEquals(LayoutSettings(), decodeLauncherSettings("{}").layout)
        val old = JSONObject(encodeLauncherSettings(LauncherSettings(layout = custom))).apply { remove("layout") }
        assertEquals(LayoutSettings(), decodeLauncherSettings(old.toString()).layout)
    }

    @Test
    fun anOldBlobKeepsEverythingElseItHeld() {
        val old =
            JSONObject(encodeLauncherSettings(LauncherSettings().copy(layout = custom))).apply { remove("layout") }
        val decoded = decodeLauncherSettings(old.toString())
        assertEquals(LauncherSettings(), decoded)
    }

    @Test
    fun aPostureThatIsMissingKeepsItsOwnDefault() {
        val decoded = decodeLayout("""{"folded": {"pageScroll": "vertical"}}""")
        assertEquals(PageScrollDirection.VERTICAL, decoded.folded.pageScroll)
        assertEquals(PostureLayoutSettings.UNFOLDED_DEFAULT, decoded.unfolded)
    }

    @Test
    fun aValueThisBuildDoesNotKnowReadsAsThatPosturesDefaultOneFieldAtATime() {
        val decoded =
            decodeLayout(
                """{"folded": {"pageScroll": "diagonal", "indicator": "off", "quickScroll": "yes"},
                    "unfolded": {"pageScroll": "horizontal", "indicator": "in_the_dock", "quickScroll": false}}""",
            )
        assertEquals(PageScrollDirection.HORIZONTAL, decoded.folded.pageScroll)
        assertEquals(PageIndicatorPlacement.OFF, decoded.folded.indicator)
        assertTrue(decoded.folded.quickScroll)
        assertEquals(PageScrollDirection.HORIZONTAL, decoded.unfolded.pageScroll)
        assertEquals(PageIndicatorPlacement.BESIDE_DOCK, decoded.unfolded.indicator)
        assertFalse(decoded.unfolded.quickScroll)
    }

    @Test
    fun aMistypedLayoutOrPostureReadsAsTheDefaults() {
        assertEquals(LayoutSettings(), decodeLauncherSettings("""{"version": 11, "layout": "wide"}""").layout)
        assertEquals(LayoutSettings(), decodeLayout("""{"folded": 3, "unfolded": []}"""))
    }

    @Test
    fun aPostureFromANewerBuildIsIgnored() {
        val decoded = decodeLayout("""{"folded": {"pageScroll": "vertical"}, "tabletop": {"pageScroll": "vertical"}}""")
        assertEquals(PageScrollDirection.VERTICAL, decoded.folded.pageScroll)
        assertEquals(PostureLayoutSettings.UNFOLDED_DEFAULT, decoded.unfolded)
    }

    // "Show apps behind the cards": a stored boolean per posture (folded off, unfolded on, unless it says otherwise)

    @Test
    fun appsBehindTheCardsRoundTripsAndIsStoredPerPosture() {
        val both =
            LayoutSettings()
                .withAppPeekThrough(LayoutPosture.UNFOLDED, false)
                .withAppPeekThrough(LayoutPosture.FOLDED, true)
        val settings = LauncherSettings(layout = both)
        assertEquals(both, decodeLauncherSettings(encodeLauncherSettings(settings)).layout)
        val stored = JSONObject(encodeLauncherSettings(settings)).getJSONObject("layout")
        // Unfolded keeps the key it always had; folded has its own.
        assertFalse(stored.getJSONObject("unfolded").getBoolean("appPeekThrough"))
        assertTrue(stored.getJSONObject("folded").getBoolean("appsBehindCards"))
        assertFalse(stored.getJSONObject("folded").has("appPeekThrough"))
    }

    @Test
    fun aLayoutStoredBeforeTheSettingDecodesToEachPosturesDefaultAndKeepsEverythingElse() {
        val decoded =
            decodeLayout(
                """{"folded": {"pageScroll": "vertical", "indicator": "off", "quickScroll": false},
                    "unfolded": {"pageScroll": "horizontal", "indicator": "beside_dock", "quickScroll": false}}""",
            )
        assertFalse(decoded.folded.appPeekThrough)
        assertTrue(decoded.unfolded.appPeekThrough)
        assertEquals(custom.copy(folded = custom.folded.copy(appPeekThrough = false)), decoded)
    }

    @Test
    fun aFoldedPeekThroughOfTrueStoredByTheEarlierBuildIsIgnored() {
        // The build before the folded setting existed wrote the (unused) value for both postures, true for folded.
        // Reading it would turn "apps behind the cards" on for everyone's folded phone: it decodes to the default.
        val decoded =
            decodeLayout(
                """{"folded": {"pageScroll": "horizontal", "appPeekThrough": true},
                    "unfolded": {"pageScroll": "vertical", "appPeekThrough": false}}""",
            )
        assertFalse(decoded.folded.appPeekThrough)
        assertFalse(decoded.unfolded.appPeekThrough)
    }

    @Test
    fun aMistypedSettingReadsAsThePosturesDefault() {
        assertTrue(decodeLayout("""{"unfolded": {"appPeekThrough": "no"}}""").unfolded.appPeekThrough)
        assertFalse(decodeLayout("""{"unfolded": {"appPeekThrough": false}}""").unfolded.appPeekThrough)
        assertFalse(decodeLayout("""{"folded": {"appsBehindCards": "yes"}}""").folded.appPeekThrough)
        assertTrue(decodeLayout("""{"folded": {"appsBehindCards": true}}""").folded.appPeekThrough)
    }

    // The Dock edge: stored where it always was, so a stored choice wins over the new default

    @Test
    fun aStoredUnfoldedDockEdgeStillWinsOverTheRightEdgeDefault() {
        val foldableLeft =
            HomeLayoutSet.standard()
                .selectDeviceClass(HomeLayoutDeviceClass.FOLDABLE)
                .withDockEdge(HomeLayoutDeviceClass.FOLDABLE, ModeSurface.HOME, DockPosition.LEFT)
        val layoutSet = foldableLeft

        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        val foldable = HomeLayoutDeviceClass.FOLDABLE
        assertEquals(DockPosition.LEFT, decoded.dockFor(foldable).position)
        assertEquals(
            DockPosition.LEFT,
            resolveDockPosition(decoded.dockFor(foldable).position, foldable.templateDockPosition),
        )
    }

    @Test
    fun aSetThatNeverChoseAnEdgeFollowsTheNewDefaultsPerPosture() {
        val decoded = decodeHomeLayoutSet(encodeHomeLayoutSet(HomeLayoutSet.standard()))

        listOf(HomeLayoutDeviceClass.PHONE to DockPosition.BOTTOM, HomeLayoutDeviceClass.FOLDABLE to DockPosition.RIGHT)
            .forEach { (deviceClass, expected) ->
                assertEquals(null, decoded.dockFor(deviceClass).position)
                assertEquals(
                    expected,
                    resolveDockPosition(decoded.dockFor(deviceClass).position, deviceClass.templateDockPosition),
                )
            }
    }

    @Test
    fun theLayoutPageShowsEachPosturesStoredEdgeOrItsDefault() {
        val start = LauncherShellState().settingsSurfaceState()
        assertEquals(DockPosition.BOTTOM, start.postureDockEdges.getValue(LayoutPosture.FOLDED))
        assertEquals(DockPosition.RIGHT, start.postureDockEdges.getValue(LayoutPosture.UNFOLDED))

        val chosenSet =
            LauncherShellState().homeLayoutSet
                .withDockEdge(HomeLayoutDeviceClass.FOLDABLE, ModeSurface.HOME, DockPosition.LEFT)
                .withDockEdge(HomeLayoutDeviceClass.PHONE, ModeSurface.HOME, DockPosition.RIGHT)
        val chosen =
            LauncherShellState().copy(homeLayout = chosenSet.activeLayout, homeLayoutSet = chosenSet)
                .settingsSurfaceState()
        assertEquals(DockPosition.RIGHT, chosen.postureDockEdges.getValue(LayoutPosture.FOLDED))
        assertEquals(DockPosition.LEFT, chosen.postureDockEdges.getValue(LayoutPosture.UNFOLDED))
    }
}
