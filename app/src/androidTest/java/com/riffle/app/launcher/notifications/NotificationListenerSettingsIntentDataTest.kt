package com.riffle.app.launcher.notifications

import android.content.ComponentName
import android.os.Build
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationListenerSettingsIntentDataTest {
    @Test
    fun detailIntentSerializesRifflesListenerComponentExtra() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val componentName =
            ComponentName(context, RiffleNotificationListenerService::class.java).flattenToString()

        val detailIntent =
            notificationListenerSettingsIntentData(
                sdkInt = Build.VERSION_CODES.R,
                listenerComponentName = componentName,
            ).first().toIntent()

        assertEquals(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS, detailIntent.action)
        assertEquals(
            componentName,
            detailIntent.getStringExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME),
        )
    }
}
