package com.riffle.app.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.Dock
import com.riffle.app.launcher.DockDynamicEntry
import com.riffle.app.launcher.DockDynamicEntryIntent
import com.riffle.app.launcher.DockInteractions
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.DockPosition
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dock on its own: pinned apps on the static side and the dynamic section (a waiting app's
 * stage plus the merged All-notifications entry) running on from it, on a bottom and a side edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotDevices.SDK], qualifiers = ScreenshotDevices.COMPACT_PHONE)
class DockScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dynamicSectionBottom() {
        render(DockPosition.BOTTOM)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.NIGHT)
    fun dynamicSectionBottomDark() {
        render(DockPosition.BOTTOM)
    }

    @Test
    @Config(fontScale = ScreenshotDevices.LARGE_FONT_SCALE)
    fun dynamicSectionBottomLargeFont() {
        render(DockPosition.BOTTOM)
    }

    @Test
    fun dynamicSectionSide() {
        render(DockPosition.LEFT)
    }

    @Test
    @Config(qualifiers = ScreenshotDevices.UNFOLDED_FOLDABLE)
    fun dynamicSectionSideUnfolded() {
        render(DockPosition.LEFT)
    }

    private fun render(position: DockPosition) {
        val iconLoader = SolidColorAppIconLoader()
        val pinned = listOf(ScreenshotFixtures.camera, ScreenshotFixtures.mail, ScreenshotFixtures.chat)
        composeRule.setContent {
            ScreenshotBackdrop {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = position.screenAlignment(),
                ) {
                    Dock(
                        dock =
                            DockModel(
                                capacity = pinned.size,
                                items = pinned.map { app -> ScreenshotFixtures.shortcut(app) },
                                position = position,
                            ),
                        isEditing = false,
                        notificationGroupsByApp = ScreenshotFixtures.notificationGroups,
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = iconLoader,
                        position = position,
                        interactions = DockInteractions(position = position, onAction = {}),
                        dynamicEntries =
                            listOf(
                                stageEntry(ScreenshotFixtures.calendar, badgeCount = 1, isSelected = true),
                                stageEntry(ScreenshotFixtures.photos, badgeCount = 2, isSelected = false),
                                allNotificationsEntry,
                            ),
                    )
                }
            }
        }
        composeRule.captureScreen()
    }

    private fun DockPosition.screenAlignment(): Alignment =
        when (this) {
            DockPosition.TOP -> Alignment.TopCenter
            DockPosition.BOTTOM -> Alignment.BottomCenter
            DockPosition.LEFT -> Alignment.CenterStart
            DockPosition.RIGHT -> Alignment.CenterEnd
        }

    private fun stageEntry(
        app: InstalledApp,
        badgeCount: Int,
        isSelected: Boolean,
    ): DockDynamicEntry {
        val stageId = AppStageId(packageName = app.identity.packageName, profileId = app.identity.profile.id)
        return DockDynamicEntry(
            key = "stage:${app.identity.packageName.value}",
            label = app.label,
            identity = app.identity,
            badgeCount = badgeCount,
            isSelected = isSelected,
            contentDescription = "${app.label}, $badgeCount cards, Open stage",
            intent = DockDynamicEntryIntent.Dispatch(LauncherShellAction.SelectAppStage(stageId)),
        )
    }

    private val allNotificationsEntry =
        DockDynamicEntry(
            key = "all-notifications",
            label = "All notifications",
            identity = null,
            badgeCount = ScreenshotFixtures.notificationGroups.sumOf { group -> group.count },
            isSelected = false,
            contentDescription = "All notifications, Open stage",
            intent = DockDynamicEntryIntent.ShowAllNotifications,
        )
}
