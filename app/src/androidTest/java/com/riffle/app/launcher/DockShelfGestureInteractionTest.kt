package com.riffle.app.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.gestures.GestureThresholds
import com.riffle.core.domain.launcher.home.AppShortcutItem
import com.riffle.core.domain.launcher.home.DockModel
import com.riffle.core.domain.launcher.home.HomeLayoutDefaults
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.notifications.AppNotificationGroup
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationAccessStatus
import com.riffle.core.domain.launcher.notifications.NotificationAgeBucket
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.settings.HomeGesture
import com.riffle.core.domain.launcher.settings.HomeGestureSettings
import com.riffle.core.domain.launcher.settings.LauncherGestureAction
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DockShelfGestureInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    // Shelf expansion is switched off launcher-wide for now (DockShelfExpansion); these tests cover
    // the dormant shelf, so they switch it on around themselves.
    @Before
    fun enableShelfExpansion() {
        DockShelfExpansion.enabled = true
    }

    @After
    fun restoreShelfExpansion() {
        DockShelfExpansion.enabled = false
    }

    @Test
    fun dockOpeningDragEndingOnReleaseExpandsBeforeTheHomeGestureThreshold() {
        val homeActions = mutableListOf<LauncherShellAction>()
        var isShelfExpanded by mutableStateOf(false)
        var openingDragWasConsumedBeforeHomeThreshold by mutableStateOf(false)
        composeRule.setContent {
            Box(
                modifier =
                    Modifier
                        .size(200.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                                val start = down.position
                                val claimPx = shelfClaimPx()
                                val homeThresholdPx = homeSwipePx()
                                while (true) {
                                    val change =
                                        awaitPointerEvent(PointerEventPass.Final)
                                            .changes
                                            .firstOrNull { it.id == down.id }
                                            ?: break
                                    val drag = change.position - start
                                    if (
                                        drag.y <= -claimPx &&
                                        drag.y > -homeThresholdPx &&
                                        change.isConsumed
                                    ) {
                                        openingDragWasConsumedBeforeHomeThreshold = true
                                    }
                                    if (!change.pressed) break
                                }
                            }
                        }
                        .homeGestureInput(
                            enabled = true,
                            // Swipe up is unbound by default, so bind it: the point is that the
                            // home layer does not also act on the drag the shelf claimed.
                            settings =
                                HomeGestureSettings().withAction(
                                    gesture = HomeGesture.ONE_FINGER_UP,
                                    action = LauncherGestureAction.OPEN_SEARCH,
                                ),
                            onAction = homeActions::add,
                        ),
            ) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .testTag("dock-shelf")
                            .dockShelfGestureInput(
                                DockInteractions(
                                    isShelfExpanded = isShelfExpanded,
                                    onShelfExpandedChange = { isShelfExpanded = it },
                                    onAction = {},
                                ),
                            ),
                )
            }
        }

        composeRule.onNodeWithTag("dock-shelf").performTouchInput {
            down(Offset(width / 2f, height - 1f))
            moveBy(Offset(0f, -shelfClaimPx()))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -shelfOpeningRemainderPx()))
            up()
        }

        composeRule.runOnIdle {
            assertTrue(isShelfExpanded)
            assertTrue(openingDragWasConsumedBeforeHomeThreshold)
            assertEquals(emptyList<LauncherShellAction>(), homeActions)
        }
    }

    @Test
    fun upwardSwipeOnCollapsedProductionDockExpandsTheShelf() {
        val primary = shortcut("primary")
        val overflow = shortcut("overflow")
        setProductionDock(primary, overflow)
        assertCollapsed()

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -shelfClaimPx()))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -shelfOpeningRemainderPx()))
            up()
        }

        composeRule.waitForIdle()
        assertExpanded()
    }

    @Test
    fun productionDockDoesNotReuseTheOpeningPointerAfterExpanding() {
        val primary = shortcut("primary")
        val overflow = shortcut("overflow")
        setProductionDock(primary, overflow)
        assertCollapsed()

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -shelfClaimPx()))
            // This crosses the expansion threshold while the pointer remains pressed. StandardHome
            // replaces the collapsed Dock with ExpandedDockSurface before the same pointer releases.
            moveBy(Offset(0f, -shelfOpeningRemainderPx()))
            advanceEventTime(100)
            // A rebound must not be interpreted as a new expanded-shelf collapse gesture by the
            // restarted production pointer handler. The second movement reaches the collapse
            // threshold relative to the first rebound, which is the origin the old free-running
            // handler incorrectly adopted after the collapsed Dock was replaced.
            moveBy(Offset(0f, 1f))
            advanceEventTime(100)
            moveBy(Offset(0f, GestureThresholds.DOCK_SHELF_TOGGLE_DP.dp.toPx() + 7.dp.toPx()))
            up()
        }

        composeRule.waitForIdle()
        assertExpanded()
    }

    @Test
    fun productionDockRetainsAnOpeningSwipeAcrossCallbackRecomposition() {
        val primary = shortcut("primary")
        val overflow = shortcut("overflow")
        var isShelfExpanded by mutableStateOf(false)
        var callbackVersion by mutableStateOf(0)
        composeRule.setContent {
            val callbackGeneration = callbackVersion
            val onShelfExpandedChange =
                remember(callbackGeneration) {
                    { expanded: Boolean -> isShelfExpanded = expanded }
                }
            MaterialTheme {
                Box(
                    modifier =
                        Modifier
                            .size(200.dp)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                                    while (true) {
                                        val change =
                                            awaitPointerEvent(PointerEventPass.Final)
                                                .changes
                                                .firstOrNull { it.id == down.id }
                                                ?: break
                                        if (change.isConsumed) {
                                            callbackVersion += 1
                                            break
                                        }
                                        if (!change.pressed) break
                                    }
                                }
                            },
                ) {
                    Dock(
                        dock = DockModel(capacity = 1, items = listOf(primary, overflow)),
                        isEditing = false,
                        notificationGroupsByApp = emptyList(),
                        appShortcutsByApp = emptyMap(),
                        appIconLoader = EmptyAppIconLoader,
                        interactions =
                            DockInteractions(
                                onShelfExpandedChange = onShelfExpandedChange,
                                onAction = {},
                            ),
                    )
                }
            }
        }

        composeRule.onNodeWithTag(dockItemTestTag(primary.id)).performTouchInput {
            down(center)
            moveBy(Offset(0f, -shelfClaimPx()))
            updatePointerBy(pointerId = 0, delta = Offset(0f, -shelfOpeningRemainderPx()))
            up()
        }

        composeRule.runOnIdle { assertTrue(isShelfExpanded) }
    }

    /**
     * A production dock holding [primary] and [overflow] with room for one. The shelf's content is the notification
     * section (the dock's live app slots default to on), so a group from [chat] is what there is to expand into: its
     * card showing is what "expanded" looks like, and it is absent while the dock is collapsed.
     */
    private fun setProductionDock(
        primary: AppShortcutItem,
        overflow: AppShortcutItem,
    ) {
        val layout =
            HomeLayoutDefaults.standard().let { standardLayout ->
                standardLayout.copy(
                    dock = standardLayout.dock.copy(capacity = 1, items = listOf(primary, overflow)),
                )
            }
        val apps = listOf(primary.installedApp(), overflow.installedApp(), chat)
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(400.dp)) {
                    StandardHome(
                        layout = layout,
                        installedApps = apps,
                        interactions = StandardHomeInteractions(),
                        presentation =
                            StandardHomePresentation(
                                notificationGroupsByApp = listOf(chatNotificationGroup()),
                                notificationAccessStatus = NotificationAccessStatus.GRANTED,
                                installedApps = apps,
                                appShortcutsByApp = emptyMap(),
                            ),
                        appIconLoader = EmptyAppIconLoader,
                        onAction = {},
                    )
                }
            }
        }
    }

    private fun assertExpanded() = composeRule.onAllNodesWithText(chat.label).assertCountEquals(1)

    private fun assertCollapsed() = composeRule.onAllNodesWithText(chat.label).assertCountEquals(0)

    private val chat =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.chat"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = "Chat",
        )

    private fun chatNotificationGroup(): AppNotificationGroup =
        AppNotificationGroup(
            packageName = chat.identity.packageName,
            profileId = chat.identity.profile.id,
            latestCategory = NotificationCategory.MESSAGE,
            latestAgeBucket = NotificationAgeBucket.RECENT,
            notifications =
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("chat:1"),
                        packageName = chat.identity.packageName,
                        profileId = chat.identity.profile.id,
                        category = NotificationCategory.MESSAGE,
                        canDismiss = true,
                        postedAtEpochMillis = 1L,
                    ),
                ),
        )

    private fun shortcut(name: String): AppShortcutItem =
        AppShortcutItem(
            id = LauncherItemId(name),
            appIdentity =
                AppIdentity(
                    packageName = AppPackageName("com.riffle.$name"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = name,
        )

    private fun AppShortcutItem.installedApp(): InstalledApp =
        InstalledApp(
            identity = appIdentity,
            label = label,
        )
}

// Every threshold is dp (GestureThresholds); these drags are too, so they mean the same physical
// gesture on whatever density the test device runs at.
private fun Density.shelfClaimPx(): Float = GestureThresholds.DOCK_SHELF_CLAIM_DP.dp.toPx()

/** What is left, past the claim, to go a few dp beyond the shelf's toggle threshold. */
private fun Density.shelfOpeningRemainderPx(): Float =
    (GestureThresholds.DOCK_SHELF_TOGGLE_DP - GestureThresholds.DOCK_SHELF_CLAIM_DP + 3f).dp.toPx()

private fun Density.homeSwipePx(): Float = GestureThresholds.HOME_SWIPE_DP.dp.toPx()
