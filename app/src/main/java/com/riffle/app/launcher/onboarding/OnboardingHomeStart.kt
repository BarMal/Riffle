package com.riffle.app.launcher.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.home.HomeStart

internal fun homeStartTestTag(start: HomeStart): String = "onboarding-home-start-${start.name}"

/**
 * "How should Home start?": two small previews to choose between. Choosing is only a selection; the set-up's Next
 * button applies it. "All my apps" is preselected and recommended.
 */
@Composable
internal fun HomeStartChoice(
    selected: HomeStart,
    onSelect: (HomeStart) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
        Text(
            text = stringResource(R.string.onboarding_home_start_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.m)) {
            HomeStartCard(
                modifier = Modifier.weight(1f),
                start = HomeStart.ALL_APPS,
                title = stringResource(R.string.onboarding_home_start_all_title),
                body = stringResource(R.string.onboarding_home_start_all_body),
                tag = stringResource(R.string.onboarding_home_start_recommended),
                selected = selected == HomeStart.ALL_APPS,
                onSelect = onSelect,
            )
            HomeStartCard(
                modifier = Modifier.weight(1f),
                start = HomeStart.EMPTY,
                title = stringResource(R.string.onboarding_home_start_empty_title),
                body = stringResource(R.string.onboarding_home_start_empty_body),
                tag = null,
                selected = selected == HomeStart.EMPTY,
                onSelect = onSelect,
            )
        }
        Text(
            text = stringResource(R.string.onboarding_home_start_dock_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeStartCard(
    start: HomeStart,
    title: String,
    body: String,
    tag: String?,
    selected: Boolean,
    onSelect: (HomeStart) -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        modifier =
            modifier
                .testTag(homeStartTestTag(start))
                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(start) }),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(if (selected) 2.dp else 1.dp, outline),
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            HomeStartPreview(filled = start == HomeStart.ALL_APPS)
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = tag ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** A tiny Home: four rows of icons (or empty cells) above a Dock of four, drawn with plain shapes. */
@Composable
private fun HomeStartPreview(filled: Boolean) {
    val description =
        stringResource(
            if (filled) R.string.onboarding_home_start_preview_all else R.string.onboarding_home_start_preview_empty,
        )
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp))
                .padding(8.dp)
                .semantics { contentDescription = description },
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(PREVIEW_ROWS) { PreviewRow(count = 4, filled = filled, dim = false) }
        }
        PreviewRow(count = 4, filled = true, dim = true)
    }
}

@Composable
private fun PreviewRow(
    count: Int,
    filled: Boolean,
    dim: Boolean,
) {
    val fill = if (dim) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        repeat(count) {
            Box(
                modifier =
                    Modifier
                        .size(ICON_SIZE)
                        .background(
                            color = if (filled) fill else MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(5.dp),
                        ),
            )
        }
    }
}

private val PREVIEW_HEIGHT = 112.dp
private val ICON_SIZE = 16.dp
private const val PREVIEW_ROWS = 3
