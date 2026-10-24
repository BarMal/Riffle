package com.riffle.app.launcher.pool

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** A read-only home (the pool cannot be used this process) refuses edits; say so (toasts are announced by TalkBack). */
internal fun ComponentActivity.showBlockedHomeEdits(readOnlyHome: ReadOnlyHomeLayoutRepository?) {
    if (readOnlyHome == null) return
    lifecycleScope.launch {
        readOnlyHome.blockedEdits.collect {
            Toast.makeText(this@showBlockedHomeEdits, READ_ONLY_HOME_NOTICE, Toast.LENGTH_LONG).show()
        }
    }
}
