package com.riffle.core.domain.launcher.workspace.editor

import com.riffle.core.domain.launcher.workspace.SourceCapability
import com.riffle.core.domain.launcher.workspace.SourceDescriptor
import com.riffle.core.domain.launcher.workspace.SourceId
import com.riffle.core.domain.launcher.workspace.settings.SourceStatus
import com.riffle.core.domain.launcher.workspace.sources.SourceAccess

/**
 * One source the Source step offers. [badges] are its capabilities in a stable order. [access] is what
 * the platform gate allows right now (read without prompting): [needsPermission] flags a source the UI
 * must show with a "needs access" affordance that routes to an explicit, user-initiated flow. Choosing
 * such a source is allowed (the lens is only a definition); it simply shows no items until access is granted.
 */
data class SourceChoice(
    val descriptor: SourceDescriptor,
    val badges: List<SourceCapability>,
    val access: SourceAccess,
    /**
     * What Settings > Sources would say about the source right now (Ready, Needs permission, Off...), for hosts that
     * know it; null where the host does not (the editor). Only informs the UI: [selectable] and [needsPermission]
     * stay derived from [access].
     */
    val status: SourceStatus? = null,
) {
    val id: SourceId get() = descriptor.id

    val needsPermission: Boolean get() = access == SourceAccess.REQUIRED

    /** An unavailable source (not supported on this device) cannot be picked. */
    val selectable: Boolean get() = access != SourceAccess.UNAVAILABLE

    val groupable: Boolean get() = SourceCapability.GROUPABLE in descriptor.capabilities
}

object SourceChoices {
    /** [access] defaults to granted for sources it does not mention (apps need no permission). */
    fun build(
        descriptors: List<SourceDescriptor>,
        access: Map<SourceId, SourceAccess> = emptyMap(),
    ): List<SourceChoice> =
        descriptors.distinctBy { it.id }.map { descriptor ->
            SourceChoice(
                descriptor = descriptor,
                badges = descriptor.capabilities.sortedBy { it.ordinal },
                access = access[descriptor.id] ?: SourceAccess.GRANTED,
            )
        }
}
