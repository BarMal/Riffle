package com.riffle.app

internal fun launcherVersionLabel(
    versionName: String?,
    versionCode: Long,
): String =
    when {
        versionName.isNullOrBlank() -> versionCode.toString()
        else -> "$versionName ($versionCode)"
    }

internal fun launcherBuildIdentityLabel(
    appVersionLabel: String,
    packageName: String,
    buildType: String,
): String =
    listOf(appVersionLabel, packageName, buildType)
        .filter(String::isNotBlank)
        .joinToString(" / ")
