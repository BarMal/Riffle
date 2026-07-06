package com.riffle.app.launcher.apps

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidPackageChangeObserverTest {
    @Test
    fun profileChangeActionsCoverManagedAndGenericProfileLifecycleBroadcasts() {
        assertEquals(expectedProfileChangeActions, profileChangeActions())
    }

    @Test
    fun profileChangeFilterActionsContainEachActionOnce() {
        val filterActions = profileChangeFilterActions()

        assertEquals(expectedProfileChangeActions.size, filterActions.size)
        expectedProfileChangeActions.forEach { action ->
            assertEquals(1, filterActions.count { it == action })
        }
    }

    private companion object {
        val expectedProfileChangeActions =
            setOf(
                Intent.ACTION_MANAGED_PROFILE_ADDED,
                Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_REMOVED,
                Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
                Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
                Intent.ACTION_PROFILE_ACCESSIBLE,
                Intent.ACTION_PROFILE_ADDED,
                Intent.ACTION_PROFILE_AVAILABLE,
                Intent.ACTION_PROFILE_INACCESSIBLE,
                Intent.ACTION_PROFILE_REMOVED,
                Intent.ACTION_PROFILE_UNAVAILABLE,
            )
    }
}
