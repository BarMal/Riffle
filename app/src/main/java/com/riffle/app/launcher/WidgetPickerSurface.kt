@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun WidgetPickerSurface(
    providers: List<InstalledWidgetProvider>,
    previewImageLoader: WidgetPreviewImageLoader = EmptyWidgetPreviewImageLoader,
    isDragHandoffActive: Boolean = false,
    onWidgetDragStarted: (InstalledWidgetProvider) -> Unit = {},
    onWidgetDropped: (InstalledWidgetProvider, Offset, IntSize) -> Unit = { _, _, _ -> },
    onAction: (LauncherShellAction) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var collapsedSectionTitles by rememberSaveable { mutableStateOf("") }
    val filteredProviders = providers.filteredWidgetProviders(query)
    val providerSections = widgetPickerSectionsFor(filteredProviders)

    Surface(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        color =
            if (isDragHandoffActive) {
                androidx.compose.ui.graphics.Color.Transparent
            } else {
                MaterialTheme.colorScheme.surface
            },
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(if (isDragHandoffActive) 0f else 1f)
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
                collapsedSectionTitles = collapsedSectionTitles,
                onCollapsedSectionTitlesChange = { value -> collapsedSectionTitles = value },
                previewImageLoader = previewImageLoader,
                onAction = onAction,
                onWidgetDragStarted = onWidgetDragStarted,
                onWidgetDropped = onWidgetDropped,
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

@Suppress("LongParameterList")
@Composable
private fun WidgetPickerContent(
    providers: List<InstalledWidgetProvider>,
    providerSections: List<WidgetPickerSection>,
    query: String,
    collapsedSectionTitles: String,
    onCollapsedSectionTitlesChange: (String) -> Unit,
    previewImageLoader: WidgetPreviewImageLoader,
    onAction: (LauncherShellAction) -> Unit,
    onWidgetDragStarted: (InstalledWidgetProvider) -> Unit,
    onWidgetDropped: (InstalledWidgetProvider, Offset, IntSize) -> Unit,
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
                val collapsedSections = collapsedSectionTitles.toCollapsedWidgetPickerSections()
                providerSections.forEach { section ->
                    val isExpanded = section.key !in collapsedSections
                    item(
                        key = "section:${section.key}",
                        span = { GridItemSpan(maxLineSpan) },
                    ) {
                        WidgetPickerSectionHeader(
                            title = section.displayTitle,
                            expanded = isExpanded,
                            onExpandedChange = { expanded ->
                                onCollapsedSectionTitlesChange(
                                    collapsedSections
                                        .toMutableSet()
                                        .apply {
                                            if (expanded) remove(section.key) else add(section.key)
                                        }.joinToString(WIDGET_PICKER_SECTION_STATE_SEPARATOR),
                                )
                            },
                        )
                    }
                    if (isExpanded) {
                        items(
                            items = section.providers,
                            key = { provider -> provider.widgetPickerKey },
                        ) { provider ->
                            WidgetProviderTile(
                                provider = provider,
                                previewImageLoader = previewImageLoader,
                                onAction = onAction,
                                onWidgetDragStarted = onWidgetDragStarted,
                                onWidgetDropped = onWidgetDropped,
                            )
                        }
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
    onWidgetDragStarted: (InstalledWidgetProvider) -> Unit,
    onWidgetDropped: (InstalledWidgetProvider, Offset, IntSize) -> Unit,
) {
    val summary = provider.widgetPickerSummary()
    val currentOnWidgetDragStarted by rememberUpdatedState(onWidgetDragStarted)
    val currentOnWidgetDropped by rememberUpdatedState(onWidgetDropped)
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    var dropPosition by remember { mutableStateOf(Offset.Zero) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val rootSize =
        with(density) {
            IntSize(configuration.screenWidthDp.dp.roundToPx(), configuration.screenHeightDp.dp.roundToPx())
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
                .onGloballyPositioned { layoutCoordinates -> coordinates = layoutCoordinates }
                .pointerInput(provider.widgetPickerKey) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dropPosition = offset
                            currentOnWidgetDragStarted(provider)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dropPosition = change.position
                        },
                        onDragEnd = {
                            coordinates?.let { layoutCoordinates ->
                                currentOnWidgetDropped(
                                    provider,
                                    layoutCoordinates.positionInRoot() + dropPosition,
                                    rootSize,
                                )
                            }
                        },
                    )
                },
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
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        WidgetProviderAddMenu(provider = provider, onAction = onAction)
    }
}

@Composable
private fun WidgetProviderAddMenu(
    provider: InstalledWidgetProvider,
    onAction: (LauncherShellAction) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { menuExpanded = true }) {
            Text(text = "Add ${provider.label}")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Choose Home position") },
                onClick = {
                    menuExpanded = false
                    onAction(provider.requestAddWidgetAction(WidgetAddTarget.HOME))
                },
            )
            DropdownMenuItem(
                text = { Text("Choose Dock position") },
                onClick = {
                    menuExpanded = false
                    onAction(provider.requestAddWidgetAction(WidgetAddTarget.DOCK))
                },
            )
        }
    }
}

@Composable
private fun WidgetProviderPreview(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
) {
    val preview = rememberWidgetPreview(provider = provider, previewImageLoader = previewImageLoader)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val previewAspectRatio = provider.widgetPickerPreviewAspectRatio().boundedFor(maxWidth)

        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "${provider.label} widget preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.widgetPickerPreviewBounds(previewAspectRatio),
            )
        } else {
            WidgetProviderPreviewFallback(
                provider = provider,
                previewAspectRatio = previewAspectRatio,
            )
        }
    }
}

@Composable
private fun rememberWidgetPreview(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
): ImageBitmap? {
    var preview by remember(provider.identity, previewImageLoader) {
        mutableStateOf(previewImageLoader.cachedPreviewForOrNull(provider.identity))
    }

    LaunchedEffect(provider.identity, previewImageLoader) {
        val cachedPreview = previewImageLoader.cachedPreviewForOrNull(provider.identity)
        preview =
            cachedPreview ?: withContext(Dispatchers.Default) {
                previewImageLoader.previewForOrNull(provider.identity)
            }
    }

    return preview
}

@Composable
private fun WidgetProviderPreviewFallback(
    provider: InstalledWidgetProvider,
    previewAspectRatio: Float,
) {
    Box(
        modifier =
            Modifier
                .widgetPickerPreviewBounds(previewAspectRatio)
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

private fun Modifier.widgetPickerPreviewBounds(previewAspectRatio: Float): Modifier =
    fillMaxWidth()
        .aspectRatio(previewAspectRatio)
        .clip(RoundedCornerShape(12.dp))
        .testTag(WIDGET_PICKER_PREVIEW_TEST_TAG)

private fun Float.boundedFor(maxWidth: Dp): Float =
    coerceIn(
        minimumValue = maxWidth / WIDGET_PREVIEW_MAX_HEIGHT_DP.dp,
        maximumValue = maxWidth / WIDGET_PREVIEW_MIN_HEIGHT_DP.dp,
    )

internal fun InstalledWidgetProvider.requestAddWidgetAction(
    target: WidgetAddTarget = WidgetAddTarget.HOME,
): LauncherShellAction.RequestAddWidget =
    LauncherShellAction.RequestAddWidget(
        provider = identity,
        label = label,
        dimensions = dimensions,
        supportsHorizontalResize = supportsHorizontalResize,
        supportsVerticalResize = supportsVerticalResize,
        target = target,
    )

private const val WIDGET_PICKER_SCREEN_PADDING_DP = 20
private const val WIDGET_TILE_MIN_WIDTH_DP = 144
private const val WIDGET_PREVIEW_MIN_HEIGHT_DP = 72
private const val WIDGET_PREVIEW_MAX_HEIGHT_DP = 240
internal const val WIDGET_PICKER_PREVIEW_TEST_TAG = "widget-picker-preview"
private const val WIDGET_PICKER_SECTION_STATE_SEPARATOR = "\u001f"

private fun String.toCollapsedWidgetPickerSections(): Set<String> =
    split(WIDGET_PICKER_SECTION_STATE_SEPARATOR)
        .filter(String::isNotEmpty)
        .toSet()
