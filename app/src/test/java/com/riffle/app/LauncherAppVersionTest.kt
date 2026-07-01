package com.riffle.app

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherAppVersionTest {
    @Test
    fun formatsVersionNameAndCode() {
        assertEquals("0.1.0-alpha109 (109)", launcherVersionLabel(versionName = "0.1.0-alpha109", versionCode = 109))
    }

    @Test
    fun fallsBackToVersionCodeWhenNameIsBlank() {
        assertEquals("109", launcherVersionLabel(versionName = "", versionCode = 109))
    }

    @Test
    fun formatsBuildIdentityWithPackageAndBuildType() {
        assertEquals(
            "0.1.0-alpha109 (109) / com.riffle.app.debug / debug",
            launcherBuildIdentityLabel(
                appVersionLabel = "0.1.0-alpha109 (109)",
                packageName = "com.riffle.app.debug",
                buildType = "debug",
            ),
        )
    }
}
