package com.riffle.app.launcher.expressions

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.riffle.app.launcher.designsystem.RiffleSpacing
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.ItemGroup
import com.riffle.core.domain.launcher.workspace.LensResult

/**
 * What an expression should show around its [LensResult]. This lives in the UI layer on purpose:
 * expressions never see a source's own state, so whoever hosts one (a container) maps the source
 * state it observes onto this.
 *
 * [Ready] with an empty result draws the empty message; [Loading], [Unavailable] and [Off] replace the
 * content entirely.
 */
sealed interface ExpressionState {
    data object Ready : ExpressionState

    data object Loading : ExpressionState

    /** The data cannot be shown (for example a permission is missing); [message] says why. */
    data class Unavailable(val message: String) : ExpressionState

    /**
     * The user turned the source off in Settings > Sources. [message] says so and the action labelled
     * [actionLabel] runs [onEnable] to turn it back on: an explicit user action, never automatic.
     */
    data class Off(
        val message: String,
        val actionLabel: String,
        val onEnable: () -> Unit,
    ) : ExpressionState
}

/**
 * Everything an expression needs from its host besides the data: how to load images and format
 * times, the insets to pad content by (system bars, cutouts, dock), and the resolved reduced-motion
 * setting. Grouped so each expression keeps a short, uniform signature.
 */
data class ExpressionEnvironment(
    val imageLoader: ExpressionImageLoader = NoExpressionImageLoader,
    val timeFormatter: ExpressionTimeFormatter = RelativeExpressionTimeFormatter,
    val contentPadding: PaddingValues = PaddingValues(),
    val reducedMotion: Boolean = false,
)

/** User-visible fallbacks shared by every expression. */
object ExpressionText {
    const val LOADING = "Loading"
    const val EMPTY = "Nothing to show"
    const val HIDDEN_TITLE = "Hidden content"
}

/** Minimum touch target edge, the same as the largest spacing token (48 dp). */
internal val ExpressionMinTouchTarget: Dp = RiffleSpacing.xxxl

/** Text-heavy expressions stop growing past this width on tablets and unfolded foldables. */
internal val ExpressionMaxReadableWidth: Dp = 640.dp

/** Stable lazy-list key. Source id plus item id, so equal ids from different sources never collide. */
internal fun Item.lazyKey(): String = "${sourceId.value}/${id.value}"

/** Title to draw; redacted (null or blank) titles fall back to a neutral label. */
internal fun Item.displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: ExpressionText.HIDDEN_TITLE

/** Every item of a result in order, without duplicates, so lazy keys are unique. */
internal fun LensResult.allItems(): List<Item> =
    when (this) {
        is LensResult.Flat -> items
        is LensResult.Grouped -> groups.flatMap { it.items }
    }.distinctBy { it.lazyKey() }

/** A flat result as one unlabelled group, so grouped and flat results share one drawing path. */
internal fun LensResult.asGroups(): List<ItemGroup> =
    when (this) {
        is LensResult.Flat -> listOf(ItemGroup(key = "", label = null, items = items.distinctBy { it.lazyKey() }))
        is LensResult.Grouped -> groups.map { it.copy(items = it.items.distinctBy { item -> item.lazyKey() }) }
    }

/** Heading text for a group, or null for the unlabelled group a flat result becomes. */
internal fun ItemGroup.heading(): String? = label?.takeIf { it.isNotBlank() } ?: key.takeIf { it.isNotBlank() }
