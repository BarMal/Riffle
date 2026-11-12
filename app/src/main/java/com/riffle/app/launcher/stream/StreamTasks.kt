package com.riffle.app.launcher.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.riffle.app.R
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.stream.NotConnectedReason
import com.riffle.core.domain.launcher.stream.StreamSectionResult
import com.riffle.core.domain.launcher.stream.TaskContent

internal const val TASKS_QUICK_ADD_TEST_TAG = "stream-tasks-quick-add"
internal const val TASKS_ADD_BUTTON_TEST_TAG = "stream-tasks-add"
internal const val TASKS_REFRESH_BUTTON_TEST_TAG = "stream-tasks-refresh"
internal const val TASKS_CONNECT_TEST_TAG = "stream-tasks-connect"
internal const val TASKS_RETRY_TEST_TAG = "stream-tasks-retry"

/** The quick-add field at the top of Tasks, with a refresh button. The typed text is not kept across restarts. */
@Composable
internal fun TaskQuickAdd(
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            callbacks.onAddTask(text.trim())
            text = ""
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(RiffleSpacing.xs),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).testTag(TASKS_QUICK_ADD_TEST_TAG),
            singleLine = true,
            label = { Text(stringResource(R.string.stream_task_add_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(
                    onClick = submit,
                    enabled = text.isNotBlank(),
                    modifier = Modifier.size(StreamMinTarget).testTag(TASKS_ADD_BUTTON_TEST_TAG),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.stream_task_add))
                }
            },
        )
        IconButton(
            onClick = callbacks.onRefreshTasks,
            modifier = Modifier.size(StreamMinTarget).testTag(TASKS_REFRESH_BUTTON_TEST_TAG),
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.stream_task_refresh))
        }
    }
}

/** Shown under the list while it is the last one fetched because Todoist could not be reached. */
@Composable
internal fun TaskStaleNote(
    callbacks: StreamCallbacks,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = RiffleSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.stream_task_stale),
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = callbacks.onRefreshTasks,
            modifier = Modifier.heightIn(min = StreamMinTarget).testTag(TASKS_RETRY_TEST_TAG),
        ) {
            Text(stringResource(R.string.stream_task_retry))
        }
    }
}

/** Whether any task of [result] is the last fetched list rather than a fresh one. */
internal fun StreamSectionResult.hasStaleTasks(): Boolean =
    this is StreamSectionResult.Ready && items.any { it is TaskContent && it.stale }

/** The Tasks section's own states: quick-add over the list or the empty message; connect and retry for the rest. */
@Composable
internal fun TasksSectionBody(
    result: StreamSectionResult,
    callbacks: StreamCallbacks,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(RiffleSpacing.s)) {
        when (result) {
            is StreamSectionResult.Ready, StreamSectionResult.Empty -> {
                TaskQuickAdd(callbacks)
                content()
                if (result.hasStaleTasks()) TaskStaleNote(callbacks)
            }
            is StreamSectionResult.NotConnected ->
                StreamStateMessage(
                    message =
                        stringResource(
                            if (result.reason == NotConnectedReason.TOKEN_REJECTED) {
                                R.string.stream_tasks_token_rejected
                            } else {
                                R.string.stream_not_connected_tasks
                            },
                        ),
                    actionLabel =
                        stringResource(
                            if (result.reason == NotConnectedReason.TOKEN_REJECTED) {
                                R.string.stream_tasks_replace_token
                            } else {
                                R.string.stream_tasks_connect
                            },
                        ),
                    onAction = callbacks.onConnectTasks,
                )
            StreamSectionResult.Unavailable ->
                StreamStateMessage(
                    message = stringResource(R.string.stream_tasks_unavailable),
                    actionLabel = stringResource(R.string.stream_task_retry),
                    onAction = callbacks.onRefreshTasks,
                )
            else -> content()
        }
    }
}
