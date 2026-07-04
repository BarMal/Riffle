package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchGridResultsTest {
    @Test
    fun searchGridAppsAreAlphabetical() {
        assertEquals(
            listOf("Calendar", "Camera", "Maps"),
            searchGridApps(
                listOf(
                    app("Maps"),
                    app("Camera"),
                    app("Calendar"),
                ),
            ).map { app -> app.label },
        )
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase()}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}
