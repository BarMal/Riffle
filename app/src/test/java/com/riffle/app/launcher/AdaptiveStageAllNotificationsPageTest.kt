package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.cards.AppStage
import com.riffle.core.domain.launcher.cards.AppStageId
import com.riffle.core.domain.launcher.cards.AppStageLifecycle
import com.riffle.core.domain.launcher.cards.AppStageOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the "All notifications" virtual page (#1057): the plain
 * [AdaptiveStagePage] list a real stage list expands into, which index the pager/rail should show
 * as selected, and what settling on a given index should dispatch. The actual merged-content
 * rendering ([AdaptiveStageAllNotificationsStack]) is covered separately by
 * [AdaptiveStageAdaptiveLayoutInteractionTest]-style androidTests, since it needs a real Compose
 * tree.
 */
class AdaptiveStageAllNotificationsPageTest {
    @Test
    fun withAllNotificationsPageAppendsTheVirtualPageAfterEveryRealStage() {
        val mail = stage("mail")
        val chat = stage("chat")
        val stages = listOf(appStage(mail), appStage(chat))

        val pages = stages.withAllNotificationsPage()

        assertEquals(3, pages.size)
        assertEquals(mail, (pages[0] as AdaptiveStagePage.Stage).stage.id)
        assertEquals(chat, (pages[1] as AdaptiveStagePage.Stage).stage.id)
        assertEquals(AdaptiveStagePage.AllNotifications, pages[2])
    }

    @Test
    fun withAllNotificationsPageOnAnEmptyStageListIsJustTheVirtualPage() {
        val pages = emptyList<AppStage>().withAllNotificationsPage()

        assertEquals(listOf(AdaptiveStagePage.AllNotifications), pages)
    }

    @Test
    fun selectedPageIndexResolvesARealSelectedStage() {
        val mail = stage("mail")
        val chat = stage("chat")
        val pages = listOf(appStage(mail), appStage(chat)).withAllNotificationsPage()

        assertEquals(
            1,
            adaptiveStageSelectedPageIndex(pages, selectedStageId = chat, allNotificationsSelected = false),
        )
    }

    @Test
    fun selectedPageIndexResolvesTheLastPageWhenAllNotificationsIsSelected() {
        val pages = listOf(appStage(stage("mail")), appStage(stage("chat"))).withAllNotificationsPage()

        assertEquals(
            2,
            adaptiveStageSelectedPageIndex(pages, selectedStageId = null, allNotificationsSelected = true),
        )
    }

    @Test
    fun selectedPageIndexIsOutOfBoundsWhenNoStageIsSelectedAndAllNotificationsIsNotChosen() {
        val pages = listOf(appStage(stage("mail"))).withAllNotificationsPage()

        val index =
            adaptiveStageSelectedPageIndex(pages, selectedStageId = null, allNotificationsSelected = false)

        assertEquals(-1, index)
        assertNull(pages.getOrNull(index))
    }

    @Test
    fun selectedPageIndexStaysZeroForAnEmptyStageListWithAllNotificationsSelected() {
        val pages = emptyList<AppStage>().withAllNotificationsPage()

        assertEquals(
            0,
            adaptiveStageSelectedPageIndex(pages, selectedStageId = null, allNotificationsSelected = true),
        )
    }

    @Test
    fun settlingOnARealStagePageSelectsItAndLeavesAllNotifications() {
        val mail = stage("mail")
        val pages = listOf(appStage(mail)).withAllNotificationsPage()
        var dispatchedAction: LauncherShellAction? = null
        var allNotificationsSelected: Boolean? = null

        adaptiveStageOnPageSettled(
            pages,
            index = 0,
            onAction = { dispatchedAction = it },
            onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
        )

        assertEquals(LauncherShellAction.SelectAppStage(mail), dispatchedAction)
        assertEquals(false, allNotificationsSelected)
    }

    @Test
    fun settlingOnTheAllNotificationsPageSelectsItWithoutDispatchingAStageAction() {
        val pages = listOf(appStage(stage("mail"))).withAllNotificationsPage()
        var dispatchedAction: LauncherShellAction? = null
        var allNotificationsSelected: Boolean? = null

        adaptiveStageOnPageSettled(
            pages,
            index = 1,
            onAction = { dispatchedAction = it },
            onAllNotificationsSelectedChanged = { allNotificationsSelected = it },
        )

        assertNull(dispatchedAction)
        assertEquals(true, allNotificationsSelected)
    }

    @Test
    fun settlingOnAnOutOfBoundsIndexDoesNothing() {
        val pages = listOf(appStage(stage("mail"))).withAllNotificationsPage()
        var called = false

        adaptiveStageOnPageSettled(
            pages,
            index = 99,
            onAction = { called = true },
            onAllNotificationsSelectedChanged = { called = true },
        )

        assertTrue(!called)
    }

    @Test
    fun pageKeyDistinguishesEveryRealStageFromTheVirtualPage() {
        val mail = stage("mail")
        val chat = stage("chat")

        val mailKey = adaptiveStagePageKey(AdaptiveStagePage.Stage(appStage(mail)))
        val chatKey = adaptiveStagePageKey(AdaptiveStagePage.Stage(appStage(chat)))
        val allNotificationsKey = adaptiveStagePageKey(AdaptiveStagePage.AllNotifications)

        assertEquals(adaptiveStageStageKey(mail), mailKey)
        assertEquals(adaptiveStageStageKey(chat), chatKey)
        assertEquals("all-notifications", allNotificationsKey)
        assertTrue(setOf(mailKey, chatKey, allNotificationsKey).size == 3)
    }

    private fun stage(
        name: String,
        profile: AppProfile = AppProfile.personal(),
    ) = AppStageId(packageName = AppPackageName("com.riffle.$name"), profileId = profile.id)

    @Suppress("MaxLineLength")
    private fun appStage(id: AppStageId) =
        AppStage(id, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.ACTIVE, emptyList())
}
