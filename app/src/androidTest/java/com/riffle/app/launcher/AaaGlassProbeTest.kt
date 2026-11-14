package com.riffle.app.launcher

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
 * the same Dock with two pinned apps three times under each of four settings, in a fixed order:
 * neither edge lights nor spotlight, spotlight only, edge lights only, both. A Dock with pinned apps
 * killed the emulator as the very first test of a run, so the first group the run dies in names the
 * part responsible (the first group: neither of them).
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
    fun u11_noEdgeLightsNoSpotlight() = switched(edgeLights = false, spotlight = false, "noEdgeLightsNoSpotlight 1")

    @Test
    fun u12_noEdgeLightsNoSpotlight() = switched(edgeLights = false, spotlight = false, "noEdgeLightsNoSpotlight 2")

    @Test
    fun u13_noEdgeLightsNoSpotlight() = switched(edgeLights = false, spotlight = false, "noEdgeLightsNoSpotlight 3")

    @Test
    fun u21_spotlightOnly() = switched(edgeLights = false, spotlight = true, "spotlightOnly 1")

    @Test
    fun u22_spotlightOnly() = switched(edgeLights = false, spotlight = true, "spotlightOnly 2")

    @Test
    fun u23_spotlightOnly() = switched(edgeLights = false, spotlight = true, "spotlightOnly 3")

    @Test
    fun u31_edgeLightsOnly() = switched(edgeLights = true, spotlight = false, "edgeLightsOnly 1")

    @Test
    fun u32_edgeLightsOnly() = switched(edgeLights = true, spotlight = false, "edgeLightsOnly 2")

    @Test
    fun u33_edgeLightsOnly() = switched(edgeLights = true, spotlight = false, "edgeLightsOnly 3")

    @Test
    fun u41_both() = switched(edgeLights = true, spotlight = true, "both 1")

    @Test
    fun u42_both() = switched(edgeLights = true, spotlight = true, "both 2")

    @Test
    fun u43_both() = switched(edgeLights = true, spotlight = true, "both 3")

    private fun switched(
        edgeLights: Boolean,
        spotlight: Boolean,
        name: String,
    ) {
        GlassDiagnostics.edgeLights = edgeLights
        GlassDiagnostics.spotlight = spotlight
        try {
            probe(name) { ProbeDock(items = listOf(camera, mail)) }
        } finally {
            GlassDiagnostics.edgeLights = true
            GlassDiagnostics.spotlight = true
        }
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
