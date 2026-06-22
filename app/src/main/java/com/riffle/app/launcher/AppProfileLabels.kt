package com.riffle.app.launcher

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.apps.InstalledApp

fun InstalledApp.drawerSubtitle(): String =
    identity.profile.drawerProfilePrefix()
        ?.let { prefix -> "$prefix - ${identity.packageName.value}" }
        ?: identity.packageName.value

fun AppProfile.drawerProfilePrefix(): String? =
    when {
        type == AppProfileType.WORK -> "Work"
        id != AppProfile.personal().id -> "Personal"
        else -> null
    }
