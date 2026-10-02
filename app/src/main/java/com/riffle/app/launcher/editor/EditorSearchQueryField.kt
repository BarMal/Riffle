package com.riffle.app.launcher.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.editor.SearchQueryInput
import kotlinx.coroutines.delay

internal const val EDITOR_QUERY_FIELD_TEST_TAG = "editor-query-field"
internal const val EDITOR_QUERY_CLEAR_TEST_TAG = "editor-query-clear"

/**
 * Lets the pane apply a query the user typed but the debounce has not applied yet, before something else (Next,
 * Cancel) acts: the flow only accepts a query at the Source step, so it must land first. [flush] is a no-op when
 * nothing is pending, and applying the same text twice changes nothing.
 */
internal class PendingQueryFlush {
    var pending: (() -> Unit)? = null

    fun flush() {
        pending?.invoke()
    }
}

/**
 * The search text of one lens (Source step, shown under a selected source that takes a query). [applied] is what the
 * flow draft holds ("" for none: the lens then uses the shared search text); typing applies after
 * [SearchQueryInput.DEBOUNCE_MILLIS] of quiet (every keystroke restarts the wait, so a burst is one edit), "Done"
 * and the clear button apply at once, and [flush] lets the pane apply a pending edit before it leaves the step.
 * The text is capped at [SearchQueryInput.MAX_LENGTH] with a visible counter. Preview only: nothing here stores
 * anything, and the text is user-authored lens configuration, never item content.
 */
@Composable
internal fun SourceQueryField(
    source: SourceId,
    applied: String,
    onApply: (String) -> Unit,
    modifier: Modifier = Modifier,
    flush: PendingQueryFlush? = null,
) {
    var typed by rememberSaveable(source.value) { mutableStateOf(applied) }
    val currentApplied by rememberUpdatedState(applied)
    val latestOnApply by rememberUpdatedState(onApply)
    val focus = LocalFocusManager.current
    val applyNow = {
        if (SearchQueryInput.needsApply(typed, currentApplied)) latestOnApply(SearchQueryInput.applied(typed))
    }
    LaunchedEffect(typed) {
        if (SearchQueryInput.needsApply(typed, currentApplied)) {
            delay(SearchQueryInput.DEBOUNCE_MILLIS)
            latestOnApply(SearchQueryInput.applied(typed))
        }
    }
    SideEffect { flush?.pending = applyNow }
    DisposableEffect(flush) { onDispose { if (flush != null) flush.pending = null } }
    OutlinedTextField(
        value = typed,
        onValueChange = { typed = SearchQueryInput.capped(it) },
        modifier = modifier.fillMaxWidth().testTag(EDITOR_QUERY_FIELD_TEST_TAG),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    applyNow()
                    focus.clearFocus()
                },
            ),
        label = { Text(EditorText.QUERY_LABEL) },
        trailingIcon = {
            if (typed.isNotEmpty()) {
                IconButton(
                    onClick = {
                        typed = ""
                        if (currentApplied.isNotEmpty()) latestOnApply("")
                    },
                    modifier =
                        Modifier
                            .testTag(EDITOR_QUERY_CLEAR_TEST_TAG)
                            .semantics { contentDescription = EditorText.QUERY_CLEAR },
                ) { Text("X") }
            }
        },
        supportingText = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(EditorText.QUERY_HELP, modifier = Modifier.weight(1f))
                Text(
                    text = SearchQueryInput.counter(typed),
                    color =
                        if (SearchQueryInput.atLimit(typed)) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        },
    )
}
