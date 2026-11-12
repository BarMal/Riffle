@file:OptIn(ExperimentalFoundationApi::class)

package com.riffle.app.launcher.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppDrawerRow
import com.riffle.app.launcher.AppDrawerRowOverflowMenu
import com.riffle.app.launcher.AppListContext
import com.riffle.app.launcher.GlassSurface
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.LauncherShellAction
import com.riffle.app.launcher.designsystem.RiffleShapes
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.drawerKey
import com.riffle.app.launcher.drawerRowState
import com.riffle.app.launcher.expressions.AlphaScrubber
import com.riffle.app.launcher.glassSpotlightSource
import com.riffle.app.launcher.launcherIconSize
import com.riffle.app.launcher.launcherPanelContentColor
import com.riffle.app.launcher.launcherPanelSurfaceColor
import com.riffle.app.launcher.rememberGlassSpotlightColor
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.expressions.AlphaIndex
import com.riffle.core.domain.launcher.library.LibraryCategories
import com.riffle.core.domain.launcher.library.LibraryCategory
import kotlinx.coroutines.launch

/** The smallest touch target the Library draws anywhere (design-language: 48 dp). */
internal val LibraryMinTarget = 48.dp

/** The display name of a tile: the platform category's label, or the localised fallback. */
@Composable
internal fun LibraryCategory.displayName(): String =
    if (isOther) {
        stringResource(
            R.string.library_category_other,
        )
    } else {
        key
    }

/**
 * Categories (iOS App Library style): a grid of Regular glass tiles, each previewing its first few icons; tap an icon
 * to launch it, tap the name to open the whole category. Each tile carries the spotlight, lit by its icons.
 */
@Composable
internal fun LibraryCategoriesView(
    categories: List<LibraryCategory>,
    context: AppListContext,
    onOpen: (LibraryCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = RiffleSpacing.xxxl * 4),
        modifier = modifier.fillMaxSize().testTag(LIBRARY_CATEGORIES_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.l),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = RiffleSpacing.s),
    ) {
        items(items = categories, key = { category -> category.key }) { category ->
            LibraryCategoryTile(category = category, context = context, onOpen = { onOpen(category) })
        }
    }
}

@Composable
private fun LibraryCategoryTile(
    category: LibraryCategory,
    context: AppListContext,
    onOpen: () -> Unit,
) {
    val preview = category.apps.take(LibraryCategories.PREVIEW_COUNT)
    val overflow = category.apps.size - preview.size
    val name = category.displayName()
    GlassSurface(
        modifier = Modifier.fillMaxWidth().testTag("$LIBRARY_CATEGORY_TILE_TEST_TAG_PREFIX${category.key}"),
        weight = GlassWeight.Regular,
        shape = RiffleShapes.large,
        tint = launcherPanelSurfaceColor(),
        contentColor = launcherPanelContentColor(),
        spotlight = true,
    ) {
        Column(
            modifier = Modifier.padding(RiffleSpacing.m),
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
        ) {
            preview.chunked(TILE_COLUMNS).forEach { rowApps ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rowApps.forEach { app -> LibraryTileIcon(app = app, context = context) }
                }
            }
            val title = if (overflow > 0) stringResource(R.string.library_category_with_more, name, overflow) else name
            val open = stringResource(R.string.library_open_category, name)
            Text(
                text = if (overflow > 0) "$name  +$overflow" else name,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = LibraryMinTarget)
                        .clickable(onClickLabel = open, role = Role.Button, onClick = onOpen)
                        .padding(vertical = RiffleSpacing.s)
                        .semantics {
                            heading()
                            contentDescription = title
                        },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One previewed app of a tile: tap launches, long-press opens the app's menu; its colour lights the tile. */
@Composable
private fun LibraryTileIcon(
    app: InstalledApp,
    context: AppListContext,
) {
    val menuOpen = remember(app.identity) { mutableStateOf(false) }
    val state = remember(app, context) { app.drawerRowState(context) }
    Box(
        modifier =
            Modifier
                .size(LibraryMinTarget)
                .glassSpotlightSource(
                    key = app.drawerKey,
                    color = rememberGlassSpotlightColor(app.identity, context.appIconLoader),
                )
                .combinedClickable(
                    onClick = { context.onAction(LauncherShellAction.LaunchApp(app.identity)) },
                    onLongClick = {
                        context.haptics.longPress()
                        menuOpen.value = true
                    },
                    onLongClickLabel = "Show ${app.label} actions",
                ),
        contentAlignment = Alignment.Center,
    ) {
        LauncherAppIcon(
            identity = app.identity,
            label = app.label,
            iconLoader = context.appIconLoader,
            modifier = Modifier.launcherIconSize(),
            shape = RiffleShapes.medium,
        )
        AppDrawerRowOverflowMenu(
            state = state,
            isExpanded = menuOpen.value,
            onExpandedChange = { expanded -> menuOpen.value = expanded },
            showButton = false,
            onAction = context.onAction,
        )
    }
}

/** The A-Z list: apps under letter headings, with the scrubber beside it (every letter is also a button). */
@Composable
internal fun LibraryAlphaListView(
    apps: List<InstalledApp>,
    context: AppListContext,
    modifier: Modifier = Modifier,
) {
    val sections = remember(apps) { AlphaIndex.sections(apps) { app -> app.label } }
    val headerIndices = remember(sections) { AlphaIndex.headerIndices(sections) }
    val letters = remember(sections) { sections.map { section -> section.letter } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag(LIBRARY_ALPHA_LIST_TEST_TAG),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
        ) {
            sections.forEach { section ->
                item(key = "letter:${section.letter}") {
                    Text(
                        text = section.letter.toString(),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = RiffleSpacing.s)
                                .semantics { heading() },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(items = section.entries, key = { app -> app.drawerKey }) { app ->
                    AppDrawerRow(
                        state = app.drawerRowState(context),
                        appIconLoader = context.appIconLoader,
                        onAction = context.onAction,
                    )
                }
            }
        }
        AlphaScrubber(
            letters = letters,
            onLetterSelected = { letter ->
                headerIndices[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
            },
            onDragEnd = {},
        )
    }
}

private const val TILE_COLUMNS = 2
internal const val LIBRARY_CATEGORIES_TEST_TAG = "library-categories"
internal const val LIBRARY_CATEGORY_TILE_TEST_TAG_PREFIX = "library-category-"
internal const val LIBRARY_ALPHA_LIST_TEST_TAG = "library-alpha-list"
