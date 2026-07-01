package com.riffle.app.launcher.apps

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidPackageChangeObserverTest {
    @Test
    fun profileChangeFilterRefreshesWhenProfilesBecomeAvailableOrUnavailable() {
        val actions = profileChangeActions()

        assertTrue("android.intent.action.PROFILE_AVAILABLE" in actions)
        assertTrue("android.intent.action.PROFILE_UNAVAILABLE" in actions)
        assertTrue("android.intent.action.MANAGED_PROFILE_ADDED" in actions)
        assertTrue("android.intent.action.MANAGED_PROFILE_REMOVED" in actions)
    }
}
