package com.riffle.app.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.R
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.app.launcher.icons.ChangeIconTarget
import com.riffle.app.launcher.icons.InstalledIconPack
import com.riffle.app.launcher.icons.LocalAppIconPipeline
import com.riffle.app.launcher.icons.rememberLoaded
import com.riffle.core.domain.launcher.designsystem.GlassWeight
import com.riffle.core.domain.launcher.icons.IconPackDrawableRef
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val CHANGE_ICON_TEST_TAG = "change-icon"
internal const val CHANGE_ICON_SEARCH_TEST_TAG = "change-icon-search"
internal const val CHANGE_ICON_NAME_TEST_TAG = "change-icon-name"
internal const val CHANGE_ICON_RESET_TEST_TAG = "change-icon-reset"
internal const val CHANGE_ICON_CHOICE_TEST_TAG_PREFIX = "change-icon-choice:"

private const val ICON_CELL_DP = 64
private const val ICON_CELL_PX = 128
private const val PAGE_SIZE = 90

/**
 * "Change icon" (an app's long-press menu): choose another icon for this app from the icons of the current pack or of
 * another installed pack (a searchable grid, loaded a page at a time and each icon only as it scrolls into view), go
 * back to the style's own icon, or give the app another name. Drawn like the other sheets: Thick glass over Home.
 * What is stored is a reference (the pack and the drawable's name), never a bitmap.
 */
@OptIn(ExperimentalLayoutApi::class)
@Suppress("LongMethod")
@Composable
internal fun ChangeIconSurface(
    target: ChangeIconTarget,
    style: IconStyleSettings,
    onAction: (LauncherShellAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val pipeline = LocalAppIconPipeline.current ?: remember(context) { AppIconPipeline.shared(context) }
    val override = style.overrideFor(target.identity)
    var name by remember(target) { mutableStateOf(override?.label ?: target.label) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 640.dp).testTag(CHANGE_ICON_TEST_TAG),
            weight = GlassWeight.Thick,
            shape = LocalLauncherPanelShape.current,
            tint = launcherPanelSurfaceColor(),
            contentColor = launcherPanelContentColor(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.change_icon_title, target.label),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                }
                NameField(
                    name = name,
                    onNameChange = { name = it },
                    renamed = override?.label != null,
                    onSave = {
                        onAction(
                            LauncherShellAction.SetAppLabelOverride(
                                target.identity,
                                name.takeIf { it != target.label },
                            ),
                        )
                    },
                    onReset = {
                        name = target.label
                        onAction(LauncherShellAction.SetAppLabelOverride(target.identity, null))
                    },
                )
                IconPackChooser(
                    pipeline = pipeline,
                    preferredPack = override?.icon?.packPackage ?: style.iconPack,
                    selected = override?.icon,
                    onChoose = { ref ->
                        onAction(LauncherShellAction.SetAppIconOverride(target.identity, ref))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f, fill = false),
                )
                TextButton(
                    onClick = {
                        onAction(LauncherShellAction.SetAppIconOverride(target.identity, null))
                        onDismiss()
                    },
                    enabled = override?.icon != null,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(CHANGE_ICON_RESET_TEST_TAG),
                ) { Text(stringResource(R.string.change_icon_reset)) }
            }
        }
    }
}

/**
 * The installed icon packs as chips, a search field and the chosen pack's icons as a grid; [onChoose] gets the tapped
 * icon. Opens on [preferredPack] when it is installed, else the first pack. Shared by every "choose an icon" surface.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconPackChooser(
    pipeline: AppIconPipeline,
    preferredPack: String?,
    selected: IconPackDrawableRef?,
    onChoose: (IconPackDrawableRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val packs by rememberLoaded(emptyList<InstalledIconPack>(), pipeline, pipeline.packs.packListVersion) {
        pipeline.packs.installedPacks()
    }
    var browsing by remember(packs) {
        mutableStateOf(
            preferredPack?.takeIf { p -> packs.any { it.packageName == p } } ?: packs.firstOrNull()?.packageName,
        )
    }
    var query by remember { mutableStateOf("") }
    if (packs.isEmpty()) {
        Text(text = stringResource(R.string.icon_pack_none_installed), style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            packs.forEach { pack ->
                FilterChip(
                    selected = pack.packageName == browsing,
                    onClick = { browsing = pack.packageName },
                    label = { Text(pack.label) },
                    modifier = Modifier.heightIn(min = 48.dp).semantics { role = Role.RadioButton },
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().testTag(CHANGE_ICON_SEARCH_TEST_TAG),
            singleLine = true,
            label = { Text(stringResource(R.string.change_icon_search)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        browsing?.let { pack ->
            PackIconGrid(
                pipeline = pipeline,
                pack = pack,
                query = query,
                selected = selected,
                onChoose = onChoose,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** An icon-pack drawable decoded off the main thread at [sizePx]; null until it is ready or if the pack lost it. */
@Composable
internal fun rememberPackIcon(
    pipeline: AppIconPipeline,
    ref: IconPackDrawableRef,
    sizePx: Int,
): ImageBitmap? {
    val icon by rememberLoaded<ImageBitmap?>(null, ref, sizePx) {
        pipeline.packs.drawable(ref.packPackage, ref.drawableName)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
    }
    return icon
}

@Composable
private fun NameField(
    name: String,
    onNameChange: (String) -> Unit,
    renamed: Boolean,
    onSave: () -> Unit,
    onReset: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            modifier = Modifier.weight(1f).testTag(CHANGE_ICON_NAME_TEST_TAG),
            singleLine = true,
            label = { Text(stringResource(R.string.change_icon_name)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        TextButton(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.menu_rename))
        }
        if (renamed) {
            TextButton(onClick = onReset, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.change_icon_reset_name))
            }
        }
    }
}

/** The pack's icons that match [query], a page at a time; each cell decodes its drawable only when it is composed. */
@Composable
private fun PackIconGrid(
    pipeline: AppIconPipeline,
    pack: String,
    query: String,
    selected: IconPackDrawableRef?,
    onChoose: (IconPackDrawableRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The pack's list is read off the main thread the first time it is browsed.
    var names by remember(pack, pipeline.packs.catalogVersion) { mutableStateOf(pipeline.packs.drawableNames(pack)) }
    LaunchedEffect(pack) {
        withContext(Dispatchers.Default) { pipeline.packs.watch(setOf(pack), selected = pack) }
        names = pipeline.packs.drawableNames(pack)
    }
    val matching = remember(names, query) { names.filter { it.contains(query.trim(), ignoreCase = true) } }
    var shown by remember(pack, query) { mutableIntStateOf(PAGE_SIZE) }
    val state = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf { state.firstVisibleItemIndex + state.layoutInfo.visibleItemsInfo.size >= shown - 12 }
    }
    LaunchedEffect(nearEnd, matching) { if (nearEnd && shown < matching.size) shown += PAGE_SIZE }
    if (matching.isEmpty()) {
        Text(
            text = stringResource(R.string.change_icon_no_icons),
            style = MaterialTheme.typography.bodyMedium,
            modifier = modifier.padding(vertical = 8.dp),
        )
        return
    }
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(ICON_CELL_DP.dp),
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(matching.take(shown), key = { it }) { drawableName ->
            val ref = IconPackDrawableRef(pack, drawableName)
            PackIconCell(pipeline = pipeline, ref = ref, selected = ref == selected, onClick = { onChoose(ref) })
        }
    }
}

@Composable
private fun PackIconCell(
    pipeline: AppIconPipeline,
    ref: IconPackDrawableRef,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val icon = rememberPackIcon(pipeline, ref, ICON_CELL_PX)
    val description = stringResource(R.string.change_icon_choice_description, ref.drawableName)
    Box(
        modifier =
            Modifier
                .size(ICON_CELL_DP.dp)
                .testTag(CHANGE_ICON_CHOICE_TEST_TAG_PREFIX + ref.drawableName)
                .semantics {
                    contentDescription = description
                    role = Role.Button
                }
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(if (selected) 56.dp else 48.dp),
            )
        }
    }
}
