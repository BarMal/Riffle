@file:Suppress("LongMethod", "LongParameterList", "TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.WidgetProviderCatalogStatus
import com.riffle.core.domain.launcher.apps.AppProfileContentVisibility
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.widgets.InstalledWidgetProvider

@Composable
fun WidgetPickerSurface(
    providers: List<InstalledWidgetProvider>,
    profileContentVisibility: Map<AppProfileId, AppProfileContentVisibility> =
        providers.associate { provider ->
            provider.identity.profile.id to AppProfileContentVisibility.VISIBLE
        },
    catalogStatus: WidgetProviderCatalogStatus = WidgetProviderCatalogStatus.READY,
    previewImageLoader: WidgetPreviewImageLoader = EmptyWidgetPreviewImageLoader,
    accessiblePlacement: WidgetPickerAccessiblePlacement? = null,
    isDragHandoffActive: Boolean = false,
    onWidgetDragStarted: (InstalledWidgetProvider) -> Unit = {},
    onWidgetDragMoved: (InstalledWidgetProvider, Offset, IntSize) -> Unit = { _, _, _ -> },
    onWidgetDragCancelled: (InstalledWidgetProvider) -> Unit = {},
    onWidgetDropped: (InstalledWidgetProvider, Offset, IntSize) -> Unit = { _, _, _ -> },
    onAccessiblePlacementRequested: (InstalledWidgetProvider, WidgetAddTarget) -> Unit = { _, _ -> },
    onAccessiblePlacementSelected: (WidgetPickerAccessiblePlacement) -> Unit = {},
    onAccessiblePlacementConfirmed: () -> Unit = {},
    onAccessiblePlacementCancelled: () -> Unit = {},
    onAction: (LauncherShellAction) -> Unit,
    onRetryRequested: () -> Unit = {},
    onCloseRequested: () -> Unit = { onAction(LauncherShellAction.CloseWidgetPicker) },
) {
    var query by rememberSaveable { mutableStateOf("") }
    var collapsedSectionTitles by rememberSaveable { mutableStateOf("") }
    val filteredProviders = providers.filteredWidgetProviders(query)
    val providerSections = widgetPickerSectionsFor(filteredProviders, profileContentVisibility)
    var rootCoordinates: LayoutCoordinates? by remember { mutableStateOf(null) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag(WIDGET_PICKER_ROOT_TEST_TAG)
                .onGloballyPositioned { coordinates -> rootCoordinates = coordinates }
                .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .widthIn(max = WIDGET_PICKER_MAX_WIDTH_DP.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(WIDGET_PICKER_PANEL_MARGIN_DP.dp),
        ) {
            Surface(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag(WIDGET_PICKER_PANEL_TEST_TAG),
                shape = LocalLauncherPanelShape.current,
                color =
                    if (isDragHandoffActive) {
                        androidx.compose.ui.graphics.Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = WIDGET_PICKER_SURFACE_ALPHA)
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
                border =
                    if (isDragHandoffActive) {
                        null
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.56f))
                    },
                tonalElevation = if (isDragHandoffActive) 0.dp else 6.dp,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .alpha(if (isDragHandoffActive) 0f else 1f)
                            .padding(WIDGET_PICKER_SCREEN_PADDING_DP.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        TextButton(onClick = onCloseRequested) {
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
                    Text(
                        text =
                            if (isDragHandoffActive) {
                                "Release on Home or Dock to place this widget"
                            } else {
                                "Long-press a widget to drag it to Home or Dock"
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (catalogStatus == WidgetProviderCatalogStatus.FAILED && providers.isNotEmpty()) {
                        WidgetPickerProviderReadFailure(onRetryRequested)
                    }
                    accessiblePlacement?.let { placement ->
                        WidgetPickerAccessiblePlacementControls(
                            placement = placement,
                            onSelect = onAccessiblePlacementSelected,
                            onConfirm = onAccessiblePlacementConfirmed,
                            onCancel = onAccessiblePlacementCancelled,
                        )
                    }
                    WidgetPickerContent(
                        providers = providers,
                        catalogStatus = catalogStatus,
                        providerSections = providerSections,
                        query = query,
                        collapsedSectionTitles = collapsedSectionTitles,
                        onCollapsedSectionTitlesChange = { value -> collapsedSectionTitles = value },
                        previewImageLoader = previewImageLoader,
                        onWidgetDragStarted = onWidgetDragStarted,
                        onWidgetDragMoved = onWidgetDragMoved,
                        onWidgetDragCancelled = onWidgetDragCancelled,
                        onWidgetDropped = onWidgetDropped,
                        onAccessiblePlacementRequested = onAccessiblePlacementRequested,
                        rootCoordinates = rootCoordinates,
                        onRetryRequested = onRetryRequested,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
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
    catalogStatus: WidgetProviderCatalogStatus,
    providerSections: List<WidgetPickerSection>,
    query: String,
    collapsedSectionTitles: String,
    onCollapsedSectionTitlesChange: (String) -> Unit,
    previewImageLoader: WidgetPreviewImageLoader,
    onWidgetDragStarted: (InstalledWidgetProvider) -> Unit,
    onWidgetDragMoved: (InstalledWidgetProvider, Offset, IntSize) -> Unit,
    onWidgetDragCancelled: (InstalledWidgetProvider) -> Unit,
    onWidgetDropped: (InstalledWidgetProvider, Offset, IntSize) -> Unit,
    onAccessiblePlacementRequested: (InstalledWidgetProvider, WidgetAddTarget) -> Unit,
    rootCoordinates: LayoutCoordinates?,
    onRetryRequested: () -> Unit,
    modifier: Modifier,
) {
    when {
        catalogStatus == WidgetProviderCatalogStatus.LOADING && providers.isEmpty() ->
            WidgetPickerEmptyMessage(text = "Loading widgets…")

        catalogStatus == WidgetProviderCatalogStatus.FAILED && providers.isEmpty() ->
            WidgetPickerProviderReadFailure(onRetryRequested)

        providers.isEmpty() ->
            WidgetPickerEmptyMessage(text = widgetPickerEmptyMessageText(providers.size, query))

        providerSections.isEmpty() ->
            WidgetPickerEmptyMessage(text = widgetPickerEmptyMessageText(providers.size, query))

        else ->
            LazyVerticalGrid(
                modifier = modifier.fillMaxSize(),
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
                        if (section.contentVisibility != AppProfileContentVisibility.VISIBLE) {
                            item(
                                key = "profile-state:${section.key}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                WidgetPickerProfileState(section.contentVisibility)
                            }
                        }
                        items(
                            items = section.providers,
                            key = { provider -> provider.widgetPickerKey },
                        ) { provider ->
                            WidgetProviderTile(
                                provider = provider,
                                contentVisibility = section.contentVisibility,
                                previewImageLoader = previewImageLoader,
                                onWidgetDragStarted = onWidgetDragStarted,
                                onWidgetDragMoved = onWidgetDragMoved,
                                onWidgetDragCancelled = onWidgetDragCancelled,
                                onWidgetDropped = onWidgetDropped,
                                onAccessiblePlacementRequested = onAccessiblePlacementRequested,
                                rootCoordinates = rootCoordinates,
                            )
                        }
                    }
                }
            }
    }
}

@Composable
private fun WidgetPickerProviderReadFailure(onRetryRequested: () -> Unit) {
    Column(
        modifier = Modifier.padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Widgets couldn’t be loaded",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetryRequested) {
            Text("Try again")
        }
    }
}

@Composable
private fun WidgetPickerProfileState(contentVisibility: AppProfileContentVisibility) {
    Text(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        text = widgetPickerProfileStateText(contentVisibility),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun widgetPickerProfileStateText(contentVisibility: AppProfileContentVisibility): String =
    when (contentVisibility) {
        AppProfileContentVisibility.VISIBLE -> ""
        AppProfileContentVisibility.REDACTED_QUIET ->
            "This profile is paused. Turn it on to preview or place its widgets."

        AppProfileContentVisibility.REDACTED_LOCKED ->
            "This profile is locked. Unlock it to preview or place its widgets."

        AppProfileContentVisibility.REDACTED_UNAVAILABLE ->
            "This profile is unavailable. Its widgets can’t be previewed or placed."
    }

@Composable
private fun WidgetProviderTile(
    provider: InstalledWidgetProvider,
    contentVisibility: AppProfileContentVisibility,
    previewImageLoader: WidgetPreviewImageLoader,
    onWidgetDragStarted: (InstalledWidgetProvider) -> Unit,
    onWidgetDragMoved: (InstalledWidgetProvider, Offset, IntSize) -> Unit,
    onWidgetDragCancelled: (InstalledWidgetProvider) -> Unit,
    onWidgetDropped: (InstalledWidgetProvider, Offset, IntSize) -> Unit,
    onAccessiblePlacementRequested: (InstalledWidgetProvider, WidgetAddTarget) -> Unit,
    rootCoordinates: LayoutCoordinates?,
) {
    val isAvailable = contentVisibility == AppProfileContentVisibility.VISIBLE
    val summary = provider.widgetPickerSummary()
    val currentOnWidgetDragStarted by rememberUpdatedState(onWidgetDragStarted)
    val currentOnWidgetDragMoved by rememberUpdatedState(onWidgetDragMoved)
    val currentOnWidgetDragCancelled by rememberUpdatedState(onWidgetDragCancelled)
    val currentOnWidgetDropped by rememberUpdatedState(onWidgetDropped)
    val currentRootCoordinates by rememberUpdatedState(rootCoordinates)
    var coordinates: LayoutCoordinates? by remember { mutableStateOf(null) }
    var dropPosition by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 2.dp)
                .testTag(WIDGET_PROVIDER_TILE_TEST_TAG)
                .semantics {
                    role = Role.Image
                    if (isAvailable) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("Add ${provider.label} to Home") {
                                    onAccessiblePlacementRequested(provider, WidgetAddTarget.HOME)
                                    true
                                },
                                CustomAccessibilityAction("Add ${provider.label} to Dock") {
                                    onAccessiblePlacementRequested(provider, WidgetAddTarget.DOCK)
                                    true
                                },
                            )
                    } else {
                        disabled()
                    }
                }
                .onGloballyPositioned { layoutCoordinates -> coordinates = layoutCoordinates }
                .pointerInput(provider.widgetPickerKey, isAvailable) {
                    if (!isAvailable) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dropPosition = offset
                            currentOnWidgetDragStarted(provider)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dropPosition = change.position
                            val tileCoordinates = coordinates
                            val dragRootCoordinates = currentRootCoordinates
                            if (tileCoordinates != null && dragRootCoordinates != null) {
                                currentOnWidgetDragMoved(
                                    provider,
                                    dragRootCoordinates.localPositionOf(tileCoordinates, dropPosition),
                                    dragRootCoordinates.size,
                                )
                            }
                        },
                        onDragEnd = {
                            val tileCoordinates = coordinates
                            val dragRootCoordinates = currentRootCoordinates
                            if (tileCoordinates != null && dragRootCoordinates != null) {
                                currentOnWidgetDropped(
                                    provider,
                                    dragRootCoordinates.localPositionOf(tileCoordinates, dropPosition),
                                    dragRootCoordinates.size,
                                )
                            } else {
                                currentOnWidgetDragCancelled(provider)
                            }
                        },
                        onDragCancel = {
                            currentOnWidgetDragCancelled(provider)
                        },
                    )
                },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetProviderPreview(
            provider = provider,
            previewImageLoader = previewImageLoader,
            loadPreview = isAvailable,
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
        WidgetProviderAddMenu(
            provider = provider,
            enabled = isAvailable,
            onAccessiblePlacementRequested = onAccessiblePlacementRequested,
        )
    }
}

@Composable
private fun WidgetPickerAccessiblePlacementControls(
    placement: WidgetPickerAccessiblePlacement,
    onSelect: (WidgetPickerAccessiblePlacement) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    var pageMenuExpanded by remember { mutableStateOf(false) }
    var positionMenuExpanded by remember { mutableStateOf(false) }
    val selected = placement.selectedCandidate
    val pageCandidates = placement.candidates.filter { candidate -> candidate.pageId != null }.distinctBy { it.pageId }
    val positionCandidates =
        placement.candidates.filter { candidate ->
            placement.target == WidgetAddTarget.DOCK || candidate.pageId == selected?.pageId
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Placement preview", style = MaterialTheme.typography.titleMedium)
            Text(
                text = placement.accessiblePlacementAnnouncement(),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (placement.target == WidgetAddTarget.HOME) {
                    Box {
                        TextButton(onClick = { pageMenuExpanded = true }) {
                            Text("Page ${selected?.pageId?.value ?: "unavailable"}")
                        }
                        DropdownMenu(
                            expanded = pageMenuExpanded,
                            onDismissRequest = { pageMenuExpanded = false },
                        ) {
                            pageCandidates.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text("Page ${candidate.pageId?.value}") },
                                    onClick = {
                                        pageMenuExpanded = false
                                        onSelect(placement.selectCandidate(candidate))
                                    },
                                )
                            }
                        }
                    }
                }
                Box {
                    TextButton(
                        onClick = { positionMenuExpanded = true },
                        enabled = positionCandidates.isNotEmpty(),
                    ) {
                        Text(selected?.placementPositionLabel() ?: "Position unavailable")
                    }
                    DropdownMenu(
                        expanded = positionMenuExpanded,
                        onDismissRequest = { positionMenuExpanded = false },
                    ) {
                        positionCandidates.forEach { candidate ->
                            DropdownMenuItem(
                                text = { Text(candidate.placementPositionLabel()) },
                                onClick = {
                                    positionMenuExpanded = false
                                    onSelect(placement.selectCandidate(candidate))
                                },
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onConfirm, enabled = placement.isValid) {
                    Text("Place")
                }
                TextButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

private fun WidgetPickerPlacementCandidate.placementPositionLabel(): String =
    when {
        dockIndex != null -> "Dock position ${dockIndex + 1}"
        cell != null -> "Column ${cell.column + 1}, row ${cell.row + 1}"
        else -> "Position unavailable"
    }

internal fun WidgetPickerAccessiblePlacement.accessiblePlacementAnnouncement(): String =
    when {
        !isValid -> "${provider.label} cannot be placed at the selected ${target.name.lowercase()} target."
        target == WidgetAddTarget.HOME && selectedCandidate?.pageId != null ->
            "${provider.label} on Home page ${selectedCandidate?.pageId?.value}, " +
                "${selectedCandidate?.placementPositionLabel()}; placement is ready."
        target == WidgetAddTarget.DOCK ->
            "${provider.label} in the Dock at ${selectedCandidate?.placementPositionLabel()}; placement is ready."
        else -> "${provider.label} placement is ready."
    }

@Composable
private fun WidgetProviderAddMenu(
    provider: InstalledWidgetProvider,
    enabled: Boolean,
    onAccessiblePlacementRequested: (InstalledWidgetProvider, WidgetAddTarget) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { menuExpanded = true }, enabled = enabled) {
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
                    onAccessiblePlacementRequested(provider, WidgetAddTarget.HOME)
                },
            )
            DropdownMenuItem(
                text = { Text("Choose Dock position") },
                onClick = {
                    menuExpanded = false
                    onAccessiblePlacementRequested(provider, WidgetAddTarget.DOCK)
                },
            )
        }
    }
}

@Composable
private fun WidgetProviderPreview(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
    loadPreview: Boolean,
) {
    val preview =
        if (loadPreview) {
            rememberWidgetPreview(provider = provider, previewImageLoader = previewImageLoader)
        } else {
            null
        }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val previewSize =
            widgetPickerPreviewSize(
                maxWidth = maxWidth,
                preferredAspectRatio = provider.widgetPickerPreviewAspectRatio(),
            )
        val previewIsConstrained =
            widgetPickerPreviewIsConstrained(
                maxWidth = maxWidth,
                preferredAspectRatio = provider.widgetPickerPreviewAspectRatio(),
            )

        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "${provider.label} widget preview",
                contentScale = ContentScale.Fit,
                modifier = Modifier.widgetPickerPreviewBounds(previewSize),
            )
        } else {
            WidgetProviderPreviewFallback(
                provider = provider,
                previewSize = previewSize,
                isConstrained = previewIsConstrained,
            )
        }
    }
}

@Composable
private fun rememberWidgetPreview(
    provider: InstalledWidgetProvider,
    previewImageLoader: WidgetPreviewImageLoader,
): ImageBitmap? {
    val previewRevision = previewImageLoader.previewRevision
    var preview by remember(provider.identity, previewImageLoader, previewRevision) {
        mutableStateOf(previewImageLoader.cachedPreviewForOrNull(provider.identity))
    }

    LaunchedEffect(provider.identity, previewImageLoader, previewRevision) {
        val cachedPreview = previewImageLoader.cachedPreviewForOrNull(provider.identity)
        preview =
            cachedPreview ?: previewImageLoader.previewForOrNull(provider.identity)
    }

    return preview
}

@Composable
private fun WidgetProviderPreviewFallback(
    provider: InstalledWidgetProvider,
    previewSize: DpSize,
    isConstrained: Boolean,
) {
    Box(
        modifier =
            Modifier
                .widgetPickerPreviewBounds(previewSize)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text =
                if (isConstrained) {
                    "Preview unavailable"
                } else {
                    provider.widgetPickerPreviewLabel()
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

private fun Modifier.widgetPickerPreviewBounds(previewSize: DpSize): Modifier =
    size(previewSize)
        .clip(RoundedCornerShape(12.dp))
        .testTag(WIDGET_PICKER_PREVIEW_TEST_TAG)

internal fun widgetPickerPreviewSize(
    maxWidth: Dp,
    preferredAspectRatio: Float,
    maxHeight: Dp = WIDGET_PREVIEW_MAX_HEIGHT_DP.dp,
): DpSize {
    val aspectRatio = preferredAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val width = maxWidth
    val height = width / aspectRatio
    if (widgetPickerPreviewIsConstrained(maxWidth, aspectRatio, maxHeight)) {
        return DpSize(
            width = width.coerceAtMost(WIDGET_PREVIEW_CONSTRAINED_WIDTH_DP.dp),
            height = maxHeight.coerceAtMost(WIDGET_PREVIEW_CONSTRAINED_HEIGHT_DP.dp),
        )
    }
    return if (height <= maxHeight) {
        DpSize(width = width, height = height)
    } else {
        DpSize(width = maxHeight * aspectRatio, height = maxHeight)
    }
}

internal fun widgetPickerPreviewIsConstrained(
    maxWidth: Dp,
    preferredAspectRatio: Float,
    maxHeight: Dp = WIDGET_PREVIEW_MAX_HEIGHT_DP.dp,
    minimumDimension: Dp = WIDGET_PREVIEW_MIN_LEGIBLE_DIMENSION_DP.dp,
): Boolean {
    val aspectRatio = preferredAspectRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
    val naturalHeight = maxWidth / aspectRatio
    val naturalWidth = if (naturalHeight <= maxHeight) maxWidth else maxHeight * aspectRatio
    return naturalWidth < minimumDimension || naturalHeight < minimumDimension
}

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
private const val WIDGET_PICKER_PANEL_MARGIN_DP = 12
private const val WIDGET_PICKER_MAX_WIDTH_DP = 840
private const val WIDGET_TILE_MIN_WIDTH_DP = 220
private const val WIDGET_PICKER_SURFACE_ALPHA = 0.76f
private const val WIDGET_PREVIEW_MAX_HEIGHT_DP = 240
private const val WIDGET_PREVIEW_MIN_LEGIBLE_DIMENSION_DP = 48
private const val WIDGET_PREVIEW_CONSTRAINED_WIDTH_DP = 180
private const val WIDGET_PREVIEW_CONSTRAINED_HEIGHT_DP = 96
internal const val WIDGET_PICKER_PREVIEW_TEST_TAG = "widget-picker-preview"
internal const val WIDGET_PICKER_ROOT_TEST_TAG = "widget-picker-root"
internal const val WIDGET_PICKER_PANEL_TEST_TAG = "widget-picker-panel"
internal const val WIDGET_PROVIDER_TILE_TEST_TAG = "widget-provider-tile"
private const val WIDGET_PICKER_SECTION_STATE_SEPARATOR = "\u001f"

private fun String.toCollapsedWidgetPickerSections(): Set<String> =
    split(WIDGET_PICKER_SECTION_STATE_SEPARATOR)
        .filter(String::isNotEmpty)
        .toSet()
