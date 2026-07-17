package com.riffle.app.launcher

import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
                providers =
                    sectionProviders.sortedWith(
                        compareBy<InstalledWidgetProvider> { provider -> provider.label.lowercase() }
                            .thenBy { provider -> provider.identity.className.value },
                    ),
            )
        }
        .sortedWith(
            compareBy<WidgetPickerSection> { section -> section.title.widgetPickerProfileSortRank() }
                .thenBy { section -> section.title.widgetPickerAppSortLabel() },
        )

internal fun InstalledWidgetProvider.widgetPickerSectionTitle(): String {
    val profilePrefix = identity.profile.drawerProfilePrefix() ?: "Personal"
    return "$profilePrefix - ${appLabel.trim().ifEmpty { label }}"
}

private fun String.widgetPickerProfileSortRank(): Int =
    when (substringBefore(" - ")) {
        "Personal" -> 0
        "Work" -> 1
        "Private" -> 2
        else -> 3
    }

private fun String.widgetPickerAppSortLabel(): String = substringAfter(" - ").lowercase()

@Composable
internal fun WidgetPickerSectionHeader(
    title: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    TextButton(onClick = { onExpandedChange(!expanded) }) {
        Text(
            text = "$title, ${if (expanded) "collapse" else "expand"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
