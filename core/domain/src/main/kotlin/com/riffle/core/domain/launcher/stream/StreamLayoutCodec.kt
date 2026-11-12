package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.StoredValue
import com.riffle.core.domain.launcher.workspace.arr
import com.riffle.core.domain.launcher.workspace.array
import com.riffle.core.domain.launcher.workspace.bool
import com.riffle.core.domain.launcher.workspace.enumOrNull
import com.riffle.core.domain.launcher.workspace.guarded
import com.riffle.core.domain.launcher.workspace.long
import com.riffle.core.domain.launcher.workspace.num
import com.riffle.core.domain.launcher.workspace.obj
import com.riffle.core.domain.launcher.workspace.str
import com.riffle.core.domain.launcher.workspace.string
import com.riffle.core.domain.launcher.workspace.workspaceLayoutClass

const val CURRENT_STREAM_LAYOUT_SCHEMA_VERSION = 1

/**
 * Encodes and decodes [StreamLayouts] to [StoredValue]: the user's choice of sections (on or off, order, Stack or
 * List) and the per-section options, never any content.
 *
 * Decoding never throws and always yields a valid layout. A value that is not an object is null; a posture with an
 * unknown device class is dropped; a section id that is unknown or repeated is dropped; a section the data lacks (one
 * a later build adds) is appended, on, with its default view; an unknown view falls back to the section's default;
 * options are read per section and clamped, so one bad number never loses the rest. A landscape-phone layout is merged
 * into the phone's unless the phone has its own. A newer schema version is read on a best-effort basis.
 */
@Suppress("TooManyFunctions")
object StreamLayoutCodec {
    /** Counts above this are never useful and would only make evaluation slow. */
    private const val MAX_COUNT = 50L
    private const val MAX_AGE_MILLIS = 365L * 24L * 60L * 60L * 1000L

    fun encode(layouts: StreamLayouts): StoredValue.Obj =
        obj(
            "version" to num(CURRENT_STREAM_LAYOUT_SCHEMA_VERSION),
            "postures" to
                arr(
                    layouts.byPosture.entries.sortedBy { it.key.ordinal }.map { (deviceClass, layout) ->
                        obj(
                            "deviceClass" to str(deviceClass.name),
                            "sections" to arr(layout.sections.map(::encodeSection)),
                            "options" to encodeOptions(layout.options),
                        )
                    },
                ),
        )

    fun decode(value: StoredValue?): StreamLayouts? {
        val root = value as? StoredValue.Obj ?: return null
        val decoded =
            root.array("postures")
                .mapNotNull { (it as? StoredValue.Obj)?.let(::decodePosture) }
        // Phone first, so a landscape entry never replaces a phone entry that exists.
        val byPosture = LinkedHashMap<HomeLayoutDeviceClass, StreamLayout>()
        decoded.sortedBy { it.first == HomeLayoutDeviceClass.PHONE_LANDSCAPE }.forEach { (deviceClass, layout) ->
            byPosture.putIfAbsent(deviceClass.workspaceLayoutClass(), layout)
        }
        return StreamLayouts(byPosture)
    }

    private fun decodePosture(root: StoredValue.Obj): Pair<HomeLayoutDeviceClass, StreamLayout>? {
        val deviceClass = enumOrNull<HomeLayoutDeviceClass>(root.string("deviceClass")) ?: return null
        val stored =
            root.array("sections")
                .mapNotNull { (it as? StoredValue.Obj)?.let(::decodeSection) }
                .distinctBy { it.section }
        val missing = StreamSection.entries.filter { section -> stored.none { it.section == section } }
        val sections = stored + missing.map { StreamSectionLayout(it) }
        val options = (root.fields["options"] as? StoredValue.Obj)?.let(::decodeOptions) ?: StreamSectionOptions()
        return deviceClass to StreamLayout(sections, options)
    }

    private fun encodeSection(layout: StreamSectionLayout): StoredValue.Obj =
        obj(
            "id" to str(layout.section.storedId),
            "enabled" to StoredValue.Bool(layout.enabled),
            "view" to str(layout.view.storedId),
        )

    private fun decodeSection(root: StoredValue.Obj): StreamSectionLayout? {
        val section = root.string("id")?.let(StreamSection::fromStoredId) ?: return null
        return StreamSectionLayout(
            section = section,
            enabled = root.bool("enabled") ?: true,
            view = StreamView.fromStoredId(root.string("view")) ?: StreamLayout.defaultViewOf(section),
        )
    }

    private fun encodeOptions(options: StreamSectionOptions): StoredValue.Obj =
        obj(
            "now" to
                obj(
                    "maxMedia" to num(options.now.maxMedia),
                    "showNextEvent" to StoredValue.Bool(options.now.showNextEvent),
                    "showAlarm" to StoredValue.Bool(options.now.showAlarm),
                ),
            "notifications" to
                obj(
                    "maxApps" to num(options.notifications.maxApps),
                    "maxPerApp" to num(options.notifications.maxPerApp),
                    "maxAgeMillis" to options.notifications.maxAgeMillis?.let(::num),
                ),
            "recents" to
                obj(
                    "limit" to num(options.recents.limit),
                    "maxAgeMillis" to options.recents.maxAgeMillis?.let(::num),
                    "frequentLimit" to num(options.recents.frequentLimit),
                ),
            "agenda" to
                obj(
                    "daysAhead" to num(options.agenda.daysAhead),
                    "maxEvents" to num(options.agenda.maxEvents),
                    "includeCalendarFeeds" to StoredValue.Bool(options.agenda.includeCalendarFeeds),
                ),
            "tasks" to
                obj(
                    "limit" to num(options.tasks.limit),
                    "showCompleted" to StoredValue.Bool(options.tasks.showCompleted),
                ),
            "feeds" to
                obj(
                    "maxFeeds" to num(options.feeds.maxFeeds),
                    "perFeed" to num(options.feeds.perFeed),
                ),
        )

    private fun decodeOptions(root: StoredValue.Obj): StreamSectionOptions {
        val defaults = StreamSectionOptions()
        return StreamSectionOptions(
            now = guarded { decodeNow(root.obj("now"), defaults.now) } ?: defaults.now,
            notifications =
                guarded { decodeNotifications(root.obj("notifications"), defaults.notifications) }
                    ?: defaults.notifications,
            recents = guarded { decodeRecents(root.obj("recents"), defaults.recents) } ?: defaults.recents,
            agenda = guarded { decodeAgenda(root.obj("agenda"), defaults.agenda) } ?: defaults.agenda,
            tasks = guarded { decodeTasks(root.obj("tasks"), defaults.tasks) } ?: defaults.tasks,
            feeds = guarded { decodeFeeds(root.obj("feeds"), defaults.feeds) } ?: defaults.feeds,
        )
    }

    private fun decodeNow(
        root: StoredValue.Obj?,
        defaults: NowOptions,
    ) = NowOptions(
        maxMedia = root.count("maxMedia", defaults.maxMedia),
        showNextEvent = root?.bool("showNextEvent") ?: defaults.showNextEvent,
        showAlarm = root?.bool("showAlarm") ?: defaults.showAlarm,
    )

    private fun decodeNotifications(
        root: StoredValue.Obj?,
        defaults: NotificationsOptions,
    ) = NotificationsOptions(
        maxApps = root.count("maxApps", defaults.maxApps),
        maxPerApp = root.count("maxPerApp", defaults.maxPerApp),
        maxAgeMillis = root.age("maxAgeMillis", defaults.maxAgeMillis),
    )

    private fun decodeRecents(
        root: StoredValue.Obj?,
        defaults: RecentsOptions,
    ) = RecentsOptions(
        limit = root.count("limit", defaults.limit),
        maxAgeMillis = root.age("maxAgeMillis", defaults.maxAgeMillis),
        frequentLimit = root.count("frequentLimit", defaults.frequentLimit),
    )

    private fun decodeAgenda(
        root: StoredValue.Obj?,
        defaults: AgendaOptions,
    ) = AgendaOptions(
        daysAhead = root.count("daysAhead", defaults.daysAhead).coerceAtLeast(1),
        maxEvents = root.count("maxEvents", defaults.maxEvents),
        includeCalendarFeeds = root?.bool("includeCalendarFeeds") ?: defaults.includeCalendarFeeds,
    )

    private fun decodeTasks(
        root: StoredValue.Obj?,
        defaults: TasksOptions,
    ) = TasksOptions(
        limit = root.count("limit", defaults.limit),
        showCompleted = root?.bool("showCompleted") ?: defaults.showCompleted,
    )

    private fun decodeFeeds(
        root: StoredValue.Obj?,
        defaults: FeedsOptions,
    ) = FeedsOptions(
        maxFeeds = root.count("maxFeeds", defaults.maxFeeds),
        perFeed = root.count("perFeed", defaults.perFeed),
    )

    private fun StoredValue.Obj?.count(
        key: String,
        default: Int,
    ): Int = (this?.long(key) ?: default.toLong()).coerceIn(0L, MAX_COUNT).toInt()

    /** An absent key is the default; a stored age outside 1 ms to a year is the default too. */
    private fun StoredValue.Obj?.age(
        key: String,
        default: Long?,
    ): Long? {
        val value = this?.long(key) ?: return default
        return if (value in 1L..MAX_AGE_MILLIS) value else default
    }
}

/**
 * The Stream layouts in a backup document. Layouts hold only choices, no content, so they are written as they are;
 * a restore decodes through the codec's own safe rules and leaves current data alone when the section is absent.
 */
object StreamLayoutBackup {
    /** The layouts to write, or null when there is nothing but defaults (so the section is omitted). */
    fun export(layouts: StreamLayouts?): StreamLayouts? = layouts?.takeIf { !it.isDefault }

    /** The layouts to restore, or null to leave the current ones untouched. */
    fun restore(layouts: StreamLayouts?): StreamLayouts? = layouts?.takeIf { it.byPosture.isNotEmpty() }
}
