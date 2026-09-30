package com.riffle.core.domain.launcher.workspace.sources

import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.workspace.ItemExtKey
import com.riffle.core.domain.launcher.workspace.ItemExtValue
import com.riffle.core.domain.launcher.workspace.WorkspaceSourceIds

/**
 * The `app.profile` ext every app-backed item carries so lenses can filter or group by profile:
 * `personal`, `work`, or `private` for the private space. A profile is not content, so it is also set on
 * redacted items (a quiet work profile must still land on the Work page).
 */
internal fun appProfileExt(type: AppProfileType): Map<ItemExtKey, ItemExtValue> =
    mapOf(WorkspaceSourceIds.APP_PROFILE_EXT to ItemExtValue.Text(type.name.lowercase()))

/** For sources that only know a profile id: the type from [known] profiles, else from the well-known ids. */
internal fun appProfileExt(
    profileId: AppProfileId,
    known: Map<AppProfileId, AppProfileType>,
): Map<ItemExtKey, ItemExtValue> {
    val type =
        known[profileId]
            ?: listOf(AppProfile.personal(), AppProfile.work(), AppProfile.private())
                .firstOrNull { profile -> profile.id == profileId }
                ?.type
    return type?.let(::appProfileExt).orEmpty()
}
