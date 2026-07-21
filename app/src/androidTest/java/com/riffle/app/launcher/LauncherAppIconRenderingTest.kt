package com.riffle.app.launcher

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.riffle.app.launcher.apps.PackageManagerAppIconLoader
import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherAppIconRenderingTest {
    @Test
    fun loaderRasterizesAnActivityIconAtTheDisplayDensityAwareSize() {
        val loader =
            PackageManagerAppIconLoader(
                packageManager = InstrumentationRegistry.getInstrumentation().targetContext.packageManager,
                activityIconFor = { ColorDrawable(Color.BLUE) },
                displayDensityFor = { 4f },
            )

        val icon = loader.iconFor(identity = testIdentity())

        assertNotNull(icon)
        assertEquals(320, icon?.width)
        assertEquals(320, icon?.height)
    }

    private fun testIdentity() =
        AppIdentity(
            packageName = AppPackageName("com.riffle.icon-test"),
            activityName = AppActivityName(".MainActivity"),
        )
}
