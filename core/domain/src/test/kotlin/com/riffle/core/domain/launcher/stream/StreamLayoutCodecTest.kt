package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreamLayoutCodecTest {
    private fun obj(vararg entries: Pair<String, StoredValue>) = StoredValue.Obj(mapOf(*entries))

    private fun section(
        id: String,
        enabled: Boolean = true,
        view: String = "stack",
    ) = obj("id" to StoredValue.Str(id), "enabled" to StoredValue.Bool(enabled), "view" to StoredValue.Str(view))

    private fun posture(
        deviceClass: String,
        sections: List<StoredValue>,
        options: StoredValue? = null,
    ) = obj(
        *listOfNotNull(
            "deviceClass" to StoredValue.Str(deviceClass),
            "sections" to StoredValue.Arr(sections),
            options?.let { "options" to it },
        ).toTypedArray(),
    )

    private fun root(vararg postures: StoredValue) =
        obj("version" to StoredValue.Num(1), "postures" to StoredValue.Arr(postures.toList()))

    @Test
    fun roundTripsEveryChoiceAndOption() {
        val custom =
            StreamLayout(
                sections =
                    listOf(
                        StreamSectionLayout(StreamSection.FEEDS, enabled = false, view = StreamView.LIST),
                        StreamSectionLayout(StreamSection.NOW, view = StreamView.LIST),
                        StreamSectionLayout(StreamSection.TASKS),
                        StreamSectionLayout(StreamSection.NOTIFICATIONS),
                        StreamSectionLayout(StreamSection.AGENDA, view = StreamView.STACK),
                        StreamSectionLayout(StreamSection.RECENTS),
                    ),
                options =
                    StreamSectionOptions(
                        now = NowOptions(maxMedia = 2, showNextEvent = false, showAlarm = false),
                        notifications = NotificationsOptions(maxApps = 3, maxPerApp = 2, maxAgeMillis = 3_600_000L),
                        recents = RecentsOptions(limit = 5, maxAgeMillis = 86_400_000L, frequentLimit = 0),
                        agenda = AgendaOptions(daysAhead = 3, maxEvents = 8, includeCalendarFeeds = false),
                        tasks = TasksOptions(limit = 4, showCompleted = true),
                        feeds = FeedsOptions(maxFeeds = 2, perFeed = 3),
                    ),
            )
        val layouts =
            StreamLayouts()
                .with(HomeLayoutDeviceClass.PHONE, custom)
                .with(HomeLayoutDeviceClass.TABLET, StreamLayout.DEFAULT)
        assertEquals(layouts, StreamLayoutCodec.decode(StreamLayoutCodec.encode(layouts)))
    }

    @Test
    fun aValueThatIsNotAnObjectDecodesToNull() {
        assertNull(StreamLayoutCodec.decode(null))
        assertNull(StreamLayoutCodec.decode(StoredValue.Str("nope")))
        assertNull(StreamLayoutCodec.decode(StoredValue.Arr(emptyList())))
    }

    @Test
    fun anObjectWithNoPosturesDecodesToEmptyLayouts() {
        assertEquals(StreamLayouts(), StreamLayoutCodec.decode(obj()))
    }

    @Test
    fun unknownAndRepeatedSectionsAreDroppedAndMissingOnesAreAppended() {
        val decoded =
            StreamLayoutCodec.decode(
                root(
                    posture(
                        "PHONE",
                        listOf(
                            section("feeds", view = "list"),
                            section("hologram"),
                            section("feeds", enabled = false),
                            section("now", enabled = false),
                        ),
                    ),
                ),
            )
        val layout = assertNotNull(decoded).forPosture(HomeLayoutDeviceClass.PHONE)
        assertEquals(StreamSection.FEEDS, layout.sections[0].section)
        assertEquals(StreamView.LIST, layout.sections[0].view)
        assertTrue(layout.sections[0].enabled)
        assertEquals(StreamSection.NOW, layout.sections[1].section)
        assertEquals(false, layout.sections[1].enabled)
        assertEquals(StreamSection.entries.toSet(), layout.sections.map { it.section }.toSet())
        assertEquals(StreamSection.entries.size, layout.sections.size)
        // The ones the data lacked come last, on, with their default view.
        assertEquals(
            listOf(StreamSection.NOTIFICATIONS, StreamSection.RECENTS, StreamSection.AGENDA, StreamSection.TASKS),
            layout.sections.drop(2).map { it.section },
        )
        assertTrue(layout.sections.drop(2).all { it.enabled })
    }

    @Test
    fun anUnknownViewFallsBackToTheSectionsDefault() {
        val layout =
            assertNotNull(
                StreamLayoutCodec.decode(root(posture("PHONE", listOf(section("agenda", view = "carousel"))))),
            ).forPosture(HomeLayoutDeviceClass.PHONE)
        assertEquals(StreamView.LIST, layout.viewOf(StreamSection.AGENDA))
    }

    @Test
    fun aPostureWithAnUnknownDeviceClassIsDropped() {
        val decoded = assertNotNull(StreamLayoutCodec.decode(root(posture("HOLOGRAM", emptyList()))))
        assertTrue(decoded.byPosture.isEmpty())
    }

    @Test
    fun aLandscapePhoneLayoutFoldsIntoThePhoneUnlessThePhoneHasItsOwn() {
        val landscapeOnly =
            assertNotNull(
                StreamLayoutCodec.decode(root(posture("PHONE_LANDSCAPE", listOf(section("now", view = "list"))))),
            )
        assertEquals(StreamView.LIST, landscapeOnly.forPosture(HomeLayoutDeviceClass.PHONE).viewOf(StreamSection.NOW))

        val both =
            assertNotNull(
                StreamLayoutCodec.decode(
                    root(
                        posture("PHONE_LANDSCAPE", listOf(section("now", view = "list"))),
                        posture("PHONE", listOf(section("now", view = "stack"))),
                    ),
                ),
            )
        assertEquals(StreamView.STACK, both.forPosture(HomeLayoutDeviceClass.PHONE).viewOf(StreamSection.NOW))
    }

    @Test
    fun badOptionNumbersAreClampedOrReplacedPerSectionWithoutLosingTheRest() {
        val options =
            obj(
                "recents" to
                    obj(
                        "limit" to StoredValue.Num(-4),
                        "frequentLimit" to StoredValue.Num(9_999),
                        "maxAgeMillis" to StoredValue.Num(-1),
                    ),
                "agenda" to obj("daysAhead" to StoredValue.Num(0), "maxEvents" to StoredValue.Str("many")),
                "feeds" to obj("maxFeeds" to StoredValue.Num(2), "perFeed" to StoredValue.Num(7)),
            )
        val layout =
            assertNotNull(
                StreamLayoutCodec.decode(root(posture("PHONE", emptyList(), options))),
            ).forPosture(HomeLayoutDeviceClass.PHONE)
        assertEquals(0, layout.options.recents.limit)
        assertEquals(50, layout.options.recents.frequentLimit)
        assertNull(layout.options.recents.maxAgeMillis)
        assertEquals(1, layout.options.agenda.daysAhead)
        assertEquals(AgendaOptions().maxEvents, layout.options.agenda.maxEvents)
        assertEquals(FeedsOptions(maxFeeds = 2, perFeed = 7), layout.options.feeds)
        assertEquals(NowOptions(), layout.options.now)
    }

    @Test
    fun backupOmitsDefaultsAndRestoreLeavesCurrentDataAloneWhenEmpty() {
        assertNull(StreamLayoutBackup.export(null))
        assertNull(StreamLayoutBackup.export(StreamLayouts()))
        assertNull(StreamLayoutBackup.export(StreamLayouts().with(HomeLayoutDeviceClass.PHONE, StreamLayout.DEFAULT)))
        val custom =
            StreamLayouts().with(
                HomeLayoutDeviceClass.PHONE,
                StreamLayoutReducer.reduce(
                    StreamLayout.DEFAULT,
                    StreamLayoutAction.SetView(StreamSection.NOW, StreamView.LIST),
                ),
            )
        assertEquals(custom, StreamLayoutBackup.export(custom))
        assertNull(StreamLayoutBackup.restore(null))
        assertNull(StreamLayoutBackup.restore(StreamLayouts()))
        assertEquals(custom, StreamLayoutBackup.restore(custom))
    }
}
