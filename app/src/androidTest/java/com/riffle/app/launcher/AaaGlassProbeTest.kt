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
 * a Dock with two pinned apps under five settings of its edge lights, twice each, in a fixed order:
 * nothing lit, the hairline only, a solid border only, the gradient border only, everything. The
 * previous probe showed the spotlight is harmless and the edge lights on a Dock with pinned apps
 * kill the emulator; the first group this run dies in names the drawing call.
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
    fun v11_nothingLit() = switched(false, GlassDiagnostics.BORDER_NONE, "nothingLit 1")

    @Test
    fun v12_nothingLit() = switched(false, GlassDiagnostics.BORDER_NONE, "nothingLit 2")

    @Test
    fun v21_hairlineOnly() = switched(true, GlassDiagnostics.BORDER_NONE, "hairlineOnly 1")

    @Test
    fun v22_hairlineOnly() = switched(true, GlassDiagnostics.BORDER_NONE, "hairlineOnly 2")

    @Test
    fun v31_solidBorderOnly() = switched(false, GlassDiagnostics.BORDER_SOLID, "solidBorderOnly 1")

    @Test
    fun v32_solidBorderOnly() = switched(false, GlassDiagnostics.BORDER_SOLID, "solidBorderOnly 2")

    @Test
    fun v41_gradientBorderOnly() = switched(false, GlassDiagnostics.BORDER_GRADIENT, "gradientBorderOnly 1")

    @Test
    fun v42_gradientBorderOnly() = switched(false, GlassDiagnostics.BORDER_GRADIENT, "gradientBorderOnly 2")

    @Test
    fun v51_everything() = switched(true, GlassDiagnostics.BORDER_GRADIENT, "everything 1")

    @Test
    fun v52_everything() = switched(true, GlassDiagnostics.BORDER_GRADIENT, "everything 2")

    private fun switched(
        specular: Boolean,
        border: Int,
        name: String,
    ) {
        GlassDiagnostics.spotlight = false
        GlassDiagnostics.specular = specular
        GlassDiagnostics.border = border
        try {
            probe(name) { ProbeDock(items = listOf(camera, mail)) }
        } finally {
            GlassDiagnostics.spotlight = true
            GlassDiagnostics.specular = true
            GlassDiagnostics.border = GlassDiagnostics.BORDER_GRADIENT
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
