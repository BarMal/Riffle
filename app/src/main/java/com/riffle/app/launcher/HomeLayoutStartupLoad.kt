@file:Suppress("ForbiddenImport")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Upper bound on how long `onStop` waits for the pending layout write, well inside the platform's
 * ANR budget. A write still running at the deadline is cancelled and stays pending, so the next
 * save or flush retries it.
 */
internal const val HOME_LAYOUT_STOP_FLUSH_TIMEOUT_MILLIS = 2_000L

/**
 * The single blocking read of the home layout set, once per process.
 *
 * The launcher cannot draw a home screen before it knows the layout, and the view model builds its
 * initial state synchronously, so this first read waits (on [Dispatchers.IO]). Every read after it is
 * served from memory by [WriteBehindHomeLayoutRepository], and every write goes behind, off the main
 * thread. Making startup itself asynchronous (a loading state before the first frame) is tracked as a
 * follow-up to #1198. Together with [flushHomeLayoutBlocking] this file holds the only two sanctioned
 * uses of `runBlocking` for layouts.
 */
internal fun loadHomeLayoutSetAtStartup(store: DataStoreHomeLayoutStore): HomeLayoutSet? =
    runBlocking(Dispatchers.IO) { store.read() }

/**
 * Blocks the caller until [repository]'s newest unsaved layout set has been written, or until
 * [timeoutMillis] passes. Used from `onStop`: after it returns the process may be killed without
 * warning, so this is the last point at which the latest edit can be made durable.
 */
internal fun flushHomeLayoutBlocking(
    repository: WriteBehindHomeLayoutRepository,
    timeoutMillis: Long = HOME_LAYOUT_STOP_FLUSH_TIMEOUT_MILLIS,
) {
    runBlocking(Dispatchers.IO) { withTimeoutOrNull(timeoutMillis) { repository.flushNow() } }
}
