package com.riffle.app.launcher

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction

@Composable
fun AppSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Search apps",
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        modifier = modifier,
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        label = { Text(text = label) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.semantics { contentDescription = searchClearContentDescription(label) },
                    onClick = { onQueryChanged("") },
                ) {
                    Text(text = "X")
                }
            }
        },
    )
}

internal fun searchClearContentDescription(label: String): String = "Clear ${label.lowercase()}"
