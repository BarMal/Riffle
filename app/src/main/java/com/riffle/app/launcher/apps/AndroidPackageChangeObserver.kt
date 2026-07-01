package com.riffle.app.launcher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class AndroidPackageChangeObserver(
    private val context: Context,
    private val onPackagesChanged: () -> Unit,
) : DefaultLifecycleObserver {
    private var registered = false
    private var profileReceiverRegistered = false
    private val launcherApps by lazy { context.getSystemService(LauncherApps::class.java) }
    private val profileReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                onPackagesChanged()
            }
        }
    private val callback =
        object : LauncherApps.Callback() {
            override fun onPackageAdded(
                packageName: String,
                user: UserHandle,
            ) {
                onPackagesChanged()
            }

            override fun onPackageRemoved(
                packageName: String,
                user: UserHandle,
            ) {
                onPackagesChanged()
            }

            override fun onPackageChanged(
                packageName: String,
                user: UserHandle,
            ) {
                onPackagesChanged()
            }

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                onPackagesChanged()
            }

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: UserHandle,
                replacing: Boolean,
            ) {
                onPackagesChanged()
            }

            override fun onPackagesSuspended(
                packageNames: Array<out String>,
                user: UserHandle,
            ) {
                onPackagesChanged()
            }

            override fun onPackagesUnsuspended(
                packageNames: Array<out String>,
                user: UserHandle,
            ) {
                onPackagesChanged()
            }

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: UserHandle,
            ) {
                onPackagesChanged()
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
        profileChangeActions().forEach(::addAction)
    }

internal fun profileChangeActions(): Set<String> =
    setOf(
        Intent.ACTION_PROFILE_AVAILABLE,
        Intent.ACTION_PROFILE_UNAVAILABLE,
        Intent.ACTION_MANAGED_PROFILE_ADDED,
        Intent.ACTION_MANAGED_PROFILE_REMOVED,
    )
