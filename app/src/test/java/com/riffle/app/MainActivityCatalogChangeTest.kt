package com.riffle.app

import com.riffle.app.launcher.apps.AppCatalogChange
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityCatalogChangeTest {
    @Test
    fun catalogRefreshInvalidatesPreviewsBeforeRefreshingAppsAndWidgetProviders() {
        val events = mutableListOf<String>()

        handleAppCatalogChange(
            change = AppCatalogChange.Refresh,
            invalidateWidgetPreviews = { events += "invalidate previews" },
            refreshInstalledApps = { events += "refresh apps" },
            refreshWidgetProviders = { events += "refresh widgets" },
            onConfirmedPackageRemoved = { _, _ -> events += "remove package" },
        )

        assertEquals(
            listOf("invalidate previews", "refresh apps", "refresh widgets"),
            events,
        )
    }

    @Test
    fun packageRemovalInvalidatesPreviewsAndRefreshesProvidersAfterPruningThePackage() {
        val events = mutableListOf<String>()
        val packageName = AppPackageName("com.example.clock")
        val profile = AppProfile.work()

        handleAppCatalogChange(
            change = AppCatalogChange.PackageRemoved(packageName, profile),
            invalidateWidgetPreviews = { events += "invalidate previews" },
            refreshInstalledApps = { events += "refresh apps" },
            refreshWidgetProviders = { events += "refresh widgets" },
            onConfirmedPackageRemoved = { removedPackage, removedProfile ->
                events += "remove ${removedProfile.id.value}:${removedPackage.value}"
            },
        )

        assertEquals(
            listOf(
                "invalidate previews",
                "remove work:com.example.clock",
                "refresh widgets",
            ),
            events,
        )
    }

    @Test
    fun anInstallGoesToTheInstallHandlerNotToARemovalOrAPlainRefresh() {
        val events = mutableListOf<String>()
        val packageName = AppPackageName("com.example.notes")

        handleAppCatalogChange(
            change = AppCatalogChange.PackageAdded(packageName, AppProfile.personal()),
            invalidateWidgetPreviews = { events += "invalidate previews" },
            refreshInstalledApps = { events += "refresh apps" },
            refreshWidgetProviders = { events += "refresh widgets" },
            onConfirmedPackageRemoved = { _, _ -> events += "remove package" },
            onPackageInstalled = { installed, _ -> events += "installed ${installed.value}" },
        )

        assertEquals(
            listOf("invalidate previews", "installed com.example.notes", "refresh widgets"),
            events,
        )
    }

    @Test
    fun withoutAnInstallHandlerAnInstallIsAPlainRefresh() {
        val events = mutableListOf<String>()

        handleAppCatalogChange(
            change = AppCatalogChange.PackageAdded(AppPackageName("com.example.notes"), AppProfile.personal()),
            invalidateWidgetPreviews = { events += "invalidate previews" },
            refreshInstalledApps = { events += "refresh apps" },
            refreshWidgetProviders = { events += "refresh widgets" },
            onConfirmedPackageRemoved = { _, _ -> events += "remove package" },
        )

        assertEquals(listOf("invalidate previews", "refresh apps", "refresh widgets"), events)
    }
}
