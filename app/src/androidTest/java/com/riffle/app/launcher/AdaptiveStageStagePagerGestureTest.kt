package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageLifecycle
import com.riffle.core.domain.launcher.cards.AppStageOrigin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Drives [adaptiveStageStagePagerDrag] directly against a minimal harness, the same way
 * [CardStackGestureTest] exercises [CardStack] in isolation, rather than the full
 * [AdaptiveStageAppStageSurface] tree. Works in plain page indices rather than [AppStage]s
 * directly (see #1057) -- the pager itself no longer knows whether a given index is a real stage
 * or the virtual All-notifications page; only the harness (mirroring
 * [AdaptiveStageAppStageSurface]'s own [adaptiveStageOnPageSettled]) maps a settled index back to
 * a [LauncherShellAction].
 */
class AdaptiveStageStagePagerGestureTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun stage(packageName: String): AppStage =
        AppStage(
            id = AppStageId(AppPackageName("com.example.$packageName"), AppProfile.personal().id),
            origins = setOf(AppStageOrigin.PINNED),
            lifecycle = AppStageLifecycle.EMPTY,
        )

    @Test
    fun horizontalDragPastThresholdSettlesToTheNextStage() {
        val stages = listOf(stage("first"), stage("second"))
        var selectedIndex by mutableIntStateOf(0)
        val dispatched = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val pagerState =
                rememberAdaptiveStageStagePagerState(
                    pageCount = stages.size,
                    selectedIndex = selectedIndex,
                    onSettle = { index ->
                        dispatched.add(LauncherShellAction.SelectAppStage(stages[index].id))
                        selectedIndex = index
                    },
                )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("pager")
                        .adaptiveStageStagePagerDrag(
                            enabled = true,
                            stageWidthPx = 1000f,
                            pageCount = stages.size,
                            selectedIndex = selectedIndex,
                            navigationKey = "test",
                            pagerState = pagerState,
                            reducedMotion = false,
                            launchStageMotion = { action ->
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { action() }
                            },
                        ),
            )
        }

        composeRule.onNodeWithTag("pager").performTouchInput {
            swipe(start = Offset(width - 1f, height / 2f), end = Offset(1f, height / 2f))
        }

        composeRule.runOnIdle {
            assertEquals(1, selectedIndex)
            assertEquals(
                listOf(LauncherShellAction.SelectAppStage(stages[1].id)),
                dispatched,
            )
        }
    }

    @Test
    fun shortDragBelowThresholdSettlesBackWithoutDispatchingAnAction() {
        val stages = listOf(stage("first"), stage("second"))
        var selectedIndex by mutableIntStateOf(0)
        val dispatched = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val pagerState =
                rememberAdaptiveStageStagePagerState(
                    pageCount = stages.size,
                    selectedIndex = selectedIndex,
                    onSettle = { index ->
                        dispatched.add(LauncherShellAction.SelectAppStage(stages[index].id))
                        selectedIndex = index
                    },
                )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("pager")
                        .adaptiveStageStagePagerDrag(
                            enabled = true,
                            // A very wide virtual stage width relative to the small on-screen drag
                            // below keeps this well under both the distance and fling thresholds.
                            stageWidthPx = 100_000f,
                            pageCount = stages.size,
                            selectedIndex = selectedIndex,
                            navigationKey = "test",
                            pagerState = pagerState,
                            reducedMotion = false,
                            launchStageMotion = { action ->
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) { action() }
                            },
                        ),
            )
        }

        composeRule.onNodeWithTag("pager").performTouchInput {
            swipe(
                start = Offset(width / 2f, height / 2f),
                end = Offset(width / 2f - 40f, height / 2f),
                durationMillis = 400,
            )
        }

        composeRule.runOnIdle {
            assertEquals(0, selectedIndex)
            assertEquals(emptyList<LauncherShellAction>(), dispatched)
        }
    }
}
