package com.riffle.app

internal fun launcherVersionLabel(
    versionName: String?,
    versionCode: Long,
): String =
    when {
        versionName.isNullOrBlank() -> versionCode.toString()
        else -> "$versionName ($versionCode)"
    }
