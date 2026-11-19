package com.riffle.app.launcher.search.wider

import android.Manifest
import android.app.DownloadManager
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.ContextCompat
import com.riffle.app.R
import com.riffle.app.launcher.expressions.ExpressionImageLoader
import com.riffle.core.domain.launcher.search.wider.CalculatorSource
import com.riffle.core.domain.launcher.search.wider.ContactsSource
import com.riffle.core.domain.launcher.search.wider.FilesSource
import com.riffle.core.domain.launcher.search.wider.NumberSymbols
import com.riffle.core.domain.launcher.search.wider.SearchSourceId
import com.riffle.core.domain.launcher.search.wider.SystemSettingsSource
import com.riffle.core.domain.launcher.search.wider.WiderSearchAction
import com.riffle.core.domain.launcher.search.wider.WiderSearchEngine
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The real wider-search host. Sources are built once from the device: the calculator with the locale's separators, the
 * system settings that resolve here, contacts and files through their providers (each off the main thread, with a row
 * limit). Permissions are asked for only through [requestContacts] and [requestMedia], which the activity launches from
 * the inline "Allow" row and nowhere else. Nothing is stored, logged or sent anywhere.
 */
internal class AndroidWiderSearchHost(
    private val context: Context,
    private val requestContacts: () -> Unit,
    private val requestMedia: () -> Unit,
) : WiderSearchHost {
    private val version = mutableIntStateOf(0)
    private val resolver: ContentResolver get() = context.contentResolver
    private val files = AndroidFilesReader(resolver, ::hasMediaAccess)

    override val engine: WiderSearchEngine =
        WiderSearchEngine(
            listOf(
                CalculatorSource(symbols = ::localeSymbols),
                SystemSettingsSource(entries = SystemSettingsCatalog(context)::entries),
                ContactsSource(AndroidContactsReader(resolver)),
                FilesSource(files),
            ),
        )

    override val contactPhotos: ExpressionImageLoader = ContactPhotoLoader(resolver)

    override val accessVersion: Int get() = version.intValue

    /** A permission result arrived, or the app came back from system settings: readers check again. */
    fun onAccessMayHaveChanged() {
        version.intValue++
    }

    override fun hasAccess(source: SearchSourceId): Boolean =
        when (source) {
            SearchSourceId.CONTACTS -> granted(Manifest.permission.READ_CONTACTS)
            SearchSourceId.FILES -> hasMediaAccess()
            else -> true
        }

    override fun requestAccess(source: SearchSourceId) {
        when (source) {
            SearchSourceId.CONTACTS -> requestContacts()
            SearchSourceId.FILES -> requestMedia()
            else -> Unit
        }
    }

    private fun hasMediaAccess(): Boolean = MEDIA_PERMISSIONS.any(::granted)

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun perform(action: WiderSearchAction) {
        when (action) {
            is WiderSearchAction.Copy -> copy(action.text)
            is WiderSearchAction.OpenSystemSetting -> launch(Intent(action.intentAction))
            is WiderSearchAction.OpenContact -> launch(Intent(Intent.ACTION_VIEW, Uri.parse(action.contactUri)))
            // Only the dialler and the messaging app open: nothing is called or sent from here.
            is WiderSearchAction.Dial -> launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", action.number, null)))
            is WiderSearchAction.Message ->
                launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", action.number, null)))
            is WiderSearchAction.OpenFile ->
                launch(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(Uri.parse(action.uri), action.mimeType)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                )
            is WiderSearchAction.OpenFilesApp ->
                launch(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).putExtra(SearchManager.QUERY, action.query))
        }
    }

    private fun copy(text: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
        // Android 13 and later confirm a copy themselves.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, R.string.search_copied, Toast.LENGTH_SHORT).show()
        }
    }

    private fun launch(intent: Intent) {
        val started = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!started) Toast.makeText(context, R.string.search_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun localeSymbols(): NumberSymbols {
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        return NumberSymbols(symbols.decimalSeparator, symbols.groupingSeparator)
    }

    companion object {
        private val MEDIA_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                )
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

        /** The permissions asked for when the person taps "Allow" on the Files row. */
        fun mediaPermissions(): Array<String> = MEDIA_PERMISSIONS.toTypedArray()
    }
}

/**
 * The activity's two permission launchers and the host that uses them. Built as a property of the activity (a launcher
 * must be registered before it starts). The launchers run only when [AndroidWiderSearchHost.requestAccess] is called,
 * which only an inline "Allow" tap does; a result, granted or not, makes the search check its access again.
 */
internal class WiderSearchAccess(
    activity: ComponentActivity,
) {
    private val contacts =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            host.onAccessMayHaveChanged()
        }
    private val media =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            host.onAccessMayHaveChanged()
        }

    val host: AndroidWiderSearchHost =
        AndroidWiderSearchHost(
            context = activity,
            requestContacts = { contacts.launch(Manifest.permission.READ_CONTACTS) },
            requestMedia = { media.launch(AndroidWiderSearchHost.mediaPermissions()) },
        )
}
