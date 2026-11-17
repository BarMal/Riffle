package com.riffle.app.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.riffle.app.R
import com.riffle.app.launcher.diagnostics.pair.PairScriptKind

// The copy and share rows of the "App pair experiment" page: a log goes to the clipboard or, when it is too long to
// paste, out through the system share sheet as plain text.

internal const val APP_PAIR_SCRIPT_COPY_TAG = "appPair.script.copy"
internal const val APP_PAIR_HELD_COPY_TAG = "appPair.script.copyHeld"
internal const val APP_PAIR_PROBE_COPY_TAG = "appPair.script.copyProbe"
internal const val APP_PAIR_NINE_COPY_TAG = "appPair.script.copyNine"
internal const val APP_PAIR_SHARE_TAG_SUFFIX = ".share"

private const val DISABLED_ROW_ALPHA = 0.5f

/** Which script log a copy or share row puts out: the latest run of [kind], or of any kind when null. */
internal enum class ScriptCopy(
    val kind: PairScriptKind?,
    val tag: String,
) {
    ANY(null, APP_PAIR_SCRIPT_COPY_TAG),
    HELD(PairScriptKind.HELD, APP_PAIR_HELD_COPY_TAG),
    PROBES(PairScriptKind.PROBES, APP_PAIR_PROBE_COPY_TAG),
    NINE(PairScriptKind.SCENARIOS, APP_PAIR_NINE_COPY_TAG),
}

/** A copy row with a "Share…" button beside it; both are disabled while [enabled] is false. */
@Composable
internal fun PairCopyRow(
    title: String,
    subtitle: String,
    tag: String,
    enabled: Boolean,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingsClickableRow(
            modifier = Modifier.weight(1f).testTag(tag).alpha(if (enabled) 1f else DISABLED_ROW_ALPHA),
            title = title,
            subtitle = subtitle,
            enabled = enabled,
            onClick = onCopy,
            trailingContent = null,
        )
        TextButton(
            modifier = Modifier.testTag(tag + APP_PAIR_SHARE_TAG_SUFFIX),
            enabled = enabled,
            onClick = onShare,
        ) {
            Text(stringResource(R.string.app_pair_share))
        }
    }
}

/** Hands [text] to the system share sheet as plain text; a missing or refusing sheet is ignored. */
internal fun sharePairText(
    context: Context,
    text: String,
) {
    runCatching {
        val send =
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "App pair experiment")
                .putExtra(Intent.EXTRA_TEXT, text)
        val chooser = Intent.createChooser(send, null)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
