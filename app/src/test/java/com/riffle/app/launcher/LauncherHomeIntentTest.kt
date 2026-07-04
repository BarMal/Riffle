package com.riffle.app.launcher

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherHomeIntentTest {
    @Test
    fun detectsLauncherHomeIntent() {
        assertTrue(
            isLauncherHomeIntent(
                action = Intent.ACTION_MAIN,
                categories = setOf(Intent.CATEGORY_HOME),
            ),
        )
    }

    @Test
    fun ignoresNonHomeMainIntent() {
        assertFalse(
            isLauncherHomeIntent(
                action = Intent.ACTION_MAIN,
                categories = emptySet(),
            ),
        )
    }
}
