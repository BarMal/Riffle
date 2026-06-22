package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Test

class AppDrawerSectionsTest {
    @Test
    fun sectionsPreserveAppOrderWithinFirstLetterBuckets() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("Browser"),
                    app("Calculator"),
                    app("Calendar"),
                    app("Camera"),
                ),
            )

        assertEquals(listOf("B", "C"), sections.map { section -> section.title })
        assertEquals(listOf("Browser"), sections[0].apps.map { app -> app.label })
        assertEquals(listOf("Calculator", "Calendar", "Camera"), sections[1].apps.map { app -> app.label })
    }

    @Test
    fun sectionsUseOtherBucketForLabelsWithoutLeadingLetters() {
        val sections =
            AppDrawerSections.from(
                listOf(
                    app("  1Password"),
                    app("- Debug"),
                    app("Alpha"),
                ),
            )

        assertEquals(listOf("#", "A"), sections.map { section -> section.title })
        assertEquals(listOf("  1Password", "- Debug"), sections[0].apps.map { app -> app.label })
    }

    private fun app(label: String): InstalledApp =
        InstalledApp(
            identity =
                AppIdentity(
                    packageName = AppPackageName("com.example.${label.lowercase().replace(" ", ".")}"),
                    activityName = AppActivityName(".MainActivity"),
                ),
            label = label,
        )
}
