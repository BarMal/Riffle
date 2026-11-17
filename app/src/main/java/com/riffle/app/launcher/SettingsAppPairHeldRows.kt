package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.pair.HeldVariant
import com.riffle.app.launcher.diagnostics.pair.PairLogEntry

// One row of the "With a holding window" and "Probes" sections (SettingsAppPairScenarios.kt): a variant, one tap each.

@Composable
internal fun HeldVariantRow(
    variant: HeldVariant,
    enabled: Boolean,
    log: List<PairLogEntry>,
    onRun: (HeldVariant) -> Unit,
) {
    val latest = log.firstOrNull { it.held?.id == variant.id }
    SettingsClickableRow(
        modifier = Modifier.testTag(APP_PAIR_HELD_TAG_PREFIX + variant.id).alpha(rowAlpha(enabled)),
        title = variant.title,
        subtitle = stringResource(variant.summaryRes()),
        enabled = enabled,
        onClick = { onRun(variant) },
        trailingContent = { ScenarioTrailing(APP_PAIR_HELD_RESULT_TAG_PREFIX + variant.id, latest) },
    )
}

private fun HeldVariant.summaryRes(): Int =
    when (id) {
        "H1" -> R.string.app_pair_held_h1_summary
        "H2" -> R.string.app_pair_held_h2_summary
        "H3" -> R.string.app_pair_held_h3_summary
        "H4" -> R.string.app_pair_held_h4_summary
        "H5" -> R.string.app_pair_held_h5_summary
        "H6" -> R.string.app_pair_held_h6_summary
        "P1" -> R.string.app_pair_probe_p1_summary
        "P2" -> R.string.app_pair_probe_p2_summary
        "P3" -> R.string.app_pair_probe_p3_summary
        "P4" -> R.string.app_pair_probe_p4_summary
        else -> R.string.app_pair_probe_p5_summary
    }
