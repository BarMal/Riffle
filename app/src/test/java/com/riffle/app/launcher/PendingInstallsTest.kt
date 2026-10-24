package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** S9: an install is kept until it is placed, so one that arrives before the workspaces load is never dropped. */
class PendingInstallsTest {
    private fun app(
        pkg: String,
        profile: AppProfile = AppProfile.personal(),
    ) = InstalledApp(AppIdentity(AppPackageName(pkg), AppActivityName("$pkg.Main"), profile), pkg)

    @Test
    fun anArrivedInstallStaysQueuedUntilItIsDone() {
        val pending = PendingInstalls()
        pending.add(AppPackageName("new.app"), AppProfile.personal())
        val apps = listOf(app("old.app"), app("new.app"))

        // Seen in the app list, but not placed yet (the workspaces have not loaded): asked again, still there.
        assertEquals(listOf(app("new.app")), pending.arrived(apps))
        assertEquals(listOf(app("new.app")), pending.arrived(apps))

        pending.done(pending.arrived(apps))
        assertTrue(pending.arrived(apps).isEmpty())
        assertTrue(pending.isEmpty)
    }

    @Test
    fun anInstallNotYetInTheAppListWaitsAndOtherProfilesAreOtherApps() {
        val pending = PendingInstalls()
        pending.add(AppPackageName("new.app"), AppProfile.work())

        assertTrue(pending.arrived(listOf(app("old.app"))).isEmpty())
        assertTrue("the personal twin is not the queued work app", pending.arrived(listOf(app("new.app"))).isEmpty())
        assertEquals(1, pending.arrived(listOf(app("new.app", AppProfile.work()))).size)
    }
}
