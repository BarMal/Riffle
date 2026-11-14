package com.riffle.app.launcher

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.widgets.EmptyHomeWidgetViewFactory
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * TEMPORARY diagnostic (remove once the emulator crash is understood). With the flat glass, CI's
 * emulator goes away during the first test that composes the Dock. This class sorts first and draws
 * the same Dock twelve times, each in its own test and in a fixed order. The first run of probes
 * showed every piece of the glass draws once without harm and the emulator went away the second
 * time the same Dock was shown; this run tells a repeat of the same content (dies at the second)
 * from something that builds up (dies later) from something else (dies at the first).
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AaaGlassProbeTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun probe(
        name: String,
        content: @Composable () -> Unit,
    ) {
        Log.i(TAG, "probe start: $name")
        composeRule.setContent { MaterialTheme { Box(Modifier.size(420.dp)) { content() } } }
        composeRule.waitForIdle()
        Thread.sleep(HOLD_MILLIS)
        composeRule.waitForIdle()
        Log.i(TAG, "probe drew: $name")
    }

    @Test
    fun s01_theSameDockWithTwoPinnedApps() = probe("same dock, time 1") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s02_theSameDockWithTwoPinnedApps() = probe("same dock, time 2") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s03_theSameDockWithTwoPinnedApps() = probe("same dock, time 3") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s04_theSameDockWithTwoPinnedApps() = probe("same dock, time 4") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s05_theSameDockWithTwoPinnedApps() = probe("same dock, time 5") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s06_theSameDockWithTwoPinnedApps() = probe("same dock, time 6") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s07_theSameDockWithTwoPinnedApps() = probe("same dock, time 7") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s08_theSameDockWithTwoPinnedApps() = probe("same dock, time 8") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s09_theSameDockWithTwoPinnedApps() = probe("same dock, time 9") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s10_theSameDockWithTwoPinnedApps() = probe("same dock, time 10") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s11_theSameDockWithTwoPinnedApps() = probe("same dock, time 11") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun s12_theSameDockWithTwoPinnedApps() = probe("same dock, time 12") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun t13_glassRoundedThin() =
        probe("glass rounded thin") {
            GlassSurface(Modifier.size(200.dp, 80.dp), weight = GlassWeight.Thin, shape = RoundedCornerShape(28.dp))
        }

    @Test
    fun t14_glassWithSpotlight() =
        probe("glass with spotlight") {
            GlassSurface(Modifier.size(200.dp, 80.dp), shape = RoundedCornerShape(28.dp), spotlight = true)
        }

    @Test
    fun t15_theDocksAppearanceAlone() =
        probe("dock appearance alone") {
            Box(Modifier.size(300.dp, 80.dp).dockSurfaceAppearance(DockModel(capacity = 2, items = emptyList())))
        }

    @Composable
    private fun ProbeDock(items: List<AppShortcutItem>) {
        Dock(
            dock = DockModel(capacity = 2, items = items),
            isEditing = false,
            notificationGroupsByApp = emptyList(),
            appShortcutsByApp = emptyMap(),
            appIconLoader = EmptyAppIconLoader,
            widgetViewFactory = EmptyHomeWidgetViewFactory,
            position = DockPosition.BOTTOM,
            interactions = DockInteractions(onAction = {}),
            dynamicEntries = emptyList(),
        )
    }

    private companion object {
        const val TAG = "GlassProbe"
        const val HOLD_MILLIS = 400L

        fun shortcut(name: String): AppShortcutItem =
            AppShortcutItem(
                id = LauncherItemId(name),
                appIdentity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.$name"),
                        activityName = AppActivityName(".MainActivity"),
                    ),
                label = name,
            )

        val camera = shortcut("camera")
        val mail = shortcut("mail")
    }
}
