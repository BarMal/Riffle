package com.riffle.app.launcher.calendar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.riffle.app.launcher.sources.SourceChangeSource
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStatus
import com.riffle.core.domain.launcher.workspace.sources.CalendarAccessStep
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.sources.calendarAccessStatus
import com.riffle.core.domain.launcher.workspace.sources.calendarAccessStep
import java.util.concurrent.CopyOnWriteArraySet

/** Platform seam for `READ_CALENDAR`. Checking never prompts; the request itself is launched by the activity. */
internal interface CalendarAccessGateway {
    /** Full status for UI; needs the activity to read the system rationale flag. */
    fun status(): CalendarAccessStatus

    /** Cheap, thread-safe grant check for source reads. */
    fun isGranted(): Boolean

    /** Records how a system request we launched ended, so a permanent denial can be told from "never asked". */
    fun recordRequestResult(granted: Boolean)

    fun createAppSettingsIntent(): Intent
}

/** The launcher's own record that a calendar request came back denied. Not calendar data. */
internal interface CalendarDenialHistory {
    var denied: Boolean
}

internal class SharedPreferencesCalendarDenialHistory(context: Context) : CalendarDenialHistory {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override var denied: Boolean
        get() = preferences.getBoolean(KEY_DENIED, false)
        set(value) {
            preferences.edit().putBoolean(KEY_DENIED, value).apply()
        }

    private companion object {
        const val PREFERENCES_NAME = "riffle_calendar_access"
        const val KEY_DENIED = "request_denied"
    }
}

internal class AndroidCalendarAccessGateway(
    private val activity: Activity,
    private val history: CalendarDenialHistory,
) : CalendarAccessGateway {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override fun status(): CalendarAccessStatus =
        runCatching {
            calendarAccessStatus(
                granted = isGranted(),
                shouldShowSystemRationale =
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_CALENDAR),
                hasBeenDenied = history.denied,
            )
        }.getOrDefault(CalendarAccessStatus.UNKNOWN)

    override fun recordRequestResult(granted: Boolean) {
        history.denied = !granted
    }

    override fun createAppSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null))
}

/** What the source gate reads: a confirmed grant or [SourceAccess.REQUIRED]; never prompts. */
internal fun CalendarAccessGateway.sourceAccess(): SourceAccess =
    if (isGranted()) SourceAccess.GRANTED else SourceAccess.REQUIRED

/**
 * Tells the Calendar stream when the permission flips, so a grant shows events and a revocation drops them
 * without waiting for a calendar change. Feed it every status refresh; repeated equal statuses are silent.
 */
internal class CalendarAccessChanges : SourceChangeSource {
    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    @Volatile
    private var lastGranted: Boolean? = null

    override fun observe(onChanged: () -> Unit): () -> Unit {
        listeners.add(onChanged)
        return { listeners.remove(onChanged) }
    }

    fun onStatus(status: CalendarAccessStatus) {
        val granted = status == CalendarAccessStatus.GRANTED
        val previous = lastGranted
        lastGranted = granted
        if (previous != null && previous != granted) listeners.forEach { listener -> listener() }
    }
}

/**
 * The single entry point for an explicit "allow calendar access" action (Settings row, or any surface
 * showing a "needs calendar access" state via `LauncherShellAction.RequestCalendarAccess`). It launches the
 * system dialog or the app settings page as the policy dictates and reports the step taken; it is never
 * called without a user action.
 *
 * [rationaleVisible] defaults to true because every caller is required to show the rationale next to its
 * button; a caller that cannot passes false and gets [CalendarAccessStep.SHOW_RATIONALE] back instead.
 */
internal class CalendarAccessCoordinator(
    private val gateway: CalendarAccessGateway,
    private val launchPermissionRequest: () -> Unit,
    private val openAppSettings: () -> Unit,
) {
    fun request(rationaleVisible: Boolean = true): CalendarAccessStep {
        val step = calendarAccessStep(gateway.status(), rationaleVisible)
        when (step) {
            CalendarAccessStep.REQUEST_PERMISSION -> launchPermissionRequest()
            CalendarAccessStep.OPEN_APP_SETTINGS -> openAppSettings()
            CalendarAccessStep.NONE,
            CalendarAccessStep.SHOW_RATIONALE,
            -> Unit
        }
        return step
    }
}
