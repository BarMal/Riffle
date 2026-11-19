package com.riffle.app.launcher

import android.util.LruCache

/**
 * A synchronized, entry-count-bounded cache backed by the platform's [LruCache] instead of a
 * hand-rolled `LinkedHashMap` + `removeEldestEntry` replica -- eviction order, bound
 * enforcement, and thread-safety are the platform's own tested implementation, not ours.
 * [LruCache] itself rejects null values, so a caller that needs to remember "looked up, found
 * nothing" distinctly from "never looked up" wraps its value type in a non-null payload (see
 * `PackageManagerAppIconLoader`'s `DominantColorEntry` and `AdaptiveStageArtworkCache`'s own
 * wrapper for two examples of that pattern).
 */
internal class BoundedCache<Key : Any, Value : Any>(maxEntries: Int) {
    private val delegate = LruCache<Key, Value>(maxEntries)

    operator fun get(key: Key): Value? = delegate.get(key)

    operator fun set(
        key: Key,
        value: Value,
    ) {
        delegate.put(key, value)
    }

    fun evictAll() = delegate.evictAll()

    val size: Int
        get() = delegate.size()
}

/**
 * The same cache bounded by the bytes its values hold ([sizeOf] each), not by their count: an icon bitmap's size
 * depends on the screen density and the cache must not grow with the number of styles drawn. Least recently used
 * entries are evicted first; a value larger than the whole bound is not kept.
 */
internal class ByteBoundedCache<Key : Any, Value : Any>(
    maxBytes: Int,
    private val sizeOf: (Value) -> Int,
) {
    private val delegate =
        object : LruCache<Key, Value>(maxBytes) {
            override fun sizeOf(
                key: Key,
                value: Value,
            ): Int = this@ByteBoundedCache.sizeOf(value).coerceAtLeast(1)
        }

    operator fun get(key: Key): Value? = delegate.get(key)

    operator fun set(
        key: Key,
        value: Value,
    ) {
        delegate.put(key, value)
    }

    fun evictAll() = delegate.evictAll()

    /** The bytes held now (the sum of [sizeOf]): never above [maxBytes]. */
    val bytes: Int
        get() = delegate.size()

    val entryCount: Int
        get() = delegate.snapshot().size

    val maxBytes: Int
        get() = delegate.maxSize()
}
