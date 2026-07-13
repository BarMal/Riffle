package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiscreteSettingSliderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun announcesItsTitleAndFormattedValueAndCommitsAccessibleDiscreteValues() {
        val selectedValues = mutableListOf<Int>()
        composeRule.setContent {
            var selectedValue by remember { mutableIntStateOf(10) }
            MaterialTheme {
                DiscreteSettingSlider(
                    title = "Horizontal screen margin",
                    value = selectedValue,
                    valueRange = 0..20,
                    valueLabel = { "$it dp" },
                    onValueChange = { value ->
                        selectedValue = value
                        selectedValues += value
                    },
                )
            }
        }

        val slider = composeRule.onNodeWithContentDescription("Horizontal screen margin")
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "10 dp"))

        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(0f))
        }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(14f))
        }
        slider.performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
            assertTrue(setProgress(20f))
        }

        composeRule.runOnIdle {
            assertEquals(listOf(0, 14, 20), selectedValues)
        }
        slider.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "20 dp"))
    }

    @Test
    fun dragPreviewsIntermediateValuesAndCommitsOnlyItsFinalValue() {
        val committedValues = mutableListOf<Int>()
        composeRule.setContent {
            var persistedValue by remember { mutableIntStateOf(10) }
            MaterialTheme {
                DiscreteSettingSlider(
                    title = "Horizontal screen margin",
                    value = persistedValue,
                    valueRange = 0..20,
                    valueLabel = { "$it dp" },
                    onValueChange = { value ->
                        persistedValue = value
                        committedValues += value
                    },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("Horizontal screen margin")
            .performTouchInput {
                swipe(
                    start = Offset(width / 2f, height / 2f),
                    end = Offset(width - 1f, height / 2f),
                )
            }

        composeRule.runOnIdle {
            assertEquals(1, committedValues.size)
            assertTrue(committedValues.single() > 10)
        }
    }
}
