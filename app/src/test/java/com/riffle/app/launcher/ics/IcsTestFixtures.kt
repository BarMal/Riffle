package com.riffle.app.launcher.ics

import com.riffle.core.domain.launcher.workspace.sources.ics.IcsEvent
import com.riffle.core.domain.launcher.workspace.sources.ics.IcsFeedSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId

internal const val SECRET_URL = "https://calendar.example.com/private/SECRET-TOKEN-123/basic.ics"

/** An in-memory [IcsStorePort] that records writes. Suspend only by signature: nothing here waits. */
internal class FakeIcsStore(
    var text: IcsStoredText = IcsStoredText(null, null),
    var failRead: Boolean = false,
) : IcsStorePort {
    var settingsWrites = 0
    var cacheWrites = 0

    override suspend fun read(): IcsStoredText {
        check(!failRead) { "read failed" }
        return text
    }

    override suspend fun writeSettings(json: String) {
        settingsWrites++
        text = IcsStoredText(json, text.cache)
    }

    override suspend fun writeCache(json: String) {
        cacheWrites++
        text = IcsStoredText(text.settings, json)
    }
}

/** Runs repository work inline, so a test sees every effect as soon as the call returns. */
internal fun inlineScope(): CoroutineScope = CoroutineScope(Dispatchers.Unconfined)

internal fun icsEvent(
    uid: String,
    start: String = "2026-03-11T09:00",
    title: String = "Event $uid",
    rrule: String? = null,
) = IcsEvent(
    uid = uid,
    title = title,
    location = null,
    start = LocalDateTime.parse(start),
    zone = ZoneId.of("UTC"),
    allDay = false,
    duration = Duration.ofHours(1),
    rrule = rrule,
)

internal fun settingsWithOne(id: String = "feed-1"): IcsFeedSettings =
    requireNotNull(IcsFeedSettings().withAdded("Work", SECRET_URL) { id })

internal fun loadedRepository(
    store: FakeIcsStore = FakeIcsStore(IcsStoredText(IcsJsonCodecs.encodeSettings(settingsWithOne()), null)),
): CachedIcsFeedRepository = CachedIcsFeedRepository(store, inlineScope())
