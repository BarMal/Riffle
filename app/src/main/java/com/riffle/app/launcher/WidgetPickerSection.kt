package com.riffle.app.launcher

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal data class WidgetPickerSection(
    val title: String,
    val providers: List<InstalledWidgetProvider>,
) {
    val displayTitle: String = "$title (${providers.size})"
}

internal fun widgetPickerSectionsFor(providers: List<InstalledWidgetProvider>): List<WidgetPickerSection> =
    providers
        .groupBy { provider -> provider.widgetPickerSectionTitle() }
        .map { (title, sectionProviders) ->
            WidgetPickerSection(
                title = title,
                providers = sectionProviders,
            )
        }
        .sortedWith(
            compareBy<WidgetPickerSection> { section -> section.title.widgetPickerProfileSortRank() }
                .thenBy { section -> section.title },
        )

internal fun InstalledWidgetProvider.widgetPickerSectionTitle(): String {
    return identity.profile.drawerProfilePrefix() ?: "Personal"
}

private fun String.widgetPickerProfileSortRank(): Int =
    when (this) {
        "Personal" -> 0
        "Work" -> 1
        "Private" -> 2
        else -> 3
    }

@Composable
internal fun WidgetPickerSectionHeader(title: String) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 2.dp),
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}
