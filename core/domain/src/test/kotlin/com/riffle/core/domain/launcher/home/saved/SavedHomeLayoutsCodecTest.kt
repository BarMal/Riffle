package com.riffle.core.domain.launcher.home.saved

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.pool.PoolWidget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedHomeLayoutsCodecTest {
    private val phone = HomeLayoutDeviceClass.PHONE

    private fun sample(): SavedHomeLayouts {
        val captured =
            SavedHomeCapture.capture(
                listOf(
                    page("a", app("mail", 0, 0), folder("f", 1, 0, "maps", "music"), widget("cal", 10, 0, 1)),
                    page("b", stack("s", 0, 0, "calendar" to 10, "weather" to 11)),
                ),
                providers(10 to "calendar", 11 to "weather"),
                "one",
                "Evening",
                SavedHomeLayoutKind.SAVED,
                1234L,
            )
        return SavedHomeLayouts()
            .let { (it.add(phone, captured) as SavedHomeChange.Changed).layouts }
            .withAutomatic(phone, captured.copy(id = "auto", name = "Before Evening"))
    }

    @Test
    fun itRoundTrips() {
        val layouts = sample()
        val decoded = SavedHomeLayoutsCodec.decode(SavedHomeLayoutsCodec.encode(layouts))
        assertEquals(layouts, decoded.layouts)
        assertFalse(decoded.newerThanThisBuild)
    }

    @Test
    fun itWritesNoHostId() {
        val text = SavedHomeLayoutsCodec.encode(sample()).toString()
        assertFalse(text.contains("\"host\""))
        assertEquals(1L, ((SavedHomeLayoutsCodec.encode(sample()).fields["version"]) as StoredValue.Num).value)
    }

    @Test
    fun aHostIdAnOlderOrOutsideWriterLeftIsDropped() {
        val encoded = SavedHomeLayoutsCodec.encode(sample()).toString()
        assertTrue(encoded.isNotEmpty())
        val layouts = SavedHomeLayoutsCodec.decode(SavedHomeLayoutsCodec.encode(sample())).layouts
        assertTrue(
            layouts.forDeviceClass(phone).flatMap {
                it.items.values
            }.filterIsInstance<PoolWidget>().all { it.hostIds.isEmpty() },
        )
    }

    @Test
    fun nothingStoredAndNonsenseReadAsEmpty() {
        assertTrue(SavedHomeLayoutsCodec.decode(null).layouts.isEmpty)
        assertTrue(SavedHomeLayoutsCodec.decode(StoredValue.Str("x")).layouts.isEmpty)
        assertTrue(SavedHomeLayoutsCodec.decode(StoredValue.Obj(emptyMap())).layouts.isEmpty)
    }

    @Test
    fun aMalformedLayoutIsSkippedAndTheRestKept() {
        val good = SavedHomeLayoutsCodec.encode(sample())
        val section = (good.fields["layouts"] as StoredValue.Arr).items.single() as StoredValue.Obj
        val items = (section.fields["items"] as StoredValue.Arr).items
        val broken =
            listOf(
                StoredValue.Str("junk"),
                StoredValue.Obj(mapOf("id" to StoredValue.Str("no-name"))),
                StoredValue.Obj(mapOf("name" to StoredValue.Str("no-id"))),
            )
        val mixed =
            StoredValue.Obj(
                good.fields +
                    (
                        "layouts" to
                            StoredValue.Arr(
                                listOf(
                                    StoredValue.Obj(section.fields + ("items" to StoredValue.Arr(broken + items))),
                                    StoredValue.Obj(mapOf("deviceClass" to StoredValue.Str("NOT_A_CLASS"))),
                                    StoredValue.Str("junk"),
                                ),
                            )
                    ),
            )
        val decoded = SavedHomeLayoutsCodec.decode(mixed).layouts
        assertEquals(listOf("one", "auto"), decoded.forDeviceClass(phone).map { it.id })
    }

    @Test
    fun aValueFromANewerBuildDecodesWhatThisBuildKnowsAndSaysSo() {
        val newer = StoredValue.Obj(SavedHomeLayoutsCodec.encode(sample()).fields + ("version" to StoredValue.Num(2)))
        val decoded = SavedHomeLayoutsCodec.decode(newer)
        assertTrue(decoded.newerThanThisBuild)
        assertEquals(2, decoded.layouts.forDeviceClass(phone).size)
    }

    @Test
    fun moreThanTheCapOrSeveralAutomaticLayoutsAreTrimmedWhenRead() {
        val captured = SavedHomeLayout("x", "x")
        val many =
            (1..15).map { captured.copy(id = "l$it", name = "L$it") } +
                listOf("a1", "a2").map {
                    captured.copy(id = it, name = it, kind = SavedHomeLayoutKind.BEFORE_ACTIVATION)
                }
        val encoded = SavedHomeLayoutsCodec.encode(SavedHomeLayouts(mapOf(phone to many)))
        val decoded = SavedHomeLayoutsCodec.decode(encoded).layouts.forDeviceClass(phone)
        assertEquals(SavedHomeLayouts.MAX_PER_DEVICE_CLASS, decoded.count { it.kind == SavedHomeLayoutKind.SAVED })
        assertEquals(listOf("a2"), decoded.filter { it.kind == SavedHomeLayoutKind.BEFORE_ACTIVATION }.map { it.id })
    }

    @Test
    fun anUnknownKeyIsIgnored() {
        val extra = StoredValue.Obj(SavedHomeLayoutsCodec.encode(sample()).fields + ("future" to StoredValue.Str("x")))
        assertEquals(sample(), SavedHomeLayoutsCodec.decode(extra).layouts)
        assertNull(SavedHomeLayoutsCodec.decode(extra).layouts.find(HomeLayoutDeviceClass.TABLET, "one"))
    }
}
