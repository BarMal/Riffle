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
                        StreamSectionLayout(StreamSection.TODAY, view = StreamView.LIST),
                        StreamSectionLayout(StreamSection.NOTIFICATIONS),
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
        assertEquals(StreamSection.layoutSections.toSet(), layout.sections.map { it.section }.toSet())
        assertEquals(StreamSection.layoutSections.size, layout.sections.size)
        // The ones the data lacked come last, on, with their default view.
        assertEquals(
            listOf(StreamSection.NOTIFICATIONS, StreamSection.RECENTS, StreamSection.TODAY),
            layout.sections.drop(2).map { it.section },
        )
        assertTrue(layout.sections.drop(2).all { it.enabled })
    }

    @Test
    fun anUnknownViewFallsBackToTheSectionsDefault() {
        val layout =
            assertNotNull(
                StreamLayoutCodec.decode(root(posture("PHONE", listOf(section("today", view = "carousel"))))),
            ).forPosture(HomeLayoutDeviceClass.PHONE)
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.TODAY))
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

    private fun decodeOrder(vararg sections: StoredValue): StreamLayout =
        assertNotNull(StreamLayoutCodec.decode(root(posture("PHONE", sections.toList()))))
            .forPosture(HomeLayoutDeviceClass.PHONE)

    private fun StreamLayout.sectionOf(section: StreamSection) = sections.first { it.section == section }

    @Test
    fun aLayoutStoredWithAgendaAndTasksMapsToOneTodayAtAgendasPlace() {
        // The six sections as the build before the Today page wrote them (its default order).
        val layout =
            decodeOrder(
                section("now"),
                section("notifications"),
                section("recents"),
                section("agenda", view = "list"),
                section("tasks", view = "list"),
                section("feeds"),
            )
        assertEquals(
            listOf(
                StreamSection.NOW,
                StreamSection.NOTIFICATIONS,
                StreamSection.RECENTS,
                StreamSection.TODAY,
                StreamSection.FEEDS,
            ),
            layout.sections.map { it.section },
        )
        // The view is Agenda's, and Today is shown.
        assertEquals(StreamView.LIST, layout.viewOf(StreamSection.TODAY))
        assertTrue(layout.sectionOf(StreamSection.TODAY).enabled)
    }

    @Test
    fun todayTakesAgendasPlaceWhereverItWasMovedAndTasksGoes() {
        val layout =
            decodeOrder(
                section("agenda", view = "stack"),
                section("feeds"),
                section("now"),
                section("tasks", view = "list"),
                section("recents"),
                section("notifications"),
            )
        assertEquals(
            listOf(
                StreamSection.TODAY,
                StreamSection.FEEDS,
                StreamSection.NOW,
                StreamSection.RECENTS,
                StreamSection.NOTIFICATIONS,
            ),
            layout.sections.map { it.section },
        )
        // Agenda's view wins over Tasks'.
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.TODAY))
    }

    @Test
    fun todayIsHiddenOnlyWhenBothAgendaAndTasksWereHidden() {
        fun enabledFor(
            agenda: Boolean,
            tasks: Boolean,
        ) = decodeOrder(section("agenda", enabled = agenda), section("tasks", enabled = tasks))
            .sectionOf(StreamSection.TODAY).enabled
        assertEquals(false, enabledFor(agenda = false, tasks = false))
        assertEquals(true, enabledFor(agenda = false, tasks = true))
        assertEquals(true, enabledFor(agenda = true, tasks = false))
        assertEquals(true, enabledFor(agenda = true, tasks = true))
    }

    @Test
    fun aLayoutWithOnlyOneOfTheRetiredSectionsStillMapsToToday() {
        val onlyTasks = decodeOrder(section("now"), section("tasks", enabled = false, view = "list"))
        assertEquals(listOf(StreamSection.NOW, StreamSection.TODAY), onlyTasks.sections.take(2).map { it.section })
        assertEquals(false, onlyTasks.sectionOf(StreamSection.TODAY).enabled)
        assertEquals(StreamView.LIST, onlyTasks.viewOf(StreamSection.TODAY))

        val onlyAgenda = decodeOrder(section("agenda", enabled = false, view = "stack"))
        assertEquals(StreamSection.TODAY, onlyAgenda.sections.first().section)
        assertEquals(false, onlyAgenda.sectionOf(StreamSection.TODAY).enabled)
    }

    @Test
    fun aLayoutThatAlreadyNamesTodayKeepsItAndIgnoresTheRetiredEntries() {
        val layout =
            decodeOrder(
                section("recents"),
                section("agenda", enabled = false, view = "list"),
                section("today", enabled = true, view = "stack"),
                section("tasks", enabled = false),
            )
        assertEquals(StreamSection.RECENTS, layout.sections[0].section)
        assertEquals(StreamSection.TODAY, layout.sections[1].section)
        assertEquals(true, layout.sectionOf(StreamSection.TODAY).enabled)
        assertEquals(StreamView.STACK, layout.viewOf(StreamSection.TODAY))
        assertEquals(1, layout.sections.count { it.section == StreamSection.TODAY })
    }

    @Test
    fun theRetiredStoredIdsAreKeptAndDerivedSectionsNeverReachALayout() {
        assertEquals("agenda", LegacyStreamSections.AGENDA_ID)
        assertEquals("tasks", LegacyStreamSections.TASKS_ID)
        assertNull(StreamSection.fromStoredId("agenda"))
        // A shortcuts page is derived from the Dock: a stored entry for it is dropped, never laid out.
        val layout = decodeOrder(section("shortcuts"), section("now"))
        assertEquals(StreamSection.layoutSections, layout.sections.map { it.section }.sortedBy { it.ordinal })
        assertEquals(StreamSection.NOW, layout.sections.first().section)
    }

    @Test
    fun theOptionsOfAgendaAndTasksStillDecodeUnderTheirOwnKeysAndTodayWritesNeitherRetiredSection() {
        val options =
            obj(
                "agenda" to obj("daysAhead" to StoredValue.Num(3), "maxEvents" to StoredValue.Num(6)),
                "tasks" to obj("limit" to StoredValue.Num(7), "showCompleted" to StoredValue.Bool(true)),
            )
        val decoded =
            assertNotNull(
                StreamLayoutCodec.decode(root(posture("PHONE", listOf(section("agenda"), section("tasks")), options))),
            ).forPosture(HomeLayoutDeviceClass.PHONE)
        assertEquals(3, decoded.options.agenda.daysAhead)
        assertEquals(TasksOptions(limit = 7, showCompleted = true), decoded.options.tasks)
        // Writing the migrated layout names today and neither retired id; reading that back is a no-op.
        val written = StreamLayoutCodec.encode(StreamLayouts().with(HomeLayoutDeviceClass.PHONE, decoded))
        val posture = (written.fields["postures"] as StoredValue.Arr).items.single() as StoredValue.Obj
        val ids =
            (posture.fields["sections"] as StoredValue.Arr).items
                .map { ((it as StoredValue.Obj).fields["id"] as StoredValue.Str).value }
        assertTrue("today" in ids)
        assertTrue("agenda" !in ids && "tasks" !in ids)
        assertEquals(StreamLayouts().with(HomeLayoutDeviceClass.PHONE, decoded), StreamLayoutCodec.decode(written))
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

    @Test
    fun aLayoutStoredWithTheSplitAndTheStageViewStillDecodesAndTheyAreIgnored() {
        // Stored by the unfolded Home and Stream split and the Pages or Stage view, both replaced by the upright stack
        // attached to the Dock (ADR 0003, Amendment 2): the sections and options survive, the two fields are dropped.
        val old =
            obj(
                "version" to StoredValue.Num(1),
                "postures" to
                    StoredValue.Arr(
                        listOf(
                            obj(
                                "deviceClass" to StoredValue.Str("FOLDABLE"),
                                "sections" to StoredValue.Arr(listOf(section("now", view = "list"))),
                                "split" to
                                    obj(
                                        "fractionPermille" to StoredValue.Num(600),
                                        "lastShown" to StoredValue.Str("stream"),
                                    ),
                                "wideView" to StoredValue.Str("stage"),
                            ),
                            obj(
                                "deviceClass" to StoredValue.Str("TABLET"),
                                "sections" to StoredValue.Arr(emptyList()),
                                "split" to StoredValue.Str("not an object"),
                                "wideView" to StoredValue.Str("carousel"),
                            ),
                        ),
                    ),
            )
        val decoded = StreamLayoutCodec.decode(old)
        assertEquals(StreamView.LIST, decoded?.forPosture(HomeLayoutDeviceClass.FOLDABLE)?.viewOf(StreamSection.NOW))
        assertEquals(StreamLayout.DEFAULT, decoded?.forPosture(HomeLayoutDeviceClass.TABLET))
        // Writing it back drops both keys, and what is written reads back as the same layouts.
        val written = StreamLayoutCodec.encode(decoded!!)
        val postures = (written.fields["postures"] as StoredValue.Arr).items.map { (it as StoredValue.Obj).fields }
        assertTrue(postures.all { "split" !in it && "wideView" !in it })
        assertTrue(postures.all { it.keys.containsAll(listOf("deviceClass", "sections", "options")) })
        assertEquals(decoded, StreamLayoutCodec.decode(written))
    }
}
