package com.riffle.app.launcher.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserHandle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class AndroidPackageChangeObserver(
    private val context: Context,
    private val onPackagesChanged: () -> Unit,
) : DefaultLifecycleObserver {
    private var registered = false
    private val launcherApps by lazy { context.getSystemService(LauncherApps::class.java) }
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
    }

    private fun unregister() {
        if (registered) {
            runCatching {
                launcherApps.unregisterCallback(callback)
            }
            registered = false
        }
    }
}
