package com.riffle.app.launcher.widgets

import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidWidgetHostGatewayTest {
    @Test
    fun mapsWidgetProviderIdentityToAndroidBindingTarget() {
        val identity =
            WidgetProviderIdentity(
                packageName = AppPackageName("com.example.weather"),
                className = WidgetProviderClassName(".WeatherWidget"),
            )

        assertEquals(
            AndroidWidgetProviderBindingTarget(
                packageName = "com.example.weather",
                className = ".WeatherWidget",
            ),
            identity.androidBindingTarget(),
        )
    }
}
