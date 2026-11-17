package com.riffle.app.launcher.diagnostics.pair

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.riffle.app.launcher.diagnostics.pair.AndroidPairStarter.Companion.toIntent

/** The intent that starts the holder activity, and reading its request back out of the intent's extras. */
object AndroidHolderIntents {
    private const val HOLDER_FLAGS =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
    private const val EXTRA_VARIANT = "pair.holder.variant"
    private const val EXTRA_FIRST_PACKAGE = "pair.holder.first.package"
    private const val EXTRA_FIRST_ACTIVITY = "pair.holder.first.activity"
    private const val EXTRA_SECOND_PACKAGE = "pair.holder.second.package"
    private const val EXTRA_SECOND_ACTIVITY = "pair.holder.second.activity"

    /** A new task of its own for every run, so a holder left behind is never reused. */
    fun intentFor(
        context: Context,
        request: HolderRequest,
    ): Intent =
        Intent(context, holderClass(request.variant))
            .addFlags(HOLDER_FLAGS)
            .putExtras(
                Bundle().apply {
                    putString(EXTRA_VARIANT, request.variant.id)
                    putString(EXTRA_FIRST_PACKAGE, request.first.packageName)
                    putString(EXTRA_FIRST_ACTIVITY, request.first.activityName)
                    putString(EXTRA_SECOND_PACKAGE, request.second.packageName)
                    putString(EXTRA_SECOND_ACTIVITY, request.second.activityName)
                },
            )

    /** The holder class a variant runs in: the Recents-listed one for the probe that tests that. */
    fun holderClass(variant: HeldVariant): Class<out Activity> =
        if (variant.listedHolder) PairHolderListedActivity::class.java else PairHolderActivity::class.java

    fun requestFrom(extras: Bundle?): HolderRequest? {
        val variant = HeldVariants.byId(extras?.getString(EXTRA_VARIANT))
        val first = extras?.targetOf(EXTRA_FIRST_PACKAGE, EXTRA_FIRST_ACTIVITY)
        val second = extras?.targetOf(EXTRA_SECOND_PACKAGE, EXTRA_SECOND_ACTIVITY)
        return if (variant == null || first == null || second == null) null else HolderRequest(first, second, variant)
    }

    private fun Bundle.targetOf(
        packageKey: String,
        activityKey: String,
    ): PairTarget? {
        val pkg = getString(packageKey)
        val activity = getString(activityKey)
        return if (pkg == null || activity == null) null else PairTarget(pkg, activity, pkg)
    }
}

/** The real [HolderHost]: [activity]'s own window and `startActivity`, and a main-thread handler for the timer. */
class ActivityHolderHost(
    private val activity: Activity,
) : HolderHost {
    private val handler = Handler(Looper.getMainLooper())

    // The probes below must never take the holder down: a failed read is just "false" or "unknown".
    override fun isInMultiWindowMode(): Boolean = runCatching { activity.isInMultiWindowMode }.getOrDefault(false)

    override fun boundsText(): String =
        runCatching {
            val b = activity.windowManager.currentWindowMetrics.bounds
            val density = activity.resources.displayMetrics.density
            "[${b.left},${b.top}][${b.right},${b.bottom}] ${b.width()}x${b.height()} px " +
                "(${(b.width() / density).toInt()}x${(b.height() / density).toInt()} dp)"
        }.getOrDefault("unknown")

    override fun isInPictureInPicture(): Boolean = runCatching { activity.isInPictureInPictureMode }.getOrDefault(false)

    override fun configText(): String =
        runCatching {
            val c = activity.resources.configuration
            "smallestScreenWidthDp=${c.smallestScreenWidthDp} screenWidthDp=${c.screenWidthDp}"
        }.getOrDefault("unknown")

    override fun holderFacts(): List<String> = HolderFacts.read(activity)

    override fun start(
        target: PairTarget,
        flags: Int,
    ) = activity.startActivity(PairStart(target, flags).toIntent())

    override fun finishHolder() {
        activity.finishAndRemoveTask()
        @Suppress("DEPRECATION") // The only way to switch the exit animation off on SDK 31-33.
        activity.overridePendingTransition(0, 0)
    }

    override fun post(
        delayMillis: Long,
        task: Runnable,
    ) {
        handler.postDelayed(task, delayMillis)
    }

    override fun cancel(task: Runnable) = handler.removeCallbacks(task)
}

/** What can be read about the holder activity itself through public APIs (the toast may be a rule about it). */
internal object HolderFacts {
    fun read(activity: Activity): List<String> =
        listOf(
            "activityInfo: ${activityInfo(activity)}",
            "FEATURE_FREEFORM_WINDOW_MANAGEMENT=" +
                runCatching {
                    activity.packageManager.hasSystemFeature("android.software.freeform_window_management")
                }.getOrNull(),
            "holds the HOME role: ${homeRole(activity)}",
        )

    // ActivityInfo.resizeMode is hidden and no public API says whether an activity is resizeable, so it is not logged.
    private fun activityInfo(activity: Activity): String =
        runCatching {
            val info = activity.packageManager.getActivityInfo(activity.componentName, 0)
            val recents = info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0
            "${activity.javaClass.simpleName} flags=0x${info.flags.toString(HEX)} (excludeFromRecents=$recents) " +
                "launchMode=${info.launchMode} taskAffinity='${info.taskAffinity}' " +
                "screenOrientation=${info.screenOrientation} (resizeableActivity: no public API reads it)"
        }.getOrDefault("not readable")

    private fun homeRole(activity: Activity): String =
        runCatching {
            activity.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_HOME)?.toString()
        }.getOrNull() ?: "unknown"

    private const val HEX = 16
}
