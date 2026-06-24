package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

@Composable
fun WidgetPickerDialog(
    providers: List<InstalledWidgetProvider>,
    onAction: (LauncherShellAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onAction(LauncherShellAction.CloseWidgetPicker) },
        title = { Text(text = "Widgets") },
        text = {
            if (providers.isEmpty()) {
                Text(
                    modifier = Modifier.padding(vertical = 24.dp),
                    text = "No widgets available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = WIDGET_PICKER_MAX_HEIGHT_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(
                        items = providers,
                        key = { provider -> provider.widgetPickerKey },
                    ) { provider ->
                        WidgetProviderRow(
                            provider = provider,
                            onAction = onAction,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAction(LauncherShellAction.CloseWidgetPicker) }) {
                Text(text = "Close")
            }
        },
    )
}

@Composable
private fun WidgetProviderRow(
    provider: InstalledWidgetProvider,
    onAction: (LauncherShellAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = provider.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = provider.widgetPickerSummary(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onAction(provider.requestAddWidgetAction()) }) {
            Text(text = "Add")
        }
    }
}

internal fun InstalledWidgetProvider.requestAddWidgetAction(): LauncherShellAction.RequestAddWidget =
    LauncherShellAction.RequestAddWidget(
        provider = identity,
        label = label,
    )

internal fun InstalledWidgetProvider.widgetPickerSummary(): String =
    "${identity.packageName.value} - ${dimensions.minWidthDp}x${dimensions.minHeightDp}dp"

private val InstalledWidgetProvider.widgetPickerKey: String
    get() = "${identity.profile.id.value}:${identity.packageName.value}/${identity.className.value}"

private const val WIDGET_PICKER_MAX_HEIGHT_DP = 420
