package com.riffle.app.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.lifecycle.LifecycleOwner
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.HostedWidgetId
import com.riffle.core.domain.launcher.home.LauncherItemId
import com.riffle.core.domain.launcher.home.WidgetItem
import com.riffle.core.domain.launcher.widgets.WidgetProviderClassName
import com.riffle.core.domain.launcher.widgets.WidgetProviderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidWidgetHostGatewayTest {
    private val platform = FakeAndroidWidgetHostPlatform()
    private val gateway = AndroidWidgetHostGateway(platform)

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

    @Test
    fun startsAndStopsPlatformListening() {
        gateway.onStart(FakeLifecycleOwner)
        gateway.onStop(FakeLifecycleOwner)

        assertEquals(1, platform.startListeningCount)
        assertEquals(1, platform.stopListeningCount)
    }

    @Test
    fun allocatesAndDeletesHostedWidgetIds() {
        platform.allocatedAppWidgetId = 42

        val hostedWidgetId = gateway.allocateHostedWidgetId()
        gateway.deleteHostedWidgetId(hostedWidgetId)

        assertEquals(HostedWidgetId(42), hostedWidgetId)
        assertEquals(listOf(42), platform.deletedAppWidgetIds)
    }

    @Test
    fun bindsWidgetAndBuildsBindIntent() {
        val hostedWidgetId = HostedWidgetId(42)
        val provider = weatherProvider()
        platform.bindAllowed = true

        assertEquals(WidgetBindingResult.Bound, gateway.bindHostedWidget(hostedWidgetId, provider))
        assertEquals(
            AndroidWidgetProviderBindingTarget("com.example.weather", ".WeatherWidget"),
            platform.boundProvider,
        )

        val intentData = bindHostedWidgetIntentData(hostedWidgetId, provider)
        assertEquals(
            AndroidWidgetHostIntentData(
                action = AppWidgetManager.ACTION_APPWIDGET_BIND,
                appWidgetId = 42,
                provider = AndroidWidgetProviderBindingTarget("com.example.weather", ".WeatherWidget"),
            ),
            intentData,
        )
    }

    @Test
    fun reportsBindingPermissionRequirement() {
        platform.bindAllowed = false

        assertEquals(
            WidgetBindingResult.RequiresPermission,
            gateway.bindHostedWidget(HostedWidgetId(42), weatherProvider()),
        )
    }

    @Test
    fun reportsConfigurationAndBuildsConfigureIntent() {
        val configureActivity = AndroidWidgetProviderBindingTarget("com.example.weather", ".ConfigureWeatherWidget")

        assertFalse(gateway.hostedWidgetRequiresConfiguration(HostedWidgetId(42)))
        platform.configureActivity = configureActivity

        assertTrue(gateway.hostedWidgetRequiresConfiguration(HostedWidgetId(42)))
        assertEquals(
            AndroidWidgetHostIntentData(
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE,
                appWidgetId = 42,
                provider = configureActivity,
            ),
            configureHostedWidgetIntentData(HostedWidgetId(42), configureActivity),
        )
    }

    @Test
    fun createsViewOnlyWhenPlatformCanCreateOne() {
        val widget =
            WidgetItem(
                id = LauncherItemId("widget:42"),
                appWidgetId = HostedWidgetId(42),
                label = "Weather",
            )

        val context = ContextWrapper(null)
        assertNull(gateway.createHostedWidgetView(context = context, widget = widget))

        val view = null as View?
        platform.view = view
        assertSame(view, gateway.createHostedWidgetView(context = context, widget = widget))
        assertEquals(listOf(42, 42), platform.requestedViewIds)
    }

    private fun weatherProvider() =
        WidgetProviderIdentity(
            packageName = AppPackageName("com.example.weather"),
            className = WidgetProviderClassName(".WeatherWidget"),
        )

    private object FakeLifecycleOwner : LifecycleOwner {
        override val lifecycle get() = error("Not used")
    }

    private class FakeAndroidWidgetHostPlatform : AndroidWidgetHostPlatform {
        var allocatedAppWidgetId = 0
        var bindAllowed = false
        var configureActivity: AndroidWidgetProviderBindingTarget? = null
        var view: View? = null
        var boundProvider: AndroidWidgetProviderBindingTarget? = null
        var startListeningCount = 0
        var stopListeningCount = 0
        val deletedAppWidgetIds = mutableListOf<Int>()
        val requestedViewIds = mutableListOf<Int>()

        override fun startListening() {
            startListeningCount += 1
        }

        override fun stopListening() {
            stopListeningCount += 1
        }

        override fun allocateAppWidgetId(): Int = allocatedAppWidgetId

        override fun bindAppWidgetIdIfAllowed(
            appWidgetId: Int,
            provider: AndroidWidgetProviderBindingTarget,
        ): Boolean {
            boundProvider = provider
            return bindAllowed
        }

        override fun configureActivity(appWidgetId: Int): AndroidWidgetProviderBindingTarget? = configureActivity

        override fun createView(
            context: Context,
            appWidgetId: Int,
        ): View? {
            requestedViewIds += appWidgetId
            return view
        }

        override fun deleteAppWidgetId(appWidgetId: Int) {
            deletedAppWidgetIds += appWidgetId
        }
    }
}
