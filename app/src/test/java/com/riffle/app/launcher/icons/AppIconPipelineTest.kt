package com.riffle.app.launcher.icons

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.core.graphics.drawable.toBitmap
import androidx.test.core.app.ApplicationProvider
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import com.riffle.core.domain.launcher.icons.LegacyIconHandling
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AppIconPipelineTest {
    private val packageManager = ApplicationProvider.getApplicationContext<Application>().packageManager
    private val adaptiveApp = identity("com.example.adaptive")
    private val legacyApp = identity("com.example.legacy")
    private val plainSource = FakeIconPackSource()
    private val icons = mutableMapOf<String, () -> Drawable>()
    private val violations = mutableListOf<String>()
    private val size = 108

    // The pipeline's state is written outside any composition; hand those writes to Compose before the next test.
    @After
    fun applyPendingSnapshotWrites() = Snapshot.sendApplyNotifications()

    private fun pipeline(
        source: FakeIconPackSource = plainSource,
        sdk: Int = 35,
    ) = AppIconPipeline(
        packageManager = packageManager,
        packs = IconPackStore(source),
        activityIconFor = { identity -> icons[identity.packageName.value]?.invoke() },
        packageIconFor = { name -> icons[name]?.invoke() ?: error("none") },
        launchActivityFor = { name -> identity(name) },
        guard = { violations += "main" },
        sdkInt = sdk,
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        isMainThread = { false },
    ).also {
        icons["com.example.adaptive"] = { adaptiveIcon(monochromeMark = Color.BLACK) }
        icons["com.example.legacy"] = { legacyIcon() }
    }

    private fun Bitmap.sameAs(other: Bitmap) = width == other.width && height == other.height && sameAs(other)

    @Test
    fun theDefaultStyleDrawsExactlyWhatThePlatformDrawsForAdaptiveAndLegacyIcons() {
        val pipeline = pipeline()

        listOf(adaptiveApp, legacyApp).forEach { app ->
            val expected = icons.getValue(app.packageName.value)().toBitmap(size, size)
            assertTrue(app.packageName.value, pipeline.forActivity(app, size)!!.sameAs(expected))
        }
    }

    @Test
    fun theDefaultStyleIsTheSamePixelsForAPackageIcon() {
        val pipeline = pipeline()
        val expected = icons.getValue("com.example.legacy")().toBitmap(size, size)

        assertTrue(pipeline.forPackage("com.example.legacy", size)!!.sameAs(expected))
    }

    @Test
    fun aCircleCutsTheCornersOfAnAdaptiveIconAndKeepsItsMiddle() {
        val pipeline = pipeline()
        pipeline.applyStyle(IconStyleSettings(shape = IconShape.CIRCLE), colors = null)

        val bitmap = pipeline.forActivity(adaptiveApp, size)!!

        assertEquals(0, bitmap.alphaAt(1, 1))
        assertEquals(0, bitmap.alphaAt(size - 2, size - 2))
        assertEquals(255, bitmap.alphaAt(size / 2, size / 2))
        // The foreground's mark is white in the middle; the background is the blue around it.
        assertEquals(Color.WHITE, bitmap.getPixel(size / 2, size / 2))
        assertEquals(Color.rgb(30, 100, 220), bitmap.getPixel(size / 2, 20))
    }

    @Test
    fun everyShapeIsOpaqueInTheMiddleAndTheCornerDiffersByShape() {
        val pipeline = pipeline()
        val cornerAlpha =
            IconShape.entries.filter { it != IconShape.SYSTEM }.associateWith { shape ->
                pipeline.applyStyle(IconStyleSettings(shape = shape), colors = null)
                val bitmap = pipeline.forActivity(adaptiveApp, size)!!
                assertEquals(shape.name, 255, bitmap.alphaAt(size / 2, size / 2))
                bitmap.alphaAt(2, 2)
            }

        assertEquals(0, cornerAlpha.getValue(IconShape.CIRCLE))
        assertTrue(cornerAlpha.getValue(IconShape.SQUARE) > 200)
        assertTrue(cornerAlpha.getValue(IconShape.SQUIRCLE) < cornerAlpha.getValue(IconShape.SQUARE))
    }

    @Test
    fun aLegacyIconSitsOnAPlateInsideTheShapeAndIsNotCropped() {
        val pipeline = pipeline()
        pipeline.applyStyle(IconStyleSettings(shape = IconShape.CIRCLE), colors = null)

        val bitmap = pipeline.forActivity(legacyApp, size)!!

        // The plate shows outside the scaled-down icon, the icon's own colour inside it, and the corner is cut.
        assertEquals(IconRenderer.LEGACY_PLATE_ARGB, bitmap.getPixel(size / 2, 8))
        assertEquals(Color.rgb(220, 60, 60), bitmap.getPixel(size / 2 - 18, size / 2 - 18).let { it })
        assertEquals(0, bitmap.alphaAt(1, 1))
    }

    @Test
    fun legacyIconsLeftAsTheyAreAreNotPlatedOrMasked() {
        val pipeline = pipeline()
        pipeline.applyStyle(
            IconStyleSettings(shape = IconShape.CIRCLE, legacyIcons = LegacyIconHandling.AS_IS),
            colors = null,
        )

        val expected = icons.getValue("com.example.legacy")().toBitmap(size, size)

        assertTrue(pipeline.forActivity(legacyApp, size)!!.sameAs(expected))
    }

    @Test
    fun themedIconsDrawTheMonochromeLayerInTheThemeColoursOnThePlate() {
        val pipeline = pipeline()
        val colors = ThemedIconColors(plateArgb = Color.rgb(10, 20, 30), glyphArgb = Color.rgb(240, 230, 220))
        pipeline.applyStyle(IconStyleSettings(shape = IconShape.SQUARE, themedIcons = true), colors)

        val bitmap = pipeline.forActivity(adaptiveApp, size)!!

        assertEquals(colors.glyphArgb, bitmap.getPixel(size / 2, size / 2))
        assertEquals(colors.plateArgb, bitmap.getPixel(size / 2, 14))
    }

    @Test
    fun themedIconsUnderTheSystemShapeAreMaskedByThePlatform() {
        val pipeline = pipeline()
        val colors = ThemedIconColors(plateArgb = Color.rgb(10, 20, 30), glyphArgb = Color.rgb(240, 230, 220))
        pipeline.applyStyle(IconStyleSettings(themedIcons = true), colors)

        val bitmap = pipeline.forActivity(adaptiveApp, size)!!

        assertEquals(colors.glyphArgb, bitmap.getPixel(size / 2, size / 2))
        assertEquals(colors.plateArgb, bitmap.getPixel(size / 2, 14))
    }

    @Test
    fun anAppWithoutAMonochromeLayerStaysAsItIsUnlessAllIconsAreThemed() {
        val pipeline = pipeline()
        val colors = ThemedIconColors(plateArgb = Color.rgb(10, 20, 30), glyphArgb = Color.rgb(240, 230, 220))
        icons["com.example.adaptive"] = { adaptiveIcon() }
        pipeline.applyStyle(IconStyleSettings(shape = IconShape.SQUARE, themedIcons = true), colors)
        val untouched = pipeline.forActivity(adaptiveApp, size)!!
        assertEquals(Color.rgb(30, 100, 220), untouched.getPixel(size / 2, 14))

        pipeline.applyStyle(
            IconStyleSettings(shape = IconShape.SQUARE, themedIcons = true, themeAllIcons = true),
            colors,
        )
        val generated = pipeline.forActivity(adaptiveApp, size)!!
        assertEquals(colors.plateArgb, generated.getPixel(size / 2, 14))
        assertEquals(colors.glyphArgb, generated.getPixel(size / 2, size / 2))
    }

    @Test
    fun aLegacyIconIsThemedWhenAllIconsAre() {
        val pipeline = pipeline()
        val colors = ThemedIconColors(plateArgb = Color.rgb(10, 20, 30), glyphArgb = Color.rgb(240, 230, 220))
        pipeline.applyStyle(
            IconStyleSettings(shape = IconShape.SQUARE, themedIcons = true, themeAllIcons = true),
            colors,
        )

        val bitmap = pipeline.forActivity(legacyApp, size)!!

        assertEquals(colors.plateArgb, bitmap.getPixel(size / 2, 6))
        assertEquals(colors.glyphArgb, bitmap.getPixel(size / 2, size / 2))
    }

    @Test
    fun themedIconsAreIgnoredWhereThePlatformHasNoMonochromeLayers() {
        val pipeline = pipeline(sdk = 32)
        pipeline.applyStyle(IconStyleSettings(themedIcons = true, themeAllIcons = true), colors = null)

        val expected = icons.getValue("com.example.adaptive")().toBitmap(size, size)

        assertTrue(pipeline.forActivity(adaptiveApp, size)!!.sameAs(expected))
        assertTrue(pipeline.effectiveStyle.isDefault)
    }

    @Test
    fun theStyleVersionChangesWithEveryChoiceAndWithTheThemeColoursOnlyWhileThemed() {
        val pipeline = pipeline()
        val seen = mutableSetOf(pipeline.styleVersion)
        listOf(
            IconStyleSettings(shape = IconShape.CIRCLE),
            IconStyleSettings(shape = IconShape.CIRCLE, legacyIcons = LegacyIconHandling.AS_IS),
            IconStyleSettings(shape = IconShape.CIRCLE, themedIcons = true),
        ).forEach { style ->
            pipeline.applyStyle(style, colors = null)
            assertTrue("$style", seen.add(pipeline.styleVersion))
        }

        val before = pipeline.styleVersion
        pipeline.applyStyle(
            IconStyleSettings(shape = IconShape.CIRCLE, themedIcons = true),
            ThemedIconColors(Color.RED, Color.BLUE),
        )
        assertNotEquals(before, pipeline.styleVersion)

        pipeline.applyStyle(IconStyleSettings(shape = IconShape.CIRCLE), ThemedIconColors(Color.RED, Color.BLUE))
        val unthemed = pipeline.styleVersion
        pipeline.applyStyle(IconStyleSettings(shape = IconShape.CIRCLE), ThemedIconColors(Color.GREEN, Color.BLUE))
        assertEquals(unthemed, pipeline.styleVersion)
    }

    @Test
    fun anOverridesOwnVersionChangesOnlyThatAppsVersion() {
        val pipeline = pipeline()
        val before = pipeline.versionFor(adaptiveApp) to pipeline.versionFor(legacyApp)

        pipeline.applyStyle(
            IconStyleSettings().withIconOverride(adaptiveApp, IconPackDrawableRef("com.example.pack", "x")),
            colors = null,
        )

        assertNotEquals(before.first, pipeline.versionFor(adaptiveApp))
        assertEquals(before.second, pipeline.versionFor(legacyApp))
    }

    @Test
    fun resolvingOnTheMainThreadIsReportedAndOffItIsNot() {
        val pipeline =
            AppIconPipeline(
                packageManager = packageManager,
                packs = IconPackStore(plainSource),
                activityIconFor = { legacyIcon() },
                guard = { violations += "main" },
                sdkInt = 35,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )

        // Robolectric runs the test on the main looper's thread.
        pipeline.forActivity(legacyApp, size)
        assertEquals(1, violations.size)

        runBlocking { withContext(Dispatchers.Default) { pipeline.forActivity(legacyApp, size) } }
        assertEquals(1, violations.size)
    }

    @Test
    fun anIconThatCannotBeLoadedIsNull() {
        val pipeline =
            AppIconPipeline(
                packageManager = packageManager,
                packs = IconPackStore(plainSource),
                activityIconFor = { error("gone") },
                sdkInt = 35,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
                isMainThread = { false },
            )

        assertNull(pipeline.forActivity(legacyApp, size))
    }

    @Test
    fun aPacksDrawableBeatsTheStyleAndAnOverrideBeatsThePack() {
        val source =
            FakeIconPackSource(
                mapOf(
                    "com.example.pack.one" to
                        FakeIconPackSource.FakePack(
                            label = "One",
                            appFilter = LEGACY_APP_FILTER,
                            drawables = mapOf("legacy_one" to solid(Color.GREEN)),
                        ),
                    "com.example.pack.two" to
                        FakeIconPackSource.FakePack(
                            label = "Two",
                            drawables = mapOf("special" to solid(Color.MAGENTA)),
                        ),
                ),
            )
        val pipeline = pipeline(source)
        val style = IconStyleSettings(shape = IconShape.CIRCLE, iconPack = "com.example.pack.one")
        pipeline.applyStyle(style, colors = null)
        pipeline.packs.watch(setOf("com.example.pack.one", "com.example.pack.two"), "com.example.pack.one")

        assertEquals(Color.GREEN, pipeline.forActivity(legacyApp, size)!!.getPixel(2, 2))
        // An app the pack has nothing for falls through to the shape.
        assertEquals(0, pipeline.forActivity(adaptiveApp, size)!!.alphaAt(1, 1))

        pipeline.applyStyle(
            style.withIconOverride(legacyApp, IconPackDrawableRef("com.example.pack.two", "special")),
            colors = null,
        )
        assertEquals(Color.MAGENTA, pipeline.forActivity(legacyApp, size)!!.getPixel(2, 2))
    }

    @Test
    fun anOverrideWhosePackIsMissingFallsBackAndWorksAgainWhenThePackReturns() {
        val source = FakeIconPackSource()
        val pipeline = pipeline(source)
        val style =
            IconStyleSettings().withIconOverride(
                legacyApp,
                IconPackDrawableRef("com.example.pack.two", "special"),
            )
        pipeline.applyStyle(style, colors = null)
        pipeline.packs.watch(setOf("com.example.pack.two"), null)
        val expected = icons.getValue("com.example.legacy")().toBitmap(size, size)
        assertTrue(pipeline.forActivity(legacyApp, size)!!.sameAs(expected))
        val withoutPack = pipeline.versionFor(legacyApp)

        source.packs =
            mapOf(
                "com.example.pack.two" to
                    FakeIconPackSource.FakePack("Two", drawables = mapOf("special" to solid(Color.MAGENTA))),
            )
        pipeline.packs.onCatalogChanged(null)

        assertNotEquals(withoutPack, pipeline.versionFor(legacyApp))
        assertEquals(Color.MAGENTA, pipeline.forActivity(legacyApp, size)!!.getPixel(2, 2))
        assertNotNull(pipeline.style.overrideFor(legacyApp))
    }

    private fun solid(color: Int): Drawable = android.graphics.drawable.ColorDrawable(color)

    private companion object {
        const val LEGACY_APP_FILTER =
            """<resources><item component="ComponentInfo{com.example.legacy/com.example.legacy.Main}" """ +
                """drawable="legacy_one"/></resources>"""
    }
}
