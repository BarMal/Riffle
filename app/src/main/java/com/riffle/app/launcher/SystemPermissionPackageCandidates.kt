package com.riffle.app.launcher

private const val DEBUG_APPLICATION_ID_SUFFIX = ".debug"

internal fun systemPermissionPackageCandidates(appPackageName: String): Set<String> =
    buildSet {
        add(appPackageName)
        appPackageName
            .removeSuffix(DEBUG_APPLICATION_ID_SUFFIX)
            .takeIf { releasePackageName -> releasePackageName != appPackageName }
            ?.let(::add)
    }
