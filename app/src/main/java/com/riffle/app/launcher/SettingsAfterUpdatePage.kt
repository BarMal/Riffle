package com.riffle.app.launcher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.SharedPreferencesAfterUpdateStore
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateLog
import com.riffle.core.domain.launcher.diagnostics.AfterUpdateStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal const val AFTER_UPDATE_TEXT_TEST_TAG = "after-update-text"
internal const val AFTER_UPDATE_COPY_TEST_TAG = "after-update-copy"

/**
 * Settings > About > Diagnostics > After-update starts: what the first start after each update looked like (the last
 * ten), with a Copy button. Read-only: it changes nothing. See `places-views-plan.md`, "After-update starts".
 */
@Composable
internal fun SettingsAfterUpdatePageContent() {
    val context = LocalContext.current
    var entries by remember { mutableStateOf<List<AfterUpdateStart>?>(null) }
    LaunchedEffect(context) {
        entries =
            withContext(Dispatchers.IO) { SharedPreferencesAfterUpdateStore(context).read().entries }
    }
    SettingsAfterUpdateContent(entries = entries, onCopy = { text -> copyAfterUpdateText(context, text) })
}

/** The page without its storage: [entries] is null until they have been read. */
@Composable
internal fun SettingsAfterUpdateContent(
    entries: List<AfterUpdateStart>?,
    onCopy: (String) -> Unit,
) {
    val text = remember(entries) { entries?.let { AfterUpdateLog.asText(it, ::afterUpdateTime) }.orEmpty() }
    var copied by remember { mutableStateOf(false) }
    SettingsSection(title = stringResource(R.string.settings_page_after_update)) {
        if (entries.isNullOrEmpty()) {
            if (entries != null) SettingsSecondaryText(text = stringResource(R.string.after_update_empty))
        } else {
            SelectionContainer { Text(modifier = Modifier.testTag(AFTER_UPDATE_TEXT_TEST_TAG), text = text) }
            TextButton(
                modifier = Modifier.testTag(AFTER_UPDATE_COPY_TEST_TAG),
                onClick = {
                    onCopy(text)
                    copied = true
                },
            ) {
                Text(stringResource(if (copied) R.string.after_update_copied else R.string.after_update_copy))
            }
        }
    }
}

private fun afterUpdateTime(epochMillis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date(epochMillis))

private fun copyAfterUpdateText(
    context: Context,
    text: String,
) {
    runCatching {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.after_update_clipboard_label), text))
    }
}
