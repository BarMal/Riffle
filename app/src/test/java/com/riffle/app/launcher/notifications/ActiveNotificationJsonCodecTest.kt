package com.riffle.app.launcher.notifications

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.notifications.LauncherNotification
import com.riffle.core.domain.launcher.notifications.LauncherNotificationKey
import com.riffle.core.domain.launcher.notifications.NotificationCategory
import com.riffle.core.domain.launcher.notifications.NotificationPriority
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveNotificationJsonCodecTest {
    @Test
    fun roundTripsActiveNotifications() {
        val notifications =
            listOf(
                LauncherNotification(
                    key = LauncherNotificationKey("camera-1"),
                    packageName = AppPackageName("com.riffle.camera"),
                    profileId = AppProfileId("work"),
                    category = NotificationCategory.MESSAGE,
                    priority = NotificationPriority.HIGH,
                    canDismiss = true,
                    title = "Camera",
                    text = "Rendering complete",
                    largeIconPngBase64 = "png-data",
                    postedAtEpochMillis = 1_000L,
                ),
            )

        assertEquals(notifications, decodeActiveNotifications(encodeActiveNotifications(notifications)))
    }

    @Test
    fun encodesProfileId() {
        val encoded =
            encodeActiveNotifications(
                listOf(
                    LauncherNotification(
                        key = LauncherNotificationKey("docs-1"),
                        packageName = AppPackageName("com.riffle.docs"),
                        profileId = AppProfileId("company"),
                        postedAtEpochMillis = 1_000L,
                    ),
                ),
            )

        assertEquals("company", JSONArray(encoded).getJSONObject(0).getString("profileId"))
    }

    @Test
    fun decodesEmptyNotificationList() {
        assertEquals(emptyList<LauncherNotification>(), decodeActiveNotifications("[]"))
    }

    @Test
    fun decodesMissingProfileIdAsPersonal() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(AppProfile.personal().id, notifications.single().profileId)
    }

    @Test
    fun decodesMissingNotificationCategoryAsUnknown() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(NotificationCategory.UNKNOWN, notifications.single().category)
    }

    @Test
    fun decodesMissingNotificationPriorityAsUnknown() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(NotificationPriority.UNKNOWN, notifications.single().priority)
    }

    @Test
    fun decodesMissingDismissibleStateAsFalse() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            )

        assertEquals(false, notifications.single().canDismiss)
    }

    @Test
    fun decodesMissingContentFieldsAsBlankOrNull() {
        val notification =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "legacy",
                        "packageName": "com.riffle.legacy",
                        "postedAtEpochMillis": 1000
                    }
                ]
                """.trimIndent(),
            ).single()

        assertEquals("", notification.title)
        assertEquals("", notification.text)
        assertEquals(null, notification.largeIconPngBase64)
    }

    @Test
    fun skipsMalformedNotificationEntries() {
        val notifications =
            decodeActiveNotifications(
                """
                [
                    {
                        "key": "valid",
                        "packageName": "com.riffle.valid",
                        "postedAtEpochMillis": 1000
                    },
                    {
                        "key": "missing-package"
                    },
                    {
                        "key": "work-valid",
                        "packageName": "com.riffle.work",
                        "profileId": "work",
                        "postedAtEpochMillis": 2000
                    },
                    "not an object"
                ]
                """.trimIndent(),
            )

        assertEquals(
            listOf(LauncherNotificationKey("valid"), LauncherNotificationKey("work-valid")),
            notifications.map { notification -> notification.key },
        )
        assertEquals(
            listOf(AppProfile.personal().id, AppProfileId("work")),
            notifications.map { notification -> notification.profileId },
        )
    }
}
