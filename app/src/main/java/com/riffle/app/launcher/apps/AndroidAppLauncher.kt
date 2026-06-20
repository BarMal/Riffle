package com.riffle.app.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.riffle.core.domain.launcher.apps.AppIdentity

class AndroidAppLauncher(
    private val context: Context,
) {
    fun launch(identity: AppIdentity): Boolean =
        runCatching {
            context.startActivity(identity.launchIntent)
        }.isSuccess

    private val AppIdentity.launchIntent: Intent
        get() =
            Intent.makeMainActivity(
                ComponentName(
                    packageName.value,
                    activityName.value,
                ),
            )
}
