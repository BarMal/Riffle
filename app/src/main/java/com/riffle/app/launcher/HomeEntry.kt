package com.riffle.app.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.riffle.app.MainActivity
import com.riffle.core.domain.launcher.entry.shouldHandOverToHome
import java.util.concurrent.atomic.AtomicBoolean

private const val HOME_ENTRY_LOG_TAG = "RiffleHomeEntry"

/**
 * A plain request for Home: `ACTION_MAIN` + `CATEGORY_HOME` and nothing else. Deliberately no component, no package and
 * no other category: the system resolves it to the default Home, and an activity started from exactly such an intent is
 * given the home activity type, so its task is the home task the system's Home gesture and Recents animate. (An
 * explicit component, or a second category, makes the task a standard one.)
 */
internal fun implicitHomeIntent(): Intent =
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The app opened explicitly, as its icon did before the entry activity: for when it is not the default Home. */
internal fun openAppIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Whether a Home hand-over was already made in this process; at most one, so a start that stays odd cannot loop. */
internal class HomeHandOverAttempts {
    private val attempted = AtomicBoolean(false)

    val alreadyAttempted: Boolean get() = attempted.get()

    /** True for the first caller only. */
    fun claim(): Boolean = attempted.compareAndSet(false, true)

    companion object {
        val process = HomeHandOverAttempts()
    }
}

/**
 * Self-heal of a task the system typed as a standard task: [activity] was just created as the root of its task from an
 * intent that is not a Home intent (an old shortcut, `am start` with the component, another app starting it) while the
 * app is the default Home. It ends that task and asks the system for Home instead, so the task that results is a real
 * home task. Returns true when it did (the caller returns from `onCreate` and starts nothing).
 *
 * Never when the intent already carries `CATEGORY_HOME`, when the app is not the default Home, when the activity is not
 * the task root, or when a hand-over was already made in this process. The task is finished before Home is requested,
 * so the request cannot be delivered to the instance that is going away.
 */
internal fun handOverMisrootedTaskToHome(
    activity: Activity,
    isTaskRoot: Boolean,
    intent: Intent?,
    attempts: HomeHandOverAttempts = HomeHandOverAttempts.process,
    homeRoleHeld: () -> Boolean,
): Boolean {
    val handOver =
        shouldHandOverToHome(
            isTaskRoot = isTaskRoot,
            hasHomeCategory = intent?.hasCategory(Intent.CATEGORY_HOME) == true,
            alreadyHandedOver = attempts.alreadyAttempted,
            homeRoleHeld = homeRoleHeld,
        ) && attempts.claim()
    if (!handOver) return false
    Log.i(HOME_ENTRY_LOG_TAG, "Task root started from a non-Home intent while Home: handing over to a Home start")
    activity.finishAndRemoveTask()
    runCatching { activity.startActivity(implicitHomeIntent()) }
        .onFailure { failure -> Log.w(HOME_ENTRY_LOG_TAG, "Home request failed", failure) }
    return true
}
