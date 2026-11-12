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
)

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
        return when {
            groups.isNotEmpty() -> StreamSectionResult.Ready(groups)
            states.any { it is Slot.Loading } -> StreamSectionResult.Loading
            states.any { it is Slot.Permission } ->
                StreamSectionResult.PermissionRequired(
                    states.filterIsInstance<Slot.Permission>().map {
                        StreamSourceIds.userSourceOf(it.source)
                    }.distinct(),
                )
            states.any { it is Slot.NotConnected } -> StreamSectionResult.NotConnected
            states.any { it is Slot.Ready } -> StreamSectionResult.Empty
            states.any { it is Slot.Unavailable } -> StreamSectionResult.Unavailable
            else -> StreamSectionResult.Off
        }
    }

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

        data object NotConnected : Slot

        data object Unavailable : Slot

        data object Off : Slot

        data class Ready(val ready: ReadyContent) : Slot {
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
                    TypedSourceState.NotConnected -> NotConnected
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
