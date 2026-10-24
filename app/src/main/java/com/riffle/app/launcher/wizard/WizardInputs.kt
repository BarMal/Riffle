package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.apps.AppVisibility
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.RssSettings
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.FeedOption
import java.net.URI

/** The pickers' choices, from what the shell already holds. Identities and labels only; no item content. */
internal object WizardInputs {
    /** The apps an app picker offers: visible, enabled apps by label (each identity once). */
    fun apps(installed: List<InstalledApp>): List<AppPick> =
        installed
            .filter { it.enabled && it.visibility == AppVisibility.VISIBLE }
            .distinctBy { it.identity }
            .sortedBy { it.label.lowercase() }
            .map { AppPick(it.identity, it.label) }

    /**
     * The feeds a feed picker offers: every RSS feed (by its host; the full link is never shown) and each calendar
     * feed by name. The keys are the group keys their items carry (`FeedItemMapper`, `IcsItemMapper`).
     */
    fun feeds(
        rss: RssSettings,
        calendars: List<Pair<String, String>> = emptyList(),
    ): List<FeedOption> =
        rss.feeds.map { FeedOption(SourceIds.RSS, it.id.value, hostOf(it.url.value)) } +
            calendars.map { (key, name) -> FeedOption(SourceIds.ICS, key, name) }

    fun hostOf(url: String): String = runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url
}
