package com.riffle.app.launcher.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class AndroidPackageChangeObserver(
    private val context: Context,
    private val onPackagesChanged: () -> Unit,
) : DefaultLifecycleObserver {
    private var registered = false
    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
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
            ContextCompat.registerReceiver(
                context,
                receiver,
                packageChangeIntentFilter(),
                ContextCompat.RECEIVER_EXPORTED,
            )
            registered = true
        }
    }

    private fun unregister() {
        if (registered) {
            runCatching {
                context.unregisterReceiver(receiver)
            }
            registered = false
        }
    }
}

fun packageChangeIntentFilter(): IntentFilter =
    IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_CHANGED)
        addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addAction(Intent.ACTION_PACKAGE_REPLACED)
        addDataScheme("package")
    }
