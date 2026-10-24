package com.riffle.app.launcher.wizard

import com.riffle.core.domain.launcher.apps.AppActivityName
import com.riffle.core.domain.launcher.apps.AppIdentity
import com.riffle.core.domain.launcher.apps.AppPackageName
import com.riffle.core.domain.launcher.apps.AppProfile
import com.riffle.core.domain.launcher.apps.AppProfileId
import com.riffle.core.domain.launcher.apps.AppProfileType
import com.riffle.core.domain.launcher.workspace.ContainerId
import com.riffle.core.domain.launcher.workspace.editor.WidgetSlot
import com.riffle.core.domain.launcher.workspace.editor.WidgetTarget
import com.riffle.core.domain.launcher.workspace.wizard.AppPick
import com.riffle.core.domain.launcher.workspace.wizard.ContentGoalId
import com.riffle.core.domain.launcher.workspace.wizard.Destination
import com.riffle.core.domain.launcher.workspace.wizard.GoalSetup
import com.riffle.core.domain.launcher.workspace.wizard.WizardState
import com.riffle.core.domain.launcher.workspace.wizard.WizardStep

/**
 * The wizard's choices as values a saved instance state can hold (nested `ArrayList`s of strings, numbers, booleans
 * and nulls), so a run survives process death. Identities and lens choices only, never item content. A closed wizard
 * ([WizardState.exit]) is not kept. [decode] never throws: anything it cannot read gives null (start again).
 */
internal object WizardStateCodec {
    private const val VERSION = 1

    fun encode(state: WizardState): ArrayList<Any?> =
        arrayListOf(
            VERSION,
            state.step.name,
            state.setup?.let(::encodeSetup),
            state.destination?.name,
            state.widgetTarget?.let { arrayListOf(it.pageId?.value, it.slot.column, it.slot.row) },
            state.saveToLibrary,
            state.name,
        )

    fun decode(value: Any?): WizardState? = runCatching { decodeOrThrow(value) }.getOrNull()

    private fun decodeOrThrow(value: Any?): WizardState? {
        val list = (value as? List<*>)?.takeIf { it.getOrNull(0) == VERSION } ?: return null
        return WizardState(
            step = WizardStep.valueOf(list[1] as String),
            setup = (list[2] as List<*>?)?.let(::decodeSetup),
            destination = (list[3] as String?)?.let(Destination::valueOf),
            widgetTarget = (list[4] as List<*>?)?.let(::decodeTarget),
            saveToLibrary = list[5] as Boolean,
            name = list[6] as String?,
        )
    }

    private fun encodeSetup(setup: GoalSetup): ArrayList<Any?> =
        arrayListOf(
            setup.goal.key,
            setup.filterId,
            setup.lookId,
            ArrayList(setup.apps.map(::encodeApp)),
            ArrayList(setup.feeds),
            setup.query,
        )

    private fun decodeSetup(list: List<*>): GoalSetup =
        GoalSetup(
            goal = checkNotNull(ContentGoalId.fromKey(list[0] as String)),
            filterId = list[1] as String,
            lookId = list[2] as String,
            apps = (list[3] as List<*>).map { decodeApp(it as List<*>) },
            feeds = (list[4] as List<*>).map { it as String },
            query = list[5] as String,
        )

    private fun encodeApp(app: AppPick): ArrayList<Any?> =
        arrayListOf(
            app.identity.packageName.value,
            app.identity.activityName.value,
            app.identity.profile.id.value,
            app.identity.profile.type.name,
            app.label,
        )

    private fun decodeApp(list: List<*>): AppPick =
        AppPick(
            AppIdentity(
                AppPackageName(list[0] as String),
                AppActivityName(list[1] as String),
                AppProfile(AppProfileId(list[2] as String), AppProfileType.valueOf(list[3] as String)),
            ),
            list[4] as String,
        )

    private fun decodeTarget(list: List<*>): WidgetTarget =
        WidgetTarget((list[0] as String?)?.let(::ContainerId), WidgetSlot(list[1] as Int, list[2] as Int))
}
