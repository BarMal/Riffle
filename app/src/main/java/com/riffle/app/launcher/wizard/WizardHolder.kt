package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.workspace.wizard.ContentGoal
import com.riffle.core.domain.launcher.workspace.wizard.ContentRecipes
import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.SourcePermission
import com.riffle.core.domain.launcher.workspace.wizard.WizardAction
import com.riffle.core.domain.launcher.workspace.wizard.WizardContext
import com.riffle.core.domain.launcher.workspace.wizard.WizardFlow
import com.riffle.core.domain.launcher.workspace.wizard.WizardReducer
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import com.riffle.core.domain.launcher.workspace.wizard.WizardStep

/** What the wizard screen shows: the domain state and, after a refused apply, why (a polite live region). */
internal data class WizardUiState(
    val wizard: WizardState,
    val notice: String? = null,
)

/** What the host did with a finished wizard: closed it, or refused (the wizard stays open on its last step). */
internal sealed interface WizardOutcome {
    data object Closed : WizardOutcome

    data class Refused(val message: String) : WizardOutcome
}

/**
 * The wizard's state holder, pure so it is JVM-tested: it drives [WizardReducer] and adds what the screen needs on
 * top (the restored state after process death, the visible steps, a refusal notice). Every decision stays the
 * domain's; nothing here validates on its own.
 */
internal object WizardHolder {
    /** [restored] (from saved instance state) when it is still a step this run shows, else a fresh start. */
    fun start(
        context: WizardContext,
        restored: WizardState? = null,
    ): WizardUiState {
        val usable =
            restored?.takeIf {
                it.exit == null && it.step != WizardStep.DONE && it.step in WizardFlow.steps(it, context)
            }
        return WizardUiState(usable ?: WizardFlow.start(context))
    }

    /** One action; any change ends a previous refusal notice. */
    fun reduce(
        state: WizardUiState,
        action: WizardAction,
        context: WizardContext,
    ): WizardUiState {
        val next = WizardReducer.reduce(state.wizard, action, context)
        return if (next == state.wizard) state else WizardUiState(next)
    }

    /**
     * The host could not apply (the layout changed meanwhile, a read-only home): reopen on the last step with
     * [message], so nothing the user chose is lost and Back still works.
     */
    fun refused(
        state: WizardUiState,
        message: String,
        context: WizardContext,
    ): WizardUiState {
        val last = visibleSteps(state.wizard, context).lastOrNull() ?: WizardStep.SOURCE
        return WizardUiState(state.wizard.copy(step = last, exit = null), message)
    }

    /** The steps shown with "Step n of m", without the closing [WizardStep.DONE]. */
    fun visibleSteps(
        state: WizardState,
        context: WizardContext,
    ): List<WizardStep> = WizardFlow.steps(state, context).filter { it != WizardStep.DONE }

    /** 1-based position of the current step and the count, for "Step n of m". */
    fun progress(
        state: WizardState,
        context: WizardContext,
    ): Pair<Int, Int> {
        val steps = visibleSteps(state, context)
        return (steps.indexOf(state.step) + 1).coerceAtLeast(1) to steps.size
    }

    /** True on the step whose Next finishes (its button reads "Add" or "Save"). */
    fun isLast(
        state: WizardState,
        context: WizardContext,
    ): Boolean = visibleSteps(state, context).lastOrNull() == state.step

    fun lensOnly(state: WizardState): Boolean = state.destination == Destination.LENS_ONLY

    /** The chosen goal and the access it still needs, for the steps after Source (a source page's run skips it). */
    fun neededAccess(
        state: WizardState,
        context: WizardContext,
    ): Pair<ContentGoal, List<SourcePermission>>? {
        val setup = state.setup ?: return null
        val goal = ContentRecipes.goal(setup)
        val needs = WizardFlow.goalChoice(goal, context).needs
        return if (needs.isEmpty()) null else goal to needs
    }

    /** The name the closing announcement uses: the saved lens's, else the goal's label. */
    fun addedName(
        state: WizardState,
        context: WizardContext,
    ): String =
        if (WizardFlow.saves(state)) {
            WizardFlow.name(state, context)
        } else {
            state.setup?.let { ContentRecipes.goal(it).label }.orEmpty()
        }
}
