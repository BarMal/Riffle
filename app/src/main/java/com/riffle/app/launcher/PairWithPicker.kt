package com.riffle.app.launcher

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.riffle.core.domain.launcher.apps.InstalledApp
import com.riffle.core.domain.launcher.home.AppShortcutItem

internal const val PAIR_WITH_SEARCH_TEST_TAG = "pair-with-search"
internal const val PAIR_WITH_PICK_TEST_TAG_PREFIX = "pair-with-pick:"

/** The apps "Pair with..." offers [first]: every launchable app that matches [query], except [first] itself. */
internal fun pairWithCandidates(
    apps: List<InstalledApp>,
    first: AppShortcutItem,
    query: String,
): List<InstalledApp> = pairCandidates(apps, query).filter { it.identity != first.appIdentity }

/** The full app picker behind "Pair with...": search, every launchable app, the pinned app itself left out. */
@Composable
internal fun PairWithPicker(
    first: AppShortcutItem,
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair ${first.label} with") },
        text = { PairWithPickerContent(first, apps, iconLoader, onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** What the picker shows: a search field over the list of apps to pair [first] with. */
@Composable
internal fun PairWithPickerContent(
    first: AppShortcutItem,
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onPick: (InstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, first, query) { pairWithCandidates(apps, first, query) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSearchField(
            modifier = Modifier.fillMaxWidth().testTag(PAIR_WITH_SEARCH_TEST_TAG),
            query = query,
            onQueryChanged = { query = it },
        )
        LazyColumn(modifier = Modifier.heightIn(max = PICKER_MAX_HEIGHT_DP.dp)) {
            items(
                shown,
                key = { "${it.identity.packageName.value}/${it.identity.activityName.value}" },
            ) { app ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag(PAIR_WITH_PICK_TEST_TAG_PREFIX + app.identity.packageName.value)
                            .clickable(onClickLabel = "Pair with ${app.label}") { onPick(app) }
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LauncherAppIcon(app.identity, app.label, iconLoader, Modifier.size(40.dp))
                    Text(text = app.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private const val PICKER_MAX_HEIGHT_DP = 360
