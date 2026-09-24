@file:Suppress("ForbiddenImport")

package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * The single blocking read of the home layout set, once per process.
 *
 * The launcher cannot draw a home screen before it knows the layout, and the view model builds its
 * initial state synchronously, so this first read waits (on [Dispatchers.IO]). Every read after it is
 * served from memory by [WriteBehindHomeLayoutRepository], and every write goes behind, off the main
 * thread. Making startup itself asynchronous (a loading state before the first frame) is tracked as a
 * follow-up to #1198; until then this file is the one sanctioned use of `runBlocking` for layouts.
 */
internal fun loadHomeLayoutSetAtStartup(store: DataStoreHomeLayoutStore): HomeLayoutSet? =
    runBlocking(Dispatchers.IO) { store.read() }
