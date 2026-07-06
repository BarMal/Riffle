package com.riffle.app.launcher

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebSearchLauncherTest {
    @Test
    fun webSearchSpecUsesAndroidSearchActionWhenAvailable() {
        val spec = webSearchIntentSpec(query = " weather today ", hasWebSearchProvider = true)

        assertEquals(
            WebSearchIntentSpec(
                action = Intent.ACTION_WEB_SEARCH,
                query = "weather today",
                flags = Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
            spec,
        )
    }

    @Test
    fun webSearchSpecFallsBackToGoogleUrlWhenSearchUnavailable() {
        val spec = webSearchIntentSpec(query = "weather today", hasWebSearchProvider = false)

        assertEquals(
            WebSearchIntentSpec(
                action = Intent.ACTION_VIEW,
                uriString = "https://www.google.com/search?q=weather+today",
                flags = Intent.FLAG_ACTIVITY_NEW_TASK,
            ),
            spec,
        )
    }

    @Test
    fun webSearchSpecIgnoresBlankQueries() {
        assertNull(webSearchIntentSpec(query = "   ", hasWebSearchProvider = true))
    }
}
