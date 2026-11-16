package com.riffle.app.launcher.diagnostics.pair

import android.content.Context
import android.content.Intent

/** The Android side of the scripted run: finding an app's launch activity and starting the launcher's own Home. */
object AndroidPairScriptPlatform {
    /** The target of the app's launch intent, or null when [packageName] has none (not installed or not launchable). */
    fun resolve(
        context: Context,
        packageName: String,
    ): PairTarget? {
        val pm = context.packageManager
        val component = pm.getLaunchIntentForPackage(packageName)?.component ?: return null
        val label =
            runCatching { pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString() }.getOrDefault(packageName)
        return PairTarget(packageName, component.className, label)
    }

    /**
     * Starts this app's Home activity (restricted to this package) in a new task. A start from the background can be
     * refused by the system, which is why the caller treats an exception as a logged outcome, not an error.
     */
    fun returnToLauncher(context: Context) {
        val intent =
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
