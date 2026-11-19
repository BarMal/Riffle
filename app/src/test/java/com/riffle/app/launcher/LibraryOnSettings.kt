package com.riffle.app.launcher

import com.riffle.core.domain.launcher.settings.FeatureSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository

/** Settings with the Library switched on: it defaults off, and some tests are about the sheet it opens. */
internal val libraryOnSettings = LauncherSettings(features = FeatureSettings(library = true))

/** A settings store that holds [libraryOnSettings] and keeps what is saved. */
internal class LibraryOnSettingsRepository : LauncherSettingsRepository {
    private var saved: LauncherSettings = libraryOnSettings

    override fun loadLauncherSettings(): LauncherSettings = saved

    override fun saveLauncherSettings(settings: LauncherSettings) {
        saved = settings
    }
}
