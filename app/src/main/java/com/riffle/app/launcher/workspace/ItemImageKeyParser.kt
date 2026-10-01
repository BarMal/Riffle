package com.riffle.app.launcher.workspace

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.workspace.ItemImageHandle

/** What an image handle names, parsed from the `ItemImageKeys` scheme. */
internal sealed interface ParsedImageKey {
    /** The launcher icon of one activity. */
    data class ActivityIcon(val identity: AppIdentity) : ParsedImageKey

    /** The default icon of a package. */
    data class PackageIcon(val profileId: String, val packageName: String) : ParsedImageKey

    /** A key this build cannot resolve (feed and notification artwork have no loader yet). */
    data object Unsupported : ParsedImageKey
}

/**
 * Reads the keys `ItemImageKeys` writes. Profile ids may contain colons (`user:10`), package and activity
 * names never contain `:` or `/`, so the profile is everything before the last colon of the head part.
 */
internal object ItemImageKeyParser {
    private const val APP_ICON = "app-icon:"
    private const val PACKAGE_ICON = "package-icon:"

    fun parse(handle: ItemImageHandle): ParsedImageKey =
        when {
            handle.key.startsWith(APP_ICON) -> parseActivityIcon(handle.key.removePrefix(APP_ICON))
            handle.key.startsWith(PACKAGE_ICON) -> parsePackageIcon(handle.key.removePrefix(PACKAGE_ICON))
            else -> ParsedImageKey.Unsupported
        }

    private fun parseActivityIcon(rest: String): ParsedImageKey {
        val slash = rest.indexOf('/')
        val head = if (slash > 0) rest.substring(0, slash) else ""
        val activity = if (slash > 0) rest.substring(slash + 1) else ""
        val colon = head.lastIndexOf(':')
        return if (colon <= 0 || colon == head.lastIndex || activity.isBlank()) {
            ParsedImageKey.Unsupported
        } else {
            ParsedImageKey.ActivityIcon(
                AppIdentity(
                    packageName = AppPackageName(head.substring(colon + 1)),
                    activityName = AppActivityName(activity),
                    profile = profileFor(head.substring(0, colon)),
                ),
            )
        }
    }

    private fun parsePackageIcon(rest: String): ParsedImageKey {
        val colon = rest.lastIndexOf(':')
        return if (colon <= 0 || colon == rest.lastIndex) {
            ParsedImageKey.Unsupported
        } else {
            ParsedImageKey.PackageIcon(profileId = rest.substring(0, colon), packageName = rest.substring(colon + 1))
        }
    }

    /** The profile type does not matter for resolving or launching (profiles match by id); this is a best guess. */
    fun profileFor(id: String): AppProfile =
        listOf(AppProfile.personal(), AppProfile.work(), AppProfile.private())
            .firstOrNull { it.id.value == id }
            ?: AppProfile(AppProfileId(id), AppProfileType.WORK)
}
