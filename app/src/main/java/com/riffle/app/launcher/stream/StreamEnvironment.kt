package com.riffle.app.launcher.stream

import android.text.format.DateUtils
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.app.launcher.workspace.ItemImageKeyParser
import com.riffle.app.launcher.workspace.ParsedImageKey
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.stream.MediaContent
import com.riffle.core.domain.launcher.stream.NotificationContent
import com.riffle.core.domain.launcher.stream.RecentAppContent
import com.riffle.core.domain.launcher.stream.StreamAction
import com.riffle.core.domain.launcher.stream.StreamAppRef
import com.riffle.core.domain.launcher.stream.StreamContent
import com.riffle.core.domain.launcher.stream.StreamLayoutAction
import com.riffle.core.domain.launcher.stream.StreamSection
import com.riffle.core.domain.launcher.workspace.ItemImageHandle
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.settings.HideKind
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Everything the Stream formats with a clock or a locale, behind one interface so a screenshot test can fix it.
 * [live] says the surface should re-read [now] now and then (countdowns); a fixed clock sets it false.
 */
internal interface StreamFormats {
    val live: Boolean get() = true

    fun now(): Long

    /** "5 min. ago". */
    fun age(epochMillis: Long): String

    /** The clock time of [epochMillis] ("14:30" or "2:30 PM"). */
    fun time(epochMillis: Long): String

    /** A day that is neither today nor tomorrow ("Nov 14, 2026"). */
    fun dayLabel(day: LocalDate): String

    fun today(): LocalDate
}

internal class SystemStreamFormats(
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val clock: () -> Long = System::currentTimeMillis,
) : StreamFormats {
    override fun now(): Long = clock()

    override fun age(epochMillis: Long): String =
        DateUtils.getRelativeTimeSpanString(
            epochMillis,
            clock(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()

    override fun time(epochMillis: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone()).format(Instant.ofEpochMilli(epochMillis))

    override fun dayLabel(day: LocalDate): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(day)

    override fun today(): LocalDate = Instant.ofEpochMilli(clock()).atZone(zone()).toLocalDate()
}

/** What the renderers read besides the content itself. */
internal data class StreamEnvironment(
    val imageLoader: ExpressionImageLoader,
    val appIconLoader: AppIconLoader,
    val installedApps: List<InstalledApp>,
    val reducedMotion: Boolean,
    val formats: StreamFormats,
    /** The time countdowns count from; refreshed by the surface while [StreamFormats.live]. */
    val nowEpochMillis: Long,
)

/**
 * What the Stream asks its host to do. Every user action goes through [onAction] (the one gateway), Hide through
 * [onHide] (the existing exclusions flow; null when the shell offers none), a layout edit through [onLayoutAction].
 * A data class on purpose: a surface that wraps one callback does it with `copy`, so every other callback reaches
 * every page.
 */
@Suppress("LongParameterList")
internal data class StreamCallbacks(
    val onAction: (StreamContent, StreamAction) -> Unit = { _, _ -> },
    val onLayoutAction: (StreamLayoutAction) -> Unit = {},
    /** "Allow access to show this": the existing explicit source access flow for the first of these sources. */
    val onRequestAccess: (List<SourceId>) -> Unit = {},
    /** "Turn on": an explicit user action that turns the section's sources back on. */
    val onEnableSection: (StreamSection) -> Unit = {},
    val onHide: ((StreamContent, String?, HideKind) -> Unit)? = null,
    /** Quick-add: a new task with this text, due today. */
    val onAddTask: (String) -> Unit = {},
    /** An explicit refresh of the tasks (the Retry and Refresh buttons). */
    val onRefreshTasks: () -> Unit = {},
    /** "Connect Todoist": opens Settings > Stream > Tasks. */
    val onConnectTasks: () -> Unit = {},
)

internal fun StreamContent.appRef(): StreamAppRef? =
    when (this) {
        is NotificationContent -> app
        is MediaContent -> app
        is RecentAppContent -> app
        else -> null
    }

/** The installed app a piece of content belongs to, to light its card with the app's own colour; null when unknown. */
internal fun StreamEnvironment.identityFor(
    app: StreamAppRef?,
    icon: ItemImageHandle? = null,
): AppIdentity? {
    val installed =
        app?.let {
            val ofPackage =
                installedApps.filter {
                        candidate ->
                    candidate.identity.packageName.value == app.packageName
                }
            ofPackage.firstOrNull { candidate ->
                app.profileId == null || candidate.identity.profile.id.value == app.profileId
            } ?: ofPackage.firstOrNull()
        }
    return installed?.identity ?: (icon?.let(ItemImageKeyParser::parse) as? ParsedImageKey.ActivityIcon)?.identity
}
