package com.riffle.app.launcher.sources

import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

/**
 * The name the platform gives a package, for an app the installed-apps snapshot does not list (one with no launcher
 * entry, such as a screen recorder). Called off the main thread; null when the platform cannot say.
 */
internal fun interface PackageLabelResolver {
    fun label(packageName: String): String?

    companion object {
        /** Says nothing: such an app is named by its notification's title. */
        val NONE = PackageLabelResolver { null }
    }
}

/**
 * [PackageLabelResolver] over [PackageManager]. Each package is asked once: what the platform said, or that it could
 * not say, is kept for the life of the process (a package this launcher cannot see stays unseen).
 */
internal class AndroidPackageLabels(
    private val packageManager: PackageManager,
) : PackageLabelResolver {
    private val resolved = ConcurrentHashMap<String, String>()
    private val unknown = ConcurrentHashMap.newKeySet<String>()

    override fun label(packageName: String): String? {
        val known = resolved[packageName]
        if (known != null || packageName in unknown) return known
        val name =
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)) }
                .getOrNull()?.toString()?.takeIf { it.isNotBlank() }
        if (name == null) unknown += packageName else resolved[packageName] = name
        return name
    }
}
