package com.riffle.app.launcher.sources

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The platform name of an app that has no launcher entry: asked once per package and kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [com.riffle.app.screenshots.ScreenshotDevices.SDK])
class PackageLabelResolverTest {
    private val packageManager = ApplicationProvider.getApplicationContext<Context>().packageManager

    private fun install(
        name: String,
        label: CharSequence?,
    ) {
        val info =
            PackageInfo().apply {
                packageName = name
                applicationInfo =
                    ApplicationInfo().apply {
                        packageName = name
                        nonLocalizedLabel = label
                    }
            }
        shadowOf(packageManager).installPackage(info)
    }

    @Test
    fun anAppTheSnapshotLacksIsNamedByThePlatformAndRememberedAfterwards() {
        install("recorder", "Screen recorder")
        val labels = AndroidPackageLabels(packageManager)

        assertEquals("Screen recorder", labels.label("recorder"))
        shadowOf(packageManager).deletePackage("recorder")
        // Asked once: the second answer is the first, even though the package is gone.
        assertEquals("Screen recorder", labels.label("recorder"))
    }

    @Test
    fun anUnknownPackageHasNoLabelAndTheNoneResolverNeverNamesAnything() {
        assertNull(AndroidPackageLabels(packageManager).label("nowhere"))
        assertNull(PackageLabelResolver.NONE.label("recorder"))
    }
}
