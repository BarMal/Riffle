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
