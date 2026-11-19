package com.riffle.app.launcher

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.riffle.core.domain.launcher.dock.PairAvailability

/** What [PairAvailability.canSplit] needs to know about the launcher's window. */
internal data class SplitFacts(
    val sdkInt: Int,
    val windowWidthDp: Int,
    val inMultiWindow: Boolean,
) {
    val canSplit: Boolean get() = PairAvailability.canSplit(sdkInt, windowWidthDp, inMultiWindow)
}

/**
 * Overrides the facts read from the device. Null (the default) reads them: the platform SDK, the window's width and
 * whether the launcher is itself in multi-window mode. Tests provide their own to fold and unfold a window.
 */
internal val LocalSplitFacts = compositionLocalOf<SplitFacts?> { null }

@Composable
internal fun currentSplitFacts(): SplitFacts {
    val override = LocalSplitFacts.current
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    return override
        ?: SplitFacts(
            sdkInt = Build.VERSION.SDK_INT,
            windowWidthDp = configuration.screenWidthDp,
            inMultiWindow = context.findActivity()?.isInMultiWindowMode ?: false,
        )
}
