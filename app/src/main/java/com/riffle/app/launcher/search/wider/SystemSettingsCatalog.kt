package com.riffle.app.launcher.search.wider

import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import com.riffle.app.R
import com.riffle.core.domain.launcher.search.wider.SystemSettingEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One system settings screen: the `Settings.ACTION_*` intent that opens it and the words that find it. */
internal class SystemSettingSpec(
    val id: String,
    val intentAction: String,
    @StringRes val labelRes: Int,
    @StringRes val keywordsRes: Int,
)

/**
 * The common system settings screens, offered only where they resolve on this device. The check runs once, off the
 * main thread, and is kept for the life of the process (the set of screens an installation has does not change while
 * the launcher runs; a reinstall restarts it). Labels and keywords are string resources.
 */
internal class SystemSettingsCatalog(
    private val context: Context,
    private val specs: List<SystemSettingSpec> = SPECS,
) {
    @Volatile
    private var resolved: List<SystemSettingEntry>? = null

    suspend fun entries(): List<SystemSettingEntry> =
        resolved ?: withContext(Dispatchers.Default) { resolve() }.also { resolved = it }

    private fun resolve(): List<SystemSettingEntry> {
        val packageManager = context.packageManager
        return specs.filter { spec -> packageManager.resolveActivity(Intent(spec.intentAction), 0) != null }
            .map { spec ->
                SystemSettingEntry(
                    id = spec.id,
                    title = context.getString(spec.labelRes),
                    keywords = context.getString(spec.keywordsRes).split(' ').filter(String::isNotBlank),
                    intentAction = spec.intentAction,
                )
            }
    }

    companion object {
        /** Every screen the catalog may offer; also what the manifest's `queries` must list. */
        val SPECS: List<SystemSettingSpec> =
            listOf(
                SystemSettingSpec(
                    "wifi",
                    "android.settings.WIFI_SETTINGS",
                    R.string.system_setting_wifi,
                    R.string.system_setting_wifi_keywords,
                ),
                SystemSettingSpec(
                    "bluetooth",
                    "android.settings.BLUETOOTH_SETTINGS",
                    R.string.system_setting_bluetooth,
                    R.string.system_setting_bluetooth_keywords,
                ),
                SystemSettingSpec(
                    "network",
                    "android.settings.WIRELESS_SETTINGS",
                    R.string.system_setting_network,
                    R.string.system_setting_network_keywords,
                ),
                SystemSettingSpec(
                    "airplane",
                    "android.settings.AIRPLANE_MODE_SETTINGS",
                    R.string.system_setting_airplane,
                    R.string.system_setting_airplane_keywords,
                ),
                SystemSettingSpec(
                    "hotspot",
                    "com.android.settings.TETHER_SETTINGS",
                    R.string.system_setting_hotspot,
                    R.string.system_setting_hotspot_keywords,
                ),
                SystemSettingSpec(
                    "data_usage",
                    "android.settings.DATA_USAGE_SETTINGS",
                    R.string.system_setting_data_usage,
                    R.string.system_setting_data_usage_keywords,
                ),
                SystemSettingSpec(
                    "nfc",
                    "android.settings.NFC_SETTINGS",
                    R.string.system_setting_nfc,
                    R.string.system_setting_nfc_keywords,
                ),
                SystemSettingSpec(
                    "display",
                    "android.settings.DISPLAY_SETTINGS",
                    R.string.system_setting_display,
                    R.string.system_setting_display_keywords,
                ),
                SystemSettingSpec(
                    "sound",
                    "android.settings.SOUND_SETTINGS",
                    R.string.system_setting_sound,
                    R.string.system_setting_sound_keywords,
                ),
                SystemSettingSpec(
                    "notifications",
                    "android.settings.ALL_APPS_NOTIFICATION_SETTINGS",
                    R.string.system_setting_notifications,
                    R.string.system_setting_notifications_keywords,
                ),
                SystemSettingSpec(
                    "battery",
                    "android.settings.BATTERY_SAVER_SETTINGS",
                    R.string.system_setting_battery,
                    R.string.system_setting_battery_keywords,
                ),
                SystemSettingSpec(
                    "storage",
                    "android.settings.INTERNAL_STORAGE_SETTINGS",
                    R.string.system_setting_storage,
                    R.string.system_setting_storage_keywords,
                ),
                SystemSettingSpec(
                    "apps",
                    "android.settings.MANAGE_APPLICATIONS_SETTINGS",
                    R.string.system_setting_apps,
                    R.string.system_setting_apps_keywords,
                ),
                SystemSettingSpec(
                    "default_apps",
                    "android.settings.MANAGE_DEFAULT_APPS_SETTINGS",
                    R.string.system_setting_default_apps,
                    R.string.system_setting_default_apps_keywords,
                ),
                SystemSettingSpec(
                    "location",
                    "android.settings.LOCATION_SOURCE_SETTINGS",
                    R.string.system_setting_location,
                    R.string.system_setting_location_keywords,
                ),
                SystemSettingSpec(
                    "privacy",
                    "android.settings.PRIVACY_SETTINGS",
                    R.string.system_setting_privacy,
                    R.string.system_setting_privacy_keywords,
                ),
                SystemSettingSpec(
                    "security",
                    "android.settings.SECURITY_SETTINGS",
                    R.string.system_setting_security,
                    R.string.system_setting_security_keywords,
                ),
                SystemSettingSpec(
                    "accessibility",
                    "android.settings.ACCESSIBILITY_SETTINGS",
                    R.string.system_setting_accessibility,
                    R.string.system_setting_accessibility_keywords,
                ),
                SystemSettingSpec(
                    "date_time",
                    "android.settings.DATE_SETTINGS",
                    R.string.system_setting_date_time,
                    R.string.system_setting_date_time_keywords,
                ),
                SystemSettingSpec(
                    "language",
                    "android.settings.LOCALE_SETTINGS",
                    R.string.system_setting_language,
                    R.string.system_setting_language_keywords,
                ),
                SystemSettingSpec(
                    "keyboard",
                    "android.settings.INPUT_METHOD_SETTINGS",
                    R.string.system_setting_keyboard,
                    R.string.system_setting_keyboard_keywords,
                ),
                SystemSettingSpec(
                    "accounts",
                    "android.settings.SYNC_SETTINGS",
                    R.string.system_setting_accounts,
                    R.string.system_setting_accounts_keywords,
                ),
                SystemSettingSpec(
                    "cast",
                    "android.settings.CAST_SETTINGS",
                    R.string.system_setting_cast,
                    R.string.system_setting_cast_keywords,
                ),
                SystemSettingSpec(
                    "developer",
                    "android.settings.APPLICATION_DEVELOPMENT_SETTINGS",
                    R.string.system_setting_developer,
                    R.string.system_setting_developer_keywords,
                ),
                SystemSettingSpec(
                    "about",
                    "android.settings.DEVICE_INFO_SETTINGS",
                    R.string.system_setting_about,
                    R.string.system_setting_about_keywords,
                ),
            )
    }
}
