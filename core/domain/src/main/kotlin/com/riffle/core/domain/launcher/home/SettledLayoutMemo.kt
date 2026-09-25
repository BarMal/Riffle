package com.riffle.core.domain.launcher.home

/**
 * Skips a pure layout transform whose inputs have not changed since it last left a layout alone.
 *
 * The transform must be a pure function of the layout and the inputs. When it returns the layout
 * unchanged, that (layout, inputs) pair is remembered as settled; asking again with an equal pair
 * returns the layout without recomputing. Anything else -- a different layout (grid included) or
 * different inputs -- recomputes. Because only no-op results are remembered, the answer is always
 * exactly what the transform would have returned.
 *
 * Holds one entry, replaced atomically, so it is safe to share across threads.
 */
class SettledLayoutMemo<I> {
    @Volatile
    private var settled: SettledLayout<I>? = null

    fun transformUnlessSettled(
        layout: HomeLayout,
        inputs: I,
        transform: (HomeLayout) -> HomeLayout,
    ): HomeLayout {
        val known = settled
        if (known != null && known.layout == layout && known.inputs == inputs) {
            return layout
        }
        return transform(layout).also { result ->
            if (result == layout) {
                settled = SettledLayout(layout = layout, inputs = inputs)
            }
        }
    }

    private class SettledLayout<I>(
        val layout: HomeLayout,
        val inputs: I,
    )
}
