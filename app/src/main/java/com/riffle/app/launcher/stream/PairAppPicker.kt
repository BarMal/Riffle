package com.riffle.app.launcher.stream

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.riffle.app.R
import com.riffle.app.launcher.AppIconLoader
import com.riffle.app.launcher.AppSearchField
import com.riffle.app.launcher.LauncherAppIcon
import com.riffle.app.launcher.pairCandidates
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.InstalledApp

internal const val PAIR_PICKER_SEARCH_TEST_TAG = "pair-picker-search"
internal const val PAIR_PICKER_PICK_TEST_TAG_PREFIX = "pair-picker-pick:"

/** The apps the picker offers: every launchable app of the personal profile that matches [query], but [exclude]. */
internal fun pairPickerCandidates(
    apps: List<InstalledApp>,
    exclude: AppIdentity?,
    query: String,
): List<InstalledApp> =
    pairCandidates(apps, query).filter { app ->
        exclude == null || app.identity.packageName != exclude.packageName || app.identity.profile != exclude.profile
    }

/** The app picker of a pair's half: search, every launchable personal-profile app, the other half's app left out. */
@Composable
internal fun PairAppPicker(
    title: String,
    exclude: AppIdentity?,
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { PairAppPickerContent(exclude, apps, iconLoader, onPick) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.stream_pair_cancel)) } },
    )
}

/** What the picker shows: a search field over the list of apps. */
@Composable
internal fun PairAppPickerContent(
    exclude: AppIdentity?,
    apps: List<InstalledApp>,
    iconLoader: AppIconLoader,
    onPick: (InstalledApp) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, exclude, query) { pairPickerCandidates(apps, exclude, query) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppSearchField(
            modifier = Modifier.fillMaxWidth().testTag(PAIR_PICKER_SEARCH_TEST_TAG),
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
                            .testTag(PAIR_PICKER_PICK_TEST_TAG_PREFIX + app.identity.packageName.value)
                            .clickable { onPick(app) }
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
