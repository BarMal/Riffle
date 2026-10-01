package com.riffle.core.domain.launcher.workspace.pool

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.WorkspaceSetCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PoolWithLibraryCodecTest {
    @Test
    fun aLayoutWithBothALibraryAndAPoolRoundTrips() {
        val lens = Lens(sources = listOf(SourceId("apps")))
        val page = PageContainer(ContainerId("c"), PageContent.Bound(LensBinding(lens, ExpressionKind.LIST)))
        val one = Workspace(W1, "One", listOf(page))
        val pool = emptyPool(listOf(W1)).add(poolApp("a"), cell = at(0, 0)).add(poolWidget("w"), page = P2)
        val library = LensLibrary(listOf(SavedLens(LensId("l1"), "Apps", lens)))
        val layout = LayoutWorkspaces.single(one).copy(library = library, pool = pool)
        val set = WorkspaceSet(mapOf(HomeLayoutDeviceClass.PHONE to layout))
        val decoded = assertNotNull(WorkspaceSetCodec.decode(WorkspaceSetCodec.encode(set)))
        assertEquals(set, decoded)
    }
}
