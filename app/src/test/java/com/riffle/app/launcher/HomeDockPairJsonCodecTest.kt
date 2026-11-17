package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppPairItem
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDockPairJsonCodecTest {
    private fun app(name: String) =
        AppShortcutItem(
            id = LauncherItemId("dock:$name:1"),
            appIdentity = AppIdentity(AppPackageName("com.riffle.$name"), AppActivityName(".Main")),
            label = name,
        )

    private fun layoutWith(vararg items: com.riffle.core.domain.launcher.home.LauncherItem) =
        HomeLayoutDefaults.standard().let { it.copy(dock = it.dock.copy(capacity = 6, items = items.toList())) }

    @Test
    fun aPairRoundTripsWithItsOrder() {
        val pair = AppPairItem(LauncherItemId("dock-pair:1"), app("notes"), app("maps"))
        val layout = layoutWith(app("mail"), pair)

        val decoded = decodeHomeLayout(encodeHomeLayout(layout))

        assertEquals(layout.dock.items, decoded.dock.items)
    }

    @Test
    fun aDockStoredBeforePairsDecodesUnchanged() {
        val layout = layoutWith(app("mail"), app("clock"))
        val encoded = encodeHomeLayout(layout)

        assertEquals(layout.dock.items, decodeHomeLayout(encoded).dock.items)
    }

    @Test
    fun anUnknownFutureKindIsSkippedAndTheRestKept() {
        val pair = AppPairItem(LauncherItemId("dock-pair:1"), app("notes"), app("maps"))
        val encoded = JSONObject(encodeHomeLayout(layoutWith(app("mail"), pair)).toString())
        val items = encoded.getJSONObject("dock").getJSONArray("items")
        val future = JSONObject().put("type", "triple").put("id", "x").put("packageName", "p").put("activityName", "a")
        val withFuture = JSONArray().put(items.get(0)).put(future).put(items.get(1))
        encoded.getJSONObject("dock").put("items", withFuture)

        val decoded = decodeHomeLayout(encoded.toString())

        assertEquals(listOf("dock:mail:1", "dock-pair:1"), decoded.dock.items.map { it.id.value })
        assertTrue(decoded.dock.items[1] is AppPairItem)
    }

    @Test
    fun anUninstalledAppLeavesTheRemainingPinnedAppUnderThePairsId() {
        val notes = app("notes")
        val maps = app("maps")
        val pair = AppPairItem(LauncherItemId("dock-pair:1"), notes, maps)
        val layout = layoutWith(app("mail"), pair)

        val oneGone = layout.keepingApps(setOf(notes.appIdentity, app("mail").appIdentity))
        val bothGone = layout.keepingApps(setOf(app("mail").appIdentity))

        assertEquals(listOf("dock:mail:1", "dock-pair:1"), oneGone.dock.items.map { it.id.value })
        assertEquals(notes.appIdentity, (oneGone.dock.items[1] as AppShortcutItem).appIdentity)
        assertEquals(listOf("dock:mail:1"), bothGone.dock.items.map { it.id.value })
    }
}
