package com.riffle.app.launcher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile

internal sealed interface AppCatalogChange {
    data object Refresh : AppCatalogChange

    data class PackageRemoved(
        val packageName: AppPackageName,
        val profile: AppProfile,
    ) : AppCatalogChange
}

internal class AndroidPackageChangeObserver(
    private val context: Context,
    private val onCatalogChanged: (AppCatalogChange) -> Unit,
) : DefaultLifecycleObserver {
    private var registered = false
    private var profileReceiverRegistered = false
    private val launcherApps by lazy { context.getSystemService(LauncherApps::class.java) }
    private val userManager by lazy { context.getSystemService(UserManager::class.java) }
    private val profileReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }
        }
    private val callback =
        object : LauncherApps.Callback() {
            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }

            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) {
                onCatalogChanged(
                    AppCatalogChange.PackageRemoved(
                        packageName = AppPackageName(packageName),
                        profile = user.toAppProfile(userManager = userManager, launcherApps = launcherApps),
                    ),
                )
            }

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }

            override fun onPackagesSuspended(
                packageNames: Array<out String>,
                user: UserHandle,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>,
                user: UserHandle,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle,
            ) {
                onCatalogChanged(AppCatalogChange.Refresh)
            }
        }

    override fun onStart(owner: LifecycleOwner) {
        register()
    }

    override fun onStop(owner: LifecycleOwner) {
        unregister()
    }

    private fun register() {
        if (!registered) {
            launcherApps.registerCallback(callback)
            registered = true
        }
        if (!profileReceiverRegistered) {
            ContextCompat.registerReceiver(
                context,
                profileReceiver,
                profileChangeIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED,
            )
            profileReceiverRegistered = true
        }
    }

    private fun unregister() {
        if (registered) {
            runCatching {
                launcherApps.unregisterCallback(callback)
            }
            registered = false
        }
        if (profileReceiverRegistered) {
            runCatching {
                context.unregisterReceiver(profileReceiver)
            }
            profileReceiverRegistered = false
        }
    }
}

internal fun profileChangeIntentFilter(): IntentFilter =
    IntentFilter().apply {
        profileChangeFilterActions().forEach(::addAction)
    }

internal fun profileChangeFilterActions(): List<String> = profileChangeActions().toList()

internal fun profileChangeActions(): Set<String> =
    setOf(
        Intent.ACTION_MANAGED_PROFILE_ADDED,
        Intent.ACTION_MANAGED_PROFILE_AVAILABLE,
        Intent.ACTION_MANAGED_PROFILE_REMOVED,
        Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE,
        Intent.ACTION_MANAGED_PROFILE_UNLOCKED,
        Intent.ACTION_PROFILE_ACCESSIBLE,
        Intent.ACTION_PROFILE_ADDED,
        Intent.ACTION_PROFILE_AVAILABLE,
        Intent.ACTION_PROFILE_INACCESSIBLE,
        Intent.ACTION_PROFILE_REMOVED,
        Intent.ACTION_PROFILE_UNAVAILABLE,
    )
