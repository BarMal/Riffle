package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A settings blob saved by a build that still had the workspace menu, stages, the Terminal preset, the Liquid glass
 * switch and the developer switches must still decode (ADR 0003: stored values that name removed things never crash).
 */
class LauncherSettingsRemovedKeysTest {
    private val removedActions = listOf("OPEN_WORKSPACE_MENU", "SELECT_NEXT_APP_STAGE", "SELECT_PREVIOUS_APP_STAGE")

    private fun blobWithRemovedThings(): JSONObject {
        val json = JSONObject(encodeLauncherSettings(LauncherSettings()))
        val gestures = json.getJSONObject("gestures").getJSONObject("homeGestures")
        gestures.put("ONE_FINGER_UP", removedActions[0])
        gestures.put("TWO_FINGER_LEFT", removedActions[1])
        gestures.put("THREE_FINGER_LEFT", removedActions[2])
        json.getJSONObject("appearance")
            .put("themePreset", "TERMINAL")
            .put("themeColors", JSONObject().put("background", 4278190080L).put("dock", 4278190335L))
        json.getJSONObject("liquidGlass").put("enabled", false)
        json.put("workspaceMenuEnabled", true)
        json.put("developer", JSONObject().put("homeFromPlacedItems", false))
        json.put("somethingNobodyKnows", JSONArray().put("x"))
        return json
    }

    @Test
    fun removedGestureActionsDecodeAsNoActionAndNothingElseMoves() {
        val decoded = decodeLauncherSettings(blobWithRemovedThings().toString())
        val defaults = LauncherSettings()
        val gestures = decoded.gestures.homeGestures

        listOf(
            HomeGesture.ONE_FINGER_UP,
            HomeGesture.TWO_FINGER_LEFT,
            HomeGesture.THREE_FINGER_LEFT,
        ).forEach { gesture ->
            assertNotEquals(gesture.name, "OPEN_WORKSPACE_MENU", gestures.actionFor(gesture).name)
        }
        removedActions.forEach { name -> assertNull(name, LauncherGestureAction.fromStoredName(name)) }
        // The untouched bindings are exactly the defaults.
        val changed = setOf(HomeGesture.ONE_FINGER_UP, HomeGesture.TWO_FINGER_LEFT, HomeGesture.THREE_FINGER_LEFT)
        HomeGesture.entries
            .filterNot { it in changed }
            .forEach { gesture ->
                assertEquals(defaults.gestures.homeGestures.actionFor(gesture), gestures.actionFor(gesture))
            }
    }

    @Test
    fun aRemovedThemePresetTheSwitchAndUnknownKeysAreIgnored() {
        val decoded = decodeLauncherSettings(blobWithRemovedThings().toString())
        val defaults = LauncherSettings()

        assertEquals(defaults.appearance.themeMode, decoded.appearance.themeMode)
        assertEquals(defaults.appearance.themeAccent, decoded.appearance.themeAccent)
        assertEquals(defaults.motion, decoded.motion)
        assertEquals(defaults.privacy, decoded.privacy)
        assertEquals(defaults.rss, decoded.rss)
    }

    @Test
    fun theDecodedSettingsEncodeAgainWithoutTheRemovedNames() {
        val decoded = decodeLauncherSettings(blobWithRemovedThings().toString())
        val encoded = encodeLauncherSettings(decoded)

        assertEquals(decoded, decodeLauncherSettings(encoded))
        removedActions.forEach { name -> assertEquals(name, false, encoded.contains(name)) }
    }
}
