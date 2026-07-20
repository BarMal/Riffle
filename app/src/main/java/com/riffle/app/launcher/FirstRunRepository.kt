package com.riffle.app.launcher

interface FirstRunRepository {
    /**
     * Legacy onboarding completion marker, retained only to migrate the setup-card presentation
     * for existing installs. It must not be used as a capability or Home-role source of truth.
     */
    fun isFirstRunComplete(): Boolean

    fun setFirstRunComplete()

    /** Whether the optional preview setup card should stay dismissed. */
    fun isSetupCardDismissed(): Boolean = isFirstRunComplete()

    fun setSetupCardDismissed() = Unit

    /**
     * Presentation-only recovery state for a Home-role system request that may outlive an
     * activity or process. A recreated process preserves this marker until the first live
     * Home-role reconciliation; the request is never relaunched from this value.
     */
    fun isHomeRoleRequestPending(): Boolean = false

    fun setHomeRoleRequestPending(pending: Boolean) = Unit
}
