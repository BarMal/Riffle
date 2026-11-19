package com.riffle.app.launcher.widgets

import com.riffle.app.launcher.WidgetAddTarget
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.home.GridCell
import com.riffle.core.domain.launcher.home.GridSpan
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetAddTransactionStoreTest {
    @Test
    fun serializesPendingConfigurationTransactionForFreshProcessRecovery() {
        val transaction =
            PendingWidgetAddTransaction(
                hostedWidgetId = HostedWidgetId(42),
                provider =
                    WidgetProviderIdentity(
                        packageName = AppPackageName("com.example.weather"),
                        className = WidgetProviderClassName(".WeatherWidget"),
                        profile = AppProfile.private(),
                    ),
                label = "Weather",
                preferredSpan = GridSpan(columns = 3, rows = 2),
                target = WidgetAddTarget.HOME,
                targetPageId = LauncherPageId("home-2"),
                targetCell = GridCell(column = 2, row = 3),
                dockIndex = 1,
                step = PendingWidgetAddStep.CONFIGURATION,
                createdAtEpochMillis = 1234L,
            )

        assertEquals(transaction, decodeWidgetAddTransaction(encodeWidgetAddTransaction(transaction)))
    }

    @Test
    fun dropsMalformedOrObsoletePersistedTransactions() {
        assertNull(decodeWidgetAddTransaction("not-json"))
        val obsoleteTransaction =
            listOf(
                "{\"version\":99,\"hostedWidgetId\":42,\"packageName\":\"com.example\",",
                "\"className\":\".Widget\",\"profileId\":\"personal\",\"profileType\":\"PERSONAL\",",
                "\"label\":\"Widget\",\"columns\":1,\"rows\":1,\"target\":\"HOME\",",
                "\"step\":\"PERMISSION\",\"createdAtEpochMillis\":0}",
            ).joinToString(separator = "")
        assertNull(
            decodeWidgetAddTransaction(
                obsoleteTransaction,
            ),
        )
    }

    @Test
    fun recoversHostedIdFromObsoleteTransactionForCleanup() {
        val obsoleteTransaction =
            listOf(
                "{\"version\":99,\"hostedWidgetId\":42,\"packageName\":\"com.example\",",
                "\"className\":\".Widget\",\"profileId\":\"personal\",\"profileType\":\"PERSONAL\",",
                "\"label\":\"Widget\",\"columns\":1,\"rows\":1,\"target\":\"HOME\",",
                "\"step\":\"PERMISSION\",\"createdAtEpochMillis\":0}",
            ).joinToString(separator = "")

        assertEquals(HostedWidgetId(42), decodeInvalidWidgetAddTransactionHostedId(obsoleteTransaction))
        assertNull(decodeInvalidWidgetAddTransactionHostedId("not-json"))
    }

    @Test
    fun aPlaceholderSetUpKeepsItsItemAndMemberThroughTheProcessDying() {
        val transaction =
            PendingWidgetAddTransaction(
                hostedWidgetId = HostedWidgetId(7),
                provider =
                    WidgetProviderIdentity(AppPackageName("com.example.weather"), WidgetProviderClassName(".W")),
                label = "Weather",
                preferredSpan = GridSpan(columns = 2, rows = 2),
                target = WidgetAddTarget.HOME_PLACEHOLDER,
                targetItemId = LauncherItemId("widget:3"),
                targetMemberIndex = 2,
                step = PendingWidgetAddStep.CONFIGURATION,
                createdAtEpochMillis = 55L,
            )

        assertEquals(transaction, decodeWidgetAddTransaction(encodeWidgetAddTransaction(transaction)))
    }

    @Test
    fun aTransactionWrittenBeforePlaceholderSetUpsStillDecodes() {
        val version3 =
            listOf(
                "{\"version\":3,\"hostedWidgetId\":42,\"packageName\":\"com.example\",",
                "\"className\":\".Widget\",\"profileId\":\"personal\",\"profileType\":\"PERSONAL\",",
                "\"label\":\"Widget\",\"columns\":1,\"rows\":1,\"target\":\"HOME_STACK\",",
                "\"targetStackId\":\"widget:9\",\"step\":\"PERMISSION\",\"createdAtEpochMillis\":0}",
            ).joinToString(separator = "")

        val decoded = decodeWidgetAddTransaction(version3)

        assertEquals(WidgetAddTarget.HOME_STACK, decoded?.target)
        assertEquals(LauncherItemId("widget:9"), decoded?.targetStackId)
        assertNull(decoded?.targetItemId)
        assertEquals(0, decoded?.targetMemberIndex)
    }

    @Test
    fun aPlaceholderSetUpWithNoItemIsDiscarded() {
        val noItem =
            listOf(
                "{\"version\":4,\"hostedWidgetId\":42,\"packageName\":\"com.example\",",
                "\"className\":\".Widget\",\"profileId\":\"personal\",\"profileType\":\"PERSONAL\",",
                "\"label\":\"Widget\",\"columns\":1,\"rows\":1,\"target\":\"HOME_PLACEHOLDER\",",
                "\"step\":\"PERMISSION\",\"createdAtEpochMillis\":0}",
            ).joinToString(separator = "")

        assertNull(decodeWidgetAddTransaction(noItem))
        assertEquals(HostedWidgetId(42), decodeInvalidWidgetAddTransactionHostedId(noItem))
    }
}
