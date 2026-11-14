package com.riffle.app.launcher

import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView

/**
 * Puts the keyboard away for good before the field that raised it leaves the screen.
 *
 * Compose's keyboard controller only hides while a text input session is live, so it is asked first, while the field
 * still holds focus; then the window is asked directly, which also reaches a keyboard that is still on its way up; and
 * only then is focus cleared. Clearing focus first ends the session and leaves a rising keyboard up over what the page
 * shows next.
 */
@Composable
internal fun rememberKeyboardDismisser(): () -> Unit {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    return remember(keyboard, focus, view) {
        {
            keyboard?.hide()
            val inputMethods = view.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            inputMethods?.hideSoftInputFromWindow(view.windowToken, 0)
            focus.clearFocus()
        }
    }
}
