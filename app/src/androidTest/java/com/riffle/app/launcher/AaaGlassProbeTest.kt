package com.riffle.app.launcher

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
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
 * the glass one piece at a time, each in its own test and in a fixed order, so the first probe the
 * run dies in names the piece that kills the emulator. Each probe holds its frame on screen briefly:
 * the emulator dies while drawing, and a probe that only composed would pass.
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
    fun p01_aPlainBox() = probe("plain box") { Box(Modifier.size(200.dp, 80.dp)) }

    @Test
    fun p02_glassRectangle() = probe("glass rectangle") { GlassSurface(Modifier.size(200.dp, 80.dp)) }

    @Test
    fun p03_glassRoundedThin() =
        probe("glass rounded thin") {
            GlassSurface(Modifier.size(200.dp, 80.dp), weight = GlassWeight.Thin, shape = RoundedCornerShape(28.dp))
        }

    @Test
    fun p04_glassWithEdgeTint() =
        probe("glass with edge tint") {
            GlassSurface(Modifier.size(200.dp, 80.dp), shape = RoundedCornerShape(28.dp), edgeTint = Color.Green)
        }

    @Test
    fun p05_glassWithSpotlight() =
        probe("glass with spotlight") {
            GlassSurface(Modifier.size(200.dp, 80.dp), shape = RoundedCornerShape(28.dp), spotlight = true)
        }

    @Test
    fun p06_theDocksAppearanceAlone() =
        probe("dock appearance alone") {
            Box(Modifier.size(300.dp, 80.dp).dockSurfaceAppearance(DockModel(capacity = 2, items = emptyList())))
        }

    @Test
    fun p07_anEmptyDock() = probe("empty dock") { ProbeDock(items = emptyList()) }

    @Test
    fun p08_aDockWithTwoPinnedApps() = probe("dock with two pinned apps") { ProbeDock(items = listOf(camera, mail)) }

    @Test
    fun p09_aTapOnAPinnedApp() {
        probe("dock, then a tap on a pinned app") { ProbeDock(items = listOf(camera, mail)) }
        val pinned =
            SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)
                .and(hasTestTag(dockItemTestTag(mail.id)).or(hasAnyAncestor(hasTestTag(dockItemTestTag(mail.id)))))
        composeRule.onNode(pinned).performClick()
        composeRule.waitForIdle()
        Thread.sleep(HOLD_MILLIS)
        Log.i(TAG, "probe drew: after the tap")
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
        const val HOLD_MILLIS = 1200L

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
