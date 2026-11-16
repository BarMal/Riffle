package com.riffle.app.launcher.diagnostics.pair

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

/** Starts the experiment's activities with a plain [Context.startActivity] from [context] (the launcher's activity). */
class AndroidPairStarter(
    private val context: Context,
) : PairStarter {
    override fun start(start: PairStart) = context.startActivity(start.toIntent())

    override fun startAll(starts: List<PairStart>) =
        context.startActivities(starts.map { it.toIntent() }.toTypedArray())

    override fun startTrampoline(request: TrampolineRequest) {
        val intent =
            Intent(context, PairTrampolineActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtras(request.toExtras())
        context.startActivity(intent)
    }

    override fun startHolder(request: HolderRequest) =
        context.startActivity(AndroidHolderIntents.intentFor(context, request))

    companion object {
        const val EXTRA_ADJACENT_FIRST = "pair.adjacentFirst"
        private const val EXTRA_FIRST_PACKAGE = "pair.first.package"
        private const val EXTRA_FIRST_ACTIVITY = "pair.first.activity"
        private const val EXTRA_SECOND_PACKAGE = "pair.second.package"
        private const val EXTRA_SECOND_ACTIVITY = "pair.second.activity"

        fun PairStart.toIntent(): Intent =
            Intent.makeMainActivity(ComponentName(target.packageName, target.activityName)).addFlags(flags)

        fun TrampolineRequest.toExtras(): Bundle =
            Bundle().apply {
                putBoolean(EXTRA_ADJACENT_FIRST, adjacentFirst)
                putString(EXTRA_FIRST_PACKAGE, first.packageName)
                putString(EXTRA_FIRST_ACTIVITY, first.activityName)
                putString(EXTRA_SECOND_PACKAGE, second.packageName)
                putString(EXTRA_SECOND_ACTIVITY, second.activityName)
            }

        fun requestFrom(extras: Bundle?): TrampolineRequest? {
            val first = extras?.targetOf(EXTRA_FIRST_PACKAGE, EXTRA_FIRST_ACTIVITY)
            val second = extras?.targetOf(EXTRA_SECOND_PACKAGE, EXTRA_SECOND_ACTIVITY)
            return if (extras == null || first == null || second == null) {
                null
            } else {
                TrampolineRequest(first, second, extras.getBoolean(EXTRA_ADJACENT_FIRST))
            }
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
}

/** Reads the facts through public APIs only; [posture] is the layout class Riffle reports for this window. */
class AndroidPairFactsSource(
    private val context: Context,
    private val posture: () -> String,
) : PairFactsSource {
    override fun collect(
        first: PairTarget,
        second: PairTarget,
    ): PairFacts {
        val pm = context.packageManager
        val config = context.resources.configuration
        val activity =
            generateSequence(context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
        return PairFacts(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            inMultiWindow = activity?.isInMultiWindowMode,
            windowDp = "${config.screenWidthDp}x${config.screenHeightDp}",
            widthClass = widthClassFor(config.screenWidthDp),
            posture = posture(),
            features =
                listOf(
                    "FEATURE_FREEFORM_WINDOW_MANAGEMENT=" +
                        pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
                    "FEATURE_PICTURE_IN_PICTURE=${pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)}",
                    "FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS=" +
                        pm.hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS),
                    "smallestScreenWidthDp=${config.smallestScreenWidthDp}",
                ),
            targets = listOf(first, second).map { targetFact(pm, it) },
        )
    }

    // ActivityInfo.resizeMode is hidden API, so resizability cannot be read; the public screenOrientation is the hint.
    private fun targetFact(
        pm: PackageManager,
        target: PairTarget,
    ): String {
        val component = ComponentName(target.packageName, target.activityName)
        val info = runCatching { pm.getActivityInfo(component, 0) }.getOrNull()
        val orientation = info?.let { "screenOrientation=${it.screenOrientation}" } ?: "activity info not readable"
        // FLAG_STOPPED is set only for an app never started since install or force-stopped; it is not a running check.
        val stopped =
            runCatching { pm.getApplicationInfo(target.packageName, 0).flags and ApplicationInfo.FLAG_STOPPED != 0 }
                .getOrNull()
        return "${target.packageName}: $orientation, FLAG_STOPPED=${stopped ?: "unknown"} " +
            "(resizeMode is not public; FLAG_STOPPED false does not mean running)"
    }
}
