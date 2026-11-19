package com.riffle.app.launcher.diagnostics

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.ViewTreeObserver
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
 * The platform side of the after-update diagnostics: one recorder per process on its own thread, started once per
 * process from the first activity's `onCreate`. No receivers, no background starts and no permissions.
 */
internal object AfterUpdateProcess {
    private val started = AtomicBoolean(false)

    @Volatile
    private var recorder: AfterUpdateRecorder? = null

    /** Records this process's start when it is the first; call from `onCreate` after the window exists. */
    fun start(activity: Activity) {
        if (!started.compareAndSet(false, true)) return
        val context = activity.applicationContext
        val instance = recorderFor(context)
        val intent = activity.intent
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
            probe = { probe(context) },
        )
        reportFirstFrame(activity, instance)
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
                    recorder.onFirstFrame(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
                    // A listener cannot be removed from inside onDraw.
                    decor.post { decor.viewTreeObserver.takeIf { it.isAlive }?.removeOnDrawListener(this) }
                }
            },
        )
    }

    private fun probe(context: Context): StartProbe {
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
        )
    }
}
