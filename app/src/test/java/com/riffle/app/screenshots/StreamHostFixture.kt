package com.riffle.app.screenshots

import com.riffle.app.launcher.expressions.FakeExpressionImageLoader
import com.riffle.app.launcher.stream.CachedStreamLayoutRepository
import com.riffle.app.launcher.stream.StreamHost
import com.riffle.app.launcher.stream.StreamLayoutStorePort
import com.riffle.core.domain.launcher.stream.StreamActionResult
import com.riffle.core.domain.launcher.stream.StreamContentProvider
import com.riffle.core.domain.launcher.stream.StreamContext
import com.riffle.core.domain.launcher.stream.StreamLayouts
import com.riffle.core.domain.launcher.workspace.ItemSource
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job

private const val STREAM_HOST_TEST_NOW = 1_767_225_600_000L

/** A [StreamHost] over no sources, with a fixed clock: the Stream draws its states, the Dock its Stream button. */
internal fun emptyStreamHost(): StreamHost =
    StreamHost(
        provider =
            StreamContentProvider(
                object : SourceRegistry {
                    override fun descriptors(): List<SourceDescriptor> = emptyList()

                    override fun source(id: SourceId): ItemSource? = null
                },
                context = { StreamContext(nowEpochMillis = STREAM_HOST_TEST_NOW) },
            ),
        layouts =
            CachedStreamLayoutRepository(
                object : StreamLayoutStorePort {
                    override suspend fun read() = null

                    override suspend fun write(layouts: StreamLayouts) = Unit
                },
                CoroutineScope(Dispatchers.Unconfined + Job()),
            ),
        actions = { _, _ -> StreamActionResult.NotOffered },
        imageLoader = FakeExpressionImageLoader(),
        formats = FixedStreamFormats(),
    )
