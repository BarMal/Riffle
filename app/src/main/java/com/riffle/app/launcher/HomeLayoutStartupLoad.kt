@file:Suppress("ForbiddenImport")

package com.riffle.app.launcher

import com.riffle.app.launcher.pool.CachedPoolRepository
import com.riffle.app.launcher.pool.HomeLayoutCutoverStore
import com.riffle.app.launcher.pool.HomeLayoutOpening
import com.riffle.app.launcher.pool.PoolHomeLayoutRepository
import com.riffle.app.launcher.pool.openHomeLayouts
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
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
 * The single blocking read of the home layout set (and, with it, the placed-items pool), once per process.
 *
 * The launcher cannot draw a home screen before it knows the layout, and the view model builds its
 * initial state synchronously, so this first read waits (on [Dispatchers.IO]). Every read after it is
 * served from memory by [WriteBehindHomeLayoutRepository] (and [PoolHomeLayoutRepository]), and every
 * write goes behind, off the main thread. Making startup itself asynchronous (a loading state before
 * the first frame) is tracked as a follow-up to #1198. Since the S4 cutover the pool is read in the same
 * step, so the first frame already shows the pool's items; see [openHomeLayouts] for the cutover and
 * fallback rules. Together with [flushHomeLayoutBlocking] and [flushPoolHomeBlocking] this file holds the
 * only sanctioned uses of `runBlocking` for layouts.
 */
internal fun openHomeLayoutsAtStartup(
    store: HomeLayoutCutoverStore,
    pool: CachedPoolRepository,
    enabled: Boolean,
    providerOf: (HostedWidgetId) -> WidgetProviderIdentity?,
): HomeLayoutOpening = runBlocking(Dispatchers.IO) { openHomeLayouts(store, pool, enabled, providerOf = providerOf) }

/** [flushHomeLayoutBlocking] for the placed-items pool: `onStop` waits for the pool write before the layout set's. */
internal fun flushPoolHomeBlocking(
    repository: PoolHomeLayoutRepository,
    timeoutMillis: Long = HOME_LAYOUT_STOP_FLUSH_TIMEOUT_MILLIS,
) {
    runBlocking(Dispatchers.IO) { withTimeoutOrNull(timeoutMillis) { repository.flushNow() } }
}

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
