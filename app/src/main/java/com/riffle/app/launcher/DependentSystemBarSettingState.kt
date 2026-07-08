package com.riffle.app.launcher

internal fun dependentSystemBarSettingState(
    fullscreenHome: Boolean,
    hidden: Boolean,
    enabledSubtitle: String,
): DependentSystemBarSettingState =
    DependentSystemBarSettingState(
        checked = hidden,
        enabled = !fullscreenHome,
        subtitle =
            if (fullscreenHome) {
                "Turn off Fullscreen home to choose bars separately"
            } else {
                enabledSubtitle
            },
    )

internal data class DependentSystemBarSettingState(
    val checked: Boolean,
    val enabled: Boolean,
    val subtitle: String,
)
