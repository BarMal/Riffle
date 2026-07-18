package com.riffle.app.launcher

interface FirstRunRepository {
    /** Legacy onboarding completion marker, retained to migrate existing installs. */
    fun isFirstRunComplete(): Boolean

    fun setFirstRunComplete()

    /** Whether the optional preview setup card should stay dismissed. */
    fun isSetupCardDismissed(): Boolean = isFirstRunComplete()

    fun setSetupCardDismissed() = Unit
}
