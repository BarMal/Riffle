package com.riffle.app.screenshots

import android.app.Application
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.app.launcher.icons.FakeIconPackSource
import com.riffle.app.launcher.icons.IconPackStore
import com.riffle.app.launcher.icons.ThemedIconColors
import com.riffle.app.launcher.icons.identity
import com.riffle.app.launcher.icons.legacyIcon
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Icon resolution never runs on the main thread from the Compose surfaces, including after a style change. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconLoaderThreadingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val packageManager = ApplicationProvider.getApplicationContext<Application>().packageManager
    private val violations = mutableListOf<String>()

    private fun loader(): PackageManagerAppIconLoader {
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
            displayDensityFor = { 4f },
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
}
