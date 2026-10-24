package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceIds
import com.riffle.core.domain.launcher.workspace.lens.SourceContentLevel
import com.riffle.core.domain.launcher.workspace.settings.InMemoryContentLevelStore
import com.riffle.core.domain.launcher.workspace.settings.StoredSourceContentPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceContentLevelsControllerTest {
    private val sensitive = setOf(SourceCapability.PRIVACY_SENSITIVE)
    private val descriptors =
        listOf(
            SourceDescriptor(SourceIds.ALL_APPS),
            SourceDescriptor(SourceIds.NOTIFICATIONS, sensitive),
            SourceDescriptor(SourceIds.MEDIA, sensitive),
            SourceDescriptor(SourceIds.CALENDAR, sensitive),
        )
    private val store = InMemoryContentLevelStore()
    private val privacy = StoredSourceContentPrivacy(store)
    private val controller = SourceContentLevelsController(privacy) { descriptors }

    @Test
    fun rowsAreThePrivacySensitiveSourcesAtShowByDefault() {
        val rows = controller.rows(controller.levels.value)
        assertEquals(listOf(SourceIds.NOTIFICATIONS, SourceIds.MEDIA, SourceIds.CALENDAR), rows.map { it.id })
        assertTrue(rows.all { it.level == SourceContentLevel.SHOW })
        assertTrue(controller.supports(SourceIds.MEDIA))
        assertFalse(controller.supports(SourceIds.ALL_APPS))
    }

    @Test
    fun settingALevelPersistsAndShowsAtOnce() {
        controller.setLevel(SourceIds.NOTIFICATIONS, SourceContentLevel.HIDE_TEXT)

        assertEquals(SourceContentLevel.HIDE_TEXT, controller.levels.value.levelOf(SourceIds.NOTIFICATIONS))
        assertEquals(mapOf("notifications" to "hide_text"), store.stored)
    }

    @Test
    fun whileOpenItFollowsChangesMadeElsewhereAndStopsWhenClosed() {
        controller.open()
        controller.open()
        privacy.setLevel(SourceIds.MEDIA, SourceContentLevel.HIDE_BODY)
        assertEquals(SourceContentLevel.HIDE_BODY, controller.levels.value.levelOf(SourceIds.MEDIA))

        controller.close()
        privacy.setLevel(SourceIds.MEDIA, SourceContentLevel.SHOW)
        assertEquals(SourceContentLevel.HIDE_BODY, controller.levels.value.levelOf(SourceIds.MEDIA))

        controller.open()
        assertEquals(SourceContentLevel.SHOW, controller.levels.value.levelOf(SourceIds.MEDIA))
        controller.close()
    }
}
