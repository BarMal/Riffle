package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedHomeLayoutsTest {
    private val phone = HomeLayoutDeviceClass.PHONE
    private val tablet = HomeLayoutDeviceClass.TABLET

    private fun layout(
        id: String,
        name: String = id,
        kind: SavedHomeLayoutKind = SavedHomeLayoutKind.SAVED,
    ) = SavedHomeLayout(id = id, name = name, kind = kind)

    private fun SavedHomeLayouts.added(
        deviceClass: HomeLayoutDeviceClass,
        layout: SavedHomeLayout,
    ): SavedHomeLayouts = (add(deviceClass, layout) as SavedHomeChange.Changed).layouts

    @Test
    fun aLayoutBelongsToTheDeviceClassItWasSavedOn() {
        val layouts = SavedHomeLayouts().added(phone, layout("a"))
        assertEquals(listOf("a"), layouts.forDeviceClass(phone).map { it.id })
        assertTrue(layouts.forDeviceClass(tablet).isEmpty())
    }

    @Test
    fun theCapIsTwelveSavedLayoutsPerDeviceClass() {
        var layouts = SavedHomeLayouts()
        repeat(SavedHomeLayouts.MAX_PER_DEVICE_CLASS) { layouts = layouts.added(phone, layout("l$it")) }
        assertTrue(layouts.isFull(phone))
        assertIs<SavedHomeChange.Full>(layouts.add(phone, layout("one-too-many")))
        // Another device class has its own twelve.
        assertIs<SavedHomeChange.Changed>(layouts.add(tablet, layout("t")))
    }

    @Test
    fun theAutomaticLayoutDoesNotCountTowardTheCapAndThereIsOnlyOne() {
        var layouts = SavedHomeLayouts()
        repeat(SavedHomeLayouts.MAX_PER_DEVICE_CLASS) { layouts = layouts.added(phone, layout("l$it")) }
        layouts = layouts.withAutomatic(phone, layout("auto1", "Before one"))
        layouts = layouts.withAutomatic(phone, layout("auto2", "Before two"))
        assertEquals(SavedHomeLayouts.MAX_PER_DEVICE_CLASS, layouts.savedCount(phone))
        val automatic = layouts.forDeviceClass(phone).filter { it.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION }
        assertEquals(listOf("auto2"), automatic.map { it.id })
        assertEquals("auto2", layouts.forDeviceClass(phone).last().id)
    }

    @Test
    fun savedLayoutsListInSavingOrderBeforeTheAutomaticOne() {
        var layouts = SavedHomeLayouts().added(phone, layout("a"))
        layouts = layouts.withAutomatic(phone, layout("auto"))
        layouts = layouts.added(phone, layout("b"))
        assertEquals(listOf("a", "b", "auto"), layouts.forDeviceClass(phone).map { it.id })
    }

    @Test
    fun aBlankNameIsRejectedAndNamesAreTidied() {
        assertIs<SavedHomeChange.BlankName>(SavedHomeLayouts().add(phone, layout("a", "   ")))
        val added = SavedHomeLayouts().add(phone, layout("a", "  My   Home\nlayout "))
        assertEquals("My Home layout", (added as SavedHomeChange.Changed).layout.name)
        val long = SavedHomeLayouts().add(phone, layout("b", "x".repeat(100))) as SavedHomeChange.Changed
        assertEquals(SavedHomeLayouts.MAX_NAME_LENGTH, long.layout.name.length)
    }

    @Test
    fun theNextDefaultNameIsTheFirstFreeNumber() {
        val format = { n: Int -> "Home layout $n" }
        assertEquals("Home layout 1", SavedHomeLayouts.nextName(emptyList(), format))
        assertEquals("Home layout 3", SavedHomeLayouts.nextName(listOf("Home layout 1", "home layout 2"), format))
        assertEquals("Home layout 1", SavedHomeLayouts.nextName(listOf("Home layout 2"), format))
    }

    @Test
    fun renamingKeepsTheContentAndRejectsUnknownAndBlank() {
        val layouts = SavedHomeLayouts().added(phone, layout("a", "Old"))
        val renamed = layouts.rename(phone, "a", " New ") as SavedHomeChange.Changed
        assertEquals("New", renamed.layouts.find(phone, "a")?.name)
        assertIs<SavedHomeChange.Unknown>(layouts.rename(phone, "missing", "x"))
        assertIs<SavedHomeChange.BlankName>(layouts.rename(phone, "a", " "))
    }

    @Test
    fun renamingTheAutomaticLayoutKeepsItByPromotingItWhenThereIsRoom() {
        val layouts = SavedHomeLayouts().withAutomatic(phone, layout("auto", "Before x"))
        val renamed = (layouts.rename(phone, "auto", "Keep me") as SavedHomeChange.Changed).layouts
        assertEquals(SavedHomeLayoutKind.SAVED, renamed.find(phone, "auto")?.kind)
        // The next automatic layout no longer overwrites it.
        val next = renamed.withAutomatic(phone, layout("auto2", "Before y"))
        assertEquals(listOf("auto", "auto2"), next.forDeviceClass(phone).map { it.id })
    }

    @Test
    fun promotingTheAutomaticLayoutNeedsRoom() {
        var layouts = SavedHomeLayouts()
        repeat(SavedHomeLayouts.MAX_PER_DEVICE_CLASS) { layouts = layouts.added(phone, layout("l$it")) }
        layouts = layouts.withAutomatic(phone, layout("auto"))
        assertIs<SavedHomeChange.Full>(layouts.rename(phone, "auto", "Keep"))
    }

    @Test
    fun deletingAndRestoringPutsTheLayoutBack() {
        val layouts = SavedHomeLayouts().added(phone, layout("a")).let { it.added(phone, layout("b")) }
        val deleted = layouts.delete(phone, "a") as SavedHomeChange.Changed
        assertNull(deleted.layouts.find(phone, "a"))
        val back = deleted.layouts.restore(phone, deleted.layout) as SavedHomeChange.Changed
        assertEquals(setOf("a", "b"), back.layouts.forDeviceClass(phone).map { it.id }.toSet())
        assertIs<SavedHomeChange.Unknown>(layouts.delete(phone, "missing"))
    }

    @Test
    fun replacingTheContentKeepsTheNameAndKind() {
        val captured = SavedHomeLayout("x", "ignored", savedAtEpochMillis = 99L)
        val layouts = SavedHomeLayouts().added(phone, layout("a", "Mine"))
        val replaced = layouts.replaceContent(phone, "a", captured) as SavedHomeChange.Changed
        assertEquals("Mine", replaced.layout.name)
        assertEquals(99L, replaced.layout.savedAtEpochMillis)
    }
}
