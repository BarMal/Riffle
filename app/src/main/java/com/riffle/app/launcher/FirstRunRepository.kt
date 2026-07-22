package com.riffle.app.launcher

import com.riffle.core.domain.launcher.ShellDestination

data class HomeRoleRequestContext(
    val destination: ShellDestination,
)

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

    /** Legacy pending marker retained to recover an update interrupted before context migration. */
    fun isHomeRoleRequestPending(): Boolean = false

    fun setHomeRoleRequestPending(pending: Boolean) = Unit

    /** Legacy destination retained to recover an update interrupted before context migration. */
    fun homeRoleRequestDestination(): ShellDestination? = null

    fun setHomeRoleRequestDestination(destination: ShellDestination?) = Unit

    /**
     * Atomically persists the presentation-only context for a pending Home-role request.
     * A recreated process uses it to return to the destination being explored, but never to
     * infer a grant or relaunch Android UI.
     */
    fun homeRoleRequestContext(): HomeRoleRequestContext? = null

    /**
     * Compatibility fallback for repositories that have not yet adopted the atomic context
     * storage. The production SharedPreferences implementation overrides this in one edit.
     */
    fun setHomeRoleRequestContext(context: HomeRoleRequestContext?) {
        setHomeRoleRequestPending(pending = context != null)
        setHomeRoleRequestDestination(destination = context?.destination)
    }
}
