package com.riffle.app.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
 * Drives [timeScapeStagePagerDrag] directly against a minimal harness, the same way
 * [CardStackGestureTest] exercises [CardStack] in isolation, rather than the full
 * [TimeScapeAppStageSurface] tree.
 */
class TimeScapeStagePagerGestureTest {
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
        var selectedStageId by mutableStateOf(stages[0].id)
        val dispatched = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val pagerState =
                rememberTimeScapeStagePagerState(
                    stages = stages,
                    selectedStageId = selectedStageId,
                    onAction = { action ->
                        dispatched.add(action)
                        if (action is LauncherShellAction.SelectAppStage) selectedStageId = action.stageId
                    },
                )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("pager")
                        .timeScapeStagePagerDrag(
                            enabled = true,
                            stageWidthPx = 1000f,
                            stages = stages,
                            selectedStageId = selectedStageId,
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
            assertEquals(stages[1].id, selectedStageId)
            assertEquals(
                listOf(LauncherShellAction.SelectAppStage(stages[1].id)),
                dispatched,
            )
        }
    }

    @Test
    fun shortDragBelowThresholdSettlesBackWithoutDispatchingAnAction() {
        val stages = listOf(stage("first"), stage("second"))
        var selectedStageId by mutableStateOf(stages[0].id)
        val dispatched = mutableListOf<LauncherShellAction>()

        composeRule.setContent {
            val coroutineScope = rememberCoroutineScope()
            val pagerState =
                rememberTimeScapeStagePagerState(
                    stages = stages,
                    selectedStageId = selectedStageId,
                    onAction = { action ->
                        dispatched.add(action)
                        if (action is LauncherShellAction.SelectAppStage) selectedStageId = action.stageId
                    },
                )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("pager")
                        .timeScapeStagePagerDrag(
                            enabled = true,
                            // A very wide virtual stage width relative to the small on-screen drag
                            // below keeps this well under both the distance and fling thresholds.
                            stageWidthPx = 100_000f,
                            stages = stages,
                            selectedStageId = selectedStageId,
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
            assertEquals(stages[0].id, selectedStageId)
            assertEquals(emptyList<LauncherShellAction>(), dispatched)
        }
    }
}
