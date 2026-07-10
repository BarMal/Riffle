package com.riffle.app.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WidgetPickerSurface(
    providers: List<InstalledWidgetProvider>,
    previewImageLoader: WidgetPreviewImageLoader = EmptyWidgetPreviewImageLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filteredProviders = providers.filteredWidgetProviders(query)
    val providerSections = widgetPickerSectionsFor(filteredProviders)

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(WIDGET_PICKER_SCREEN_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Widgets",
                    style = MaterialTheme.typography.headlineMedium,
                )
                TextButton(onClick = { onAction(LauncherShellAction.CloseWidgetPicker) }) {
                    Text(text = "Close")
                }
            }
            AppSearchField(
                modifier = Modifier.fillMaxWidth(),
                query = query,
                onQueryChanged = { value -> query = value },
                label = "Search widgets",
            )
            Text(
                text =
                    widgetPickerResultSummaryText(
                        totalProviderCount = providers.size,
                        resultCount = filteredProviders.size,
                        query = query,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            WidgetPickerContent(
                providers = providers,
                providerSections = providerSections,
                query = query,
                previewImageLoader = previewImageLoader,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun WidgetPickerEmptyMessage(text: String) {
    Text(
        modifier = Modifier.padding(vertical = 24.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WidgetPickerContent(
    providers: List<InstalledWidgetProvider>,
    providerSections: List<WidgetPickerSection>,
    query: String,
    previewImageLoader: WidgetPreviewImageLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    when {
        providers.isEmpty() ->
            WidgetPickerEmptyMessage(text = widgetPickerEmptyMessageText(providers.size, query))

        providerSections.isEmpty() ->
            WidgetPickerEmptyMessage(text = widgetPickerEmptyMessageText(providers.size, query))

        else ->
            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Adaptive(WIDGET_TILE_MIN_WIDTH_DP.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                providerSections.forEach { section ->
                    if (providerSections.size > 1) {
                        item(
                            key = "section:${section.title}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            WidgetPickerSectionHeader(title = section.displayTitle)
                        }
                    }
                    items(
                        items = section.providers,
                        key = { provider -> provider.widgetPickerKey },
                    ) { provider ->
                        WidgetProviderTile(
                            provider = provider,
                            previewImageLoader = previewImageLoader,
                            onAction = onAction,
                        )
                    }
                }
            }
    }
}

@Composable
private fun WidgetProviderTile(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
    onAction: (LauncherShellAction) -> Unit,
) {
    val requestAddWidgetAction = provider.requestAddWidgetAction()

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onAction(requestAddWidgetAction) }
                .padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetProviderPreview(
            provider = provider,
            previewImageLoader = previewImageLoader,
        )
        Text(
            text = provider.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = provider.widgetPickerSummary(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onAction(provider.requestAddWidgetAction(WidgetAddTarget.HOME)) }) {
                Text(text = "Home")
            }
            TextButton(onClick = { onAction(provider.requestAddWidgetAction(WidgetAddTarget.DOCK)) }) {
                Text(text = "Dock")
            }
        }
    }
}

@Composable
private fun WidgetProviderPreview(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
) {
    val preview = rememberWidgetPreview(provider = provider, previewImageLoader = previewImageLoader)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "${provider.label} widget preview",
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(provider.widgetPickerPreviewAspectRatio()),
            )
        } else {
            WidgetProviderPreviewFallback(provider = provider)
        }
    }
}

@Composable
private fun rememberWidgetPreview(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
): ImageBitmap? {
    var preview by remember(provider.identity, previewImageLoader) {
        mutableStateOf(previewImageLoader.cachedPreviewFor(provider.identity))
    }

    LaunchedEffect(provider.identity, previewImageLoader) {
        val cachedPreview = previewImageLoader.cachedPreviewFor(provider.identity)
        preview =
            cachedPreview ?: withContext(Dispatchers.Default) {
                previewImageLoader.previewFor(provider.identity)
            }
    }

    return preview
}

@Composable
private fun WidgetProviderPreviewFallback(provider: InstalledWidgetProvider) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(provider.widgetPickerPreviewAspectRatio())
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = provider.widgetPickerPreviewLabel(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

internal fun InstalledWidgetProvider.requestAddWidgetAction(
    target: WidgetAddTarget = WidgetAddTarget.HOME,
): LauncherShellAction.RequestAddWidget =
    LauncherShellAction.RequestAddWidget(
        provider = identity,
        label = label,
        dimensions = dimensions,
        target = target,
    )

private const val WIDGET_PICKER_SCREEN_PADDING_DP = 20
private const val WIDGET_TILE_MIN_WIDTH_DP = 144
