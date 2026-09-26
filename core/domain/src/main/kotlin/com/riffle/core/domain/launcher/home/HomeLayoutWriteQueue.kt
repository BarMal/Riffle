package com.riffle.core.domain.launcher.home

/**
 * The in-memory home layout set plus the bookkeeping for writing it behind to durable storage.
 *
 * Callers read and replace the set here synchronously; a background writer asks for the
 * [pendingWrite], persists it, and reports back with [markWritten]. Only the newest unwritten
 * version is ever handed out, and versions only move forward, so a slow write can never land after
 * (and overwrite) a newer one: writes may be coalesced but never reordered.
 *
 * Thread-safe: every access goes through one lock, and the lock is never held while persisting.
 */
class HomeLayoutWriteQueue(
    initialLayoutSet: HomeLayoutSet?,
) {
    private val lock = Any()
    private var latestLayoutSet: HomeLayoutSet? = initialLayoutSet
    private var latestVersion = 0L
    private var writtenVersion = 0L

    /** The newest layout set, written or not. */
    fun current(): HomeLayoutSet? = synchronized(lock) { latestLayoutSet }

    /** Replaces the in-memory layout set and returns the version it must be persisted as. */
    fun enqueue(layoutSet: HomeLayoutSet): Long =
        synchronized(lock) {
            latestLayoutSet = layoutSet
            latestVersion += 1
            latestVersion
        }

    /** Whether [version] is still the newest enqueued version, i.e. nothing has superseded it. */
    fun isLatest(version: Long): Boolean = synchronized(lock) { version == latestVersion }

    val hasPendingWrite: Boolean
        get() = synchronized(lock) { latestVersion > writtenVersion }

    /** The newest layout set not yet persisted, or null when storage is already up to date. */
    fun pendingWrite(): PendingHomeLayoutWrite? =
        synchronized(lock) {
            latestLayoutSet
                ?.takeIf { latestVersion > writtenVersion }
                ?.let { layoutSet -> PendingHomeLayoutWrite(version = latestVersion, layoutSet = layoutSet) }
        }

    /** Records that [version] reached storage. Older reports never move the watermark back. */
    fun markWritten(version: Long) {
        synchronized(lock) {
            writtenVersion = maxOf(writtenVersion, version)
        }
    }
}

data class PendingHomeLayoutWrite(
    val version: Long,
    val layoutSet: HomeLayoutSet,
)
