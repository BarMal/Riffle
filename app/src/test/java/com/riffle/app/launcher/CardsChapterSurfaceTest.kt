package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.cards.CardsChapter
import com.riffle.core.domain.launcher.cards.CardsChapterId
import org.junit.Assert.assertEquals
import org.junit.Test

class CardsChapterSurfaceTest {
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
