package com.riffle.app.launcher.rss

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedArticleBrowserLauncherTest {
    @Test
    fun acceptsHttpsUrls() {
        assertTrue(isHttpsFeedArticleUrl("https://example.com/article"))
    }

    @Test
    fun rejectsNonHttpsSchemes() {
        assertFalse(isHttpsFeedArticleUrl("http://example.com/article"))
        assertFalse(isHttpsFeedArticleUrl("javascript:alert(1)"))
        assertFalse(isHttpsFeedArticleUrl("file:///etc/passwd"))
        assertFalse(isHttpsFeedArticleUrl("intent://example.com#Intent;end"))
    }

    @Test
    fun rejectsMalformedOrBlankUrls() {
        assertFalse(isHttpsFeedArticleUrl(""))
        assertFalse(isHttpsFeedArticleUrl("   "))
        assertFalse(isHttpsFeedArticleUrl("not a url"))
    }

    @Test
    fun noOpLauncherNeverReportsSuccess() {
        assertFalse(NoOpFeedArticleBrowserLauncher.launch("https://example.com"))
    }
}
