package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.home.HomeLayoutKey
import com.riffle.core.domain.launcher.home.HomeLayoutSet
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherViewMode
import com.riffle.core.domain.launcher.home.dockFor
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One dock per device class, shared by every mode (#1205): it survives a save as its own "docks"
 * entry, and a set written when every mode kept its own dock decodes to the showing mode's dock
 * without losing any pin.
 */
class HomeLayoutSetSharedDockJsonCodecTest {
    @Test
    fun roundTripsHomeLayoutSet() {
        val standard = HomeLayoutDefaults.standard()
        val cards =
            HomeLayoutDefaults.standard().copy(
                viewMode = LauncherViewMode.CARD_INTERFACE,
                dock = HomeLayoutDefaults.standard().dock.copy(capacity = 4),
            )
        val layoutSet =
            HomeLayoutSet(
                activeKey = cardsKey,
                layouts = mapOf(standardKey to standard, cardsKey to cards),
                preferredModesByDeviceClass = mapOf(HomeLayoutDeviceClass.PHONE to LauncherViewMode.CARD_INTERFACE),
            )

        val decodedLayoutSet = decodeHomeLayoutSet(encodeHomeLayoutSet(layoutSet))

        assertEquals(cardsKey, decodedLayoutSet.activeKey)
        // The active mode's dock is the dock every mode shows; this used to assert that Standard
        // kept a capacity-5 dock of its own, a premise #1205 removed.
        assertEquals(4, decodedLayoutSet.activeLayout.dock.capacity)
        assertEquals(4, decodedLayoutSet.layoutFor(standardKey).dock.capacity)
        assertEquals(layoutSet.docks, decodedLayoutSet.docks)
        assertEquals(
            mapOf(HomeLayoutDeviceClass.PHONE to LauncherViewMode.CARD_INTERFACE),
            decodedLayoutSet.preferredModesByDeviceClass,
        )
    }

    @Test
    fun roundTripsOneDockPerDeviceClass() {
        val phoneDock = DockModel(capacity = 4, items = listOf(app("phone")), position = DockPosition.LEFT)
        val tabletDock = DockModel(capacity = 7, items = listOf(app("mail")), iconSizeDp = 52)
        val layoutSet =
            HomeLayoutSet.standard()
                .let { set -> set.withActiveLayout(set.activeLayout.copy(dock = phoneDock)) }
                .selectDeviceClass(HomeLayoutDeviceClass.TABLET)
                .let { set -> set.withActiveLayout(set.activeLayout.copy(dock = tabletDock)) }
                .selectMode(LauncherViewMode.CARD_INTERFACE)

        val encoded = encodeHomeLayoutSet(layoutSet)
        val decoded = decodeHomeLayoutSet(encoded)

        assertTrue(JSONObject(encoded).has("docks"))
        assertEquals(phoneDock, decoded.dockFor(HomeLayoutDeviceClass.PHONE))
        assertEquals(tabletDock, decoded.dockFor(HomeLayoutDeviceClass.TABLET))
        assertEquals(tabletDock, decoded.activeLayout.dock)
        assertEquals(
            tabletDock,
            decoded.layoutFor(HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER, HomeLayoutDeviceClass.TABLET)).dock,
        )
    }

    @Test
    fun decodesThePerModeDockFormatToTheShowingModesDock() {
        val decoded = decodeHomeLayoutSet(perModeDockJson(active = libraryKey))

        assertEquals(libraryDock, decoded.activeLayout.dock)
        assertEquals(libraryDock, decoded.layoutFor(standardKey).dock)
        assertEquals(libraryDock, decoded.layoutFor(cardsKey).dock)
    }

    @Test
    fun decodingThePerModeDockFormatDropsNoPin() {
        val decoded = decodeHomeLayoutSet(perModeDockJson(active = libraryKey))

        // Standard's own pin goes to Standard's home page; the Cards-only one to the showing mode's.
        assertTrue(app("standard-only").appIdentity in decoded.layoutFor(standardKey).homeApps())
        assertTrue(app("cards-only").appIdentity in decoded.layoutFor(libraryKey).homeApps())
    }

    @Test
    fun aMigratedSetSavesAndReloadsUnchanged() {
        val migrated = decodeHomeLayoutSet(perModeDockJson(active = cardsKey))

        val reloaded = decodeHomeLayoutSet(encodeHomeLayoutSet(migrated))

        assertEquals(migrated.docks, reloaded.docks)
        listOf(standardKey, libraryKey, cardsKey).forEach { key ->
            assertEquals(migrated.layoutFor(key), reloaded.layoutFor(key))
        }
    }

    /** A set as it was stored before #1205: no "docks", and each layout carrying a dock of its own. */
    private fun perModeDockJson(active: HomeLayoutKey): String {
        val docks = mapOf(standardKey to standardDock, libraryKey to libraryDock, cardsKey to cardsDock)
        val layoutSet =
            HomeLayoutSet(
                activeKey = active,
                layouts =
                    docks.mapValues { (key, _) -> HomeLayoutDefaults.standard().copy(viewMode = key.viewMode) },
            )
        val json = JSONObject(encodeHomeLayoutSet(layoutSet))
        json.remove("docks")
        val layouts = json.getJSONArray("layouts")
        (0 until layouts.length()).forEach { index ->
            val entry = layouts.getJSONObject(index)
            val mode = LauncherViewMode.valueOf(entry.getJSONObject("key").getString("viewMode"))
            entry.getJSONObject("layout").put("dock", encodeDock(docks.getValue(HomeLayoutKey(mode))))
        }
        return json.toString()
    }

    private fun HomeLayout.homeApps(): List<AppIdentity> =
        pages.flatMap { page -> page.items }.filterIsInstance<AppShortcutItem>().map { item -> item.appIdentity }

    private companion object {
        val standardKey = HomeLayoutKey(LauncherViewMode.STANDARD_APP_DRAWER)
        val libraryKey = HomeLayoutKey(LauncherViewMode.HOME_SCREEN_LIBRARY)
        val cardsKey = HomeLayoutKey(LauncherViewMode.CARD_INTERFACE)

        val standardDock = DockModel(capacity = 4, items = listOf(app("shared"), app("standard-only")))
        val libraryDock =
            DockModel(capacity = 5, iconSizeDp = 40, items = listOf(app("shared"), app("library-only")))
        val cardsDock =
            DockModel(capacity = 6, position = DockPosition.RIGHT, items = listOf(app("shared"), app("cards-only")))

        fun app(name: String): AppShortcutItem =
            AppShortcutItem(
                id = LauncherItemId(name),
                appIdentity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.$name"),
                        activityName = AppActivityName(".MainActivity"),
                    ),
                label = name,
            )
    }
}
