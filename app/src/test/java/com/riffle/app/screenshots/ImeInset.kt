package com.riffle.app.screenshots

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Simulates the keyboard: a window inset for the input method of [heightPx] at the bottom (0 takes it away again),
 * dispatched to the compose view the way the platform does. Compose reads it as `WindowInsets.ime`.
 */
internal fun AndroidComposeTestRule<*, ComponentActivity>.setImeInset(heightPx: Int) {
    val content = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
    val insets =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, heightPx))
            .setVisible(WindowInsetsCompat.Type.ime(), heightPx > 0)
            .build()
    runOnUiThread { ViewCompat.dispatchApplyWindowInsets(content, insets) }
    waitForIdle()
}
