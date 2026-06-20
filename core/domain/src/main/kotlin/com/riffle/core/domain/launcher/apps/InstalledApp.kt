package com.riffle.core.domain.launcher.apps

data class InstalledApp(
    val identity: AppIdentity,
    val label: String,
    val iconKey: AppIconKey? = null,
    val enabled: Boolean = true,
    val visibility: AppVisibility = AppVisibility.VISIBLE,
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
    }
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
}

enum class AppVisibility {
    VISIBLE,
    HIDDEN,
    EXCLUDED,
}
