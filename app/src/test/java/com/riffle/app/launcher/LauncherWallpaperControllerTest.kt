package com.riffle.app.launcher

import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherPage
import com.riffle.core.domain.launcher.home.LauncherPageId
import com.riffle.core.domain.launcher.home.WallpaperScrollMode
import com.riffle.core.domain.launcher.home.WallpaperSettings
import com.riffle.core.domain.launcher.home.WallpaperSource
import com.riffle.core.domain.launcher.settings.AppearanceSettings
import com.riffle.core.domain.launcher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherWallpaperControllerTest {
    @Test
    fun systemWallpaperSourceUsesTransparentWallpaperWindow() {
        assertEquals(
            LauncherWallpaperWindowCommand.ShowSystemWallpaper,
            WallpaperSource.SYSTEM.launcherWallpaperWindowCommand(),
        )
    }

    @Test
    fun solidColorWallpaperSourceUsesSolidColorWindow() {
        assertEquals(
            LauncherWallpaperWindowCommand.ShowSolidColor,
            WallpaperSource.SOLID_COLOR.launcherWallpaperWindowCommand(),
        )
    }

    @Test
    fun appliesRequestedWallpaperSourceToWindow() {
        val window = FakeLauncherWallpaperWindow()

        val result = AndroidLauncherWallpaperController(window).applySource(WallpaperSource.SYSTEM)

        assertEquals(LauncherWallpaperApplyResult.Applied(WallpaperSource.SYSTEM), result)
        assertEquals(listOf(LauncherWallpaperWindowCommand.ShowSystemWallpaper), window.commands)
    }

    @Test
    fun appliesWallpaperOffsetCommandToWindow() {
        val window = FakeLauncherWallpaperWindow()
        val command = LauncherWallpaperOffsetCommand(xOffset = 0.5f, xOffsetStep = 1f)

        AndroidLauncherWallpaperController(window).applyOffset(command)

        assertEquals(listOf(command), window.offsetCommands)
    }

    @Test
    fun ignoresWallpaperOffsetApplyFailures() {
        val window = FakeLauncherWallpaperWindow(failOffsetCommands = true)

        AndroidLauncherWallpaperController(window).applyOffset(
            LauncherWallpaperOffsetCommand(xOffset = 0.5f, xOffsetStep = 1f),
        )
    }

    @Test
    fun staticWallpaperOffsetStaysCentered() {
        assertEquals(
            LauncherWallpaperOffsetCommand(xOffset = 0.5f, xOffsetStep = 1f),
            wallpaperOffsetCommand(
                scrollMode = WallpaperScrollMode.STATIC,
                selectedPageIndex = 2,
                pageCount = 5,
            ),
        )
    }

    @Test
    fun scrollingWallpaperOffsetTracksSelectedPage() {
        assertEquals(
            LauncherWallpaperOffsetCommand(xOffset = 0f, xOffsetStep = 0.25f),
            wallpaperOffsetCommand(
                scrollMode = WallpaperScrollMode.SCROLLING,
                selectedPageIndex = 0,
                pageCount = 5,
            ),
        )
        assertEquals(
            LauncherWallpaperOffsetCommand(xOffset = 0.5f, xOffsetStep = 0.25f),
            wallpaperOffsetCommand(
                scrollMode = WallpaperScrollMode.SCROLLING,
                selectedPageIndex = 2,
                pageCount = 5,
            ),
        )
        assertEquals(
            LauncherWallpaperOffsetCommand(xOffset = 1f, xOffsetStep = 0.25f),
            wallpaperOffsetCommand(
                scrollMode = WallpaperScrollMode.SCROLLING,
                selectedPageIndex = 4,
                pageCount = 5,
            ),
        )
    }

    @Test
    fun scrollingWallpaperOffsetFallsBackToStaticForSinglePage() {
        assertEquals(
            LauncherWallpaperOffsetCommand(xOffset = 0.5f, xOffsetStep = 1f),
            wallpaperOffsetCommand(
                scrollMode = WallpaperScrollMode.SCROLLING,
                selectedPageIndex = 0,
                pageCount = 1,
            ),
        )
    }

    @Test
    fun wallpaperOffsetSyncSkipsSolidWallpaper() {
        val state =
            LauncherShellState(
                launcherSettings =
                    LauncherSettings(
                        appearance =
                            AppearanceSettings(
                                wallpaper = WallpaperSettings(source = WallpaperSource.SOLID_COLOR),
                            ),
                    ),
            )

        assertNull(launcherWallpaperOffsetCommand(state))
    }

    @Test
    fun wallpaperOffsetSyncUsesSelectedPageAndPageCountForSystemWallpaper() {
        val defaultGrid = HomeLayoutDefaults.standard().selectedPage.grid
        val firstPage = LauncherPage(id = LauncherPageId("first"), grid = defaultGrid)
        val secondPage = LauncherPage(id = LauncherPageId("second"), grid = defaultGrid)
        val thirdPage = LauncherPage(id = LauncherPageId("third"), grid = defaultGrid)
        val layout =
            HomeLayoutDefaults.standard().copy(
                pages = listOf(firstPage, secondPage, thirdPage),
                selectedPageId = secondPage.id,
            )
        val state =
            LauncherShellState(
                homeLayout = layout,
                launcherSettings =
                    LauncherSettings(
                        appearance =
                            AppearanceSettings(
                                wallpaper =
                                    WallpaperSettings(
                                        source = WallpaperSource.SYSTEM,
                                        scrollMode = WallpaperScrollMode.SCROLLING,
                                    ),
                            ),
                    ),
            )

        assertEquals(
            LauncherWallpaperOffsetCommand(xOffset = 0.5f, xOffsetStep = 0.5f),
            launcherWallpaperOffsetCommand(state),
        )
    }

    @Test
    fun fallsBackToSolidColorWhenSystemWallpaperWindowApplyFails() {
        val window =
            FakeLauncherWallpaperWindow(
                failingCommands = setOf(LauncherWallpaperWindowCommand.ShowSystemWallpaper),
            )

        val result = AndroidLauncherWallpaperController(window).applySource(WallpaperSource.SYSTEM)

        assertEquals(
            LauncherWallpaperApplyResult.Failed(
                requestedSource = WallpaperSource.SYSTEM,
                fallbackSource = WallpaperSource.SOLID_COLOR,
            ),
            result,
        )
        assertEquals(
            listOf(
                LauncherWallpaperWindowCommand.ShowSystemWallpaper,
                LauncherWallpaperWindowCommand.ShowSolidColor,
            ),
            window.commands,
        )
    }

    @Test
    fun doesNotReportFallbackWhenSolidColorWindowApplyFails() {
        val window =
            FakeLauncherWallpaperWindow(
                failingCommands = setOf(LauncherWallpaperWindowCommand.ShowSolidColor),
            )

        val result = AndroidLauncherWallpaperController(window).applySource(WallpaperSource.SOLID_COLOR)

        assertEquals(
            LauncherWallpaperApplyResult.Failed(
                requestedSource = WallpaperSource.SOLID_COLOR,
                fallbackSource = null,
            ),
            result,
        )
        assertEquals(listOf(LauncherWallpaperWindowCommand.ShowSolidColor), window.commands)
    }

    @Test
    fun explainsWallpaperApplyFailuresForSettingsFeedback() {
        assertNull(LauncherWallpaperApplyResult.Applied(WallpaperSource.SYSTEM).failureMessage())
        assertEquals(
            "System wallpaper could not be shown; using solid background.",
            LauncherWallpaperApplyResult.Failed(
                requestedSource = WallpaperSource.SYSTEM,
                fallbackSource = WallpaperSource.SOLID_COLOR,
            ).failureMessage(),
        )
        assertEquals(
            "Wallpaper could not be updated.",
            LauncherWallpaperApplyResult.Failed(
                requestedSource = WallpaperSource.SOLID_COLOR,
                fallbackSource = null,
            ).failureMessage(),
        )
    }

    @Test
    fun createsFallbackSettingsActionWhenFallbackSourceDiffersFromRequestedSource() {
        assertNull(LauncherWallpaperApplyResult.Applied(WallpaperSource.SYSTEM).fallbackWallpaperSourceAction())
        assertNull(
            LauncherWallpaperApplyResult.Failed(
                requestedSource = WallpaperSource.SOLID_COLOR,
                fallbackSource = null,
            ).fallbackWallpaperSourceAction(),
        )
        assertEquals(
            LauncherShellAction.SelectWallpaperSource(WallpaperSource.SOLID_COLOR),
            LauncherWallpaperApplyResult.Failed(
                requestedSource = WallpaperSource.SYSTEM,
                fallbackSource = WallpaperSource.SOLID_COLOR,
            ).fallbackWallpaperSourceAction(),
        )
    }

    private class FakeLauncherWallpaperWindow(
        private val failingCommands: Set<LauncherWallpaperWindowCommand> = emptySet(),
        private val failOffsetCommands: Boolean = false,
    ) : LauncherWallpaperWindow {
        val commands = mutableListOf<LauncherWallpaperWindowCommand>()
        val offsetCommands = mutableListOf<LauncherWallpaperOffsetCommand>()

        override fun apply(command: LauncherWallpaperWindowCommand) {
            commands += command
            if (command in failingCommands) {
                error("Failed to apply $command")
            }
        }

        override fun applyOffset(command: LauncherWallpaperOffsetCommand) {
            if (failOffsetCommands) {
                error("Failed to apply $command")
            }
            offsetCommands += command
        }
    }
}
