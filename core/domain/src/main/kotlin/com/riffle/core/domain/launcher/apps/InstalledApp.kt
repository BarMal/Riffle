package com.riffle.core.domain.launcher.apps

data class InstalledApp(
    val identity: AppIdentity,
    val label: String,
    val iconKey: AppIconKey? = null,
    val enabled: Boolean = true,
    val visibility: AppVisibility = AppVisibility.VISIBLE,
    val category: String? = null,
)

data class AppIdentity(
    val packageName: AppPackageName,
    val activityName: AppActivityName,
    val profile: AppProfile = AppProfile.personal(),
)

data class AppProfile(
    val id: AppProfileId,
    val type: AppProfileType,
) {
    companion object {
        fun personal(): AppProfile =
            AppProfile(
                id = AppProfileId("personal"),
                type = AppProfileType.PERSONAL,
            )

        fun work(): AppProfile =
            AppProfile(
                id = AppProfileId("work"),
                type = AppProfileType.WORK,
            )

        fun private(): AppProfile =
            AppProfile(
                id = AppProfileId("private"),
                type = AppProfileType.PRIVATE,
            )
    }
}

/** Whether notification content for a profile may be exposed outside the system notification shade. */
enum class AppProfileContentVisibility {
    VISIBLE,
    REDACTED_QUIET,
    REDACTED_LOCKED,
    REDACTED_UNAVAILABLE,
}

@JvmInline
value class AppProfileId(val value: String)

@JvmInline
value class AppPackageName(val value: String)

@JvmInline
value class AppActivityName(val value: String)

@JvmInline
value class AppIconKey(val value: String)

enum class AppProfileType {
    PERSONAL,
    WORK,
    PRIVATE,
}

enum class AppVisibility {
    VISIBLE,
    HIDDEN,
    EXCLUDED,
}
