package com.riffle.app.launcher.icons

import android.app.Application
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.ByteBoundedCache
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.apps.MAX_CACHED_ICON_BYTES
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Icon resolution never runs on the main thread from the Compose surfaces, and the icon cache is bounded in bytes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconLoaderThreadingAndBoundsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val packageManager = ApplicationProvider.getApplicationContext<Application>().packageManager
    private val violations = mutableListOf<String>()

    private fun loader(density: Float = 4f): PackageManagerAppIconLoader {
        val pipeline =
            AppIconPipeline(
                packageManager = packageManager,
                packs = IconPackStore(FakeIconPackSource()),
                activityIconFor = { legacyIcon() },
                guard = { violations += "resolved on the main thread" },
                sdkInt = 35,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            )
        return PackageManagerAppIconLoader(
            packageManager,
            activityIconFor = { legacyIcon() },
            displayDensityFor = { density },
            pipeline = pipeline,
        )
    }

    @Test
    fun theComposeIconLoadsOffTheMainThreadAndAStyleChangeDrawsItAgainWithoutResolvingOnIt() {
        val loader = loader()
        val app = identity("com.example.notes")
        composeRule.setContent {
            LauncherAppIcon(identity = app, label = "Notes", iconLoader = loader, modifier = Modifier.size(48.dp))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) { loader.cachedIconFor(app) != null }
        val firstVersion = loader.iconVersionFor(app)

        loader.applyStyle(IconStyleSettings(shape = IconShape.CIRCLE), ThemedIconColors(Color.BLACK, Color.WHITE))
        composeRule.waitUntil(timeoutMillis = 5_000) { loader.cachedIconFor(app) != null }
        composeRule.waitForIdle()

        assertTrue(loader.iconVersionFor(app) != firstVersion)
        assertTrue(violations.toString(), violations.isEmpty())
    }

    @Test
    fun theIconCacheNeverHoldsMoreThanItsByteBound() {
        val loader = loader(density = 4f)

        // 320 px icons are 409,600 bytes each: far more of them than fit in the bound are loaded.
        val count = MAX_CACHED_ICON_BYTES / (320 * 320 * 4) * 3
        repeat(count) { index -> loader.iconFor(identity("com.example.app$index")) }

        assertTrue("${loader.cachedIconBytes}", loader.cachedIconBytes <= MAX_CACHED_ICON_BYTES)
        assertNotNull(loader.cachedIconFor(identity("com.example.app${count - 1}")))
        assertNull(loader.cachedIconFor(identity("com.example.app0")))
    }

    @Test
    fun aStyleChangeMakesTheOldEntriesUnreachableWithoutClearingAnything() {
        val loader = loader(density = 1f)
        val app = identity("com.example.notes")
        loader.iconFor(app)
        assertNotNull(loader.cachedIconFor(app))
        val bytes = loader.cachedIconBytes

        loader.applyStyle(IconStyleSettings(shape = IconShape.SQUIRCLE), ThemedIconColors(Color.BLACK, Color.WHITE))

        assertNull(loader.cachedIconFor(app))
        assertEquals(bytes, loader.cachedIconBytes)
    }

    @Test
    fun theByteBoundedCacheEvictsLeastRecentlyUsedFirstAndRejectsWhatCannotFit() {
        val cache = ByteBoundedCache<String, ByteArray>(maxBytes = 100) { it.size }

        cache["a"] = ByteArray(40)
        cache["b"] = ByteArray(40)
        assertNotNull(cache["a"])
        cache["c"] = ByteArray(40)

        assertNull(cache["b"])
        assertEquals(80, cache.bytes)
        cache["huge"] = ByteArray(500)
        assertNull(cache["huge"])
        assertTrue(cache.bytes <= 100)
    }
}
