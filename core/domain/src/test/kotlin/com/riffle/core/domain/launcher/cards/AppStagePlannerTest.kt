package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppStagePlannerTest {
    private val planner = AppStagePlanner()

    @Test
    fun ordersPinnedStagesBeforeDynamicStagesByActivityThenStableIdentity() {
        val calendar = stage("calendar")
        val mail = stage("mail")
        val alpha = stage("alpha")
        val zeta = stage("zeta")

        val snapshot =
            planner.reconcile(
                inventory(calendar, mail, alpha, zeta),
                content(mail to 5L, zeta to 10L, alpha to 10L),
                AppStagePreferences(pinnedStageIds = listOf(calendar, mail)),
            )

        assertEquals(listOf(calendar, mail, alpha, zeta), snapshot.stages.map(AppStage::id))
        assertEquals(AppStageLifecycle.EMPTY, snapshot.stages.first().lifecycle)
        assertEquals(setOf(AppStageOrigin.PINNED, AppStageOrigin.DYNAMIC), snapshot.stages[1].origins)
    }

    @Test
    fun retainsFocusedDynamicEmptyStageUntilSelectionLeavesIt() {
        val mail = stage("mail")
        val chat = stage("chat")
        val initial =
            planner.reconcile(
                inventory(mail, chat),
                content(mail to 10L, chat to 5L),
                AppStagePreferences(selectedStageId = mail),
            )
        val emptyWhileFocused =
            planner.reconcile(
                inventory(mail, chat),
                content(chat to 5L),
                initial.preferences,
                initial,
            )
        val afterNavigation =
            planner.reconcile(
                inventory(mail, chat),
                content(chat to 5L),
                emptyWhileFocused.preferences.select(chat),
                emptyWhileFocused,
            )

        assertEquals(mail, emptyWhileFocused.selectedStage?.id)
        assertEquals(AppStageLifecycle.EMPTY, emptyWhileFocused.selectedStage?.lifecycle)
        assertEquals(listOf(chat), afterNavigation.stages.map(AppStage::id))
    }

    @Test
    fun refreshPreservesSelectionAcrossDynamicReorder() {
        val mail = stage("mail")
        val chat = stage("chat")
        val initial =
            planner.reconcile(
                inventory(mail, chat),
                content(mail to 5L, chat to 10L),
                AppStagePreferences(selectedStageId = mail),
            )

        val refreshed =
            planner.reconcile(
                inventory(mail, chat),
                content(mail to 20L, chat to 10L),
                initial.preferences,
                initial,
            )

        assertEquals(listOf(mail, chat), refreshed.stages.map(AppStage::id))
        assertEquals(mail, refreshed.preferences.selectedStageId)
    }

    @Test
    fun refreshKeepsPinnedOrderWhenTheirActivityChanges() {
        val calendar = stage("calendar")
        val mail = stage("mail")
        val chat = stage("chat")
        val preferences =
            AppStagePreferences(
                pinnedStageIds = listOf(calendar, mail),
                selectedStageId = mail,
            )
        val initial =
            planner.reconcile(
                inventory(calendar, mail, chat),
                content(calendar to 5L, mail to 10L, chat to 15L),
                preferences,
            )

        val refreshed =
            planner.reconcile(
                inventory(calendar, mail, chat),
                content(calendar to 30L, mail to 1L, chat to 20L),
                initial.preferences,
                initial,
            )

        assertEquals(listOf(calendar, mail, chat), refreshed.stages.map(AppStage::id))
        assertEquals(mail, refreshed.preferences.selectedStageId)
    }

    @Test
    fun treatsPersonalAndWorkAsIndependentStages() {
        val personal = stage("mail", AppProfile.personal())
        val work = stage("mail", AppProfile.work())

        val snapshot = planner.reconcile(inventory(personal, work), content(personal to 4L, work to 3L))

        assertEquals(listOf(personal, work), snapshot.stages.map(AppStage::id))
    }

    @Test
    fun removesUninstalledOrRemovedProfilePinsAndRejectsTheirContent() {
        val mail = stage("mail")
        val work = stage("chat", AppProfile.work())
        val snapshot =
            planner.reconcile(
                AppStageIdentitySnapshot(
                    installedStageIds = listOf(mail, work),
                    profileStates = mapOf(work.profileId to AppStageProfileState.REMOVED),
                ),
                content(mail to 1L, work to 2L),
                AppStagePreferences(pinnedStageIds = listOf(stage("gone"), work)),
            )

        assertEquals(listOf(mail), snapshot.stages.map(AppStage::id))
        assertEquals(emptyList(), snapshot.preferences.pinnedStageIds)
    }

    @Test
    fun retainsPinnedLockedProfileWithoutItsTransientPayload() {
        val work = stage("mail", AppProfile.work())
        val snapshot =
            planner.reconcile(
                AppStageIdentitySnapshot(
                    installedStageIds = listOf(work),
                    profileStates = mapOf(work.profileId to AppStageProfileState.LOCKED),
                ),
                content(work to 12L),
                AppStagePreferences(pinnedStageIds = listOf(work), selectedStageId = work),
            )

        assertEquals(AppStageLifecycle.PROFILE_LOCKED, snapshot.selectedStage?.lifecycle)
        assertEquals(emptyList(), snapshot.selectedStage?.content)
    }

    @Test
    fun normalizesDuplicateInputsAndInvalidRestoredSelectionWithoutThrowing() {
        val mail = stage("mail")
        val chat = stage("chat")
        val duplicateId = LauncherCardId("duplicate")
        val snapshot =
            planner.reconcile(
                inventory(mail, chat),
                AppStageContentSnapshot(
                    listOf(
                        AppStageContent(duplicateId, mail, AppStageContentKind.NOTIFICATION, 1L),
                        AppStageContent(duplicateId, chat, AppStageContentKind.MEDIA, 2L),
                    ),
                ),
                AppStagePreferences(pinnedStageIds = listOf(mail, mail), selectedStageId = stage("missing")),
            )

        assertEquals(listOf(mail, chat), snapshot.stages.map(AppStage::id))
        assertEquals(listOf(mail), snapshot.preferences.pinnedStageIds)
        assertEquals(mail, snapshot.preferences.selectedStageId)
        assertEquals(duplicateId, snapshot.stages.last().content.single().id)
    }

    @Test
    fun producesEmptySnapshotForEmptyInputs() {
        val snapshot = planner.reconcile(AppStageIdentitySnapshot())

        assertEquals(emptyList(), snapshot.stages)
        assertNull(snapshot.preferences.selectedStageId)
    }

    private fun inventory(vararg stages: AppStageId) = AppStageIdentitySnapshot(installedStageIds = stages.toList())

    private fun content(vararg entries: Pair<AppStageId, Long>) =
        AppStageContentSnapshot(
            entries.mapIndexed { index, (id, time) ->
                AppStageContent(LauncherCardId("content-$index"), id, AppStageContentKind.NOTIFICATION, time)
            },
        )

    private fun stage(
        name: String,
        profile: AppProfile = AppProfile.personal(),
    ) = AppStageId(
        packageName = AppPackageName("com.riffle.$name"),
        profileId = profile.id,
    )
}
