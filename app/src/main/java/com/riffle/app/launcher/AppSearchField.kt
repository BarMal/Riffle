package com.riffle.app.launcher

import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun AppSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        modifier = modifier,
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        label = { Text(text = "Search apps") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    modifier = Modifier.semantics { contentDescription = "Clear app search" },
                    onClick = { onQueryChanged("") },
                ) {
                    Text(text = "X")
                }
            }
        },
    )
}
