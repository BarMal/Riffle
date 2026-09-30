package com.riffle.core.domain.launcher.workspace

/**
 * Source ids WS5 needs beyond the canonical [SourceIds] (owned by the source adapters), in the same
 * dotted scheme. Persisted lenses refer to sources by these strings, so they are a stored contract:
 * never rename one, only add new ids.
 *
 * [HOME_GRID] is not an adapter over platform data: it stands for the items a user placed on a home
 * page (apps, folders, widgets, shortcuts). Those stay owned by `HomeLayout`; a lens over this source
 * narrows to one page with `GroupKeyIs(pageId)`.
 */
object WorkspaceSourceIds {
    val FREQUENT_APPS = SourceId("apps.frequent")
    val FAVOURITE_APPS = SourceId("apps.favourite")
    val HOME_GRID = SourceId("home.grid")

    /** Ext key a profile-aware app source sets to `work` or `personal`; migrated Work/Personal pages filter on it. */
    val APP_PROFILE_EXT = ItemExtKey("app.profile")

    const val PROFILE_WORK = "work"
    const val PROFILE_PERSONAL = "personal"
}
