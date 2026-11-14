package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.app.launcher.expressions.NoExpressionImageLoader
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.LauncherShellState
import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.stream.StreamActionGateway
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The way out of the Stream that needs no gesture: system Back (the gesture or the button) while the Stream shows asks
 * the shell for Home, through the real [LauncherShellContent] with a Stream.
 */
@RunWith(AndroidJUnit4::class)
class StreamBackInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun systemBackWhileTheStreamShowsReturnsHome() {
        val sent = mutableListOf<LauncherShellAction>()
        val state = LauncherShellState(destination = ShellDestination.STREAM)
        composeRule.setContent {
            LauncherShellContent(state = state, stream = streamHost(), onAction = { action -> sent += action })
        }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule.runOnIdle { assertEquals(listOf<LauncherShellAction>(LauncherShellAction.OpenHome), sent) }
    }

    private fun streamHost(): StreamHost =
        StreamHost(
            provider =
                StreamContentProvider(
                    object : SourceRegistry {
                        override fun descriptors(): List<SourceDescriptor> = emptyList()

                        override fun source(id: SourceId): ItemSource? = null
                    },
                    context = { StreamContext(nowEpochMillis = NOW) },
                ),
            layouts =
                CachedStreamLayoutRepository(
                    object : StreamLayoutStorePort {
                        override suspend fun read(): StreamLayouts? = null

                        override suspend fun write(layouts: StreamLayouts) = Unit
                    },
                    CoroutineScope(Dispatchers.Unconfined + Job()),
                ),
            actions =
                object : StreamActionGateway {
                    override fun perform(
                        content: com.riffle.core.domain.launcher.stream.StreamContent,
                        action: com.riffle.core.domain.launcher.stream.StreamAction,
                    ): StreamActionResult = StreamActionResult.NotOffered
                },
            imageLoader = NoExpressionImageLoader,
        )

    private companion object {
        const val NOW = 1_767_225_600_000L
    }
}
