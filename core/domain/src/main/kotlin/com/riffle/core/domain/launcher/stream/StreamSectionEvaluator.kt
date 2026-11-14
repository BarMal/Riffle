package com.riffle.core.domain.launcher.stream

import com.riffle.core.domain.launcher.workspace.Item
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.SourceState
import com.riffle.core.domain.launcher.workspace.exclusions.SourceExclusionFilter

/** The latest state of every input a section reads; an input the section does not read is ignored. */
data class StreamSectionInputs(
    val items: Map<SourceId, SourceState> = emptyMap(),
    val tasks: TypedSourceState<TaskContent> = TypedSourceState.Loading,
    val alarm: TypedSourceState<NextAlarm> = TypedSourceState.Loading,
) {
    /**
     * The honest-Loading rule: once a section has waited [StreamLoadingRule.TIMEOUT_MILLIS] for a first answer, every
     * input of [section] that has not answered reads as unavailable, so the section shows what did arrive, else its
     * Unavailable state with Retry, never Loading for good. An input that answered keeps its state.
     */
    fun withLoadingExpired(section: StreamSection): StreamSectionInputs =
        StreamSectionInputs(
            items =
                section.itemSources.associateWith { id ->
                    items[id]?.takeUnless { it == SourceState.Loading } ?: SourceState.Unavailable
                },
            tasks = if (tasks == TypedSourceState.Loading) TypedSourceState.Unavailable else tasks,
            alarm = if (alarm == TypedSourceState.Loading) TypedSourceState.Unavailable else alarm,
        )
}

/** How long a Stream section may show Loading while a first result is genuinely pending. */
object StreamLoadingRule {
    const val TIMEOUT_MILLIS = 5_000L
}

/**
 * The pure core of the Stream: source states in, one [StreamSectionResult] out. Per section it
 *
 * 1. drops items hidden by the layout's exclusion rules ([SourceExclusionFilter], the lens pipeline's own pre-step),
 * 2. redacts and types them ([StreamContentMapper], through the lens pipeline's own redaction and content levels),
 * 3. runs the section's fixed query ([StreamSectionQueries]),
 * 4. reduces the states of the inputs to one availability.
 *
 * Availability: any content wins; otherwise still loading, then a missing permission, then no account, then
 * "readable but empty", then unavailable, then off. So a section that reads several sources shows what exists and
 * only reports a gap when there is nothing to show.
 */
object StreamSectionEvaluator {
    fun evaluate(
        section: StreamSection,
        inputs: StreamSectionInputs,
        context: StreamContext,
    ): StreamSectionResult {
        val states = section.states(inputs)
        val content = states.flatMap { it.content(section, context) }
        val groups = StreamSectionQueries.run(section, content, context)
        if (section == StreamSection.TODAY) todayResult(groups, states)?.let { return it }
        return when {
            groups.isNotEmpty() -> StreamSectionResult.Ready(groups)
            states.any { it is Slot.Loading } -> StreamSectionResult.Loading
            states.any { it is Slot.Permission } ->
                StreamSectionResult.PermissionRequired(
                    states.filterIsInstance<Slot.Permission>().map {
                        StreamSourceIds.userSourceOf(it.source)
                    }.distinct(),
                )
            states.any { it is Slot.NotConnected } ->
                StreamSectionResult.NotConnected(states.filterIsInstance<Slot.NotConnected>().first().reason)
            states.any { it is Slot.Ready } -> StreamSectionResult.Empty
            states.any { it is Slot.Unavailable } -> StreamSectionResult.Unavailable
            else -> StreamSectionResult.Off
        }
    }

    /**
     * Today draws what is there beside what is not: events and tasks are ready together with what each input that gave
     * none said ([StreamGap]), and a connected task list with nothing on it is ready (the checklist and its add
     * field). Null when there is nothing of that kind to say, and the usual availability rules apply.
     */
    private fun todayResult(
        groups: List<StreamGroup>,
        states: List<Slot>,
    ): StreamSectionResult? {
        val gaps =
            StreamSection.TODAY.slotSources().zip(states).mapNotNull { (source, slot) ->
                when (slot) {
                    is Slot.Permission ->
                        StreamGap(
                            source,
                            StreamSectionResult.PermissionRequired(listOf(StreamSourceIds.userSourceOf(slot.source))),
                        )
                    is Slot.NotConnected -> StreamGap(source, StreamSectionResult.NotConnected(slot.reason))
                    // Only the task list says so: a calendar feed that did not answer is not worth a card of its own.
                    Slot.Unavailable ->
                        StreamGap(source, StreamSectionResult.Unavailable).takeIf { source == StreamSourceIds.TASKS }
                    is Slot.Ready -> StreamGap(source, StreamSectionResult.Empty).takeIf { _ -> slot.isEmptyTasks }
                    Slot.Loading, Slot.Off -> null
                }
            }
        return when {
            groups.isNotEmpty() -> StreamSectionResult.Ready(groups, gaps)
            // A readable, empty task list is something to draw even while the calendar is still answering.
            gaps.any { it.source == StreamSourceIds.TASKS && it.state == StreamSectionResult.Empty } ->
                StreamSectionResult.Ready(emptyList(), gaps)
            else -> null
        }
    }

    private fun StreamSection.slotSources(): List<SourceId> =
        itemSources +
            listOfNotNull(StreamSourceIds.TASKS.takeIf { readsTasks }, StreamSourceIds.ALARM.takeIf { readsAlarm })

    /** A section with no inputs at all reads as empty. */
    private fun StreamSection.states(inputs: StreamSectionInputs): List<Slot> =
        buildList {
            itemSources.forEach { id -> add(Slot.of(id, inputs.items[id] ?: SourceState.Loading)) }
            if (readsTasks) add(Slot.of(StreamSourceIds.TASKS, inputs.tasks))
            if (readsAlarm) add(Slot.of(StreamSourceIds.ALARM, inputs.alarm))
        }

    private sealed interface Slot {
        fun content(
            section: StreamSection,
            context: StreamContext,
        ): List<StreamContent> = emptyList()

        data object Loading : Slot

        data class Permission(val source: SourceId) : Slot

        data class NotConnected(val reason: NotConnectedReason) : Slot

        data object Unavailable : Slot

        data object Off : Slot

        data class Ready(val ready: ReadyContent) : Slot {
            /** A readable task list with no task on it. */
            val isEmptyTasks: Boolean get() = ready is ReadyContent.Tasks && ready.tasks.isEmpty()

            override fun content(
                section: StreamSection,
                context: StreamContext,
            ): List<StreamContent> = ready.build(context)
        }

        companion object {
            fun of(
                id: SourceId,
                state: SourceState,
            ): Slot =
                when (state) {
                    SourceState.Loading -> Loading
                    SourceState.PermissionRequired -> Permission(id)
                    SourceState.Unavailable -> Unavailable
                    SourceState.Off -> Off
                    is SourceState.Ready -> Ready(ReadyContent.Items(state.items))
                }

            @Suppress("UNCHECKED_CAST")
            fun <T> of(
                id: SourceId,
                state: TypedSourceState<T>,
            ): Slot =
                when (state) {
                    TypedSourceState.Loading -> Loading
                    TypedSourceState.PermissionRequired -> Permission(id)
                    is TypedSourceState.NotConnected -> NotConnected(state.reason)
                    TypedSourceState.Unavailable -> Unavailable
                    TypedSourceState.Off -> Off
                    is TypedSourceState.Ready<*> ->
                        Ready(
                            when (id) {
                                StreamSourceIds.TASKS -> ReadyContent.Tasks(state.values as List<TaskContent>)
                                else -> ReadyContent.Alarms(state.values as List<NextAlarm>)
                            },
                        )
                }
        }
    }

    private sealed interface ReadyContent {
        fun build(context: StreamContext): List<StreamContent>

        data class Items(val items: List<Item>) : ReadyContent {
            override fun build(context: StreamContext): List<StreamContent> =
                SourceExclusionFilter.apply(context.exclusions, items)
                    .mapNotNull { StreamContentMapper.map(it, context.contentLevels) }
        }

        data class Tasks(val tasks: List<TaskContent>) : ReadyContent {
            override fun build(context: StreamContext): List<StreamContent> =
                tasks.map { StreamContentMapper.redact(it, context.contentLevels) }
        }

        data class Alarms(val alarms: List<NextAlarm>) : ReadyContent {
            override fun build(context: StreamContext): List<StreamContent> =
                alarms.map { StreamContentMapper.alarm(it, context.contentLevels) }
        }
    }
}
