package com.riffle.app.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A test hook that counts how often a composable's scope recomposes (a recomposition is a successful pass that ends in
 * a [SideEffect]). Null (the default) counts nothing and costs one local read per recomposition; a test that wants the
 * count provides one around the content. Used to prove that the open card's motion, which runs every frame, recomposes
 * nothing: Home's cells and the stack's other cards must not recompose per frame.
 */
internal class RecompositionProbe {
    private val counts = mutableMapOf<String, Int>()

    fun hit(key: String) {
        counts[key] = (counts[key] ?: 0) + 1
    }

    fun count(key: String): Int = counts[key] ?: 0

    /** The sum of the counts of every key that starts with [prefix]. */
    fun total(prefix: String): Int = counts.filterKeys { it.startsWith(prefix) }.values.sum()
}

internal val LocalRecompositionProbe = staticCompositionLocalOf<RecompositionProbe?> { null }

/** Inline, so that the effect belongs to the scope of the caller and counts the caller's own recompositions. */
@Composable
internal inline fun CountRecomposition(key: String) {
    val probe = LocalRecompositionProbe.current
    if (probe != null) SideEffect { probe.hit(key) }
}
