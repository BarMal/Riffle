package com.riffle.app.launcher.rss

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.rss.FeedId
import com.riffle.core.domain.launcher.rss.FeedStage
import com.riffle.core.domain.launcher.rss.FeedStageId
import com.riffle.core.domain.launcher.rss.FeedStageItem
import com.riffle.core.domain.launcher.rss.FeedStageLifecycle
import com.riffle.core.domain.launcher.settings.AdaptiveStageAppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FeedStageSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersFocusedArticleCardWithoutArtwork() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles = listOf(article(digest(1), title = "Only article", summary = "Body text")),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText("Only article").assertCountEquals(1)
        composeRule.onAllNodesWithText("Body text").assertCountEquals(1)
    }

    @Test
    fun rendersFocusedArticleCardWithArtwork() {
        val bitmap =
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.RED)
            }
        val loader = RecordingFeedArtworkLoader(bitmap.asImageBitmap())

        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles = listOf(article(digest(1), title = "Artwork article")),
                    appearance = AdaptiveStageAppearanceSettings(),
                    artworkLoader = loader,
                )
            }
        }

        composeRule.onAllNodesWithText("Artwork article").assertCountEquals(1)
        composeRule.runOnIdle { assertEquals(listOf(digest(1)), loader.requestedDigests) }
    }

    @Test
    fun onlyOneFocusedCardIsAPoliteLiveRegionAtATime() {
        val focusedLiveRegion =
            SemanticsMatcher
                .expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
                .and(hasContentDescription("Focused article card", substring = true))

        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1, 20L), item(2, 10L))),
                    articles =
                        listOf(
                            article(digest(1), title = "Newest article"),
                            article(digest(2), title = "Older article"),
                        ),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodes(focusedLiveRegion).assertCountEquals(1)
        composeRule.onAllNodesWithText("Older article")[0].performClick()
        composeRule.onAllNodes(focusedLiveRegion).assertCountEquals(1)
    }

    @Test
    fun explicitPreviousAndNextControlsNavigateAndDisableAtBoundaries() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1, 20L), item(2, 10L))),
                    articles =
                        listOf(
                            article(digest(1), title = "First article"),
                            article(digest(2), title = "Second article"),
                        ),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText("Article 1 of 2").assertCountEquals(1)
        composeRule.onAllNodesWithText("Next article")[0].performClick()
        composeRule.onAllNodesWithText("Article 2 of 2").assertCountEquals(1)
        composeRule.onAllNodesWithText("Previous article")[0].performClick()
        composeRule.onAllNodesWithText("Article 1 of 2").assertCountEquals(1)
    }

    @Test
    fun detailSanitizesRawHtmlMarkupFromTheCachedSummary() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles =
                        listOf(
                            article(
                                digest(1),
                                title = "<b>Bold</b> title",
                                summary = "<p>Body &amp; more</p><script>evil()</script>",
                            ),
                        ),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText("Show details")[0].performClick()

        composeRule.onAllNodesWithText("Bold title").assertCountEquals(1)
        composeRule.onAllNodesWithText("Body & more evil()").assertCountEquals(1)
        composeRule.onAllNodesWithText("<b>Bold</b> title").assertCountEquals(0)
        composeRule.onAllNodesWithText("<script>evil()</script>", substring = true).assertCountEquals(0)
    }

    @Test
    fun openInBrowserTriggersTheInjectedLauncherWithTheCanonicalUrl() {
        val launcher = RecordingFeedArticleBrowserLauncher()

        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles =
                        listOf(article(digest(1), canonicalUrl = "https://example.com/article")),
                    appearance = AdaptiveStageAppearanceSettings(),
                    browserLauncher = launcher,
                )
            }
        }

        composeRule.onAllNodesWithText("Show details")[0].performClick()
        composeRule.onAllNodesWithText("Open in browser")[0].performClick()

        composeRule.runOnIdle {
            assertEquals(listOf("https://example.com/article"), launcher.launchedUrls)
        }
    }

    @Test
    fun openInBrowserIsNotOfferedForANonHttpsUrl() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles = listOf(article(digest(1), canonicalUrl = null)),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText("Show details")[0].performClick()

        composeRule.onAllNodesWithText("Open in browser").assertCountEquals(0)
    }

    @Test
    fun backButtonRestoresTheStageFromDetail() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles = listOf(article(digest(1), title = "Only article")),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText("Show details")[0].performClick()
        composeRule.onAllNodesWithText("Only article").assertCountEquals(1)

        composeRule.onAllNodesWithText("Back")[0].performClick()
        composeRule.mainClock.advanceTimeBy(200)

        composeRule.onAllNodesWithText("Article 1 of 1").assertCountEquals(1)
    }

    @Test
    fun articleRemovedMidViewClosesDetailWithARecoveryMessage() {
        var articles by mutableStateOf(listOf(article(digest(1), title = "Only article")))

        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles = articles,
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText("Show details")[0].performClick()
        composeRule.onAllNodesWithText("Only article").assertCountEquals(1)

        composeRule.runOnIdle { articles = emptyList() }

        composeRule.onAllNodesWithText(FEED_MESSAGE_EMPTY).assertCountEquals(1)
        composeRule.onAllNodesWithText("The selected card is no longer available.").assertCountEquals(1)
    }

    @Test
    fun everyLifecycleRendersAnHonestPlaceholderOrCards() {
        val cases =
            listOf(
                FeedStageLifecycle.LOADING to FEED_MESSAGE_LOADING,
                FeedStageLifecycle.EMPTY to FEED_MESSAGE_EMPTY,
                FeedStageLifecycle.UNAVAILABLE to FEED_MESSAGE_UNAVAILABLE,
                FeedStageLifecycle.ERROR to FEED_MESSAGE_ERROR,
                FeedStageLifecycle.PROFILE_LOCKED to FEED_MESSAGE_PROFILE_LOCKED,
            )

        composeRule.setContent {
            MaterialTheme {
                Column {
                    cases.forEach { (lifecycle, _) ->
                        Column(modifier = Modifier.height(120.dp)) {
                            FeedStageSurface(
                                stage = stage(lifecycle, emptyList()),
                                articles = emptyList(),
                                appearance = AdaptiveStageAppearanceSettings(),
                            )
                        }
                    }
                }
            }
        }

        cases.forEach { (_, message) -> composeRule.onAllNodesWithText(message).assertCountEquals(1) }
    }

    @Test
    fun staleLifecycleRendersCardsWithASavedContentBanner() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    stage = stage(FeedStageLifecycle.STALE, listOf(item(1))),
                    articles = listOf(article(digest(1), title = "Stale article")),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText(FEED_MESSAGE_STALE_BANNER).assertCountEquals(1)
        composeRule.onAllNodesWithText("Stale article").assertCountEquals(1)
    }

    @Test
    fun activeLifecycleWithNoJoinableArticlesFallsBackToTheEmptyPlaceholder() {
        composeRule.setContent {
            MaterialTheme {
                FeedStageSurface(
                    // Item references a digest with no matching cached article -- e.g. evicted.
                    stage = stage(FeedStageLifecycle.ACTIVE, listOf(item(1))),
                    articles = emptyList(),
                    appearance = AdaptiveStageAppearanceSettings(),
                )
            }
        }

        composeRule.onAllNodesWithText(FEED_MESSAGE_EMPTY).assertCountEquals(1)
    }

    private fun digest(seed: Int): String = seed.toString().padStart(64, '0')

    private fun item(
        seed: Int,
        publishedAtEpochMillis: Long? = null,
    ): FeedStageItem = FeedStageItem(digest = digest(seed), publishedAtEpochMillis = publishedAtEpochMillis, sourceOrder = seed)

    private fun stage(
        lifecycle: FeedStageLifecycle,
        items: List<FeedStageItem>,
    ): FeedStage = FeedStage(id = FeedStageId(FeedId("feed-1")), lifecycle = lifecycle, items = items)

    private fun article(
        digest: String,
        title: String = "Title",
        summary: String? = null,
        canonicalUrl: String? = null,
    ): CachedFeedArticle =
        CachedFeedArticle(
            digest = digest,
            title = title,
            summary = summary,
            canonicalUrl = canonicalUrl,
            sourceOrder = 0,
        )

    private class RecordingFeedArtworkLoader(
        private val bitmap: ImageBitmap?,
    ) : FeedArtworkLoader {
        val requestedDigests = mutableListOf<String>()

        override suspend fun artworkFor(digest: String): ImageBitmap? {
            requestedDigests += digest
            return bitmap
        }
    }

    private class RecordingFeedArticleBrowserLauncher : FeedArticleBrowserLauncher {
        val launchedUrls = mutableListOf<String>()

        override fun launch(url: String): Boolean {
            launchedUrls += url
            return true
        }
    }
}
