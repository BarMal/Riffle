package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppShortcut
import com.riffle.core.domain.launcher.apps.AppShortcutId
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import com.riffle.core.domain.launcher.settings.LauncherGestureLaunchTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureSettingsPanelTest {
    @Test
    fun appTargetLabelUsesInstalledAppLabel() {
        val mail = app(label = "Mail")

        assertEquals(
            "Launch app: Mail",
            LauncherGestureAction.LAUNCH_APP.targetLabel(
                target = LauncherGestureLaunchTarget.App(mail.identity),
                installedApps = listOf(mail),
            ),
        )
    }

    @Test
    fun appTargetLabelDisambiguatesProfilesWithTheSameAppLabel() {
        val personalMail = app(label = "Mail")
        val workMail = app(label = "Mail", profile = AppProfile.work())

        assertEquals(
            "Launch app: Work - Mail",
            LauncherGestureAction.LAUNCH_APP.targetLabel(
                target = LauncherGestureLaunchTarget.App(workMail.identity),
                installedApps = listOf(personalMail, workMail),
            ),
        )
    }

    @Test
    fun appTargetLabelDisambiguatesActivitiesWithTheSameAppLabelAndProfile() {
        val primaryMail = app(label = "Mail", activityName = ".PrimaryActivity")
        val secondaryMail = app(label = "Mail", activityName = ".SecondaryActivity")

        assertEquals(
            "Launch app: Mail (.SecondaryActivity)",
            LauncherGestureAction.LAUNCH_APP.targetLabel(
                target = LauncherGestureLaunchTarget.App(secondaryMail.identity),
                installedApps = listOf(primaryMail, secondaryMail),
            ),
        )
    }

    @Test
    fun appTargetLabelFallsBackToPackageNameWhenAppIsNoLongerInstalled() {
        val mail = app(label = "Mail")

        assertEquals(
            "Launch app: com.riffle.mail",
            LauncherGestureAction.LAUNCH_APP.targetLabel(
                target = LauncherGestureLaunchTarget.App(mail.identity),
                installedApps = emptyList(),
            ),
        )
    }

    @Test
    fun appPickerLabelDisambiguatesProfilesWithTheSameAppLabel() {
        val personalMail = app(label = "Mail")
        val workMail = app(label = "Mail", profile = AppProfile.work())

        assertEquals("Work - Mail", workMail.gesturePickerLabel(listOf(personalMail, workMail)))
    }

    @Test
    fun shortcutPickerLabelIncludesOwningAppForDuplicateShortcutLabels() {
        val personalMail = app(label = "Mail")
        val workMail = app(label = "Mail", profile = AppProfile.work())
        val personalCompose = shortcut(id = "personal-compose", app = personalMail, label = "Compose")
        val workCompose = shortcut(id = "work-compose", app = workMail, label = "Compose")

        assertEquals(
            "Compose — Work - Mail",
            workCompose.gesturePickerLabel(
                shortcuts = listOf(personalCompose, workCompose),
                installedApps = listOf(personalMail, workMail),
            ),
        )
    }

    @Test
    fun shortcutPickerLabelIncludesShortcutIdWhenOwningAppIsAlsoDuplicated() {
        val mail = app(label = "Mail")
        val firstCompose = shortcut(id = "compose-first", app = mail, label = "Compose")
        val secondCompose = shortcut(id = "compose-second", app = mail, label = "Compose")

        assertEquals(
            "Compose — Mail (compose-second)",
            secondCompose.gesturePickerLabel(
                shortcuts = listOf(firstCompose, secondCompose),
                installedApps = listOf(mail),
            ),
        )
    }

    private fun app(
        label: String,
        activityName: String = ".MainActivity",
        profile: AppProfile = AppProfile.personal(),
    ): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.mail"),
                    activityName = AppActivityName(activityName),
                    profile = profile,
                ),
            label = label,
        )

    private fun shortcut(
        id: String,
        app: InstalledApp,
        label: String,
    ): AppShortcut =
        AppShortcut(
            id = AppShortcutId(id),
            appIdentity = app.identity,
            shortLabel = label,
        )
}
