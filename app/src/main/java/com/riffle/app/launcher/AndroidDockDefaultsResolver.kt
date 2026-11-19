package com.riffle.app.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Telephony
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.home.DockDefaultsResolver

/**
 * The Dock's first-run apps, found by role or intent and never by package name: the default dialer (`ACTION_DIAL`),
 * the default SMS app, the default browser (`ACTION_VIEW` of an https address) and the camera
 * (`INTENT_ACTION_STILL_IMAGE_CAMERA`, then `ACTION_IMAGE_CAPTURE`), in that order. A role that resolves to nothing, or
 * only to the system's chooser (the user has not picked a default), is left out; whether the package is launchable is
 * decided where the list is used, against the installed apps. Needs the matching `<queries>` entries.
 */
internal class AndroidDockDefaultsResolver(
    private val context: Context,
) : DockDefaultsResolver {
    override fun resolve(): List<AppPackageName> =
        listOfNotNull(dialer(), messages(), browser(), camera()).distinct().map(::AppPackageName)

    private fun dialer(): String? = resolved(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")))

    private fun messages(): String? =
        runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()
            ?: resolved(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))

    private fun browser(): String? =
        resolved(Intent(Intent.ACTION_VIEW, Uri.parse("https://")).addCategory(Intent.CATEGORY_BROWSABLE))

    private fun camera(): String? =
        resolved(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?: resolved(Intent(MediaStore.ACTION_IMAGE_CAPTURE))

    /** The package of the activity the system would open for [intent], or null when none or only the chooser. */
    private fun resolved(intent: Intent): String? =
        runCatching {
            val flags = PackageManager.MATCH_DEFAULT_ONLY
            val info =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.resolveActivity(intent, flags)
                }
            info?.activityInfo?.packageName?.takeIf { it != SYSTEM_CHOOSER_PACKAGE && it != context.packageName }
        }.getOrNull()

    private companion object {
        /** The package of the framework's resolver (chooser) activity: no default has been chosen. */
        const val SYSTEM_CHOOSER_PACKAGE = "android"
    }
}
