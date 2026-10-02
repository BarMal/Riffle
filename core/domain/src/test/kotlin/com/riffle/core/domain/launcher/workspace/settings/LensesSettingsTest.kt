package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.BreakPolicy
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.ExpressionKind
import com.riffle.core.domain.launcher.workspace.LL_APPS
import com.riffle.core.domain.launcher.workspace.LL_NOTES
import com.riffle.core.domain.launcher.workspace.LayoutCapabilities
import com.riffle.core.domain.launcher.workspace.LayoutWorkspaces
import com.riffle.core.domain.launcher.workspace.LensBinding
import com.riffle.core.domain.launcher.workspace.LensGroup
import com.riffle.core.domain.launcher.workspace.LensId
import com.riffle.core.domain.launcher.workspace.LensLibrary
import com.riffle.core.domain.launcher.workspace.LensOrigin
import com.riffle.core.domain.launcher.workspace.LibraryProblem
import com.riffle.core.domain.launcher.workspace.MAX_SAVED_LENSES
import com.riffle.core.domain.launcher.workspace.PageContainer
import com.riffle.core.domain.launcher.workspace.PageContent
import com.riffle.core.domain.launcher.workspace.PageSetContainer
import com.riffle.core.domain.launcher.workspace.RemovePolicy
import com.riffle.core.domain.launcher.workspace.SavedLens
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.Workspace
import com.riffle.core.domain.launcher.workspace.WorkspaceDock
import com.riffle.core.domain.launcher.workspace.WorkspaceId
import com.riffle.core.domain.launcher.workspace.WorkspaceSet
import com.riffle.core.domain.launcher.workspace.allBindings
import com.riffle.core.domain.launcher.workspace.llBinding
import com.riffle.core.domain.launcher.workspace.llCounter
import com.riffle.core.domain.launcher.workspace.llLayout
import com.riffle.core.domain.launcher.workspace.llLens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LensesSettingsTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET
    private val descriptors =
        listOf(
            SourceDescriptor(LL_APPS, setOf(SourceCapability.GROUPABLE)),
            SourceDescriptor(LL_NOTES, setOf(SourceCapability.GROUPABLE)),
        )
    private val flat = llLens()
    private val grouped = llLens(LL_NOTES, group = LensGroup.ByGroupKey)
    private val idFlat = LensId("flat")
    private val idGrouped = LensId("grouped")
    private val idSpare = LensId("spare")

    /** w1's list page and dock use "Flat"; its page-set uses "Grouped"; w2's list page uses "Flat". */
    private val layout: LayoutWorkspaces =
        llLayout().let { base ->
            val bound =
                base.copy(
                    workspaces =
                        base.workspaces.map { ws ->
                            ws.copy(
                                pages =
                                    ws.pages.map { page ->
                                        when {
                                            page.id.value.endsWith("-list") ->
                                                (page as PageContainer).copy(
                                                    content =
                                                        PageContent.Bound(
                                                            llBinding(lens = flat, ref = idFlat),
                                                        ),
                                                )
                                            page.id.value.endsWith("-set") ->
                                                (page as PageSetContainer).copy(
                                                    binding = llBinding(ExpressionKind.CARD_STACK, grouped, idGrouped),
                                                )
                                            else -> page
                                        }
                                    },
                            )
                        },
                )
            bound.copy(
                library =
                    LensLibrary(
                        listOf(
                            SavedLens(idGrouped, "grouped notes", grouped),
                            SavedLens(idFlat, "Flat apps", flat),
                            SavedLens(idSpare, "Spare", llLens(limit = 3), LensOrigin.Preset("nova", "x")),
                        ),
                    ),
            )
        }
    private val set = WorkspaceSet(mapOf(phone to layout))
    private val env = LensesEnvironment(descriptors, ids = llCounter("new"))

    private fun library(
        s: WorkspaceSet,
        device: HomeLayoutDeviceClass = phone,
    ) = s.workspacesFor(device).library

    @Test
    fun theListIsSortedByNameCaseInsensitivelyWithUsageCounts() {
        val model = LensesSettingsPlanner.plan(set, phone)

        assertEquals(listOf("Flat apps", "grouped notes", "Spare"), model.rows.map { it.name })
        val byName = model.rows.associateBy { it.name }
        assertEquals(2, byName.getValue("Flat apps").usedIn)
        assertEquals(2, byName.getValue("grouped notes").usedIn)
        assertEquals(0, byName.getValue("Spare").usedIn)
        assertFalse(byName.getValue("Flat apps").grouped)
        assertTrue(byName.getValue("grouped notes").grouped)
        assertTrue(byName.getValue("Spare").fromPreset)
        assertEquals(listOf(LL_NOTES), byName.getValue("grouped notes").sources)
        assertFalse(model.isFull)
    }

    @Test
    fun aLayoutWithNoSavedLensesIsAnEmptyList() {
        assertEquals(emptyList(), LensesSettingsPlanner.plan(set, tablet).rows)
    }

    @Test
    fun theDetailListsEveryUserInWorkspaceAndPageOrder() {
        val detail = LensDetailPlanner.plan(layout, idGrouped, "grouped notes", grouped, descriptors)

        assertEquals(listOf("w1", "w2"), detail.usedBy.map { it.workspaceName })
        assertTrue(detail.usedBy.all { it.place == UsePlace.Page(2, PageKind.PAGE_SET) })
        val flatUsers = LensDetailPlanner.usedBy(layout, idFlat)
        assertEquals(UsePlace.Page(1, PageKind.PAGE), flatUsers.first().place)
        assertEquals(ContainerId("w1-list"), flatUsers.first().containerId)
    }

    @Test
    fun anUnchangedDraftHasNothingToSaveAndABrokenOneSaysWhy() {
        val same = LensDetailPlanner.plan(layout, idFlat, "Flat apps", flat, descriptors)
        assertFalse(same.changed)
        assertFalse(same.canSave)

        val noSource = LensDetailPlanner.plan(layout, idFlat, "Flat apps", null, descriptors)
        assertEquals(listOf(LensProblem.NoSource), noSource.problems)
        assertFalse(noSource.canSave)

        val blank = LensDetailPlanner.plan(layout, idFlat, "  ", flat.copy(limit = 2), descriptors)
        assertEquals(listOf(LensProblem.Library(LibraryProblem.BLANK_NAME)), blank.problems)
        val taken = LensDetailPlanner.plan(layout, idFlat, "SPARE", flat, descriptors)
        assertEquals(listOf(LensProblem.Library(LibraryProblem.NAME_TAKEN)), taken.problems)
        val long = LensDetailPlanner.plan(layout, idFlat, "x".repeat(41), flat, descriptors)
        assertEquals(listOf(LensProblem.Library(LibraryProblem.NAME_TOO_LONG)), long.problems)
    }

    @Test
    fun aCleanEditCanBeSavedWithoutAChoice() {
        val detail = LensDetailPlanner.plan(layout, idFlat, "Flat apps", flat.copy(limit = 7), descriptors)

        assertTrue(detail.canSave)
        assertTrue(detail.broken.isEmpty())
        assertFalse(detail.needsChoice)
        assertTrue(ExpressionKind.LIST in detail.drawableAs)
    }

    @Test
    fun anEditThatWouldBreakAPageSetListsTheAffectedContainersWithReasons() {
        // Making the grouped lens flat breaks the page-set that draws one page per group.
        val flatNotes = llLens(LL_NOTES)
        val detail = LensDetailPlanner.plan(layout, idGrouped, "grouped notes", flatNotes, descriptors)

        assertTrue(detail.canSave)
        assertTrue(detail.needsChoice)
        assertEquals(listOf("w1", "w2"), detail.broken.map { it.use.workspaceName })
        assertTrue(detail.broken.all { it.issues.isNotEmpty() })
    }

    @Test
    fun saveRefusesToBreakContainersUnlessTheUserChoosesToDetachThem() {
        val flatNotes = llLens(LL_NOTES)

        val refused = LensesSettingsAction.Save(idGrouped, "grouped notes", flatNotes).applyTo(set, phone, env)
        assertFalse(refused.applied)
        assertEquals(LensesSettingsMessage.BreaksContainers(2), refused.message)
        assertEquals(set, refused.set)

        val detached =
            LensesSettingsAction.Save(idGrouped, "grouped notes", flatNotes, BreakPolicy.DETACH_BROKEN)
                .applyTo(set, phone, env)
        assertTrue(detached.applied)
        assertEquals(LensesSettingsMessage.Saved("grouped notes", 2), detached.message)
        val after = detached.set.workspacesFor(phone)
        assertEquals(flatNotes, after.library.find(idGrouped)?.lens)
        // The broken containers keep drawing their old lens, inline.
        val pageSets = after.workspaces.map { it.pages[1] as PageSetContainer }
        assertTrue(pageSets.all { it.binding.ref == null && it.binding.lens == grouped })
        assertEquals(set, detached.undo(detached.set))
    }

    @Test
    fun saveRenamesAndRefreshesEveryUsersSnapshotAtOnce() {
        val edited = flat.copy(limit = 7)

        val change = LensesSettingsAction.Save(idFlat, "  Apps, short  ", edited).applyTo(set, phone, env)

        assertTrue(change.applied)
        val after = change.set.workspacesFor(phone)
        assertEquals("Apps, short", after.library.find(idFlat)?.name)
        val users = after.allBindings().filter { it.ref == idFlat }
        assertEquals(2, users.size)
        assertTrue(users.all { it.lens == edited })
        assertTrue(change.undoable)
        assertEquals(set, change.undo(change.set))
    }

    @Test
    fun saveOfAnUnchangedDraftOrABadNameChangesNothing() {
        val same = LensesSettingsAction.Save(idFlat, "Flat apps", flat).applyTo(set, phone, env)
        assertEquals(LensesSettingsMessage.NothingToDo, same.message)
        assertFalse(same.applied)

        val taken = LensesSettingsAction.Save(idFlat, "spare", flat.copy(limit = 2)).applyTo(set, phone, env)
        assertEquals(LensesSettingsMessage.Refused(LibraryProblem.NAME_TAKEN), taken.message)
        assertEquals(set, taken.set)

        val gone = LensesSettingsAction.Save(LensId("nope"), "x", flat).applyTo(set, phone, env)
        assertEquals(LensesSettingsMessage.Refused(LibraryProblem.UNKNOWN_LENS), gone.message)
    }

    @Test
    fun saveOfOnlyANameIsARename() {
        val change = LensesSettingsAction.Save(idFlat, "Plain", flat).applyTo(set, phone, env)

        assertEquals(LensesSettingsMessage.Renamed("Plain"), change.message)
        assertEquals("Plain", library(change.set).find(idFlat)?.name)
    }

    @Test
    fun createAddsALensWithAFreshIdAndReportsEveryRefusal() {
        val created = LensesSettingsAction.Create("  Newest  ", flat).applyTo(set, phone, env)

        assertTrue(created.applied)
        assertEquals(LensesSettingsMessage.Created("Newest"), created.message)
        val id = checkNotNull(created.lensId)
        assertEquals("Newest", library(created.set).find(id)?.name)
        assertEquals(4, library(created.set).lenses.size)
        assertFalse(created.undoable)

        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.NAME_TAKEN),
            LensesSettingsAction.Create("flat APPS", flat).applyTo(set, phone, env).message,
        )
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.BLANK_NAME),
            LensesSettingsAction.Create(" ", flat).applyTo(set, phone, env).message,
        )
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.NAME_TOO_LONG),
            LensesSettingsAction.Create("y".repeat(41), flat).applyTo(set, phone, env).message,
        )
    }

    @Test
    fun theHundredLensLimitIsEnforcedWithAClearReason() {
        val full =
            layout.copy(
                library =
                    LensLibrary(
                        (0 until MAX_SAVED_LENSES).map {
                            SavedLens(
                                LensId("l$it"),
                                "Lens $it",
                                llLens(limit = it + 1),
                            )
                        },
                    ),
            )
        val fullSet = WorkspaceSet(mapOf(phone to full))

        assertTrue(LensesSettingsPlanner.plan(fullSet, phone).isFull)
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.LIBRARY_FULL),
            LensesSettingsAction.Create("One more", flat).applyTo(fullSet, phone, env).message,
        )
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.LIBRARY_FULL),
            LensesSettingsAction.Duplicate(LensId("l0")).applyTo(fullSet, phone, env).message,
        )
        val detail = LensDetailPlanner.plan(full, null, "One more", flat, descriptors)
        assertEquals(listOf(LensProblem.Library(LibraryProblem.LIBRARY_FULL)), detail.problems)
    }

    @Test
    fun aLensNothingCanDrawIsNotCreatedOrSaved() {
        val noSupport = LensesEnvironment(descriptors, LayoutCapabilities(emptySet()))

        assertEquals(
            LensesSettingsMessage.NothingCanDraw,
            LensesSettingsAction.Create("Nope", flat).applyTo(set, phone, noSupport).message,
        )
        assertEquals(
            LensesSettingsMessage.NothingCanDraw,
            LensesSettingsAction.Save(idFlat, "Flat apps", flat.copy(limit = 2)).applyTo(set, phone, noSupport).message,
        )
        val detail =
            LensDetailPlanner.plan(
                layout,
                idFlat,
                "Flat apps",
                flat.copy(limit = 2),
                descriptors,
                LayoutCapabilities(emptySet()),
            )
        assertEquals(listOf(LensProblem.NothingCanDraw), detail.problems)
    }

    @Test
    fun renameKeepsEveryUserAndChecksTheName() {
        val change = LensesSettingsAction.Rename(idFlat, "Everything").applyTo(set, phone, env)

        assertEquals(LensesSettingsMessage.Renamed("Everything"), change.message)
        assertEquals(layout.allBindings(), change.set.workspacesFor(phone).allBindings())
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.NAME_TAKEN),
            LensesSettingsAction.Rename(idFlat, "spare").applyTo(set, phone, env).message,
        )
        // Renaming to its own name (a different case) is allowed.
        assertTrue(LensesSettingsAction.Rename(idFlat, "FLAT APPS").applyTo(set, phone, env).applied)
    }

    @Test
    fun duplicateInsertsAFreshUniqueCopyAndRebindsNothing() {
        val first = LensesSettingsAction.Duplicate(idFlat).applyTo(set, phone, env)
        val copy = checkNotNull(library(first.set).find(checkNotNull(first.lensId)))

        assertEquals("Flat apps copy", copy.name)
        assertEquals(flat, copy.lens)
        assertNull(copy.origin)
        assertNotEquals(idFlat, copy.id)
        assertEquals(LensesSettingsMessage.Duplicated("Flat apps copy"), first.message)
        assertEquals(layout.allBindings(), first.set.workspacesFor(phone).allBindings())
        val second = LensesSettingsAction.Duplicate(idFlat).applyTo(first.set, phone, env)
        assertEquals("Flat apps copy 2", library(second.set).find(checkNotNull(second.lensId))?.name)
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.UNKNOWN_LENS),
            LensesSettingsAction.Duplicate(LensId("nope")).applyTo(set, phone, env).message,
        )
    }

    @Test
    fun deleteDetachesEveryUserByDefaultAndCanBeUndone() {
        val change = LensesSettingsAction.Delete(idFlat).applyTo(set, phone, env)

        assertEquals(
            LensesSettingsMessage.Deleted("Flat apps", usedBy = 2, detached = 2, replacedBy = null),
            change.message,
        )
        val after = change.set.workspacesFor(phone)
        assertNull(after.library.find(idFlat))
        assertTrue(after.allBindings().none { it.ref == idFlat })
        // They keep drawing exactly what they drew.
        assertEquals(layout.allBindings().map { it.lens }, after.allBindings().map { it.lens })
        assertTrue(change.undoable)
        assertEquals(set, change.undo(change.set))
    }

    @Test
    fun deleteCanReplaceUsersAndListsWhatItDetaches() {
        val other = llLens(limit = 9)
        val withOther =
            set.update(phone) {
                it.copy(library = it.library.copy(lenses = it.library.lenses + SavedLens(LensId("o"), "Other", other)))
            }

        val change =
            LensesSettingsAction.Delete(idFlat, RemovePolicy.ReplaceWith(LensId("o"))).applyTo(withOther, phone, env)

        assertEquals(LensesSettingsMessage.Deleted("Flat apps", 2, 0, "Other"), change.message)
        val after = change.set.workspacesFor(phone)
        assertEquals(2, after.allBindings().count { it.ref == LensId("o") })
        assertTrue(after.allBindings().filter { it.ref == LensId("o") }.all { it.lens == other })

        // A replacement a page-set cannot use detaches that user instead of breaking it.
        val broken =
            LensesSettingsAction.Delete(idGrouped, RemovePolicy.ReplaceWith(LensId("o"))).applyTo(withOther, phone, env)
        assertEquals(LensesSettingsMessage.Deleted("grouped notes", 2, 2, "Other"), broken.message)
        assertTrue(broken.set.workspacesFor(phone).workspaces.all { it.pages[1].id.value.endsWith("-set") })
    }

    @Test
    fun deleteOfAnUnknownLensOrAMissingReplacementChangesNothing() {
        assertEquals(
            LensesSettingsMessage.Refused(LibraryProblem.UNKNOWN_LENS),
            LensesSettingsAction.Delete(LensId("nope")).applyTo(set, phone, env).message,
        )
        val change =
            LensesSettingsAction.Delete(
                idFlat,
                RemovePolicy.ReplaceWith(LensId("nope")),
            ).applyTo(set, phone, env)
        assertFalse(change.applied)
        assertEquals(set, change.set)
    }

    @Test
    fun copyToAnotherLayoutIsAOneTimeCopyWithAFreshIdAndNoOrigin() {
        val change = LensesSettingsAction.CopyToLayout(idSpare, tablet).applyTo(set, phone, env)

        assertTrue(change.applied)
        val copy = checkNotNull(library(change.set, tablet).find(checkNotNull(change.lensId)))
        assertEquals("Spare", copy.name)
        assertNotEquals(idSpare, copy.id)
        assertNull(copy.origin)
        assertEquals(llLens(limit = 3), copy.lens)
        assertEquals(LensesSettingsMessage.CopiedToLayout("Spare", tablet, "Spare"), change.message)
        // The source layout is untouched; no workspace on the target refers to anything.
        assertEquals(layout, change.set.workspacesFor(phone))
        assertTrue(change.set.workspacesFor(tablet).allBindings().none { it.ref != null })
        // Undo removes it again from the target only.
        assertEquals(emptyList(), library(change.undo(change.set), tablet).lenses)
    }

    @Test
    fun aCopyIntoALayoutWithTheNameGetsASuffixAndAFullLayoutRefuses() {
        val once = LensesSettingsAction.CopyToLayout(idSpare, tablet).applyTo(set, phone, env)
        val twice = LensesSettingsAction.CopyToLayout(idSpare, tablet).applyTo(once.set, phone, env)

        assertEquals(LensesSettingsMessage.CopiedToLayout("Spare", tablet, "Spare 2"), twice.message)
        val targets = LensesSettingsPlanner.copyTargets(once.set, phone, idSpare, listOf(phone, tablet))
        assertEquals(listOf(LensCopyTarget(tablet, 1, false, "Spare 2")), targets)

        val full =
            WorkspaceSet(
                mapOf(
                    phone to layout,
                    tablet to
                        LayoutWorkspaces.single(Workspace(WorkspaceId("t"), "T"))
                            .copy(
                                library =
                                    LensLibrary(
                                        (0 until MAX_SAVED_LENSES).map {
                                            SavedLens(LensId("t$it"), "T$it", llLens(limit = it + 1))
                                        },
                                    ),
                            ),
                ),
            )
        assertTrue(LensesSettingsPlanner.copyTargets(full, phone, idSpare, listOf(tablet)).single().full)
        val refused = LensesSettingsAction.CopyToLayout(idSpare, tablet).applyTo(full, phone, env)
        assertEquals(LensesSettingsMessage.Refused(LibraryProblem.LIBRARY_FULL), refused.message)
        assertEquals(full, refused.set)
        assertEquals(
            LensesSettingsMessage.NothingToDo,
            LensesSettingsAction.CopyToLayout(idSpare, phone).applyTo(set, phone, env).message,
        )
    }

    @Test
    fun deletionReplacementsAreEveryOtherLensByName() {
        assertEquals(
            listOf("grouped notes", "Spare"),
            LensesSettingsPlanner.replacements(layout, idFlat).map { it.name },
        )
    }

    @Test
    fun placesDescribeWidgetsAndTheDock() {
        val widgetLens = llLens(limit = 3)
        val ws = llLayout()
        val bound =
            ws.copy(
                library = LensLibrary(listOf(SavedLens(idSpare, "Spare", widgetLens))),
                workspaces =
                    ws.workspaces.map { w ->
                        w.copy(dock = WorkspaceDock(LensBinding(widgetLens, ExpressionKind.ICON_ROW, idSpare)))
                    },
            )
        val places = LensDetailPlanner.usedBy(bound, idSpare).map { it.place }

        assertEquals(listOf(UsePlace.Dock, UsePlace.Dock), places)
    }

    @Test
    fun theBuildersSourceRowsCarryHonestStatusInTheSourcesPageOrder() {
        val all = descriptors + SourceDescriptor(SourceIds.CALENDAR) + SourceDescriptor(SourceIds.MEDIA)
        val choices =
            LensSourceChoices.build(
                descriptors = all,
                statuses =
                    mapOf(
                        SourceIds.CALENDAR to SourceStatus.NEEDS_PERMISSION,
                        SourceIds.MEDIA to SourceStatus.UNAVAILABLE,
                    ),
                disabled = setOf(LL_NOTES),
            )
        val byId = choices.associateBy { it.id }

        assertEquals(SourceStatus.NEEDS_PERMISSION, byId.getValue(SourceIds.CALENDAR).status)
        assertTrue(byId.getValue(SourceIds.CALENDAR).needsPermission)
        assertTrue(byId.getValue(SourceIds.CALENDAR).selectable)
        assertFalse(byId.getValue(SourceIds.MEDIA).selectable)
        // Off is honest and still pickable: a lens is only a definition.
        assertEquals(SourceStatus.OFF, byId.getValue(LL_NOTES).status)
        assertTrue(byId.getValue(LL_NOTES).selectable)
        assertEquals(SourceStatus.LOADING, byId.getValue(LL_APPS).status)
        assertEquals(all.map { it.id }.toSet(), choices.map { it.id }.toSet())
    }
}
