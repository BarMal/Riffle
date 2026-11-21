package com.riffle.app

import android.app.Application
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The launcher icon's entry point: Home when the app is the default Home, the app otherwise; it always finishes. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherEntryActivityTest {
    private fun holdHomeRole(held: Boolean) {
        val roles =
            shadowOf(ApplicationProvider.getApplicationContext<Application>().getSystemService(RoleManager::class.java))
        roles.addAvailableRole(RoleManager.ROLE_HOME)
        // The shadow keeps its held roles across tests, and refuses to remove one that was never added.
        if (held) {
            roles.addHeldRole(
                RoleManager.ROLE_HOME,
            )
        } else {
            runCatching { roles.removeHeldRole(RoleManager.ROLE_HOME) }
        }
    }

    /** Another app is the default Home: what Home resolves to, which the gateway weighs together with the role. */
    private fun anotherAppIsHome() {
        val other =
            ResolveInfo().apply {
                activityInfo =
                    ActivityInfo().apply {
                        packageName = "com.example.otherhome"
                        name = "com.example.otherhome.Home"
                    }
                priority = 1000
                isDefault = true
            }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
            .addResolveInfoForIntent(homeIntent, other)
    }

    @Test
    fun theActivityCanBeConstructedBeforeItHasAContext() {
        assertNotNull(LauncherEntryActivity::class.java.getDeclaredConstructor().newInstance())
    }

    @Test
    fun asTheDefaultHomeItAsksTheSystemForHomeWithAnImplicitIntentAndFinishes() {
        holdHomeRole(held = true)

        val activity = Robolectric.buildActivity(LauncherEntryActivity::class.java).create().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertEquals(setOf(Intent.CATEGORY_HOME), started.categories)
        assertNull("no component: the system resolves the default Home", started.component)
        assertNull("no package either", started.`package`)
        assertNull(started.data)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull("only one start", shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }

    @Test
    fun beforeItIsTheDefaultHomeItOpensTheAppExplicitlyAndFinishes() {
        holdHomeRole(held = false)
        anotherAppIsHome()

        val activity = Robolectric.buildActivity(LauncherEntryActivity::class.java).create().get()

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(activity.packageName, started.component?.packageName)
        assertEquals(Intent.ACTION_MAIN, started.action)
        assertFalse(
            "not a Home intent, so the app is not asked to open Home",
            started.hasCategory(Intent.CATEGORY_HOME),
        )
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, started.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertNull("only one start", shadowOf(activity).nextStartedActivity)
        assertTrue(activity.isFinishing)
    }
}
