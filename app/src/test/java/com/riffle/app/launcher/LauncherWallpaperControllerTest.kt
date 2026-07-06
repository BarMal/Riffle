package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.WallpaperSource
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

    private class FakeLauncherWallpaperWindow(
        private val failingCommands: Set<LauncherWallpaperWindowCommand> = emptySet(),
    ) : LauncherWallpaperWindow {
        val commands = mutableListOf<LauncherWallpaperWindowCommand>()

        override fun apply(command: LauncherWallpaperWindowCommand) {
            commands += command
            if (command in failingCommands) {
                error("Failed to apply $command")
            }
        }
    }
}
