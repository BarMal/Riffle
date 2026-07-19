package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PageIndicatorInteractionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun announcesPagePositionAndSelectsPagesThroughAccessibleProgress() {
        val selectedPages = mutableListOf<Int>()
        composeRule.setContent {
            var selectedPageIndex by remember { mutableIntStateOf(1) }
            MaterialTheme {
                PageIndicator(
                    pageCount = 5,
                    selectedPageIndex = selectedPageIndex,
                    onPageSelected = { pageIndex ->
                        selectedPageIndex = pageIndex
                        selectedPages += pageIndex
                    },
                )
            }
        }

        val indicator = composeRule.onNodeWithContentDescription("Page selector")
        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 2 of 5"))
        indicator.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(4f))
        }

        composeRule.runOnIdle {
            assertEquals(listOf(4), selectedPages)
        }
        indicator.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Page 5 of 5"))
    }
}
