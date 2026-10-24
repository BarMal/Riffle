package com.riffle.core.domain.launcher.workspace.settings

import com.riffle.core.domain.launcher.ShellDestination
import com.riffle.core.domain.launcher.settings.LauncherSettings
import com.riffle.core.domain.launcher.settings.PrivacySettings
import com.riffle.core.domain.launcher.settings.protectsWindow
import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.container.ContextChanges
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SourceContentPrivacyTest {
    @Test
    fun everySourceShowsEverythingUntilChanged() {
        val privacy = StoredSourceContentPrivacy(InMemoryContentLevelStore())
        assertEquals(SourceContentLevels.ALL_SHOWN, privacy.levels())
        SourceIds.BUILT_IN.forEach { assertEquals(SourceContentLevel.SHOW, privacy.levels().levelOf(it)) }
    }

    @Test
    fun setLevelPersistsAndNotifiesOnlyOnChange() {
        val store = InMemoryContentLevelStore()
        val privacy = StoredSourceContentPrivacy(store)
        var changes = 0
        val subscription = privacy.observe { changes++ }

        privacy.setLevel(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT)
        privacy.setLevel(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT)
        assertEquals(1, changes)
        assertEquals(mapOf("notifications" to "hide_text"), store.stored)

        privacy.setLevel(SourceIds.NOTIFICATIONS, SourceContentLevel.SHOW)
        assertEquals(2, changes)
        assertEquals(emptyMap(), store.stored)

        subscription.cancel()
        privacy.setLevel(SourceIds.MEDIA, SourceContentLevel.HIDE_BODY)
        assertEquals(2, changes)
    }

    @Test
    fun levelsSurviveARestart() {
        val store = InMemoryContentLevelStore()
        StoredSourceContentPrivacy(store).setLevel(SourceIds.CALENDAR, SourceContentLevel.HIDE_BODY)
        val restarted = StoredSourceContentPrivacy(store)
        assertEquals(SourceContentLevel.HIDE_BODY, restarted.levels().levelOf(SourceIds.CALENDAR))
    }

    @Test
    fun unknownOrBlankStoredValuesFallBackToShow() {
        val store =
            InMemoryContentLevelStore(
                mapOf("notifications" to "nonsense", "" to "hide_text", "media" to "hide_body", "calendar" to "show"),
            )
        val levels = StoredSourceContentPrivacy(store).levels()
        assertEquals(SourceContentLevels(mapOf(SourceIds.MEDIA to SourceContentLevel.HIDE_BODY)), levels)
    }

    @Test
    fun aStoreThatFailsMeansEverythingShowsAndChangesStillApply() {
        val broken =
            object : ContentLevelStore {
                override fun read(): Map<String, String> = error("unreadable")

                override fun write(levels: Map<String, String>) = error("read-only")
            }
        val privacy = StoredSourceContentPrivacy(broken)
        assertEquals(SourceContentLevels.ALL_SHOWN, privacy.levels())
        privacy.setLevel(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT)
        assertEquals(SourceContentLevel.HIDE_TEXT, privacy.levels().levelOf(SourceIds.NOTIFICATIONS))
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val levels =
            SourceContentLevels(
                mapOf(
                    SourceIds.NOTIFICATIONS to SourceContentLevel.HIDE_TEXT,
                    SourceIds.ICS to SourceContentLevel.HIDE_BODY,
                ),
            )
        assertEquals(levels, StoredSourceContentPrivacy.decode(StoredSourceContentPrivacy.encode(levels)))
    }

    @Test
    fun plannerListsOnlyPrivacySensitiveSourcesInBuiltInOrder() {
        val sensitive = setOf(SourceCapability.PRIVACY_SENSITIVE)
        val descriptors =
            listOf(
                SourceDescriptor(SourceIds.ICS, sensitive),
                SourceDescriptor(SourceIds.ALL_APPS),
                SourceDescriptor(SourceId("custom"), sensitive),
                SourceDescriptor(SourceIds.NOTIFICATIONS, sensitive),
                SourceDescriptor(SourceIds.MEDIA, sensitive),
                SourceDescriptor(SourceIds.RSS),
                SourceDescriptor(SourceIds.CALENDAR, sensitive),
            )
        val levels = SourceContentLevels(mapOf(SourceIds.MEDIA to SourceContentLevel.HIDE_BODY))
        val rows = ContentLevelPlanner.plan(descriptors, levels)
        assertEquals(
            listOf("notifications", "media", "calendar", "ics", "custom"),
            rows.map { it.id.value },
        )
        assertEquals(listOf("Notifications", "Media", "Calendar", "Calendar feeds"), rows.take(4).map { it.title })
        assertEquals(SourceContentLevel.HIDE_BODY, rows[1].level)
        assertTrue(ContentLevelPlanner.supports(descriptors, SourceIds.NOTIFICATIONS))
        assertFalse(ContentLevelPlanner.supports(descriptors, SourceIds.RSS))
        assertFalse(ContentLevelPlanner.supports(descriptors, SourceIds.SEARCH))
    }

    @Test
    fun mergedContextChangesFansInAndCancelsEverything() {
        val a = RecordingChanges()
        val b = RecordingChanges()
        assertNull(mergedContextChanges(null, null))
        assertSame(a, mergedContextChanges(a, null))
        var count = 0
        val subscription = mergedContextChanges(a, b)!!.observe { count++ }
        a.fire()
        b.fire()
        assertEquals(2, count)
        subscription.cancel()
        assertEquals(0, a.listeners.size + b.listeners.size)
    }

    @Test
    fun screenProtectionIsOffByDefaultAndNeverCoversSettings() {
        assertFalse(LauncherSettings().privacy.protectScreenContent)
        ShellDestination.entries.forEach { assertFalse(PrivacySettings().protectsWindow(it)) }
        val on = PrivacySettings(protectScreenContent = true)
        ShellDestination.entries.forEach { destination ->
            assertEquals(destination != ShellDestination.SETTINGS, on.protectsWindow(destination), destination.name)
        }
    }

    private class RecordingChanges : ContextChanges {
        val listeners = mutableListOf<() -> Unit>()

        fun fire() = listeners.toList().forEach { it() }

        override fun observe(onChange: () -> Unit): SourceSubscription {
            listeners += onChange
            return SourceSubscription { listeners -= onChange }
        }
    }
}
