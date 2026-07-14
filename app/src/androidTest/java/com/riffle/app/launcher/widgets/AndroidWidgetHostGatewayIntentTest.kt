package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidWidgetHostGatewayIntentTest {
    private val platform = FakeAndroidWidgetHostPlatform()
    private val gateway = AndroidWidgetHostGateway(platform)

    @Test
    fun bindIntentContainsActionHostedWidgetIdAndProvider() {
        val intent = gateway.createBindHostedWidgetIntent(HostedWidgetId(42), weatherProvider())

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, intent.action)
        assertEquals(42, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(
            ComponentName("com.example.weather", ".WeatherWidget"),
            intent.getParcelableExtra(
                AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,
                ComponentName::class.java,
            ),
        )
    }

    @Test
    fun configureIntentContainsActionHostedWidgetIdAndProviderActivity() {
        platform.configureActivity =
            AndroidWidgetProviderBindingTarget("com.example.weather", ".ConfigureWeatherWidget")

        val intent = gateway.createConfigureHostedWidgetIntent(HostedWidgetId(42))

        assertEquals(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE, intent.action)
        assertEquals(42, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(ComponentName("com.example.weather", ".ConfigureWeatherWidget"), intent.component)
        assertEquals(42, platform.configuredAppWidgetId)
    }

    private fun weatherProvider() =
        WidgetProviderIdentity(
            packageName = AppPackageName("com.example.weather"),
            className = WidgetProviderClassName(".WeatherWidget"),
        )

    private class FakeAndroidWidgetHostPlatform : AndroidWidgetHostPlatform {
        var configureActivity: AndroidWidgetProviderBindingTarget? = null
        var configuredAppWidgetId: Int? = null

        override fun startListening() = Unit

        override fun stopListening() = Unit

        override fun allocateAppWidgetId(): Int = error("Not used")

        override fun bindAppWidgetIdIfAllowed(
            appWidgetId: Int,
            provider: AndroidWidgetProviderBindingTarget,
        ): Boolean = error("Not used")

        override fun configureActivity(appWidgetId: Int): AndroidWidgetProviderBindingTarget? {
            configuredAppWidgetId = appWidgetId
            return configureActivity
        }

        override fun createView(
            context: Context,
            appWidgetId: Int,
        ): View? = error("Not used")

        override fun deleteAppWidgetId(appWidgetId: Int) = Unit
    }
}
