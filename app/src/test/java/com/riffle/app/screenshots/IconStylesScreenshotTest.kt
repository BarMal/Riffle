package com.riffle.app.screenshots

import android.app.Application
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.HomeDestination
import com.riffle.app.launcher.SettingsPage
import com.riffle.app.launcher.SettingsSurface
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.app.launcher.icons.FakeIconPackSource
import com.riffle.app.launcher.icons.IconPackStore
import com.riffle.app.launcher.icons.LocalAppIconLoader
import com.riffle.app.launcher.icons.LocalAppIconPipeline
import com.riffle.app.launcher.icons.ThemedIconColors
import com.riffle.app.launcher.icons.letterAdaptiveIcon
import com.riffle.app.launcher.icons.letterLegacyIcon
import com.riffle.app.launcher.settingsSurfaceState
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Icon styles drawn through the real pipeline: each shape on the Home grid (the fixture apps alternate between an
 * adaptive icon and a legacy one), themed icons in light and dark, an icon pack, and the Icons page of Settings.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class IconStylesScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val packManifest =
        FakeIconPackSource(
            mapOf(
                EXAMPLE_PACK to
                    FakeIconPackSource.FakePack(
                        label = "Example pack",
                        appFilter = exampleAppFilter(),
                        drawables = exampleDrawables(),
                    ),
            ),
        )

    @Test
    fun shapeCircle() = home(IconStyleSettings(shape = IconShape.CIRCLE))

    @Test
    fun shapeSquircle() = home(IconStyleSettings(shape = IconShape.SQUIRCLE))

    @Test
    fun shapeRoundedSquare() = home(IconStyleSettings(shape = IconShape.ROUNDED_SQUARE))

    @Test
    fun shapeSquare() = home(IconStyleSettings(shape = IconShape.SQUARE))

    @Test
    fun shapeTeardrop() = home(IconStyleSettings(shape = IconShape.TEARDROP))

    @Test
    fun themedIconsLight() = home(IconStyleSettings(shape = IconShape.SQUIRCLE, themedIcons = true))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun themedIconsDark() = home(IconStyleSettings(shape = IconShape.SQUIRCLE, themedIcons = true))

    @Test
    fun themedIconsForAllAppsLight() =
        home(IconStyleSettings(shape = IconShape.SQUIRCLE, themedIcons = true, themeAllIcons = true))

    @Test
    fun iconPackApplied() = home(IconStyleSettings(shape = IconShape.CIRCLE, iconPack = EXAMPLE_PACK))

    @Test
    fun iconsPageLight() = iconsPage(IconStyleSettings(shape = IconShape.SQUIRCLE, themedIcons = true))

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun iconsPageDark() = iconsPage(IconStyleSettings(shape = IconShape.SQUIRCLE, themedIcons = true))

    private class Harness(
        packs: FakeIconPackSource,
    ) {
        private val packageManager = ApplicationProvider.getApplicationContext<Application>().packageManager
        private val identities = ScreenshotFixtures.installedApps.map { it.identity }
        val pipeline =
            AppIconPipeline(
                packageManager = packageManager,
                packs = IconPackStore(packs),
                activityIconFor = { identity -> fixtureIcon(identity) },
                packageIconFor = { error("not used") },
                launchActivityFor = { null },
                guard = {},
                sdkInt = ScreenshotDevices.SDK,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                isMainThread = { false },
            )
        val loader: AppIconLoader =
            PackageManagerAppIconLoader(
                packageManager,
                activityIconFor = { identity -> fixtureIcon(identity) },
                displayDensityFor = { DENSITY },
                pipeline = pipeline,
            )

        private fun fixtureIcon(identity: AppIdentity): Drawable? {
            val index = identities.indexOf(identity)
            if (index < 0) return null
            val color = PALETTE[index % PALETTE.size]
            val letter = ScreenshotFixtures.installedApps[index].label.take(1)
            return if (index % 2 == 0) letterAdaptiveIcon(color, letter) else letterLegacyIcon(color, letter)
        }

        /** Applies the style with the theme's colours and draws every icon now, so the first frame has them all. */
        @Composable
        fun Apply(style: IconStyleSettings) {
            val scheme = MaterialTheme.colorScheme
            remember(style, scheme) {
                loader.applyStyle(
                    style,
                    ThemedIconColors(scheme.primaryContainer.toArgb(), scheme.onPrimaryContainer.toArgb()),
                )
                pipeline.packs.watch(setOfNotNull(style.iconPack), style.iconPack)
                loader.preloadIcons(identities)
                0
            }
        }
    }

    private fun home(style: IconStyleSettings) {
        val harness = Harness(packManifest)
        val state: LauncherShellState =
            ScreenshotFixtures.homeState(dockPosition = DockPosition.BOTTOM)
                .let { it.copy(launcherSettings = it.launcherSettings.copy(icons = style)) }
        composeRule.setContent {
            ScreenshotBackdrop {
                harness.Apply(style)
                HomeDestination(state = state, appIconLoader = harness.loader, onAction = {})
            }
        }
        composeRule.captureScreen()
    }

    private fun iconsPage(style: IconStyleSettings) {
        val harness = Harness(packManifest)
        val style = style.copy(iconPack = EXAMPLE_PACK)
        val state =
            ScreenshotFixtures.cardsState(launcherSettings = LauncherSettings(icons = style))
                .settingsSurfaceState(appVersionLabel = "Riffle 1.0 (test)", appBuildIdentityLabel = "Build identity")
        composeRule.setContent {
            ScreenshotBackdrop {
                harness.Apply(style)
                CompositionLocalProvider(
                    LocalAppIconLoader provides harness.loader,
                    LocalAppIconPipeline provides harness.pipeline,
                ) {
                    SettingsSurface(state = state, initialPage = SettingsPage.APPEARANCE_ICONS, onAction = {})
                }
            }
        }
        composeRule.captureScreen()
    }

    private fun exampleAppFilter(): String {
        val items =
            ScreenshotFixtures.installedApps.take(4).joinToString("") { app ->
                val identity = app.identity
                """<item component="ComponentInfo{${identity.packageName.value}/${identity.activityName.value}}" """ +
                    """drawable="pack_${app.label.lowercase()}"/>"""
            }
        return "<resources>$items</resources>"
    }

    private fun exampleDrawables(): Map<String, Drawable> =
        ScreenshotFixtures.installedApps.take(4).associate { app ->
            "pack_${app.label.lowercase()}" to letterLegacyIcon(Color.rgb(40, 40, 48), app.label.take(1))
        }

    private companion object {
        const val EXAMPLE_PACK = "com.example.iconpack.sample"
        const val DENSITY = 2.625f
        val PALETTE =
            listOf(
                Color.rgb(26, 115, 232),
                Color.rgb(217, 48, 37),
                Color.rgb(24, 128, 56),
                Color.rgb(224, 150, 0),
                Color.rgb(147, 52, 230),
                Color.rgb(18, 181, 203),
                Color.rgb(232, 113, 10),
                Color.rgb(95, 99, 104),
            )
    }
}
