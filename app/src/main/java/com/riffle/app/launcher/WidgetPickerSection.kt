package com.riffle.app.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.glance.GlanceWidgets
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

internal data class WidgetPickerSection(
    val key: String,
    val title: String,
    val providers: List<InstalledWidgetProvider>,
    val contentVisibility: AppProfileContentVisibility = AppProfileContentVisibility.VISIBLE,
) {
    val displayTitle: String = "$title (${providers.size})"
}

internal fun widgetPickerSectionsFor(
    providers: List<InstalledWidgetProvider>,
    profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> =
        providers.associate { provider ->
            provider.identity.profile.id to AppProfileContentVisibility.VISIBLE
        },
): List<WidgetPickerSection> =
    providers
        .groupBy { provider -> provider.widgetPickerSectionKey }
        .map { (key, sectionProviders) ->
            WidgetPickerSection(
                key = key,
                title =
                    if (sectionProviders.first().isRiffleGlance) {
                        RIFFLE_SECTION_TITLE
                    } else {
                        sectionProviders.first().widgetPickerSectionTitle()
                    },
                providers =
                    sectionProviders.sortedWith(
                        compareBy<InstalledWidgetProvider> { provider -> provider.label.lowercase() }
                            .thenBy { provider -> provider.identity.className.value },
                    ),
                contentVisibility =
                    profileContentVisibility[sectionProviders.first().identity.profile.id]
                        ?: AppProfileContentVisibility.REDACTED_UNAVAILABLE,
            )
        }
        .sortedWith(
            // Riffle's own widgets lead; then Personal, Work and Private, each by app.
            compareBy<WidgetPickerSection> { section -> if (section.title == RIFFLE_SECTION_TITLE) 0 else 1 }
                .thenBy { section -> section.title.widgetPickerProfileSortRank() }
                .thenBy { section -> section.title.widgetPickerAppSortLabel() },
        )

/** The picker's group of Riffle's own glance widgets (a platform app could never use the reserved package). */
internal const val RIFFLE_SECTION_TITLE = "Riffle"

private val InstalledWidgetProvider.isRiffleGlance: Boolean
    get() = GlanceWidgets.kindOf(identity) != null

internal fun InstalledWidgetProvider.widgetPickerSectionTitle(): String {
    val profilePrefix = identity.profile.drawerProfilePrefix() ?: "Personal"
    return "$profilePrefix - ${appLabel.trim().ifEmpty { label }}"
}

internal val InstalledWidgetProvider.widgetPickerSectionKey: String
    get() = "${identity.profile.id.value}:${identity.profile.type.name}:${identity.packageName.value}"

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
