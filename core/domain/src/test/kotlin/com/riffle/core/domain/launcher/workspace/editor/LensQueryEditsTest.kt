package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.Lens
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceParameter
import com.riffle.core.domain.launcher.workspace.sources.MAX_SEARCH_QUERY_LENGTH
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LensQueryEditsTest {
    private val search = SourceIds.SEARCH
    private val context =
        EditContext(sources = DESCRIPTORS + SourceDescriptor(search, setOf(SourceCapability.SEARCHABLE)))
    private val target = FlowMode.EditPage(cid("p"))

    private fun searchPage(query: String? = null): PageContainer {
        val parameters = query?.let { mapOf(search to assertNotNull(SourceParameter.query(it))) }.orEmpty()
        return PageContainer(
            cid("p"),
            PageContent.Bound(LensBinding(Lens(listOf(search), parameters = parameters), ExpressionKind.LIST)),
        )
    }

    private fun queryOf(session: WorkspaceEditSession): String? =
        (
            (session.draft.pages.single() as PageContainer).content as PageContent.Bound
        ).binding.lens.parameterFor(search)?.text

    private fun attempted(result: QueryEditResult): SessionStep = assertIs<QueryEditResult.Attempted>(result).step

    @Test
    fun setQueryIsAnEditThatUndoAndRedoRevert() {
        var session = WorkspaceEditSession(workspace(searchPage()))
        session = attempted(LensQueryEdits.setQuery(session, target, search, "  camera ", context)).session
        assertEquals("camera", queryOf(session))
        assertTrue(session.isDirty)

        session = session.undo()
        assertNull(queryOf(session))
        session = session.redo()
        assertEquals("camera", queryOf(session))
    }

    @Test
    fun clearQueryRemovesItAndBlankTextClears() {
        var session = WorkspaceEditSession(workspace(searchPage("mail")))
        session = attempted(LensQueryEdits.clearQuery(session, target, search, context)).session
        assertNull(queryOf(session))

        session = WorkspaceEditSession(workspace(searchPage("mail")))
        session = attempted(LensQueryEdits.setQuery(session, target, search, "   ", context)).session
        assertNull(queryOf(session))
    }

    @Test
    fun theSameQueryIsNotRecordedInHistory() {
        val session = WorkspaceEditSession(workspace(searchPage("mail")))
        val step = attempted(LensQueryEdits.setQuery(session, target, search, " mail ", context))
        assertSame(session, step.session)
    }

    @Test
    fun overlongQueriesAreCut() {
        val session = WorkspaceEditSession(workspace(searchPage()))
        val next = attempted(LensQueryEdits.setQuery(session, target, search, "x".repeat(500), context)).session
        assertEquals(MAX_SEARCH_QUERY_LENGTH, queryOf(next)?.length)
    }

    @Test
    fun refusesAnUnsupportedSourceAMissingTargetAndASourceNotInTheLens() {
        val session = WorkspaceEditSession(workspace(searchPage(), boundPage("apps-page")))
        val apps = FlowMode.EditPage(cid("apps-page"))

        val unsupported = LensQueryEdits.setQuery(session, apps, APPS, "x", context)
        assertEquals(QueryEditResult.Refused(QueryEditRefusal.UnsupportedSource), unsupported)
        val notInLens = LensQueryEdits.setQuery(session, apps, search, "x", context)
        assertEquals(QueryEditResult.Refused(QueryEditRefusal.SourceNotInLens), notInLens)
        val missing = LensQueryEdits.setQuery(session, FlowMode.EditPage(cid("zzz")), search, "x", context)
        assertEquals(QueryEditResult.Refused(QueryEditRefusal.NoExistingBinding), missing)
        val adding = LensQueryEdits.setQuery(session, FlowMode.Add, search, "x", context)
        assertEquals(QueryEditResult.Refused(QueryEditRefusal.NoExistingBinding), adding)
    }

    @Test
    fun theEditorGateStillAppliesAndALensKeepsItsExpressionValidity() {
        // A query changes no field a lens projects or shape it produces, so pairing validity is untouched.
        val session = WorkspaceEditSession(workspace(searchPage()))
        val step = attempted(LensQueryEdits.setQuery(session, target, search, "x", context))
        assertIs<EditResult.Applied>(step.result)
    }

    @Test
    fun changingTheQueryDetachesASavedLensReference() {
        val ref = com.riffle.core.domain.launcher.workspace.LensId("saved")
        val page =
            PageContainer(
                cid("p"),
                PageContent.Bound(LensBinding(Lens(listOf(search)), ExpressionKind.LIST, ref)),
            )
        val session = WorkspaceEditSession(workspace(page))
        val next = attempted(LensQueryEdits.setQuery(session, target, search, "x", context)).session
        val binding = ((next.draft.pages.single() as PageContainer).content as PageContent.Bound).binding
        assertNull(binding.ref)
        assertEquals("x", binding.lens.parameterFor(search)?.text)
    }

    @Test
    fun theFlowCarriesQueriesIntoTheBindingAndDropsThemWithTheirSource() {
        val ctx = BindingFlowContext(choices() + searchChoice(), workspace(boundPage("a")), ids = counterIds())
        val start = BindingFlow.start(ctx)
        val reduce = { s: BindingFlowState, a: BindingFlowAction -> BindingFlowReducer.reduce(s, a, ctx) }

        val noSource = reduce(start, BindingFlowAction.SetQuery(search, "x"))
        assertEquals(start, noSource)

        var state = reduce(start, BindingFlowAction.ToggleSource(search))
        state = reduce(state, BindingFlowAction.SetQuery(search, " mail "))
        assertEquals("mail", BindingFlow.lens(state)?.parameterFor(search)?.text)
        state = reduce(state, BindingFlowAction.ApplyPreset(LensPreset.NEWEST_FIRST))
        assertEquals("mail", BindingFlow.lens(state)?.parameterFor(search)?.text)

        val cleared = reduce(state, BindingFlowAction.SetQuery(search, ""))
        assertNull(BindingFlow.lens(cleared)?.parameterFor(search))

        val removed =
            reduce(reduce(state, BindingFlowAction.ToggleSource(APPS)), BindingFlowAction.ToggleSource(search))
        assertTrue(removed.draft.parameters.isEmpty())
    }

    @Test
    fun editingAnExistingBindingInTheFlowStartsFromItsQuery() {
        val lens = Lens(listOf(search), parameters = mapOf(search to assertNotNull(SourceParameter.query("mail"))))
        assertEquals(lens, LensDraft.from(lens).toLens())
    }

    @Test
    fun seededRandomQueryEditsKeepTheDraftConsistentAndUndoable() {
        repeat(SEEDS) { seed ->
            val random = Random(seed)
            var session = WorkspaceEditSession(workspace(searchPage()))
            val history = ArrayList<String?>().apply { add(null) }
            repeat(STEPS) {
                val text = if (random.nextInt(4) == 0) "" else "q${random.nextInt(5)}"
                val step = attempted(LensQueryEdits.setQuery(session, target, search, text, context))
                val expected = text.ifBlank { null }
                assertEquals(expected, queryOf(step.session), "seed $seed")
                if (step.session !== session) history += expected
                session = step.session
            }
            while (session.canUndo) {
                history.removeAt(history.size - 1)
                session = session.undo()
                assertEquals(history.last(), queryOf(session), "seed $seed")
            }
        }
    }

    private fun searchChoice() =
        SourceChoices.build(
            listOf(SourceDescriptor(search, setOf(SourceCapability.SEARCHABLE))),
        )

    private companion object {
        const val SEEDS = 60
        const val STEPS = 25
    }
}
