package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardsChapter
import com.riffle.core.domain.launcher.cards.CardsChapterId
import com.riffle.core.domain.launcher.cards.CardsChapterPlanner
import com.riffle.core.domain.launcher.cards.CardsChapterPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class CardsChapterSurfaceTest {
    @Test
    fun navigatorKeepsEveryChapterReachableWhenThePlanExceedsCompactWidth() {
        val profile = AppProfile.personal()
        val chapterIds =
            (1..12).map { index ->
                CardsChapterId.App(AppPackageName("com.riffle.app$index"), profile.id)
            }
        val state =
            CardsChapterPlanner().state(
                notificationGroups = emptyList(),
                preferences = CardsChapterPreferences(pinnedChapterIds = chapterIds),
            )

        assertEquals(
            listOf(CardsChapterId.Overview) + chapterIds,
            cardsChapterNavigatorChapterIds(state),
        )
    }

    @Test
    fun labelsAppChaptersWithTheMatchingProfiledApp() {
        val personal = AppProfile.personal()
        val app =
            InstalledApp(
                identity =
                    AppIdentity(
                        packageName = AppPackageName("com.riffle.mail"),
                        activityName = AppActivityName(".Main"),
                        profile = personal,
                    ),
                label = "Mail",
            )
        val chapter =
            CardsChapter.App(
                id = CardsChapterId.App(AppPackageName("com.riffle.mail"), personal.id),
                notificationGroup = null,
                isPinned = true,
            )

        assertEquals("Mail", chapter.label(listOf(app)))
    }
}
