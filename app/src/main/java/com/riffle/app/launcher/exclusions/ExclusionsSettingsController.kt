package com.riffle.app.launcher.exclusions

import com.riffle.core.domain.launcher.home.HomeLayoutDeviceClass
import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceSubscription
import com.riffle.core.domain.launcher.workspace.WorkspaceIdFactory
import com.riffle.core.domain.launcher.workspace.exclusions.ExclusionRuleId
import com.riffle.core.domain.launcher.workspace.exclusions.LayoutExclusionRules
import com.riffle.core.domain.launcher.workspace.settings.ExclusionHideActions
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsChange
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsAction
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsModel
import com.riffle.core.domain.launcher.workspace.settings.ExclusionsSettingsPlanner
import com.riffle.core.domain.launcher.workspace.settings.HideKind
import com.riffle.core.domain.launcher.workspace.settings.TextRuleDraft
import com.riffle.core.domain.launcher.workspace.settings.TextRuleProblem
import com.riffle.core.domain.launcher.workspace.settings.TextRuleValidator
import com.riffle.core.domain.launcher.workspace.settings.applyTo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The one-time announcement the page shows after an action: [message] is read out by the snackbar and
 * [canUndo] adds its Undo button. [id] is new for every announcement so an identical message shows again.
 */
internal data class ExclusionsFeedback(
    val id: Long,
    val message: String,
    val canUndo: Boolean,
    /** True for a contextual Hide that touched one layout: the snackbar then also offers "Hide on all layouts". */
    val canHideEverywhere: Boolean = false,
)

/**
 * Runs the Hidden items page's actions (and the contextual Hide action) against the [repository] and keeps the
 * one Undo the announcement offers. No Android types. Every change goes through [CachedExclusionRepository.update],
 * so lens observers re-evaluate and the rules are persisted; nothing here reads or writes home items.
 *
 * Only the latest destructive change can be undone, and the offer ends if anything else changes the rules after
 * it (a Hide from a page, another edit), so Undo can never overwrite something newer. While the page is open
 * ([open] to [close]) [counter] reads the sources the viewed layout's rules can hide from, to count what they
 * hide; closing releases every subscription.
 */
@Suppress("TooManyFunctions")
internal class ExclusionsSettingsController(
    private val repository: CachedExclusionRepository,
    private val counterFor: ((Map<ExclusionRuleId, Int>) -> Unit) -> ExclusionMatchCounter,
    private val disabledSources: () -> Set<SourceId>,
    private val layoutName: (HomeLayoutDeviceClass) -> String,
    private val ids: WorkspaceIdFactory = WorkspaceIdFactory.Random,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private class UndoOffer(val change: ExclusionsChange, val version: Int)

    /** What the last contextual Hide hid, kept only while its snackbar is on offer (memory only, never stored). */
    private class HideRequest(val layout: HomeLayoutDeviceClass, val item: Item, val kind: HideKind)

    private var offer: UndoOffer? = null
    private var lastHide: HideRequest? = null
    private var announcements = 0L
    private var counter: ExclusionMatchCounter? = null
    private var observing: SourceSubscription? = null

    @Volatile
    private var viewed: HomeLayoutDeviceClass? = null
    private val mutableFeedback = MutableStateFlow<ExclusionsFeedback?>(null)
    private val mutableCounts = MutableStateFlow<Map<ExclusionRuleId, Int>>(emptyMap())

    val feedback: StateFlow<ExclusionsFeedback?> = mutableFeedback.asStateFlow()

    /** What each enabled rule hides right now (only rules whose source has reported). Empty while closed. */
    val counts: StateFlow<Map<ExclusionRuleId, Int>> = mutableCounts.asStateFlow()

    /** Changes whenever the rules do, so the page re-plans. */
    val version: StateFlow<Int> get() = repository.version

    /** Starts counting for the page. Idempotent. */
    fun open() {
        if (counter != null) return
        counter = counterFor { next -> mutableCounts.value = next }.also { it.start() }
        observing = repository.observe { retrack() }
        retrack()
    }

    /** Releases every subscription and forgets the counts and the Undo offer. Idempotent. */
    fun close() {
        observing?.cancel()
        observing = null
        counter?.stop()
        counter = null
        mutableCounts.value = emptyMap()
        leave()
    }

    /** The layout whose rules the page shows; counting follows it. */
    fun watch(layout: HomeLayoutDeviceClass) {
        viewed = layout
        retrack()
    }

    /** The page's model for [viewed], or null until the rules have loaded. */
    fun model(
        viewed: HomeLayoutDeviceClass,
        available: Collection<HomeLayoutDeviceClass>,
        counts: Map<ExclusionRuleId, Int> = mutableCounts.value,
    ): ExclusionsSettingsModel? =
        repository.snapshot()?.let { rules ->
            ExclusionsSettingsPlanner.plan(rules, viewed, available, counts, disabledSources())
        }

    /** The reason [draft] cannot be added on [layout] yet, or null; used live while typing. */
    fun problemWith(
        layout: HomeLayoutDeviceClass,
        draft: TextRuleDraft,
    ): TextRuleProblem? = TextRuleValidator.validate(draft, repository.rules(layout))

    /** Applies [action] to [layout] and persists it. False when it changed nothing (the reason is announced). */
    fun dispatch(
        layout: HomeLayoutDeviceClass,
        action: ExclusionsSettingsAction,
    ): Boolean = applyChange { current -> action.applyTo(current, layout, ids, nowEpochMillis) }

    /**
     * The contextual Hide action's hook: hides what [kind] describes for [item] on [layout] (every layout with
     * [allLayouts]) and announces it with Undo. A hide on one layout also offers [hideOnAllLayouts] until the
     * announcement ends; the [item] is held in memory for that long only and never stored or logged.
     */
    fun hide(
        layout: HomeLayoutDeviceClass,
        item: Item,
        kind: HideKind,
        allLayouts: Boolean = false,
    ): Boolean {
        val applied = applyChange { current -> ExclusionHideActions.apply(current, layout, item, kind, allLayouts) }
        if (applied && !allLayouts) {
            lastHide = HideRequest(layout, item, kind)
            mutableFeedback.value = mutableFeedback.value?.copy(canHideEverywhere = true)
        }
        return applied
    }

    /**
     * The snackbar's "Hide on all layouts": repeats the last contextual Hide for every layout (rules that exist
     * are left alone, so it is safe after any other change) and announces it with its own Undo.
     */
    fun hideOnAllLayouts(): Boolean {
        val request = lastHide ?: return false
        return hide(request.layout, request.item, request.kind, allLayouts = true)
    }

    /** Puts back what the last destructive change replaced, if its Undo is still on offer and still safe. */
    fun undo() {
        val pending = offer ?: return
        offer = null
        lastHide = null
        if (repository.version.value != pending.version) {
            announce(ExclusionsAnnouncements.CANNOT_UNDO, canUndo = false)
            return
        }
        repository.update { pending.change.undo() }
        announce(ExclusionsAnnouncements.UNDONE, canUndo = false)
    }

    /** The announcement was shown (or dismissed): without Undo being pressed the offer ends with it. */
    fun feedbackShown(id: Long) {
        if (mutableFeedback.value?.id == id) {
            mutableFeedback.value = null
            offer = null
            lastHide = null
        }
    }

    /** The page closed: nothing is left to announce and no Undo is on offer. */
    fun leave() {
        mutableFeedback.value = null
        offer = null
        lastHide = null
    }

    private fun applyChange(compute: (LayoutExclusionRules) -> ExclusionsChange): Boolean {
        lastHide = null
        var computed: ExclusionsChange? = null
        repository.update { current -> compute(current).also { computed = it }.after }
        val change = computed ?: return false
        offer = if (change.applied && change.undoable) UndoOffer(change, repository.version.value) else null
        announce(ExclusionsAnnouncements.message(change.message, layoutName), canUndo = offer != null)
        return change.applied
    }

    private fun retrack() {
        val layout = viewed ?: return
        counter?.track(repository.rules(layout))
    }

    private fun announce(
        message: String,
        canUndo: Boolean,
    ) {
        mutableFeedback.value = ExclusionsFeedback(++announcements, message, canUndo)
    }
}
