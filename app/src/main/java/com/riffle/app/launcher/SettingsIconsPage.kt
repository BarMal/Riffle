package com.riffle.app.launcher

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.riffle.app.R
import com.riffle.app.launcher.icons.AppIconPipeline
import com.riffle.app.launcher.icons.InstalledIconPack
import com.riffle.app.launcher.icons.LocalAppIconLoader
import com.riffle.app.launcher.icons.LocalAppIconPipeline
import com.riffle.app.launcher.icons.rememberLoaded
import com.riffle.core.domain.launcher.icons.IconShape
import com.riffle.core.domain.launcher.icons.IconStyleSettings
import com.riffle.core.domain.launcher.icons.LegacyIconHandling
import kotlinx.coroutines.launch

internal const val ICONS_PREVIEW_TEST_TAG = "icons-preview"
internal const val ICONS_RESET_TEST_TAG = "icons-reset"
internal const val ICONS_RESET_CONFIRM_TEST_TAG = "icons-reset-confirm"
internal const val ICONS_PREVIEW_COUNT = 6
private const val PACK_ICON_PX = 96

@Composable
internal fun rememberAppIconPipeline(): AppIconPipeline {
    val context = LocalContext.current
    return LocalAppIconPipeline.current ?: remember(context) { AppIconPipeline.shared(context) }
}

/** Settings > Appearance > Icons: shape, legacy icons, themed icons, the icon pack and "Reset icon changes". */
@Composable
internal fun SettingsIconsPageContent(
    state: SettingsSurfaceState,
    onAction: (LauncherShellAction) -> Unit,
) {
    val icons = state.settings.icons
    SettingsSection(title = stringResource(R.string.settings_section_icons_preview)) {
        IconStylePreview(apps = state.installedApps.take(ICONS_PREVIEW_COUNT).map { it.identity to it.label })
    }
    SettingsSection(title = stringResource(R.string.settings_section_icons_shape)) {
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.ICONS_SHAPE),
            title = SettingKey.ICONS_SHAPE.label(),
            options = IconShape.entries,
            selected = icons.shape,
            onSelect = { shape -> onAction(LauncherShellAction.SelectIconShape(shape)) },
            label = { shape -> stringResource(shape.labelRes) },
        )
        val hasShape = icons.shape != IconShape.SYSTEM
        SettingsChoiceRow(
            modifier = Modifier.settingAnchor(SettingKey.ICONS_LEGACY),
            title = SettingKey.ICONS_LEGACY.label(),
            subtitle =
                stringResource(
                    if (hasShape) R.string.setting_icons_legacy_subtitle else R.string.setting_icons_legacy_needs_shape,
                ),
            options = LegacyIconHandling.entries,
            selected = icons.legacyIcons,
            enabled = hasShape,
            onSelect = { handling -> onAction(LauncherShellAction.SelectLegacyIconHandling(handling)) },
            label = { handling -> stringResource(handling.labelRes) },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_icons_themed)) {
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.ICONS_THEMED),
            title = SettingKey.ICONS_THEMED.label(),
            subtitle =
                stringResource(
                    if (supported) {
                        R.string.setting_icons_themed_subtitle
                    } else {
                        R.string.setting_icons_themed_needs_android_13
                    },
                ),
            checked = icons.themedIcons && supported,
            enabled = supported,
            onCheckedChange = { enabled -> onAction(LauncherShellAction.SetThemedIcons(enabled)) },
        )
        val themed = icons.themedIcons && supported
        SettingsSwitchRow(
            modifier = Modifier.settingAnchor(SettingKey.ICONS_THEME_ALL),
            title = SettingKey.ICONS_THEME_ALL.label(),
            subtitle =
                stringResource(
                    if (themed) {
                        R.string.setting_icons_theme_all_subtitle
                    } else {
                        R.string.setting_icons_theme_all_needs_themed
                    },
                ),
            checked = icons.themeAllIcons && themed,
            enabled = themed,
            onCheckedChange = { enabled -> onAction(LauncherShellAction.SetThemeAllIcons(enabled)) },
        )
    }
    SettingsSection(title = stringResource(R.string.settings_section_icons_pack)) {
        IconPackChoice(selected = icons.iconPack, onSelect = { onAction(LauncherShellAction.SelectIconPack(it)) })
    }
    SettingsSection(title = stringResource(R.string.settings_section_icons_changes)) {
        IconResetRow(icons = icons, onAction = onAction)
    }
}

/** The row on the Appearance page that opens the Icons page, saying what the style is. */
@Composable
internal fun SettingsIconsRow(
    icons: IconStyleSettings,
    onOpen: () -> Unit,
) {
    val parts = mutableListOf<String>()
    parts += stringResource(icons.shape.labelRes)
    if (icons.themedIcons) parts += stringResource(R.string.settings_icons_row_themed_suffix)
    val changed = icons.overrides.values.count { it.icon != null }
    if (changed > 0) parts += stringResource(R.string.settings_icons_row_changed_suffix, changed)
    SettingsSection(title = stringResource(R.string.settings_section_icons)) {
        SettingsClickableRow(
            title = stringResource(R.string.settings_page_appearance_icons),
            subtitle = parts.joinToString(", "),
            onClick = onOpen,
        )
    }
}

/** A strip of the user's apps drawn by the launcher's loader, so it shows exactly the style every surface shows. */
@Composable
internal fun IconStylePreview(apps: List<Pair<com.riffle.core.domain.launcher.apps.AppIdentity, String>>) {
    val loader = LocalAppIconLoader.current
    val description = stringResource(R.string.settings_icons_preview_description)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag(ICONS_PREVIEW_TEST_TAG)
                .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { (identity, label) ->
            LauncherAppIcon(
                identity = identity,
                label = label,
                iconLoader = loader,
                modifier = Modifier.size(ICON_PREVIEW_SIZE_DP.dp),
            )
        }
    }
}

private const val ICON_PREVIEW_SIZE_DP = 44

/** None and the installed icon packs, one 48 dp radio row each, with the pack's own icon. */
@Composable
private fun IconPackChoice(
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    val pipeline = rememberAppIconPipeline()
    val listVersion = pipeline.packs.packListVersion
    val packs by rememberLoaded(emptyList<InstalledIconPack>(), pipeline, listVersion) {
        pipeline.packs.installedPacks()
    }
    val missing = selected != null && packs.none { it.packageName == selected }
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.settingAnchor(SettingKey.ICONS_PACK).selectableGroup(),
    ) {
        SettingsListRow(
            title = SettingKey.ICONS_PACK.label(),
            subtitle =
                when {
                    missing -> stringResource(R.string.icon_pack_missing)
                    packs.isEmpty() -> stringResource(R.string.icon_pack_none_installed)
                    else -> stringResource(R.string.setting_icons_pack_subtitle)
                },
        )
        IconPackRow(label = stringResource(R.string.icon_pack_none), icon = null, selected = selected == null) {
            onSelect(null)
        }
        packs.forEach { pack ->
            val icon by rememberLoaded<ImageBitmap?>(null, pipeline, pack.packageName) {
                pipeline.packs.packIcon(pack.packageName)?.toBitmap(PACK_ICON_PX, PACK_ICON_PX)?.asImageBitmap()
            }
            IconPackRow(label = pack.label, icon = icon, selected = pack.packageName == selected) {
                onSelect(pack.packageName)
            }
        }
    }
}

@Composable
private fun IconPackRow(
    label: String,
    icon: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = stringResource(R.string.icon_pack_icon_description, label),
                modifier = Modifier.size(32.dp),
            )
        }
        Text(text = label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
    }
}

/** "Reset icon changes": asks first, then "Icon changes reset" with Undo. */
@Composable
private fun IconResetRow(
    icons: IconStyleSettings,
    onAction: (LauncherShellAction) -> Unit,
) {
    val snackbar = LocalSettingsSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val undo = stringResource(R.string.stream_undo)
    val done = stringResource(R.string.settings_icons_reset_done)
    val changed = icons.overrides.values.count { it.icon != null }
    var confirm by remember { mutableStateOf(false) }
    SettingsClickableRow(
        modifier = Modifier.settingAnchor(SettingKey.ICONS_RESET).testTag(ICONS_RESET_TEST_TAG),
        title = SettingKey.ICONS_RESET.label(),
        subtitle =
            if (changed == 0) {
                stringResource(R.string.setting_icons_reset_none)
            } else {
                pluralStringResource(R.plurals.setting_icons_reset_count, changed, changed)
            },
        enabled = changed > 0,
        onClick = { confirm = true },
        trailingContent = { SettingsButtonText(text = stringResource(R.string.setting_icons_reset_action)) },
    )
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.settings_icons_reset_dialog_title)) },
            text = { Text(stringResource(R.string.settings_icons_reset_dialog_body)) },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag(ICONS_RESET_CONFIRM_TEST_TAG),
                    onClick = {
                        confirm = false
                        val before = icons
                        onAction(LauncherShellAction.ResetIconChanges)
                        scope.launch {
                            scope.announceWithUndo(snackbar, done, undo) { onAction(restoreIcons(before)) }
                        }
                    },
                ) { Text(stringResource(R.string.settings_icons_reset_dialog_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) {
                    Text(stringResource(R.string.settings_icons_reset_dialog_cancel))
                }
            },
        )
    }
}

/** The actions that put every per-app icon choice back as it was: one per app that had one. */
private fun restoreIcons(before: IconStyleSettings): LauncherShellAction =
    LauncherShellAction.RestoreIconOverrides(before.overrides)

private val IconShape.labelRes: Int
    get() =
        when (this) {
            IconShape.SYSTEM -> R.string.icon_shape_system
            IconShape.CIRCLE -> R.string.icon_shape_circle
            IconShape.SQUIRCLE -> R.string.icon_shape_squircle
            IconShape.ROUNDED_SQUARE -> R.string.icon_shape_rounded_square
            IconShape.SQUARE -> R.string.icon_shape_square
            IconShape.TEARDROP -> R.string.icon_shape_teardrop
        }

private val LegacyIconHandling.labelRes: Int
    get() =
        when (this) {
            LegacyIconHandling.PLATE -> R.string.icon_legacy_plate
            LegacyIconHandling.AS_IS -> R.string.icon_legacy_as_is
        }
