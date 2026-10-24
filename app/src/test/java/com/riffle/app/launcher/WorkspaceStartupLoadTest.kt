package com.riffle.app.launcher

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** S9, design 1.2: the startup read of the workspaces is a hard cap, and a late load still lands. */
class WorkspaceStartupLoadTest {
    /** A store whose read blocks its thread (uninterruptible work, like a large decode) until [release]. */
    private class SlowStore : WorkspaceStorePort {
        val release = CountDownLatch(1)

        override suspend fun read(): WorkspaceSet? {
            release.await(READ_LIMIT_SECONDS, TimeUnit.SECONDS)
            return null
        }

        override suspend fun write(set: WorkspaceSet) = Unit
    }

    private class QuickStore : WorkspaceStorePort {
        override suspend fun read(): WorkspaceSet? = null

        override suspend fun write(set: WorkspaceSet) = Unit
    }

    @Test
    fun aQuickReadLoadsBeforeTheFirstFrameAndSeedsTheDefault() {
        val repository = CachedWorkspaceRepository(QuickStore(), CoroutineScope(Job()))

        assertTrue(loadWorkspacesAtStartup(repository, timeoutMillis = 2_000))
        assertTrue(repository.load()!!.hasLayout(HomeLayoutDeviceClass.PHONE))
    }

    @Test
    fun aBlockingReadIsCutOffAtTheCapAndItsResultStillArrivesLater() {
        val store = SlowStore()
        val repository = CachedWorkspaceRepository(store, CoroutineScope(Job()))

        val started = System.nanoTime()
        val loaded = loadWorkspacesAtStartup(repository, timeoutMillis = 100)
        val waitedMillis = (System.nanoTime() - started) / 1_000_000

        assertFalse(loaded)
        assertTrue("waited $waitedMillis ms", waitedMillis < 1_500)
        assertTrue(repository.load() == null)

        store.release.countDown()
        runBlocking { withTimeout(5_000) { repository.version.first { it > 0 } } }
        assertNotNull("the late load fills the repository like any other load", repository.load())
    }

    private companion object {
        const val READ_LIMIT_SECONDS = 10L
    }
}
