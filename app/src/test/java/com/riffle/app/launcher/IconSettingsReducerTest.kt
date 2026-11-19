package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppVisibilityRepository
import com.riffle.core.domain.launcher.home.HomeLayout
import com.riffle.core.domain.launcher.home.HomeLayoutRepository
import com.riffle.core.domain.launcher.icons.AppIconOverride
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.LegacyIconHandling
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.LauncherSettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconSettingsReducerTest {
    private val saved = mutableListOf<LauncherSettings>()
    private val reducer =
        LauncherSettingsStateReducer(
            homeLayoutRepository =
                object : HomeLayoutRepository {
                    override fun loadHomeLayout(): HomeLayout? = null

                    override fun saveHomeLayout(layout: HomeLayout) = Unit
                },
            launcherSettingsRepository =
                object : LauncherSettingsRepository {
                    override fun loadLauncherSettings(): LauncherSettings? = null

                    override fun saveLauncherSettings(settings: LauncherSettings) {
                        saved += settings
                    }
                },
            appVisibilityRepository =
                object : AppVisibilityRepository {
                    override fun hiddenAppIdentities(): Set<AppIdentity> = emptySet()

                    override fun hideApp(identity: AppIdentity) = Unit

                    override fun showApp(identity: AppIdentity) = Unit
                },
        )

    private val app = AppIdentity(AppPackageName("com.example.notes"), AppActivityName("com.example.notes.Main"))
    private val ref = IconPackDrawableRef("com.example.pack", "notes")

    private fun apply(vararg actions: LauncherShellAction): LauncherShellState =
        actions.fold(LauncherShellState()) { state, action -> reducer.reduce(state, action) }

    @Test
    fun eachChoiceIsStoredAndSaved() {
        val state =
            apply(
                LauncherShellAction.SelectIconShape(IconShape.TEARDROP),
                LauncherShellAction.SelectLegacyIconHandling(LegacyIconHandling.AS_IS),
                LauncherShellAction.SetThemedIcons(true),
                LauncherShellAction.SetThemeAllIcons(true),
                LauncherShellAction.SelectIconPack("com.example.pack"),
            )

        val icons = state.launcherSettings.icons
        assertEquals(IconShape.TEARDROP, icons.shape)
        assertEquals(LegacyIconHandling.AS_IS, icons.legacyIcons)
        assertTrue(icons.themedIcons && icons.themeAllIcons)
        assertEquals("com.example.pack", icons.iconPack)
        assertEquals(icons, saved.last().icons)
    }

    @Test
    fun anOverrideAndACustomNameAreStoredPerApp() {
        val state =
            apply(
                LauncherShellAction.SetAppIconOverride(app, ref),
                LauncherShellAction.SetAppLabelOverride(app, " Jot "),
            )

        assertEquals(AppIconOverride(ref, "Jot"), state.launcherSettings.icons.overrides[app])
    }

    @Test
    fun resetIconChangesClearsTheIconsKeepsTheNamesAndUndoRestoresThem() {
        val before =
            apply(
                LauncherShellAction.SetAppIconOverride(app, ref),
                LauncherShellAction.SetAppLabelOverride(app, "Jot"),
            )

        val reset = reducer.reduce(before, LauncherShellAction.ResetIconChanges)
        assertEquals(AppIconOverride(label = "Jot"), reset.launcherSettings.icons.overrides[app])

        val undone =
            reducer.reduce(
                reset,
                LauncherShellAction.RestoreIconOverrides(before.launcherSettings.icons.overrides),
            )
        assertEquals(before.launcherSettings.icons, undone.launcherSettings.icons)
    }

    @Test
    fun anActionThatChangesNothingSavesNothing() {
        val savedBefore = saved.size

        apply(LauncherShellAction.SelectIconShape(IconShape.SYSTEM), LauncherShellAction.ResetIconChanges)

        assertEquals(savedBefore, saved.size)
    }
}
