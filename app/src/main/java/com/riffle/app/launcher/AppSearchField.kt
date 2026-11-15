package com.riffle.app.launcher

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    /** Called when the Search or Enter key is pressed, before the keyboard hides (the Library launches the top hit). */
    onSearch: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        modifier = modifier,
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onSearch()
                    keyboardController?.hide()
                },
            ),
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

/** How strong the placeholder and the clear button are against the panel's own text colour. */
private const val PANEL_FIELD_SECONDARY_ALPHA = 0.74f

/**
 * The search field of a glass panel that is itself the field's container (search from the Dock): no box inside the glass,
 * no container fill, the text, the caret and the hint in the panel's own [contentColor] (the hint softened, still well over
 * 4.5:1 on Thick glass). The outlined field it replaces took the theme's outline and variant colours, which are
 * tuned for a plain surface and read as washed out on the glass.
 */
@Composable
internal fun PanelSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
    label: String = "Search apps",
    onSearch: () -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val secondary = contentColor.copy(alpha = PANEL_FIELD_SECONDARY_ALPHA)
    TextField(
        modifier = modifier,
        value = query,
        onValueChange = onQueryChanged,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions =
            KeyboardActions(
                onSearch = {
                    onSearch()
                    keyboardController?.hide()
                },
            ),
        placeholder = { Text(text = label) },
        colors =
            TextFieldDefaults.colors(
                focusedTextColor = contentColor,
                unfocusedTextColor = contentColor,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = contentColor,
                focusedPlaceholderColor = secondary,
                unfocusedPlaceholderColor = secondary,
                focusedTrailingIconColor = contentColor,
                unfocusedTrailingIconColor = contentColor,
            ),
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
