package com.riffle.app.launcher

import android.app.Activity
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The self-heal of a task the system typed as a standard task: when `MainActivity` hands over to a Home start. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeEntryTest {
    private val launcherIntent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName("com.example.app", "Main")
    private val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    private fun activity(): Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    private fun Activity.tryHandOver(
        intent: Intent?,
        roleHeld: Boolean,
        attempts: HomeHandOverAttempts,
        isTaskRoot: Boolean = true,
    ) = handOverMisrootedTaskToHome(this, isTaskRoot, intent, attempts) { roleHeld }

    @Test
    fun theImplicitHomeIntentHasNoComponentNoPackageAndOnlyTheHomeCategory() {
        val intent = implicitHomeIntent()

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertEquals(setOf(Intent.CATEGORY_HOME), intent.categories)
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertNull(intent.data)
        assertNull(intent.type)
    }

    @Test
    fun aHomeIntentStays() {
        val activity = activity()

        assertFalse(activity.tryHandOver(homeIntent, roleHeld = true, attempts = HomeHandOverAttempts()))

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun aLauncherIntentWhileTheAppIsHomeHandsOverOnceAndRemovesTheTask() {
        val activity = activity()
        val attempts = HomeHandOverAttempts()

        assertTrue(activity.tryHandOver(launcherIntent, roleHeld = true, attempts = attempts))

        assertTrue(activity.isFinishing)
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(setOf(Intent.CATEGORY_HOME), started.categories)
        assertNull(started.component)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertTrue(attempts.alreadyAttempted)
    }

    @Test
    fun withoutTheHomeRoleItStays() {
        val activity = activity()
        val attempts = HomeHandOverAttempts()

        assertFalse(activity.tryHandOver(launcherIntent, roleHeld = false, attempts = attempts))

        assertFalse(activity.isFinishing)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertFalse(attempts.alreadyAttempted)
    }

    @Test
    fun theSmokeTestsExplicitComponentWithTheHomeCategoryStaysEvenWhenHome() {
        val smoke = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setClassName("com.example.app", "Main")
        val activity = activity()

        assertFalse(activity.tryHandOver(smoke, roleHeld = true, attempts = HomeHandOverAttempts()))
        assertFalse(activity.isFinishing)
    }

    @Test
    fun aSecondAttemptInTheSameProcessStays() {
        val attempts = HomeHandOverAttempts()
        val first = activity()
        assertTrue(first.tryHandOver(launcherIntent, roleHeld = true, attempts = attempts))
        // Started activities are recorded per application, so the first hand-over's start is taken off first.
        assertEquals(setOf(Intent.CATEGORY_HOME), shadowOf(first).nextStartedActivity.categories)

        val second = activity()
        assertFalse(second.tryHandOver(launcherIntent, roleHeld = true, attempts = attempts))

        assertFalse(second.isFinishing)
        assertNull(shadowOf(second).nextStartedActivity)
    }

    @Test
    fun anActivityThatIsNotTheTaskRootAndAMissingIntentStay() {
        val activity = activity()
        val attempts = HomeHandOverAttempts()

        assertFalse(activity.tryHandOver(launcherIntent, roleHeld = true, attempts = attempts, isTaskRoot = false))
        // A missing intent is not a Home intent: with the role held it is handed over like any other.
        assertTrue(activity.tryHandOver(null, roleHeld = true, attempts = attempts))
    }
}
