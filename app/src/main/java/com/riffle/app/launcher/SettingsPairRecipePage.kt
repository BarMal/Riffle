package com.riffle.app.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.riffle.app.R
import com.riffle.core.domain.launcher.settings.PairRecipeKind

internal const val PAIR_RECIPE_TEST_TAG_PREFIX = "pair-recipe-"

/** Settings > About > Developer options > Pair recipe: three radio rows, read by the Pairs stack at launch time. */
@Composable
internal fun SettingsPairRecipePageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val selected = state.settings.diagnostics.pairRecipe
    SettingsSection(title = stringResource(R.string.settings_page_pair_recipe)) {
        Column(modifier = Modifier.selectableGroup()) {
            PairRecipeKind.entries.forEach { kind ->
                val (title, summary) = kind.texts()
                SettingsListRow(
                    modifier =
                        Modifier
                            .testTag("$PAIR_RECIPE_TEST_TAG_PREFIX${kind.name}")
                            .selectable(
                                selected = kind == selected,
                                role = Role.RadioButton,
                                onClick = { onAction(LauncherShellAction.SetPairRecipe(kind)) },
                            ),
                    title = stringResource(title),
                    subtitle = stringResource(summary),
                    trailingContent = { RadioButton(selected = kind == selected, onClick = null) },
                )
            }
        }
    }
}

private fun PairRecipeKind.texts(): Pair<Int, Int> =
    when (this) {
        PairRecipeKind.ONE_CALL -> R.string.pair_recipe_one_call to R.string.pair_recipe_one_call_summary
        PairRecipeKind.TWO_STARTS -> R.string.pair_recipe_two_starts to R.string.pair_recipe_two_starts_summary
        PairRecipeKind.PLAIN_THEN_ADJACENT ->
            R.string.pair_recipe_plain_then_adjacent to R.string.pair_recipe_plain_then_adjacent_summary
    }
