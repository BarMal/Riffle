package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.SourceSubscription
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedTypedSourceTest {
    private var connects = 0
    private var cancels = 0
    private var sink: ((TypedSourceState<String>) -> Unit)? = null
    private val source =
        SharedTypedSource<String> { emit ->
            connects++
            sink = emit
            SourceSubscription { cancels++ }
        }

    @Test
    fun `observers share one upstream, replay the latest and the upstream stops with the last observer`() {
        val first = mutableListOf<TypedSourceState<String>>()
        val a = source.subscribe { first += it }
        sink!!(TypedSourceState.Ready(listOf("x")))
        val second = mutableListOf<TypedSourceState<String>>()
        val b = source.subscribe { second += it }

        assertEquals(1, connects)
        assertEquals(listOf<TypedSourceState<String>>(TypedSourceState.Ready(listOf("x"))), second)
        a.cancel()
        assertEquals(0, cancels)
        sink!!(TypedSourceState.Off as TypedSourceState<String>)
        assertEquals<TypedSourceState<String>>(TypedSourceState.Off, second.last())
        assertEquals(2, first.size)
        b.cancel()
        assertEquals(1, cancels)

        val third = mutableListOf<TypedSourceState<String>>()
        source.subscribe { third += it }
        assertEquals(2, connects)
        assertEquals(listOf<TypedSourceState<String>>(TypedSourceState.Loading), third)
    }
}
