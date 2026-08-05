package com.riffle.core.domain.launcher.cards

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppStageContentMergeTest {
    @Test
    fun emptyStageListMergesToEmpty() {
        assertEquals(emptyList(), emptyList<AppStage>().mergedContentByRecency())
    }

    @Test
    fun singleStageMergesToItsOwnContentInRecencyOrder() {
        val mail = stage("mail")
        val older = content("older", mail, 5L)
        val newer = content("newer", mail, 10L)
        val stages = listOf(appStage(mail, listOf(older, newer)))

        val merged = stages.mergedContentByRecency()

        assertEquals(listOf(newer, older), merged.map(AppStageContentEntry::content))
        assertTrue(merged.all { entry -> entry.stage.id == mail })
    }

    @Test
    fun multipleStagesInterleaveByRecencyAcrossStages() {
        val mail = stage("mail")
        val chat = stage("chat")
        val mailOld = content("mail-old", mail, 5L)
        val mailNew = content("mail-new", mail, 15L)
        val chatMid = content("chat-mid", chat, 10L)
        val stages =
            listOf(
                appStage(mail, listOf(mailOld, mailNew)),
                appStage(chat, listOf(chatMid)),
            )

        val merged = stages.mergedContentByRecency()

        assertEquals(listOf(mailNew, chatMid, mailOld), merged.map(AppStageContentEntry::content))
        assertEquals(listOf(mail, chat, mail), merged.map { entry -> entry.stage.id })
    }

    @Test
    fun tiedActivityBreaksByContentIdMatchingWithinStageOrdering() {
        val mail = stage("mail")
        val chat = stage("chat")
        // Same timestamp, different ids -- id.value ("content-a" < "content-b") decides order,
        // mirroring AppStagePlanner's private contentOrder comparator's own tie-break.
        val fromChat = content("content-b", chat, 10L)
        val fromMail = content("content-a", mail, 10L)
        val stages =
            listOf(
                appStage(mail, listOf(fromMail)),
                appStage(chat, listOf(fromChat)),
            )

        val merged = stages.mergedContentByRecency()

        assertEquals(listOf(fromMail, fromChat), merged.map(AppStageContentEntry::content))
    }

    private fun content(
        id: String,
        stageId: AppStageId,
        meaningfulActivityAtEpochMillis: Long,
    ) = AppStageContent(LauncherCardId(id), stageId, AppStageContentKind.NOTIFICATION, meaningfulActivityAtEpochMillis)

    private fun appStage(
        id: AppStageId,
        content: List<AppStageContent>,
    ) = AppStage(id, setOf(AppStageOrigin.DYNAMIC), AppStageLifecycle.ACTIVE, content)

    private fun stage(
        name: String,
        profile: AppProfile = AppProfile.personal(),
    ) = AppStageId(
        packageName = AppPackageName("com.riffle.$name"),
        profileId = profile.id,
    )
}
