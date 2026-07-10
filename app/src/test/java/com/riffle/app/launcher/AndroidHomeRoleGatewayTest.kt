package com.riffle.app.launcher

import com.riffle.core.domain.launcher.HomeRoleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidHomeRoleGatewayTest {
    @Test
    fun reportsDefaultHomeWhenHomeRoleIsHeld() {
        assertEquals(
            HomeRoleStatus.DEFAULT_HOME,
            homeRoleStatusFromRoleManager(
                isHomeRoleAvailable = true,
                isHomeRoleHeld = true,
            ),
        )
    }

    @Test
    fun reportsNotDefaultHomeWhenHomeRoleIsAvailableButNotHeld() {
        assertEquals(
            HomeRoleStatus.NOT_DEFAULT_HOME,
            homeRoleStatusFromRoleManager(
                isHomeRoleAvailable = true,
                isHomeRoleHeld = false,
            ),
        )
    }

    @Test
    fun fallsBackWhenHomeRoleIsUnavailable() {
        assertNull(
            homeRoleStatusFromRoleManager(
                isHomeRoleAvailable = false,
                isHomeRoleHeld = false,
            ),
        )
    }

    @Test
    fun reportsDefaultHomeFromResolvedLauncherPackage() {
        assertEquals(
            HomeRoleStatus.DEFAULT_HOME,
            homeRoleStatusFromResolvedDefaultHome(
                appPackageName = "com.riffle",
                resolvedDefaultHomePackageName = "com.riffle",
            ),
        )
    }

    @Test
    fun debugBuildAcceptsReleasePackageAsResolvedDefaultHome() {
        assertEquals(
            HomeRoleStatus.DEFAULT_HOME,
            homeRoleStatusFromResolvedDefaultHome(
                appPackageName = "com.riffle.debug",
                resolvedDefaultHomePackageName = "com.riffle",
            ),
        )
    }

    @Test
    fun releaseBuildDoesNotAcceptDebugPackageAsResolvedDefaultHome() {
        assertEquals(
            HomeRoleStatus.NOT_DEFAULT_HOME,
            homeRoleStatusFromResolvedDefaultHome(
                appPackageName = "com.riffle",
                resolvedDefaultHomePackageName = "com.riffle.debug",
            ),
        )
    }

    @Test
    fun reportsNotDefaultHomeFromDifferentResolvedLauncherPackage() {
        assertEquals(
            HomeRoleStatus.NOT_DEFAULT_HOME,
            homeRoleStatusFromResolvedDefaultHome(
                appPackageName = "com.riffle",
                resolvedDefaultHomePackageName = "com.android.launcher",
            ),
        )
    }

    @Test
    fun reportsUnknownWhenNoDefaultHomeCanBeResolved() {
        assertEquals(
            HomeRoleStatus.UNKNOWN,
            homeRoleStatusFromResolvedDefaultHome(
                appPackageName = "com.riffle",
                resolvedDefaultHomePackageName = null,
            ),
        )
    }

    @Test
    fun prefersResolvedDefaultHomeWhenRoleManagerReportsNotHeld() {
        assertEquals(
            HomeRoleStatus.DEFAULT_HOME,
            homeRoleStatus(
                roleManagerStatus = HomeRoleStatus.NOT_DEFAULT_HOME,
                resolvedDefaultHomeStatus = HomeRoleStatus.DEFAULT_HOME,
            ),
        )
    }

    @Test
    fun fallsBackToResolvedHomeStatusWhenRoleManagerIsUnavailable() {
        assertEquals(
            HomeRoleStatus.NOT_DEFAULT_HOME,
            homeRoleStatus(
                roleManagerStatus = null,
                resolvedDefaultHomeStatus = HomeRoleStatus.NOT_DEFAULT_HOME,
            ),
        )
    }
}
