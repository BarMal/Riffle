package com.riffle.app.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.app.launcher.summon.summonCollectionAddChoices
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.summon.SummonCollection
import com.riffle.core.domain.launcher.summon.SummonCollectionId
import com.riffle.core.domain.launcher.summon.SummonSettings
import com.riffle.core.domain.launcher.summon.SummonSlot
import com.riffle.core.domain.launcher.summon.collection
import com.riffle.core.domain.launcher.summon.movedCollectionItem
import com.riffle.core.domain.launcher.summon.renamedCollection
import com.riffle.core.domain.launcher.summon.withCollectionItem
import com.riffle.core.domain.launcher.summon.withNewCollection
import com.riffle.core.domain.launcher.summon.withoutCollection
import com.riffle.core.domain.launcher.summon.withoutCollectionItem
import java.util.UUID

private val EDITOR_MAX_HEIGHT = 420.dp

/**
 * Settings › Summon › Collections: Summon's own folders. Each opens an editor to rename it, add, reorder and remove
 * what it holds (apps, glances, other collections) and delete it. A collection goes on the ring from "Add to the ring".
 */
@Composable
internal fun SummonCollectionsSection(
    summon: SummonSettings,
    installed: List<InstalledApp>,
    label: (SummonSlot) -> String,
    set: (SummonSettings) -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val newName = stringResource(R.string.settings_summon_collection_new_name, summon.collections.size + 1)
    SettingsSection(title = SettingKey.SUMMON_COLLECTIONS.label()) {
        SettingsClickableRow(
            modifier = Modifier.settingAnchor(SettingKey.SUMMON_COLLECTIONS),
            title = stringResource(R.string.settings_summon_collection_new),
            subtitle = stringResource(R.string.settings_summon_collections_subtitle),
            onClick = {
                val id = SummonCollectionId(UUID.randomUUID().toString())
                set(summon.withNewCollection(id, newName))
                editing = id.value
            },
        )
        summon.collections.forEach { collection ->
            SettingsClickableRow(
                title = collection.name,
                subtitle =
                    pluralStringResource(
                        R.plurals.settings_summon_collection_items,
                        collection.items.size,
                        collection.items.size,
                    ),
                onClick = { editing = collection.id.value },
            )
        }
    }
    val open = editing?.let { summon.collection(SummonCollectionId(it)) }
    if (open != null) {
        SummonCollectionEditor(
            collection = open,
            summon = summon,
            installed = installed,
            label = label,
            set = set,
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun SummonCollectionEditor(
    collection: SummonCollection,
    summon: SummonSettings,
    installed: List<InstalledApp>,
    label: (SummonSlot) -> String,
    set: (SummonSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val id = collection.id
    var adding by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_summon_collection_edit)) },
        text = {
            Column(
                Modifier.heightIn(max = EDITOR_MAX_HEIGHT).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s),
            ) {
                OutlinedTextField(
                    value = collection.name,
                    onValueChange = { set(summon.renamedCollection(id, it)) },
                    label = { Text(stringResource(R.string.settings_summon_collection_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                collection.items.forEachIndexed { index, slot ->
                    CollectionItemRow(
                        name = label(slot),
                        canMoveUp = index > 0,
                        canMoveDown = index < collection.items.lastIndex,
                        onMoveUp = { set(summon.movedCollectionItem(id, index, index - 1)) },
                        onMoveDown = { set(summon.movedCollectionItem(id, index, index + 1)) },
                        onRemove = { set(summon.withoutCollectionItem(id, index)) },
                    )
                }
                TextButton(onClick = { adding = true }) {
                    Text(stringResource(R.string.settings_summon_collection_add))
                }
                TextButton(onClick = {
                    onDismiss()
                    set(summon.withoutCollection(id))
                }) { Text(stringResource(R.string.settings_summon_collection_delete)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) } },
    )
    if (adding) {
        SummonAddDialog(
            title = stringResource(R.string.settings_summon_collection_add_to, collection.name),
            choices = summonCollectionAddChoices(summon, id, installed),
            label = label,
            onAdd = { set(summon.withCollectionItem(id, it)) },
        ) { adding = false }
    }
}

@Composable
private fun CollectionItemRow(
    name: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, Modifier.weight(1f).padding(top = RiffleSpacing.m))
        IconButton(enabled = canMoveUp, onClick = onMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.settings_summon_slot_move_up, name))
        }
        IconButton(enabled = canMoveDown, onClick = onMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.settings_summon_slot_move_down, name))
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Close, stringResource(R.string.settings_summon_collection_remove, name))
        }
    }
}
