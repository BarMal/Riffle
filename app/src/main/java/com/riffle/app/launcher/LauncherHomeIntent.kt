package com.riffle.app.launcher

import android.content.Intent

internal fun Intent.isLauncherHomeIntent(): Boolean =
    isLauncherHomeIntent(
        action = action,
        categories = categories,
    )

internal fun isLauncherHomeIntent(
    action: String?,
    categories: Set<String>?,
): Boolean = action == Intent.ACTION_MAIN && categories.orEmpty().contains(Intent.CATEGORY_HOME)
