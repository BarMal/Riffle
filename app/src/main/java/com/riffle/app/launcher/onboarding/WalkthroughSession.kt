package com.riffle.app.launcher.onboarding

import com.riffle.core.domain.launcher.workspace.sources.SourceAccess
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughAction
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughContext
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughFlow
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughReducer
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughState
import com.riffle.core.domain.launcher.workspace.wizard.WalkthroughStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the walkthrough screen draws: the domain state and the context it was reduced against. */
internal data class WalkthroughUi(
    val state: WalkthroughState,
    val context: WalkthroughContext,
) {
    val steps: List<WalkthroughStep> get() = WalkthroughFlow.steps(state, context)

    /**
     * 1-based position of the current step among the visible ones, for "Step n of m" and the progress bar. By order,
     * so a step that just left the sequence (Permissions, once everything is granted) keeps its place.
     */
    val position: Int get() = steps.count { it.ordinal <= state.step.ordinal }.coerceAtLeast(1)

    val canAdvance: Boolean get() = WalkthroughFlow.canAdvance(state, context)
}

/**
 * One run of the walkthrough (Android-free): [WalkthroughReducer] over a [WalkthroughContext] that is rebuilt by
 * [contextOf] whenever access may have changed. Permissions follow the domain's rule (an explanation first, one at a
 * time): when a [WalkthroughAction.RequestPermission] is accepted, [launchPermission] runs the existing explicit flow
 * for it, synchronously from the user's tap. The result is reported back as [WalkthroughAction.PermissionResult]:
 * - when the host returns to the foreground after the flow ([onHostPaused] then [onHostResumed]; a system dialog or a
 *   settings screen both pause the launcher), or
 * - when the user taps "Check again" ([checkPermissionAgain]), for a flow that could not open at all.
 * Whether it was granted is read from the rebuilt context, never assumed from the flow's own result.
 */
internal class WalkthroughSession(
    private val contextOf: () -> WalkthroughContext,
    private val launchPermission: (SourcePermission) -> Unit,
) {
    private val mutableUi: MutableStateFlow<WalkthroughUi>

    init {
        val context = contextOf()
        mutableUi = MutableStateFlow(WalkthroughUi(WalkthroughFlow.start(context), context))
    }

    val ui: StateFlow<WalkthroughUi> = mutableUi.asStateFlow()

    private var awaitingReturn = false
    private var leftForFlow = false

    fun dispatch(action: WalkthroughAction) {
        val current = mutableUi.value
        val next = WalkthroughReducer.reduce(current.state, action, current.context)
        if (next == current.state) return
        mutableUi.value = current.copy(state = next)
        val requested = next.pendingRequest
        if (requested != null && current.state.pendingRequest == null) {
            awaitingReturn = true
            leftForFlow = false
            launchPermission(requested)
        }
    }

    /** Rebuilds the context (access, workspaces, pool, apps), keeping the user's choices. */
    fun refresh() {
        mutableUi.value = mutableUi.value.copy(context = contextOf())
    }

    fun onHostPaused() {
        if (awaitingReturn) leftForFlow = true
    }

    fun onHostResumed() {
        if (awaitingReturn && leftForFlow) reportPending() else refresh()
    }

    /**
     * After an apply that did not go through (the home is read-only or still loading, or the domain refused a stale
     * state): back on the Review step with every choice kept, so the user can try again or leave.
     */
    fun reopenReview() {
        val current = mutableUi.value
        mutableUi.value = WalkthroughUi(current.state.copy(step = WalkthroughStep.REVIEW, exit = null), contextOf())
    }

    /** "Check again" while a request is pending: reports what access is now, whatever the flow did. */
    fun checkPermissionAgain() {
        if (mutableUi.value.state.pendingRequest != null) reportPending()
    }

    private fun reportPending() {
        awaitingReturn = false
        leftForFlow = false
        val context = contextOf()
        val state = mutableUi.value.state
        val permission = state.pendingRequest
        if (permission == null) {
            mutableUi.value = WalkthroughUi(state, context)
            return
        }
        val access = context.sources.associate { it.id to it.access }
        val granted = SourcePermission.accessOf(permission, access) == SourceAccess.GRANTED
        val result = WalkthroughAction.PermissionResult(permission, granted)
        val reported = WalkthroughReducer.reduce(state, result, context)
        mutableUi.value = WalkthroughUi(reported, context)
    }
}
