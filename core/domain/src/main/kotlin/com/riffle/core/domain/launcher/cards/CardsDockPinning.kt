package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.LauncherItemId

/*
 * Pinning a Cards stage means pinning its app to the dock (#XXXX): the dock's own pinned items are
 * the durable, live source of a stage's pinned state, in place of a separate Cards-only preference.
 * A dock pin is activity-specific (AppIdentity); a stage is package+profile-level (it aggregates
 * every activity's notifications), so these are the two directions that translation runs.
 */

/** The stage a dock app identity belongs to, aggregating across that app's activities. */
fun AppIdentity.toAppStageId(): AppStageId = AppStageId(packageName, profile.id)

/** The stages currently pinned to [dock], in the dock's own item order. */
fun dockPinnedStageIds(dock: DockModel): List<AppStageId> =
    dock.items
        .filterIsInstance<AppShortcutItem>()
        .map { item -> item.appIdentity.toAppStageId() }
        .distinct()

/** The dock item ids backing [stageId], however many of the stage's activities are pinned. */
fun DockModel.itemIdsForStage(stageId: AppStageId): List<LauncherItemId> =
    items
        .filterIsInstance<AppShortcutItem>()
        .filter { item -> item.appIdentity.toAppStageId() == stageId }
        .map { item -> item.id }

/**
 * The app [stageId] pins to the dock, when pinning it: a stage has no single activity of its own,
 * so this picks the same stable, deterministic one an empty stage's own detail card already shows
 * (`appStageEmptyAppCard`) -- alphabetically first by activity name, so repeated calls agree.
 */
fun representativeInstalledAppForStage(
    stageId: AppStageId,
    installedApps: List<InstalledApp>,
): InstalledApp? =
    installedApps
        .asSequence()
        .filter { app ->
            app.identity.packageName == stageId.packageName && app.identity.profile.id == stageId.profileId
        }
        .sortedBy { app -> app.identity.activityName.value }
        .firstOrNull()
