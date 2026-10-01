package com.riffle.app.launcher.containers

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.riffle.app.launcher.expressions.ExpressionEnvironment
import com.riffle.app.launcher.expressions.ExpressionState
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemAction
import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensResult
import com.riffle.core.domain.launcher.workspace.container.LensAvailability
import com.riffle.core.domain.launcher.workspace.container.LensOutput
import com.riffle.core.domain.launcher.workspace.container.LensResultProvider

/** What a tap on an item, one of its actions, or a group heading should do. Supplied by the surface. */
data class ContainerActions(
    val onItemClick: (Item) -> Unit = {},
    val onAction: (Item, ItemAction) -> Unit = { _, _ -> },
    val onGroupClick: (ItemGroup) -> Unit = {},
    /** Turns on every source [Lens] reads (the "Turn on" button of a lens whose source is off). */
    val onEnableSources: (Lens) -> Unit = {},
)

/**
 * Everything a container host needs from its surface besides the container itself: where lens results
 * come from ([provider], shared by every container so each source has one subscription), how expressions
 * draw ([environment]: images, time, insets, reduced motion) and what interactions do ([actions]).
 */
data class ContainerServices(
    val provider: LensResultProvider,
    val environment: ExpressionEnvironment = ExpressionEnvironment(),
    val actions: ContainerActions = ContainerActions(),
)

/** User-visible fallbacks for container-level states. */
object ContainerText {
    const val PERMISSION_REQUIRED = "Allow access to show this"
    const val UNAVAILABLE = "This content is not available"
    const val OFF = "This source is turned off"
    const val ENABLE = "Turn on"
    const val NEEDS_GROUPED = "This page set needs a grouped lens"
    const val OTHER_PAGE = "Other"
}

/** The state an expression should draw for this output; [onEnable] is what its Turn on button does. */
internal fun LensOutput.toExpressionState(onEnable: () -> Unit = {}): ExpressionState =
    when (availability) {
        LensAvailability.LOADING -> ExpressionState.Loading
        LensAvailability.READY -> ExpressionState.Ready
        LensAvailability.PERMISSION_REQUIRED -> ExpressionState.Unavailable(ContainerText.PERMISSION_REQUIRED)
        LensAvailability.OFF -> ExpressionState.Off(ContainerText.OFF, ContainerText.ENABLE, onEnable)
        LensAvailability.UNAVAILABLE -> ExpressionState.Unavailable(ContainerText.UNAVAILABLE)
    }

/** The result to draw; not-ready outputs draw an empty one under their loading or unavailable state. */
internal fun LensOutput.resultOrEmpty(): LensResult = result ?: LensResult.Flat(emptyList())

/**
 * Observes [lens] through [provider] for as long as the caller stays in the composition. The provider
 * evaluates off the main thread and this writes the latest output into snapshot state (safe from any
 * thread); leaving the composition or changing the lens cancels the observation, which detaches from the
 * sources and stops any in-flight evaluation.
 */
@Composable
internal fun rememberLensOutput(
    provider: LensResultProvider,
    lens: Lens,
): State<LensOutput> {
    val output = remember(provider, lens) { mutableStateOf(LensOutput.Loading) }
    DisposableEffect(provider, lens) {
        val handle = provider.observe(lens) { next -> output.value = next }
        onDispose { handle.cancel() }
    }
    return output
}
