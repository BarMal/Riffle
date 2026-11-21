package com.riffle.app.launcher.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.ViewTreeObserver
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateLog
import com.riffle.core.domain.launcher.diagnostics.PackageStamp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** The intent as text for the diagnostics: action, categories, flags and component (extras and data are not read). */
internal fun intentDiagnosticsText(
    action: String?,
    categories: Set<String>?,
    flags: Int,
    component: String?,
): String =
    "act=${action ?: "none"} cat=[${categories.orEmpty().sorted().joinToString(",")}] " +
        "flg=0x${Integer.toHexString(flags)} cmp=${component ?: "none"}"

/**
 * The platform side of the start diagnostics: one recorder per process on its own thread, started once per
 * process from the first activity's `onCreate`. No receivers, no background starts and no permissions. The milestones
 * ([StartupTimeline]) are written with the first frame, and once more if a later one ([onMilestone]) is reached.
 */
internal object AfterUpdateProcess {
    private val started = AtomicBoolean(false)

    @Volatile
    private var recorder: AfterUpdateRecorder? = null

    /** Whether the first frame (and with it the milestones so far) has been handed to the recorder; main thread. */
    private var frameReported = false

    /** Records this process's start when it is the first; call from `onCreate` after the window exists. */
    fun start(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        val context = activity.applicationContext
        val instance = recorderFor(context)
        val intent = activity.intent
        val taskId = activity.taskId
        instance.onStart(
            atMillis = System.currentTimeMillis(),
            intentText =
                intentDiagnosticsText(
                    action = intent?.action,
                    categories = intent?.categories,
                    flags = intent?.flags ?: 0,
                    component = intent?.component?.flattenToShortString(),
                ),
            isTaskRoot = activity.isTaskRoot,
            cold = AfterUpdateLog.isColdStart(StartupTimeline.millisOf(StartMilestones.ON_CREATE_ENTERED)),
            sdkInt = Build.VERSION.SDK_INT,
            probe = { probe(context, taskId) },
        )
        reportFirstFrame(activity, instance)
        watchResumeAndFocus(activity)
    }

    /**
     * Marks [label] (once). A milestone reached after the first frame replaces the stored list: one more write, off
     * the main thread.
     */
    fun onMilestone(label: String) {
        if (StartupTimeline.mark(label) && frameReported) recorder?.onMilestones(StartupTimeline.snapshot())
    }

    /** A new intent arrived at the activity; only one carrying `CATEGORY_HOME` is recorded, as a timestamp. */
    fun onNewIntent(intent: Intent) {
        if (intent.hasCategory(Intent.CATEGORY_HOME)) recorder?.onHomeIntent(System.currentTimeMillis())
    }

    private fun recorderFor(context: Context): AfterUpdateRecorder =
        AfterUpdateRecorder(
            store = SharedPreferencesAfterUpdateStore(context),
            executor =
                Executors.newSingleThreadExecutor { task ->
                    Thread(task, "after-update-diagnostics").apply { isDaemon = true }
                },
        ).also { recorder = it }

    /** The first `onResume` and the first window focus of [activity] are milestones too; nothing is kept after both. */
    private fun watchResumeAndFocus(activity: Activity) {
        activity.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(resumed: Activity) {
                    if (resumed !== activity) return
                    onMilestone(StartMilestones.ON_RESUME)
                    activity.unregisterActivityLifecycleCallbacks(this)
                }

                override fun onActivityCreated(
                    a: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(a: Activity) = Unit

                override fun onActivityPaused(a: Activity) = Unit

                override fun onActivityStopped(a: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    a: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(a: Activity) = Unit
            },
        )
        val decor = activity.window?.decorView ?: return
        decor.viewTreeObserver.addOnWindowFocusChangeListener(
            object : ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) return
                    onMilestone(StartMilestones.WINDOW_FOCUS)
                    decor.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(this)
                }
            },
        )
    }

    private fun reportFirstFrame(
        activity: Activity,
        recorder: AfterUpdateRecorder,
    ) {
        val decor = activity.window?.decorView ?: return
        val done = AtomicBoolean(false)
        decor.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    if (!done.compareAndSet(false, true)) return
                    frameReported = true
                    recorder.onFirstFrame(
                        millis = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
                        milestones = StartupTimeline.snapshot(),
                    )
                    // A listener cannot be removed from inside onDraw.
                    decor.post { decor.viewTreeObserver.takeIf { it.isAlive }?.removeOnDrawListener(this) }
                }
            },
        )
    }

    /** The base intent of the task [taskId] as text: what the task is rooted in. Null when it cannot be read. */
    private fun taskBaseIntentText(
        context: Context,
        taskId: Int,
    ): String? =
        runCatching {
            context.getSystemService(ActivityManager::class.java)
                ?.appTasks
                ?.map { it.taskInfo }
                ?.firstOrNull { it.taskId == taskId }
                ?.baseIntent
                ?.let { base ->
                    intentDiagnosticsText(
                        action = base.action,
                        categories = base.categories,
                        flags = base.flags,
                        component = base.component?.flattenToShortString(),
                    )
                }
        }.getOrNull()

    private fun probe(
        context: Context,
        taskId: Int,
    ): StartProbe {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved =
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.resolveActivity(homeIntent, PackageManager.ResolveInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.resolveActivity(homeIntent, 0)
                }
            }.getOrNull()
        return StartProbe(
            pkg =
                PackageStamp(
                    versionCode = info.longVersionCode,
                    lastUpdateTimeMillis = info.lastUpdateTime,
                    firstInstallTimeMillis = info.firstInstallTime,
                ),
            homeRoleHeld =
                runCatching {
                    context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME)
                }.getOrNull(),
            homeResolvesTo = resolved?.activityInfo?.let { "${it.packageName}/${it.name}" },
            taskBaseIntent = taskBaseIntentText(context, taskId),
        )
    }
}
