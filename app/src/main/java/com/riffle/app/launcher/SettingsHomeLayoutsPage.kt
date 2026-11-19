@file:Suppress("TooManyFunctions")

package com.riffle.app.launcher

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.savedhome.SavedHomeActivation
import com.riffle.app.launcher.savedhome.SavedHomeLayoutsController
import com.riffle.core.domain.launcher.home.saved.SavedHomeChange
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayout
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayoutKind
import com.riffle.core.domain.launcher.home.saved.SavedHomeLayouts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** The saved Home layouts' actions for the Settings pages; null where the shell provides none (previews). */
internal val LocalSavedHomeLayouts = staticCompositionLocalOf<SavedHomeLayoutsController?> { null }

internal const val HOME_LAYOUTS_SAVE_TEST_TAG = "home-layouts-save"
internal const val HOME_LAYOUTS_NAME_FIELD_TEST_TAG = "home-layouts-name"
internal const val HOME_LAYOUTS_DIALOG_CONFIRM_TEST_TAG = "home-layouts-confirm"

private val NoLayouts = MutableStateFlow(SavedHomeLayouts())

/** What a row's menu asked for, held until the dialog that confirms or names it is answered. */
private sealed interface LayoutRequest {
    data object Save : LayoutRequest

    data class Activate(val layout: SavedHomeLayout) : LayoutRequest

    data class Rename(val layout: SavedHomeLayout) : LayoutRequest

    data class Replace(val layout: SavedHomeLayout) : LayoutRequest

    data class Delete(val layout: SavedHomeLayout) : LayoutRequest
}

/**
 * Settings > Home > Home layouts: the arrangements of Home the user saved on this device class, with Save current Home.
 * A saved layout is a snapshot, not a live link. Every action is a button or a menu item (nothing needs a long press),
 * and the ones that replace something ask first. Off in Simple mode, where Home is generated.
 */
@Composable
internal fun SettingsHomeLayoutsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    SettingsPostureTabs(state = state, onAction = onAction)
    val controller = LocalSavedHomeLayouts.current
    val all by (controller?.layouts ?: NoLayouts).collectAsState()
    val deviceClass = state.selectedLayoutDeviceClass
    val layouts = all.forDeviceClass(deviceClass)
    val simple = state.settings.features.simpleMode
    val snackbar = LocalSettingsSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val messages = HomeLayoutMessages.rememberMessages()
    var request by remember { mutableStateOf<LayoutRequest?>(null) }
    val full = all.isFull(deviceClass)

    SettingsSection(title = stringResource(R.string.settings_section_home_layouts_save)) {
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.HOME_LAYOUTS_SAVE).testTag(HOME_LAYOUTS_SAVE_TEST_TAG),
            title = SettingKey.HOME_LAYOUTS_SAVE.label(),
            subtitle =
                when {
                    simple -> stringResource(R.string.settings_feature_off_in_simple_mode)
                    full ->
                        stringResource(
                            R.string.settings_home_layouts_save_full,
                            SavedHomeLayouts.MAX_PER_DEVICE_CLASS,
                        )
                    else -> stringResource(R.string.settings_home_layouts_save_subtitle)
                },
            enabled = controller != null && !simple && !full,
            trailingContent = {
                SettingsButtonText(text = stringResource(R.string.settings_home_layouts_save_action))
            },
            onClick = { request = LayoutRequest.Save },
        )
        SettingsListRow(title = stringResource(R.string.settings_home_layouts_snapshot_note))
    }
    SettingsSection(
        title = stringResource(R.string.settings_section_home_layouts_saved),
        modifier = Modifier.settingAnchor(SettingKey.HOME_LAYOUTS_LIST),
    ) {
        if (layouts.isEmpty()) {
            SettingsListRow(title = stringResource(R.string.settings_home_layouts_empty))
        }
        layouts.forEach { layout ->
            SavedLayoutRow(layout = layout, enabled = controller != null && !simple, onRequest = { request = it })
        }
    }

    request?.let { shown ->
        LayoutRequestDialog(
            request = shown,
            defaultName = defaultNameFor(shown, layouts),
            onDismiss = { request = null },
            onConfirm = { name ->
                request = null
                if (controller != null) {
                    scope.run(shown, name, controller, snackbar, messages)
                }
            },
        )
    }
}

@Composable
private fun defaultNameFor(
    request: LayoutRequest,
    layouts: List<SavedHomeLayout>,
): String {
    val resources = LocalContext.current.resources
    return when (request) {
        LayoutRequest.Save ->
            SavedHomeLayouts.nextName(layouts.map { it.name }) { n ->
                resources.getString(R.string.home_layout_default_name, n)
            }
        is LayoutRequest.Rename -> request.layout.name
        else -> ""
    }
}

@Composable
private fun SavedLayoutRow(
    layout: SavedHomeLayout,
    enabled: Boolean,
    onRequest: (LayoutRequest) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val request: (LayoutRequest) -> Unit = {
        expanded = false
        onRequest(it)
    }
    val automatic = layout.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION
    val pages = pluralStringResource(R.plurals.home_layout_pages, layout.pageCount, layout.pageCount)
    val items = pluralStringResource(R.plurals.home_layout_items, layout.itemCount, layout.itemCount)
    SettingsClickableRow(
        title = layout.name,
        subtitle =
            if (automatic) {
                stringResource(
                    R.string.home_layout_row_summary_automatic,
                    stringResource(R.string.home_layout_automatic_label),
                    pages,
                    items,
                )
            } else {
                stringResource(R.string.home_layout_row_summary, pages, items)
            },
        enabled = enabled,
        onClick = { expanded = true },
        trailingContent = {
            Box {
                IconButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.home_layout_options_description, layout.name),
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    LayoutMenuItem(R.string.home_layout_activate) { request(LayoutRequest.Activate(layout)) }
                    LayoutMenuItem(R.string.home_layout_rename) { request(LayoutRequest.Rename(layout)) }
                    if (!automatic) {
                        LayoutMenuItem(R.string.home_layout_replace) { request(LayoutRequest.Replace(layout)) }
                    }
                    LayoutMenuItem(R.string.home_layout_delete) { request(LayoutRequest.Delete(layout)) }
                }
            }
        },
    )
}

@Composable
private fun LayoutMenuItem(
    label: Int,
    onClick: () -> Unit,
) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun LayoutRequestDialog(
    request: LayoutRequest,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val naming = request is LayoutRequest.Save || request is LayoutRequest.Rename
    var name by rememberSaveable(request) { mutableStateOf(defaultName) }
    val (title, body, confirm) = dialogTexts(request)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text =
            if (naming) {
                {
                    OutlinedTextField(
                        modifier = Modifier.testTag(HOME_LAYOUTS_NAME_FIELD_TEST_TAG),
                        value = name,
                        onValueChange = { name = it.take(SavedHomeLayouts.MAX_NAME_LENGTH) },
                        singleLine = true,
                    )
                }
            } else {
                { Text(body) }
            },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(HOME_LAYOUTS_DIALOG_CONFIRM_TEST_TAG),
                enabled = !naming || name.isNotBlank(),
                onClick = { onConfirm(name) },
            ) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.home_layout_dialog_cancel)) }
        },
    )
}

@Composable
private fun dialogTexts(request: LayoutRequest): Triple<String, String, String> =
    when (request) {
        LayoutRequest.Save ->
            Triple(
                stringResource(R.string.home_layout_save_dialog_title),
                "",
                stringResource(R.string.home_layout_save_dialog_confirm),
            )
        is LayoutRequest.Rename ->
            Triple(
                stringResource(R.string.home_layout_rename_dialog_title),
                "",
                stringResource(R.string.home_layout_rename_dialog_confirm),
            )
        is LayoutRequest.Activate ->
            Triple(
                stringResource(R.string.home_layout_activate_dialog_title, request.layout.name),
                stringResource(
                    R.string.home_layout_activate_dialog_body,
                    beforeNameFor(request.layout),
                ),
                stringResource(R.string.home_layout_activate),
            )
        is LayoutRequest.Replace ->
            Triple(
                stringResource(R.string.home_layout_replace_dialog_title, request.layout.name),
                stringResource(R.string.home_layout_replace_dialog_body),
                stringResource(R.string.home_layout_replace_dialog_confirm),
            )
        is LayoutRequest.Delete ->
            Triple(
                stringResource(R.string.home_layout_delete_dialog_title, request.layout.name),
                stringResource(R.string.home_layout_delete_dialog_body),
                stringResource(R.string.home_layout_delete_dialog_confirm),
            )
    }

/** The name the automatic copy of the Home that [activated] replaces gets. */
@Composable
private fun beforeNameFor(activated: SavedHomeLayout): String =
    if (activated.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION) {
        stringResource(R.string.home_layout_before_previous)
    } else {
        stringResource(R.string.home_layout_before_name, activated.name)
    }

/** The strings the actions announce, read once on the composition so the actions can run in a coroutine. */
@Suppress("LongParameterList") // One entry per announced sentence.
private class HomeLayoutMessages(
    val saved: (String) -> String,
    val renamed: String,
    val replaced: String,
    val deleted: String,
    val activated: String,
    val activatedWidgets: (Int) -> String,
    val full: String,
    val blank: String,
    val loading: String,
    val failed: String,
    val undo: String,
    val beforeName: (SavedHomeLayout) -> String,
) {
    companion object {
        @Composable
        fun rememberMessages(): HomeLayoutMessages {
            val resources = LocalContext.current.resources
            return remember(resources) {
                HomeLayoutMessages(
                    saved = { resources.getString(R.string.home_layout_saved_message, it) },
                    renamed = resources.getString(R.string.home_layout_renamed_message),
                    replaced = resources.getString(R.string.home_layout_replaced_message),
                    deleted = resources.getString(R.string.home_layout_deleted_message),
                    activated = resources.getString(R.string.home_layout_activated_message),
                    activatedWidgets = {
                        resources.getQuantityString(R.plurals.home_layout_activated_widgets_message, it, it)
                    },
                    full = resources.getString(R.string.home_layout_full_message),
                    blank = resources.getString(R.string.home_layout_blank_message),
                    loading = resources.getString(R.string.home_layout_apps_loading_message),
                    failed = resources.getString(R.string.home_layout_failed_message),
                    undo = resources.getString(R.string.stream_undo),
                    beforeName = { activated ->
                        if (activated.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION) {
                            resources.getString(R.string.home_layout_before_previous)
                        } else {
                            resources.getString(R.string.home_layout_before_name, activated.name)
                        }
                    },
                )
            }
        }
    }
}

private fun CoroutineScope.run(
    request: LayoutRequest,
    name: String,
    controller: SavedHomeLayoutsController,
    snackbar: SnackbarHostState?,
    messages: HomeLayoutMessages,
) {
    launch {
        when (request) {
            LayoutRequest.Save ->
                controller.save(
                    name,
                ).announce(snackbar, messages) { messages.saved((it as SavedHomeChange.Changed).layout.name) }
            is LayoutRequest.Rename ->
                controller.rename(request.layout.id, name).announce(snackbar, messages) { messages.renamed }
            is LayoutRequest.Replace ->
                controller.replaceWithCurrent(request.layout.id).announce(snackbar, messages) { messages.replaced }
            is LayoutRequest.Delete -> {
                val outcome = controller.delete(request.layout.id)
                if (outcome is SavedHomeChange.Changed) {
                    announceUndoable(snackbar, messages.deleted, messages.undo) { controller.restore(outcome.layout) }
                } else {
                    outcome.announce(snackbar, messages) { messages.deleted }
                }
            }
            is LayoutRequest.Activate -> activate(request.layout, controller, snackbar, messages)
        }
    }
}

private suspend fun SavedHomeChange.announce(
    snackbar: SnackbarHostState?,
    messages: HomeLayoutMessages,
    success: (SavedHomeChange) -> String,
) {
    val text =
        when (this) {
            is SavedHomeChange.Changed -> success(this)
            SavedHomeChange.Full -> messages.full
            SavedHomeChange.BlankName -> messages.blank
            SavedHomeChange.Unknown -> messages.failed
        }
    snackbar?.currentSnackbarData?.dismiss()
    snackbar?.showSnackbar(text)
}

/** A message with Undo; [onUndo] runs only if Undo was tapped. */
private suspend fun announceUndoable(
    snackbar: SnackbarHostState?,
    message: String,
    undoLabel: String,
    onUndo: suspend () -> Unit,
) {
    if (snackbar == null) return
    snackbar.currentSnackbarData?.dismiss()
    val outcome = snackbar.showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Long)
    if (outcome == SnackbarResult.ActionPerformed) onUndo()
}

/**
 * Activates [layout]. The previous Home is kept first (the controller's order); the Undo window is the snackbar, and
 * whatever ends it (Undo, the timeout, another message, leaving the page) closes the widget-id hold exactly once.
 */
private suspend fun activate(
    layout: SavedHomeLayout,
    controller: SavedHomeLayoutsController,
    snackbar: SnackbarHostState?,
    messages: HomeLayoutMessages,
) {
    when (val result = controller.activate(layout.id, messages.beforeName)) {
        is SavedHomeActivation.Activated -> {
            try {
                val text =
                    if (result.placeholders > 0) {
                        messages.activatedWidgets(
                            result.placeholders,
                        )
                    } else {
                        messages.activated
                    }
                announceUndoable(snackbar, text, messages.undo) { controller.undo(result.before) }
            } finally {
                controller.closeUndoWindow()
            }
        }
        SavedHomeActivation.AppsNotReady -> snackbar?.showSnackbar(messages.loading)
        SavedHomeActivation.Unknown, SavedHomeActivation.NotSaved, SavedHomeActivation.SimpleMode ->
            snackbar?.showSnackbar(messages.failed)
    }
}

/** The row on Settings > Home that opens the Home layouts page, saying how many are saved on this device class. */
@Composable
internal fun SettingsHomeLayoutsRow(
    state: SettingsSurfaceState,
    onOpen: () -> Unit,
) {
    val all by (LocalSavedHomeLayouts.current?.layouts ?: NoLayouts).collectAsState()
    val count = all.savedCount(state.selectedLayoutDeviceClass)
    val simple = state.settings.features.simpleMode
    SettingsSection(title = stringResource(R.string.settings_page_home_layouts)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_home_layouts),
            subtitle =
                when {
                    simple -> stringResource(R.string.settings_home_layouts_row_off_in_simple_mode)
                    count == 0 -> stringResource(R.string.settings_home_layouts_row_none)
                    else -> pluralStringResource(R.plurals.settings_home_layouts_row_count, count, count)
                },
            onClick = onOpen,
        )
    }
}
