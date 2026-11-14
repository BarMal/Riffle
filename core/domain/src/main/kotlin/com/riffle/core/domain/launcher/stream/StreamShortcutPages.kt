package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutsByApp
import com.riffle.core.domain.launcher.workspace.sources.ItemImageKeys

/** An app pinned in the Dock: its identity and the name the Dock shows. */
data class PinnedApp(
    val identity: AppIdentity,
    val label: String,
)

/**
 * The Stream pages of the Dock's pinned apps (ADR 0003, Amendment 3): "an app with nothing live shows its shortcuts".
 *
 * Every pinned app that has no live page of its own gets a page of [StreamSection.SHORTCUTS] with the app's launcher
 * shortcuts (the ones its long-press menu offers: static, dynamic and pinned, in the platform's rank order), one card
 * each. An app with no shortcuts, or when the launcher role is missing (shortcuts can only be read by the Home app),
 * gets one card that opens the app; without the role a gap says why, after the card. A pinned app that has a live page
 * (it has notifications) is already a stack of cards and gets no second page. Pure; the pages are transient.
 */
object StreamShortcutPages {
    /** The group key of an app's page: `package:profile`, the same key its notifications group has. */
    fun appKeyOf(identity: AppIdentity): String = "${identity.packageName.value}:${identity.profile.id.value}"

    /** The key of the shortcuts page of the app keyed [appKey]. */
    fun pageKeyOf(appKey: String): String = StreamPagePlanner.keyOf(StreamSection.SHORTCUTS, appKey)

    /**
     * The shortcuts pages of [pinned] (one per distinct app, in the Dock's order) that are not [liveGroupKeys], from
     * [shortcutsByApp] (empty without the role). [launcherRole] is whether the Home role is held.
     */
    fun plan(
        pinned: List<PinnedApp>,
        liveGroupKeys: Set<String>,
        shortcutsByApp: AppShortcutsByApp,
        launcherRole: Boolean,
    ): List<StreamPage> =
        pinned
            .distinctBy { appKeyOf(it.identity) }
            .filter { appKeyOf(it.identity) !in liveGroupKeys }
            .map { app -> page(app, shortcutsByApp[app.identity].orEmpty(), launcherRole) }

    private fun page(
        app: PinnedApp,
        shortcuts: List<AppShortcut>,
        launcherRole: Boolean,
    ): StreamPage {
        val appKey = appKeyOf(app.identity)
        val ref = StreamAppRef(app.identity.packageName.value, app.identity.profile.id.value)
        val cards =
            if (launcherRole && shortcuts.isNotEmpty()) {
                shortcuts.sortedBy { it.rank }.map { shortcut ->
                    AppShortcutContent(
                        id = StreamContentId("shortcut:$appKey/${shortcut.id.value}"),
                        app = ref,
                        appIdentity = app.identity,
                        appLabel = app.label,
                        shortcutId = shortcut.id.value,
                        shortLabel = shortcut.shortLabel,
                        longLabel = shortcut.longLabel?.takeIf { it.isNotBlank() && it != shortcut.shortLabel },
                        icon = ItemImageKeys.shortcutIcon(app.identity, shortcut.id.value),
                        enabled = shortcut.enabled,
                        disabledMessage = shortcut.disabledMessage,
                    )
                }
            } else {
                listOf(openCard(app, appKey, ref))
            }
        val gaps =
            if (launcherRole) {
                emptyList()
            } else {
                listOf(
                    StreamGap(
                        StreamSourceIds.SHORTCUTS,
                        StreamSectionResult.PermissionRequired(listOf(StreamSourceIds.SHORTCUTS)),
                    ),
                )
            }
        return StreamPage(
            key = pageKeyOf(appKey),
            section = StreamSection.SHORTCUTS,
            groupKey = appKey,
            label = app.label,
            count = cards.size,
            view = StreamView.STACK,
            body = StreamPageBody.Content(listOf(StreamGroup(appKey, app.label, cards)), gaps),
            lead = cards.first(),
        )
    }

    private fun openCard(
        app: PinnedApp,
        appKey: String,
        ref: StreamAppRef,
    ) = AppShortcutContent(
        id = StreamContentId("shortcut:$appKey/"),
        app = ref,
        appIdentity = app.identity,
        appLabel = app.label,
        shortcutId = null,
        shortLabel = app.label,
        longLabel = null,
        icon = ItemImageKeys.appIcon(app.identity),
    )
}
